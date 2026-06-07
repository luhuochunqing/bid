// Input: NotificationCreatedEvent recipients; coordinates with User/WeCom integration
// Output: per-recipient OutboundLog rows + best-effort WeCom push
// Pos: Service/企微推送编排服务（<200 行，Split-First）
package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.application.WeComCredentialCipher;
import com.xiyu.bid.integration.application.WeComMessagePublisher;
import com.xiyu.bid.integration.domain.WeComSendResult;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.notification.outbound.core.OutboundChannel;
import com.xiyu.bid.notification.outbound.core.OutboundStatus;
import com.xiyu.bid.notification.outbound.core.SkipReason;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter.FormattedMessage;
import com.xiyu.bid.notification.outbound.entity.OutboundLog;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.repository.OutboundLogRepository;
import com.xiyu.bid.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WeComPushService {

    private static final long SINGLETON_ID = 1L;
    private static final int MAX_ERRMSG_LENGTH = 500;
    private static final Logger log = LoggerFactory.getLogger(WeComPushService.class);

    private final UserRepository userRepository;
    private final WeComIntegrationJpaRepository integrationRepository;
    private final WeComCredentialCipher cipher;
    private final WeComMessagePublisher publisher;
    private final OutboundLogRepository logRepository;
    private final String platformBaseUrl;

    public WeComPushService(
        UserRepository userRepository,
        WeComIntegrationJpaRepository integrationRepository,
        WeComCredentialCipher cipher,
        WeComMessagePublisher publisher,
        OutboundLogRepository logRepository,
        @Value("${app.platform.base-url:http://localhost:1314}") String platformBaseUrl
    ) {
        this.userRepository = userRepository;
        this.integrationRepository = integrationRepository;
        this.cipher = cipher;
        this.publisher = publisher;
        this.logRepository = logRepository;
        this.platformBaseUrl = platformBaseUrl;
    }

    @Transactional
    public void pushForRecipient(NotificationCreatedEvent event, Long recipientUserId) {
        Optional<WeComIntegrationEntity> integrationOpt = integrationRepository.findById(SINGLETON_ID);
        if (integrationOpt.isEmpty() || !integrationOpt.get().isMessageEnabled()) {
            writeSkipped(event.notificationId(), recipientUserId, SkipReason.DISABLED);
            return;
        }

        Optional<User> userOpt = userRepository.findById(recipientUserId);
        if (userOpt.isEmpty() || isBlank(userOpt.get().getWecomUserId())) {
            writeSkipped(event.notificationId(), recipientUserId, SkipReason.NOT_BOUND);
            return;
        }

        WeComIntegrationEntity integration = integrationOpt.get();
        String wecomUserId = userOpt.get().getWecomUserId();
        FormattedMessage message = WeComMessageFormatter.format(
            event.title(), event.type(), event.sourceEntityType(), event.sourceEntityId(), platformBaseUrl);

        WeComSendResult result = trySend(integration, wecomUserId, message);
        writeResult(event.notificationId(), recipientUserId, result);
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
            return new WeComSendResult(false, -1, truncate(e.getMessage()), List.of(wecomUserId));
        }
    }

    private static String assembleContent(FormattedMessage message) {
        return message.title() + "\n" + message.description() + "\n" + message.url();
    }

    private void writeSkipped(Long notificationId, Long userId, SkipReason reason) {
        logRepository.save(OutboundLog.builder()
            .notificationId(notificationId)
            .userId(userId)
            .channel(OutboundChannel.WECOM)
            .status(OutboundStatus.SKIPPED)
            .skipReason(reason)
            .attemptCount(1)
            .build());
    }

    private void writeResult(Long notificationId, Long userId, WeComSendResult result) {
        OutboundLog.OutboundLogBuilder builder = OutboundLog.builder()
            .notificationId(notificationId)
            .userId(userId)
            .channel(OutboundChannel.WECOM)
            .status(result.success() ? OutboundStatus.SENT : OutboundStatus.FAILED)
            .wecomErrcode(result.errcode())
            .wecomErrmsg(truncate(result.errmsg()))
            .attemptCount(1);
        if (!result.success()) {
            builder.skipReason(SkipReason.ERROR);
        }
        logRepository.save(builder.build());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_ERRMSG_LENGTH ? s : s.substring(0, MAX_ERRMSG_LENGTH);
    }
}
