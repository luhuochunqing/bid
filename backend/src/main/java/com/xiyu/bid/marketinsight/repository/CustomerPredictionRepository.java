package com.xiyu.bid.marketinsight.repository;

import com.xiyu.bid.marketinsight.entity.CustomerPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 客户预测数据访问层
 */
@Repository
public interface CustomerPredictionRepository extends JpaRepository<CustomerPrediction, Long> {

    List<CustomerPrediction> findByPurchaserHash(String purchaserHash);

    List<CustomerPrediction> findByStatus(CustomerPrediction.Status status);

    List<CustomerPrediction> findAllByOrderByOpportunityScoreDesc();
}
