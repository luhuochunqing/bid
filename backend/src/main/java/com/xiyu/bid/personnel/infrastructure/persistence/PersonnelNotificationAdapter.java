package com.xiyu.bid.personnel.infrastructure.persistence;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelNotificationPort;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonnelNotificationAdapter implements PersonnelNotificationPort {

    private static final List<String> RECIPIENT_ROLES = List.of("/bidAdmin", "bid-TeamLeader");
    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    private final NotificationApplicationService notificationService;
    private final UserRepository userRepository;

    /** 24h 去重：key = "personnelId:certificateName" → lastSentTime */
    private final Map<String, Instant> dedupCache = new ConcurrentHashMap<>();

    @Override
    public void notifyCertificateExpiry(List<Personnel> personnelWithExpiringCerts, int warningDays) {
        List<Long> recipientIds = getRecipientUserIds();
        if (recipientIds.isEmpty()) {
            log.warn("无 bid_admin/bid_lead 用户，跳过证书到期通知");
            return;
        }

        int notifiedCount = 0;
        for (Personnel p : personnelWithExpiringCerts) {
            List<Certificate> expiringCerts = p.certificates().stream()
                    .filter(c -> c.remainingDays() <= warningDays && !c.isExpired() && !c.isPermanent())
                    .toList();
            if (expiringCerts.isEmpty()) continue;

            // 过滤 24h 内已通知的证书
            List<Certificate> newExpiring = expiringCerts.stream()
                    .filter(c -> !isNotifiedRecently(p.id(), c.name()))
                    .toList();
            if (newExpiring.isEmpty()) continue;

            String certNames = newExpiring.stream().map(Certificate::name).reduce((a, b) -> a + "、" + b).orElse("");
            String title = p.name() + " 的执业证书即将到期";
            String content = String.format("%s，以下执业证书将在 %d 天内到期，请及时续期：%s",
                    p.name(), warningDays, certNames);
            CreateNotificationRequest request = new CreateNotificationRequest(
                    NotificationType.SYSTEM.name(), "PERSONNEL_CERT", null,
                    title, content, null, recipientIds
            );
            try {
                notificationService.createNotification(request, null);
                // 记录去重标记
                for (Certificate c : newExpiring) {
                    dedupCache.put(buildDedupKey(p.id(), c.name()), Instant.now());
                }
                notifiedCount++;
            } catch (RuntimeException e) {
                log.warn("发送人员证书到期通知失败: {}", e.getMessage());
            }
        }
        log.info("证书到期通知发送完成，共 {} 人收到提醒", notifiedCount);
    }

    @Override
    public void notifyCertificateExpired(Personnel personnel, List<Certificate> expiredCerts) {
        List<Long> recipientIds = getRecipientUserIds();
        if (recipientIds.isEmpty()) {
            log.warn("无 bid_admin/bid_lead 用户，跳过证书过期通知");
            return;
        }

        String names = expiredCerts.stream().map(Certificate::name).reduce((a, b) -> a + "、" + b).orElse("");
        String title = personnel.name() + " 的执业证书已过期";
        String content = String.format("%s，以下执业证书已过期，请立即处理：%s", personnel.name(), names);
        CreateNotificationRequest request = new CreateNotificationRequest(
                NotificationType.SYSTEM.name(), "PERSONNEL_CERT", null,
                title, content, null, recipientIds
        );
        try {
            notificationService.createNotification(request, null);
        } catch (RuntimeException e) {
            log.warn("发送证书过期通知失败: {}", e.getMessage());
        }
    }

    /** 查询所有启用状态的 bid_admin / bid_lead 用户 ID */
    private List<Long> getRecipientUserIds() {
        return userRepository.findEnabledByRoleProfileCodes(RECIPIENT_ROLES).stream()
                .map(User::getId)
                .toList();
    }

    /** 检查某证书是否在 24h 内已发送过通知 */
    private boolean isNotifiedRecently(Long personnelId, String certificateName) {
        Instant lastSent = dedupCache.get(buildDedupKey(personnelId, certificateName));
        if (lastSent == null) return false;
        return Duration.between(lastSent, Instant.now()).compareTo(DEDUP_WINDOW) < 0;
    }

    private static String buildDedupKey(Long personnelId, String certificateName) {
        return personnelId + ":" + certificateName;
    }
}
