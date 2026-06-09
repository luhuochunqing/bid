package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.application.command.PersonnelUpsertCommand;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.mapper.PersonnelMapper;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.service.PersonnelValidator;
import com.xiyu.bid.personnel.domain.valueobject.CertificateType;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.personnel.application.service.PersonnelOperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CreatePersonnelAppService 单元测试（蓝图 4.3 "新增证书" 核心创建路径）
 * 纯核心逻辑验证，Repository 为 mock。
 */
class CreatePersonnelAppServiceTest {

    private PersonnelRepository repository;
    private PersonnelMapper mapper;
    private PersonnelValidator validator;
    private PersonnelOperationLogService logService;
    private CreatePersonnelAppService service;

    @BeforeEach
    void setUp() {
        repository = mock(PersonnelRepository.class);
        mapper = new PersonnelMapper();
        validator = new PersonnelValidator();
        logService = mock(PersonnelOperationLogService.class);
        service = new CreatePersonnelAppService(repository, mapper, validator, logService);
    }

    @Test
    void shouldCreatePersonnelWithEducations() {
        when(repository.existsByEmployeeNumber("EMP001", null)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> {
            Personnel p = inv.getArgument(0);
            return new Personnel(1L, p.name(), p.employeeNumber(),
                    p.departmentCode(), p.departmentName(),
                    p.gender(), p.entryDate(), p.birthDate(), p.phone(),
                    p.education(), p.technicalTitle(),
                    p.status(), p.attachmentUrl(), p.remark(),
                    p.certificates(), p.educations(),
                    p.createdAt(), p.updatedAt());
        });

        var command = new PersonnelUpsertCommand(
                "张三", "EMP001", "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", null, null,
                List.of(), List.of(
                        new PersonnelUpsertCommand.EducationEntry(
                                "清华大学", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                                "本科", "全日制", "计算机科学与技术", false
                        )
                )
        );

        PersonnelDTO result = service.create(command, 1L, "张三（EMP001）");

        assertThat(result.name()).isEqualTo("张三");
        assertThat(result.employeeNumber()).isEqualTo("EMP001");
        assertThat(result.educations()).hasSize(1);
        assertThat(result.educations().get(0).schoolName()).isEqualTo("清华大学");
        verify(repository).save(any(Personnel.class));
    }

    @Test
    void shouldRejectDuplicateEmployeeNumber() {
        when(repository.existsByEmployeeNumber("EMP001", null)).thenReturn(true);

        var command = new PersonnelUpsertCommand(
                "张三", "EMP001", "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", null, null,
                List.of(), List.of()
        );

        assertThatThrownBy(() -> service.create(command, 1L, "张三（EMP001）"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该工号已存在");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreatePersonnelWithCertificatesAndEducations() {
        when(repository.existsByEmployeeNumber("EMP002", null)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> {
            Personnel p = inv.getArgument(0);
            return new Personnel(2L, p.name(), p.employeeNumber(),
                    p.departmentCode(), p.departmentName(),
                    p.gender(), p.entryDate(), p.birthDate(), p.phone(),
                    p.education(), p.technicalTitle(),
                    p.status(), p.attachmentUrl(), p.remark(),
                    p.certificates(), p.educations(),
                    p.createdAt(), p.updatedAt());
        });

        var command = new PersonnelUpsertCommand(
                "李四", "EMP002", "DEPT02", "商务部",
                "女", LocalDate.of(2021, 6, 15), LocalDate.of(1998, 1, 1), "13900139000",
                "硕士", "经济师", null, null,
                List.of(new PersonnelUpsertCommand.CertificateEntry(
                        "一级建造师", "CERT001", CertificateType.CONSTRUCTOR,
                        LocalDate.of(2022, 1, 1), LocalDate.of(2027, 1, 1),
                        "/attachments/2/cert.pdf", null, false, null
                )),
                List.of(
                        new PersonnelUpsertCommand.EducationEntry(
                                "北京大学", LocalDate.of(2013, 9, 1), LocalDate.of(2017, 6, 30),
                                "本科", "全日制", "土木工程", false
                        ),
                        new PersonnelUpsertCommand.EducationEntry(
                                "北京大学", LocalDate.of(2017, 9, 1), LocalDate.of(2020, 6, 30),
                                "硕士", "全日制", "结构工程", false
                        )
                )
        );

        PersonnelDTO result = service.create(command, 1L, "李四（EMP002）");

        assertThat(result.certificates()).hasSize(1);
        assertThat(result.educations()).hasSize(2);
        assertThat(result.highestEducation()).isEqualTo("硕士");
    }
}
