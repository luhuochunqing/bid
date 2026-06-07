package com.xiyu.bid.warehouse.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WarehouseImportTaskRepository extends JpaRepository<WarehouseImportTaskEntity, Long> {
    Optional<WarehouseImportTaskEntity> findByIdAndCreatedBy(Long id, Long createdBy);
}
