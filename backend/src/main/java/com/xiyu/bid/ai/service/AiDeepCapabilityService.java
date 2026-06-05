// Input: AI repositories, DTOs, and support services
// Output: AI Deep Capability business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.core.AiJobLifecyclePolicy;
import com.xiyu.bid.ai.core.ProjectScorePreviewPolicy;
import com.xiyu.bid.ai.core.TenderAiAnalysisPolicy;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.ProjectAiCardsDTO;
import com.xiyu.bid.ai.dto.ProjectScorePreviewDTO;
import com.xiyu.bid.ai.dto.ProjectScorePreviewRequestDTO;
import com.xiyu.bid.ai.dto.TenderAiAnalysisDTO;
import com.xiyu.bid.ai.entity.AiAnalysisJob;
import com.xiyu.bid.ai.entity.AiAnalysisResult;
import com.xiyu.bid.ai.entity.ProjectScorePreview;
import com.xiyu.bid.ai.repository.AiAnalysisJobRepository;
import com.xiyu.bid.ai.repository.AiAnalysisResultRepository;
import com.xiyu.bid.ai.repository.ProjectScorePreviewRepository;
import com.xiyu.bid.competitionintel.dto.CompetitionAnalysisDTO;
import com.xiyu.bid.competitionintel.service.CompetitionIntelService;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.service.ComplianceCheckService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.roi.dto.ROIAnalysisDTO;
import com.xiyu.bid.roi.service.ROIAnalysisService;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisDTO;
import com.xiyu.bid.scoreanalysis.service.ScoreAnalysisService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiDeepCapabilityService {

    private final AiService aiService;
    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final AiAnalysisJobRepository aiAnalysisJobRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final ProjectScorePreviewRepository projectScorePreviewRepository;
    private final ScoreAnalysisService scoreAnalysisService;
    private final CompetitionIntelService competitionIntelService;
    private final ComplianceCheckService complianceCheckService;
    private final ROIAnalysisService roiAnalysisService;
    private final ObjectMapper objectMapper;
    private final ProjectAccessScopeService projectAccessScopeService;

    @Transactional(readOnly = true)
    public Optional<TenderAiAnalysisDTO> getLatestTenderAnalysis(Long tenderId) {
        assertCanAccessTender(tenderId);
        return aiAnalysisResultRepository
            .findFirstByTenderIdAndAnalysisTypeOrderByCreatedAtDesc(tenderId, AiAnalysisJob.AnalysisType.TENDER_ANALYSIS)
            .map(this::deserializeTenderAnalysis);
    }

    @Transactional
    public TenderAiAnalysisDTO analyzeTender(Long tenderId, Long requestedBy) {
        Tender tender = tenderRepository.findById(tenderId)
            .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        assertCanAccessTender(tender);
        AiAnalysisJob job = createJob(AiAnalysisJob.AnalysisType.TENDER_ANALYSIS, AiAnalysisJob.TargetType.TENDER, tenderId, requestedBy);

        try {
            AiAnalysisResponse response = aiService.analyzeTenderSync(tenderId, Map.of(
                "budget", tender.getBudget() == null ? java.math.BigDecimal.ZERO : tender.getBudget(),
                "source", tender.getSource() == null ? "" : tender.getSource(),
                "deadline", tender.getDeadline() == null ? "" : tender.getDeadline().toString()
            ));
            TenderAiAnalysisPolicy.AnalysisResult analysis = TenderAiAnalysisPolicy.evaluate(
                AiDeepCapabilityAssembler.toTenderAnalysisInput(response, LocalDate.now())
            );
            TenderAiAnalysisDTO dto = AiDeepCapabilityAssembler.toTenderAnalysisDto(tenderId, analysis);
            aiAnalysisResultRepository.save(AiDeepCapabilityAssembler.toTenderAnalysisResult(
                job.getId(),
                tenderId,
                analysis,
                response == null ? null : response.getRiskLevel(),
                AiDeepCapabilityAssembler.writeValue(dto, objectMapper)
            ));
            markJobCompleted(job);
            return dto;
        } catch (RuntimeException ex) {
            markJobFailed(job, ex);
            throw ex;
        }
    }

    @Transactional
    public ProjectScorePreviewDTO createScorePreview(ProjectScorePreviewRequestDTO request, Long requestedBy) {
        assertCanAccessScorePreviewTarget(request);
        AiAnalysisJob job = createJob(
            AiAnalysisJob.AnalysisType.PROJECT_SCORE_PREVIEW,
            request.getProjectId() != null ? AiAnalysisJob.TargetType.PROJECT : AiAnalysisJob.TargetType.TENDER,
            request.getProjectId() != null ? request.getProjectId() : request.getTenderId(),
            requestedBy
        );

        try {
            ProjectScorePreviewPolicy.PreviewResult preview = ProjectScorePreviewPolicy.evaluate(
                AiDeepCapabilityAssembler.toProjectScorePreviewInput(request)
            );
            ProjectScorePreviewDTO dto = AiDeepCapabilityAssembler.toProjectScorePreviewDto(request.getProjectId(), request.getTenderId(), preview);
            String tagsJson = AiDeepCapabilityAssembler.writeValue(request.getTags() == null ? List.of() : request.getTags(), objectMapper);
            ProjectScorePreview entity = projectScorePreviewRepository.save(
                AiDeepCapabilityAssembler.toProjectScorePreviewEntity(
                    request,
                    preview,
                    tagsJson,
                    AiDeepCapabilityAssembler.writeValue(dto, objectMapper)
                )
            );
            dto.setId(entity.getId());
            markJobCompleted(job);
            return dto;
        } catch (RuntimeException ex) {
            markJobFailed(job, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ProjectAiCardsDTO getProjectAiCards(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));

        ApiResponse<ScoreAnalysisDTO> scoreResponse = scoreAnalysisService.getAnalysisByProject(projectId);
        ScoreAnalysisDTO score = scoreResponse.isSuccess() ? scoreResponse.getData() : null;
        List<CompetitionAnalysisDTO> competition = competitionIntelService.getAnalysisByProject(projectId);
        List<ComplianceCheckResult> compliance = complianceCheckService.getCheckResultsByProjectId(projectId);

        ROIAnalysisDTO roi = null;
        try {
            roi = roiAnalysisService.getAnalysisByProject(projectId);
        } catch (ResourceNotFoundException ignored) {
            log.debug("No ROI analysis for project {}", projectId);
        }

        return ProjectAiCardsDTO.builder()
            .score(score)
            .competition(competition)
            .compliance(compliance)
            .roi(roi)
            .build();
    }

    private AiAnalysisJob createJob(AiAnalysisJob.AnalysisType analysisType, AiAnalysisJob.TargetType targetType, Long targetId, Long requestedBy) {
        return aiAnalysisJobRepository.save(AiAnalysisJob.builder()
            .analysisType(analysisType)
            .targetType(targetType)
            .targetId(targetId)
            .requestedBy(requestedBy)
            .status(AiDeepCapabilityAssembler.toEntityStatus(AiJobLifecyclePolicy.pending().status()))
            .build());
    }

    private void markJobCompleted(AiAnalysisJob job) {
        applyLifecycle(job, AiJobLifecyclePolicy.completed(LocalDateTime.now()));
    }

    private void markJobFailed(AiAnalysisJob job, RuntimeException ex) {
        applyLifecycle(job, AiJobLifecyclePolicy.failed(ex.getMessage(), LocalDateTime.now()));
    }

    private void applyLifecycle(AiAnalysisJob job, AiJobLifecyclePolicy.JobLifecycle lifecycle) {
        job.setStatus(AiDeepCapabilityAssembler.toEntityStatus(lifecycle.status()));
        job.setCompletedAt(lifecycle.completedAt());
        job.setErrorMessage(lifecycle.errorMessage());
        aiAnalysisJobRepository.save(job);
    }

    private TenderAiAnalysisDTO deserializeTenderAnalysis(AiAnalysisResult result) {
        try {
            return objectMapper.readValue(result.getPayloadJson(), TenderAiAnalysisDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read tender AI analysis result", e);
        }
    }

    private void assertCanAccessScorePreviewTarget(ProjectScorePreviewRequestDTO request) {
        if (request.getProjectId() != null) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(request.getProjectId());
            return;
        }
        if (request.getTenderId() != null) {
            assertCanAccessTender(request.getTenderId());
        }
    }

    private void assertCanAccessTender(Long tenderId) {
        Tender tender = tenderRepository.findById(tenderId)
            .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        assertCanAccessTender(tender);
    }

    private void assertCanAccessTender(Tender tender) {
        List<com.xiyu.bid.entity.Project> linkedProjects = projectRepository.findByTenderId(tender.getId());
        if (linkedProjects.isEmpty() && !projectAccessScopeService.currentUserHasAdminAccess()) {
            throw new AccessDeniedException("权限不足，无法访问未关联项目的标讯");
        }
        for (com.xiyu.bid.entity.Project project : linkedProjects) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(project.getId());
        }
    }
}
