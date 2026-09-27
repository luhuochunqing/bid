// Input: project-scoped extraction version commands and authenticated user context
// Output: extraction snapshots, candidate evidence, and activation responses
// Pos: scoreparse HTTP adapter; delegates project access and version invariants to application services
// 维护声明: 维护者按项目SOP；评分标准提取版本接口必须保持来源证据与激活版本边界（2026-09-27）
package com.xiyu.bid.scoreparse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreparse.application.OversizedTenderTextException;
import com.xiyu.bid.scoreparse.application.ScoreExtractionVersionFeature;
import com.xiyu.bid.scoreparse.application.ScoreParseTaskStateService;
import com.xiyu.bid.scoreparse.application.TenderIntake;
import com.xiyu.bid.scoreparse.application.SidecarTableExtractor;
import com.xiyu.bid.scoreparse.application.ScoreFactShadowPipeline;
import com.xiyu.bid.scoreparse.application.ScoreGlobalMergeOrchestrator;
import com.xiyu.bid.scoreparse.application.ScoreCandidatePostProcessor;
import com.xiyu.bid.scoreparse.application.ScoreDocExcerptExtractor;
import com.xiyu.bid.scoreparse.application.ScoreScoringVersionService;
import com.xiyu.bid.scoreparse.application.EstimatedScoreService;
import com.xiyu.bid.scoreparse.application.ScoreExtractionException;
import com.xiyu.bid.scoreparse.application.ScoreExtractionCodec;
import com.xiyu.bid.scoreparse.application.SidecarStreamTextExtractor;
import com.xiyu.bid.scoreparse.application.BidFileQuotaException;
import com.xiyu.bid.scoreparse.application.BidDocumentUploadService;
import com.xiyu.bid.scoreparse.application.StreamingBidFileFetcher;
import com.xiyu.bid.scoreparse.application.TenderFileQuotaException;
import com.xiyu.bid.scoreparse.application.ScoreVersionActivationService;
import com.xiyu.bid.scoreparse.application.ScoreExtractionSnapshotWriter;
import com.xiyu.bid.scoreparse.application.TenderDocumentStoredListener;
import com.xiyu.bid.scoreparse.application.TenderExecutionSource;
import com.xiyu.bid.scoreparse.application.ScoreParseReviewAppService;
import com.xiyu.bid.scoreparse.application.MultimodalScoreFactExtractor;
import com.xiyu.bid.scoreparse.application.ScoreItemPersistenceService;
import com.xiyu.bid.scoreparse.application.OversizedImageResponseException;
import com.xiyu.bid.scoreparse.application.SidecarImageExtractor;
import com.xiyu.bid.scoreparse.application.TempBidFileStore;
import com.xiyu.bid.scoreparse.application.ScoreParseProgressService;
import com.xiyu.bid.scoreparse.application.SidecarImageExtraction;
import com.xiyu.bid.scoreparse.application.TenderTextSource;
import com.xiyu.bid.scoreparse.application.SidecarDocumentArtifactExtractor;
import com.xiyu.bid.scoreparse.application.ScoreExtractionTaskFinalizer;
import com.xiyu.bid.scoreparse.application.BidFileLoadingCoordinator;
import com.xiyu.bid.scoreparse.application.InitiationTenderTextResolver;
import com.xiyu.bid.scoreparse.application.VersionedScoreParseCompletion;
import com.xiyu.bid.scoreparse.application.ScoreResultOverwriter;
import com.xiyu.bid.scoreparse.application.ScoreParseAppService;
import com.xiyu.bid.scoreparse.application.OversizedBidTextException;
import com.xiyu.bid.scoreparse.application.ScoreScoringAppService;
import com.xiyu.bid.scoreparse.application.ScoreExtractionAppService;
import com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Both application services call ProjectAccessScopeService.assertCurrentUserCanAccessProject before task lookup. */
@RestController @RequestMapping("/api/projects/{projectId}/score-parse/extraction")
@RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
public class ScoreExtractionController {
    private final ScoreExtractionAppService extraction;
    private final ScoreVersionActivationService activation;
    @GetMapping("/latest")
    public ApiResponse<ScoreExtractionAppService.View> latest(@PathVariable Long projectId) {return ApiResponse.success(extraction.getLatest(projectId));}
    @GetMapping("/{taskId}")
    public ApiResponse<ScoreExtractionAppService.View> byTask(@PathVariable Long projectId,@PathVariable String taskId) {return ApiResponse.success(extraction.getByTaskId(projectId,taskId));}
    @PostMapping("/{taskId}/activate")
    public ApiResponse<ScoreExtractionAppService.View> activate(@PathVariable Long projectId,@PathVariable String taskId,
            @RequestBody ActivateScoreExtractionCommand command,Authentication authentication) {
        activation.activate(projectId,taskId,command,authentication==null?null:authentication.getName());
        return ApiResponse.success(extraction.getByTaskId(projectId,taskId));
    }
    @ExceptionHandler(ScoreExtractionException.class)
    public ResponseEntity<ApiResponse<Void>> extractionError(ScoreExtractionException e) {return ResponseEntity.status(e.status()).body(ApiResponse.error(e.status(),e.getMessage()));}
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e) {return ResponseEntity.status(403).body(ApiResponse.error(403,"PROJECT_ACCESS_DENIED"));}
}
