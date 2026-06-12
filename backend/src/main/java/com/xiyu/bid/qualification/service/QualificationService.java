// Input: compatibility DTOs, businessqualification application services, DTO mapper
// Output: legacy qualification API orchestration
// Pos: Service/业务编排层
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.alerts.service.QualificationExpiryNotificationService;
import com.xiyu.bid.businessqualification.application.service.AlertConfigAppService;
import com.xiyu.bid.businessqualification.application.service.CreateQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.DeleteQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.ListQualificationsAppService;
import com.xiyu.bid.businessqualification.application.service.UpdateQualificationAppService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class QualificationService {

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
        var domainObj = listQualificationsAppService.get(id);
        var dto = mapper.toDto(domainObj);
        dto.setRetireReason(reason);
        var command = mapper.toUpsertCommand(dto);
        var retiredCommand = command.toBuilder().retired(true).build();
        updateQualificationAppService.update(id, retiredCommand);
        return mapper.toDto(listQualificationsAppService.get(id));
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
