package com.petfoster.aspect;

import com.petfoster.annotation.ResourceType;
import com.petfoster.common.BusinessException;
import com.petfoster.repository.FosterRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FosterRequestOwnershipResolver implements OwnershipResolver {

    private final FosterRequestRepository requestRepository;

    @Override
    public boolean supports(ResourceType resource, String role) {
        return resource == ResourceType.FOSTER_REQUEST && (role == null || role.isEmpty());
    }

    @Override
    public Long resolveOwnerId(Long resourceId) {
        return requestRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"))
                .getOwnerId();
    }
}
