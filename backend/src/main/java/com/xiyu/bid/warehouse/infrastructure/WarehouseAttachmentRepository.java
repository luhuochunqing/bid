package com.xiyu.bid.warehouse.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WarehouseAttachmentRepository extends JpaRepository<WarehouseAttachmentEntity, Long> {
    List<WarehouseAttachmentEntity> findByWarehouseId(Long warehouseId);
    List<WarehouseAttachmentEntity> findByWarehouseIdAndType(Long warehouseId, com.xiyu.bid.warehouse.domain.WarehouseAttachmentType type);
    void deleteByWarehouseIdAndType(Long warehouseId, com.xiyu.bid.warehouse.domain.WarehouseAttachmentType type);
}
