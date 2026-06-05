package com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository;

import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrandAuthAttachmentJpaRepository extends JpaRepository<BrandAuthAttachmentEntity, Long> {

    List<BrandAuthAttachmentEntity> findByAuthorizationId(Long authorizationId);

    void deleteByAuthorizationId(Long authorizationId);
}
