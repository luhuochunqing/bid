package com.xiyu.bid.tender.repository;

import com.xiyu.bid.tender.entity.TenderEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 标讯评估数据访问接口
 */
@Repository
public interface TenderEvaluationRepository extends JpaRepository<TenderEvaluation, Long> {

    /**
     * 根据标讯ID查询评估
     */
    Optional<TenderEvaluation> findByTenderId(Long tenderId);

    /**
     * 检查标讯是否有评估记录
     */
    boolean existsByTenderId(Long tenderId);

    /**
     * 批量根据标讯ID查询评估
     */
    List<TenderEvaluation> findByTenderIdIn(List<Long> tenderIds);
}
