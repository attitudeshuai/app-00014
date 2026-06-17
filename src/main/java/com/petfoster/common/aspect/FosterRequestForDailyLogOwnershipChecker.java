package com.petfoster.common.aspect;

import com.petfoster.common.BusinessException;
import com.petfoster.entity.FosterRequest;
import com.petfoster.repository.FosterRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FosterRequestForDailyLogOwnershipChecker implements OwnershipChecker {

    private final FosterRequestRepository requestRepository;

    @Override
    public ResourceType getResourceType() {
        return ResourceType.FOSTER_REQUEST_FOR_DAILY_LOG;
    }

    @Override
    public void checkOwnership(Long resourceId, Long userId, OwnershipRole role) {
        FosterRequest request = requestRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));
        if (request.getFostererId() == null || !request.getFostererId().equals(userId)) {
            throw BusinessException.forbidden("只有寄养人才能创建日报");
        }
    }
}
