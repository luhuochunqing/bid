package com.xiyu.bid.qualification.service;

import com.xiyu.bid.qualification.application.QualificationOrchestrationService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy facade that delegates to the new orchestration service.
 * Maintains backward compatibility for existing controllers.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QualificationService {

    private final QualificationOrchestrationService orchestrationService;

    public QualificationDTO createQualification(QualificationDTO dto) {
        return orchestrationService.createQualification(dto);
    }

    public QualificationDTO updateQualification(Long id, QualificationDTO dto) {
        return orchestrationService.updateQualification(id, dto);
    }

    public void deleteQualification(Long id) {
        orchestrationService.deleteQualification(id);
    }

    public int scanExpiringQualifications(int thresholdDays) {
        return orchestrationService.scanExpiringQualifications(thresholdDays);
    }

    public QualificationDTO retireQualification(Long id, String reason) {
        return orchestrationService.retireQualification(id, reason);
    }

    /**
     * CO-368 fix: 轻量级清空 fileUrl，对称 retireQualification facade。
     */
    public void clearFileUrl(Long id) {
        orchestrationService.clearFileUrl(id);
    }

    public QualificationDTO restoreQualification(Long id) {
        return orchestrationService.restoreQualification(id);
    }
}
