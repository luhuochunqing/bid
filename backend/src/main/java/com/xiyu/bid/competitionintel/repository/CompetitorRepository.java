package com.xiyu.bid.competitionintel.repository;

import com.xiyu.bid.competitionintel.entity.Competitor;
import org.springframework.data.jpa.repository.JpaRepository;
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
