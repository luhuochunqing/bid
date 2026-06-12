// Input: compatibility DTOs, businessqualification application services, DTO mapper, and project access scope
// Output: legacy qualification API orchestration with project-linked borrow record access control
// Pos: Service/业务编排层
// 维护声明: 仅维护兼容入口编排；业务规则下沉到 businessqualification 子域；
//           Excel/ZIP/Template 导出迁到 QualificationExportService。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.access.core.ProjectLinkedRecordVisibilityPolicy;
import com.xiyu.bid.alerts.service.QualificationExpiryNotificationService;
import com.xiyu.bid.businessqualification.application.service.AlertConfigAppService;
import com.xiyu.bid.businessqualification.application.service.CreateQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.DeleteQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.ImportQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.ListQualificationsAppService;
import com.xiyu.bid.businessqualification.application.service.UpdateQualificationAppService;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.dto.QualificationOverviewDTO;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class QualificationService {

    private static final Logger log = LoggerFactory.getLogger(QualificationService.class);

    private final CreateQualificationAppService createQualificationAppService;
    private final UpdateQualificationAppService updateQualificationAppService;
    private final ListQualificationsAppService listQualificationsAppService;
    /** §4.1.3.8 资质到期通知编排（替代旧 ScanExpiringQualificationsAppService）。 */
    private final QualificationExpiryNotificationService qualificationExpiryNotificationService;
    private final AlertConfigAppService alertConfigAppService;
    private final DeleteQualificationAppService deleteQualificationAppService;
    private final ImportQualificationAppService importQualificationAppService;
    private final QualificationDtoMapper mapper;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final BusinessQualificationRepository businessQualificationRepository;

    public QualificationDTO createQualification(QualificationDTO dto) {
        return mapper.toDto(createQualificationAppService.create(mapper.toUpsertCommand(dto)));
    }

    @Transactional(readOnly = true)
    public List<QualificationDTO> getAllQualifications(
            String subjectType, String subjectName, String category, String level, List<String> status,
            Integer expiringWithinDays, LocalDate expiringFrom, LocalDate expiringTo, String issuer, String keyword
    ) {
        return listQualificationsAppService.list(mapper.toCriteria(
                        subjectType, subjectName, category, level, status,
                        expiringWithinDays, expiringFrom, expiringTo, issuer, keyword))
                .stream().map(mapper::toDto).toList();
    }

    /**
     * CO-155 fix: paginated version. Repository uses Specification to push
     * to SQL; controller receives Spring Page<DTO> for JSON convenience.
     */
    @Transactional(readOnly = true)
    public Page<QualificationDTO> getAllQualifications(
            String subjectType, String subjectName, String category, String level, List<String> status,
            Integer expiringWithinDays, LocalDate expiringFrom, LocalDate expiringTo, String issuer, String keyword,
            int page, int size
    ) {
        // application service translates domain QualificationPage -> Spring Page
        return listQualificationsAppService.list(
                mapper.toCriteria(subjectType, subjectName, category, level, status,
                        expiringWithinDays, expiringFrom, expiringTo, issuer, keyword),
                page, size
        ).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public QualificationDTO getQualificationById(Long id) {
        return mapper.toDto(findQualification(id));
    }

    public QualificationDTO updateQualification(Long id, QualificationDTO dto) {
        return mapper.toDto(updateQualificationAppService.update(id, mapper.toUpsertCommand(dto)));
    }

    public void deleteQualification(Long id) {
        deleteQualificationAppService.delete(id);
    }

    @Transactional(readOnly = true)
    public List<QualificationDTO> getQualificationsByType(com.xiyu.bid.entity.Qualification.Type type) {
        String category = type == null ? null : mapper.toUpsertCommand(QualificationDTO.builder().type(type).build())
                .getCategory().name();
        return getAllQualifications(null, null, category, null, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<QualificationDTO> getValidQualifications() {
        return getAllQualifications(null, null, null, null, null, null, null, null, null, null).stream()
                .filter(item -> !"expired".equals(item.getStatus()))
                .toList();
    }





    @Transactional(readOnly = true)
    public QualificationOverviewDTO getOverview() {
        return mapper.toOverview(getAllQualifications(null, null, null, null, null, null, null, null, null, null));
    }

    @Transactional
    public int scanExpiringQualifications(int thresholdDays) {
        int effective = thresholdDays > 0 ? thresholdDays : alertConfigAppService.getConfig().alertDays();
        QualificationExpiryNotificationService.ScanOutcome outcome =
                qualificationExpiryNotificationService.runScan(effective, null);
        return outcome.scanned();
    }

    @Transactional
    public QualificationDTO retireQualification(Long id, String reason) {
        var dto = getQualificationById(id);
        dto.setRetireReason(reason);
        var command = mapper.toUpsertCommand(dto);
        var retiredCommand = command.toBuilder().retired(true).build();
        updateQualificationAppService.update(id, retiredCommand);
        return getQualificationById(id);
    }

    @Transactional
    public QualificationDTO restoreQualification(Long id) {
        var dto = getQualificationById(id);
        dto.setRetireReason("");
        var command = mapper.toUpsertCommand(dto);
        var restoredCommand = command.toBuilder().retired(false).build();
        updateQualificationAppService.update(id, restoredCommand);
        return getQualificationById(id);
    }

    @Transactional(readOnly = true)
    public List<String> getAllLevels() {
        return businessQualificationRepository.findAllLevels();
    }

    @Transactional
    public ImportQualificationAppService.ImportSummary importFromExcel(MultipartFile file, String operatorName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }
        return importQualificationAppService.importFromExcel(file, operatorName);
    }

    @Transactional
    public QualificationDTO uploadAttachment(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }
        QualificationDTO dto = getQualificationById(id);
        dto.setFileUrl(file.getOriginalFilename());
        return updateQualification(id, dto);
    }

    private BusinessQualification findQualification(Long id) {
        return listQualificationsAppService.get(id);
    }




}
