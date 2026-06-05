// checkstyle:off
package com.xiyu.bid.warehouse.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseExpiryScanTask {

    private final WarehouseRepository warehouseRepo;
    private final UserRepository userRepo;
    private final NotificationApplicationService notificationService;
    private final NotificationRepository notificationRepo;

    // 每天早上 9:00 执行一次
    @Scheduled(cron = "0 0 9 * * ?")
    public void scanWarehouseExpirations() {
        log.info("[WarehouseExpiryScanTask] Starting scheduled warehouse expiry scan...");
        try {
            int alertsSent = processScan();
            log.info("[WarehouseExpiryScanTask] Expiry scan completed, sent {} alerts.", alertsSent);
        } catch (RuntimeException e) {
            log.error("[WarehouseExpiryScanTask] Failed to execute warehouse expiry scan", e);
        }
    }

    public int processScan() {
        List<WarehouseEntity> warehouses = warehouseRepo.findAll();
        int alertCount = 0;

        // 获取所有投标管理员与投标组长
        List<User> recipients = userRepo.findEnabledByRoleProfileCodes(List.of("bid_admin", "bid_lead"));
        if (recipients.isEmpty()) {
            log.warn("[WarehouseExpiryScanTask] No active users with bid_admin or bid_lead roles. Skipping notifications.");
            return 0;
        }
        List<Long> recipientIds = recipients.stream().map(User::getId).toList();

        LocalDate today = LocalDate.now();
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusDays(1);

        for (WarehouseEntity wh : warehouses) {
            // 已关仓的仓库不再发送任何提醒
            if (wh.getStatus() == WarehouseStatus.CLOSED) {
                continue;
            }

            LocalDate endDate = wh.getEndDate();
            if (endDate == null) {
                continue;
            }

            long remainingDays = ChronoUnit.DAYS.between(today, endDate);

            if (remainingDays <= 30 && remainingDays > 0) {
                // 即将到期
                if (wh.getStatus() == WarehouseStatus.IN_USE) {
                    wh.setStatus(WarehouseStatus.EXPIRING);
                    warehouseRepo.save(wh);
                }

                boolean alreadySent = notificationRepo.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                        "WAREHOUSE_EXPIRY_WARNING", wh.getId(), twentyFourHoursAgo
                );

                if (!alreadySent) {
                    sendExpiryWarningNotification(wh, remainingDays, recipientIds);
                    alertCount++;
                }
            } else if (remainingDays <= 0) {
                // 已到期
                if (wh.getStatus() != WarehouseStatus.EXPIRED) {
                    wh.setStatus(WarehouseStatus.EXPIRED);
                    warehouseRepo.save(wh);
                }

                boolean alreadySent = notificationRepo.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                        "WAREHOUSE_EXPIRED_WARNING", wh.getId(), twentyFourHoursAgo
                );

                if (!alreadySent) {
                    sendExpiredNotification(wh, Math.abs(remainingDays), recipientIds);
                    alertCount++;
                }
            } else {
                // 恢复为正常状态
                if (wh.getStatus() == WarehouseStatus.EXPIRING || wh.getStatus() == WarehouseStatus.EXPIRED) {
                    wh.setStatus(WarehouseStatus.IN_USE);
                    warehouseRepo.save(wh);
                }
            }
        }
        return alertCount;
    }

    private void sendExpiryWarningNotification(WarehouseEntity wh, long remainingDays, List<Long> recipientIds) {
        String title = String.format("【仓库租约到期提醒】%s 还有 %d 天到期", wh.getName(), remainingDays);
        String body = String.format(
                "您好，公司仓库租约即将到期，请及时安排续约或退租：\n" +
                "- 仓库名称：%s\n" +
                "- 所属区域：%s\n" +
                "- 仓库类型：%s\n" +
                "- 具体地址：%s\n" +
                "- 出租方：%s\n" +
                "- 结束时间：%s\n" +
                "- 到期天数：%d 天\n" +
                "- 区域联系人：%s\n" +
                "- 关仓计划：%s",
                wh.getName(),
                wh.getRegion(),
                wh.getType() == com.xiyu.bid.warehouse.domain.WarehouseType.SELF_OPERATED ? "自营" : "云仓",
                wh.getAddress(),
                wh.getLessor(),
                wh.getEndDate(),
                remainingDays,
                wh.getContactPerson(),
                wh.getClosePlan() != null ? wh.getClosePlan() : "（暂无）"
        );

        CreateNotificationRequest request = new CreateNotificationRequest(
                NotificationType.SYSTEM.name(),
                "WAREHOUSE_EXPIRY_WARNING",
                wh.getId(),
                title,
                body,
                null,
                recipientIds
        );
        notificationService.createNotification(request, null);
    }

    private void sendExpiredNotification(WarehouseEntity wh, long expiredDays, List<Long> recipientIds) {
        String title = String.format("【仓库租约已到期】%s 已到期 %d 天", wh.getName(), expiredDays);
        String body = String.format(
                "您好，公司仓库租约已到期，请尽快处理（续约 / 关仓）：\n" +
                "- 仓库名称：%s\n" +
                "- 所属区域：%s\n" +
                "- 结束时间：%s\n" +
                "- 过期天数：%d 天",
                wh.getName(),
                wh.getRegion(),
                wh.getEndDate(),
                expiredDays
        );

        CreateNotificationRequest request = new CreateNotificationRequest(
                NotificationType.SYSTEM.name(),
                "WAREHOUSE_EXPIRED_WARNING",
                wh.getId(),
                title,
                body,
                null,
                recipientIds
        );
        notificationService.createNotification(request, null);
    }
}
