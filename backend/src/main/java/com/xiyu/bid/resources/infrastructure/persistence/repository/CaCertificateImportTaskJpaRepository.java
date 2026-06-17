package com.xiyu.bid.resources.infrastructure.persistence.repository;

import com.xiyu.bid.resources.infrastructure.persistence.entity.CaCertificateImportTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaCertificateImportTaskJpaRepository extends JpaRepository<CaCertificateImportTaskEntity, Long> {
    List<CaCertificateImportTaskEntity> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
}
