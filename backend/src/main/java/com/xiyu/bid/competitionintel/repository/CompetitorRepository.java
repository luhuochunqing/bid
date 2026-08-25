package com.xiyu.bid.competitionintel.repository;

import com.xiyu.bid.competitionintel.entity.Competitor;
import com.xiyu.bid.competitionintel.entity.CompetitorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 竞争对手数据访问接口
 */
@Repository
public interface CompetitorRepository extends JpaRepository<Competitor, Long> {

    /**
     * 根据名称查找竞争对手
     */
    Optional<Competitor> findByName(String name);

    /**
     * 按状态查询竞品（名称升序）
     */
    List<Competitor> findAllByStatusOrderByNameAsc(CompetitorStatus status);

    /**
     * 查询全部竞品（名称升序，治理页用）
     */
    List<Competitor> findAllByOrderByNameAsc();

    /**
     * 幂等插入竞品（specs/055）：唯一约束冲突时 no-op，保证并发同名不重复创建。
     */
    @Modifying
    @Query(value = "INSERT INTO competitors (name, status, created_at) VALUES (:name, 'ACTIVE', NOW()) " +
            "ON DUPLICATE KEY UPDATE name = name", nativeQuery = true)
    int insertCompetitorIfAbsent(@Param("name") String name);

    /**
     * 根据行业查找竞争对手
     */
    List<Competitor> findByIndustry(String industry);

    /**
     * 根据名称搜索（模糊匹配）
     */
    @Query("SELECT c FROM Competitor c WHERE c.name LIKE %:name%")
    List<Competitor> searchByName(@Param("name") String name);

    /**
     * 检查名称是否存在
     */
    boolean existsByName(String name);
}
