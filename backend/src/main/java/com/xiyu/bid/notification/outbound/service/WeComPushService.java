package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.application.WeComCredentialCipher;
import com.xiyu.bid.integration.application.WeComMessagePublisher;
import com.xiyu.bid.integration.domain.WeComSendResult;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.notification.outbound.application.NotificationDeliveryCommand;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter.FormattedMessage;
import com.xiyu.bid.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WeComPushService {

    private static final long SINGLETON_ID = 1L;
    private static final Logger log = LoggerFactory.getLogger(WeComPushService.class);

    private final UserRepository userRepository;
    private final WeComIntegrationJpaRepository integrationRepository;
    private final WeComCredentialCipher cipher;
    private final WeComMessagePublisher publisher;
    private final String platformBaseUrl;

    public WeComPushService(
        UserRepository userRepository,
        WeComIntegrationJpaRepository integrationRepository,
        WeComCredentialCipher cipher,
        WeComMessagePublisher publisher,
        @Value("${app.platform.base-url:http://localhost:1314}") String platformBaseUrl
    ) {
        this.userRepository = userRepository;
        this.integrationRepository = integrationRepository;
        this.cipher = cipher;
        this.publisher = publisher;
        this.platformBaseUrl = platformBaseUrl;
    }

    public NotificationDeliveryResult pushForRecipient(NotificationCreatedEvent event, Long recipientUserId) {
        return push(NotificationDeliveryCommand.fromEvent(event, recipientUserId));
    }

    public NotificationDeliveryResult push(NotificationDeliveryCommand command) {
        Optional<WeComIntegrationEntity> integrationOpt = integrationRepository.findById(SINGLETON_ID);
        if (integrationOpt.isEmpty() || !integrationOpt.get().isMessageEnabled()) {
            return NotificationDeliveryResult.skip("integration disabled");
        }

        Optional<User> userOpt = userRepository.findById(command.recipientUserId());
        if (userOpt.isEmpty() || isBlank(userOpt.get().getWecomUserId())) {
            return NotificationDeliveryResult.skip("recipient not bound");
        }

        WeComIntegrationEntity integration = integrationOpt.get();
        String wecomUserId = userOpt.get().getWecomUserId();
        FormattedMessage message = WeComMessageFormatter.format(
            command.title(), command.type(), command.sourceEntityType(), command.sourceEntityId(), platformBaseUrl);

        WeComSendResult result = trySend(integration, wecomUserId, message);
        return result.success()
                ? NotificationDeliveryResult.success(result.errcode(), result.errmsg())
                : NotificationDeliveryResult.failure(result.errcode(), result.errmsg());
    }

    private WeComSendResult trySend(WeComIntegrationEntity integration, String wecomUserId, FormattedMessage message) {
        try {
            String plainSecret = cipher.decrypt(integration.getEncryptedSecret());
            String content = assembleContent(message);
            return publisher.sendTextMessage(
                integration.getCorpId(),
                integration.getAgentId(),
                plainSecret,
                List.of(wecomUserId),
                content
            );
        } catch (RuntimeException e) {
            log.warn("WeCom push failed for user {}: {}", wecomUserId, e.getMessage());
            throw e;
        }
    }

    private static String assembleContent(FormattedMessage message) {
        return message.title() + "\n" + message.description() + "\n" + message.url();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
