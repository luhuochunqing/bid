package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.application.command.PersonnelUpsertCommand;
import com.xiyu.bid.personnel.application.mapper.PersonnelMapper;
import com.xiyu.bid.personnel.application.result.PersonnelUpdateResult;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog.ChangeDetail;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.service.PersonnelChangeDetector;
import com.xiyu.bid.personnel.domain.service.PersonnelValidator;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.CertificateType;
import com.xiyu.bid.personnel.domain.valueobject.Education;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * UpdatePersonnelAppService 单元测试（CO-417）
 * 验证结构化字段级 diff 日志记录，覆盖基础字段/证书/教育经历三类变更的多条日志记录。
 */
class UpdatePersonnelAppServiceTest {

    private PersonnelRepository repository;
    private PersonnelMapper mapper;
    private PersonnelValidator validator;
    private PersonnelOperationLogService logService;
    private PersonnelChangeDetector changeDetector;
    private UpdatePersonnelAppService service;

    @BeforeEach
    void setUp() {
        repository = mock(PersonnelRepository.class);
        mapper = new PersonnelMapper();
        validator = new PersonnelValidator();
        logService = mock(PersonnelOperationLogService.class);
        changeDetector = new PersonnelChangeDetector();
        service = new UpdatePersonnelAppService(repository, mapper, validator, logService, changeDetector);
    }

    @Test
    void shouldRecordBasicFieldUpdateLogWithNameChange() {
        Personnel existing = samplePersonnel("张三", "EMP001");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP001", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        var command = upsertCommand("李四", "EMP001", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注", List.of(), List.of(SAMPLE_EDU_ENTRY));

        service.update(1L, command, 1L, "操作人");

