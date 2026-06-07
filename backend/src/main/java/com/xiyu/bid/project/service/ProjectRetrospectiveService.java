// Input: 提交/审核复盘请求 + 当前用户
// Output: RetrospectiveDTO；通过策略校验+持久化+审计；审核通过后推进 stage
// Pos: project/service/ - 编排层（不含纯规则）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.entity.ProjectRetrospective;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.core.ProjectFieldLockPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.core.RetrospectiveFieldPolicy;
import com.xiyu.bid.project.dto.RetrospectiveDTO;
import com.xiyu.bid.project.dto.RetrospectiveReviewRequest;
import com.xiyu.bid.project.dto.RetrospectiveSubmitRequest;
import com.xiyu.bid.project.repository.ProjectRetrospectiveRepository;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectRetrospectiveService {

    private final ProjectRetrospectiveRepository repository;
    private final ProjectRepository projectRepository;
    private final ProjectStageService projectStageService;

    @Auditable(action = "SUBMIT_RETROSPECTIVE", entityType = "ProjectRetrospective", description = "提交项目复盘")
    public RetrospectiveDTO submit(Long projectId, RetrospectiveSubmitRequest req, Long currentUserId) {
        mustGetProject(projectId);
        // §3.6 全字段锁定 — CLOSED 阶段拒绝写入。
        ProjectStage stage = projectStageService.currentStage(projectId);
        var lockDecision = ProjectFieldLockPolicy.assertWritable(stage, "retrospective");
        if (!lockDecision.allowed()) {
            var deny = (ProjectFieldLockPolicy.Decision.Deny) lockDecision;
            throw new ResponseStatusException(HttpStatus.LOCKED, deny.reason());
        }
        BidResultType rt = req.getResultType();
        var input = new RetrospectiveFieldPolicy.RetrospectiveInput(
                req.getSummary(), req.getWinFactors(), req.getLossReasons(),
                req.getCompetitorNotes(), req.getImprovementActions(),
                req.getMeetingTime(), req.getMeetingFormat(), req.getMeetingParticipants(),
                toCsv(req.getLossReasonFlags()),
                req.getProcessHighlights(), req.getPostWinImprovements(),
                req.getProcessProblems(), req.getPostLossMeasures(),
                toCsvLong(req.getReportFileIds()));
        var decision = RetrospectiveFieldPolicy.validate(rt, input);
        if (!decision.allowed()) {
            var deny = (RetrospectiveFieldPolicy.Decision.Deny) decision;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, deny.reason());
        }
        ProjectRetrospective entity = repository.findByProjectId(projectId)
                .orElseGet(newEntity(projectId, currentUserId));
        entity.setResultType(rt.name());
        entity.setSummary(req.getSummary());
        entity.setWinFactors(req.getWinFactors());
        entity.setLossReasons(req.getLossReasons());
        entity.setCompetitorNotes(req.getCompetitorNotes());
        entity.setImprovementActions(req.getImprovementActions());
        entity.setProcessHighlights(req.getProcessHighlights());
        entity.setMeetingTime(parseDateTime(req.getMeetingTime()));
        entity.setMeetingFormat(req.getMeetingFormat());
        entity.setMeetingParticipants(req.getMeetingParticipants());
        entity.setLossReasonFlags(toCsv(req.getLossReasonFlags()));
        entity.setPostWinImprovements(req.getPostWinImprovements());
        entity.setProcessProblems(req.getProcessProblems());
        entity.setPostLossMeasures(req.getPostLossMeasures());
        entity.setReportFileIds(toCsvLong(req.getReportFileIds()));
        entity.setReviewStatus(ProjectRetrospective.ReviewStatus.PENDING_REVIEW.name());
        entity.setUpdatedBy(currentUserId);
        ProjectRetrospective saved = repository.save(entity);
        // §5.4: 复盘提交后推进 RESULT_PENDING → RETROSPECTIVE（幂等跳过）
        ProjectStage afterSaveStage = projectStageService.currentStage(projectId);
        if (afterSaveStage == ProjectStage.RESULT_PENDING) {
            projectStageService.requestTransition(projectId, ProjectStage.RETROSPECTIVE,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
        }
        // 复盘提交后同时推进 RETROSPECTIVE → CLOSED（幂等跳过），
        // 与 review() 中审批通过后的推进路径互补：
        // - submit() 直达 CLOSED（无需审批的场景）
        // - review() 中的推进保留作为审批通过后的兜底
        ProjectStage afterRetroTransition = projectStageService.currentStage(projectId);
        if (afterRetroTransition == ProjectStage.RETROSPECTIVE) {
            projectStageService.requestTransition(projectId, ProjectStage.CLOSED,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
        }
        log.info("Retrospective submitted project={} status=PENDING_REVIEW user={}", projectId, currentUserId);
        return toDto(saved);
    }

    @Auditable(action = "REVIEW_RETROSPECTIVE", entityType = "ProjectRetrospective", description = "审核项目复盘")
    public RetrospectiveDTO review(Long projectId, RetrospectiveReviewRequest req, Long reviewerId) {
        mustGetProject(projectId);
        // §3.6 全字段锁定 — CLOSED 阶段拒绝写入。
        ProjectStage stage = projectStageService.currentStage(projectId);
        var lockDecision = ProjectFieldLockPolicy.assertWritable(stage, "retrospective");
        if (!lockDecision.allowed()) {
            var deny = (ProjectFieldLockPolicy.Decision.Deny) lockDecision;
            throw new ResponseStatusException(HttpStatus.LOCKED, deny.reason());
        }
        ProjectRetrospective entity = repository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectRetrospective", String.valueOf(projectId)));
        if (!ProjectRetrospective.ReviewStatus.PENDING_REVIEW.name().equals(entity.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "复盘当前状态不可审核：" + entity.getReviewStatus());
        }
        boolean approve = Boolean.TRUE.equals(req.getApprove());
        if (!approve && (req.getComment() == null || req.getComment().trim().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "驳回必须提供 comment");
        }
        entity.setReviewStatus(approve
                ? ProjectRetrospective.ReviewStatus.APPROVED.name()
                : ProjectRetrospective.ReviewStatus.REJECTED.name());
        entity.setReviewComment(req.getComment());
        entity.setReviewedBy(reviewerId);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setUpdatedBy(reviewerId);
        ProjectRetrospective saved = repository.save(entity);
        if (approve && stage == ProjectStage.RETROSPECTIVE) {
            projectStageService.requestTransition(projectId, ProjectStage.CLOSED,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
        }
        log.info("Retrospective reviewed project={} approve={} reviewer={}", projectId, approve, reviewerId);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<RetrospectiveDTO> getByProject(Long projectId) {
        return repository.findByProjectId(projectId).map(this::toDto);
    }

    private Project mustGetProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }

    private Supplier<ProjectRetrospective> newEntity(Long projectId, Long userId) {
        return () -> ProjectRetrospective.builder()
                .projectId(projectId)
                .reviewStatus(ProjectRetrospective.ReviewStatus.PENDING_REVIEW.name())
                .createdBy(userId)
                .build();
    }

    private static String toCsv(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return String.join(",", values);
    }

    private static List<String> fromCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return List.of(csv.split(","));
    }

    private static String toCsvLong(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
    }

    private static List<Long> fromCsvLong(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        try {
            return java.util.Arrays.stream(csv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
        } catch (NumberFormatException ex) {
            log.warn("reportFileIds CSV 解析失败 csv='{}' error={}", csv, ex.getMessage());
            return List.of();
        }
    }

    private static LocalDateTime parseDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDateTime.parse(iso.replace(" ", "T"));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private RetrospectiveDTO toDto(ProjectRetrospective e) {
        return RetrospectiveDTO.builder()
                .id(e.getId())
                .projectId(e.getProjectId())
                .resultType(e.getResultType())
                .summary(e.getSummary())
                .winFactors(e.getWinFactors())
                .lossReasons(e.getLossReasons())
                .competitorNotes(e.getCompetitorNotes())
                .improvementActions(e.getImprovementActions())
                .processHighlights(e.getProcessHighlights())
                .meetingTime(e.getMeetingTime())
                .meetingFormat(e.getMeetingFormat())
                .meetingParticipants(e.getMeetingParticipants())
                .lossReasonFlags(fromCsv(e.getLossReasonFlags()))
                .postWinImprovements(e.getPostWinImprovements())
                .processProblems(e.getProcessProblems())
                .postLossMeasures(e.getPostLossMeasures())
                .reportFileIds(fromCsvLong(e.getReportFileIds()))
                .reviewStatus(e.getReviewStatus())
                .reviewedBy(e.getReviewedBy())
                .reviewedAt(e.getReviewedAt())
                .reviewComment(e.getReviewComment())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
