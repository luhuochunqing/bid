package com.xiyu.bid.tender.core;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标讯管理类操作权限注解
 * <p>
 * 用于标讯管理相关操作，包括编辑、删除、状态筛选、统计等。
 * 允许角色：ADMIN、MANAGER、BID_TEAMLEADER、BIDADMIN
 * </p>
 *
 * @see PreAuthorize
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN')")
public @interface TenderManagePermission {
}