        ArgumentCaptor<PersonnelOperationLog> logCaptor = ArgumentCaptor.forClass(PersonnelOperationLog.class);
        verify(logService).save(logCaptor.capture());
        PersonnelOperationLog log = logCaptor.getValue();
        assertThat(log.operationType()).isEqualTo(PersonnelOperationLog.OperationType.UPDATE.name());
        assertThat(log.changeDetails()).contains(new ChangeDetail("name", "张三", "李四"));
    }

    @Test
    void shouldRecordBasicFieldUpdateLogWithDepartmentChange() {
        Personnel existing = samplePersonnel("张三", "EMP001");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP001", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        var command = upsertCommand("张三", "EMP001", "市场部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注", List.of(), List.of(SAMPLE_EDU_ENTRY));

        service.update(1L, command, 1L, "操作人");

        ArgumentCaptor<PersonnelOperationLog> logCaptor = ArgumentCaptor.forClass(PersonnelOperationLog.class);
        verify(logService).save(logCaptor.capture());
        assertThat(logCaptor.getValue().changeDetails())
                .contains(new ChangeDetail("departmentName", "技术部", "市场部"));
    }

    @Test
    void shouldRecordCertificateAddLogIndependently() {
        Certificate existingCert = new Certificate(1L, "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "/old.pdf", "建造师", false, null);
        Personnel existing = new Personnel(1L, "张三", "EMP001", "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", PersonnelStatus.ACTIVE, null, "原备注",
                List.of(existingCert), List.of(SAMPLE_EDU), null, null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP001", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        // 保留旧证书 + 新增一个证书（id 为 null 表示新增）
        var command = upsertCommand("张三", "EMP001", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注",
                List.of(
                        new PersonnelUpsertCommand.CertificateEntry(
                                "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1),
                                "/old.pdf", "建造师", false, null),
                        new PersonnelUpsertCommand.CertificateEntry(
                                "PMP", "C002", CertificateType.PMP,
                                LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1),
                                "/pmp.pdf", "PMP", false, null)
                ),
                List.of(SAMPLE_EDU_ENTRY));

        service.update(1L, command, 1L, "操作人");

        ArgumentCaptor<PersonnelOperationLog> logCaptor = ArgumentCaptor.forClass(PersonnelOperationLog.class);
        verify(logService, atLeastOnce()).save(logCaptor.capture());
        List<PersonnelOperationLog> logs = logCaptor.getAllValues();
        assertThat(logs).extracting(PersonnelOperationLog::operationType)
                .contains(PersonnelOperationLog.OperationType.CERTIFICATE_ADD.name());
        PersonnelOperationLog certAddLog = logs.stream()
                .filter(l -> l.operationType().equals(PersonnelOperationLog.OperationType.CERTIFICATE_ADD.name()))
                .findFirst().orElseThrow();
        assertThat(certAddLog.changeDetails())
                .contains(new ChangeDetail("certificate", "", "PMP"));
    }

    @Test
    void shouldRecordCertificateRemoveLogIndependently() {
        Certificate cert1 = new Certificate(1L, "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "/old.pdf", "建造师", false, null);
        Certificate cert2 = new Certificate(2L, "PMP", "C002", CertificateType.PMP,
                LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1), "/pmp.pdf", "PMP", false, null);
        Personnel existing = new Personnel(1L, "张三", "EMP001", "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", PersonnelStatus.ACTIVE, null, "原备注",
                List.of(cert1, cert2), List.of(SAMPLE_EDU), null, null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP001", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        // 只保留 cert1，删除 cert2
        var command = upsertCommand("张三", "EMP001", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注",
                List.of(new PersonnelUpsertCommand.CertificateEntry(
                        "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                        LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1),
                        "/old.pdf", "建造师", false, null)),
                List.of(SAMPLE_EDU_ENTRY));

        service.update(1L, command, 1L, "操作人");

        ArgumentCaptor<PersonnelOperationLog> logCaptor = ArgumentCaptor.forClass(PersonnelOperationLog.class);
        verify(logService, atLeastOnce()).save(logCaptor.capture());
        List<PersonnelOperationLog> logs = logCaptor.getAllValues();
        assertThat(logs).extracting(PersonnelOperationLog::operationType)
                .contains(PersonnelOperationLog.OperationType.CERTIFICATE_REMOVE.name());
        PersonnelOperationLog certRemoveLog = logs.stream()
                .filter(l -> l.operationType().equals(PersonnelOperationLog.OperationType.CERTIFICATE_REMOVE.name()))
                .findFirst().orElseThrow();
        assertThat(certRemoveLog.changeDetails())
                .contains(new ChangeDetail("certificate", "PMP", ""));
    }

    @Test
    void shouldRecordCertificateRemoveAndAddWhenCommandHasNoId() {
        // PersonnelUpsertCommand.CertificateEntry 无 id 字段，mapper.toCertificate 总是返回 id=null
        // 因此修改现有证书在当前架构下表现为"删除旧证书 + 新增新证书"
        Certificate oldCert = new Certificate(1L, "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "/old.pdf", "建造师", false, null);
        Personnel existing = new Personnel(1L, "张三", "EMP001", "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", PersonnelStatus.ACTIVE, null, "原备注",
                List.of(oldCert), List.of(SAMPLE_EDU), null, null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP001", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        // command 中证书 id 为 null，触发"删除旧 + 新增新"
        var command = upsertCommand("张三", "EMP001", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注",
                List.of(new PersonnelUpsertCommand.CertificateEntry(
                        "一级建造师", "C001-NEW", CertificateType.CONSTRUCTOR,
                        LocalDate.of(2020, 1, 1), LocalDate.of(2026, 6, 30),
                        "/old.pdf", "建造师", false, null)),
                List.of(SAMPLE_EDU_ENTRY));

        service.update(1L, command, 1L, "操作人");

        ArgumentCaptor<PersonnelOperationLog> logCaptor = ArgumentCaptor.forClass(PersonnelOperationLog.class);
        verify(logService, atLeastOnce()).save(logCaptor.capture());
        List<PersonnelOperationLog> logs = logCaptor.getAllValues();
        assertThat(logs).extracting(PersonnelOperationLog::operationType)
                .containsExactlyInAnyOrder(
                        PersonnelOperationLog.OperationType.CERTIFICATE_REMOVE.name(),
                        PersonnelOperationLog.OperationType.CERTIFICATE_ADD.name());
    }

    @Test
    void shouldRecordEducationAddLogIndependently() {
        Education oldEdu = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);
        Personnel existing = new Personnel(1L, "张三", "EMP001", "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", PersonnelStatus.ACTIVE, null, "原备注",
                List.of(), List.of(oldEdu), null, null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP001", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        var command = upsertCommand("张三", "EMP001", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注", List.of(),
                List.of(
                        new PersonnelUpsertCommand.EducationEntry(
                                "清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                                "本科", "全日制", "计算机", false),
                        new PersonnelUpsertCommand.EducationEntry(
                                "北大", LocalDate.of(2019, 9, 1), LocalDate.of(2022, 6, 30),
                                "硕士", "全日制", "AI", false)
                ));

        service.update(1L, command, 1L, "操作人");

        ArgumentCaptor<PersonnelOperationLog> logCaptor = ArgumentCaptor.forClass(PersonnelOperationLog.class);
        verify(logService, atLeastOnce()).save(logCaptor.capture());
        List<PersonnelOperationLog> logs = logCaptor.getAllValues();
        assertThat(logs).extracting(PersonnelOperationLog::operationType)
                .contains(PersonnelOperationLog.OperationType.EDUCATION_ADD.name());
    }

    @Test
    void shouldRecordEducationRemoveLogIndependently() {
        Education edu1 = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);
        Education edu2 = new Education("北大", LocalDate.of(2019, 9, 1), LocalDate.of(2022, 6, 30),
                "硕士", "全日制", "AI", false);
        Personnel existing = new Personnel(1L, "张三", "EMP001", "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", PersonnelStatus.ACTIVE, null, "原备注",
                List.of(), List.of(edu1, edu2), null, null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP001", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        var command = upsertCommand("张三", "EMP001", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注", List.of(),
                List.of(new PersonnelUpsertCommand.EducationEntry(
                        "清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                        "本科", "全日制", "计算机", false)));

        service.update(1L, command, 1L, "操作人");

        ArgumentCaptor<PersonnelOperationLog> logCaptor = ArgumentCaptor.forClass(PersonnelOperationLog.class);
        verify(logService, atLeastOnce()).save(logCaptor.capture());
        List<PersonnelOperationLog> logs = logCaptor.getAllValues();
        assertThat(logs).extracting(PersonnelOperationLog::operationType)
                .contains(PersonnelOperationLog.OperationType.EDUCATION_REMOVE.name());
    }

    @Test
    void shouldNotRecordAnyLogWhenNothingChanged() {
        Personnel existing = samplePersonnel("张三", "EMP001");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP001", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        var command = upsertCommand("张三", "EMP001", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注", List.of(), List.of(SAMPLE_EDU_ENTRY));

        service.update(1L, command, 1L, "操作人");

        verify(logService, never()).save(any());
    }

    @Test
    void shouldStillRecordEmployeeNumberWarningWhenChanged() {
        Personnel existing = samplePersonnel("张三", "EMP001");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmployeeNumber("EMP002", 1L)).thenReturn(false);
        when(repository.save(any(Personnel.class))).thenAnswer(inv -> inv.getArgument(0));

        var command = upsertCommand("张三", "EMP002", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", "原备注", List.of(), List.of(SAMPLE_EDU_ENTRY));

        PersonnelUpdateResult result = service.update(1L, command, 1L, "操作人");

        assertThat(result.warnings()).contains("修改工号将影响外部对账，请确认必要性");
    }

    // ============ 辅助方法 ============

    private static final Education SAMPLE_EDU = new Education("清华",
            LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
            "本科", "全日制", "计算机", false);

    private static final PersonnelUpsertCommand.EducationEntry SAMPLE_EDU_ENTRY =
            new PersonnelUpsertCommand.EducationEntry(
                    "清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                    "本科", "全日制", "计算机", false);

    private Personnel samplePersonnel(String name, String empNo) {
        return Personnel.create(
                1L, name, empNo, "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", PersonnelStatus.ACTIVE, null, "原备注",
                List.of(), List.of(SAMPLE_EDU)
        );
    }

    private PersonnelUpsertCommand upsertCommand(
            String name, String empNo, String deptName,
            String gender, LocalDate entryDate, LocalDate birthDate, String phone,
            String education, String technicalTitle, String remark,
            List<PersonnelUpsertCommand.CertificateEntry> certs,
            List<PersonnelUpsertCommand.EducationEntry> educations) {
        return new PersonnelUpsertCommand(
                name, empNo, "DEPT01", deptName,
                gender, entryDate, birthDate, phone,
                education, technicalTitle, null, remark,
                certs, educations
        );
    }
}
