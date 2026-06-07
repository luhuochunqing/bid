package com.xiyu.bid.resources.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * CA 证书到期及借用逾期扫描服务。
 * <p>
 * 纯核心逻辑：扫描证书到期情况（即将到期/已过期），
 * 以及借用记录逾期情况（即将到期归还/已逾期），
 * 生成告警历史。</p>
 * <p>副作用：调用 AlertHistoryService 写入告警历史。</p>
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
    private final AlertRuleRepository alertRuleRepository;
    private final AlertHistoryService alertHistoryService;

    /**
     * 扫描即将到期或已过期的 CA 证书并生成告警。
     *
     * @return 生成的告警数量
     */
    @Transactional
    public int scanCertificateExpiry() {
        AlertRule rule = ensureRule(AlertRule.AlertType.CA_EXPIRY, "CA证书到期提醒", CA_EXPIRY_THRESHOLD_DAYS);
        List<CaCertificateEntity> allCertificates = certificateRepository.findAll();

        int created = 0;
        for (CaCertificateEntity cert : allCertificates) {
            if ("INACTIVE".equals(cert.getStatus())) continue;

            LocalDate expiryDate = cert.getExpiryDate();
            if (expiryDate == null) continue;

            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
            if (daysUntil < 0) {
                // 已过期
                AlertHistoryCreateRequest req = buildRequest(rule, "HIGH",
                        "ca:expired:" + cert.getId(),
                        String.format("【CA已过期】%s（%s）已于 %s 过期，请立即处理",
                                cert.getHolderName(), cert.getCaType(), expiryDate));
                alertHistoryService.createAlertHistory(req);
                created++;
            } else if (daysUntil <= CA_EXPIRY_THRESHOLD_DAYS) {
                // 即将到期
                AlertHistoryCreateRequest req = buildRequest(rule, "MEDIUM",
                        "ca:expiring:" + cert.getId(),
                        String.format("【CA即将到期】%s（%s）还有 %d 天到期，有效期至 %s",
                                cert.getHolderName(), cert.getCaType(), daysUntil, expiryDate));
                alertHistoryService.createAlertHistory(req);
                created++;
            }
        }

        log.info("CA certificate expiry scan completed. Created {} alerts.", created);
        return created;
    }

    /**
     * 扫描借用即将到期或已逾期的 CA 借出记录并生成告警。
     *
     * @return 生成的告警数量
     */
    @Transactional
    public int scanBorrowOverdue() {
        AlertRule rule = ensureRule(AlertRule.AlertType.CA_BORROW_OVERDUE, "CA借用归还提醒", BORROW_RETURN_THRESHOLD_DAYS);
        List<CaBorrowApplicationEntity> approvedBorrows = borrowRepository.findAll().stream()
                .filter(b -> "APPROVED".equals(b.getStatus()))
                .toList();

        int created = 0;
        for (CaBorrowApplicationEntity borrow : approvedBorrows) {
            if (borrow.getExpectedReturnDate() == null) continue;

            long daysUntilReturn = ChronoUnit.DAYS.between(LocalDate.now(), borrow.getExpectedReturnDate());

            if (daysUntilReturn < 0) {
                // 已逾期
                AlertHistoryCreateRequest req = buildRequest(rule, "HIGH",
                        "ca:borrow-overdue:" + borrow.getId(),
                        String.format("【CA借用已逾期】借用人 %s 的 CA 借用已于 %s 到期，已逾期 %d 天，请催促归还",
                                borrow.getApplicantName(), borrow.getExpectedReturnDate(), Math.abs(daysUntilReturn)));
                alertHistoryService.createAlertHistory(req);
                created++;
            } else if (daysUntilReturn <= BORROW_RETURN_THRESHOLD_DAYS) {
                // 即将到期
                AlertHistoryCreateRequest req = buildRequest(rule, "MEDIUM",
                        "ca:borrow-expiring:" + borrow.getId(),
                        String.format("【CA借用即将到期】借用人 %s 的 CA 借用将于 %s 到期，还有 %d 天",
                                borrow.getApplicantName(), borrow.getExpectedReturnDate(), daysUntilReturn));
                alertHistoryService.createAlertHistory(req);
                created++;
            }
        }

        log.info("CA borrow overdue scan completed. Created {} alerts.", created);
        return created;
    }

    private AlertRule ensureRule(AlertRule.AlertType type, String name, int thresholdDays) {
        return alertRuleRepository.findByType(type).stream()
                .findFirst()
                .orElseGet(() -> alertRuleRepository.save(AlertRule.builder()
                        .name(name)
                        .type(type)
                        .condition(AlertRule.ConditionType.LESS_THAN)
                        .threshold(BigDecimal.valueOf(thresholdDays))
                        .enabled(true)
                        .createdBy("system")
                        .build()));
    }

    private AlertHistoryCreateRequest buildRequest(AlertRule rule, String level,
                                                    String relatedId, String message) {
        AlertHistoryCreateRequest req = new AlertHistoryCreateRequest();
        req.setRuleId(rule.getId());
        req.setLevel(AlertHistory.AlertLevel.valueOf(level));
        req.setRelatedId(relatedId);
        req.setMessage(message);
        return req;
    }
}
