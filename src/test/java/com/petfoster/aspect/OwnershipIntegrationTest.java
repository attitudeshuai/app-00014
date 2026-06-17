package com.petfoster.aspect;

import com.petfoster.common.BusinessException;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Pet;
import com.petfoster.repository.FosterDailyLogRepository;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import com.petfoster.service.FileStorageService;
import com.petfoster.service.FosterRequestService;
import com.petfoster.service.PetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OwnershipIntegrationTest.TestConfig.class)
class OwnershipIntegrationTest {

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        PetRepository petRepository() {
            return mock(PetRepository.class);
        }

        @Bean
        FosterRequestRepository fosterRequestRepository() {
            return mock(FosterRequestRepository.class);
        }

        @Bean
        FosterDailyLogRepository fosterDailyLogRepository() {
            return mock(FosterDailyLogRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        ApplicationEventPublisher eventPublisher() {
            return mock(ApplicationEventPublisher.class);
        }

        @Bean
        PetOwnershipResolver petOwnershipResolver(PetRepository repo) {
            return new PetOwnershipResolver(repo);
        }

        @Bean
        FosterRequestOwnershipResolver fosterRequestOwnershipResolver(FosterRequestRepository repo) {
            return new FosterRequestOwnershipResolver(repo);
        }

        @Bean
        FosterRequestFostererResolver fosterRequestFostererResolver(FosterRequestRepository repo) {
            return new FosterRequestFostererResolver(repo);
        }

        @Bean
        FosterRequestParticipantResolver fosterRequestParticipantResolver(FosterRequestRepository repo) {
            return new FosterRequestParticipantResolver(repo);
        }

        @Bean
        DailyLogOwnershipResolver dailyLogOwnershipResolver(FosterDailyLogRepository repo) {
            return new DailyLogOwnershipResolver(repo);
        }

        @Bean
        OwnershipAspect ownershipAspect(java.util.List<OwnershipResolver> resolvers) {
            return new OwnershipAspect(resolvers);
        }

        @Bean
        FileStorageService fileStorageService() {
            return mock(FileStorageService.class);
        }

        @Bean
        PetService petService(PetRepository petRepository, UserRepository userRepository,
                              FileStorageService fileStorageService) {
            return new PetService(petRepository, userRepository, fileStorageService);
        }

        @Bean
        FosterRequestService fosterRequestService(FosterRequestRepository requestRepository,
                                                   PetRepository petRepository,
                                                   UserRepository userRepository,
                                                   ApplicationEventPublisher eventPublisher) {
            return new FosterRequestService(requestRepository, petRepository, userRepository, eventPublisher);
        }
    }

    @Nested
    @DisplayName("PetService 单注解 @RequireOwner 集成测试")
    class PetServiceTests {

        @Autowired
        private PetService petService;

        @Autowired
        private PetRepository petRepository;

        @BeforeEach
        void setUp() {
            Mockito.reset(petRepository);
        }

        @Test
        @DisplayName("宠物主人删除自己的宠物 - 放行")
        void shouldAllowOwnerToDeletePet() {
            Pet pet = Pet.builder().id(1L).ownerId(10L).build();
            Mockito.when(petRepository.findById(1L)).thenReturn(Optional.of(pet));

            petService.deletePet(10L, 1L);

            verify(petRepository).delete(pet);
        }

        @Test
        @DisplayName("非宠物主人删除宠物 - 抛出403")
        void shouldForbidNonOwnerToDeletePet() {
            Pet pet = Pet.builder().id(1L).ownerId(10L).build();
            Mockito.when(petRepository.findById(1L)).thenReturn(Optional.of(pet));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> petService.deletePet(999L, 1L));

            assertEquals(403, ex.getCode());
            assertEquals("无权限删除此宠物", ex.getMessage());
            verify(petRepository, never()).delete(any());
        }

        @Test
        @DisplayName("宠物不存在 - 抛出404")
        void shouldThrowNotFoundWhenPetMissing() {
            Mockito.when(petRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> petService.deletePet(10L, 999L));

            assertEquals(404, ex.getCode());
        }
    }

    @Nested
    @DisplayName("FosterRequestService PARTICIPANT 角色 OR 逻辑集成测试")
    class FosterRequestServiceTests {

        @Autowired
        private FosterRequestService fosterRequestService;

