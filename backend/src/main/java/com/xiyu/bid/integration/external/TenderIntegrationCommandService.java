package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.util.InputSanitizer;
import com.xiyu.bid.webhook.domain.OperatorDisplayName;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部标讯集成写入服务。
 * 负责标讯推送、更新、附件保存。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderIntegrationCommandService {

    private final TenderRepository tenderRepository;
    private final TenderAttachmentRepository attachmentRepository;
    private final CrmTenderLinkService crmTenderLinkService;
    private final TenderIntegrationMapper mapper;
    private final TenderEvaluationIntegrationService evaluationService;
    private final TenderIntegrationResolver helper;
    private final TenderIntegrationCommandSupport support;
    private final ApplicationEventPublisher eventPublisher;
    private final com.xiyu.bid.tender.service.TenderAuditService tenderAuditService;
    private final UserRepository userRepository;

    /**
     * 幂等推送标讯。
     */
    @Transactional
    public TenderPushResponse pushTender(TenderPushRequest request, Long userId) {
        log.info("pushTender received: sourceSystem={}, sourceId={}, crmId={}, crmOpportunityId={}, crmOpportunityName={}, title={}, region={}, projectType={}, sourcePlatform={}, source={}, industry={}",
                request.getSourceSystem(), request.getSourceId(), request.getCrmId(),
                request.getCrmOpportunityId(), request.getCrmOpportunityName(), request.getTitle(),
                request.getRegion(), request.getProjectType(), request.getSourcePlatform(),
                request.getSource(), request.getIndustry());

        String externalId = TenderIntegrationMapper.buildExternalId(request.getSourceSystem(), request.getSourceId());
        return tenderRepository.findByExternalId(externalId)
                .map(existing -> handleExistingTender(existing, request, userId, externalId))
                .orElseGet(() -> {
                    rejectDuplicateBusinessTender(request);
                    return createNewTender(request, userId, externalId);
                });
    }

    /**
     * 按 externalId 或 tenderId 更新标讯字段。
     */
    @Transactional
    public TenderDTO updateByExternalId(String sourceSystem, String sourceId, TenderUpdateRequest request, Long userId) {
        Tender tender = helper.resolveTender(sourceSystem, sourceId, request.getTenderId());
        String externalId = tender.getExternalId();

        // CO-305: 记录更新前的状态，用于判断是否需要发布 Event
        Tender.Status previousStatus = tender.getStatus();
        applyUpdateFields(tender, request);

        String crmId = TenderIntegrationMapper.firstNonBlank(request.getCrmOpportunityId(), request.getCrmId());
        crmTenderLinkService.linkIfPresent(tender, crmId);
        support.applyCrmFallback(tender, crmId, request.getCrmOpportunityName());

        Tender saved = tenderRepository.save(tender);
        // CO-305: 更新后状态变为 EVALUATED 时发布 TenderStatusChangedEvent
        if (saved.getStatus() == Tender.Status.EVALUATED && previousStatus != Tender.Status.EVALUATED) {
            String operatorName = resolveOperatorName(userId);
            eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                    saved.getId(), saved.getExternalId(),
                    previousStatus, Tender.Status.EVALUATED, saved.getTitle(),
                    null, userId, operatorName, null, null));
        }
        log.info("Updated tender id={} externalId={} crmId={} crmOpportunityId={}",
                saved.getId(), externalId, crmId, saved.getCrmOpportunityId());

        if (request.getAttachments() != null) {
            saveAttachments(saved.getId(), request.getAttachments());
        }
        if (request.getEvaluation() != null) {
            var eval = request.getEvaluation();
            evaluationService.saveEvaluation(saved.getId(), eval.getEvaluationBasic(),
                    eval.getEvaluationCustomerInfos(), eval.getEvaluationRecommendation());
        }

        return buildResponseDTO(saved);
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────────

    private void rejectDuplicateBusinessTender(TenderPushRequest request) {
        if (request.getCustomerName() == null || request.getCustomerName().isBlank()
                || request.getRegistrationDeadline() == null || request.getRegistrationDeadline().isBlank()
                || request.getBidOpeningTime() == null || request.getBidOpeningTime().isBlank()) {
            return;
        }

        String purchaserName = InputSanitizer.sanitizeString(request.getCustomerName(), 500);
        LocalDateTime registrationDeadline = TenderIntegrationMapper.parseDateTime("registrationDeadline", request.getRegistrationDeadline());
        LocalDateTime bidOpeningTime = TenderIntegrationMapper.parseDateTime("bidOpeningTime", request.getBidOpeningTime());
        tenderRepository.findFirstByPurchaserNameAndRegistrationDeadlineAndBidOpeningTime(
                purchaserName, registrationDeadline, bidOpeningTime)
                .ifPresent(existing -> {
                    log.warn("Duplicate tender business key rejected: existingId={}, purchaserName={}, registrationDeadline={}, bidOpeningTime={}",
                            existing.getId(), purchaserName, registrationDeadline, bidOpeningTime);
                    throw new IllegalArgumentException("投标管理系统该标讯已存在");
                });
    }

    private TenderPushResponse handleExistingTender(Tender existing, TenderPushRequest request, Long userId, String externalId) {
        if (Boolean.TRUE.equals(request.getForceUpdate())) {
            // CO-305: 记录更新前的状态，用于判断是否需要发布 Event
            Tender.Status previousStatus = existing.getStatus();
            mapper.applyUpdate(existing, request);
            if (userId != null) {
                existing.setCreatorId(userId);
            }
            String crmId = TenderIntegrationMapper.firstNonBlank(request.getCrmOpportunityId(), request.getCrmId());
            crmTenderLinkService.linkIfPresent(existing, crmId);
            support.applyCrmFallback(existing, crmId, null);

            Tender saved = tenderRepository.save(existing);
            // CO-305: 强制更新后状态变为 EVALUATED 时发布 TenderStatusChangedEvent
            if (saved.getStatus() == Tender.Status.EVALUATED && previousStatus != Tender.Status.EVALUATED) {
                String operatorName = resolveOperatorName(userId);
                eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                        saved.getId(), saved.getExternalId(),
                        previousStatus, Tender.Status.EVALUATED, saved.getTitle(),
                        null, userId, operatorName, null, null));
            }
            if (request.getEvaluation() != null) {
                var eval = request.getEvaluation();
                evaluationService.saveEvaluation(saved.getId(), eval.getEvaluationBasic(),
                        eval.getEvaluationCustomerInfos(), eval.getEvaluationRecommendation());
            }
            log.info("Force-updated tender id={} externalId={}", saved.getId(), externalId);
            return TenderPushResponse.builder()
                    .tenderId(saved.getId())
                    .status("UPDATED")
                    .message("标讯已覆盖更新")
                    .build();
        }
        return TenderPushResponse.builder()
                .tenderId(existing.getId())
                .status("DUPLICATE")
                .message("投标管理系统该标讯已存在")
                .build();
    }

    private TenderPushResponse createNewTender(TenderPushRequest request, Long userId, String externalId) {
        Tender tender = mapper.toEntity(request);
        tender.setExternalId(externalId);
        if (userId != null) {
            tender.setCreatorId(userId);
        }
        // CO-305: 记录创建时的初始状态，用于判断是否需要发布 Event
        Tender.Status initialStatus = tender.getStatus();
        String crmId = TenderIntegrationMapper.firstNonBlank(request.getCrmOpportunityId(), request.getCrmId());
        crmTenderLinkService.linkIfPresent(tender, crmId);
        support.applyCrmFallback(tender, crmId, null);

        Tender saved = tenderRepository.save(tender);
        // CO-305: CRM 推送创建的标讯状态变为 EVALUATED 时发布 TenderStatusChangedEvent
        if (saved.getStatus() == Tender.Status.EVALUATED && initialStatus != Tender.Status.EVALUATED) {
            String operatorName = resolveOperatorName(userId);
            eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                    saved.getId(), saved.getExternalId(),
                    initialStatus, Tender.Status.EVALUATED, saved.getTitle(),
                    null, userId, operatorName, null, null));
        }
        if (request.getEvaluation() != null) {
            var eval = request.getEvaluation();
            evaluationService.saveEvaluation(saved.getId(), eval.getEvaluationBasic(),
                    eval.getEvaluationCustomerInfos(), eval.getEvaluationRecommendation());
        }
        log.info("Created tender id={} externalId={}", saved.getId(), externalId);

        // CO-302: 第三方平台拉取标讯自动分配
        support.tryAutoAssign(saved);

        // CO-332: 记录接口创建标讯操作日志
        String createUsername = userId != null ? "integration-" + request.getSourceSystem() : "system";
        String createUserId = userId != null ? String.valueOf(userId) : "system";
        tenderAuditService.logCreate(saved.getId(), createUsername, createUserId, null);

        return TenderPushResponse.builder()
                .tenderId(saved.getId())
                .status("CREATED")
                .message("标讯创建成功")
                .build();
    }

    private void applyUpdateFields(Tender tender, TenderUpdateRequest request) {
        if (request.getTitle() != null) {
            tender.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 500));
        }
        if (request.getCustomerName() != null) {
            tender.setPurchaserName(InputSanitizer.sanitizeString(request.getCustomerName(), 500));
        }
        if (request.getPublishDate() != null) {
            tender.setPublishDate(request.getPublishDate());
        }
        if (request.getDueDate() != null) {
            tender.setDeadline(TenderIntegrationMapper.parseDateTime("dueDate", request.getDueDate()));
        }
        if (request.getBudgetAmount() != null) {
            tender.setBudget(request.getBudgetAmount());
        }
        mapper.applyBasicInfo(tender, request.getRegion(), request.getIndustry(), request.getTenderAgency(),
                request.getBidOpeningTime(), request.getRegistrationDeadline(), request.getCustomerType(),
                request.getPriority(), request.getProjectType(), request.getSourcePlatform(), request.getSource(), request.getTags());
        mapper.applyContactInfo(tender, request.getContactInfo());
        if (request.getContentDesc() != null) {
            tender.setDescription(InputSanitizer.sanitizeString(request.getContentDesc(), 5000));
        }
        if (request.getEvaluation() != null) {
            tender.setEvaluationSource(Tender.EvaluationSource.CRM_PUSH);
            tender.setStatus(Tender.Status.EVALUATED);
        }
    }

    private void saveAttachments(Long tenderId, List<TenderPushRequest.AttachmentRef> refs) {
        if (refs == null || refs.isEmpty()) return;
        attachmentRepository.deleteByTenderId(tenderId);
        int count = 0;
        for (TenderPushRequest.AttachmentRef ref : refs) {
            if (count >= 10) break;
            if (ref.getFileName() == null && ref.getFileUrl() == null) continue;
            TenderAttachment att = TenderAttachment.builder()
                    .tenderId(tenderId)
                    .fileName(ref.getFileName() != null ? ref.getFileName() : "")
                    .fileUrl(ref.getFileUrl() != null ? ref.getFileUrl() : "")
                    .build();
            attachmentRepository.save(att);
            count++;
        }
    }

    private TenderDTO buildResponseDTO(Tender saved) {
        List<TenderAttachment> attachments = attachmentRepository.findByTenderId(saved.getId());
        return mapper.toDTO(saved, attachments);
    }

    private String resolveOperatorName(Long userId) {
        if (userId == null) {
            return "";
        }
        return userRepository.findById(userId)
                .map(OperatorDisplayName::format)
                .orElse("");
    }
}
