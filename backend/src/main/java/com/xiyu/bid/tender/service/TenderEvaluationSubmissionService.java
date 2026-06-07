// Input: 评估表请求（TenderEvaluationSubmitRequest）、标讯 ID、评估人 ID
// Output: TenderEvaluationDTO（含状态机切换的最新视图）
// Pos: Service/业务编排层（命令式外壳）
// 维护声明: 仅做编排：取标讯/评估人 -> Guard -> 委托 Policy 校验 -> 状态切换 -> 持久化。
//          业务规则（必填/范围/客户信息矩阵）一律下沉至 com.xiyu.bid.tender.core.*Policy。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.core.FieldError;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluation.EvaluationStatus;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

/**
 * 标讯项目评估表草稿与提交服务（V130 三段式重构）。
 *
 * <p>三个操作：
 * <ul>
 *   <li>{@link #loadOrInitDraft(Long, Long)} — 已有记录返回原值；无记录返回空白 DRAFT（不持久化）</li>
 *   <li>{@link #saveDraft(Long, TenderEvaluationSubmitRequest, Long)} — upsert 草稿；SUBMITTED 时
 *       若标讯状态为 EVALUATED 则视为"重新编辑"（设 requires_review=true），否则拒绝</li>
 *   <li>{@link #submit(Long, TenderEvaluationSubmitRequest, Long)} — 三段全部走 Policy 校验后 DRAFT→SUBMITTED</li>
 * </ul>
 *
 * <p>V130 变更：
 * <ul>
 *   <li>负载新增三段式数据（basic/customerInfos/recommendation）</li>
 *   <li>提交时校验三段完整性（委托 {@link TenderEvaluationSubmissionValidator}）</li>
 *   <li>已评估状态下重新编辑保存时设 requires_review=true 并递增 evaluation_round</li>
 *   <li>DTO/Entity 映射委托 {@link TenderEvaluationSubmissionMapper}</li>
 *   <li>审核通知委托 {@link TenderEvaluationSubmissionNotifier}</li>
 * </ul>
 */
@Service
@Slf4j
@Transactional
public class TenderEvaluationSubmissionService {

    private final TenderEvaluationRepository evaluationRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final TenderProjectAccessGuard accessGuard;
    private final TenderAssignmentPermissions permissions;
    private final TenderEvaluationSubmissionNotifier submissionNotifier;
    private final TenderEvaluationSubmissionMapper mapper = new TenderEvaluationSubmissionMapper();
    private final Clock clock;

    public TenderEvaluationSubmissionService(
            TenderEvaluationRepository evaluationRepository,
            TenderRepository tenderRepository,
            UserRepository userRepository,
            TenderProjectAccessGuard accessGuard,
            TenderAssignmentPermissions permissions,
            TenderEvaluationSubmissionNotifier submissionNotifier,
            Clock clock) {
        this.evaluationRepository = evaluationRepository;
        this.tenderRepository = tenderRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.permissions = permissions;
        this.submissionNotifier = submissionNotifier;
        this.clock = clock;
    }

    /**
     * 加载已有评估表，或返回一个未持久化的空白 DRAFT 视图。
     * <p>V130: 返回数据包含三段式信息（basic / customerInfos / recommendation）。
     */
    @Transactional(readOnly = true)
    public TenderEvaluationDTO loadOrInitDraft(Long tenderId, Long evaluatorId) {
        Tender tender = requireTender(tenderId);
        accessGuard.assertCanAccessTender(tender);
        User evaluator = requireUser(evaluatorId);
        // 评估表字段全部来自 CRM 商机关联回填，任何状态下均不可编辑
        boolean canFill = permissions.canFill(tenderId, evaluatorId);
        boolean canDecide = permissions.canDecide(tenderId, evaluatorId);
        return evaluationRepository.findByTenderId(tenderId)
                .map(existing -> mapper.toDTO(existing, tender, canFill, canDecide))
                .orElseGet(() -> mapper.emptyDraftDTO(tender, evaluator, canFill, canDecide));
    }

