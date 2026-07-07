package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.domain.importvalidation.ParsedCertificateRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedEducationRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedPersonnelRow;
import com.xiyu.bid.personnel.domain.importvalidation.ValidationResult;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelExcelImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CO-528: 人员证书批量导入计数改为人员维度 + 工号已存在报错。
 *
 * 覆盖两个核心改动：
 * 1. 计数从行级（3 sheets 求和）改为人级（仅 Sheet1 人数）
 *    - 成功：1 人 + N 条教育 + M 条证书 → successCount=1（不是 1+N+M）
 *    - 失败：1 人失败 + 级联教育/证书失败 → failureCount=1（不是 1+N+M）
 * 2. 工号已存在时报错（不再 upsert 更新）
 *    - existing 非空 → 抛错 + errorDetails 记录"工号已存在"
 */
class PersonnelImportExecutorTest {

    private PersonnelRepository personnelRepository;
    private PersonnelImportExecutor importExecutor;

    @BeforeEach
    void setUp() {
        personnelRepository = mock(PersonnelRepository.class);
        importExecutor = new PersonnelImportExecutor(personnelRepository);
    }

    private static ParsedPersonnelRow basicRow(String empNo, String name) {
        return new ParsedPersonnelRow(2, empNo, name, "男",
                LocalDate.of(2024, 1, 1), LocalDate.of(1990, 1, 1), "13800000000",
                "本科", null, "研发部", null);
    }

    private static ParsedEducationRow eduRow(String empNo) {
        return new ParsedEducationRow(3, empNo, "张三", "清华大学",
                LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                "本科", "全日制", "计算机", true, "是");
    }

