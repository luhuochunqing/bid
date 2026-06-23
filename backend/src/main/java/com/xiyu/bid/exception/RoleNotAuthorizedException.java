package com.xiyu.bid.exception;

import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * 角色未授权异常：用户 OSS 角色不在登录白名单中。
 * 与普通认证失败区分，返回 403 而非 401。
 */
public class RoleNotAuthorizedException extends InsufficientAuthenticationException {
    public RoleNotAuthorizedException(String msg) {
        super(msg);
    }
}
