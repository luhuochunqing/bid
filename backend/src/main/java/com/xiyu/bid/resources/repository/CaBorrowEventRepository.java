package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.CaBorrowEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaBorrowEventRepository extends JpaRepository<CaBorrowEventEntity, Long> {

    List<CaBorrowEventEntity> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
}
