package com.xiyu.bid.qualification.application;

import com.xiyu.bid.alerts.service.QualificationExpiryNotificationService;
import com.xiyu.bid.businessqualification.application.service.AlertConfigAppService;
import com.xiyu.bid.businessqualification.application.service.CreateQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.DeleteQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.ListQualificationsAppService;
import com.xiyu.bid.businessqualification.application.service.UpdateQualificationAppService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.service.QualificationDtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestration layer for qualification related write operations.
 * This class contains the original business logic that was previously in {@link com.xiyu.bid.qualification.service.QualificationService}.
 * It is deliberately placed in the {@code application} package to separate responsibilities.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QualificationOrchestrationService {

    private final CreateQualificationAppService createQualificationAppService;
    private final UpdateQualificationAppService updateQualificationAppService;
    private final DeleteQualificationAppService deleteQualificationAppService;
    private final ListQualificationsAppService listQualificationsAppService;
    private final AlertConfigAppService alertConfigAppService;
    private final QualificationExpiryNotificationService qualificationExpiryNotificationService;
    private final QualificationDtoMapper mapper;

    public QualificationDTO createQualification(QualificationDTO dto) {
        return mapper.toDto(createQualificationAppService.create(mapper.toUpsertCommand(dto)));
    }

    public QualificationDTO updateQualification(Long id, QualificationDTO dto) {
        return mapper.toDto(updateQualificationAppService.update(id, mapper.toUpsertCommand(dto)));
    }

    public void deleteQualification(Long id) {
        deleteQualificationAppService.delete(id);
    }

    public int scanExpiringQualifications(int thresholdDays) {
        int effective = thresholdDays > 0 ? thresholdDays : alertConfigAppService.getConfig().alertDays();
        QualificationExpiryNotificationService.ScanOutcome outcome =
                qualificationExpiryNotificationService.runScan(effective, null);
        return outcome.scanned();
    }

    public QualificationDTO retireQualification(Long id, String reason) {
        return mapper.toDto(updateQualificationAppService.retire(id, reason));
    }

    /**
     * CO-368 fix: 轻量级清空 fileUrl，对称 retireQualification 模式。
     * 用于 deleteAttachment 同步主实体 fileUrl 场景。
     */
    public void clearFileUrl(Long id) {
        updateQualificationAppService.clearFileUrl(id);
    }

    public QualificationDTO restoreQualification(Long id) {
        var domainObj = listQualificationsAppService.get(id);
        var dto = mapper.toDto(domainObj);
        dto.setRetireReason("");
        var command = mapper.toUpsertCommand(dto);
        var restoredCommand = command.toBuilder().retired(false).build();
        updateQualificationAppService.update(id, restoredCommand);
        return mapper.toDto(listQualificationsAppService.get(id));
    }
}
