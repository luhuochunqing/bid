// Input: CustomerOpportunityAppService
// Output: Customer Opportunity REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.marketinsight.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.marketinsight.application.CustomerOpportunityAppService;
import com.xiyu.bid.marketinsight.dto.CustomerInsightDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPredictionDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPurchaseDTO;
import com.xiyu.bid.marketinsight.dto.request.CustomerInsightQuery;
import com.xiyu.bid.marketinsight.dto.request.PredictionConvertRequest;
import com.xiyu.bid.marketinsight.dto.request.PredictionStatusUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/customer-opportunities")
@RequiredArgsConstructor
@Slf4j
public class CustomerOpportunityController {

    private final CustomerOpportunityAppService customerOpportunityAppService;

    @GetMapping("/insights")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CustomerInsightDTO>>> getCustomerInsights(@ModelAttribute CustomerInsightQuery query) {
        log.info("GET /api/customer-opportunities/insights - Fetching customer insights with filters");
        List<CustomerInsightDTO> insights = customerOpportunityAppService.getCustomerInsights(query);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved customer insights", insights));
    }

    @GetMapping("/{purchaserHash}/purchases")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CustomerPurchaseDTO>>> getCustomerPurchases(@PathVariable String purchaserHash) {
        List<CustomerPurchaseDTO> purchases = customerOpportunityAppService.getCustomerPurchases(purchaserHash);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved customer purchases", purchases));
    }

    @GetMapping("/{purchaserHash}/predictions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CustomerPredictionDTO>>> getCustomerPredictions(@PathVariable String purchaserHash) {
        List<CustomerPredictionDTO> predictions = customerOpportunityAppService.getCustomerPredictions(purchaserHash);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved customer predictions", predictions));
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> refreshInsights() {
        customerOpportunityAppService.refreshInsights();
        return ResponseEntity.ok(ApiResponse.success("Customer insights refreshed successfully", null));
    }

    @PutMapping("/predictions/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CustomerPredictionDTO>> transitionPrediction(
            @PathVariable Long id,
            @Valid @RequestBody PredictionStatusUpdateRequest body) {
        CustomerPredictionDTO prediction = customerOpportunityAppService.transitionPrediction(id, body.getStatus().trim().toUpperCase(Locale.ROOT));
        return ResponseEntity.ok(ApiResponse.success("Prediction status updated successfully", prediction));
    }

    @PutMapping("/predictions/{id}/convert")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CustomerPredictionDTO>> convertPrediction(
            @PathVariable Long id,
            @RequestBody(required = false) PredictionConvertRequest body) {
        Long projectId = body == null ? null : body.getProjectId();
        CustomerPredictionDTO prediction = customerOpportunityAppService.convertPrediction(id, projectId);
        return ResponseEntity.ok(ApiResponse.success("Prediction converted successfully", prediction));
    }
}
