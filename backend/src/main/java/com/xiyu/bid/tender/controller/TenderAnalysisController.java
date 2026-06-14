package com.xiyu.bid.tender.controller;

import com.xiyu.bid.ai.dto.TenderAiAnalysisDTO;
import com.xiyu.bid.ai.service.AiDeepCapabilityService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.service.TenderAiAnalysisService;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@Tag(name = "标讯AI分析", description = "标讯AI深度分析与中标概率评估")
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
@Slf4j
public class TenderAnalysisController {

    private final AiDeepCapabilityService aiDeepCapabilityService;
    private final TenderAiAnalysisService tenderAiAnalysisService;
    private final DemoModeService demoModeService;
    private final DemoDataProvider demoDataProvider;

    @GetMapping("/{id}/ai-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "查询标讯 AI 分析结果")
    public ResponseEntity<ApiResponse<TenderAiAnalysisDTO>> getTenderAiAnalysis(@PathVariable Long id) {
        log.info("GET /api/tenders/{}/ai-analysis", id);
        if (isDemoEntityId(id)) {
            TenderDTO tender = demoDataProvider.findDemoTenderById(id).orElseThrow(
                    () -> new com.xiyu.bid.exception.ResourceNotFoundException("Tender", id.toString()));
            TenderAiAnalysisDTO dto = TenderAiAnalysisDTO.builder()
                    .tenderId(tender.getId())
                    .winScore(tender.getAiScore() == null ? 80 : tender.getAiScore())
                    .suggestion("Demo 标讯分析：重点关注资质响应、交付周期和付款条件。")
                    .dimensionScores(List.of()).risks(List.of()).autoTasks(List.of()).build();
            return ResponseEntity.ok(ApiResponse.success("查询成功", dto));
        }
        Optional<TenderAiAnalysisDTO> analysis = aiDeepCapabilityService.getLatestTenderAnalysis(id);
        if (analysis.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "AI 分析结果不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success("查询成功", analysis.get()));
    }

    @PostMapping("/{id}/ai-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "触发标讯 AI 分析")
    public ResponseEntity<ApiResponse<TenderAiAnalysisDTO>> createTenderAiAnalysis(@PathVariable Long id) {
        log.info("POST /api/tenders/{}/ai-analysis", id);
        rejectDemoMutation(id);
        TenderAiAnalysisDTO analysis = aiDeepCapabilityService.analyzeTender(id, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("分析任务已提交", analysis));
    }

    @PostMapping("/{id}/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "触发标讯 AI 分析（旧接口）")
    public ResponseEntity<ApiResponse<TenderDTO>> analyzeTender(@PathVariable Long id) {
        log.info("POST /api/tenders/{}/analyze", id);
        rejectDemoMutation(id);
        return ResponseEntity.ok(ApiResponse.success("分析完成", tenderAiAnalysisService.analyzeTender(id)));
    }

    private boolean isDemoEntityId(Long id) { return demoModeService.isEnabled() && id != null && id < 0; }
    private void rejectDemoMutation(Long id) { if (isDemoEntityId(id)) throw new IllegalArgumentException("Demo records are read-only"); }
}
