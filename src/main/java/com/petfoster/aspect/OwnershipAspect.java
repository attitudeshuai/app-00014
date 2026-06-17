package com.petfoster.aspect;

import com.petfoster.annotation.RequireOwner;
import com.petfoster.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OwnershipAspect {

    private final List<OwnershipResolver> resolvers;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(requireOwner)")
    public Object enforceOwnership(ProceedingJoinPoint joinPoint, RequireOwner requireOwner) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        EvaluationContext context = buildContext(method, joinPoint.getArgs());

        Long resourceId = evaluate(requireOwner.idParam(), context);
        Long userId = evaluate(requireOwner.userIdParam(), context);

        if (resourceId == null) {
            throw BusinessException.badRequest("资源ID不能为空");
        }

        OwnershipResolver resolver = resolvers.stream()
                .filter(r -> r.supports(requireOwner.resource(), requireOwner.role()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "未找到 OwnershipResolver: resource=" + requireOwner.resource()
                                + ", role=" + requireOwner.role()));

        if (!resolver.isOwnerMatch(resourceId, userId)) {
            throw BusinessException.forbidden(requireOwner.message());
        }

        return joinPoint.proceed();
    }

    private EvaluationContext buildContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = paramNameDiscoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return context;
    }

    private Long evaluate(String expression, EvaluationContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String spel = expression.startsWith("#") ? expression : "#" + expression;
        Object value = parser.parseExpression(spel).getValue(context);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("所有权表达式 '" + expression + "' 未解析为数值类型");
    }
}
