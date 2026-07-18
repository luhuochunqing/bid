package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.notification.outbound.application.NotificationDeliveryCommand;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter.FormattedMessage;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter.WeComSsoParams;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.wecom.WecomMessageSender;
import com.xiyu.bid.wecom.WecomSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 站内通知镜像到企微的编排。企微传输委托给独立能力 {@link WecomMessageSender}
 * （按工号、走西域统一消息中心 {@code /qywx/sendMSG}），不再直连企微 API。
 *
 * <p>收件人解析使用 User.employeeNumber（工号）。投递任务/重试/DLQ 由
 * {@code NotificationDeliveryJobService} 负责，本类只做单次推送并返回结果。
 *
 * <p>SSO 集成：若 {@link WeComIntegrationEntity#isSsoEnabled()} 为 true，
 * 消息中的 URL 会构造为 OAuth 授权链接（而非直接业务 URL），
 * 用户点击消息后会走企微 OAuth 静默授权，自动登录并跳回原目标页面。
 */
@Service
public class WeComPushService {

    private static final Logger log = LoggerFactory.getLogger(WeComPushService.class);

    private final UserRepository userRepository;
    private final WecomMessageSender wecomMessageSender;
    private final WeComIntegrationJpaRepository integrationRepository;
    private final String platformBaseUrl;

    public WeComPushService(
        UserRepository userRepository,
        WecomMessageSender wecomMessageSender,
        WeComIntegrationJpaRepository integrationRepository,
        @Value("${app.platform.base-url:http://localhost:1314}") String platformBaseUrl
    ) {
        this.userRepository = userRepository;
        this.wecomMessageSender = wecomMessageSender;
        this.integrationRepository = integrationRepository;
        this.platformBaseUrl = platformBaseUrl;
    }

    public NotificationDeliveryResult pushForRecipient(NotificationCreatedEvent event, Long recipientUserId) {
        return push(NotificationDeliveryCommand.fromEvent(event, recipientUserId));
    }

    public NotificationDeliveryResult push(NotificationDeliveryCommand command) {
        Optional<User> userOpt = userRepository.findById(command.recipientUserId());
        if (userOpt.isEmpty() || isBlank(userOpt.get().getEmployeeNumber())) {
            return NotificationDeliveryResult.skip("recipient has no employee number");
        }

        String employeeNumber = userOpt.get().getEmployeeNumber();
        WeComSsoParams ssoParams = resolveSsoParams();
        FormattedMessage message = WeComMessageFormatter.format(
            command.title(), command.type(), command.sourceEntityType(), command.sourceEntityId(),
            platformBaseUrl, command.targetUrl(), ssoParams);
        String body = message.title() + "\n" + message.description() + "\n<a href=\"" + message.url() + "\">" + message.btnText() + "</a>";

        try {
            WecomSendResult result = wecomMessageSender.send(employeeNumber, body);
            return result.success()
                ? NotificationDeliveryResult.success(result.code(), result.message())
                : NotificationDeliveryResult.failure(result.code(), result.message());
        } catch (RuntimeException e) {
            log.warn("Wecom send failed for employee {}: {}", employeeNumber, e.getMessage());
            throw e;
        }
    }

    /**
     * 从 wecom_integration 表读取 SSO 配置。
     * 未启用 SSO 或配置不全时返回 null（向后兼容，构造直接业务 URL）。
     */
    private WeComSsoParams resolveSsoParams() {
        return integrationRepository.findById(1L)
            .filter(WeComIntegrationEntity::isSsoEnabled)
            .map(entity -> new WeComSsoParams(entity.getCorpId(), entity.getAgentId()))
            .filter(WeComSsoParams::isValid)
            .orElse(null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
