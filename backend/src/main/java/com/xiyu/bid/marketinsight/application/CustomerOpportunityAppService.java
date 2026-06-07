package com.xiyu.bid.marketinsight.application;

import com.xiyu.bid.marketinsight.dto.CustomerInsightDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPredictionDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPurchaseDTO;
import com.xiyu.bid.marketinsight.dto.request.CustomerInsightQuery;
import com.xiyu.bid.marketinsight.lifecycle.CustomerOpportunityLifecycleService;
import com.xiyu.bid.marketinsight.query.CustomerOpportunityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户商机应用服务。
 * 仅编排查询与生命周期操作，不承载领域规则。
 */
@Service
@RequiredArgsConstructor
public class CustomerOpportunityAppService {

    private final CustomerOpportunityQueryService queryService;
    private final CustomerOpportunityLifecycleService lifecycleService;

    public List<CustomerInsightDTO> getCustomerInsights(CustomerInsightQuery query) {
        return queryService.getCustomerInsights(query);
    }

    public List<CustomerPurchaseDTO> getCustomerPurchases(String purchaserHash) {
        return queryService.getCustomerPurchases(purchaserHash);
    }

    public List<CustomerPredictionDTO> getCustomerPredictions(String purchaserHash) {
        return queryService.getCustomerPredictions(purchaserHash);
    }

    public void refreshInsights() {
        lifecycleService.refreshInsights();
    }

    public CustomerPredictionDTO transitionPrediction(Long id, String targetStatus) {
        return lifecycleService.transitionPrediction(id, targetStatus);
    }

    public CustomerPredictionDTO convertPrediction(Long id, Long projectId) {
        return lifecycleService.convertPrediction(id, projectId);
    }
}
