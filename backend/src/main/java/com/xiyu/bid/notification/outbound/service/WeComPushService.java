package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
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

import java.util.List;
import java.util.Optional;

/**
 * 站内通知镜像到企微的编排。企微传输委托给独立能力 {@link WecomMessageSender}
 * （按工号、走 CRM /common/sendMessage、flag=3），不再直连企微 API。
 *
 * <p>收件人解析使用 User.employeeNumber（工号）。投递任务/重试/DLQ 由
 * {@code NotificationDeliveryJobService} 负责，本类只做单次推送并返回结果。
 */
@Service
public class WeComPushService {

    private static final Logger log = LoggerFactory.getLogger(WeComPushService.class);

    private final UserRepository userRepository;
    private final WecomMessageSender wecomMessageSender;
    private final String platformBaseUrl;

    public WeComPushService(
        UserRepository userRepository,
        WecomMessageSender wecomMessageSender,
        @Value("${app.platform.base-url:http://localhost:1314}") String platformBaseUrl
    ) {
        this.userRepository = userRepository;
        this.wecomMessageSender = wecomMessageSender;
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
            command.title(), command.type(), command.sourceEntityType(), command.sourceEntityId(), platformBaseUrl);
        String content = message.description() + "\n" + message.url();

        try {
            WecomSendResult result = wecomMessageSender.send(List.of(employeeNumber), message.title(), content);
            return result.success()
                ? NotificationDeliveryResult.success(result.code(), result.message())
                : NotificationDeliveryResult.failure(result.code(), result.message());
        } catch (RuntimeException e) {
            log.warn("Wecom send failed for employee {}: {}", employeeNumber, e.getMessage());
            throw e;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
