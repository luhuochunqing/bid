package com.xiyu.bid.wecom;

import com.xiyu.bid.wecom.infrastructure.WecomMessageCenterClient;
import com.xiyu.bid.wecom.infrastructure.WecomMessageCenterClient.MessageCenterResponse;
import org.springframework.stereotype.Service;

/**
 * 独立的企微消息发送能力。任何模块需要发企微时，按工号直接调用本服务。
 *
 * <p>传输走西域统一消息中心 {@code POST /qywx/sendMSG}，由消息中心侧用已登记的
 * 企微应用（corpId/agentId/secret）转发到企业微信。本服务只持有消息中心的
 * 应用标识 {@code code}，不接触企微凭据。
 *
 * <p>站内信不在本能力范围（由 notification 收件箱承担）。
 */
@Service
public class WecomMessageSender {

    /** 本地校验失败哨兵 code。 */
    private static final int CODE_VALIDATION = -1;

    private final WecomMessageCenterClient messageCenterClient;

    public WecomMessageSender(WecomMessageCenterClient messageCenterClient) {
        this.messageCenterClient = messageCenterClient;
    }

    /**
     * 按工号发送企微消息。
     *
     * @param userName 接收人工号
     * @param message  消息内容
     * @return 发送结果
     */
    public WecomSendResult send(String userName, String message) {
        return send(userName, message, null);
    }

    /** 按工号发送，并透传已知邮箱以供消息中心定位企业微信用户。 */
    public WecomSendResult send(String userName, String message, String email) {
        if (isBlank(userName)) {
            return WecomSendResult.failure(CODE_VALIDATION, "userName is empty");
        }
        if (isBlank(message)) {
            return WecomSendResult.failure(CODE_VALIDATION, "message is empty");
        }
        MessageCenterResponse response = email == null || email.isBlank()
                ? messageCenterClient.sendMessage(userName, message)
                : messageCenterClient.sendMessage(userName, message, email);
        return response.code() == 0
                ? WecomSendResult.success(response.code(), response.message())
                : WecomSendResult.failure(response.code(), response.message());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
