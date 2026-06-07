package com.xiyu.bid.marketinsight.service;

import com.xiyu.bid.marketinsight.application.CustomerOpportunityAppService;
import com.xiyu.bid.marketinsight.dto.CustomerInsightDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPredictionDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPurchaseDTO;
import com.xiyu.bid.marketinsight.dto.request.CustomerInsightQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 兼容层：保留旧的 service 注入点，但实际委托给应用服务。
 */
@Service
@RequiredArgsConstructor
@Deprecated(forRemoval = false)
public class CustomerOpportunityService {

    private final CustomerOpportunityAppService appService;

    public List<CustomerInsightDTO> getCustomerInsights(CustomerInsightQuery query) {
        return appService.getCustomerInsights(query);
    }

    public List<CustomerPurchaseDTO> getCustomerPurchases(String purchaserHash) {
        return appService.getCustomerPurchases(purchaserHash);
    }

    public List<CustomerPredictionDTO> getCustomerPredictions(String purchaserHash) {
        return appService.getCustomerPredictions(purchaserHash);
    }

    public void refreshInsights() {
        appService.refreshInsights();
    }

    public CustomerPredictionDTO transitionPrediction(Long id, String targetStatus) {
        return appService.transitionPrediction(id, targetStatus);
    }

    public CustomerPredictionDTO convertPrediction(Long id, Long projectId) {
        return appService.convertPrediction(id, projectId);
    }
}
