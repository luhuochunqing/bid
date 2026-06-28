// Input: 登记结果请求 + 当前用户
// Output: ResultDTO；通过策略校验+持久化+审计；§5.4 自动推进 RESULT_PENDING → RETROSPECTIVE/CLOSED
// Pos: project/service/ - 编排层（不含纯规则）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.ProjectFieldLockPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.core.ResultRegistrationFieldPolicy;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.project.dto.ResultDTO;
import com.xiyu.bid.project.dto.ResultRegistrationRequest;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectResultCompetitorRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.project.entity.ProjectResultCompetitor;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.tender.service.TenderStatusSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PRD §3.4 结果确认编排：
 * <ul>
 *   <li>幂等：同 projectId 已登记 → 409 CONFLICT。</li>
 *   <li>策略：调 {@link ResultRegistrationFieldPolicy}（FAILED/ABANDONED 必须 summary，全部必须 evidence）。</li>
 *   <li>审计：@Auditable("REGISTER_PROJECT_RESULT")。</li>
 *   <li>FSM 推进：成功登记后自动推进 RESULT_PENDING → RETROSPECTIVE/CLOSED（按结果类型分流，幂等跳过）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectResultRegistrationService {

    private final ProjectResultRepository repository;
    private final ProjectResultCompetitorRepository competitorRepository;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final ProjectStageService projectStageService;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectNotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenderStatusSyncService tenderStatusSyncService;

    @Auditable(action = "REGISTER_PROJECT_RESULT", entityType = "ProjectResult", description = "登记项目结果")
    public ResultDTO register(Long projectId, ResultRegistrationRequest req, Long currentUserId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        Project project = mustGetProject(projectId);
        // §3.6 全字段锁定 — CLOSED 阶段拒绝写入。
        ProjectStage stage = projectStageService.currentStage(projectId);
        var lockDecision = ProjectFieldLockPolicy.assertWritable(stage, "result");
        if (!lockDecision.allowed()) {
            var deny = (ProjectFieldLockPolicy.Decision.Deny) lockDecision;
            throw new ResponseStatusException(HttpStatus.LOCKED, deny.reason());
        }
        if (repository.findByProjectId(projectId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目结果已登记，不可重复提交");
        }
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(req.getResultType())
                .awardAmount(req.getAwardAmount())
                .contractStartDate(req.getContractStartDate())
                .contractEndDate(req.getContractEndDate())
                .evidenceFileIds(req.getEvidenceFileIds())
                .summary(req.getSummary())
                .build();
        var decision = ResultRegistrationFieldPolicy.validate(input);
        if (!decision.allowed()) {
            var deny = (ResultRegistrationFieldPolicy.Decision.Deny) decision;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, deny.reason());
        }
        ProjectResult entity = ProjectResult.builder()
                .projectId(project.getId())
                .resultType(req.getResultType().name())
                .awardAmount(req.getAwardAmount())
                .contractStartDate(req.getContractStartDate())
                .contractEndDate(req.getContractEndDate())
                .evidenceAttachmentId(firstId(req.getEvidenceFileIds()))
                .evidenceDocIds(toCsv(req.getEvidenceFileIds()))
                .summary(req.getSummary())
                .registeredAt(LocalDateTime.now())
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();
        ProjectResult saved = repository.save(entity);
        // 保存竞争对手情况（PRD §3.3.1.4）
        persistCompetitors(saved.getId(), req.getCompetitors());
        // §4.2 CRM 回调：发布领域事件，由 ProjectResultConfirmedWebhookListener 监听后入队
        // WebhookDeliveryTask，复用 §4.1 的 1min/5min/15min 重试机制（AFTER_COMMIT 保证主事务成功）。
        User currentUser = userRepository.findById(currentUserId).orElse(null);
        String operatorName = formatOperatorName(currentUser);
        eventPublisher.publishEvent(ProjectResultConfirmedEvent.of(
                projectId, project.getTenderId(), req.getResultType(),
                req.getSummary(),
                req.getEvidenceFileIds(),
                toCompetitorSnapshots(req.getCompetitors()),
                currentUserId, operatorName, saved.getId()));
        // §5.4: 结果登记后按结果类型分流推进 RESULT_PENDING → RETROSPECTIVE / CLOSED（幂等跳过）
        // 传入 bidResult 让 ProjectStatusPolicy 正确计算 Project.Status（WON/LOST/FAILED/ABANDONED）
        ProjectStage current = projectStageService.currentStage(projectId);
        if (current == ProjectStage.RESULT_PENDING) {
            ProjectStage nextStage = ProjectStageTransitionPolicy.decideResultNext(req.getResultType());
            projectStageService.requestTransition(projectId, nextStage,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY, req.getResultType().name());
        }
        log.info("ProjectResult registered project={} type={} nextStage={} user={}",
                projectId, req.getResultType(),
                current == ProjectStage.RESULT_PENDING
                        ? ProjectStageTransitionPolicy.decideResultNext(req.getResultType())
                        : current,
                currentUserId);

        // 通知 #13: 登记中标/未中标/流标 → 团队成员+管理员
        notificationService.notifyResultRegistered(projectId, req.getResultType().name(), currentUserId);

        // 项目结果登记后同步标讯状态到与项目结果一致的终态（修复标讯转项目后状态断链）
        // 同步失败不阻塞项目结果登记（项目侧已成功，标讯同步是副作用，失败只 log warn）
        try {
            tenderStatusSyncService.syncFromProjectResult(project.getTenderId(), req.getResultType());
        } catch (RuntimeException ex) {
            log.warn("Failed to sync tender status from project result: project={}, tenderId={}, result={}",
                    projectId, project.getTenderId(), req.getResultType(), ex);
        }

        return toDto(saved, currentUserId);
    }

    @Transactional(readOnly = true)
    public Optional<ResultDTO> getByProject(Long projectId) {
        return repository.findByProjectId(projectId).map(e -> toDto(e, e.getCreatedBy()));
    }

    private Project mustGetProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }

    private static Long firstId(List<Long> ids) {
        return (ids == null || ids.isEmpty()) ? null : ids.get(0);
    }

    private static String toCsv(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().filter(java.util.Objects::nonNull)
                .map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * H4: 容错 CSV 解析 — 任一段解析失败则整体放弃并返回空（log WARN）。
     * 历史脏数据（手填、空格、非数字）不应该把 GET 路径打挂。
     */
    private static List<Long> fromCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        try {
            return Arrays.stream(csv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
        } catch (NumberFormatException ex) {
            log.warn("evidenceDocIds CSV 解析失败，返回空列表 csv='{}' error={}", csv, ex.getMessage());
            return List.of();
        }
    }

    private void persistCompetitors(Long resultId, List<ResultRegistrationRequest.CompetitorRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (int i = 0; i < rows.size(); i++) {
            ResultRegistrationRequest.CompetitorRow row = rows.get(i);
            if (isBlankRow(row)) continue;
            ProjectResultCompetitor entity = ProjectResultCompetitor.builder()
                    .resultId(resultId)
                    .name(row.name())
                    .discount(row.discount())
                    .paymentTerm(row.paymentTerm())
                    .notes(row.notes())
                    .sortOrder(i)
                    .build();
            competitorRepository.save(entity);
        }
    }

    private boolean isBlankRow(ResultRegistrationRequest.CompetitorRow row) {
        return row == null || (isStrBlank(row.name()) && isStrBlank(row.discount())
                && isStrBlank(row.paymentTerm()) && isStrBlank(row.notes()));
    }

    /**
     * 将 Controller DTO 转为领域事件快照，避免事件层依赖 Controller DTO（分层违规）。
     */
    private static List<ProjectResultConfirmedEvent.CompetitorSnapshot> toCompetitorSnapshots(
            List<ResultRegistrationRequest.CompetitorRow> competitors) {
        if (competitors == null || competitors.isEmpty()) return List.of();
        return competitors.stream()
                .map(c -> new ProjectResultConfirmedEvent.CompetitorSnapshot(
                        c.name(), c.discount(), c.paymentTerm(), c.notes()))
                .toList();
    }

    private static boolean isStrBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String formatOperatorName(User user) {
        if (user == null) {
            return "unknown";
        }
        String fullName = user.getFullName();
        // CO-300: employeeNumber 为空时回退到 username（getDisplayEmployeeNumber 已封装此逻辑），
        // 确保操作人始终为"姓名（工号）"格式，与 AuditLogItemMapper 保持一致。
        String employeeId = user.getDisplayEmployeeNumber();
        if (isStrBlank(fullName)) {
            return isStrBlank(employeeId) ? "unknown" : employeeId;
        }
        if (!isStrBlank(employeeId)) {
            return "%s（%s）".formatted(fullName, employeeId);
        }
        return fullName;
    }

    private List<ResultDTO.CompetitorRow> loadCompetitors(Long resultId) {
        List<ProjectResultCompetitor> entities =
                competitorRepository.findByResultIdOrderBySortOrderAsc(resultId);
        return entities.stream()
                .map(e -> new ResultDTO.CompetitorRow(e.getName(), e.getDiscount(),
                        e.getPaymentTerm(), e.getNotes()))
                .toList();
    }

    /**
     * 结果类型 → 凭证标签映射（与前端 evidenceTagMap 保持同步）。
     * 标签根据 resultType 自动推导，无需额外持久化。
     */
    private static String resolveEvidenceTags(String resultType) {
        if (resultType == null) return "";
        return switch (resultType) {
            case "WON" -> "中标通知书";
            case "LOST" -> "未中标说明或官方结果公告";
            case "FAILED" -> "流标公告";
            case "ABANDONED" -> "弃标说明";
            default -> "";
        };
    }

    private ResultDTO toDto(ProjectResult e, Long registeredBy) {
        List<ResultDTO.CompetitorRow> competitorRows = loadCompetitors(e.getId());
        return ResultDTO.builder()
                .id(e.getId())
                .projectId(e.getProjectId())
                .resultType(e.getResultType())
                .awardAmount(e.getAwardAmount())
                .contractStartDate(e.getContractStartDate())
                .contractEndDate(e.getContractEndDate())
                .evidenceFileIds(fromCsv(e.getEvidenceDocIds()))
                .summary(e.getSummary())
                .evidenceTags(resolveEvidenceTags(e.getResultType()))
                .registeredAt(e.getRegisteredAt())
                .registeredBy(registeredBy)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .competitors(competitorRows)
                .build();
    }
}
