package com.petfoster.common.aspect;

import com.petfoster.common.BusinessException;
import com.petfoster.entity.FosterDailyLog;
import com.petfoster.repository.FosterDailyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyLogOwnershipChecker implements OwnershipChecker {

    private final FosterDailyLogRepository logRepository;

    @Override
    public ResourceType getResourceType() {
        return ResourceType.DAILY_LOG;
    }

    @Override
    public void checkOwnership(Long resourceId, Long userId, OwnershipRole role) {
        FosterDailyLog log = logRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"));
        if (!log.getFostererId().equals(userId)) {
            throw BusinessException.forbidden("无权限操作此日报");
        }
    }
}
