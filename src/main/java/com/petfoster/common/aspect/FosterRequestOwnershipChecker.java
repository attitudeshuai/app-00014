package com.petfoster.common.aspect;

import com.petfoster.common.BusinessException;
import com.petfoster.entity.FosterRequest;
import com.petfoster.repository.FosterRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FosterRequestOwnershipChecker implements OwnershipChecker {

    private final FosterRequestRepository requestRepository;

    @Override
    public ResourceType getResourceType() {
        return ResourceType.FOSTER_REQUEST;
    }

    @Override
    public void checkOwnership(Long resourceId, Long userId, OwnershipRole role) {
        FosterRequest request = requestRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        boolean isOwner = request.getOwnerId().equals(userId);
        boolean isFosterer = request.getFostererId() != null && request.getFostererId().equals(userId);

        boolean hasPermission = switch (role) {
            case OWNER -> isOwner;
            case FOSTERER -> isFosterer;
            case EITHER -> isOwner || isFosterer;
        };

        if (!hasPermission) {
            throw BusinessException.forbidden("无权限操作此寄养申请");
        }
    }
}
