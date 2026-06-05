package com.xiyu.bid.personnel.infrastructure.persistence.repository;

import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonnelJpaRepository extends JpaRepository<PersonnelEntity, Long> {

    Optional<PersonnelEntity> findByEmployeeNumber(String employeeNumber);

    @Query("SELECT p FROM PersonnelEntity p WHERE p.employeeNumber = :empNo AND (:excludeId IS NULL OR p.id != :excludeId)")
    Optional<PersonnelEntity> findByEmployeeNumberExcluding(@Param("empNo") String empNo, @Param("excludeId") Long excludeId);

    // 扩展后的查询方法（支持「筛选与搜索」h5 的所有新条件，逐步实现中）
    @Query("SELECT p FROM PersonnelEntity p WHERE " +
           "(:keyword IS NULL OR p.name LIKE %:keyword% OR p.employeeNumber LIKE %:keyword%) AND " +
           "(:departmentCode IS NULL OR p.departmentCode = :departmentCode) AND " +
           "(:status IS NULL OR p.status = :status)")
    List<PersonnelEntity> findByCriteria(@Param("keyword") String keyword,
                                         @Param("departmentCode") String departmentCode,
                                         @Param("status") PersonnelStatus status);

    @Query("SELECT p FROM PersonnelEntity p WHERE " +
           "(:keyword IS NULL OR p.name LIKE %:keyword% OR p.employeeNumber LIKE %:keyword%) AND " +
           "(:departmentCode IS NULL OR p.departmentCode = :departmentCode) AND " +
           "(:status IS NULL OR p.status = :status)")
    Page<PersonnelEntity> findByCriteriaPageable(@Param("keyword") String keyword,
                                                 @Param("departmentCode") String departmentCode,
                                                 @Param("status") PersonnelStatus status,
                                                 Pageable pageable);

    // === 筛选与搜索 h5 完整扩展查询（使用子查询方式，避免实体关联假设） ===
    @Query("SELECT p FROM PersonnelEntity p WHERE " +
           "(:keyword IS NULL OR p.name LIKE %:keyword% OR p.employeeNumber LIKE %:keyword%) AND " +
           "(:departmentCode IS NULL OR p.departmentCode = :departmentCode) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:gender IS NULL OR p.gender = :gender) AND " +
           "(:entryDateFrom IS NULL OR p.entryDate >= :entryDateFrom) AND " +
           "(:entryDateTo IS NULL OR p.entryDate <= :entryDateTo) AND " +
           "(:highestEducations IS NULL OR EXISTS (SELECT 1 FROM PersonnelEducationEntity e WHERE e.personnelId = p.id AND e.highestEducation IN :highestEducations)) AND " +
           "(:studyForms IS NULL OR EXISTS (SELECT 1 FROM PersonnelEducationEntity e WHERE e.personnelId = p.id AND e.studyForm IN :studyForms)) AND " +
           "(:majorKeyword IS NULL OR EXISTS (SELECT 1 FROM PersonnelEducationEntity e WHERE e.personnelId = p.id AND e.major LIKE %:majorKeyword%)) AND " +
           "(:certificateKeyword IS NULL OR EXISTS (SELECT 1 FROM PersonnelCertificateEntity c WHERE c.personnelId = p.id AND c.certificateName LIKE %:certificateKeyword%)) AND " +
           "(:certificateStatuses IS NULL OR EXISTS (SELECT 1 FROM PersonnelCertificateEntity c WHERE c.personnelId = p.id AND " +
           "   ( (:certificateStatuses IS NOT NULL AND 'VALID' IN :certificateStatuses AND (c.expiryDate IS NULL OR c.expiryDate > CURRENT_DATE)) OR " +
           "     (:certificateStatuses IS NOT NULL AND 'EXPIRING' IN :certificateStatuses AND c.expiryDate IS NOT NULL AND c.expiryDate <= CURRENT_DATE + 60 DAY AND c.expiryDate > CURRENT_DATE) OR " +
           "     (:certificateStatuses IS NOT NULL AND 'EXPIRED' IN :certificateStatuses AND c.expiryDate IS NOT NULL AND c.expiryDate <= CURRENT_DATE) )))")
    List<PersonnelEntity> findByCriteriaFull(@Param("keyword") String keyword,
                                             @Param("departmentCode") String departmentCode,
                                             @Param("status") PersonnelStatus status,
                                             @Param("gender") String gender,
                                             @Param("entryDateFrom") LocalDate entryDateFrom,
                                             @Param("entryDateTo") LocalDate entryDateTo,
                                             @Param("highestEducations") List<String> highestEducations,
                                             @Param("studyForms") List<String> studyForms,
                                             @Param("majorKeyword") String majorKeyword,
                                             @Param("certificateKeyword") String certificateKeyword,
                                             @Param("certificateStatuses") List<String> certificateStatuses);

    @Query("SELECT p FROM PersonnelEntity p WHERE " +
           "(:keyword IS NULL OR p.name LIKE %:keyword% OR p.employeeNumber LIKE %:keyword%) AND " +
           "(:departmentCode IS NULL OR p.departmentCode = :departmentCode) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:gender IS NULL OR p.gender = :gender) AND " +
           "(:entryDateFrom IS NULL OR p.entryDate >= :entryDateFrom) AND " +
           "(:entryDateTo IS NULL OR p.entryDate <= :entryDateTo) AND " +
           "(:highestEducations IS NULL OR EXISTS (SELECT 1 FROM PersonnelEducationEntity e WHERE e.personnelId = p.id AND e.highestEducation IN :highestEducations)) AND " +
           "(:studyForms IS NULL OR EXISTS (SELECT 1 FROM PersonnelEducationEntity e WHERE e.personnelId = p.id AND e.studyForm IN :studyForms)) AND " +
           "(:majorKeyword IS NULL OR EXISTS (SELECT 1 FROM PersonnelEducationEntity e WHERE e.personnelId = p.id AND e.major LIKE %:majorKeyword%)) AND " +
           "(:certificateKeyword IS NULL OR EXISTS (SELECT 1 FROM PersonnelCertificateEntity c WHERE c.personnelId = p.id AND c.certificateName LIKE %:certificateKeyword%)) AND " +
           "(:certificateStatuses IS NULL OR EXISTS (SELECT 1 FROM PersonnelCertificateEntity c WHERE c.personnelId = p.id AND " +
           "   ( (:certificateStatuses IS NOT NULL AND 'VALID' IN :certificateStatuses AND (c.expiryDate IS NULL OR c.expiryDate > CURRENT_DATE)) OR " +
           "     (:certificateStatuses IS NOT NULL AND 'EXPIRING' IN :certificateStatuses AND c.expiryDate IS NOT NULL AND c.expiryDate <= CURRENT_DATE + 60 DAY AND c.expiryDate > CURRENT_DATE) OR " +
           "     (:certificateStatuses IS NOT NULL AND 'EXPIRED' IN :certificateStatuses AND c.expiryDate IS NOT NULL AND c.expiryDate <= CURRENT_DATE) )))")
    Page<PersonnelEntity> findByCriteriaPageableFull(@Param("keyword") String keyword,
                                                     @Param("departmentCode") String departmentCode,
                                                     @Param("status") PersonnelStatus status,
                                                     @Param("gender") String gender,
                                                     @Param("entryDateFrom") LocalDate entryDateFrom,
                                                     @Param("entryDateTo") LocalDate entryDateTo,
                                                     @Param("highestEducations") List<String> highestEducations,
                                                     @Param("studyForms") List<String> studyForms,
                                                     @Param("majorKeyword") String majorKeyword,
                                                     @Param("certificateKeyword") String certificateKeyword,
                                                     @Param("certificateStatuses") List<String> certificateStatuses,
                                                     Pageable pageable);
}