    private static ParsedCertificateRow certRow(String empNo) {
        return new ParsedCertificateRow(4, empNo, "张三", "一级建造师",
                "CERT001", "CONSTRUCTOR", LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1),
                "PER_张三_EMP001_01_一级建造师.pdf", "高级", false, "否", null);
    }

    private static PersonnelExcelImporter.ImportResult importResult(
            List<ParsedPersonnelRow> p, List<ParsedEducationRow> e, List<ParsedCertificateRow> c) {
        return new PersonnelExcelImporter.ImportResult(p, e, c, ValidationResult.empty());
    }

    private static PersonnelImportExecutor.ImportProgressCallback noopCallback() {
        return (message, percent) -> {};
    }

    /** 构建一个带 id 的已保存 Personnel（模拟 save() 返回值） */
    private static Personnel savedPersonnel(String empNo, String name, long id) {
        return Personnel.create(id, name, empNo,
                null, "研发部", "男", LocalDate.of(2024, 1, 1), LocalDate.of(1990, 1, 1),
                "13800000000", "本科", null, null, null, null, List.of(), List.of());
    }

    // ===== 计数：人员维度，不是行维度 =====

    @Test
    void should_count_by_person_level_not_row_level() {
        // 1 人 + 1 教育 + 1 证书，全部成功
        PersonnelExcelImporter.ImportResult result = importResult(
                List.of(basicRow("EMP001", "张三")),
                List.of(eduRow("EMP001")),
                List.of(certRow("EMP001")));

        when(personnelRepository.findByEmployeeNumber("EMP001")).thenReturn(List.of());
        when(personnelRepository.save(any(Personnel.class)))
                .thenReturn(savedPersonnel("EMP001", "张三", 100L));
        when(personnelRepository.addEducation(eq(100L), any())).thenReturn(null);
        when(personnelRepository.addCertificate(eq(100L), any())).thenReturn(null);

        PersonnelImportExecutor.ImportResult importResult = importExecutor.executeImport(
                result, noopCallback());

        // totalCount 应为 1（人员数），不是 3（行数）
        assertThat(importResult.totalCount()).isEqualTo(1);
        assertThat(importResult.successCount()).isEqualTo(1);
        assertThat(importResult.failureCount()).isEqualTo(0);
    }

    @Test
    void should_count_failed_person_once_with_cascade() {
        // 1 人在 personnel 阶段失败，其教育/证书行级联失败
        PersonnelExcelImporter.ImportResult result = importResult(
                List.of(basicRow("EMP001", "张三")),
                List.of(eduRow("EMP001")),
                List.of(certRow("EMP001")));

        // personnel save 抛错 → empNo 不进 empNoToId → 教育/证书级联报"关联的人员不存在"
        when(personnelRepository.findByEmployeeNumber("EMP001")).thenReturn(List.of());
        when(personnelRepository.save(any(Personnel.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        PersonnelImportExecutor.ImportResult importResult = importExecutor.executeImport(
                result, noopCallback());

        // failureCount 应为 1（1 个人员失败），不是 3（行级级联）
        assertThat(importResult.totalCount()).isEqualTo(1);
        assertThat(importResult.failureCount()).isEqualTo(1);
        assertThat(importResult.successCount()).isEqualTo(0);
    }

    @Test
    void should_count_mixed_success_and_failure_by_person() {
        // 2 人：EMP001 成功，EMP002 工号已存在失败
        PersonnelExcelImporter.ImportResult result = importResult(
                List.of(basicRow("EMP001", "张三"), basicRow("EMP002", "李四")),
                List.of(eduRow("EMP001")),
                List.of(certRow("EMP001")));

        when(personnelRepository.findByEmployeeNumber("EMP001")).thenReturn(List.of());
        when(personnelRepository.findByEmployeeNumber("EMP002"))
                .thenReturn(List.of(Personnel.create(50L, "旧李四", "EMP002",
                        null, "研发部", "男", LocalDate.of(2020, 1, 1), null, null,
                        null, null, null, null, null, List.of(), List.of())));
        when(personnelRepository.save(any(Personnel.class)))
                .thenReturn(savedPersonnel("EMP001", "张三", 100L));
        when(personnelRepository.addEducation(eq(100L), any())).thenReturn(null);
        when(personnelRepository.addCertificate(eq(100L), any())).thenReturn(null);

        PersonnelImportExecutor.ImportResult importResult = importExecutor.executeImport(
                result, noopCallback());

        assertThat(importResult.totalCount()).isEqualTo(2);
        assertThat(importResult.successCount()).isEqualTo(1);
        assertThat(importResult.failureCount()).isEqualTo(1);
    }

    // ===== 工号已存在 → 报错（不再 upsert） =====

    @Test
    void should_reject_existing_employee_number() {
        PersonnelExcelImporter.ImportResult result = importResult(
                List.of(basicRow("EMP001", "张三")),
                List.of(),
                List.of());

        // 工号已存在
        when(personnelRepository.findByEmployeeNumber("EMP001"))
                .thenReturn(List.of(Personnel.create(50L, "旧张三", "EMP001",
                        null, "研发部", "男", LocalDate.of(2020, 1, 1), null, null,
                        null, null, null, null, null, List.of(), List.of())));

        PersonnelImportExecutor.ImportResult importResult = importExecutor.executeImport(
                result, noopCallback());

        assertThat(importResult.failureCount()).isEqualTo(1);
        assertThat(importResult.errorDetails())
                .anyMatch(e -> e.employeeNumber().equals("EMP001")
                        && e.errorMessage().contains("工号已存在"));
    }

    @Test
    void should_not_call_save_when_employee_number_exists() {
        PersonnelExcelImporter.ImportResult result = importResult(
                List.of(basicRow("EMP001", "张三")),
                List.of(),
                List.of());

        when(personnelRepository.findByEmployeeNumber("EMP001"))
                .thenReturn(List.of(Personnel.create(50L, "旧张三", "EMP001",
                        null, "研发部", "男", LocalDate.of(2020, 1, 1), null, null,
                        null, null, null, null, null, List.of(), List.of())));

        importExecutor.executeImport(result, noopCallback());

        // 不应调用 save（不更新已有人员）
        org.mockito.Mockito.verify(personnelRepository,
                org.mockito.Mockito.never()).save(any(Personnel.class));
    }
}
