package com.xiyu.bid.warehouse.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WarehouseExportTaskRepository extends JpaRepository<WarehouseExportTaskEntity, Long> {
    Optional<WarehouseExportTaskEntity> findByIdAndCreatedBy(Long id, Long createdBy);
}
