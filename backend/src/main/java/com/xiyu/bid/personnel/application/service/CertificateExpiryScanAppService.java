package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelNotificationPort;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.service.CertificateExpiryPolicy;
import com.xiyu.bid.personnel.domain.service.CertificateExpiryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateExpiryScanAppService {

    private static final int DEFAULT_WARNING_DAYS = 60;
    private static final int BATCH_SIZE = 100;

    private final PersonnelRepository repository;
    private final PersonnelNotificationPort notificationPort;
    private final CertificateExpiryPolicy expiryPolicy;

    @Scheduled(cron = "0 0 9 * * ?")
    public void scanAndNotify() {
        scanAndNotify(DEFAULT_WARNING_DAYS);
    }

    public int scanAndNotify(int warningDays) {
        // 只扫描在职人员（ACTIVE）。已停用/删除的人员（INACTIVE）其证书到期提醒应停止。
        var criteria = PersonnelListCriteria.of(null, null, com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus.ACTIVE, null, true);
        
        long totalCount = repository.count();
        int totalBatches = (int) Math.ceil((double) totalCount / BATCH_SIZE);
        List<Personnel> allWithIssues = new java.util.ArrayList<>();

        for (int page = 0; page < totalBatches; page++) {
            PagedResult<Personnel> batch = repository.findAllPageable(criteria, page, BATCH_SIZE);
            
            List<Personnel> batchWithIssues = batch.content().stream()
                    .filter(p -> expiryPolicy.evaluate(p, warningDays).hasExpiring(warningDays))
                    .toList();
            
            allWithIssues.addAll(batchWithIssues);
            log.debug("批次 {}/{} 处理完成，发现 {} 条到期预警", page + 1, totalBatches, batchWithIssues.size());
        }

        if (!allWithIssues.isEmpty()) {
            notificationPort.notifyCertificateExpiry(allWithIssues, warningDays);
            log.info("证书到期扫描完成，共 {} 人有即将到期证书", allWithIssues.size());
        } else {
            log.info("证书到期扫描完成，未发现即将到期的证书");
        }

        return allWithIssues.size();
    }
}
