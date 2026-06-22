package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public TenderDTO updateByExternalId(String sourceSystem, String sourceId, TenderUpdateRequest request) {
        Tender tender = helper.resolveTender(sourceSystem, sourceId, request.getTenderId());
        String externalId = tender.getExternalId();

        applyUpdateFields(tender, request);

        String crmId = TenderIntegrationMapper.firstNonBlank(request.getCrmOpportunityId(), request.getCrmId());
        crmTenderLinkService.linkIfPresent(tender, crmId);
        applyCrmFallback(tender, crmId, request.getCrmOpportunityName());

        Tender saved = tenderRepository.save(tender);
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
            mapper.applyUpdate(existing, request);
            if (userId != null) {
                existing.setCreatorId(userId);
            }
            String crmId = TenderIntegrationMapper.firstNonBlank(request.getCrmOpportunityId(), request.getCrmId());
            crmTenderLinkService.linkIfPresent(existing, crmId);
            applyCrmFallback(existing, crmId, null);

            Tender saved = tenderRepository.save(existing);
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
        String crmId = TenderIntegrationMapper.firstNonBlank(request.getCrmOpportunityId(), request.getCrmId());
        crmTenderLinkService.linkIfPresent(tender, crmId);
        applyCrmFallback(tender, crmId, null);

        Tender saved = tenderRepository.save(tender);
        if (request.getEvaluation() != null) {
            var eval = request.getEvaluation();
            evaluationService.saveEvaluation(saved.getId(), eval.getEvaluationBasic(),
                    eval.getEvaluationCustomerInfos(), eval.getEvaluationRecommendation());
        }
        log.info("Created tender id={} externalId={}", saved.getId(), externalId);
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

    private void applyCrmFallback(Tender tender, String crmId, String crmOpportunityName) {
        if (crmId == null || crmId.isBlank()) {
            if (tender.getCrmOpportunityId() == null || tender.getCrmOpportunityId().isBlank()) {
                boolean linked = crmTenderLinkService.linkByChanceIdIfPresent(
                        tender, tender.getExternalId() != null ? tender.getExternalId().split(":")[0] : null,
                        tender.getExternalId() != null && tender.getExternalId().contains(":")
                                ? tender.getExternalId().split(":")[1] : null);
                if (linked) {
                    tender.setSourceType(Tender.SourceType.CRM_OPPORTUNITY);
                    tender.setSource(Tender.SourceType.CRM_OPPORTUNITY.getLabel());
                }
            }
            return;
        }

        tender.setEvaluationSource(Tender.EvaluationSource.CRM_PUSH);
        tender.setStatus(Tender.Status.EVALUATED);
        if (tender.getCrmOpportunityId() == null || tender.getCrmOpportunityId().isBlank()) {
            // CO-277 接收侧修复被 applyCrmFallback 绕过的根因修复：
            // applyCrmLinkAndAssignment 在 crmId 是纯数字 id 且反查失败/异常时保持 null（CO-277 修复），
            // 但本方法紧接着检查 null 并直接存入原始 crmId，导致数字 id 被落库，后续 webhook 回传
            // 用 id 当 code，CRM 按编号匹配失败（tender 319 案例：crmId=20942 被直接存入，
            // 回传 bidInfoSync code="20942" → CRM 返回 code:1）。
            // 修复：纯数字 crmId 不在此处存入，保持 null 让外层 linkByChanceIdIfPresent 兜底
            //（用 sourceId 反查 code）；非纯数字（code 格式）保持原逻辑直接存入。
            if (crmId == null || crmId.isBlank() || !crmId.trim().matches("\\d+")) {
                tender.setCrmOpportunityId(crmId);
            }
        }
        if (crmOpportunityName != null && !crmOpportunityName.isBlank()
                && (tender.getCrmOpportunityName() == null || tender.getCrmOpportunityName().isBlank())) {
            tender.setCrmOpportunityName(crmOpportunityName);
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
}
