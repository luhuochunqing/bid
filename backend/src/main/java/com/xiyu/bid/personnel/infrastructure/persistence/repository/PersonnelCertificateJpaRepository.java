package com.xiyu.bid.personnel.infrastructure.persistence.repository;

import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelCertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PersonnelCertificateJpaRepository extends JpaRepository<PersonnelCertificateEntity, Long> {

    List<PersonnelCertificateEntity> findByPersonnelId(Long personnelId);

    // 编辑证书子节：只查询未软删除的记录
    List<PersonnelCertificateEntity> findByPersonnelIdAndDeletedAtIsNull(Long personnelId);

    void deleteByPersonnelId(Long personnelId);

    @Query("SELECT c FROM PersonnelCertificateEntity c WHERE c.expiryDate IS NOT NULL AND c.expiryDate <= :threshold AND c.deletedAt IS NULL")
    List<PersonnelCertificateEntity> findExpiringByThreshold(@Param("threshold") LocalDate threshold);
}
