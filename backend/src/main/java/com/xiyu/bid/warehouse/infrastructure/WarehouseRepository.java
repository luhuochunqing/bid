package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long>, JpaSpecificationExecutor<WarehouseEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WarehouseEntity w where w.id = :id")
    Optional<WarehouseEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByName(String name);
    java.util.Optional<WarehouseEntity> findByName(String name);
    List<WarehouseEntity> findByStatus(WarehouseStatus status);
    List<WarehouseEntity> findByEndDateBetween(LocalDate from, LocalDate to);
    List<WarehouseEntity> findByEndDateLessThanEqualAndStatusNot(LocalDate date, WarehouseStatus excludeStatus);
}
