package com.xiyu.bid.personnel.domain.service;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog.ChangeDetail;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.CertificateType;
import com.xiyu.bid.personnel.domain.valueobject.Education;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PersonnelChangeDetector 单元测试（CO-417）
 * 验证结构化字段级 diff 生成，覆盖基础字段/证书/教育经历三类变更。
 */
class PersonnelChangeDetectorTest {

    private final PersonnelChangeDetector detector = new PersonnelChangeDetector();

    // ============ 基础字段 diff ============

    @Test
    void shouldDetectNameChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("李四", "EMP001", old.departmentName(),
                old.gender(), old.entryDate(), old.birthDate(), old.phone(), old.education(),
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("name", "张三", "李四"));
    }

    @Test
    void shouldDetectDepartmentChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", "市场部",
                old.gender(), old.entryDate(), old.birthDate(), old.phone(), old.education(),
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("departmentName", "技术部", "市场部"));
    }

    @Test
    void shouldDetectGenderChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", old.departmentName(),
                "女", old.entryDate(), old.birthDate(), old.phone(), old.education(),
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("gender", "男", "女"));
    }

    @Test
    void shouldDetectEntryDateChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", old.departmentName(),
                old.gender(), LocalDate.of(2021, 1, 1), old.birthDate(), old.phone(), old.education(),
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("entryDate", "2020-03-01", "2021-01-01"));
    }

    @Test
    void shouldDetectBirthDateChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", old.departmentName(),
                old.gender(), old.entryDate(), LocalDate.of(1996, 5, 5), old.phone(), old.education(),
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("birthDate", "1995-01-01", "1996-05-05"));
    }

    @Test
    void shouldDetectPhoneChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", old.departmentName(),
                old.gender(), old.entryDate(), old.birthDate(), "13900000000", old.education(),
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("phone", "13800138000", "13900000000"));
    }

    @Test
    void shouldDetectEducationChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", old.departmentName(),
                old.gender(), old.entryDate(), old.birthDate(), old.phone(), "硕士",
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("education", "本科", "硕士"));
    }

    @Test
    void shouldDetectTechnicalTitleChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", old.departmentName(),
                old.gender(), old.entryDate(), old.birthDate(), old.phone(), old.education(),
                "正高级工程师", old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("technicalTitle", "高级工程师", "正高级工程师"));
    }

    @Test
    void shouldDetectRemarkChange() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", old.departmentName(),
                old.gender(), old.entryDate(), old.birthDate(), old.phone(), old.education(),
                old.technicalTitle(), "新备注", old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(new ChangeDetail("remark", "原备注", "新备注"));
    }

    @Test
    void shouldReturnEmptyWhenNoBasicFieldChanged() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("张三", "EMP001", old.departmentName(),
                old.gender(), old.entryDate(), old.birthDate(), old.phone(), old.education(),
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).isEmpty();
    }

    @Test
    void shouldDetectMultipleBasicFieldChanges() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails("李四", "EMP001", "市场部",
                old.gender(), old.entryDate(), old.birthDate(), old.phone(), old.education(),
                old.technicalTitle(), old.remark(), old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactly(
                new ChangeDetail("name", "张三", "李四"),
                new ChangeDetail("departmentName", "技术部", "市场部")
        );
    }

    @Test
    void shouldHandleNullFieldsAsEmptyString() {
        Personnel old = samplePersonnel("张三", "EMP001");
        Personnel updated = old.withUpdatedDetails(null, "EMP001", null,
                null, old.entryDate(), old.birthDate(), null, null,
                null, null, old.certificates(), old.educations());

        List<ChangeDetail> changes = detector.detectBasicFieldChanges(old, updated);

        assertThat(changes).containsExactlyInAnyOrder(
                new ChangeDetail("name", "张三", ""),
                new ChangeDetail("departmentName", "技术部", ""),
                new ChangeDetail("gender", "男", ""),
                new ChangeDetail("phone", "13800138000", ""),
                new ChangeDetail("education", "本科", ""),
                new ChangeDetail("technicalTitle", "高级工程师", ""),
                new ChangeDetail("remark", "原备注", "")
        );
    }

    // ============ 证书变更 diff ============

    @Test
    void shouldDetectCertificateAdded() {
        Certificate oldCert = new Certificate(1L, "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "/old.pdf", "建造师", false, null);
        Certificate newCert1 = new Certificate(1L, "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "/old.pdf", "建造师", false, null);
        Certificate newCert2 = new Certificate(null, "PMP", "C002", CertificateType.PMP,
                LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1), "/pmp.pdf", "PMP", false, null);

        List<ChangeDetail> changes = detector.detectCertificateChanges(
                List.of(oldCert), List.of(newCert1, newCert2));

        assertThat(changes).containsExactly(
                new ChangeDetail("certificate", "", "PMP")
        );
    }

    @Test
    void shouldDetectCertificateRemoved() {
        Certificate oldCert1 = new Certificate(1L, "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "/old.pdf", "建造师", false, null);
        Certificate oldCert2 = new Certificate(2L, "PMP", "C002", CertificateType.PMP,
                LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1), "/pmp.pdf", "PMP", false, null);

        List<ChangeDetail> changes = detector.detectCertificateChanges(
                List.of(oldCert1, oldCert2), List.of(oldCert1));

        assertThat(changes).containsExactly(
                new ChangeDetail("certificate", "PMP", "")
        );
    }

    @Test
    void shouldDetectCertificateFieldUpdate() {
        Certificate oldCert = new Certificate(1L, "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "/old.pdf", "建造师", false, null);
        Certificate updatedCert = new Certificate(1L, "一级建造师", "C001-NEW", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2026, 6, 30), "/old.pdf", "建造师", false, null);

        List<ChangeDetail> changes = detector.detectCertificateChanges(
                List.of(oldCert), List.of(updatedCert));

        assertThat(changes).containsExactlyInAnyOrder(
                new ChangeDetail("certificateNumber", "C001", "C001-NEW"),
                new ChangeDetail("expiryDate", "2025-01-01", "2026-06-30")
        );
    }

    @Test
    void shouldReturnEmptyWhenCertificateUnchanged() {
        Certificate cert = new Certificate(1L, "一级建造师", "C001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "/old.pdf", "建造师", false, null);

        List<ChangeDetail> changes = detector.detectCertificateChanges(
                List.of(cert), List.of(cert));

        assertThat(changes).isEmpty();
    }

    @Test
    void shouldHandleNewCertificateWithNullIdAsAdded() {
        Certificate newCert = new Certificate(null, "PMP", "C002", CertificateType.PMP,
                LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1), "/pmp.pdf", "PMP", false, null);

        List<ChangeDetail> changes = detector.detectCertificateChanges(
                List.of(), List.of(newCert));

        assertThat(changes).containsExactly(
                new ChangeDetail("certificate", "", "PMP")
        );
    }

    // ============ 教育经历变更 diff ============

    @Test
    void shouldDetectEducationAdded() {
        Education oldEdu = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);
        Education newEdu1 = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);
        Education newEdu2 = new Education("北大", LocalDate.of(2019, 9, 1), LocalDate.of(2022, 6, 30),
                "硕士", "全日制", "AI", false);

        List<ChangeDetail> changes = detector.detectEducationChanges(
                List.of(oldEdu), List.of(newEdu1, newEdu2));

        assertThat(changes).containsExactly(
                new ChangeDetail("education", "", "北大")
        );
    }

    @Test
    void shouldDetectEducationRemoved() {
        Education oldEdu1 = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);
        Education oldEdu2 = new Education("北大", LocalDate.of(2019, 9, 1), LocalDate.of(2022, 6, 30),
                "硕士", "全日制", "AI", false);

        List<ChangeDetail> changes = detector.detectEducationChanges(
                List.of(oldEdu1, oldEdu2), List.of(oldEdu1));

        assertThat(changes).containsExactly(
                new ChangeDetail("education", "北大", "")
        );
    }

    @Test
    void shouldDetectEducationFieldUpdateBySchoolName() {
        Education oldEdu = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);
        Education updatedEdu = new Education("北大", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);

        List<ChangeDetail> changes = detector.detectEducationChanges(
                List.of(oldEdu), List.of(updatedEdu));

        assertThat(changes).containsExactly(
                new ChangeDetail("schoolName", "清华", "北大")
        );
    }

    @Test
    void shouldDetectEducationFieldUpdateByMajor() {
        Education oldEdu = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);
        Education updatedEdu = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "软件工程", false);

        List<ChangeDetail> changes = detector.detectEducationChanges(
                List.of(oldEdu), List.of(updatedEdu));

        assertThat(changes).containsExactly(
                new ChangeDetail("major", "计算机", "软件工程")
        );
    }

    @Test
    void shouldReturnEmptyWhenEducationUnchanged() {
        Education edu = new Education("清华", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false);

        List<ChangeDetail> changes = detector.detectEducationChanges(
                List.of(edu), List.of(edu));

        assertThat(changes).isEmpty();
    }

    // ============ 辅助方法 ============

    private Personnel samplePersonnel(String name, String empNo) {
        return Personnel.create(
                1L, name, empNo, "DEPT01", "技术部",
                "男", LocalDate.of(2020, 3, 1), LocalDate.of(1995, 1, 1), "13800138000",
                "本科", "高级工程师", PersonnelStatus.ACTIVE, null, "原备注",
                List.of(), List.of()
        );
    }
}
