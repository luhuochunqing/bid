// Input: ComplianceCheckService, DTOs
// Output: REST API Endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.compliance.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.compliance.application.BidDocumentQualityCheckAppService;
import com.xiyu.bid.compliance.dto.ComplianceCheckResultDTO;
import com.xiyu.bid.compliance.dto.RiskAssessmentDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.service.ComplianceCheckService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合规检查控制器
 * 提供合规检查和风险评估的API接口
 */
@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceCheckService complianceCheckService;
    private final BidDocumentQualityCheckAppService bidDocumentQualityCheckAppService;

    /**
     * 检查项目合规性
     *
     * @param projectId 项目ID
     * @return 合规检查结果
     */
    @PostMapping("/check/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "ComplianceCheck", description = "Perform project compliance check")
    public ResponseEntity<ApiResponse<ComplianceCheckResultDTO>> checkProjectCompliance(
            @PathVariable Long projectId) {

        ComplianceCheckResultDTO result = complianceCheckService.checkProjectCompliance(projectId);
        return ResponseEntity.ok(ApiResponse.success("Compliance check completed successfully", result));
    }

    /**
     * 检查标书合规性
     *
     * @param tenderId 标书ID
     * @return 合规检查结果
     */
    @PostMapping("/check/tender/{tenderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "ComplianceCheck", description = "Perform tender compliance check")
    public ResponseEntity<ApiResponse<ComplianceCheckResultDTO>> checkTenderCompliance(
            @PathVariable Long tenderId) {

        ComplianceCheckResultDTO result = complianceCheckService.checkTenderCompliance(tenderId);
        return ResponseEntity.ok(ApiResponse.success("Compliance check completed successfully", result));
    }

    /**
     * 获取合规检查结果详情
     *
     * @param resultId 结果ID
     * @return 合规检查结果详情
     */
    @GetMapping("/results/{resultId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ComplianceCheckResult>> getCheckResult(
            @PathVariable Long resultId) {

        ComplianceCheckResult result = complianceCheckService.getCheckResultById(resultId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取项目的所有合规检查结果
     *
     * @param projectId 项目ID
     * @return 合规检查结果列表
     */
    @GetMapping("/project/{projectId}/results")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ComplianceCheckResult>>> getProjectCheckResults(
            @PathVariable Long projectId) {

        List<ComplianceCheckResult> results = complianceCheckService.getCheckResultsByProjectId(projectId);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    /**
     * 评估项目风险
     *
     * @param projectId 项目ID
     * @return 风险评估结果
     */
    @GetMapping("/assess-risk/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<RiskAssessmentDTO>> assessProjectRisk(
            @PathVariable Long projectId) {

        RiskAssessmentDTO assessment = complianceCheckService.assessRisk(projectId);
        return ResponseEntity.ok(ApiResponse.success(assessment));
    }

    /**
     * 标书文档质量核查.
     *
     * @param projectId 项目ID
     * @return 质量核查结果
     */
    @PostMapping("/bid-document/check/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "BidDocumentQualityCheck",
            description = "Perform bid document quality check")
    public ResponseEntity<ApiResponse<ComplianceCheckResultDTO>>
        checkBidDocumentQuality(@PathVariable final Long projectId) {
        ComplianceCheckResultDTO result =
                bidDocumentQualityCheckAppService
                        .checkBidDocumentQuality(projectId);
        return ResponseEntity.ok(ApiResponse.success(
                "Bid document quality check completed", result));
    }

    /**
     * 获取项目最新的标书文档质量核查结果.
     *
     * @param projectId 项目ID
     * @return 质量核查结果
     */
    @GetMapping("/bid-document/results/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ComplianceCheckResultDTO>>
        getBidDocumentQualityResult(@PathVariable final Long projectId) {
        ComplianceCheckResultDTO result =
                bidDocumentQualityCheckAppService
                        .getLatestQualityCheckResult(projectId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
