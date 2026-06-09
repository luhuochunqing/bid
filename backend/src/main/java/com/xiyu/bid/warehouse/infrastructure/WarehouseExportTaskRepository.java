package com.xiyu.bid.warehouse.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseExportTaskRepository extends JpaRepository<WarehouseExportTaskEntity, Long> {
    Optional<WarehouseExportTaskEntity> findByIdAndCreatedBy(Long id, Long createdBy);
    Page<WarehouseExportTaskEntity> findByCreatedByOrderByCreatedAtDesc(Long createdBy, Pageable pageable);
    List<WarehouseExportTaskEntity> findByStatusInOrderByCreatedAtDesc(List<WarehouseExportTaskEntity.ExportStatus> statuses);
}
