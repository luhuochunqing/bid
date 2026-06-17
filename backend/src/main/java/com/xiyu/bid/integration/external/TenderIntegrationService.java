package com.xiyu.bid.integration.external;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.ContactDTO;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.entity.TenderEvaluationRecommendation;
import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * 外部标讯同步核心服务（接口规范 v2.0）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderIntegrationService {
    private final TenderRepository tenderRepository;
    private final TenderMapper tenderMapper;
    private final TenderEvaluationRepository tenderEvaluationRepository;
    private final TenderEvaluationCustomerInfoRepository customerInfoRepository;
    private final TenderEvaluationSubmissionMapper submissionMapper;
    /**
     * 幂等推送标讯。
     * 按 (sourceSystem, sourceId) 组合 externalId 进行幂等判断：
     * - 已存在：返回 DUPLICATE
     * - 不存在：创建并返回 CREATED
     */
    @Transactional
    public TenderPushResponse pushTender(TenderPushRequest request, Long userId) {
        String externalId = buildExternalId(request.getSourceSystem(), request.getSourceId());
        return tenderRepository.findByExternalId(externalId)
                .map(existing -> {
                    if (Boolean.TRUE.equals(request.getForceUpdate())) {
                        applyUpdate(existing, request);
                        if (userId != null) {
                            existing.setCreatorId(userId);
                        }
                        Tender saved = tenderRepository.save(existing);
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
                            .message("标讯已存在")
                            .build();
                })
                .orElseGet(() -> {
                    Tender tender = mapToEntity(request);
                    tender.setExternalId(externalId);
                    if (userId != null) {
                        tender.setCreatorId(userId);
                    }
                    Tender saved = tenderRepository.save(tender);
                    log.info("Created tender id={} externalId={}", saved.getId(), externalId);
                    return TenderPushResponse.builder()
                            .tenderId(saved.getId())
                            .status("CREATED")
                            .message("标讯创建成功")
                            .build();
                });
    }
    private void applyUpdate(Tender tender, TenderPushRequest r) {
        if (r.getTitle() != null) tender.setTitle(InputSanitizer.sanitizeString(r.getTitle(), 500));
        if (r.getCustomerName() != null) tender.setPurchaserName(InputSanitizer.sanitizeString(r.getCustomerName(), 500));
        if (r.getPublishDate() != null) tender.setPublishDate(r.getPublishDate());
        if (r.getDueDate() != null) tender.setDeadline(parseDateTime(r.getDueDate()));
        if (r.getBudgetAmount() != null) tender.setBudget(r.getBudgetAmount());
        applyBasicInfo(tender, r.getRegion(), r.getIndustry(), r.getTenderAgency(),
                r.getBidOpeningTime(), r.getRegistrationDeadline(), r.getCustomerType(),
                r.getPriority(), r.getProjectType(), r.getSourcePlatform(), r.getSource(), r.getTags());
        applyContactInfo(tender, r.getContactInfo());
        if (r.getContentDesc() != null) tender.setDescription(InputSanitizer.sanitizeString(r.getContentDesc(), 5000));
        tender.setEvaluationSource(com.xiyu.bid.entity.Tender.EvaluationSource.CRM_PUSH);
    }
    /**
     * 按 externalId 或 tenderId 查询标讯详情（二选一必传）。
     */
    @Transactional(readOnly = true)
    public TenderDTO getByExternalId(String sourceSystem, String sourceId, Long tenderId) {
        Tender tender;
        if (tenderId != null) {
            tender = tenderRepository.findById(tenderId)
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: id=" + tenderId));
            if (sourceSystem != null && !sourceSystem.isBlank()
                    && sourceId != null && !sourceId.isBlank()) {
                String expectedExternalId = buildExternalId(sourceSystem, sourceId);
                if (tender.getExternalId() != null && !tender.getExternalId().equals(expectedExternalId)) {
                    throw new IllegalArgumentException(
                            "tenderId=" + tenderId + " 的 externalId="
                            + tender.getExternalId() + " 与路径 sourceSystem=" + sourceSystem
                            + " sourceId=" + sourceId + " 不匹配");
                }
            }
        } else if (sourceSystem != null && !sourceSystem.isBlank()
                && sourceId != null && !sourceId.isBlank()) {
            String externalId = buildExternalId(sourceSystem, sourceId);
            tender = tenderRepository.findByExternalId(externalId)
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: " + externalId));
        } else {
            throw new IllegalArgumentException("tenderId 与 (sourceSystem, sourceId) 至少需要传一组");
        }
        TenderDTO dto = tenderMapper.toDTO(tender);
        dto.setContactInfo(tenderMapper.buildContacts(tender));
        dto.setEvaluation(buildEvaluationDTO(tender.getId(), tender));
        normalizeSourceForIntegration(dto, tender);
        normalizeFileUrls(dto);
        return dto;
    }
    /**
     * 按 externalId 或 tenderId 更新标讯字段（二选一必传）。
     * tenderId 优先级高于 sourceSystem/sourceId；两者都传时会交叉校验。
     */
    @Transactional
    public TenderDTO updateByExternalId(String sourceSystem, String sourceId, TenderUpdateRequest request) {
        Tender tender;
        if (request.getTenderId() != null) {
            tender = tenderRepository.findById(request.getTenderId())
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: id=" + request.getTenderId()));
            // 若同时传了 sourceSystem/sourceId，做交叉校验
            if (sourceSystem != null && !sourceSystem.isBlank()
                    && sourceId != null && !sourceId.isBlank()) {
                String expectedExternalId = buildExternalId(sourceSystem, sourceId);
                if (tender.getExternalId() != null && !tender.getExternalId().equals(expectedExternalId)) {
                    throw new IllegalArgumentException(
                            "tenderId=" + request.getTenderId() + " 的 externalId="
                            + tender.getExternalId() + " 与路径 sourceSystem=" + sourceSystem
                            + " sourceId=" + sourceId + " 不匹配");
                }
            }
        } else if (sourceSystem != null && !sourceSystem.isBlank()
                && sourceId != null && !sourceId.isBlank()) {
            String externalId = buildExternalId(sourceSystem, sourceId);
            tender = tenderRepository.findByExternalId(externalId)
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: " + externalId));
        } else {
            throw new IllegalArgumentException("tenderId 与 (sourceSystem, sourceId) 至少需要传一组");
        }

        String externalId = tender.getExternalId();

        if (request.getTitle() != null) {
            tender.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 500));
        }
        if (request.getCustomerName() != null) {
            tender.setPurchaserName(InputSanitizer.sanitizeString(request.getCustomerName(), 500));
        }
        if (request.getPublishDate() != null) {
            tender.setPublishDate(request.getPublishDate());
        }
        if (request.getDueDate() != null) tender.setDeadline(parseDateTime(request.getDueDate()));
        if (request.getBudgetAmount() != null) {
            tender.setBudget(request.getBudgetAmount());
        }
        applyBasicInfo(tender, request.getRegion(), request.getIndustry(), request.getTenderAgency(),
                request.getBidOpeningTime(), request.getRegistrationDeadline(), request.getCustomerType(),
                request.getPriority(), request.getProjectType(), request.getSourcePlatform(), request.getSource(), request.getTags());
        applyContactInfo(tender, request.getContactInfo());
        if (request.getContentDesc() != null) {
            tender.setDescription(InputSanitizer.sanitizeString(request.getContentDesc(), 5000));
        }
        if (request.getEvaluation() != null) {
            tender.setEvaluationSource(com.xiyu.bid.entity.Tender.EvaluationSource.CRM_PUSH);
        }

        Tender saved = tenderRepository.save(tender);
        log.info("Updated tender id={} externalId={}", saved.getId(), externalId);
        // 处理评估数据
        if (request.getEvaluation() != null) {
            saveEvaluation(saved.getId(), request.getEvaluation());
        }
        TenderDTO dto = tenderMapper.toDTO(saved);
        dto.setContactInfo(tenderMapper.buildContacts(saved));
        dto.setEvaluation(buildEvaluationDTO(saved.getId(), saved));
        normalizeSourceForIntegration(dto, saved);
        return dto;
    }
    /** 将 source 字段映射为中文标签，与 sourceType 保持一致。 */
    private void normalizeSourceForIntegration(TenderDTO dto, Tender tender) {
        if (tender.getSourceType() == null) return;
        switch (tender.getSourceType()) {
            case MANUAL_SINGLE:
            case BULK_IMPORT:
                dto.setSource("人工录入");
                break;
            case CRM_OPPORTUNITY:
                dto.setSource("CRM创建");
                break;
            case EXTERNAL_PLATFORM:
                dto.setSource("第三方平台");
                break;
            default:
                break;
        }
    }
    private void normalizeFileUrls(TenderDTO dto) {
        if (dto.getSourceDocumentFileUrl() != null && dto.getSourceDocumentFileUrl().startsWith("doc-insight://")) dto.setSourceDocumentFileUrl(toDownloadUrl(dto.getSourceDocumentFileUrl()));
        if (dto.getBidNoticeFileUrl() != null && dto.getBidNoticeFileUrl().startsWith("doc-insight://")) dto.setBidNoticeFileUrl(toDownloadUrl(dto.getBidNoticeFileUrl()));
        if (dto.getAttachments() != null) dto.getAttachments().forEach(a -> { if (a.getFileUrl() != null && a.getFileUrl().startsWith("doc-insight://")) a.setFileUrl(toDownloadUrl(a.getFileUrl())); });
    }
    private static String toDownloadUrl(String u) { return "/api/doc-insight/download?fileUrl=" + java.net.URLEncoder.encode(u, java.nio.charset.StandardCharsets.UTF_8); }
    private String buildExternalId(String sourceSystem, String sourceId) {
        return sourceSystem + ":" + sourceId;
    }
    /**
     * 将请求中的联系人数组映射到实体扁平字段（最多取前 2 个）。
     */
    private void applyContactInfo(Tender tender, List<ContactDTO> contactInfo) {
        if (contactInfo == null || contactInfo.isEmpty()) {
            return;
        }
        // 清空旧值
        tender.setContactName(null);
        tender.setContactPhone(null);
        tender.setContactTel(null);
        tender.setContactMail(null);
        tender.setContactName2(null);
        tender.setContactPhone2(null);
        tender.setContactTel2(null);
        tender.setContactMail2(null);
        ContactDTO c1 = contactInfo.get(0);
        if (c1.getName() != null) tender.setContactName(InputSanitizer.sanitizeString(c1.getName(), 100));
        if (c1.getPhone() != null) tender.setContactPhone(InputSanitizer.sanitizeString(c1.getPhone(), 50));
        if (c1.getTel() != null) tender.setContactTel(InputSanitizer.sanitizeString(c1.getTel(), 50));
        if (c1.getMail() != null) tender.setContactMail(InputSanitizer.sanitizeString(c1.getMail(), 100));

        if (contactInfo.size() > 1) {
            ContactDTO c2 = contactInfo.get(1);
            if (c2.getName() != null) tender.setContactName2(InputSanitizer.sanitizeString(c2.getName(), 100));
            if (c2.getPhone() != null) tender.setContactPhone2(InputSanitizer.sanitizeString(c2.getPhone(), 50));
            if (c2.getTel() != null) tender.setContactTel2(InputSanitizer.sanitizeString(c2.getTel(), 50));
            if (c2.getMail() != null) tender.setContactMail2(InputSanitizer.sanitizeString(c2.getMail(), 100));
        }
    }
    /** 将请求中的基本信息字段映射到实体（非空才覆盖）。 */
    private void applyBasicInfo(Tender t, String region, String industry, String tenderAgency,
                                String bidOpeningTime, String registrationDeadline, String customerType,
                                String priority, String projectType, String sourcePlatform,
                                String source, List<String> tags) {
        if (region != null) t.setRegion(InputSanitizer.sanitizeString(region, 100));
        if (industry != null) t.setIndustry(InputSanitizer.sanitizeString(industry, 100));
        if (tenderAgency != null) t.setTenderAgency(InputSanitizer.sanitizeString(tenderAgency, 255));
        if (bidOpeningTime != null) t.setBidOpeningTime(parseDateTime(bidOpeningTime));
        if (registrationDeadline != null) t.setRegistrationDeadline(parseDateTime(registrationDeadline));
        if (customerType != null) t.setCustomerType(InputSanitizer.sanitizeString(customerType, 100));
        if (priority != null) t.setPriority(InputSanitizer.sanitizeString(priority, 10));
        if (projectType != null) t.setProjectType(InputSanitizer.sanitizeString(projectType, 20));
        if (sourcePlatform != null) t.setSourcePlatform(InputSanitizer.sanitizeString(sourcePlatform, 100));
        if (source != null) t.setSource(InputSanitizer.sanitizeString(source, 200));
        if (tags != null) t.setTags(tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(""));
    }
    /** 查询评估表并构建 DTO（customerInfos 展平为按角色聚合的格式）。 */
    private Object buildEvaluationDTO(Long tenderId, Tender tender) {
        return tenderEvaluationRepository.findByTenderId(tenderId)
                .map(e -> {
                    TenderEvaluationDTO dto = submissionMapper.toDTO(e, tender, false, false);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("evaluationStatus", dto.evaluationStatus() != null ? dto.evaluationStatus().name() : null);
                    result.put("bidRecommendation", dto.bidRecommendation() != null ? dto.bidRecommendation().name() : null);
                    result.put("submittedAt", dto.submittedAt());
                    result.put("evaluatorId", dto.evaluatorId());
                    result.put("evaluatorName", dto.evaluatorName());
                    result.put("evaluatedAt", dto.evaluatedAt());
                    result.put("reviewStatus", e.getReviewStatus() != null ? e.getReviewStatus().name() : null);
                    result.put("reviewerName", e.getReviewerName());
                    result.put("reviewedAt", e.getReviewedAt());
                    result.put("reviewComment", e.getReviewComment());
                    result.put("evaluationRound", dto.evaluationRound());
                    result.put("canFillEvaluation", dto.canFillEvaluation());
                    result.put("canDecideBid", dto.canDecideBid());
                    result.put("requiresReview", dto.requiresReview());
                    result.put("lastReviewedBy", dto.lastReviewedBy());
                    result.put("lastReviewedAt", dto.lastReviewedAt());
                    result.put("evaluationBasic", dto.evaluationBasic());
                    result.put("evaluationCustomerInfos", flattenCustomerInfos(dto.evaluationCustomerInfos()));
                    result.put("evaluationRecommendation", dto.evaluationRecommendation());
                    return result;
                })
                .orElse(null);
    }
    /** 将 EAV 格式的 customerInfos 展平为按角色聚合的数组（排除已删除的三列）。 */
    private List<Map<String, Object>> flattenCustomerInfos(List<EvaluationCustomerInfoDTO> eavRows) {
        if (eavRows == null || eavRows.isEmpty()) return null;
        Map<String, Map<String, Object>> byRole = new LinkedHashMap<>();
        for (EvaluationCustomerInfoDTO row : eavRows) {
            // 已删除的列：客户信息(角色名)、是否为重点攻克对象、是否有正式高层交流
            if ("ROLE_NAME".equals(row.infoKey())
                    || "KEY_TARGET".equals(row.infoKey())
                    || "HIGH_LEVEL_EXCHANGE".equals(row.infoKey())) {
                continue;
            }
            byRole.computeIfAbsent(row.roleKey(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("roleKey", k);
                return m;
            });
            byRole.get(row.roleKey()).put(row.infoKey(), row.value());
        }
        return new ArrayList<>(byRole.values());
    }
    /** 保存评估数据（新增或覆盖更新）。 */
    private void saveEvaluation(Long tenderId, TenderUpdateRequest.EvaluationUpdate eval) {
        TenderEvaluation evalEntity = tenderEvaluationRepository.findByTenderId(tenderId)
                .orElseGet(() -> {
                    TenderEvaluation ne = new TenderEvaluation();
                    ne.setTenderId(tenderId);
                    ne.setEvaluationStatus(TenderEvaluation.EvaluationStatus.DRAFT);
                    ne.setReviewStatus(TenderEvaluation.ReviewStatus.PENDING);
                    ne.setEvaluationRound(1);
                    return ne;
                });
        // 基础信息段
        if (eval.getEvaluationBasic() != null) {
            EvaluationBasicDTO b = eval.getEvaluationBasic();
            TenderEvaluationBasic basic = evalEntity.getBasic();
            if (basic == null) {
                basic = new TenderEvaluationBasic();
                basic.setEvaluation(evalEntity);
            }
            if (b.plannedShortlistedCount() != null) basic.setPlannedShortlistedCount(b.plannedShortlistedCount());
            if (b.mroOfficeFlowAmount() != null) basic.setMroOfficeFlowAmount(b.mroOfficeFlowAmount());
            if (b.unfavorableItems() != null) basic.setUnfavorableItems(b.unfavorableItems());
            if (b.riskAssessment() != null) basic.setRiskAssessment(b.riskAssessment());
            if (b.contingencyPlan() != null) basic.setContingencyPlan(b.contingencyPlan());
            if (b.processKnowledge() != null) basic.setProcessKnowledge(b.processKnowledge());
            if (b.supportNotes() != null) basic.setSupportNotes(b.supportNotes());
            if (b.projectPlanGap() != null) basic.setProjectPlanGap(b.projectPlanGap());
            if (b.customerRevenue() != null) basic.setCustomerRevenue(b.customerRevenue());
            evalEntity.setBasic(basic);
        }

        // 客户信息段（展平格式 → EAV）
        if (eval.getEvaluationCustomerInfos() != null) {
            // 删除旧数据
            if (evalEntity.getCustomerInfos() != null) {
                customerInfoRepository.deleteAll(evalEntity.getCustomerInfos());
            }
            List<TenderEvaluationCustomerInfo> newRows = new ArrayList<>();
            for (Map<String, Object> roleData : eval.getEvaluationCustomerInfos()) {
                String roleKey = (String) roleData.get("roleKey");
                if (roleKey == null) continue;
                for (Map.Entry<String, Object> entry : roleData.entrySet()) {
                    if ("roleKey".equals(entry.getKey()) || entry.getValue() == null) continue;
                    TenderEvaluationCustomerInfo row = new TenderEvaluationCustomerInfo();
                    row.setEvaluation(evalEntity);
                    row.setRoleKey(roleKey);
                    row.setInfoKey(entry.getKey());
                    row.setCellValue(entry.getValue().toString());
                    row.setValueType(TenderEvaluationCustomerInfo.ValueType.TEXT);
                    newRows.add(row);
                }
            }
            evalEntity.setCustomerInfos(newRows);
        }

        // 投标负责人建议段
        if (eval.getEvaluationRecommendation() != null) {
            EvaluationRecommendationDTO r = eval.getEvaluationRecommendation();
            TenderEvaluationRecommendation rec = evalEntity.getRecommendation();
            if (rec == null) {
                rec = new TenderEvaluationRecommendation();
                rec.setEvaluation(evalEntity);
                rec.setEvaluationId(evalEntity.getId());
            }
            if (r.shouldBid() != null) rec.setShouldBid(r.shouldBid());
            if (r.reason() != null) rec.setReason(r.reason());
            evalEntity.setRecommendation(rec);
        }

        tenderEvaluationRepository.save(evalEntity);
    }
    private Tender mapToEntity(TenderPushRequest r) {
        Tender t = new Tender();
        t.setTitle(InputSanitizer.sanitizeString(r.getTitle(), 500));
        if (r.getCustomerName() != null) {
            t.setPurchaserName(InputSanitizer.sanitizeString(r.getCustomerName(), 500));
        }
        if (r.getPublishDate() != null) {
            t.setPublishDate(r.getPublishDate());
        }
        if (r.getDueDate() != null) t.setDeadline(parseDateTime(r.getDueDate()));
        if (r.getBudgetAmount() != null) {
            t.setBudget(r.getBudgetAmount());
        }
        applyBasicInfo(t, r.getRegion(), r.getIndustry(), r.getTenderAgency(),
                r.getBidOpeningTime(), r.getRegistrationDeadline(), r.getCustomerType(),
                r.getPriority(), r.getProjectType(), r.getSourcePlatform(), r.getSource(), r.getTags());
        applyContactInfo(t, r.getContactInfo());
        if (r.getContentDesc() != null) {
            t.setDescription(InputSanitizer.sanitizeString(r.getContentDesc(), 5000));
        }
        if (r.getTenderInfo() != null) t.setTenderInfo(InputSanitizer.sanitizeString(r.getTenderInfo(), 5000));
        if (r.getProjectManagerName() != null) t.setProjectManagerName(InputSanitizer.sanitizeString(r.getProjectManagerName(), 100));
        if (r.getDepartment() != null) t.setDepartment(InputSanitizer.sanitizeString(r.getDepartment(), 100));
        if (r.getCreatorName() != null) t.setCreatorName(InputSanitizer.sanitizeString(r.getCreatorName(), 100));
        if (r.getCreateDate() != null) t.setCreatedAt(parseDateTime(r.getCreateDate()));
        t.setSourceType(com.xiyu.bid.entity.Tender.SourceType.EXTERNAL_PLATFORM);
        t.setSource(com.xiyu.bid.entity.Tender.SourceType.EXTERNAL_PLATFORM.getLabel());
        t.setStatus(com.xiyu.bid.entity.Tender.Status.PENDING_ASSIGNMENT);
        t.setEvaluationSource(com.xiyu.bid.entity.Tender.EvaluationSource.CRM_PUSH);
        return t;
    }
    /** 解析 datetime 字符串，兼容 yyyy-MM-dd HH:mm、yyyy-MM-ddTHH:mm、yyyy-MM-ddTHH:mm:ss */
    private static java.time.LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        // 空格 → T；补秒
        String normalized = value.replace(' ', 'T');
        if (normalized.length() == 16) normalized += ":00";
        return java.time.LocalDateTime.parse(normalized);
    }
}
