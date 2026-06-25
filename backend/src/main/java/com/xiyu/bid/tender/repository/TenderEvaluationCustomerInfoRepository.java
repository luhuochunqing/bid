package com.xiyu.bid.tender.repository;

import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
