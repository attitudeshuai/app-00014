package com.petfoster.aspect;

import com.petfoster.annotation.ResourceType;
import com.petfoster.common.BusinessException;
import com.petfoster.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PetOwnershipResolver implements OwnershipResolver {

    private final PetRepository petRepository;

    @Override
    public boolean supports(ResourceType resource, String role) {
        return resource == ResourceType.PET;
    }

    @Override
    public Long resolveOwnerId(Long resourceId) {
        return petRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"))
                .getOwnerId();
    }
}
