package com.xiyu.bid.resources.notification;

import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CA 模块 → 通知 模块的桥接。
 * 把蓝图 §第五部分列出的 5 类 CA 通知场景翻译为站内信派发请求。
 *
 * <p>所有方法都是 best-effort：派发失败只记日志，不抛异常冒泡，
 * 避免影响 CA 业务主链路。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CaNotificationDispatcher {

    private final NotificationApplicationService notificationService;

    /** 借用人提交申请 → 通知 CA 保管员。 */
    public void onBorrowSubmitted(CaCertificateEntity cert, CaBorrowApplicationEntity app) {
        if (cert == null || cert.getCustodianId() == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("caCertificateId", cert.getId());
        payload.put("applicationId", app.getId());
        payload.put("applicantId", app.getApplicantId());
        payload.put("borrowDurationType", app.getBorrowDurationType());
        dispatch(NotificationType.CA_BORROW_PENDING, List.of(cert.getCustodianId()),
                "CA 借用申请待审批",
                String.format("「%s」收到借用申请，申请人：%s，请尽快审批。",
                        cert.getHolderName(), app.getApplicantName()),
                "CA_CERTIFICATE", cert.getId(), payload);
    }

    /** CA 即将到期（≤30 天）。 */
    public void onExpiring(CaCertificateEntity cert, long daysLeft) {
        if (cert == null) return;
        List<Long> recipients = resolveCustodianAndBidAdmin(cert);
        if (recipients.isEmpty()) return;
        dispatch(NotificationType.CA_EXPIRING, recipients,
                "CA 即将到期",
                String.format("「%s」将在 %d 天后到期（%s），请及时办理续期。",
                        cert.getHolderName(), daysLeft, cert.getExpiryDate()),
                "CA_CERTIFICATE", cert.getId(), Map.of(
                        "caCertificateId", cert.getId(),
                        "expiryDate", String.valueOf(cert.getExpiryDate()),
                        "daysLeft", daysLeft));
    }

    /** CA 已过期。 */
    public void onExpired(CaCertificateEntity cert) {
        if (cert == null) return;
        List<Long> recipients = resolveCustodianAndBidAdmin(cert);
        if (recipients.isEmpty()) return;
        dispatch(NotificationType.CA_EXPIRED, recipients,
                "CA 已过期",
                String.format("「%s」已于 %s 过期，请尽快处理。",
                        cert.getHolderName(), cert.getExpiryDate()),
                "CA_CERTIFICATE", cert.getId(), Map.of(
                        "caCertificateId", cert.getId(),
                        "expiryDate", String.valueOf(cert.getExpiryDate())));
    }

    /** 借用即将到期（≤30 天）。 */
    public void onBorrowDueSoon(CaCertificateEntity cert, CaBorrowApplicationEntity app, long daysLeft) {
        if (cert == null || app == null) return;
        List<Long> recipients = new java.util.ArrayList<>();
        if (app.getApplicantId() != null) recipients.add(app.getApplicantId());
        if (cert.getCustodianId() != null) recipients.add(cert.getCustodianId());
        if (recipients.isEmpty()) return;
        dispatch(NotificationType.CA_BORROW_DUE_SOON, recipients,
                "CA 借用即将到期",
                String.format("您借用的「%s」将在 %d 天后到期（%s），请按期归还。",
                        cert.getHolderName(), daysLeft, app.getExpectedReturnDate()),
                "CA_CERTIFICATE", cert.getId(), Map.of(
                        "caCertificateId", cert.getId(),
                        "applicationId", app.getId(),
                        "daysLeft", daysLeft));
    }

    /** 借用已逾期。 */
    public void onBorrowOverdue(CaCertificateEntity cert, CaBorrowApplicationEntity app) {
        if (cert == null || app == null) return;
        List<Long> recipients = new java.util.ArrayList<>();
        if (app.getApplicantId() != null) recipients.add(app.getApplicantId());
        if (cert.getCustodianId() != null) recipients.add(cert.getCustodianId());
        recipients.addAll(resolveBidAdminIds());
        if (recipients.isEmpty()) return;
        dispatch(NotificationType.CA_BORROW_OVERDUE, recipients,
                "CA 借用已逾期",
                String.format("「%s」借用已逾期（应还日期 %s），请尽快归还。",
                        cert.getHolderName(), app.getExpectedReturnDate()),
                "CA_CERTIFICATE", cert.getId(), Map.of(
                        "caCertificateId", cert.getId(),
                        "applicationId", app.getId()));
    }

    // ===== 内部辅助 =====

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(NotificationType type, List<Long> recipientUserIds, String title,
                         String body, String sourceEntityType, Long sourceEntityId,
                         Map<String, Object> payload) {
        try {
            CreateNotificationRequest request = new CreateNotificationRequest(
                    type.name(), sourceEntityType, sourceEntityId,
                    title, body,
                    payload == null ? Map.of() : Map.copyOf(payload),
                    List.copyOf(recipientUserIds));
            notificationService.createNotification(request, null);
        } catch (RuntimeException ex) {
            log.warn("CA notification dispatch failed (type={}, title={}): {}",
                    type, title, ex.getMessage());
        }
    }

    /** 解析「CA 保管员 + 投标管理员」作为到期 / 过期通知接收人。 */
    private List<Long> resolveCustodianAndBidAdmin(CaCertificateEntity cert) {
        java.util.LinkedHashSet<Long> set = new java.util.LinkedHashSet<>();
        if (cert.getCustodianId() != null) set.add(cert.getCustodianId());
        set.addAll(resolveBidAdminIds());
        return List.copyOf(set);
    }

    /**
     * 「投标管理员」定义为 bid_admin / bid_lead 角色用户。
     * 当前实现不做用户表查询，返回空列表；调用方应保证业务
     * 主链路不依赖此名单。后续接 userRepository 后可补齐。
     */
    private List<Long> resolveBidAdminIds() {
        return List.of();
    }
}
