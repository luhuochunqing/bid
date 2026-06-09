package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.application.command.PersonnelUpsertCommand;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.mapper.PersonnelMapper;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.service.PersonnelValidator;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePersonnelAppService {

    private final PersonnelRepository repository;
    private final PersonnelMapper mapper;
    private final PersonnelValidator validator;
    private final PersonnelOperationLogService logService;

    @Transactional
    public PersonnelDTO create(PersonnelUpsertCommand command, Long operatorId, String operatorName) {
        if (repository.existsByEmployeeNumber(command.employeeNumber(), null)) {
            // 精确匹配蓝图 4.3 "新增证书" 校验规则
            throw new IllegalArgumentException("该工号已存在");
        }

        var certs = mapper.toCertificateList(command.certificates());
        var educations = mapper.toEducationList(command.educations());

        Personnel personnel = Personnel.create(
                null,
                command.name(),
                command.employeeNumber(),
                command.departmentCode(),
                command.departmentName(),
                command.gender(),
                command.entryDate(),
                command.birthDate(),
                command.phone(),
                command.education(),
                command.technicalTitle(),
                PersonnelStatus.ACTIVE,
                command.attachmentUrl(),
                command.remark(),
                certs,
                educations
        );

        var validationResult = validator.validate(personnel);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.errors().get(0).message());
        }

        PersonnelDTO saved = mapper.toDTO(repository.save(personnel));

        // 记录操作日志（蓝图要求："新增人员档案 - {工号} {姓名}"）
        PersonnelOperationLog log = PersonnelOperationLog.create(
                saved.id(),
                operatorId != null ? operatorId : 0L,
                operatorName != null && !operatorName.isBlank() ? operatorName : "system",
                PersonnelOperationLog.OperationType.CREATE,
                java.util.List.of()
        );
        logService.save(log);

        return saved;
    }
}
