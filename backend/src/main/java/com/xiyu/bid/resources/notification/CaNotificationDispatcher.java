package com.xiyu.bid.resources.notification;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;

    /** 投标管理员角色码集合（管理员 + 投标管理员 + 投标组长）。 */
    private static final Set<String> BID_ADMIN_CODES = Set.of(
            RoleProfileCatalog.ADMIN_CODE,   // 管理员
            RoleProfileCatalog.BID_ADMIN_CODE,  // 投标管理员
            RoleProfileCatalog.BID_LEAD_CODE     // 投标组长
    );

    /** 借用人提交申请 → 通知 CA 保管员。 */
    public void onBorrowSubmitted(CaCertificateEntity cert, CaBorrowApplicationEntity app) {
        if (cert == null || cert.getCustodianId() == null) return;
        dispatch(NotificationType.CA_BORROW_PENDING, List.of(cert.getCustodianId()),
                "CA 借用申请待审批",
                String.format("「%s」收到借用申请，申请人：%s，请尽快审批。",
                        cert.getHolderName(), app.getApplicantName()),
                "CA_CERTIFICATE", cert.getId(), Map.of(
                        "caCertificateId", cert.getId(),
                        "applicationId", app.getId(),
                        "applicantId", app.getApplicantId(),
                        "borrowDurationType", app.getBorrowDurationType()));
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

    /** 借用申请审批通过 → 通知申请人。 */
    public void onBorrowApproved(CaBorrowApplicationEntity app) {
        if (app == null || app.getApplicantId() == null) return;
        dispatch(NotificationType.CA_BORROW_APPROVED, List.of(app.getApplicantId()),
                "CA 借用申请已通过",
                String.format("您的借用申请已通过审批，请及时领取「%s」。",
                        app.getProjectName()),
                "CA_CERTIFICATE", app.getCaCertificateId(), Map.of(
                        "caCertificateId", app.getCaCertificateId(),
                        "applicationId", app.getId()));
    }

    // ===== 内部辅助 =====

    /**
     * 内部通知派发方法。
     * 使用 REQUIRES_NEW 确保通知失败不影响主事务。
     * 设为 private 防止绕过 best-effort 语义直接调用。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void dispatch(NotificationType type, List<Long> recipientUserIds, String title,
                          String body, String sourceEntityType, Long sourceEntityId,
                          Map<String, Object> payload) {
        try {
            CreateNotificationRequest request = new CreateNotificationRequest(
                    type.name(), sourceEntityType, sourceEntityId,
                    title, body,
                    payload == null ? Map.of() : Map.copyOf(payload),
                    List.copyOf(recipientUserIds));
            DispatchResult result = notificationService.createNotification(request, null);
            if (!result.isValid()) {
                log.warn("CA notification dispatch failed: type={}, reason={}",
                        type, result.errorMessage());
            }
        } catch (RuntimeException ex) {
            log.warn("CA notification dispatch failed (type={}): {}", type, ex.getMessage());
        }
    }

    /** 解析「CA 保管员 + 投标管理员」作为到期 / 过期通知接收人。 */
    private List<Long> resolveCustodianAndBidAdmin(CaCertificateEntity cert) {
        java.util.LinkedHashSet<Long> set = new java.util.LinkedHashSet<>();
        if (cert.getCustodianId() != null) set.add(cert.getCustodianId());
        set.addAll(resolveBidAdminIds());
        return List.copyOf(set);
    }

    /** 查询所有投标管理员角色的启用用户 ID。 */
    @Transactional(readOnly = true)
    private List<Long> resolveBidAdminIds() {
        return userRepository.findEnabledByRoleProfileCodes(BID_ADMIN_CODES)
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());
    }
}
