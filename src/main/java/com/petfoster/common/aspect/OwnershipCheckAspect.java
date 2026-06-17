package com.petfoster.common.aspect;

import com.petfoster.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class OwnershipCheckAspect {

    private final Map<ResourceType, OwnershipChecker> checkerMap;
    private final SpelExpressionParser parser;

    public OwnershipCheckAspect(List<OwnershipChecker> checkers) {
        this.checkerMap = new EnumMap<>(ResourceType.class);
        for (OwnershipChecker checker : checkers) {
            checkerMap.put(checker.getResourceType(), checker);
        }
        this.parser = new SpelExpressionParser();
        log.info("所有权校验切面初始化完成，共注册 {} 个资源检查器", checkerMap.size());
    }

    @Before("@annotation(checkOwnership)")
    public void checkOwnership(JoinPoint joinPoint, CheckOwnership checkOwnership) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        Long userId = evaluateLongExpression(context, checkOwnership.userIdExpression());
        Long resourceId = evaluateLongExpression(context, checkOwnership.idExpression());

        if (userId == null || resourceId == null) {
            log.warn("所有权校验参数缺失: userId={}, resourceId={}, method={}",
                    userId, resourceId, method.getName());
            throw BusinessException.forbidden(checkOwnership.message());
        }

        OwnershipChecker checker = checkerMap.get(checkOwnership.resourceType());
        if (checker == null) {
            log.error("未找到资源类型 {} 对应的所有权检查器", checkOwnership.resourceType());
            throw BusinessException.forbidden(checkOwnership.message());
        }

        checker.checkOwnership(resourceId, userId, checkOwnership.role());
    }

    private Long evaluateLongExpression(EvaluationContext context, String expression) {
        try {
            Expression exp = parser.parseExpression(expression);
            Object value = exp.getValue(context);
            if (value instanceof Long) {
                return (Long) value;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return null;
        } catch (Exception e) {
            log.warn("SpEL表达式解析失败: expression={}, error={}", expression, e.getMessage());
            return null;
        }
    }
}
