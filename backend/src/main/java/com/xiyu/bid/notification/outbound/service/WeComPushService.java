package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.auth.OAuthStateService;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.application.WeComIntegrationAppService;
import com.xiyu.bid.integration.application.WeComSsoConfig;
import com.xiyu.bid.notification.outbound.application.NotificationDeliveryCommand;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter.FormattedMessage;
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
 * <p>SSO 集成：若 {@link WeComIntegrationAppService#getSsoConfig()} 返回有效配置，
 * 消息中的 URL 会包装为 OAuth 授权链接（而非直接业务 URL），
 * 用户点击消息后会走企微 OAuth 静默授权，自动登录并跳回原目标页面。
 * <p>每条消息生成独立的 state（7 天 TTL，只验证不删除），避免 Session Fixation 风险。
 */
@Service
public class WeComPushService {

    private static final Logger log = LoggerFactory.getLogger(WeComPushService.class);

    private final UserRepository userRepository;
    private final WecomMessageSender wecomMessageSender;
    private final WeComIntegrationAppService integrationAppService;
    private final OAuthStateService oAuthStateService;
    private final String platformBaseUrl;

    public WeComPushService(
        UserRepository userRepository,
        WecomMessageSender wecomMessageSender,
        WeComIntegrationAppService integrationAppService,
        OAuthStateService oAuthStateService,
        @Value("${app.platform.base-url:http://localhost:1314}") String platformBaseUrl
    ) {
        this.userRepository = userRepository;
        this.wecomMessageSender = wecomMessageSender;
        this.integrationAppService = integrationAppService;
        this.oAuthStateService = oAuthStateService;
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
        FormattedMessage message = WeComMessageFormatter.format(
            command.title(), command.type(), command.sourceEntityType(), command.sourceEntityId(),
            platformBaseUrl, command.targetUrl());
        // SSO 启用时，将业务 URL 包装为 OAuth 授权链接（每条消息独立 state，防 Session Fixation）
        String finalUrl = wrapWithSsoIfNeeded(message.url());
        String body = message.title() + "\n" + message.description()
            + "\n<a href=\"" + finalUrl + "\">" + message.btnText() + "</a>";

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
     * 若 SSO 启用，将业务 URL 包装为 OAuth 授权链接；否则原样返回（向后兼容）。
     * <p>每条消息生成独立的 state（{@code msg:<uuid>}），由 OAuthStateService 存储 7 天，
     * 用户点击后 callback 校验 state 存在性（只验证不删除，允许同一条消息多次点击）。
     */
    private String wrapWithSsoIfNeeded(String businessUrl) {
        Optional<WeComSsoConfig> ssoConfigOpt = integrationAppService.getSsoConfig();
        if (ssoConfigOpt.isEmpty()) {
            return businessUrl;
        }
        WeComSsoConfig ssoConfig = ssoConfigOpt.get();
        String state = oAuthStateService.storeStateForMessage();
        String targetPath = extractPath(businessUrl);
        return ssoConfig.buildAuthorizeUrl(platformBaseUrl, targetPath, state);
    }

    /**
     * 从完整业务 URL 提取 path 部分（去掉 platformBaseUrl 前缀）。
     * <p>Vue Router router.push 只接受 path，不接受完整 URL。
     */
    private String extractPath(String businessUrl) {
        String normalizedBase = platformBaseUrl == null || platformBaseUrl.isBlank()
            ? "" : (platformBaseUrl.endsWith("/")
                ? platformBaseUrl.substring(0, platformBaseUrl.length() - 1)
                : platformBaseUrl);
        if (normalizedBase.isEmpty() && businessUrl.startsWith("/")) {
            return businessUrl;
        }
        if (businessUrl.startsWith(normalizedBase)) {
            return businessUrl.substring(normalizedBase.length());
        }
        // 兜底：URL 不以 base 开头时返回根路径（不应发生，防御性编程）
        return "/";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
