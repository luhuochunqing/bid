// Input: ROI service and request DTOs
// Output: R O I Analysis REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.roi.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.roi.dto.ROIAnalysisCreateRequest;
import com.xiyu.bid.roi.dto.ROIAnalysisDTO;
import com.xiyu.bid.roi.dto.SensitivityAnalysisRequest;
import com.xiyu.bid.roi.dto.SensitivityAnalysisResult;
import com.xiyu.bid.roi.service.ROIAnalysisService;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ROI分析控制器
 * 处理ROI分析相关的HTTP请求
 */
@RestController
@RequestMapping("/api/ai/roi")
@RequiredArgsConstructor
@Slf4j
public class ROIAnalysisController {

    private final ROIAnalysisService roiAnalysisService;

    /**
     * 根据项目ID获取ROI分析
     * GET /api/ai/roi/project/{projectId}
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ROIAnalysisDTO>> getAnalysisByProject(@PathVariable Long projectId) {
        log.info("GET /api/ai/roi/project/{} - Fetching ROI analysis", projectId);

        ROIAnalysisDTO analysis = roiAnalysisService.getAnalysisByProject(projectId);

        return ResponseEntity.ok(
                ApiResponse.success("Successfully retrieved ROI analysis", analysis)
        );
    }

    /**
     * 创建ROI分析
     * POST /api/ai/roi
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ROIAnalysisDTO>> createAnalysis(
            @Valid @RequestBody ROIAnalysisCreateRequest request) {
        log.info("POST /api/ai/roi - Creating ROI analysis for project: {}", request.getProjectId());

        // 清洗输入
        sanitizeRequest(request);

        ROIAnalysisDTO analysis = roiAnalysisService.createAnalysis(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("ROI analysis created successfully", analysis)
        );
    }

    /**
     * 计算项目ROI
     * POST /api/ai/roi/project/{projectId}/calculate
     */
    @PostMapping("/project/{projectId}/calculate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ROIAnalysisDTO>> calculateROI(
            @PathVariable Long projectId,
            @Valid @RequestBody ROIAnalysisCreateRequest request) {
        log.info("POST /api/ai/roi/project/{}/calculate - Calculating ROI", projectId);

        // 清洗输入
        sanitizeRequest(request);

        ROIAnalysisDTO analysis = roiAnalysisService.calculateROI(projectId, request);

        return ResponseEntity.ok(
                ApiResponse.success("ROI calculated successfully", analysis)
        );
    }

    /**
     * 执行敏感性分析
     * POST /api/ai/roi/sensitivity
     */
    @PostMapping("/sensitivity")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<SensitivityAnalysisResult>> performSensitivityAnalysis(
            @Valid @RequestBody SensitivityAnalysisRequest request) {
        log.info("POST /api/ai/roi/sensitivity - Performing sensitivity analysis for project: {}",
                request.getProjectId());

        SensitivityAnalysisResult result = roiAnalysisService.performSensitivityAnalysis(
                request.getProjectId(), request);

        return ResponseEntity.ok(
                ApiResponse.success("Sensitivity analysis completed successfully", result)
        );
    }

    /**
     * 清洗请求中的用户输入
     */
    private void sanitizeRequest(ROIAnalysisCreateRequest request) {
        if (request.getRiskFactors() != null) {
            request.setRiskFactors(InputSanitizer.sanitizeString(request.getRiskFactors(), 5000));
        }
        if (request.getAssumptions() != null) {
            request.setAssumptions(InputSanitizer.sanitizeString(request.getAssumptions(), 5000));
        }
    }
}
