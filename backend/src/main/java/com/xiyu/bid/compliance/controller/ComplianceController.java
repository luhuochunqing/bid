// Input: ComplianceCheckService, DTOs
// Output: REST API Endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.compliance.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.compliance.application.BidQualityCheckTaskAppService;
import com.xiyu.bid.compliance.dto.ComplianceCheckResultDTO;
import com.xiyu.bid.compliance.dto.QualityCheckReportDTO;
import com.xiyu.bid.compliance.dto.QualityCheckTaskDTO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合规检查控制器
 * 提供合规检查、风险评估与AI标书质量核查的API接口
 */
@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ComplianceController {

    private final ComplianceCheckService complianceCheckService;
    private final BidQualityCheckTaskAppService qualityCheckTaskAppService;

    /**
     * 检查项目合规性
     *
     * @param projectId 项目ID
     * @return 合规检查结果
     */
    @PostMapping("/check/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RiskAssessmentDTO>> assessProjectRisk(
            @PathVariable Long projectId) {
        RiskAssessmentDTO assessment = complianceCheckService.assessRisk(projectId);
        return ResponseEntity.ok(ApiResponse.success(assessment));
    }

    /**
     * 触发标书质量核查异步任务.
     *
     * @param projectId 项目ID
     * @param force 是否强制重跑
     * @return 任务状态DTO
     */
    @PostMapping("/bid-quality/trigger/{projectId}")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "CREATE", entityType = "BidQualityCheck", description = "Trigger async bid document quality check")
    public ResponseEntity<ApiResponse<QualityCheckTaskDTO>> triggerBidQualityCheck(
            @PathVariable final Long projectId,
            @RequestParam(defaultValue = "false") final boolean force) {
        QualityCheckTaskDTO task = qualityCheckTaskAppService.triggerQualityCheck(projectId, force);
        return ResponseEntity.ok(ApiResponse.success("Quality check task triggered", task));
    }

    /**
     * 查询标书质量核查任务进度.
     *
     * @param taskId 任务UUID
     * @return 任务进度DTO
     */
    @GetMapping("/bid-quality/progress/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<QualityCheckTaskDTO>> getBidQualityCheckProgress(
            @PathVariable final String taskId) {
        QualityCheckTaskDTO task = qualityCheckTaskAppService.getTaskProgress(taskId);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    /**
     * 获取项目最新的标书质量核查完整报告.
     *
     * @param projectId 项目ID
     * @return 完整报告DTO
     */
    @GetMapping("/bid-quality/report/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<QualityCheckReportDTO>> getBidQualityReport(
            @PathVariable final Long projectId) {
        QualityCheckReportDTO report = qualityCheckTaskAppService.getLatestQualityReport(projectId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
