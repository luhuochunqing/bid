package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Tender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 标讯数据访问接口
 */
@Repository
public interface TenderRepository extends JpaRepository<Tender, Long>, JpaSpecificationExecutor<Tender> {

    /**
     * 根据外部 ID 查询标讯
     */
    java.util.Optional<Tender> findByExternalId(String externalId);

    /**
     * 根据状态查询标讯
     */
    List<Tender> findByStatus(Tender.Status status);

    /**
     * 根据来源查询标讯
     */
    List<Tender> findBySource(String source);

    /**
     * 统计指定状态的标讯数量
     */
    Long countByStatus(Tender.Status status);

    /**
     * 根据AI评分范围查询标讯
     */
    List<Tender> findByAiScoreBetween(Integer minScore, Integer maxScore);

    /**
     * 根据风险等级查询标讯
     */
    List<Tender> findByRiskLevel(Tender.RiskLevel riskLevel);

    // === Workbench deadline queries ===

    /** 全量报名截止日期查询（Admin 用） */
    @Query("SELECT t.registrationDeadline FROM Tender t WHERE t.registrationDeadline BETWEEN :start AND :end AND t.status NOT IN ('WON', 'LOST', 'ABANDONED')")
    List<LocalDateTime> findRegistrationDeadlinesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 全量开标时间查询（Admin 用） */
    @Query("SELECT t.bidOpeningTime FROM Tender t WHERE t.bidOpeningTime BETWEEN :start AND :end AND t.status NOT IN ('WON', 'LOST', 'ABANDONED')")
    List<LocalDateTime> findBidOpeningTimesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按标讯ID过滤报名截止日期（非 Admin 用） */
    @Query("SELECT t.registrationDeadline FROM Tender t WHERE t.id IN :tenderIds AND t.registrationDeadline BETWEEN :start AND :end AND t.status NOT IN ('WON', 'LOST', 'ABANDONED')")
    List<LocalDateTime> findRegistrationDeadlinesByTenderIds(@Param("tenderIds") Collection<Long> tenderIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按标讯ID过滤开标时间（非 Admin 用） */
    @Query("SELECT t.bidOpeningTime FROM Tender t WHERE t.id IN :tenderIds AND t.bidOpeningTime BETWEEN :start AND :end AND t.status NOT IN ('WON', 'LOST', 'ABANDONED')")
    List<LocalDateTime> findBidOpeningTimesByTenderIds(@Param("tenderIds") Collection<Long> tenderIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 根据业主哈希查询标讯(按发布时间降序)
     * 用于商机时间预测
     */
    /**
     * 按招标主体查找标讯，用于去重检查。
     */
    List<Tender> findByPurchaserNameAllIgnoreCase(String purchaserName);

    List<Tender> findByPurchaserHashOrderByPublishDateDesc(String purchaserHash);

    /**
     * 查询指定时间之后创建的标讯，用于关键词订阅匹配
     */
    @Query("SELECT t FROM Tender t WHERE t.createdAt > :since ORDER BY t.createdAt DESC")
    List<Tender> findTendersCreatedAfter(@Param("since") LocalDateTime since);
}
