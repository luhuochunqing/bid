package com.xiyu.bid.personnel.application.mapper;

import com.xiyu.bid.personnel.application.command.PersonnelUpsertCommand;
import com.xiyu.bid.personnel.application.dto.CertificateDTO;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.Education;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class PersonnelMapper {

    public PersonnelDTO toDTO(Personnel p) {
        if (p == null) return null;

        List<CertificateDTO> certDTOs = p.certificates().stream()
                .map(this::toCertDTO)
                .toList();

        List<PersonnelDTO.EducationDTO> eduDTOs = p.educations().stream()
                .map(this::toEducationDTO)
                .toList();

        // ===== 4.3 "查看证书" h5 计算字段（纯展示计算，放 mapper 层便于前端一致） =====
        Integer years = computeYearsOfService(p.entryDate());
        String highestEdu = computeHighestEducation(p.educations(), p.education());
        int certCount = certDTOs.size();
        int expiringCount = (int) p.certificates().stream()
                .filter(c -> c != null && !c.isExpired() && c.remainingDays() <= 60 && c.remainingDays() < Long.MAX_VALUE)
                .count();

        return new PersonnelDTO(
                p.id(), p.name(), p.employeeNumber(),
                p.departmentCode(), p.departmentName(),
                p.gender(), p.entryDate(), p.birthDate(), p.phone(),
                p.education(), p.technicalTitle(),
                p.status(), p.attachmentUrl(), p.remark(),
                certDTOs, eduDTOs, 0,
                years, highestEdu, certCount, expiringCount,
                p.createdAt(), p.updatedAt()
        );
    }

    private Integer computeYearsOfService(LocalDate entryDate) {
        if (entryDate == null) return null;
        int yearDiff = LocalDate.now().getYear() - entryDate.getYear();
        // 简单年限（不考虑月份精确，可后续增强）
        return Math.max(0, yearDiff);
    }

    private String computeHighestEducation(List<com.xiyu.bid.personnel.domain.valueobject.Education> educations, String legacyEdu) {
        if (educations != null && !educations.isEmpty()) {
            // 优先级：博士 > 硕士 > 本科 > 大专 > 中专 > 高中 > 初中 > 其他
            java.util.Map<String, Integer> priority = java.util.Map.of(
                    "博士", 7, "硕士", 6, "本科", 5, "大专", 4,
                    "中专", 3, "高中", 2, "初中", 1
            );
            return educations.stream()
                    .map(e -> e.highestEducation())
                    .filter(java.util.Objects::nonNull)
                    .max((a, b) -> Integer.compare(
                            priority.getOrDefault(a, 0),
                            priority.getOrDefault(b, 0)))
                    .orElse(legacyEdu);
        }
        return legacyEdu;
    }

    public CertificateDTO toCertDTO(Certificate c) {
        if (c == null) return null;
        String status = computeCertStatus(c);
        return new CertificateDTO(
                c.id(), c.name(), c.certificateNumber(), c.type(),
                c.issueDate(), c.expiryDate(), c.attachmentUrl(),
                c.title(), c.isPermanent(), c.remark(),
                c.isExpired(), c.remainingDays(),
                status
        );
    }

    private String computeCertStatus(Certificate c) {
        if (c.isPermanent()) return "PERMANENT";
        if (c.isExpired()) return "EXPIRED";
        if (c.isExpiringSoon(30)) return "EXPIRING";
        return "VALID";
    }

    public List<Certificate> toCertificateList(List<PersonnelUpsertCommand.CertificateEntry> entries) {
        if (entries == null) return List.of();
        return entries.stream()
                .filter(e -> e != null)
                .map(this::toCertificate)
                .toList();
    }

    private Certificate toCertificate(PersonnelUpsertCommand.CertificateEntry e) {
        return new Certificate(
                null, e.name(), e.certificateNumber(), e.type(),
                e.issueDate(), e.expiryDate(), e.attachmentUrl(),
                e.title(), e.isPermanent(), e.remark()
        );
    }

    // ==================== Education 映射（新增，支持蓝图 4.3 新增证书 Tab 2） ====================

    public List<Education> toEducationList(List<PersonnelUpsertCommand.EducationEntry> entries) {
        if (entries == null) return List.of();
        return entries.stream()
                .filter(e -> e != null)
                .map(this::toEducation)
                .toList();
    }

    private Education toEducation(PersonnelUpsertCommand.EducationEntry e) {
        return new Education(
                e.schoolName(),
                e.startDate(),
                e.endDate(),
                e.highestEducation(),
                e.studyForm(),
                e.major(),
                e.isHighestEducationSchool()
        );
    }

    public PersonnelDTO.EducationDTO toEducationDTO(Education e) {
        if (e == null) return null;
        return new PersonnelDTO.EducationDTO(
                null, // id 由持久化层回填（本迭代简化）
                e.schoolName(),
                e.startDate(),
                e.endDate(),
                e.highestEducation(),
                e.studyForm(),
                e.major(),
                e.isHighestEducationSchool()
        );
    }
}
