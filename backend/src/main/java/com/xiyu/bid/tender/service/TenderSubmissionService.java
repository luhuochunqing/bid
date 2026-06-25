package com.xiyu.bid.tender.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderAbandonRequest;
import com.xiyu.bid.tender.dto.TenderBidResponse;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenderSubmissionService {

    private final TenderRepository tenderRepository;
    private final TenderEvaluationRepository tenderEvaluationRepository;
    private final UserRepository userRepository;
    private final TenderAssignmentPermissions permissions;
    private final TenderProjectAccessGuard accessGuard;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationApplicationService notificationAppService;

    @Transactional
    @Auditable(action = "PARTICIPATE", entityType = "TENDER", description = "投标标讯")
    public TenderBidResponse participateBid(Long tenderId, Long userId) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        accessGuard.assertCanAccessTender(tender);

        if (!permissions.canDecide(tenderId, userId)) {
            throw new AccessDeniedException(
                    "user " + userId + " is not the assigner of tender " + tenderId);
        }

        TenderBidResponse rejection = switch (tender.getStatus()) {
            case BIDDING -> rejectedBidResponse(false, "该标讯已投标");
            case ABANDONED -> rejectedBidResponse(false, "该标讯已放弃，无法投标");
            default -> null;
        };
        if (rejection != null) {
            return rejection;
        }

        Tender.Status oldBidStatus = tender.getStatus();
        tender.setStatus(Tender.Status.BIDDING);

        String operatorName = userRepository.findById(userId).map(User::getFullName).orElse("未知");
        Boolean recShouldBid = null;
        String recReason = null;
        var evalOpt = tenderEvaluationRepository.findByTenderId(tenderId);
        if (evalOpt.isPresent()) {
            var eval = evalOpt.get();
            recShouldBid = eval.getBidRecommendation() != null ? eval.getBidRecommendation() == TenderEvaluation.BidRecommendation.RECOMMEND : null;
            if (eval.getRecommendation() != null) {
                recReason = eval.getRecommendation().getReason();
            }
        }
        eventPublisher.publishEvent(
                com.xiyu.bid.webhook.domain.TenderStatusChangedEvent.of(
                        tender.getId(), tender.getExternalId(), oldBidStatus, Tender.Status.BIDDING, tender.getTitle(),
                        null, userId, operatorName, recShouldBid, recReason));
        tenderRepository.save(tender);

        // CO-349: 删除投标时自动创建的待立项任务，避免提交投标时因任务未完成而报错
        log.info("Tender {} participated by user {}", tenderId, userId);
        return TenderBidResponse.builder()
                .accepted(true)
                .message("投标成功")
                .projectId(tenderId)
                .build();
    }

    @Transactional
    @Auditable(action = "ABANDON", entityType = "TENDER", description = "弃标标讯")
    public TenderBidResponse abandonBid(Long tenderId, TenderAbandonRequest req, Long userId) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        accessGuard.assertCanAccessTender(tender);

        if (!permissions.canDecide(tenderId, userId)) {
            throw new AccessDeniedException(
                    "user " + userId + " is not the assigner of tender " + tenderId);
        }

        switch (tender.getStatus()) {
            case ABANDONED -> { return rejectedBidResponse(false, "该标讯已放弃"); }
            case BIDDING -> { return rejectedBidResponse(false, "该标讯已投标，无法弃标"); }
        }

        Tender.Status oldStatus = tender.getStatus();
        tender.setStatus(Tender.Status.ABANDONED);
        tender.setAbandonmentReason(req.getReason());

        String operatorName = userRepository.findById(userId).map(User::getFullName).orElse("未知");
        Boolean recShouldBid = null;
        String recReason = null;
        var evalOpt = tenderEvaluationRepository.findByTenderId(tenderId);
        if (evalOpt.isPresent()) {
            var eval = evalOpt.get();
            recShouldBid = eval.getBidRecommendation() != null ? eval.getBidRecommendation() == TenderEvaluation.BidRecommendation.RECOMMEND : null;
            if (eval.getRecommendation() != null) {
                recReason = eval.getRecommendation().getReason();
            }
        }
        eventPublisher.publishEvent(
                com.xiyu.bid.webhook.domain.TenderStatusChangedEvent.of(
                        tender.getId(), tender.getExternalId(), oldStatus, Tender.Status.ABANDONED, tender.getTitle(),
                        req.getReason(), userId, operatorName, recShouldBid, recReason));
        tenderRepository.save(tender);
        log.info("Tender {} abandoned by user {}, reason: {}", tenderId, userId, req.getReason());
        return rejectedBidResponse(true, "已放弃该标讯");
    }

    private TenderBidResponse rejectedBidResponse(boolean accepted, String message) {
        return TenderBidResponse.builder()
                .accepted(accepted)
                .message(message)
                .build();
    }
}
