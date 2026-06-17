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
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnershipCheckAspectTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private FosterRequestRepository requestRepository;

    @Mock
    private FosterDailyLogRepository logRepository;

    @InjectMocks
    private OwnershipCheckAspect aspect;

    private Pet testPet;
    private FosterRequest testRequest;
    private FosterDailyLog testLog;

    @BeforeEach
    void setUp() {
        testPet = Pet.builder()
                .id(10L)
                .ownerId(100L)
                .name("小白")
                .build();

        testRequest = FosterRequest.builder()
                .id(20L)
                .ownerId(100L)
                .fostererId(200L)
                .build();

        testLog = FosterDailyLog.builder()
                .id(30L)
                .fostererId(200L)
                .build();
    }

    @AfterEach
    void tearDown() {
        OwnershipContext.clear();
    }

    static class TestRequest {
        private Long petId;
        private Long requestId;

        public Long getPetId() { return petId; }
        public void setPetId(Long petId) { this.petId = petId; }
        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
    }

    private ProceedingJoinPoint mockJoinPoint(Method method, Object[] args, Object returnValue) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(getParameterNames(method));
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(returnValue);
        return joinPoint;
    }

    private String[] getParameterNames(Method method) {
        int paramCount = method.getParameterCount();
        String[] names = new String[paramCount];
        if (paramCount > 0) names[0] = "userId";
        if (paramCount > 1) {
            Class<?> param1Type = method.getParameterTypes()[1];
            if (param1Type == Long.class) {
                names[1] = "id";
            } else {
                names[1] = "req";
            }
        }
        return names;
    }

    private CheckOwnership createAnnotation(ResourceType resourceType, OwnershipRole role,
                                            String resourceIdExp, String message) {
        return new CheckOwnership() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return CheckOwnership.class;
            }
            @Override
            public ResourceType resourceType() { return resourceType; }
            @Override
            public String userIdExp() { return "#userId"; }
            @Override
            public String resourceIdExp() { return resourceIdExp; }
            @Override
            public OwnershipRole role() { return role; }
            @Override
            public String message() { return message; }
        };
    }

    @Test
    @DisplayName("宠物所有者校验 - 成功，资源存入上下文，方法执行后清理")
    void testPetOwnerCheck_Success_ContextSet() throws Throwable {
        when(petRepository.findById(10L)).thenReturn(Optional.of(testPet));

        Method method = getClass().getDeclaredMethod(
                "dummySimpleMethod", Long.class, Long.class);
        Object[] args = new Object[]{100L, 10L};
        Object expectedResult = "result";
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, args, expectedResult);

        CheckOwnership annotation = createAnnotation(
                ResourceType.PET, OwnershipRole.OWNER, "#id", "无权限");

        assertNull(OwnershipContext.getResource(Pet.class), "执行前上下文应为空");

        Object result = aspect.checkOwnership(joinPoint, annotation);

        assertEquals(expectedResult, result);
        verify(joinPoint).proceed();

        assertNull(OwnershipContext.getResource(Pet.class), "执行后上下文应被清理");
        verify(petRepository, times(1)).findById(10L);
    }

    @Test
    @DisplayName("宠物所有者校验 - 失败，不执行目标方法，上下文干净")
    void testPetOwnerCheck_Forbidden_NoProceed() throws Throwable {
        when(petRepository.findById(10L)).thenReturn(Optional.of(testPet));

        Method method = getClass().getDeclaredMethod(
                "dummySimpleMethod", Long.class, Long.class);
        Object[] args = new Object[]{999L, 10L};
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, args, "result");

        CheckOwnership annotation = createAnnotation(
                ResourceType.PET, OwnershipRole.OWNER, "#id", "无权限修改此宠物");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aspect.checkOwnership(joinPoint, annotation));

        assertEquals("无权限修改此宠物", exception.getMessage());
        assertEquals(403, exception.getCode());
        verify(joinPoint, never()).proceed();
        assertNull(OwnershipContext.getResource(Pet.class));
    }

    @Test
    @DisplayName("宠物所有者校验 - 从请求对象中取 ID - 成功")
    void testPetOwnerCheck_SpelNested_Success() throws Throwable {
        when(petRepository.findById(10L)).thenReturn(Optional.of(testPet));

        Method method = getClass().getDeclaredMethod(
                "dummyRequestMethod", Long.class, TestRequest.class);
        TestRequest req = new TestRequest();
        req.setPetId(10L);
        Object[] args = new Object[]{100L, req};
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, args, "ok");

        CheckOwnership annotation = createAnnotation(
                ResourceType.PET, OwnershipRole.OWNER, "#req.petId", "无权限");

        Object result = aspect.checkOwnership(joinPoint, annotation);

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("寄养申请所有者或寄养人校验 - 双方都能通过")
    void testFosterRequestOwnerOrFosterer_Success() throws Throwable {
        when(requestRepository.findById(20L)).thenReturn(Optional.of(testRequest));

        Method method = getClass().getDeclaredMethod(
                "dummySimpleMethod", Long.class, Long.class);

        CheckOwnership annotation = createAnnotation(
                ResourceType.FOSTER_REQUEST, OwnershipRole.OWNER_OR_FOSTERER, "#id", "无权限");

        Object[] argsOwner = new Object[]{100L, 20L};
        ProceedingJoinPoint jpOwner = mockJoinPoint(method, argsOwner, "owner-ok");
        assertEquals("owner-ok", aspect.checkOwnership(jpOwner, annotation));

        Object[] argsFosterer = new Object[]{200L, 20L};
        ProceedingJoinPoint jpFosterer = mockJoinPoint(method, argsFosterer, "fosterer-ok");
        assertEquals("fosterer-ok", aspect.checkOwnership(jpFosterer, annotation));
    }

    @Test
    @DisplayName("寄养申请寄养人校验 - 从请求对象中取 requestId")
    void testFosterRequestFosterer_NestedId_Success() throws Throwable {
        when(requestRepository.findById(20L)).thenReturn(Optional.of(testRequest));

        Method method = getClass().getDeclaredMethod(
                "dummyRequestMethod", Long.class, TestRequest.class);
        TestRequest req = new TestRequest();
        req.setRequestId(20L);
        Object[] args = new Object[]{200L, req};
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, args, "ok");

        CheckOwnership annotation = createAnnotation(
                ResourceType.FOSTER_REQUEST, OwnershipRole.FOSTERER, "#req.requestId", "只有寄养人才能操作");

        assertEquals("ok", aspect.checkOwnership(joinPoint, annotation));
    }

    @Test
    @DisplayName("日报寄养人校验 - 成功")
    void testDailyLogFostererCheck_Success() throws Throwable {
        when(logRepository.findById(30L)).thenReturn(Optional.of(testLog));

        Method method = getClass().getDeclaredMethod(
                "dummySimpleMethod", Long.class, Long.class);
        Object[] args = new Object[]{200L, 30L};
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, args, "ok");

        CheckOwnership annotation = createAnnotation(
                ResourceType.DAILY_LOG, OwnershipRole.FOSTERER, "#id", "无权限");

        assertEquals("ok", aspect.checkOwnership(joinPoint, annotation));
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("资源不存在时跳过校验，继续执行方法")
    void testResourceNotFound_SkipCheck() throws Throwable {
        when(petRepository.findById(999L)).thenReturn(Optional.empty());

        Method method = getClass().getDeclaredMethod(
                "dummySimpleMethod", Long.class, Long.class);
        Object[] args = new Object[]{999L, 999L};
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, args, "not-found-ok");

        CheckOwnership annotation = createAnnotation(
                ResourceType.PET, OwnershipRole.OWNER, "#id", "无权限");

        assertEquals("not-found-ok", aspect.checkOwnership(joinPoint, annotation));
        verify(joinPoint).proceed();
        assertNull(OwnershipContext.getResource(Pet.class));
    }

    @Test
    @DisplayName("OwnershipContext - 存取和清理")
    void testOwnershipContext_SetGetClear() {
        assertNull(OwnershipContext.getResource(Pet.class));

        Pet pet = Pet.builder().id(99L).build();
        OwnershipContext.setResource(pet);

        Pet cached = OwnershipContext.getResource(Pet.class);
        assertNotNull(cached);
        assertEquals(99L, cached.getId());

        OwnershipContext.clear();
        assertNull(OwnershipContext.getResource(Pet.class));
    }

    void dummySimpleMethod(Long userId, Long id) {}
    void dummyRequestMethod(Long userId, TestRequest req) {}
}
