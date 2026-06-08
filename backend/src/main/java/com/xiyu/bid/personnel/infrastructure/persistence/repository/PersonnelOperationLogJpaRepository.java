package com.xiyu.bid.personnel.infrastructure.persistence.repository;

import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelOperationLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PersonnelOperationLogJpaRepository extends JpaRepository<PersonnelOperationLogEntity, Long> {

    List<PersonnelOperationLogEntity> findByPersonnelIdOrderByCreatedAtDesc(Long personnelId);

    Page<PersonnelOperationLogEntity> findByPersonnelIdOrderByCreatedAtDesc(Long personnelId, Pageable pageable);

    @Query("SELECT l FROM PersonnelOperationLogEntity l WHERE l.personnelId = :personnelId " +
           "AND l.createdAt >= :startTime AND l.createdAt <= :endTime ORDER BY l.createdAt DESC")
    List<PersonnelOperationLogEntity> findByPersonnelIdAndTimeRange(
            @Param("personnelId") Long personnelId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    List<PersonnelOperationLogEntity> findByOperatorIdOrderByCreatedAtDesc(Long operatorId);

    @Query("SELECT l FROM PersonnelOperationLogEntity l WHERE l.operationType = :operationType " +
           "ORDER BY l.createdAt DESC")
    List<PersonnelOperationLogEntity> findByOperationType(@Param("operationType") String operationType);
}
