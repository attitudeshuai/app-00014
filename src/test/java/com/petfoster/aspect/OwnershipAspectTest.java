package com.petfoster.aspect;

import com.petfoster.annotation.RequireOwner;
import com.petfoster.common.BusinessException;
import com.petfoster.dto.FosterRequestDTO;
import com.petfoster.service.FosterRequestService;
import com.petfoster.service.PetService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipAspectTest {

    @Mock
    private OwnershipResolver resolver;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private OwnershipAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new OwnershipAspect(List.of(resolver));
    }

    private RequireOwner annotationOf(Method method) {
        return method.getAnnotation(RequireOwner.class);
    }

    private void stubJoinPoint(Method method, Object[] args) {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);
        when(resolver.supports(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("归属匹配 - 放行并执行原方法")
    void shouldProceedWhenOwnerMatches() throws Throwable {
        Method method = PetService.class.getMethod("deletePet", Long.class, Long.class);
        stubJoinPoint(method, new Object[]{1L, 1L});
        when(resolver.isOwnerMatch(1L, 1L)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("deleted");

        Object result = aspect.enforceOwnership(joinPoint, annotationOf(method));

        assertEquals("deleted", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("归属不匹配 - 抛出403且不执行原方法")
    void shouldForbiddenWhenOwnerMismatch() throws Throwable {
        Method method = PetService.class.getMethod("deletePet", Long.class, Long.class);
        stubJoinPoint(method, new Object[]{2L, 1L});
        when(resolver.isOwnerMatch(1L, 2L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.enforceOwnership(joinPoint, annotationOf(method)));

        assertEquals("无权限删除此宠物", ex.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("资源不存在 - 透传 notFound")
    void shouldPropagateNotFound() throws Throwable {
        Method method = PetService.class.getMethod("deletePet", Long.class, Long.class);
        stubJoinPoint(method, new Object[]{1L, 1L});
        when(resolver.isOwnerMatch(1L, 1L)).thenThrow(BusinessException.notFound("宠物不存在"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.enforceOwnership(joinPoint, annotationOf(method)));

        assertEquals("宠物不存在", ex.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("isOwnerMatch返回false - 抛出403")
    void shouldForbiddenWhenIsOwnerMatchReturnsFalse() throws Throwable {
        Method method = PetService.class.getMethod("deletePet", Long.class, Long.class);
        stubJoinPoint(method, new Object[]{1L, 1L});
        when(resolver.isOwnerMatch(1L, 1L)).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> aspect.enforceOwnership(joinPoint, annotationOf(method)));
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("嵌套SpEL - 从DTO中解析资源ID")
    void shouldResolveNestedSpelId() throws Throwable {
        Method method = FosterRequestService.class.getMethod(
                "createRequest", Long.class, FosterRequestDTO.CreateRequest.class);
        FosterRequestDTO.CreateRequest req = new FosterRequestDTO.CreateRequest();
        req.setPetId(5L);

        stubJoinPoint(method, new Object[]{1L, req});
        when(resolver.isOwnerMatch(5L, 1L)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("created");

        Object result = aspect.enforceOwnership(joinPoint, annotationOf(method));

        assertEquals("created", result);
        verify(resolver).isOwnerMatch(5L, 1L);
    }

    @Test
    @DisplayName("PARTICIPANT角色 - isOwnerMatch覆盖OR逻辑")
    void shouldSupportParticipantRoleWithOrLogic() throws Throwable {
        Method method = FosterRequestService.class.getMethod(
                "updateStatus", Long.class, Long.class, com.petfoster.entity.FosterRequest.Status.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{20L, 1L, com.petfoster.entity.FosterRequest.Status.Approved});
        when(resolver.supports(any(), eq("PARTICIPANT"))).thenReturn(true);
        when(resolver.isOwnerMatch(1L, 20L)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("updated");

        RequireOwner annotation = method.getAnnotation(RequireOwner.class);
        Object result = aspect.enforceOwnership(joinPoint, annotation);

        assertEquals("updated", result);
        verify(resolver).isOwnerMatch(1L, 20L);
    }
}
