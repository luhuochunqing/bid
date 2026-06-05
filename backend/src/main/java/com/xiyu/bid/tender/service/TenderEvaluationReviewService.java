// Input: TenderEvaluationRepository, TenderRepository, UserRepository, TenderAssignmentPermissions
// Output: TenderEvaluationDTO reviewEvaluation
// Pos: Service/业务编排层
// 维护声明: 仅维护标讯评估审核确认业务规则。reviewEvaluation 设置 requires_review=false。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.entity.TenderEvaluationRecommendation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标讯评估审核确认服务（V130）。
 * <p>当前职责：
 * <ul>
 *   <li>V130 审核确认（reviewEvaluation — 设置 requires_review=false）</li>
 * </ul>
 */
@Service
@Slf4j
@Transactional
public class TenderEvaluationReviewService {

    private final TenderEvaluationRepository tenderEvaluationRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final TenderAssignmentPermissions permissions;
    private final TenderEvaluationSubmissionMapper mapper = new TenderEvaluationSubmissionMapper();

    public TenderEvaluationReviewService(TenderEvaluationRepository tenderEvaluationRepository,
                                          TenderRepository tenderRepository,
                                          UserRepository userRepository,
                                          TenderAssignmentPermissions permissions) {
        this.tenderEvaluationRepository = tenderEvaluationRepository;
        this.tenderRepository = tenderRepository;
        this.userRepository = userRepository;
        this.permissions = permissions;
    }

    /**
     * 确认审核（reviewEvaluation）：将 requires_review 设回 false，更新审核人/时间。
     * <p>对应 FR-008 / AC5：投标管理员点击「确认审核」后，状态恢复为「已评估」，
     * 此时可执行「立即投标」或「放弃投标」。
     *
     * @param evaluationId 评估表 ID
     * @param reviewerId   审核人用户 ID
     * @return 更新后的评估 DTO
     * @throws ResourceNotFoundException 评估表或用户不存在
     * @throws IllegalArgumentException  评估表无需审核（requires_review 已是 false）
     */
    public TenderEvaluationDTO reviewEvaluation(Long evaluationId, Long reviewerId) {
        log.info("Reviewing evaluation {} by user {}", evaluationId, reviewerId);

        TenderEvaluation evaluation = tenderEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TenderEvaluation", evaluationId.toString()));

        if (!evaluation.isRequiresReview()) {
            throw new IllegalArgumentException(
                    "评估表 " + evaluationId + " 无需审核（requires_review=false）");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reviewerId.toString()));

        evaluation.setRequiresReview(false);
        evaluation.setLastReviewedBy(reviewerId);
        evaluation.setLastReviewedAt(LocalDateTime.now());

        // 如果 reviewStatus 仍为 PENDING，说明审核确认后尚未做投标/弃标决策
        if (evaluation.getReviewStatus() == TenderEvaluation.ReviewStatus.PENDING
                && evaluation.getEvaluationStatus() == TenderEvaluation.EvaluationStatus.SUBMITTED) {
            log.warn("Evaluation {} reviewed but reviewStatus is still PENDING (requiresReview=false)", evaluationId);
        }

        TenderEvaluation saved = tenderEvaluationRepository.save(evaluation);

        boolean canFill = permissions.canFill(saved.getTenderId(), reviewerId);
        boolean canDecide = permissions.canDecide(saved.getTenderId(), reviewerId);
        return toDTO(saved, canFill, canDecide);
    }

    private TenderEvaluationDTO toDTO(TenderEvaluation evaluation, boolean canFill, boolean canDecide) {
        Tender tender = tenderRepository.findById(evaluation.getTenderId()).orElse(null);
        return mapper.toDTO(evaluation, tender, canFill, canDecide);
    }
}
