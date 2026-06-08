package com.xiyu.bid.personnel.infrastructure.persistence;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.Education;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelCertificateEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelEducationEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelCertificateJpaRepository;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelEducationJpaRepository;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PersonnelRepositoryAdapter implements PersonnelRepository {

    private final PersonnelJpaRepository jpaRepository;
    private final PersonnelCertificateJpaRepository certRepository;
    private final PersonnelEducationJpaRepository educationRepository;

    @Override
    @Transactional
    public Personnel save(Personnel personnel) {
        PersonnelEntity entity = toEntity(personnel);
        PersonnelEntity saved = jpaRepository.save(entity);

        // 处理证书（原有逻辑）
        certRepository.deleteByPersonnelId(saved.getId());
        for (Certificate cert : personnel.certificates()) {
            PersonnelCertificateEntity certEntity = toCertEntity(cert, saved.getId());
            certRepository.save(certEntity);
        }

        // 处理教育经历（新增）
        educationRepository.deleteByPersonnelId(saved.getId());
        for (var edu : personnel.educations()) {
            PersonnelEducationEntity eduEntity = toEducationEntity(edu, saved.getId());
            educationRepository.save(eduEntity);
        }

        return findById(saved.getId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Personnel> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Personnel> findAll(PersonnelListCriteria criteria) {
        // 使用完整扩展查询（支持所有筛选与搜索条件）
        return jpaRepository.findByCriteriaFull(
                criteria.keyword(), criteria.departmentCode(), criteria.status(),
                criteria.gender(), criteria.entryDateFrom(), criteria.entryDateTo(),
                criteria.highestEducations(), criteria.studyForms(), criteria.majorKeyword(),
                criteria.certificateKeyword(), criteria.certificateStatuses()
        ).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Personnel> findAllPageable(PersonnelListCriteria criteria, int pageNumber, int pageSize) {
        // 使用完整扩展查询（支持所有筛选与搜索条件）
        Page<PersonnelEntity> page = jpaRepository.findByCriteriaPageableFull(
                criteria.keyword(), criteria.departmentCode(), criteria.status(),
                criteria.gender(), criteria.entryDateFrom(), criteria.entryDateTo(),
                criteria.highestEducations(), criteria.studyForms(), criteria.majorKeyword(),
                criteria.certificateKeyword(), criteria.certificateStatuses(),
                PageRequest.of(pageNumber, pageSize)
        );
        List<Personnel> content = page.getContent().stream().map(this::toDomain).toList();
        return PagedResult.of(content, page.getTotalElements(), pageNumber, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Personnel> findByEmployeeNumber(String employeeNumber) {
        return jpaRepository.findByEmployeeNumber(employeeNumber)
                .map(this::toDomain).map(List::of).orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmployeeNumber(String employeeNumber, Long excludeId) {
        return jpaRepository.findByEmployeeNumberExcluding(employeeNumber, excludeId).isPresent();
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        certRepository.deleteByPersonnelId(id);
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Personnel addCertificate(Long personnelId, Certificate certificate) {
        PersonnelCertificateEntity certEntity = new PersonnelCertificateEntity();
        certEntity.setPersonnelId(personnelId);
        certEntity.setCertificateName(certificate.name());
        certEntity.setCertificateNumber(certificate.certificateNumber());
        certEntity.setCertificateType(certificate.type());
        certEntity.setIssueDate(certificate.issueDate());
        certEntity.setExpiryDate(certificate.expiryDate());
        certEntity.setAttachmentUrl(certificate.attachmentUrl());
        certEntity.setTitle(certificate.title());
        certEntity.setPermanent(certificate.isPermanent());
        certEntity.setRemark(certificate.remark());
        certRepository.save(certEntity);
        return findById(personnelId).orElseThrow();
    }

    @Override
    @Transactional
    public Personnel addEducation(Long personnelId, Education education) {
        PersonnelEducationEntity eduEntity = new PersonnelEducationEntity();
        eduEntity.setPersonnelId(personnelId);
        eduEntity.setSchoolName(education.schoolName());
        eduEntity.setStartDate(education.startDate());
        eduEntity.setEndDate(education.endDate());
        eduEntity.setHighestEducation(education.highestEducation());
        eduEntity.setStudyForm(education.studyForm());
        eduEntity.setMajor(education.major());
        eduEntity.setHighestEducationSchool(education.isHighestEducationSchool());
        educationRepository.save(eduEntity);
        return findById(personnelId).orElseThrow();
    }

    @Override
    @Transactional
    public Personnel removeCertificate(Long personnelId, Long certificateId) {
        // 编辑证书子节：支持软删除（替换附件时也走软删除）
        certRepository.findById(certificateId).ifPresent(cert -> {
            cert.softDelete();
            certRepository.save(cert);
        });
        return findById(personnelId).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findExpiringCertificates(int warningDays) {
        LocalDate threshold = LocalDate.now().plusDays(warningDays);
        return certRepository.findExpiringByThreshold(threshold).stream()
                .filter(cert -> !cert.isDeleted())  // 编辑证书子节：排除已软删除的证书
                .map(this::toCertDomain).toList();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    private PersonnelEntity toEntity(Personnel p) {
        PersonnelEntity e = new PersonnelEntity();
        if (p.id() != null) e.setId(p.id());
        e.setName(p.name());
        e.setEmployeeNumber(p.employeeNumber());
        e.setDepartmentCode(p.departmentCode());
        e.setDepartmentName(p.departmentName());
        e.setEducation(p.education());
        e.setTechnicalTitle(p.technicalTitle());
        e.setStatus(p.status());
        e.setAttachmentUrl(p.attachmentUrl());
        e.setRemark(p.remark());
        e.setBirthDate(p.birthDate());
        e.setCreatedAt(p.createdAt());
        e.setUpdatedAt(p.updatedAt());
        return e;
    }

    private Personnel toDomain(PersonnelEntity e) {
        // 编辑证书子节：使用 Repository 方法直接查询未删除记录
        List<Certificate> certs = certRepository.findByPersonnelIdAndDeletedAtIsNull(e.getId()).stream()
                .map(this::toCertDomain).toList();

        List<Education> educations =
                educationRepository.findByPersonnelId(e.getId()).stream()
                        .map(this::toEducationDomain).toList();

        return new Personnel(
                e.getId(), e.getName(), e.getEmployeeNumber(),
                e.getDepartmentCode(), e.getDepartmentName(),
                e.getGender(), e.getEntryDate(), e.getBirthDate(), e.getPhone(),
                e.getEducation(), e.getTechnicalTitle(),
                e.getStatus(), e.getAttachmentUrl(), e.getRemark(),
                certs, educations,
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private PersonnelCertificateEntity toCertEntity(Certificate c, Long personnelId) {
        PersonnelCertificateEntity e = new PersonnelCertificateEntity();
        if (c.id() != null) e.setId(c.id());
        e.setPersonnelId(personnelId);
        e.setCertificateName(c.name());
        e.setCertificateNumber(c.certificateNumber());
        e.setCertificateType(c.type());
        e.setIssueDate(c.issueDate());
        e.setExpiryDate(c.expiryDate());
        e.setAttachmentUrl(c.attachmentUrl());
        e.setTitle(c.title());
        e.setPermanent(c.isPermanent());
        e.setRemark(c.remark());
        return e;
    }

    private Certificate toCertDomain(PersonnelCertificateEntity e) {
        return new Certificate(
                e.getId(), e.getCertificateName(), e.getCertificateNumber(),
                e.getCertificateType(), e.getIssueDate(), e.getExpiryDate(),
                e.getAttachmentUrl(), e.getTitle(), e.isPermanent(), e.getRemark()
        );
    }

    // ==================== Education 持久化辅助方法 ====================

    private PersonnelEducationEntity toEducationEntity(
            com.xiyu.bid.personnel.domain.valueobject.Education e, Long personnelId) {

        PersonnelEducationEntity entity = new PersonnelEducationEntity();
        entity.setPersonnelId(personnelId);
        entity.setSchoolName(e.schoolName());
        entity.setStartDate(e.startDate());
        entity.setEndDate(e.endDate());
        entity.setHighestEducation(e.highestEducation());
        entity.setStudyForm(e.studyForm());
        entity.setMajor(e.major());
        entity.setHighestEducationSchool(e.isHighestEducationSchool());
        return entity;
    }

    private com.xiyu.bid.personnel.domain.valueobject.Education toEducationDomain(
            PersonnelEducationEntity e) {

        return new com.xiyu.bid.personnel.domain.valueobject.Education(
                e.getSchoolName(),
                e.getStartDate(),
                e.getEndDate(),
                e.getHighestEducation(),
                e.getStudyForm(),
                e.getMajor(),
                e.isHighestEducationSchool()
        );
    }
}
