package com.xiyu.bid.fees.repository;

import com.xiyu.bid.fees.entity.Fee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 费用数据访问接口
 */
@Repository
public interface FeeRepository extends JpaRepository<Fee, Long> {

    /**
     * 根据项目ID查询费用列表
     */
    List<Fee> findByProjectId(Long projectId);

    /**
     * 根据状态查询费用列表
     */
    List<Fee> findByStatus(Fee.Status status);

    /**
     * 根据项目ID集合分页查询费用
     */
    Page<Fee> findByProjectIdIn(Collection<Long> projectIds, Pageable pageable);

    /**
     * 根据状态和项目ID集合查询费用列表
     */
    List<Fee> findByStatusAndProjectIdIn(Fee.Status status, Collection<Long> projectIds);

    /**
     * 根据项目ID和状态查询费用列表
     */
    List<Fee> findByProjectIdAndStatus(Long projectId, Fee.Status status);

    /**
     * 根据费用类型查询费用列表
     */
    List<Fee> findByFeeType(Fee.FeeType feeType);

    /**
     * 计算指定项目和状态的费用总额
     */
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fee f WHERE f.projectId = :projectId AND f.status = :status")
    BigDecimal sumAmountByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") Fee.Status status);

    /**
     * 计算指定项目的所有费用总额
     */
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fee f WHERE f.projectId = :projectId")
    BigDecimal sumAmountByProjectId(@Param("projectId") Long projectId);

    /**
     * 分页查询所有费用
     */
    Page<Fee> findAll(Pageable pageable);

    /**
     * 根据项目ID分页查询费用
     */
    Page<Fee> findByProjectId(Long projectId, Pageable pageable);

    // === Workbench deadline queries ===
    // NB: For BID_BOND + PENDING, Fee.feeDate is treated as the deposit deadline.
    // See Fee#feeDate javadoc for the semantic contract.

    /** 全量保证金缴纳截止日期（Admin 用） */
    @Query("SELECT f.feeDate FROM Fee f WHERE f.feeType = 'BID_BOND' AND f.status = 'PENDING' AND f.feeDate BETWEEN :start AND :end")
    List<LocalDateTime> findDepositDeadlinesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按项目ID过滤保证金缴纳截止日期（非 Admin 用） */
    @Query("SELECT f.feeDate FROM Fee f WHERE f.feeType = 'BID_BOND' AND f.status = 'PENDING' AND f.projectId IN :projectIds AND f.feeDate BETWEEN :start AND :end")
    List<LocalDateTime> findDepositDeadlinesByProjectIds(@Param("projectIds") Collection<Long> projectIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
