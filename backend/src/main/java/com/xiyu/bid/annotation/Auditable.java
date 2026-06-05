package com.xiyu.bid.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作记录注解
 * 标记需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 操作类型
     */
    String action() default "OPERATION";

    /**
     * 实体类型
     */
    String entityType() default "";

    /**
     * 操作描述
     */
    String description() default "";
}
