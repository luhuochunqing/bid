package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.util.InputSanitizer;
import com.xiyu.bid.webhook.application.OperatorUsernameResolver;
import com.xiyu.bid.webhook.domain.OperatorDisplayName;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.tender.service.TenderCrmOccupancyChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final TenderCrmOccupancyChecker crmOccupancyChecker;
    private final OperatorUsernameResolver operatorUsernameResolver;

    /**
     * 幂等推送标讯。
     */
    @Transactional
    public TenderPushResponse pushTender(TenderPushRequest request, Long userId) {
        log.info("pushTender received: sourceSystem={}, sourceId={}, crmId={}, crmOpportunityId={}, title={}",
                request.getSourceSystem(), request.getSourceId(), request.getCrmId(),
                request.getCrmOpportunityId(), request.getTitle());

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

        String usernameForLink = operatorUsernameResolver.resolve(userId);
        crmTenderLinkService.linkIfPresent(tender, request.getCrmId(), request.getCrmOpportunityId(), usernameForLink);
        support.applyCrmFallback(tender, request.getCrmId(), request.getCrmOpportunityId(), request.getCrmOpportunityName(), usernameForLink);

        // CO-297: 更新前校验 CRM 商机号是否已被其他标讯占用，避免直接触发数据库唯一索引 500
        crmOccupancyChecker.assertCrmOpportunityNotOccupied(tender.getId(), tender.getCrmOpportunityId());
        Tender saved;
        try {
            saved = tenderRepository.save(tender);
        } catch (DataIntegrityViolationException ex) {
            crmOccupancyChecker.translateUniqueConstraintViolation(ex);
            throw ex;
        }
        // CO-305: 更新后状态变为 EVALUATED 时发布 TenderStatusChangedEvent
        // API Key 认证路径下 userId 是 API Key 创建者（如 admin），webhook 需要标讯实际创建者的 OSS token
        publishEvaluatedEvent(saved, previousStatus, tender.getCreatorId() != null ? tender.getCreatorId() : userId);
        log.info("Updated tender id={} externalId={} crmOpportunityId={}",
                saved.getId(), externalId, saved.getCrmOpportunityId());

        if (request.getAttachments() != null) {
            saveAttachments(saved.getId(), request.getAttachments());
        }
        if (request.getEvaluation() != null) {
            var eval = request.getEvaluation();
            // CO-XXX: CRM 商机负责人优先，覆盖 CRM 推送的 XIYU_CONTACT 字段
            XiyuContactOverride.apply(eval.getEvaluationCustomerInfos(), saved.getProjectManagerName());
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
            // API Key 认证路径下 userId 是 API Key 创建者（如 admin），不是实际业务操作人。
            // 保留原始 creatorId，webhook 反查 CRM 需要实际创建者的 OSS token。
            Long originalCreatorId = existing.getCreatorId();
            // CO-XXX: 在 applyUpdate 之前解析 username，让 resolver 能读到原始 creatorId
            // 复用 OperatorUsernameResolver.resolveDeliveryUsername：creatorId → projectManagerId → eventOperatorId
            String usernameForLink = operatorUsernameResolver.resolveDeliveryUsername(existing, userId);
            mapper.applyUpdate(existing, request);
            if (userId != null) {
                existing.setCreatorId(userId);
            }
            crmTenderLinkService.linkIfPresent(existing, request.getCrmId(), request.getCrmOpportunityId(), usernameForLink);
            support.applyCrmFallback(existing, request.getCrmId(), request.getCrmOpportunityId(), null, usernameForLink);

            // CO-297: 强制更新前校验 CRM 商机号是否已被其他标讯占用，避免直接触发数据库唯一索引 500
            crmOccupancyChecker.assertCrmOpportunityNotOccupied(existing.getId(), existing.getCrmOpportunityId());
            Tender saved;
            try {
                saved = tenderRepository.save(existing);
            } catch (DataIntegrityViolationException ex) {
                crmOccupancyChecker.translateUniqueConstraintViolation(ex);
                throw ex;
            }
            // CO-305: 强制更新后状态变为 EVALUATED 时发布 TenderStatusChangedEvent
            // 优先用标讯原始创建者（有 OSS token），而非 API Key 创建者（admin）
            publishEvaluatedEvent(saved, previousStatus, originalCreatorId != null ? originalCreatorId : userId);
            if (request.getEvaluation() != null) {
                var eval = request.getEvaluation();
                // CO-XXX: CRM 商机负责人优先，覆盖 CRM 推送的 XIYU_CONTACT 字段
                XiyuContactOverride.apply(eval.getEvaluationCustomerInfos(), saved.getProjectManagerName());
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
        // CO-XXX: 传入 username，让 CRM 反查能拿到 token（API Key 路径下用 userId 反查）
        // 根因修复：之前传 null，导致 CrmAuthService.getValidTokenForUser(null) 抛异常，商机无法关联
        String usernameForLink = operatorUsernameResolver.resolve(userId);
        crmTenderLinkService.linkIfPresent(tender, request.getCrmId(), request.getCrmOpportunityId(), usernameForLink);
        support.applyCrmFallback(tender, request.getCrmId(), request.getCrmOpportunityId(), null, usernameForLink);

        // CO-297: 创建前校验 CRM 商机号是否已被其他标讯占用，避免直接触发数据库唯一索引 500
        crmOccupancyChecker.assertCrmOpportunityNotOccupied(null, tender.getCrmOpportunityId());
        Tender saved;
        try {
            saved = tenderRepository.save(tender);
        } catch (DataIntegrityViolationException ex) {
            crmOccupancyChecker.translateUniqueConstraintViolation(ex);
            throw ex;
        }
        // CO-305: CRM 推送创建的标讯状态变为 EVALUATED 时发布 TenderStatusChangedEvent
        publishEvaluatedEvent(saved, initialStatus, userId);
        if (request.getEvaluation() != null) {
            var eval = request.getEvaluation();
            // CO-XXX: CRM 商机负责人优先，覆盖 CRM 推送的 XIYU_CONTACT 字段
            XiyuContactOverride.apply(eval.getEvaluationCustomerInfos(), saved.getProjectManagerName());
            evaluationService.saveEvaluation(saved.getId(), eval.getEvaluationBasic(),
                    eval.getEvaluationCustomerInfos(), eval.getEvaluationRecommendation());
        }
        log.info("Created tender id={} externalId={}", saved.getId(), externalId);

        // CO-302: 第三方平台拉取标讯自动分配
        // CO-571: 传入 userId，让自动分配触发 TRACKING 时事件携带 operator，避免 CRM 回调死信
        support.tryAutoAssign(saved, userId);

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
        mapper.applyProjectManager(tender, request.getProjectManagerName());
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

    /** 状态变为 EVALUATED 时发布 TenderStatusChangedEvent */
    private void publishEvaluatedEvent(Tender saved, Tender.Status previousStatus, Long operatorId) {
        if (saved.getStatus() != Tender.Status.EVALUATED || previousStatus == Tender.Status.EVALUATED) {
            return;
        }
        String operatorName = operatorId == null ? "" : userRepository.findById(operatorId)
                .map(OperatorDisplayName::format).orElse("");
        eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                saved.getId(), saved.getExternalId(),
                previousStatus, Tender.Status.EVALUATED, saved.getTitle(),
                null, operatorId, operatorName, null, null));
    }
}