    /**
     * 保存或更新草稿；不做业务必填校验。
     * <p>V130: 已提交状态下若标讯为 EVALUATED，允许重新编辑保存（设 requires_review=true）。
     *         三段式数据会同步保存。
     */
    public TenderEvaluationDTO saveDraft(Long tenderId,
                                         TenderEvaluationSubmitRequest req,
                                         Long evaluatorId) {
        Tender tender = requireTender(tenderId);
        accessGuard.assertCanAccessTender(tender);
        User evaluator = requireUser(evaluatorId);

        if (!permissions.canFill(tenderId, evaluatorId)) {
            throw new AccessDeniedException(
                    "user " + evaluatorId + " is not the assignee of tender " + tenderId);
        }

        TenderEvaluation entity = evaluationRepository.findByTenderId(tenderId)
                .orElseGet(() -> mapper.newEntity(tenderId, evaluator));

        // V130: 允许在 EVALUATED 状态下重新编辑已提交的评估表
        if (entity.getEvaluationStatus() == EvaluationStatus.SUBMITTED) {
            if (tender.getStatus() == Tender.Status.EVALUATED) {
                entity.setRequiresReview(true);
                entity.setEvaluationRound(entity.getEvaluationRound() + 1);
                log.info("Re-editing submitted evaluation for tender {} (evaluation_round={})",
                        tenderId, entity.getEvaluationRound());
            } else {
                throw new IllegalStateException(
                        "evaluation already submitted; cannot save as draft for tender " + tenderId);
            }
        }

        mapper.applyRequest(entity, req);
        entity.setEvaluationStatus(EvaluationStatus.DRAFT);

        TenderEvaluation saved = evaluationRepository.save(entity);
        boolean canDecide = permissions.canDecide(tenderId, evaluatorId);
        return mapper.toDTO(saved, tender, true, canDecide);
    }

    /**
     * 提交评估：三段数据全部经 Policy 校验后将 DRAFT 切换为 SUBMITTED 并打时间戳。
     *
     * <p>V130 新增校验：
     * <ul>
     *   <li>客户信息段 EAV → TenderEvaluationCustomerInfoPolicy.validate()</li>
     *   <li>投标负责人建议段 → shouldBid 必填；为 false 时 reason 必填</li>
     * </ul>
     *
     * <p>已 SUBMITTED → {@link IllegalStateException}（"already submitted"）。
     * <p>Validator 返回错误 → {@link IllegalArgumentException}（聚合所有错误消息）。
     */
    public TenderEvaluationDTO submit(Long tenderId,
                                      TenderEvaluationSubmitRequest req,
                                      Long evaluatorId) {
        Tender tender = requireTender(tenderId);
        accessGuard.assertCanAccessTender(tender);
        User evaluator = requireUser(evaluatorId);

        if (tender.getStatus() != Tender.Status.TRACKING && tender.getStatus() != Tender.Status.EVALUATED) {
            throw new IllegalStateException("标讯状态已变更，请刷新后重试");
        }

        if (!permissions.canFill(tenderId, evaluatorId)) {
            throw new AccessDeniedException(
                    "user " + evaluatorId + " is not the assignee of tender " + tenderId);
        }

        // --- 三段式完整性校验（委托 TenderEvaluationSubmissionValidator） ---
        var validationResult = TenderEvaluationSubmissionValidator.validate(req);
        if (!validationResult.isValid()) {
            String aggregated = validationResult.errors().stream()
                    .map(FieldError::message)
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Validation failed: " + aggregated);
        }

        // --- 保存 ---
        TenderEvaluation entity = evaluationRepository.findByTenderId(tenderId)
                .orElseGet(() -> mapper.newEntity(tenderId, evaluator));

        if (entity.getEvaluationStatus() == EvaluationStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "evaluation already submitted for tender " + tenderId);
        }

        mapper.applyRequest(entity, req);
        entity.setEvaluationStatus(EvaluationStatus.SUBMITTED);
        entity.setSubmittedAt(now());
        if (entity.getEvaluatorId() == null || !entity.getEvaluatorId().equals(evaluator.getId())) {
            if (entity.getEvaluatorId() != null) {
                log.info("Evaluation {} evaluator changed from {} to {} (round {})",
                    entity.getId(), entity.getEvaluatorName(), evaluator.getUsername(),
                    entity.getEvaluationRound());
            }
            entity.setEvaluatorId(evaluator.getId());
            entity.setEvaluatorName(evaluator.getUsername());
        }

        TenderEvaluation saved = evaluationRepository.save(entity);

        // H5: 推进 tender.status 到 EVALUATED（tender 是受管实体，脏检查自动刷新）
        if (tender.getStatus() == Tender.Status.TRACKING) {
            tender.setStatus(Tender.Status.EVALUATED);
        }

        // 发送审核通知
        submissionNotifier.notifyEvaluationSubmitted(tender);

        boolean canDecide = permissions.canDecide(tenderId, evaluatorId);
        return mapper.toDTO(saved, tender, true, canDecide);
    }

    // ---------- helpers ----------

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
