package com.imageaiplatform.auth.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 값을 생략하면 활성 계정 인증만 요구하고, 지정하면 해당 세부 권한도 요구합니다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value() default "";
}
