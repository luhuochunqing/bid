package com.xiyu.bid.tender.repository;

import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 标讯评估客户信息 EAV 数据访问接口。
 *
 * <p>提供按 evaluation_id 查询和删除客户信息行的能力，
 * 用于 3 段式评估表的加载与保存。
 */
@Repository
public interface TenderEvaluationCustomerInfoRepository
        extends JpaRepository<TenderEvaluationCustomerInfo, Long> {

    /**
     * 按评估表 ID 查询所有客户信息行。
     */
    List<TenderEvaluationCustomerInfo> findByEvaluationId(Long evaluationId);

    /**
     * 删除指定评估表的所有客户信息行（保存时全量替换）。
     */
    void deleteByEvaluationId(Long evaluationId);

    /**
     * 原生 SQL 删除：确保在 INSERT 之前执行 DELETE，避免 flush 顺序导致唯一约束冲突。
     * CO-266 + CO-310 修复。
     *
     * <p>注意：此方法必须在独立事务中调用（Propagation.REQUIRES_NEW），
     * 否则主事务的 rollback 会将此删除也回滚。
     */
    @Modifying
    @Query("DELETE FROM TenderEvaluationCustomerInfo ci WHERE ci.evaluation.id = :evaluationId")
    void deleteAllByEvaluationIdNative(Long evaluationId);
}
