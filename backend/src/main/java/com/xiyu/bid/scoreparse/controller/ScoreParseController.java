// Input: HTTP 请求（projectId 路径变量 + multipart 投标文件）
// Output: ApiResponse<触发/状态/清单/结果 DTO>
// Pos: scoreparse/controller — 评分标准解析 REST 入口（spec 041 US1/US4）
// 维护声明: 维护者按项目SOP；contracts/score-parse-api.md §1-§7
package com.xiyu.bid.scoreparse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreparse.application.BidDocumentUploadService;
import com.xiyu.bid.scoreparse.application.ScoreParseAppService;
import com.xiyu.bid.scoreparse.application.ScoreScoringAppService;
import com.xiyu.bid.scoreparse.dto.BidDocumentUploadDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 评分标准解析 Controller（spec 041 US1/US4）。
 * <p>鉴权：类级 isAuthenticated + Service 层 assertCurrentUserCanAccessProject（403 语义统一）。
 * <p>错误语义（契约 §5）：IllegalArgumentException → 400（NO_BID_DOCUMENT/SCORE_ITEMS_NOT_READY）；
 * IllegalStateException → 409（TASK_IN_PROGRESS）。
 */
@RestController
@RequestMapping("/api/projects/{projectId}/score-parse")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "评分标准解析", description = "AI 评分标准解析（spec 041）")
public class ScoreParseController {

    private final ScoreParseAppService scoreParseAppService;
    private final ScoreScoringAppService scoreScoringAppService;
    private final BidDocumentUploadService bidDocumentUploadService;

    @Operation(summary = "触发评分标准解析", description = "四路召回 + LLM 结构化提取 + 闭环校验；已有进行中任务时返回该任务")
    @PostMapping("/parse")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ScoreParseTriggerDTO>> triggerParse(@PathVariable Long projectId) {
        ScoreParseTriggerDTO result = scoreParseAppService.triggerParse(projectId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("解析任务已提交", result));
    }

    @Operation(summary = "查询解析状态", description = "轮询用；PENDING/PROCESSING/COMPLETED/FAILED")
    @GetMapping("/parse/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ScoreParseProgressDTO>> getStatus(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(scoreParseAppService.getStatus(projectId)));
    }

    @Operation(summary = "查询评分项清单", description = "阶段 1 结果：评分项 + 汇总统计（weightWarning）")
    @GetMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ScoreParseItemsDTO>> getItems(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(scoreParseAppService.getItems(projectId)));
    }

    @Operation(summary = "上传投标文件", description = "PDF/docx，≤50MB；阶段 2 实际打分的前置条件（契约 §4）")
    @PostMapping("/bid-documents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BidDocumentUploadDTO>> uploadBidDocument(
            @PathVariable Long projectId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("投标文件已上传",
                        bidDocumentUploadService.uploadBidDocument(projectId, file)));
    }

    @Operation(summary = "触发实际打分（阶段 2）",
            description = "前置校验：标书已上传 + 评分标准已解析；已有进行中任务返回 409（契约 §5）")
    @PostMapping("/scoring")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ScoreParseTriggerDTO>> triggerScoring(@PathVariable Long projectId) {
        try {
            ScoreParseTriggerDTO result = scoreScoringAppService.triggerScoring(projectId);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success("打分任务已提交", result));
        } catch (IllegalArgumentException exception) {
            String msg = exception.getMessage();
            if ("SCORE_ITEMS_NOT_READY".equals(msg)) {
                msg = "请等待招标文件解析完成后再进行打分";
            } else if ("NO_BID_DOCUMENT".equals(msg)) {
                msg = "请先上传投标文件后再进行打分";
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, msg));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(409, exception.getMessage()));
        }
    }

    @Operation(summary = "查询打分状态", description = "轮询用；结构同解析状态（契约 §6）")
    @GetMapping("/scoring/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ScoreParseProgressDTO>> getScoringStatus(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(scoreScoringAppService.getStatus(projectId)));
    }

    @Operation(summary = "查询打分结果", description = "阶段 2 结果：实际得分/标书引用/建议；不含 kbHit（FR-018，契约 §7）")
    @GetMapping("/results")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ScoreScoringResultsDTO>> getResults(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(scoreScoringAppService.getResults(projectId)));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, "您无权限查看此任务的评分解析结果"));
    }
}
