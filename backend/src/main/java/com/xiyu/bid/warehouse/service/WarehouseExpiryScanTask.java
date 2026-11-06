// Input: 仓库数据、接收人、通知服务及事务管理器
// Output: 带跨实例互斥和单仓库失败隔离的租约到期提醒
// Pos: warehouse/service - 每日仓库租约扫描
// 维护声明: 修改时同步仓库并发回归测试和相关模块说明。
// checkstyle:off
package com.xiyu.bid.warehouse.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

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

        // 获取投标管理员、投标组长、投标系统管理员与投标专员（排除 admin 超管，不参与业务通知）
        List<User> recipients = userRepo.findEnabledByRoleProfileCodes(List.of(
                RoleProfileCatalog.BID_ADMIN_CODE,
                RoleProfileCatalog.BID_LEAD_CODE,
                RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE,
                RoleProfileCatalog.BID_SPECIALIST_CODE));
        if (recipients.isEmpty()) {
            log.warn("[WarehouseExpiryScanTask] No active users with bid_admin/bid_lead/bid_system_admin/bid_specialist roles. Skipping notifications.");
            return 0;
        }
        List<Long> recipientIds = recipients.stream().map(User::getId).toList();

        // 区域联系人姓名 -> 格式化"姓名（工号）"映射；找不到则降级为姓名
        java.util.Set<String> contactNames = warehouses.stream()
                .map(WarehouseEntity::getContactPerson)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, String> contactDisplay = new java.util.HashMap<>();
        for (String name : contactNames) {
            List<User> matched = userRepo.findByFullName(name);
            if (!matched.isEmpty()) {
                User u = matched.get(0);
                contactDisplay.put(name, u.getFullName() + "（" + u.getUsername() + "）");
            } else {
                contactDisplay.put(name, name);
            }
        }

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        for (WarehouseEntity candidate : warehouses) {
            try {
                alertCount += transaction.execute(status -> processWarehouse(candidate.getId(), recipientIds, contactDisplay));
            } catch (RuntimeException ex) {
                // Catch outside execute so this warehouse has rolled back before continuing.
                log.error("[WarehouseExpiryScanTask] Failed to process warehouse {}; continuing scan", candidate.getId(), ex);
            }
        }
        return alertCount;
    }

    private int processWarehouse(Long id, List<Long> recipientIds, java.util.Map<String, String> contactDisplay) {
        // Lock before the deduplication read; both that read and notification creation use this transaction.
        WarehouseEntity wh = warehouseRepo.findByIdForUpdate(id).orElse(null);
        if (wh == null || wh.getStatus() == WarehouseStatus.CLOSED || wh.getEndDate() == null) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        WarehouseStatus recomputed = com.xiyu.bid.warehouse.domain.WarehouseStatusCalculator.recompute(wh.getEndDate(), today);
        if (recomputed != wh.getStatus()) {
            wh.setStatus(recomputed);
            warehouseRepo.save(wh);
        }
        String source = switch (recomputed) {
            case EXPIRING -> "WAREHOUSE_EXPIRY_WARNING";
            case EXPIRED -> "WAREHOUSE_EXPIRED_WARNING";
            default -> null;
        };
        if (source == null || notificationRepo.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                source, id, LocalDateTime.now().minusDays(1))) {
            return 0;
        }
        long remainingDays = ChronoUnit.DAYS.between(today, wh.getEndDate());
        if (recomputed == WarehouseStatus.EXPIRING) {
            sendExpiryWarningNotification(wh, remainingDays, recipientIds, contactDisplay);
        } else {
            sendExpiredNotification(wh, Math.abs(remainingDays), recipientIds);
        }
        return 1;
    }

    private void sendExpiryWarningNotification(WarehouseEntity wh, long remainingDays, List<Long> recipientIds,
                                                java.util.Map<String, String> contactDisplay) {
        String title = String.format("【仓库租约到期提醒】%s 还有 %d 天到期", wh.getName(), remainingDays);
        String contactDisplayName = contactDisplay.getOrDefault(wh.getContactPerson(), wh.getContactPerson());
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
                contactDisplayName,
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
        if (!notificationService.createNotification(request, null).isValid()) {
            throw new IllegalStateException("Warehouse expiry notification was rejected");
        }
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
                Math.abs(expiredDays)
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
        if (!notificationService.createNotification(request, null).isValid()) {
            throw new IllegalStateException("Warehouse expiry notification was rejected");
        }
    }
}
