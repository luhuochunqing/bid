package com.xiyu.bid.personnel.domain.model;

import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.Education;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record Personnel(
        Long id,
        String name,
        String employeeNumber,
        String departmentCode,
        String departmentName,
        String gender,
        LocalDate entryDate,
        String phone,
        String education,
        String technicalTitle,
        PersonnelStatus status,
        String attachmentUrl,
        List<Certificate> certificates,
        List<Education> educations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public Personnel {
        certificates = certificates == null ? List.of() : List.copyOf(certificates);
        educations = educations == null ? List.of() : List.copyOf(educations);
    }

    public static Personnel create(
            Long id, String name, String employeeNumber,
            String departmentCode, String departmentName,
            String gender, LocalDate entryDate, String phone,
            String education, String technicalTitle,
            PersonnelStatus status, String attachmentUrl,
            List<Certificate> certificates,
            List<Education> educations
    ) {
        return new Personnel(id, name, employeeNumber, departmentCode,
                departmentName, gender, entryDate, phone, education, technicalTitle,
                status, attachmentUrl, certificates, educations,
                LocalDateTime.now(), LocalDateTime.now());
    }

    public Personnel withCertificates(List<Certificate> updatedCerts) {
        return new Personnel(id, name, employeeNumber, departmentCode,
                departmentName, gender, entryDate, phone, education, technicalTitle,
                status, attachmentUrl, updatedCerts, educations,
                createdAt, LocalDateTime.now());
    }

    public Personnel withEducations(List<Education> updatedEducations) {
        return new Personnel(id, name, employeeNumber, departmentCode,
                departmentName, gender, entryDate, phone, education, technicalTitle,
                status, attachmentUrl, certificates, updatedEducations,
                createdAt, LocalDateTime.now());
    }

    public Personnel withStatus(PersonnelStatus newStatus) {
        return new Personnel(id, name, employeeNumber, departmentCode,
                departmentName, gender, entryDate, phone, education, technicalTitle,
                newStatus, attachmentUrl, certificates, educations,
                createdAt, LocalDateTime.now());
    }

    /**
     * 编辑证书子节专用：同时更新基本信息 + 教育经历 + 证书。
     * 工号变更的警示由 Application Service 负责返回。
     */
    public Personnel withEmployeeNumber(String newEmployeeNumber) {
        return new Personnel(
                id, name, newEmployeeNumber, departmentCode, departmentName,
                gender, entryDate, phone, education, technicalTitle,
                status, attachmentUrl, certificates, educations,
                createdAt, LocalDateTime.now()
        );
    }

    /**
     * 编辑证书子节专用：变更工号时返回更新后的状态 + 必须展示的警示信息。
     * 如果工号未发生变化，则 warningMessage 为 null。
     */
    public record EmployeeNumberChangeResult(
            Personnel updatedPersonnel,
            String warningMessage   // 仅当工号真正变更时才非 null
    ) {}

    public EmployeeNumberChangeResult withEmployeeNumberChange(String newEmployeeNumber) {
        boolean changed = !java.util.Objects.equals(this.employeeNumber, newEmployeeNumber);

        Personnel updated = this.withEmployeeNumber(newEmployeeNumber);

        String warning = null;
        if (changed) {
            warning = "修改工号将影响外部对账，请确认必要性";
        }

        return new EmployeeNumberChangeResult(updated, warning);
    }

    public Personnel withUpdatedDetails(
            String newName,
            String newEmployeeNumber,
            String newDepartmentName,
            String newGender,
            LocalDate newEntryDate,
            String newPhone,
            String newEducation,
            String newTechnicalTitle,
            List<Certificate> newCertificates,
            List<Education> newEducations
    ) {
        return new Personnel(
                id,
                newName,
                newEmployeeNumber,
                departmentCode,
                newDepartmentName,
                newGender,
                newEntryDate,
                newPhone,
                newEducation,
                newTechnicalTitle,
                status,
                attachmentUrl,
                newCertificates,
                newEducations,
                createdAt,
                LocalDateTime.now()
        );
    }

    /**
     * 编辑证书子节：表示一次证书附件替换操作。
     * 领域层只负责构建新状态，实际软删除旧记录由持久化层负责。
     */
    public record CertificateAttachmentReplacement(
            Long oldCertificateId,
            Certificate newCertificate
    ) {}

    /**
     * 「删除人员」h5 专用：软删除（状态变为已停用）。
     * 纯核心方法，仅改变状态，不处理证书提醒停止（由应用层协调）。
     */
    public Personnel softDelete() {
        return withStatus(PersonnelStatus.INACTIVE);
    }

    /**
     * 「删除人员」h5 专用：恢复已停用人员。
     */
    public Personnel restore() {
        return withStatus(PersonnelStatus.ACTIVE);
    }
}
