package com.petfoster.service;

import com.petfoster.common.BusinessException;
import com.petfoster.dto.PetDTO;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PetService petService;

    private Pet testPet;
    private User testOwner;
    private PetDTO.CreatePetRequest createRequest;
    private PetDTO.UpdatePetRequest updateRequest;

    @BeforeEach
    void setUp() {
        testOwner = User.builder()
                .id(1L)
                .username("owner1")
                .email("owner1@test.com")
                .build();

        testPet = Pet.builder()
                .id(1L)
                .ownerId(1L)
                .name("小白")
                .species("猫")
                .breed("英短")
                .age(3)
                .dietNotes("每天3次猫粮")
                .build();

        createRequest = new PetDTO.CreatePetRequest();
        createRequest.setName("花花");
        createRequest.setSpecies("猫");
        createRequest.setBreed("布偶");
        createRequest.setAge(2);
        createRequest.setDietNotes("自由采食");

        updateRequest = new PetDTO.UpdatePetRequest();
        updateRequest.setName("小白白");
        updateRequest.setAge(4);
    }

    @Test
    @DisplayName("获取宠物列表 - 成功")
    void testGetPets_Success() {
        List<Pet> petList = Arrays.asList(testPet);
        Page<Pet> petPage = new PageImpl<>(petList);

        when(petRepository.searchPets(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(petPage);
        when(userRepository.findAllById(anyList())).thenReturn(List.of(testOwner));

        var result = petService.getPets(0, 10, "createdAt,desc", null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("小白", result.getContent().get(0).getName());
        assertEquals("owner1", result.getContent().get(0).getOwnerUsername());
    }

    @Test
    @DisplayName("获取宠物详情 - 成功")
    void testGetPetById_Success() {
        when(petRepository.findById(1L)).thenReturn(Optional.of(testPet));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testOwner));

        PetDTO.PetResponse response = petService.getPetById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("小白", response.getName());
    }

    @Test
    @DisplayName("获取宠物详情 - 宠物不存在")
    void testGetPetById_NotFound() {
        when(petRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> petService.getPetById(999L));

        assertEquals("宠物不存在", exception.getMessage());
    }

    @Test
    @DisplayName("创建宠物 - 成功")
    void testCreatePet_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testOwner));
        when(petRepository.save(any(Pet.class))).thenAnswer(inv -> {
            Pet saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        PetDTO.PetResponse response = petService.createPet(1L, createRequest);

        assertNotNull(response);
        assertEquals("花花", response.getName());
        assertEquals("猫", response.getSpecies());
        verify(petRepository).save(any(Pet.class));
    }

    @Test
    @DisplayName("更新宠物 - 成功")
    void testUpdatePet_Success() {
        when(petRepository.findById(1L)).thenReturn(Optional.of(testPet));
        when(petRepository.save(any(Pet.class))).thenReturn(testPet);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testOwner));

        PetDTO.PetResponse response = petService.updatePet(1L, 1L, updateRequest);

        assertNotNull(response);
        verify(petRepository).save(any(Pet.class));
    }

    @Test
    @DisplayName("删除宠物 - 成功")
    void testDeletePet_Success() {
        when(petRepository.findById(1L)).thenReturn(Optional.of(testPet));
        doNothing().when(petRepository).delete(testPet);

        assertDoesNotThrow(() -> petService.deletePet(1L, 1L));
        verify(petRepository).delete(testPet);
    }
}
