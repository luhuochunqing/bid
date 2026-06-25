package com.xiyu.bid.tender.core;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标讯决策类操作权限注解
 * <p>
 * 用于标讯决策相关操作，包括投标决策、弃标决策、审核等。
 * 允许角色：ADMIN、BID_TEAMLEADER、BIDADMIN
 * </p>
 *
 * @see PreAuthorize
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')")
public @interface TenderDecisionPermission {
}
