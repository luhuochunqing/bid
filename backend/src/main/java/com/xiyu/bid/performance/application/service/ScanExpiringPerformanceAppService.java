package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.view.ExpiringPerformanceAlertView;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScanExpiringPerformanceAppService {

    private final PerformanceRepository repository;

    @Transactional(readOnly = true)
    public List<ExpiringPerformanceAlertView> scan(PerformanceAlertConfig config) {
        List<PerformanceRecord> records = repository.findAllWithExpiryDate();

        return records.stream()
                .filter(r -> r.expiryDate() != null)
                .filter(r -> {
                    long days = ChronoUnit.DAYS.between(LocalDate.now(), r.expiryDate());
                    return config.isExpiring(isSoe(r), days);
                })
                .map(r -> toView(r, config))
                .toList();
    }

    private boolean isSoe(PerformanceRecord r) {
        return r.customerType() == CustomerType.CENTRAL_SOE;
    }

    private ExpiringPerformanceAlertView toView(PerformanceRecord r, PerformanceAlertConfig config) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), r.expiryDate());
        String customerLabel = customerLabel(r.customerType());
        String projectLabel = r.projectType() != null ? r.projectType().name() : null;

        return ExpiringPerformanceAlertView.builder()
                .recordId(r.id())
                .contractName(r.contractName())
                .signingEntity(r.signingEntity())
                .groupCompany(r.groupCompany())
                .customerTypeLabel(customerLabel)
                .projectTypeLabel(projectLabel)
                .expiryDate(r.expiryDate())
                .remainingDays(days)
                .xiyuProjectManager(r.xiyuProjectManager())
                .contactPerson(r.contactPerson())
                .contactInfo(r.contactInfo())
                .relatedId(String.format("Performance:%s:%s", r.id(), r.expiryDate()))
                .message(buildMessage(r, days, customerLabel))
                .build();
    }

    private String customerLabel(CustomerType type) {
        if (type == null) return "未知";
        return switch (type) {
            case CENTRAL_SOE -> "央企";
            case GOVERNMENT_INSTITUTION -> "政府机关/事业单位";
            case LOCAL_SOE -> "地方国企";
            case PRIVATE_ENTERPRISE -> "民企";
            case FOREIGN_HK_MACAO_TW -> "港澳台/外企";
        };
    }

    private String buildMessage(PerformanceRecord r, long days, String customerLabel) {
        if (r.customerType() == CustomerType.CENTRAL_SOE) {
            return String.format(
                    "【业绩到期提醒】%s 还有 %d 天到期（%s客户）",
                    r.contractName(), days, customerLabel);
        } else {
            return String.format(
                    "【业绩到期提醒】%s 还有 %d 天到期",
                    r.contractName(), days);
        }
    }
}