        @Autowired
        private FosterRequestRepository fosterRequestRepository;

        @BeforeEach
        void setUp() {
            Mockito.reset(fosterRequestRepository);
        }

        private FosterRequest buildRequest(Long ownerId, Long fostererId) {
            return FosterRequest.builder()
                    .id(1L).ownerId(ownerId).fostererId(fostererId).petId(100L)
                    .status(FosterRequest.Status.Pending).build();
        }

        @Test
        @DisplayName("宠物主人修改寄养申请状态 - 放行")
        void shouldAllowOwnerToUpdateStatus() {
            FosterRequest request = buildRequest(10L, 20L);
            Mockito.when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            Mockito.when(fosterRequestRepository.save(any())).thenReturn(request);

            fosterRequestService.updateStatus(10L, 1L, FosterRequest.Status.Approved);

            verify(fosterRequestRepository).save(any());
        }

        @Test
        @DisplayName("寄养人修改寄养申请状态 - 放行")
        void shouldAllowFostererToUpdateStatus() {
            FosterRequest request = buildRequest(10L, 20L);
            Mockito.when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            Mockito.when(fosterRequestRepository.save(any())).thenReturn(request);

            fosterRequestService.updateStatus(20L, 1L, FosterRequest.Status.Approved);

            verify(fosterRequestRepository).save(any());
        }

        @Test
        @DisplayName("既非主人也非寄养人修改状态 - 抛出403")
        void shouldForbidStrangerToUpdateStatus() {
            FosterRequest request = buildRequest(10L, 20L);
            Mockito.when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> fosterRequestService.updateStatus(999L, 1L, FosterRequest.Status.Approved));

            assertEquals(403, ex.getCode());
            assertEquals("无权限修改此寄养申请状态", ex.getMessage());
            verify(fosterRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("fostererId为null时主人修改状态 - 放行")
        void shouldAllowOwnerWhenFostererIsNull() {
            FosterRequest request = buildRequest(10L, null);
            Mockito.when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            Mockito.when(fosterRequestRepository.save(any())).thenReturn(request);

            fosterRequestService.updateStatus(10L, 1L, FosterRequest.Status.Approved);

            verify(fosterRequestRepository).save(any());
        }

        @Test
        @DisplayName("fostererId为null时非主人修改状态 - 抛出403")
        void shouldForbidStrangerWhenFostererIsNull() {
            FosterRequest request = buildRequest(10L, null);
            Mockito.when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> fosterRequestService.updateStatus(999L, 1L, FosterRequest.Status.Approved));

            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("寄养申请不存在 - 抛出404")
        void shouldThrowNotFoundWhenRequestMissing() {
            Mockito.when(fosterRequestRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> fosterRequestService.updateStatus(10L, 999L, FosterRequest.Status.Approved));

            assertEquals(404, ex.getCode());
        }
    }

    @Nested
    @DisplayName("FosterRequestService 单注解 @RequireOwner 集成测试")
    class FosterRequestSingleAnnotationTests {

        @Autowired
        private FosterRequestService fosterRequestService;

        @Autowired
        private FosterRequestRepository fosterRequestRepository;

        @BeforeEach
        void setUp() {
            Mockito.reset(fosterRequestRepository);
        }

        @Test
        @DisplayName("宠物主人删除寄养申请 - 放行")
        void shouldAllowOwnerToDeleteRequest() {
            FosterRequest request = FosterRequest.builder()
                    .id(1L).ownerId(10L).fostererId(20L).petId(100L)
                    .status(FosterRequest.Status.Pending).build();
            Mockito.when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            fosterRequestService.deleteRequest(10L, 1L);

            verify(fosterRequestRepository).delete(request);
        }

        @Test
        @DisplayName("非主人删除寄养申请 - 抛出403")
        void shouldForbidNonOwnerToDeleteRequest() {
            FosterRequest request = FosterRequest.builder()
                    .id(1L).ownerId(10L).fostererId(20L).petId(100L)
                    .status(FosterRequest.Status.Pending).build();
            Mockito.when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> fosterRequestService.deleteRequest(999L, 1L));

            assertEquals(403, ex.getCode());
            assertEquals("无权限删除此寄养申请", ex.getMessage());
            verify(fosterRequestRepository, never()).delete(any());
        }
    }
}
