package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long>, JpaSpecificationExecutor<WarehouseEntity> {
    boolean existsByName(String name);
    List<WarehouseEntity> findByStatus(WarehouseStatus status);
    List<WarehouseEntity> findByEndDateBetween(LocalDate from, LocalDate to);
    List<WarehouseEntity> findByEndDateLessThanEqualAndStatusNot(LocalDate date, WarehouseStatus excludeStatus);
}
