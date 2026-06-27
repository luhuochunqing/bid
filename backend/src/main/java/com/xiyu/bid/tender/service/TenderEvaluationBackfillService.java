// Input: 评估表请求（TenderEvaluationSubmitRequest）、标讯 ID、操作人 ID
// Output: TenderEvaluationDTO（含状态机切换的最新视图）
// Pos: Service/业务编排层（命令式外壳）
// 维护声明: 仅做编排：取标讯/评估人 -> Guard -> 委托 Policy 校验 -> 状态切换 -> 持久化。
//          业务规则（必填/范围/客户信息矩阵）一律下沉至 com.xiyu.bid.tender.core.*Policy。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.core.FieldError;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluation.EvaluationStatus;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CO-310 修复：CRM 商机关联回填评估表专用服务。
 * <p>从 {@link TenderEvaluationSubmissionService} 拆分出来，避免原服务超过 line-budget（300 行）。
 * <p>绕过 {@code canFill} 实例级守卫（latest assignee 检查），因为调用方
 * {@link TenderCommandService#linkCrmOpportunity} 已通过
 * {@code assertCanUpdateTender} 守卫验证用户有权编辑该标讯。
 * <p>sales 角色作为投标项目负责人关联 CRM 商机是其核心职责，不应受
 * latest assignee 限制。此服务直接保存评估表数据并提交，一步完成回填。
 * <p>逻辑等价于 {@link TenderEvaluationSubmissionService#saveDraft} +
 * {@link TenderEvaluationSubmissionService#submit}，但跳过 canFill 检查。
 */
@Service
@Slf4j
@Transactional
public class TenderEvaluationBackfillService {

    private final TenderEvaluationRepository evaluationRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final TenderProjectAccessGuard accessGuard;
    private final TenderAssignmentPermissions permissions;
    private final ApplicationEventPublisher eventPublisher;
    private final TenderEvaluationGapFilesSync gapFilesSync;
    private final TenderEvaluationDocumentService documentService;
    private final TenderEvaluationSubmissionMapper mapper = new TenderEvaluationSubmissionMapper();
    private final Clock clock;

    public TenderEvaluationBackfillService(
            TenderEvaluationRepository evaluationRepository,
            TenderRepository tenderRepository,
            UserRepository userRepository,
            TenderProjectAccessGuard accessGuard,
            TenderAssignmentPermissions permissions,
            ApplicationEventPublisher eventPublisher,
            com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository projectDocumentRepository,
            TenderEvaluationDocumentService documentService,
            Clock clock) {
        this.evaluationRepository = evaluationRepository;
        this.tenderRepository = tenderRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.permissions = permissions;
        this.eventPublisher = eventPublisher;
        this.gapFilesSync = new TenderEvaluationGapFilesSync(projectDocumentRepository);
        this.documentService = documentService;
        this.clock = clock;
    }

    /**
     * CO-310 修复：CRM 商机关联回填评估表专用入口。
     * <p>绕过 {@code canFill} 实例级守卫（latest assignee 检查），因为调用方
     * {@link TenderCommandService#linkCrmOpportunity} 已通过
     * {@code assertCanUpdateTender} 守卫验证用户有权编辑该标讯。
     * <p>sales 角色作为投标项目负责人关联 CRM 商机是其核心职责，不应受
     * latest assignee 限制。此方法直接保存评估表数据并提交，一步完成回填。
     * <p>逻辑等价于 {@link TenderEvaluationSubmissionService#saveDraft} +
     * {@link TenderEvaluationSubmissionService#submit}，但跳过 canFill 检查。
     * <p>CO-325 修复：使用 {@link Propagation#REQUIRES_NEW} 在独立事务中执行。
     * 评估表校验失败时（如"计划入围供应商数量不能小于 1"），独立事务回滚，
     * 不影响主事务（CRM 商机关联）。否则 Spring 默认会将主事务标记 rollback-only，
     * 即使 {@code linkCrmOpportunity} catch 了异常，事务提交时仍会抛
     * {@link org.springframework.transaction.UnexpectedRollbackException}。
     *
     * @param tenderId 标讯 ID
     * @param req 评估表三段式数据
     * @param evaluatorId 操作人 ID（sales 等）
     * @return 保存后的评估表 DTO
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenderEvaluationDTO backfillFromCrmLink(Long tenderId,
                                                    TenderEvaluationSubmitRequest req,
                                                    Long evaluatorId) {
        Tender tender = requireTender(tenderId);
        accessGuard.assertCanAccessTender(tender);
        User evaluator = requireUser(evaluatorId);

        // CO-310: 不检查 canFill，因为 linkCrmOpportunity 已有 assertCanUpdateTender 守卫
        // CO-329 修复：CRM 数据通常不完整，不应在预填草稿（DRAFT）时抛出异常导致整个事务回滚，
        // 只需记录日志，严格校验留待用户点击“提交评估表”时进行。
        var validationResult = TenderEvaluationSubmissionValidator.validate(req);
        if (!validationResult.isValid()) {
            String aggregated = validationResult.errors().stream()
                    .map(FieldError::message)
                    .collect(Collectors.joining("; "));
            log.warn("CO-329: CRM backfill contains incomplete data (will be saved as DRAFT): {}", aggregated);
        }

        TenderEvaluation entity = evaluationRepository.findByTenderId(tenderId)
                .orElseGet(() -> mapper.newEntity(tenderId, evaluator));

        // 已提交状态下重新编辑（与 saveDraft 逻辑对齐）
        if (entity.getEvaluationStatus() == EvaluationStatus.SUBMITTED) {
            if (tender.getStatus() == Tender.Status.EVALUATED) {
                entity.setRequiresReview(true);
                entity.setEvaluationRound(entity.getEvaluationRound() + 1);
                log.info("CO-310: Re-editing submitted evaluation for tender {} via CRM link (round={})",
                        tenderId, entity.getEvaluationRound());
            } else {
                throw new IllegalStateException(
                        "evaluation already submitted; cannot backfill for tender " + tenderId);
            }
        }

        mapper.applyRequest(entity, req);
        // CO-310 两步流程：关联时只回填为 DRAFT（不提交、不记 submittedAt）；
        // 项目负责人填"是否投标"后手动 submit() 才置 SUBMITTED。
        entity.setEvaluationStatus(EvaluationStatus.DRAFT);
        entity.setSubmittedAt(null); // CO-310: DRAFT 不保留旧提交时间（二次关联场景清残留）
        if (entity.getEvaluatorId() == null || !entity.getEvaluatorId().equals(evaluator.getId())) {
            if (entity.getEvaluatorId() != null) {
                log.info("CO-310: Evaluation {} evaluator changed from {} to {} via CRM link",
                    entity.getId(), entity.getEvaluatorName(), evaluator.getUsername());
            }
            entity.setEvaluatorId(evaluator.getId());
            entity.setEvaluatorName(evaluator.getUsername());
        }

        applyCustomerInfosWithFlush(entity, req.evaluationCustomerInfos());
        TenderEvaluation saved = evaluationRepository.save(entity);

        log.info("CO-310: Backfilled evaluation for tender {} from CRM link by user {}",
                tenderId, evaluatorId);

        List<ProjectDocument> gapFiles = gapFilesSync.applyGapFiles(tenderId, req.evaluationBasic());
        boolean canDecide = permissions.canDecide(tenderId, evaluatorId);
        return mapper.toDTO(saved, tender, true, canDecide, gapFiles);
    }

    // ---------- helpers ----------

    /**
     * CO-266 修复：应用客户信息段，确保 DELETE SQL 先于 INSERT 落库。
     * 与 {@link TenderEvaluationSubmissionService#applyCustomerInfosWithFlush} 逻辑一致。
     */
    private void applyCustomerInfosWithFlush(TenderEvaluation entity,
                                             List<com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO> infos) {
        if (infos == null) {
            return;
        }
        List<TenderEvaluationCustomerInfo> newRows = mapper.buildCustomerInfoRows(entity, infos);
        if (entity.getCustomerInfos() == null) {
            entity.setCustomerInfos(new ArrayList<>());
        }
        if (!entity.getCustomerInfos().isEmpty()) {
            entity.getCustomerInfos().clear();
            evaluationRepository.saveAndFlush(entity);
        }
        entity.getCustomerInfos().addAll(newRows);
    }

    private Tender requireTender(Long tenderId) {
        return tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", String.valueOf(tenderId)));
    }

    private User requireUser(Long evaluatorId) {
        return userRepository.findById(evaluatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", String.valueOf(evaluatorId)));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }
}
