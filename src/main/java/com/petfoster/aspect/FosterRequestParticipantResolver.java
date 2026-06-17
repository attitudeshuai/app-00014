package com.petfoster.aspect;

import com.petfoster.annotation.ResourceType;
import com.petfoster.common.BusinessException;
import com.petfoster.entity.FosterRequest;
import com.petfoster.repository.FosterRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FosterRequestParticipantResolver implements OwnershipResolver {

    private final FosterRequestRepository requestRepository;

    @Override
    public boolean supports(ResourceType resource, String role) {
        return resource == ResourceType.FOSTER_REQUEST && "PARTICIPANT".equals(role);
    }

    @Override
    public Long resolveOwnerId(Long resourceId) {
        return requestRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"))
                .getOwnerId();
    }

    @Override
    public boolean isOwnerMatch(Long resourceId, Long userId) {
        FosterRequest request = requestRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));
        if (request.getOwnerId().equals(userId)) {
            return true;
        }
        return request.getFostererId() != null && request.getFostererId().equals(userId);
    }
}
