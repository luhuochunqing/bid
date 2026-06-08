package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.application.command.PersonnelUpsertCommand;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.mapper.PersonnelMapper;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePersonnelAppService {

    private final PersonnelRepository repository;
    private final PersonnelMapper mapper;

    @Transactional
    public PersonnelDTO create(PersonnelUpsertCommand command) {
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

        return mapper.toDTO(repository.save(personnel));
    }
}
