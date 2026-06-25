package com.xiyu.bid.tender.core;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标讯查看类操作权限注解
 * <p>
 * 用于标讯查看相关操作，包括列表、详情等。
 * 允许角色：ADMIN、MANAGER、BID_TEAMLEADER、BIDADMIN、BID_PROJECTLEADER、BID_TEAM
 * </p>
 *
 * @see PreAuthorize
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")
public @interface TenderViewPermission {
}
