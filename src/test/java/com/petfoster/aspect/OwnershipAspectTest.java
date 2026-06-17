package com.petfoster.aspect;

import com.petfoster.annotation.CheckOwnership;
import com.petfoster.annotation.OwnershipRole;
import com.petfoster.annotation.ResourceType;
import com.petfoster.common.BusinessException;
import com.petfoster.entity.FosterDailyLog;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Pet;
import com.petfoster.repository.FosterDailyLogRepository;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.PetRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnershipAspectTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private FosterRequestRepository fosterRequestRepository;

    @Mock
    private FosterDailyLogRepository dailyLogRepository;

    @InjectMocks
    private OwnershipAspect ownershipAspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    private Pet testPet;
    private FosterRequest testRequest;
    private FosterDailyLog testDailyLog;

    @BeforeEach
    void setUp() {
        testPet = Pet.builder()
                .id(1L)
                .ownerId(100L)
                .name("小白")
                .species("猫")
                .build();

        testRequest = FosterRequest.builder()
                .id(1L)
                .petId(1L)
                .ownerId(100L)
                .fostererId(200L)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .status(FosterRequest.Status.Pending)
                .build();

        testDailyLog = FosterDailyLog.builder()
                .id(1L)
                .requestId(1L)
                .fostererId(200L)
                .logDate(LocalDate.now())
                .build();
    }

    private CheckOwnership createAnnotation(ResourceType resourceType, int idIndex,
                                            String idField, int userIdIndex,
                                            OwnershipRole role, String errorMessage) {
        return new CheckOwnership() {
            @Override
            public ResourceType resourceType() { return resourceType; }
            @Override
            public int idIndex() { return idIndex; }
            @Override
            public int userIdIndex() { return userIdIndex; }
            @Override
            public String idField() { return idField; }
            @Override
            public OwnershipRole role() { return role; }
            @Override
            public String errorMessage() { return errorMessage; }
            @Override
            public Class<? extends Annotation> annotationType() { return CheckOwnership.class; }
        };
    }

    @Test
    @DisplayName("宠物所有权校验 - 主人验证通过")
    void testPetOwnership_Owner_Success() throws Throwable {
        when(petRepository.findById(1L)).thenReturn(Optional.of(testPet));
        when(joinPoint.getArgs()).thenReturn(new Object[]{100L, 1L});
        when(joinPoint.proceed()).thenReturn("success");

        CheckOwnership annotation = createAnnotation(
                ResourceType.PET, 1, "", 0,
                OwnershipRole.OWNER, "无权限操作此宠物");

        Object result = ownershipAspect.checkOwnership(joinPoint, annotation);

        assertEquals("success", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("宠物所有权校验 - 非主人抛出异常")
    void testPetOwnership_NotOwner_Forbidden() throws Throwable {
        when(petRepository.findById(1L)).thenReturn(Optional.of(testPet));
        when(joinPoint.getArgs()).thenReturn(new Object[]{999L, 1L});

        CheckOwnership annotation = createAnnotation(
                ResourceType.PET, 1, "", 0,
                OwnershipRole.OWNER, "无权限操作此宠物");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ownershipAspect.checkOwnership(joinPoint, annotation));

        assertEquals("无权限操作此宠物", exception.getMessage());
        assertEquals(403, exception.getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("寄养申请所有权校验 - 主人验证通过")
    void testFosterRequestOwnership_Owner_Success() throws Throwable {
        when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));
        when(joinPoint.getArgs()).thenReturn(new Object[]{100L, 1L});
        when(joinPoint.proceed()).thenReturn("success");

        CheckOwnership annotation = createAnnotation(
                ResourceType.FOSTER_REQUEST, 1, "", 0,
                OwnershipRole.OWNER, "无权限操作此申请");

        Object result = ownershipAspect.checkOwnership(joinPoint, annotation);

        assertEquals("success", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("寄养申请所有权校验 - 寄养人验证通过")
    void testFosterRequestOwnership_Fosterer_Success() throws Throwable {
        when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));
        when(joinPoint.getArgs()).thenReturn(new Object[]{200L, 1L});
        when(joinPoint.proceed()).thenReturn("success");

        CheckOwnership annotation = createAnnotation(
                ResourceType.FOSTER_REQUEST, 1, "", 0,
                OwnershipRole.FOSTERER, "无权限操作此申请");

        Object result = ownershipAspect.checkOwnership(joinPoint, annotation);

        assertEquals("success", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("寄养申请所有权校验 - 主人或寄养人验证通过")
    void testFosterRequestOwnership_OwnerOrFosterer_Success() throws Throwable {
        when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));
        when(joinPoint.getArgs()).thenReturn(new Object[]{100L, 1L});
        when(joinPoint.proceed()).thenReturn("success");

        CheckOwnership annotation = createAnnotation(
                ResourceType.FOSTER_REQUEST, 1, "", 0,
                OwnershipRole.OWNER_OR_FOSTERER, "无权限操作此申请");

        Object result = ownershipAspect.checkOwnership(joinPoint, annotation);

        assertEquals("success", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("寄养申请所有权校验 - 非相关人员抛出异常")
    void testFosterRequestOwnership_NotRelated_Forbidden() throws Throwable {
        when(fosterRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));
        when(joinPoint.getArgs()).thenReturn(new Object[]{999L, 1L});

        CheckOwnership annotation = createAnnotation(
                ResourceType.FOSTER_REQUEST, 1, "", 0,
                OwnershipRole.OWNER_OR_FOSTERER, "无权限操作此申请");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ownershipAspect.checkOwnership(joinPoint, annotation));

        assertEquals("无权限操作此申请", exception.getMessage());
        assertEquals(403, exception.getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("日报所有权校验 - 寄养人验证通过")
    void testDailyLogOwnership_Fosterer_Success() throws Throwable {
        when(dailyLogRepository.findById(1L)).thenReturn(Optional.of(testDailyLog));
        when(joinPoint.getArgs()).thenReturn(new Object[]{200L, 1L});
        when(joinPoint.proceed()).thenReturn("success");

        CheckOwnership annotation = createAnnotation(
                ResourceType.DAILY_LOG, 1, "", 0,
                OwnershipRole.FOSTERER, "无权限操作此日报");

        Object result = ownershipAspect.checkOwnership(joinPoint, annotation);

        assertEquals("success", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("日报所有权校验 - 非寄养人抛出异常")
    void testDailyLogOwnership_NotFosterer_Forbidden() throws Throwable {
        when(dailyLogRepository.findById(1L)).thenReturn(Optional.of(testDailyLog));
        when(joinPoint.getArgs()).thenReturn(new Object[]{999L, 1L});

        CheckOwnership annotation = createAnnotation(
                ResourceType.DAILY_LOG, 1, "", 0,
                OwnershipRole.FOSTERER, "无权限操作此日报");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ownershipAspect.checkOwnership(joinPoint, annotation));

        assertEquals("无权限操作此日报", exception.getMessage());
        assertEquals(403, exception.getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("资源不存在抛出404")
    void testResourceNotFound_Throws404() throws Throwable {
        when(petRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(joinPoint.getArgs()).thenReturn(new Object[]{100L, 999L});

        CheckOwnership annotation = createAnnotation(
                ResourceType.PET, 1, "", 0,
                OwnershipRole.OWNER, "无权限操作此宠物");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ownershipAspect.checkOwnership(joinPoint, annotation));

        assertEquals("宠物不存在", exception.getMessage());
        assertEquals(404, exception.getCode());
        verify(joinPoint, never()).proceed();
    }

    static class TestDto {
        private Long petId;

        public TestDto(Long petId) {
            this.petId = petId;
        }

        public Long getPetId() {
            return petId;
        }
    }

    @Test
    @DisplayName("从DTO字段提取资源ID")
    void testExtractIdFromDtoField() throws Throwable {
        when(petRepository.findById(1L)).thenReturn(Optional.of(testPet));
        when(joinPoint.getArgs()).thenReturn(new Object[]{100L, new TestDto(1L)});
        when(joinPoint.proceed()).thenReturn("success");

        CheckOwnership annotation = createAnnotation(
                ResourceType.PET, 1, "petId", 0,
                OwnershipRole.OWNER, "无权限操作此宠物");

        Object result = ownershipAspect.checkOwnership(joinPoint, annotation);

        assertEquals("success", result);
        verify(joinPoint).proceed();
    }
}
