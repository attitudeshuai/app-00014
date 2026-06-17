package com.petfoster.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckOwnership {

    ResourceType resourceType();

    int idIndex();

    int userIdIndex();

    String idField() default "";

    OwnershipRole role();

    String errorMessage() default "无权限操作此资源";
}
