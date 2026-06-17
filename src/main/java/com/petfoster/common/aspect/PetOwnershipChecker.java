package com.petfoster.common.aspect;

import com.petfoster.common.BusinessException;
import com.petfoster.entity.Pet;
import com.petfoster.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PetOwnershipChecker implements OwnershipChecker {

    private final PetRepository petRepository;

    @Override
    public ResourceType getResourceType() {
        return ResourceType.PET;
    }

    @Override
    public void checkOwnership(Long resourceId, Long userId, OwnershipRole role) {
        Pet pet = petRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"));
        if (!pet.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("无权限操作此宠物");
        }
    }
}
