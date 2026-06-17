package com.petfoster.common;

import java.util.HashMap;
import java.util.Map;

public class OwnershipContext {

    private static final ThreadLocal<Map<Class<?>, Object>> RESOURCES =
            ThreadLocal.withInitial(HashMap::new);

    private OwnershipContext() {}

    @SuppressWarnings("unchecked")
    public static <T> T getResource(Class<T> clazz) {
        Object resource = RESOURCES.get().get(clazz);
        return resource != null ? (T) resource : null;
    }

    public static void setResource(Object resource) {
        if (resource != null) {
            RESOURCES.get().put(resource.getClass(), resource);
        }
    }

    public static void clear() {
        RESOURCES.remove();
    }
}
