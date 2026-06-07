package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Case;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 案例Repository接口
 */
@Repository
public interface CaseRepository extends JpaRepository<Case, Long>, JpaSpecificationExecutor<Case> {

    /**
     * 根据行业查找案例（分页）
     */
    Page<Case> findByIndustry(Case.Industry industry, Pageable pageable);

    /**
     * 根据结果查找案例（分页）
     */
    Page<Case> findByOutcome(Case.Outcome outcome, Pageable pageable);

    /**
     * 根据标题查找案例（模糊查询，分页）
     */
    Page<Case> findByTitleContaining(String title, Pageable pageable);

    /**
     * 根据金额范围查找案例（分页）
     */
    Page<Case> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable);

    /**
     * 根据项目日期范围查找案例（分页）
     */
    Page<Case> findByProjectDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * 根据行业和结果查找案例（分页）
     */
    Page<Case> findByIndustryAndOutcome(Case.Industry industry, Case.Outcome outcome, Pageable pageable);

    /**
     * 统计行业的案例数量
     */
    Long countByIndustry(Case.Industry industry);

    /**
     * 统计结果的案例数量
     */
    Long countByOutcome(Case.Outcome outcome);

    /**
     * 查找金额大于指定值的案例，按金额降序排序（分页）
     */
    Page<Case> findByAmountGreaterThanOrderByAmountDesc(BigDecimal amount, Pageable pageable);

    /**
     * 查找指定日期之后的案例（分页）
     */
    Page<Case> findByProjectDateAfter(LocalDate date, Pageable pageable);

    /**
     * 根据行业查找案例（限制返回数量）
     */
    List<Case> findByIndustry(Case.Industry industry);

    /**
     * 根据结果查找案例（限制返回数量）
     */
    List<Case> findByOutcome(Case.Outcome outcome);

    /**
     * 按搜索文档、标题、产品线和客户名做服务端检索
     */
    @Query("""
            select c
            from Case c
            where (
                :keyword is null
                or :keyword = ''
                or lower(coalesce(c.searchDocument, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.title, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.productLine, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.customerName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.archiveSummary, '')) like lower(concat('%', :keyword, '%'))
            )
            and (:productLine is null or :productLine = '' or lower(coalesce(c.productLine, '')) = lower(:productLine))
            and (:status is null or :status = '' or lower(coalesce(c.status, '')) = lower(:status))
            and (:visibility is null or :visibility = '' or lower(coalesce(c.visibility, '')) = lower(:visibility))
            """)
    Page<Case> searchCases(
            @Param("keyword") String keyword,
            @Param("productLine") String productLine,
            @Param("status") String status,
            @Param("visibility") String visibility,
            Pageable pageable);

    @Query(value = """
            select distinct c
            from Case c
            where c.outcome = com.xiyu.bid.entity.Case$Outcome.WON
              and (
                (:industry is not null and c.industry = :industry)
                or (:keyword is not null and :keyword <> '' and (
                    lower(coalesce(c.searchDocument, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.title, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.productLine, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.customerName, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.locationName, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.archiveSummary, '')) like lower(concat('%', :keyword, '%'))
                ))
                or (:purchaserName is not null and :purchaserName <> ''
                    and lower(coalesce(c.customerName, '')) like lower(concat('%', :purchaserName, '%')))
                or (:region is not null and :region <> ''
                    and lower(coalesce(c.locationName, '')) like lower(concat('%', :region, '%')))
              )
            """, countQuery = """
            select count(distinct c.id)
            from Case c
            where c.outcome = com.xiyu.bid.entity.Case$Outcome.WON
              and (
                (:industry is not null and c.industry = :industry)
                or (:keyword is not null and :keyword <> '' and (
                    lower(coalesce(c.searchDocument, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.title, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.productLine, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.customerName, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.locationName, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.archiveSummary, '')) like lower(concat('%', :keyword, '%'))
                ))
                or (:purchaserName is not null and :purchaserName <> ''
                    and lower(coalesce(c.customerName, '')) like lower(concat('%', :purchaserName, '%')))
                or (:region is not null and :region <> ''
                    and lower(coalesce(c.locationName, '')) like lower(concat('%', :region, '%')))
              )
            """)
    Page<Case> findScopedWonCasesForBidMatch(
            @Param("industry") Case.Industry industry,
            @Param("keyword") String keyword,
            @Param("purchaserName") String purchaserName,
            @Param("region") String region,
            Pageable pageable);

    @Query("select distinct c.productLine from Case c where c.productLine is not null and trim(c.productLine) <> ''")
    List<String> findDistinctProductLines();

    @Query("select distinct c.status from Case c where c.status is not null and trim(c.status) <> ''")
    List<String> findDistinctStatuses();

    @Query("select distinct c.visibility from Case c where c.visibility is not null and trim(c.visibility) <> ''")
    List<String> findDistinctVisibilities();

    @Query("select distinct tag from Case c join c.tags tag where trim(tag) <> ''")
    List<String> findDistinctTags();

    @Query("""
            select count(distinct c.id)
            from Case c
            where c.outcome = com.xiyu.bid.entity.Case$Outcome.WON
              and (:industry is null or c.industry = :industry)
              and (:productLine is null or lower(coalesce(c.productLine, '')) = lower(:productLine))
              and (:projectDateFrom is null or c.projectDate >= :projectDateFrom)
              and (:projectDateTo is null or c.projectDate <= :projectDateTo)
            """)
    long countWonCasesByFilters(
            @Param("industry") Case.Industry industry,
            @Param("productLine") String productLine,
            @Param("projectDateFrom") LocalDate projectDateFrom,
            @Param("projectDateTo") LocalDate projectDateTo);
}
