package com.petfoster.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireOwner {

    ResourceType resource();

    String idParam() default "#id";

    String userIdParam() default "#userId";

    String role() default "";

    String message() default "无权限操作此资源";
}
