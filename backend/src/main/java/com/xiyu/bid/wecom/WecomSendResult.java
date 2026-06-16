package com.xiyu.bid.wecom;

/**
 * 企微消息发送结果。独立于 notification 模块的自有结果类型。
 *
 * @param success CRM /common/sendMessage 是否成功
 * @param code    CRM 响应 code（成功时通常为 0）
 * @param message CRM 响应 msg 或本地错误描述
 */
public record WecomSendResult(boolean success, int code, String message) {

    public static WecomSendResult success(int code, String message) {
        return new WecomSendResult(true, code, message);
    }

    public static WecomSendResult failure(int code, String message) {
        return new WecomSendResult(false, code, message);
    }
}
