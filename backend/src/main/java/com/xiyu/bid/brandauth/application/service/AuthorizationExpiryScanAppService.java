package com.xiyu.bid.brandauth.application.service;

import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.brandauth.domain.service.AuthorizationExpiryPolicy;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationExpiryScanAppService {

    private static final int DEFAULT_WARNING_DAYS = 30;

    private final BrandAuthorizationRepository repository;
    private final NotificationApplicationService notificationService;
    private final AuthorizationExpiryPolicy expiryPolicy = new AuthorizationExpiryPolicy();

    @Scheduled(cron = "0 0 9 * * ?")
    public void scanAndNotify() {
        scanAndNotify(DEFAULT_WARNING_DAYS);
    }

    public int scanAndNotify(int warningDays) {
        List<BrandAuthorization> expiring = repository.findExpiringSoon(warningDays);
        if (!expiring.isEmpty()) {
            String title = "品牌授权即将到期提醒";
            String content = String.format("共有 %d 个品牌授权将在 %d 天内到期，请及时跟进续签。",
                    expiring.size(), warningDays);
            CreateNotificationRequest request = new CreateNotificationRequest(
                    NotificationType.SYSTEM.name(), "BRAND_AUTH", null,
                    title, content, null, List.of()
            );
            try {
                notificationService.createNotification(request, null);
            } catch (Exception e) {
                log.warn("发送品牌授权到期通知失败: {}", e.getMessage());
            }
            log.info("品牌授权到期扫描完成，{} 个授权即将到期", expiring.size());
        }
        return expiring.size();
    }
}
