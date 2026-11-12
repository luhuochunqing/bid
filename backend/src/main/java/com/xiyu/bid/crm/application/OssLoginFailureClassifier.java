package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.exception.ExternalServiceException;
import com.xiyu.bid.exception.RoleNotAuthorizedException;

/**
 * 将 OSS /oauth/login 的失败响应翻译为准确的登录异常。
 * <p>
 * 背景：OSS 对"密码正确但账号未授权访问本系统"返回「没有权限访问该系统」，
 * 若与密码错误一样按 BadCredentials 处理，前端会误报「密码错误，请重新输入」。
 */
final class OssLoginFailureClassifier {

    private static final String SYSTEM_NAME = "bid-platform";

    private OssLoginFailureClassifier() {
    }

    /**
     * OSS 明确拒绝系统访问时抛 403 ROLE_NOT_AUTHORIZED；
     * OSS 网络/响应解析失败时抛 502 外部服务异常；
     * 其余（凭据错误）不抛，由调用方按密码错误处理。
     */
    static void throwIfNotCredentialFailure(CrmResponseHandler.CrmApiResponse response) {
        String ossMessage = response.msg() == null ? "" : response.msg();
        if (ossMessage.contains("密码错误")) {
            return;
        }
        if (ossMessage.contains("没有权限访问")) {
            throw new RoleNotAuthorizedException(
                    "OSS 未授权访问投标平台（system=" + SYSTEM_NAME + "）: " + ossMessage);
        }
        if (response.code() == -1) {
            throw ExternalServiceException.forService("OSS 统一认证", -1,
                    "OSS 统一认证服务暂时不可用，请稍后重试", ossMessage, null);
        }
    }
}
