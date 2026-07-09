package com.xiyu.bid.resources.service;

import com.xiyu.bid.alerts.domain.AlertMessagePolicy;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.alerts.service.AlertRuleProvisioningService;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CA 证书到期及借用逾期扫描服务。
 * <p>
 * 纯核心逻辑：扫描证书到期情况（即将到期/已过期），
 * 以及借用记录逾期情况（即将到期归还/已逾期），
 * 生成告警历史。</p>
 * <p>副作用：通过 {@link AlertNotificationOrchestrator#createAndNotifyIfNew} 写入告警历史并触发通知。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaExpiryScanService {

    /** 证书到期提醒阈值天数（30 天）。 */
    static final int CA_EXPIRY_THRESHOLD_DAYS = 30;

    /** 借用归还提醒阈值天数（30 天）。 */
    static final int BORROW_RETURN_THRESHOLD_DAYS = 30;

    private final CaCertificateRepository certificateRepository;
    private final CaBorrowApplicationRepository borrowRepository;
    private final AlertRuleProvisioningService alertRuleProvisioningService;
    private final AlertNotificationOrchestrator alertNotificationOrchestrator;

    /**
     * 扫描即将到期或已过期的 CA 证书并生成告警，同时回写陈旧的 status 字段.
     *
     * <p>CO-477: status 字段只在 create/update 时计算一次，时间流逝后会陈旧
     * （例：EXPIRING → 已过到期日 → 仍为 EXPIRING）。本方法在扫描时按 expiryDate
     * 实时重算并持久化 status（INACTIVE 下架状态不覆盖），保证 overview 聚合
     * 查询和列表筛选的准确性。
     *
     * @return 生成的告警数量
     */
    @Transactional
    public int scanCertificateExpiry() {
        AlertRule rule = alertRuleProvisioningService.ensureRule(
                AlertRule.AlertType.CA_EXPIRY, "CA证书到期提醒", CA_EXPIRY_THRESHOLD_DAYS);
        // P1-7: 仅查询非 INACTIVE 证书，避免 findAll 后内存过滤
        List<CaCertificateEntity> allCertificates = certificateRepository.findByStatusNot("INACTIVE");

        int created = 0;
        for (CaCertificateEntity cert : allCertificates) {
            LocalDate expiryDate = cert.getExpiryDate();
            if (expiryDate == null) continue;

            // CO-477: 实时重算 status 并回写（仅在变化时持久化，减少不必要的 UPDATE）
            String freshStatus = computeFreshStatus(expiryDate);
            if (!freshStatus.equals(cert.getStatus())) {
                cert.setStatus(freshStatus);
                certificateRepository.save(cert);
            }

            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
            if (daysUntil < 0) {
                // 已过期
                AlertHistoryCreateRequest req = buildCreateRequest(rule, "HIGH",
                        String.format("CaCertificate:%s", cert.getId()),
                        String.format("【CA已过期】%s（%s）已于 %s 过期，请立即处理",
                                cert.getHolderName(), cert.getCaType(), expiryDate));
                // P1-3: 使用 createAndNotifyIfNew 模板方法
                alertNotificationOrchestrator.createAndNotifyIfNew(
                        req, rule, buildCertificatePayload(cert, "EXPIRED"));
                created++;
            } else if (daysUntil <= CA_EXPIRY_THRESHOLD_DAYS) {
                // 即将到期
                AlertHistoryCreateRequest req = buildCreateRequest(rule, "MEDIUM",
                        String.format("CaCertificate:%s", cert.getId()),
                        String.format("【CA即将到期】%s（%s）还有 %d 天到期，有效期至 %s",
                                cert.getHolderName(), cert.getCaType(), daysUntil, expiryDate));
                // P1-3: 使用 createAndNotifyIfNew 模板方法
                alertNotificationOrchestrator.createAndNotifyIfNew(
                        req, rule, buildCertificatePayload(cert, "EXPIRING"));
                created++;
            }
        }

        log.info("CA certificate expiry scan completed. Created {} alerts.", created);
        return created;
    }

    /**
     * CO-477: 按 expiryDate 实时计算 status（与 CaCertificateService.computeStatus 对齐）.
     */
    private String computeFreshStatus(LocalDate expiryDate) {
        if (expiryDate == null) return "ACTIVE";
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        if (daysUntil < 0) return "EXPIRED";
        if (daysUntil <= CA_EXPIRY_THRESHOLD_DAYS) return "EXPIRING";
        return "ACTIVE";
    }

    /**
     * 扫描借用即将到期或已逾期的 CA 借出记录并生成告警。
     *
     * @return 生成的告警数量
     */
    @Transactional
    public int scanBorrowOverdue() {
        AlertRule rule = alertRuleProvisioningService.ensureRule(
                AlertRule.AlertType.CA_BORROW_OVERDUE, "CA借用归还提醒", BORROW_RETURN_THRESHOLD_DAYS);
        // P1-7: 直接查询 APPROVED 状态的借用记录，避免 findAll 后内存过滤
        List<CaBorrowApplicationEntity> approvedBorrows = borrowRepository
                .findByStatusOrderByCreatedAtDesc("APPROVED");

        int created = 0;
        for (CaBorrowApplicationEntity borrow : approvedBorrows) {
            if (borrow.getExpectedReturnDate() == null) continue;

            long daysUntilReturn = ChronoUnit.DAYS.between(LocalDate.now(), borrow.getExpectedReturnDate());

            if (daysUntilReturn < 0) {
                // 已逾期
                AlertHistoryCreateRequest req = buildCreateRequest(rule, "HIGH",
                        String.format("CaBorrowApplication:%s", borrow.getId()),
                        String.format("【CA借用已逾期】借用人 %s 的 CA 借用已于 %s 到期，已逾期 %d 天，请催促归还",
                                borrow.getApplicantName(), borrow.getExpectedReturnDate(), Math.abs(daysUntilReturn)));
                // P1-3: 使用 createAndNotifyIfNew 模板方法
                alertNotificationOrchestrator.createAndNotifyIfNew(
                        req, rule, buildBorrowPayload(borrow));
                created++;
            } else if (daysUntilReturn <= BORROW_RETURN_THRESHOLD_DAYS) {
                // 即将到期
                AlertHistoryCreateRequest req = buildCreateRequest(rule, "MEDIUM",
                        String.format("CaBorrowApplication:%s", borrow.getId()),
                        String.format("【CA借用即将到期】借用人 %s 的 CA 借用将于 %s 到期，还有 %d 天",
                                borrow.getApplicantName(), borrow.getExpectedReturnDate(), daysUntilReturn));
                // P1-3: 使用 createAndNotifyIfNew 模板方法
                alertNotificationOrchestrator.createAndNotifyIfNew(
                        req, rule, buildBorrowPayload(borrow));
                created++;
            }
        }

        log.info("CA borrow overdue scan completed. Created {} alerts.", created);
        return created;
    }

    private AlertHistoryCreateRequest buildCreateRequest(AlertRule rule, String level,
                                                         String relatedId, String message) {
        AlertHistoryCreateRequest req = new AlertHistoryCreateRequest();
        req.setRuleId(rule.getId());
        req.setLevel(AlertHistory.AlertLevel.valueOf(level));
        req.setRelatedId(relatedId);
        req.setMessage(message);
        // CO-546: CA 到期预警使用 DAILY_DEDUP 策略，当日去重，次日新建以触发每日通知
        req.setDedupPolicy(com.xiyu.bid.alerts.domain.DedupPolicy.DAILY_DEDUP);
        return req;
    }

    /**
     * 构造 CA 证书告警通知附加 payload。
     *
     * @param cert    CA 证书实体
     * @param subType 告警子类型："EXPIRED" 或 "EXPIRING"
     * @return payload Map
     */
    private Map<String, Object> buildCertificatePayload(CaCertificateEntity cert, String subType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("caCertificateId", cert.getId());
        payload.put("holderName", cert.getHolderName());
        payload.put("caType", cert.getCaType());
        payload.put("expiryDate", cert.getExpiryDate());
        payload.put(AlertMessagePolicy.PAYLOAD_KEY_ALERT_SUB_TYPE, subType);
        payload.put("targetUrl", "/resources/ca-certificates");
        // CO-546: 携带 custodianId 供 AlertNotificationOrchestrator 将 CA 保管员加入接收人，
        // 与 returnBorrow 路径的 CaNotificationDispatcher 接收人范围对齐。
        payload.put(AlertMessagePolicy.PAYLOAD_KEY_CUSTODIAN_ID, cert.getCustodianId());
        return payload;
    }

    /**
     * 构造 CA 借用告警通知附加 payload。
     *
     * @param borrow CA 借用申请实体
     * @return payload Map
     */
    private Map<String, Object> buildBorrowPayload(CaBorrowApplicationEntity borrow) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("borrowId", borrow.getId());
        payload.put("applicantName", borrow.getApplicantName());
        payload.put("expectedReturnDate", borrow.getExpectedReturnDate());
        payload.put("targetUrl", "/resources/ca-borrow-applications");
        return payload;
    }
}
