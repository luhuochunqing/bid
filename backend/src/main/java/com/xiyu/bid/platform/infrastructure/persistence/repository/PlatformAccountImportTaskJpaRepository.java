package com.xiyu.bid.platform.infrastructure.persistence.repository;

import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformAccountImportTaskJpaRepository extends JpaRepository<PlatformAccountImportTaskEntity, Long> {
    List<PlatformAccountImportTaskEntity> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
}
