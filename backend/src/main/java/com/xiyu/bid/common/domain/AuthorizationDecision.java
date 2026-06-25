package com.xiyu.bid.common.domain;

/**
 * 通用授权决策值对象。
 * <p>所有 Policy 层的权限判断统一返回此类型，避免各模块重复定义同构的 Decision record。</p>
 *
 * @param allowed 是否允许
 * @param reason  拒绝原因（允许时可为 null 或描述性文字）
 */
public record AuthorizationDecision(boolean allowed, String reason) {

    public static AuthorizationDecision permit() {
        return new AuthorizationDecision(true, null);
    }

    public static AuthorizationDecision permit(String reason) {
        return new AuthorizationDecision(true, reason);
    }

    public static AuthorizationDecision deny(String reason) {
        return new AuthorizationDecision(false, reason);
    }
}
