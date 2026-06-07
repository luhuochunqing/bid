package com.xiyu.bid.warehouse.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseOperationLogRepository extends JpaRepository<WarehouseOperationLogEntity, Long> {
    Page<WarehouseOperationLogEntity> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId, Pageable pageable);
}
