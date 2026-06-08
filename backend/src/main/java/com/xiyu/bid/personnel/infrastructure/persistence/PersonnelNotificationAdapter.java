package com.xiyu.bid.personnel.infrastructure.persistence;

import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelNotificationPort;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonnelNotificationAdapter implements PersonnelNotificationPort {

    private final NotificationApplicationService notificationService;

    @Override
    public void notifyCertificateExpiry(List<Personnel> personnelWithExpiringCerts, int warningDays) {
        for (Personnel p : personnelWithExpiringCerts) {
            long expiringCount = p.certificates().stream()
                    .filter(c -> c.remainingDays() <= warningDays && !c.isExpired())
                    .count();
            String title = p.name() + " 的执业证书即将到期";
            String content = String.format("%s，您有 %d 个执业证书将在 %d 天内到期，请及时续期。",
                    p.name(), expiringCount, warningDays);
            CreateNotificationRequest request = new CreateNotificationRequest(
                    NotificationType.SYSTEM.name(), "PERSONNEL_CERT", p.id(),
                    title, content, null, List.of()
            );
            try {
                notificationService.createNotification(request, null);
            } catch (RuntimeException e) {
                log.warn("发送人员证书到期通知失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public void notifyCertificateExpired(Personnel personnel, List<Certificate> expiredCerts) {
        String names = expiredCerts.stream().map(Certificate::name).reduce((a, b) -> a + "、" + b).orElse("");
        String title = personnel.name() + " 的执业证书已过期";
        String content = String.format("%s，以下执业证书已过期，请立即处理：%s", personnel.name(), names);
        CreateNotificationRequest request = new CreateNotificationRequest(
                NotificationType.SYSTEM.name(), "PERSONNEL_CERT", personnel.id(),
                title, content, null, List.of()
        );
        try {
            notificationService.createNotification(request, null);
        } catch (RuntimeException e) {
            log.warn("发送证书过期通知失败: {}", e.getMessage());
        }
    }
}
