package com.petfoster.aspect;

import com.petfoster.annotation.ResourceType;
import com.petfoster.common.BusinessException;
import com.petfoster.repository.FosterDailyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyLogOwnershipResolver implements OwnershipResolver {

    private final FosterDailyLogRepository logRepository;

    @Override
    public boolean supports(ResourceType resource, String role) {
        return resource == ResourceType.DAILY_LOG;
    }

    @Override
    public Long resolveOwnerId(Long resourceId) {
        return logRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"))
                .getFostererId();
    }
}
