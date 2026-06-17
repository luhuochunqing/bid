// Input: 提交复盘请求 + 当前用户
// Output: RetrospectiveDTO；通过策略校验+持久化+审计；提交即推进 stage（§2.6 无需审核）
// Pos: project/service/ - 编排层（不含纯规则）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.ProjectRetrospective;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.core.ProjectFieldLockPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.core.RetrospectiveFieldPolicy;
import com.xiyu.bid.project.dto.RetrospectiveDTO;
import com.xiyu.bid.project.dto.RetrospectiveSubmitRequest;
import com.xiyu.bid.project.repository.ProjectRetrospectiveRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
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
    private final UserRepository userRepository;
    private final NotificationApplicationService notificationService;

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
        entity.setReviewStatus(ProjectRetrospective.ReviewStatus.APPROVED.name());
        entity.setUpdatedBy(currentUserId);
        ProjectRetrospective saved = repository.save(entity);
        // §5.4: 复盘提交后推进 RESULT_PENDING → RETROSPECTIVE（幂等跳过）
        ProjectStage afterSaveStage = projectStageService.currentStage(projectId);
        if (afterSaveStage == ProjectStage.RESULT_PENDING) {
            projectStageService.requestTransition(projectId, ProjectStage.RETROSPECTIVE,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
        }
        // §2.6: 复盘无需审核，提交即转。submit() 直达 CLOSED。
        ProjectStage afterRetroTransition = projectStageService.currentStage(projectId);
        if (afterRetroTransition == ProjectStage.RETROSPECTIVE) {
            projectStageService.requestTransition(projectId, ProjectStage.CLOSED,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
        }
        log.info("Retrospective submitted project={} status=APPROVED user={}", projectId, currentUserId);

        // 通知 #14: 提交复盘 → admin
        sendRetrospectiveSubmitNotification(projectId, currentUserId);

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
                .reviewStatus(ProjectRetrospective.ReviewStatus.APPROVED.name())
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

    private void sendRetrospectiveSubmitNotification(Long projectId, Long userId) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            String projectName = project.getName();
            String submitterName = userRepository.findById(userId)
                    .map(User::getFullName).orElse("");

            List<Long> adminIds = getAdminUserIds();

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.APPROVAL.name(),
                    "Project",
                    projectId,
                    "复盘审核：项目提交复盘 - " + projectName,
                    String.format("项目名称：%s\n提交人：%s\n\n请前往项目复盘页面审核。", projectName, submitterName),
                    java.util.Map.of("projectId", String.valueOf(projectId), "projectName", projectName,
                            "targetUrl", "/project/" + projectId + "/retrospective"),
                    adminIds
            ), userId);
        } catch (RuntimeException e) {
            log.warn("sendRetrospectiveSubmitNotification failed for project={}: {}", projectId, e.getMessage());
        }
    }

    private List<Long> getAdminUserIds() {
        return userRepository.findEnabledByRoleProfileCodes(List.of("admin", "bid_admin", "bid_lead"))
                .stream().map(User::getId).collect(java.util.stream.Collectors.toList());
    }
}
