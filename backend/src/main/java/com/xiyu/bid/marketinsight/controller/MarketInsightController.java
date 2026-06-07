// Input: MarketInsightService
// Output: Market Insight REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.marketinsight.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.marketinsight.dto.MarketInsightDTO;
import com.xiyu.bid.marketinsight.service.MarketInsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场洞察控制器
 * 处理市场洞察聚合数据的HTTP请求
 */
@RestController
@RequestMapping("/api/market-insight")
@RequiredArgsConstructor
@Slf4j
public class MarketInsightController {

    private final MarketInsightService marketInsightService;

    /**
     * 获取市场洞察聚合数据
     */
    @GetMapping("/insight")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MarketInsightDTO>> getMarketInsight() {
        log.info("GET /api/market-insight/insight - Fetching market insight");
        MarketInsightDTO insight = marketInsightService.getMarketInsight();
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved market insight", insight));
    }
}
