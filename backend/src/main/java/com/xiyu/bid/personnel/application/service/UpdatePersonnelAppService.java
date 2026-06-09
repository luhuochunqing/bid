package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.application.command.PersonnelUpsertCommand;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.mapper.PersonnelMapper;
import com.xiyu.bid.personnel.application.result.PersonnelUpdateResult;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
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

    @Transactional
    public PersonnelUpdateResult update(Long id, PersonnelUpsertCommand command, Long operatorId, String operatorName) {
        Personnel existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Personnel", String.valueOf(id)));

        if (repository.existsByEmployeeNumber(command.employeeNumber(), id)) {
            throw new IllegalArgumentException("工号已被占用: " + command.employeeNumber());
        }

        List<String> warnings = new ArrayList<>();
        List<String> changes = new ArrayList<>();

        // 1. 处理工号变更（领域层返回状态 + 警示）
        Personnel.EmployeeNumberChangeResult employeeNumberResult =
                existing.withEmployeeNumberChange(command.employeeNumber());

        if (employeeNumberResult.warningMessage() != null) {
            warnings.add(employeeNumberResult.warningMessage());
            changes.add("修改了工号: " + existing.employeeNumber() + " → " + command.employeeNumber());
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

        // 4. 检测证书变更（删除 / 替换）并软删除旧记录，同时收集变更明细（为操作日志准备）
        List<String> certificateChanges = handleCertificateChanges(existing, newCertificates);
        changes.addAll(certificateChanges);

        // 5. 收集教育经历变更（为操作日志准备）
        List<String> educationChanges = detectEducationChanges(existing.educations(), newEducations);
        changes.addAll(educationChanges);

        Personnel saved = repository.save(updated);

        // 6. 持久化操作日志
        if (!changes.isEmpty()) {
            PersonnelOperationLog log = PersonnelOperationLog.create(
                    saved.id(),
                    operatorId != null ? operatorId : 0L,
                    operatorName != null && !operatorName.isBlank() ? operatorName : "system",
                    PersonnelOperationLog.OperationType.UPDATE,
                    changes.stream()
                            .map(c -> new PersonnelOperationLog.ChangeDetail("field", "", c))
                            .toList()
            );
            logService.save(log);
        }

        PersonnelDTO dto = mapper.toDTO(saved);
        return new PersonnelUpdateResult(dto, warnings);
    }

    /**
     * 改进后的证书变更处理：
     * - ID 不在新列表中 → 删除（软删除）
     * - ID 相同但附件不同 → 替换（软删除旧记录）
     * 同时收集人类可读的变更描述，为后续操作日志做准备。
     */
    private List<String> handleCertificateChanges(
            Personnel existing,
            List<Certificate> newCertificates) {

        List<String> changes = new ArrayList<>();

        for (Certificate oldCert : existing.certificates()) {
            if (oldCert.id() == null) continue;

            Certificate matchingNew = newCertificates.stream()
                    .filter(newCert -> oldCert.id().equals(newCert.id()))
                    .findFirst()
                    .orElse(null);

            if (matchingNew == null) {
                // 彻底删除
                repository.removeCertificate(existing.id(), oldCert.id());
                changes.add("删除了证书: " + oldCert.name());
            } else if (!Objects.equals(oldCert.attachmentUrl(), matchingNew.attachmentUrl())) {
                // 附件被替换
                repository.removeCertificate(existing.id(), oldCert.id());
                changes.add("替换了证书附件: " + oldCert.name());
            }
            // 同 ID 且附件没变 → 可能是其他字段修改，暂不记录为“替换”
        }

        // 检测新增的证书
        for (Certificate newCert : newCertificates) {
            if (newCert.id() == null) {
                changes.add("新增了证书: " + newCert.name());
            }
        }

        return changes;
    }

    /**
     * 简单检测教育经历变更，用于操作日志收集。
     * 当前版本只做粗粒度统计（新增/删除数量）。
     */
    private List<String> detectEducationChanges(List<Education> oldList, List<Education> newList) {
        List<String> changes = new ArrayList<>();

        int added = Math.max(0, newList.size() - oldList.size());
        int removed = Math.max(0, oldList.size() - newList.size());

        if (added > 0) {
            changes.add("新增了 " + added + " 条教育经历");
        }
        if (removed > 0) {
            changes.add("删除了 " + removed + " 条教育经历");
        }

        // 可以进一步细化对比学校名称等，这里先做基础版本
        return changes;
    }
}
