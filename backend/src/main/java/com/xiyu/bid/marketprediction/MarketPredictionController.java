package com.xiyu.bid.marketprediction;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.marketprediction.domain.MarketPredictionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 商机预测 REST 控制器
 */
@RestController
@RequestMapping("/api/market-prediction")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
@RequiredArgsConstructor
public class MarketPredictionController {

    private final MarketPredictionService predictionService;

    /**
     * 获取单个业主的商机预测
     *
     * GET /api/market-prediction/{purchaserHash}
     */
    @GetMapping("/{purchaserHash}")
    public ResponseEntity<ApiResponse<MarketPredictionDTO>> getPrediction(
            @PathVariable String purchaserHash) {

        Optional<MarketPredictionResult> result = predictionService.predictByPurchaserHash(purchaserHash);

        if (result.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(MarketPredictionDTO.noData(purchaserHash)));
        }

        MarketPredictionDTO dto = new MarketPredictionDTO(purchaserHash, result.get());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * 批量获取商机预测
     *
     * POST /api/market-prediction/batch
     * Body: { "purchaserHashes": ["hash1", "hash2", ...] }
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<MarketPredictionDTO>>> batchPredict(
            @RequestBody BatchPredictionRequest request) {

        List<MarketPredictionDTO> results = predictionService.batchPredict(request.purchaserHashes());
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    /**
     * 获取预测所需的最少历史数据条数
     */
    @GetMapping("/config/min-count")
    public ResponseEntity<ApiResponse<Integer>> getMinHistoricalCount() {
        return ResponseEntity.ok(ApiResponse.success(predictionService.getMinimumHistoricalCount()));
    }

    /**
     * 请求记录
     */
    public record BatchPredictionRequest(List<String> purchaserHashes) {}
}
