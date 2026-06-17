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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OwnershipAspect {

    private final PetRepository petRepository;
    private final FosterRequestRepository fosterRequestRepository;
    private final FosterDailyLogRepository dailyLogRepository;

    @Around("@annotation(checkOwnership)")
    public Object checkOwnership(ProceedingJoinPoint joinPoint, CheckOwnership checkOwnership) throws Throwable {
        Object[] args = joinPoint.getArgs();

        Long resourceId = extractResourceId(args, checkOwnership);
        Long userId = extractUserId(args, checkOwnership);

        validateOwnership(checkOwnership.resourceType(), resourceId, userId,
                checkOwnership.role(), checkOwnership.errorMessage());

        return joinPoint.proceed();
    }

    private Long extractResourceId(Object[] args, CheckOwnership checkOwnership) {
        Object arg = args[checkOwnership.idIndex()];

        if (arg instanceof Long) {
            return (Long) arg;
        }

        String idField = checkOwnership.idField();
        if (idField.isEmpty()) {
            throw new IllegalStateException("无法从参数中提取资源ID，请指定 idField");
        }

        try {
            Field field = arg.getClass().getDeclaredField(idField);
            field.setAccessible(true);
            Object value = field.get(arg);
            if (value instanceof Long) {
                return (Long) value;
            }
            throw new IllegalStateException("资源ID字段类型必须是 Long");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("无法从字段 " + idField + " 获取资源ID", e);
        }
    }

    private Long extractUserId(Object[] args, CheckOwnership checkOwnership) {
        Object arg = args[checkOwnership.userIdIndex()];
        if (arg instanceof Long) {
            return (Long) arg;
        }
        throw new IllegalStateException("用户ID参数类型必须是 Long");
    }

    private void validateOwnership(ResourceType resourceType, Long resourceId, Long userId,
                                    OwnershipRole role, String errorMessage) {
        switch (resourceType) {
            case PET -> validatePetOwnership(resourceId, userId, role, errorMessage);
            case FOSTER_REQUEST -> validateFosterRequestOwnership(resourceId, userId, role, errorMessage);
            case DAILY_LOG -> validateDailyLogOwnership(resourceId, userId, role, errorMessage);
        }
    }

    private void validatePetOwnership(Long petId, Long userId, OwnershipRole role, String errorMessage) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"));

        boolean isOwner = pet.getOwnerId().equals(userId);

        switch (role) {
            case OWNER -> {
                if (!isOwner) {
                    throw BusinessException.forbidden(errorMessage);
                }
            }
            case FOSTERER, OWNER_OR_FOSTERER ->
                    throw new UnsupportedOperationException("宠物不支持寄养人角色校验");
        }
    }

    private void validateFosterRequestOwnership(Long requestId, Long userId, OwnershipRole role,
                                                String errorMessage) {
        FosterRequest request = fosterRequestRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        boolean isOwner = request.getOwnerId().equals(userId);
        boolean isFosterer = request.getFostererId() != null && request.getFostererId().equals(userId);

        switch (role) {
            case OWNER -> {
                if (!isOwner) {
                    throw BusinessException.forbidden(errorMessage);
                }
            }
            case FOSTERER -> {
                if (!isFosterer) {
                    throw BusinessException.forbidden(errorMessage);
                }
            }
            case OWNER_OR_FOSTERER -> {
                if (!isOwner && !isFosterer) {
                    throw BusinessException.forbidden(errorMessage);
                }
            }
        }
    }

    private void validateDailyLogOwnership(Long logId, Long userId, OwnershipRole role, String errorMessage) {
        FosterDailyLog dailyLog = dailyLogRepository.findById(logId)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"));

        boolean isFosterer = dailyLog.getFostererId().equals(userId);

        switch (role) {
            case FOSTERER -> {
                if (!isFosterer) {
                    throw BusinessException.forbidden(errorMessage);
                }
            }
            case OWNER, OWNER_OR_FOSTERER ->
                    throw new UnsupportedOperationException("日报仅支持寄养人角色校验");
        }
    }
}
