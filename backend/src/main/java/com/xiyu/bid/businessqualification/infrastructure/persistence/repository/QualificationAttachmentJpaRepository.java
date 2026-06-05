package com.xiyu.bid.businessqualification.infrastructure.persistence.repository;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QualificationAttachmentJpaRepository extends JpaRepository<QualificationAttachmentEntity, Long> {
    List<QualificationAttachmentEntity> findByQualificationIdOrderByUploadedAtDesc(Long qualificationId);

    void deleteByQualificationId(Long qualificationId);
}
