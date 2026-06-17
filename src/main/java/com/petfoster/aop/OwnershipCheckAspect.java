package com.petfoster.aop;

import com.petfoster.common.BusinessException;
import com.petfoster.common.CheckOwnership;
import com.petfoster.common.OwnershipContext;
import com.petfoster.common.OwnershipRole;
import com.petfoster.common.ResourceType;
import com.petfoster.entity.FosterDailyLog;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Pet;
import com.petfoster.repository.FosterDailyLogRepository;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.PetRepository;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Aspect
@Component
public class OwnershipCheckAspect {

    private final PetRepository petRepository;
    private final FosterRequestRepository requestRepository;
    private final FosterDailyLogRepository logRepository;

    private final ExpressionParser parser = new SpelExpressionParser();

    public OwnershipCheckAspect(PetRepository petRepository,
                                FosterRequestRepository requestRepository,
                                FosterDailyLogRepository logRepository) {
        this.petRepository = petRepository;
        this.requestRepository = requestRepository;
        this.logRepository = logRepository;
    }

    @Around("@annotation(checkOwnership)")
    public Object checkOwnership(ProceedingJoinPoint joinPoint, CheckOwnership checkOwnership) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null && args != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
            }
        }

        Long userId = resolveLong(context, checkOwnership.userIdExp());
        Long resourceId = resolveLong(context, checkOwnership.resourceIdExp());

        if (userId == null || resourceId == null) {
            throw BusinessException.badRequest("用户ID或资源ID不能为空");
        }

        try {
            Object resource = checkAndGetResource(checkOwnership.resourceType(), resourceId, userId,
                    checkOwnership.role(), checkOwnership.message());
            if (resource != null) {
                OwnershipContext.setResource(resource);
            }
            return joinPoint.proceed();
        } finally {
            OwnershipContext.clear();
        }
    }

    private Object checkAndGetResource(ResourceType resourceType, Long resourceId, Long userId,
                                        OwnershipRole role, String message) {
        return switch (resourceType) {
            case PET -> {
                Optional<Pet> opt = petRepository.findById(resourceId);
                if (opt.isEmpty()) {
                    yield null;
                }
                Pet pet = opt.get();
                if (!checkPetOwnership(pet, userId, role)) {
                    log.warn("所有权校验失败: userId={}, resourceType={}, resourceId={}, role={}",
                            userId, resourceType, resourceId, role);
                    throw BusinessException.forbidden(message);
                }
                yield pet;
            }
            case FOSTER_REQUEST -> {
                Optional<FosterRequest> opt = requestRepository.findById(resourceId);
                if (opt.isEmpty()) {
                    yield null;
                }
                FosterRequest request = opt.get();
                if (!checkFosterRequestOwnership(request, userId, role)) {
                    log.warn("所有权校验失败: userId={}, resourceType={}, resourceId={}, role={}",
                            userId, resourceType, resourceId, role);
                    throw BusinessException.forbidden(message);
                }
                yield request;
            }
            case DAILY_LOG -> {
                Optional<FosterDailyLog> opt = logRepository.findById(resourceId);
                if (opt.isEmpty()) {
                    yield null;
                }
                FosterDailyLog dailyLog = opt.get();
                if (!checkDailyLogOwnership(dailyLog, userId, role)) {
                    log.warn("所有权校验失败: userId={}, resourceType={}, resourceId={}, role={}",
                            userId, resourceType, resourceId, role);
                    throw BusinessException.forbidden(message);
                }
                yield dailyLog;
            }
        };
    }

    private Long resolveLong(EvaluationContext context, String expression) {
        Object value = parser.parseExpression(expression).getValue(context);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean checkPetOwnership(Pet pet, Long userId, OwnershipRole role) {
        return switch (role) {
            case OWNER -> pet.getOwnerId().equals(userId);
            default -> false;
        };
    }

    private boolean checkFosterRequestOwnership(FosterRequest request, Long userId, OwnershipRole role) {
        return switch (role) {
            case OWNER -> request.getOwnerId().equals(userId);
            case FOSTERER -> request.getFostererId() != null && request.getFostererId().equals(userId);
            case OWNER_OR_FOSTERER ->
                    request.getOwnerId().equals(userId)
                    || (request.getFostererId() != null && request.getFostererId().equals(userId));
        };
    }

    private boolean checkDailyLogOwnership(FosterDailyLog dailyLog, Long userId, OwnershipRole role) {
        return switch (role) {
            case FOSTERER -> dailyLog.getFostererId().equals(userId);
            default -> false;
        };
    }
}
