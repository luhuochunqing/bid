package com.xiyu.bid.businessqualification.infrastructure.persistence.repository;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.AlertConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertConfigJpaRepository extends JpaRepository<AlertConfigEntity, Long> {
}
