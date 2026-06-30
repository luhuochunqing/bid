package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.application.command.PersonnelUpsertCommand;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.mapper.PersonnelMapper;
import com.xiyu.bid.personnel.application.result.PersonnelUpdateResult;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog.ChangeDetail;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.service.PersonnelChangeDetector;
import com.xiyu.bid.personnel.domain.service.PersonnelValidator;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.Education;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdatePersonnelAppService {

    private final PersonnelRepository repository;
    private final PersonnelMapper mapper;
    private final PersonnelValidator validator;
    private final PersonnelOperationLogService logService;
    private final PersonnelChangeDetector changeDetector;

    @Transactional
    public PersonnelUpdateResult update(Long id, PersonnelUpsertCommand command, Long operatorId, String operatorName) {
        Personnel existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Personnel", String.valueOf(id)));

        if (repository.existsByEmployeeNumber(command.employeeNumber(), id)) {
            throw new IllegalArgumentException("工号已被占用: " + command.employeeNumber());
        }

        List<String> warnings = new ArrayList<>();

        // 1. 处理工号变更（领域层返回状态 + 警示）
        Personnel.EmployeeNumberChangeResult employeeNumberResult =
                existing.withEmployeeNumberChange(command.employeeNumber());

        if (employeeNumberResult.warningMessage() != null) {
            warnings.add(employeeNumberResult.warningMessage());
        }

        // 2. 映射新的教育经历和证书
        var newEducations = mapper.toEducationList(command.educations());
        var newCertificates = mapper.toCertificateList(command.certificates());

        // 3. 构建更新后的领域模型并校验
        Personnel updated = employeeNumberResult.updatedPersonnel()
                .withUpdatedDetails(
                        command.name(),
                        command.employeeNumber(),
                        command.departmentName(),
                        command.gender(),
                        command.entryDate(),
                        command.birthDate(),
                        command.phone(),
                        command.education(),
                        command.technicalTitle(),
                        command.remark(),
                        newCertificates,
                        newEducations
                );

        var validationResult = validator.validate(updated);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.errors().get(0).message());
        }

        // 4. 检测证书变更（删除 / 替换）并软删除旧记录（副作用保留）
        handleCertificateSideEffects(existing, newCertificates);

        // 5. 持久化
        Personnel saved = repository.save(updated);

        // 6. 用 ChangeDetector 生成结构化 diff，按变更类型记录多条日志
        List<PersonnelOperationLog> logs = buildOperationLogs(
                existing, saved, employeeNumberResult, operatorId, operatorName);
        logs.forEach(logService::save);

        PersonnelDTO dto = mapper.toDTO(saved);
        return new PersonnelUpdateResult(dto, warnings);
    }

    /**
     * 根据变更类型生成多条操作日志：
     * - 工号变更 → UPDATE 日志（含工号 diff）
     * - 基础字段变更 → UPDATE 日志
     * - 证书新增 → CERTIFICATE_ADD 日志
     * - 证书删除 → CERTIFICATE_REMOVE 日志
     * - 证书字段修改 → CERTIFICATE_UPDATE 日志
     * - 教育经历新增 → EDUCATION_ADD 日志
     * - 教育经历删除 → EDUCATION_REMOVE 日志
     * - 教育经历字段修改 → EDUCATION_UPDATE 日志
     */
    private List<PersonnelOperationLog> buildOperationLogs(
            Personnel existing, Personnel saved,
            Personnel.EmployeeNumberChangeResult employeeNumberResult,
            Long operatorId, String operatorName) {

        List<PersonnelOperationLog> logs = new ArrayList<>();
        Long personnelId = saved.id();
        Long opId = operatorId != null ? operatorId : 0L;
        String opName = operatorName != null && !operatorName.isBlank() ? operatorName : "system";

        // 6.1 工号变更 + 基础字段变更 → 合并到一条 UPDATE 日志
        List<ChangeDetail> basicChanges = new ArrayList<>();
        if (employeeNumberResult.warningMessage() != null) {
            basicChanges.add(new ChangeDetail("employeeNumber",
                    existing.employeeNumber(), saved.employeeNumber()));
        }
        basicChanges.addAll(changeDetector.detectBasicFieldChanges(existing, saved));
        if (!basicChanges.isEmpty()) {
            logs.add(PersonnelOperationLog.create(
                    personnelId, opId, opName,
                    PersonnelOperationLog.OperationType.UPDATE,
                    basicChanges));
        }

        // 6.2 证书变更 → 拆分为 CERTIFICATE_ADD / CERTIFICATE_REMOVE / CERTIFICATE_UPDATE
        List<ChangeDetail> certAddChanges = new ArrayList<>();
        List<ChangeDetail> certRemoveChanges = new ArrayList<>();
        List<ChangeDetail> certUpdateChanges = new ArrayList<>();

        for (ChangeDetail cd : changeDetector.detectCertificateChanges(
                existing.certificates(), saved.certificates())) {
            if (cd.field().equals("certificate")) {
                if (cd.oldValue().isEmpty() && !cd.newValue().isEmpty()) {
                    certAddChanges.add(cd);
                } else if (!cd.oldValue().isEmpty() && cd.newValue().isEmpty()) {
                    certRemoveChanges.add(cd);
                }
            } else {
                certUpdateChanges.add(cd);
            }
        }

        if (!certAddChanges.isEmpty()) {
            logs.add(PersonnelOperationLog.create(
                    personnelId, opId, opName,
                    PersonnelOperationLog.OperationType.CERTIFICATE_ADD,
                    certAddChanges));
        }
        if (!certRemoveChanges.isEmpty()) {
            logs.add(PersonnelOperationLog.create(
                    personnelId, opId, opName,
                    PersonnelOperationLog.OperationType.CERTIFICATE_REMOVE,
                    certRemoveChanges));
        }
        if (!certUpdateChanges.isEmpty()) {
            logs.add(PersonnelOperationLog.create(
                    personnelId, opId, opName,
                    PersonnelOperationLog.OperationType.CERTIFICATE_UPDATE,
                    certUpdateChanges));
        }

        // 6.3 教育经历变更 → 拆分为 EDUCATION_ADD / EDUCATION_REMOVE / EDUCATION_UPDATE
        List<ChangeDetail> eduAddChanges = new ArrayList<>();
        List<ChangeDetail> eduRemoveChanges = new ArrayList<>();
        List<ChangeDetail> eduUpdateChanges = new ArrayList<>();

        for (ChangeDetail cd : changeDetector.detectEducationChanges(
                existing.educations(), saved.educations())) {
            if (cd.field().equals("education")) {
                if (cd.oldValue().isEmpty() && !cd.newValue().isEmpty()) {
                    eduAddChanges.add(cd);
                } else if (!cd.oldValue().isEmpty() && cd.newValue().isEmpty()) {
                    eduRemoveChanges.add(cd);
                }
            } else {
                eduUpdateChanges.add(cd);
            }
        }

        if (!eduAddChanges.isEmpty()) {
            logs.add(PersonnelOperationLog.create(
                    personnelId, opId, opName,
                    PersonnelOperationLog.OperationType.EDUCATION_ADD,
                    eduAddChanges));
        }
        if (!eduRemoveChanges.isEmpty()) {
            logs.add(PersonnelOperationLog.create(
                    personnelId, opId, opName,
                    PersonnelOperationLog.OperationType.EDUCATION_REMOVE,
                    eduRemoveChanges));
        }
        if (!eduUpdateChanges.isEmpty()) {
            logs.add(PersonnelOperationLog.create(
                    personnelId, opId, opName,
                    PersonnelOperationLog.OperationType.EDUCATION_UPDATE,
                    eduUpdateChanges));
        }

        return logs;
    }

    /**
     * 证书变更副作用处理：仅负责软删除旧记录（删除/替换场景）。
     * 变更检测由 ChangeDetector 负责，这里只保留持久化副作用。
     */
    private void handleCertificateSideEffects(
            Personnel existing,
            List<Certificate> newCertificates) {

        for (Certificate oldCert : existing.certificates()) {
            if (oldCert.id() == null) continue;

            Certificate matchingNew = newCertificates.stream()
                    .filter(newCert -> oldCert.id().equals(newCert.id()))
                    .findFirst()
                    .orElse(null);

            if (matchingNew == null) {
                // 彻底删除
                repository.removeCertificate(existing.id(), oldCert.id());
            } else if (!Objects.equals(oldCert.attachmentUrl(), matchingNew.attachmentUrl())) {
                // 附件被替换
                repository.removeCertificate(existing.id(), oldCert.id());
            }
        }
    }
}
