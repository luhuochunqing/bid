// Input: Controller 方法标记
// Output: 注解元数据
// Pos: Idempotency/请求幂等层
// 维护声明: 仅维护注解契约；实际拦截逻辑见 IdempotencyFilter。
package com.xiyu.bid.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    long ttlSeconds() default 600L;
}
