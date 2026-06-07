// Input: 登记结果请求 + 当前用户
// Output: ResultDTO；通过策略校验+持久化+审计；§5.4 自动推进 RESULT_PENDING → RETROSPECTIVE/CLOSED
// Pos: project/service/ - 编排层（不含纯规则）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.ProjectFieldLockPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.core.ResultRegistrationFieldPolicy;
import com.xiyu.bid.project.dto.ResultDTO;
import com.xiyu.bid.project.dto.ResultRegistrationRequest;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectResultCompetitorRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.project.entity.ProjectResultCompetitor;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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
    private final ProjectStageService projectStageService;
    private final ProjectAccessScopeService projectAccessScopeService;

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
        // CRM 回调（占位：后续对接CRM系统）
        logCrmCallback(projectId, req.getResultType(), req.getCompetitors(), currentUserId, LocalDateTime.now());
        // §5.4: 结果登记后按结果类型分流推进 RESULT_PENDING → RETROSPECTIVE / CLOSED（幂等跳过）
        ProjectStage current = projectStageService.currentStage(projectId);
        if (current == ProjectStage.RESULT_PENDING) {
            ProjectStage nextStage = ProjectStageTransitionPolicy.decideResultNext(req.getResultType());
            projectStageService.requestTransition(projectId, nextStage,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
        }
        log.info("ProjectResult registered project={} type={} nextStage={} user={}",
                projectId, req.getResultType(),
                current == ProjectStage.RESULT_PENDING
                        ? ProjectStageTransitionPolicy.decideResultNext(req.getResultType())
                        : current,
                currentUserId);
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

    private static boolean isStrBlank(String s) {
        return s == null || s.isBlank();
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
     * CRM 回调占位（PRD §3.3.1.4 注）。
     * 结果确认后，需回调告知CRM：标讯ID、项目ID、投标结果、竞争对手情况表（竞争对手名称、折扣、账期、其他说明）、操作人、操作时间。
     * 当前仅记录日志，后续对接 CRM 接口时替换为实际调用。
     * TODO: 对接真实 CRM API 时将 competitorDetail 格式改为 JSON 序列化（当前日志格式仅用于调试）。
     */
    private void logCrmCallback(Long projectId, BidResultType resultType,
                                List<ResultRegistrationRequest.CompetitorRow> competitors,
                                Long userId, LocalDateTime operateTime) {
        try {
            Long tenderId = findTenderIdByProject(projectId);
            String competitorDetail = (competitors == null || competitors.isEmpty()) ? "[]"
                    : competitors.stream()
                    .filter(c -> c != null && !isStrBlank(c.name()))
                    .map(c -> String.format("{name=%s, discount=%s, paymentTerm=%s, notes=%s}",
                            c.name(), c.discount(), c.paymentTerm(), c.notes()))
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
            log.info("CRM_CALLBACK tenderId={} projectId={} resultType={} competitors={} userId={} operateTime={}",
                    tenderId, projectId, resultType, competitorDetail, userId, operateTime);
        } catch (RuntimeException e) {
            log.warn("CRM 回调节点日志记录失败（不影响主流程）projectId={} error={}",
                    projectId, e.getMessage());
        }
    }

    /**
     * 查询项目关联的标讯ID（用于CRM回调）。
     * 从 Project 实体的 tenderId 字段获取；若不存在则返回 0。
     */
    private Long findTenderIdByProject(Long projectId) {
        return projectRepository.findById(projectId)
                .map(p -> p.getTenderId())
                .orElse(0L);
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
