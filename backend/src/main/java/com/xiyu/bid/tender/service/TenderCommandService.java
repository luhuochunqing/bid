package com.xiyu.bid.tender.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.crm.application.CrmTenderSubjectChecker;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.ProjectManagerIdResolver;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.core.TenderBasicInfoValidator;
import com.xiyu.bid.tender.core.TenderSubjectConsistencyPolicy;
import com.xiyu.bid.tender.dto.TenderCrmLinkRequest;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.repository.ProjectRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenderCommandService {

    private final TenderDeduplicationService tenderDeduplicationService;
    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final TenderMapper tenderMapper;
    private final TenderProjectAccessGuard accessGuard;
    private final TenderCommandAccessGuard commandAccessGuard;
    private final TenderAutoAssignmentService autoAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final TenderAssignmentNotifier assignmentNotifier;
    private final TenderAttachmentRepository attachmentRepository;
    private final TenderCrmOccupancyChecker crmOccupancyChecker;
    private final TenderEvaluationBackfillService evaluationBackfillService;
    private final ProjectManagerIdResolver projectManagerIdResolver;
    private final TenderAssignmentRecordRepository assignmentRecordRepository;
    private final TenderAuditService tenderAuditService;
    private final CrmTenderSubjectChecker crmTenderSubjectChecker;
    private final TenderCrmLinkPersistService crmLinkPersistService;
    /** 部门名反查：复用 ProjectManagerDepartmentEnricher（user.department_code → organization_departments.department_name）。 */
    private final ProjectManagerDepartmentEnricher departmentEnricher;

    public TenderDTO createTender(TenderDTO tenderDTO) {
        return createTender(tenderDTO, null);
    }

    @Auditable(action = "CREATE", entityType = "TENDER", description = "录入标讯")
    public TenderDTO createTender(TenderDTO tenderDTO, Long userId) {
        log.debug("Creating new tender: {}", tenderDTO.getTitle());

        var validation = TenderBasicInfoValidator.validateBasicInfo(tenderDTO);
        if (validation.hasErrors()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }

        validateAttachmentFileUrls(tenderDTO.getAttachments());

        resolveCreator(tenderDTO, userId);
        Tender tender = tenderMapper.toEntity(withCommandDefaults(tenderDTO, userId));
        var duplicates = tenderDeduplicationService.findDuplicates(tender);
        if (!duplicates.isEmpty()) {
            throw new TenderDuplicateException(duplicates);
        }
        Tender savedTender = tenderRepository.save(tender);
        log.info("Created tender with id: {}", savedTender.getId());
        saveAttachments(savedTender.getId(), tenderDTO.getAttachments());
        // 批量导入 / 人工录入：用操作人 username 换 CRM token，按招标主体反查项目经理
        String operatorUsername = resolveUsername(userId);
        tryAutoAssign(savedTender, userId, operatorUsername);

        // CO-332: 记录创建标讯操作日志
        String createUsername = operatorUsername != null ? operatorUsername : "system";
        String createUserId = userId != null ? String.valueOf(userId) : "system";
        tenderAuditService.logCreate(savedTender.getId(), createUsername, createUserId, null);

        return tenderMapper.toDTO(savedTender);
    }

    /**
     * 创建后自动分配（批量导入与人工录入共用）。
     *
     * @param tender           已保存标讯
     * @param userId           操作人 ID（用于写入 webhook 事件，避免 CRM 回调因无 operator 死信）
     * @param operatorUsername 操作人 username（批量导入异步线程无 SecurityContext，必须显式传入）
     */
    private boolean tryAutoAssign(Tender tender, Long userId, String operatorUsername) {
        try {
            AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender, operatorUsername);
            if (result.isMatched()) {
                applyAssignmentResult(tender, result);
                com.xiyu.bid.batch.core.TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), Tender.Status.TRACKING);
                tender.setStatus(Tender.Status.TRACKING);
                String operatorName = userId != null
                        ? userRepository.findById(userId).map(User::getFullName).orElse(null)
                        : null;
                eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                        tender.getId(), tender.getExternalId(),
                        Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING, tender.getTitle(),
                        null, userId, operatorName, null, null));
                tenderRepository.save(tender);
                log.info("Tender {} auto-assigned, status changed to TRACKING", tender.getId());
                // CO-332: 记录自动分配审计日志（oldManager=null，系统按采购方/部门规则自动匹配）
                tenderAuditService.logAssign(tender.getId(), null, tender.getProjectManagerName(),
                        "auto", "auto", null);
                assignmentNotifier.notifyAutoAssigned(tender);
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("Auto-assignment failed for tender {}, keeping PENDING_ASSIGNMENT: {}", tender.getId(), e.getMessage());
        }
        return false;
    }

    void applyAssignmentResult(Tender tender, AssignmentResult result) {
        tender.setProjectManagerName(result.projectManagerName());
        if (result.projectManagerName() != null) {
            Long resolvedId = projectManagerIdResolver.resolveByFullName(result.projectManagerName());
            if (resolvedId != null) {
                tender.setProjectManagerId(resolvedId);
            } else {
                log.warn("Auto-assignment: projectManagerName '{}' cannot be resolved to a user id, "
                        + "projectManagerId remains null for tender {}",
                        result.projectManagerName(), tender.getId());
            }
        }
        // 部门名：优先用 CRM/mapping 返回的，为空时从 projectManagerId 反查 user 部门
        // 这样新建标讯在分配时就写入 department，不依赖查询时兜底
        String departmentName = result.departmentName();
        if (StringUtils.isBlank(departmentName) && tender.getProjectManagerId() != null) {
            departmentName = departmentEnricher.resolveDepartmentNameByUserId(tender.getProjectManagerId());
        }
        tender.setDepartment(departmentName);
    }

    // CO-571 Phase C: 两参 updateStatus(Long, Status) 已删除，所有调用点必须传 operatorId，
    // 避免 webhook 事件 operatorId=null 导致空 username 死信。
    public TenderDTO updateStatus(Long id, Tender.Status targetStatus, Long userId) {
        log.debug("Updating tender status, id: {}, target: {}", id, targetStatus);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        accessGuard.assertCanAccessTender(tender);

        com.xiyu.bid.batch.core.TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), targetStatus);
        Tender.Status previousStatus = tender.getStatus();

        // CO-571: 传播 operatorId + operatorName 到 webhook 事件，避免 CRM 回调因 username=null 失败
        String operatorName = userId != null
                ? userRepository.findById(userId).map(User::getFullName).orElse(null)
                : null;
        tender.setStatus(targetStatus);
        eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                tender.getId(), tender.getExternalId(), previousStatus, targetStatus, tender.getTitle(),
                null, userId, operatorName, null, null));
        Tender updatedTender = tenderRepository.save(tender);
        log.info("Updated tender status, id: {}, status: {}", id, targetStatus);
        return tenderMapper.toDTO(updatedTender);
    }

    public TenderDTO updateTender(Long id, TenderDTO tenderDTO) {
        return updateTender(id, tenderDTO, null);
    }

    @Auditable(action = "UPDATE", entityType = "TENDER", description = "编辑标讯")
    public TenderDTO updateTender(Long id, TenderDTO tenderDTO, Long userId) {
        log.debug("Updating tender with id: {}", id);
        Tender existingTender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));

        commandAccessGuard.assertCanUpdateTender(existingTender, userId);
        validateAttachmentFileUrls(tenderDTO.getAttachments());

        tenderMapper.updateEntity(existingTender, tenderDTO);
        if (!hasText(existingTender.getPurchaserHash()) && hasText(existingTender.getPurchaserName())) {
            existingTender.setPurchaserHash(generatePurchaserHash(existingTender.getPurchaserName()));
        }
        if (existingTender.getBasicInfoSavedAt() == null) {
            existingTender.setBasicInfoSavedAt(LocalDateTime.now());
        }
        Tender updatedTender = tenderRepository.save(existingTender);
        log.info("Updated tender with id: {}", id);

        // 更新附件
        if (tenderDTO.getAttachments() != null) {
            saveAttachments(id, tenderDTO.getAttachments());
        }

        // CO-332: 记录编辑标讯操作日志
        String updateUsername = userId != null ? userRepository.findById(userId).map(User::getUsername).orElse("system") : "system";
        String updateUserId = userId != null ? String.valueOf(userId) : "system";
        tenderAuditService.logEdit(id, "标讯信息", null, "已更新", updateUsername, updateUserId, null);

        return tenderMapper.toDTO(updatedTender);
    }

    public void deleteTender(Long id) {
        deleteTender(id, null);
    }

    public TenderDTO linkCrmOpportunity(Long id, String crmOpportunityId, String crmOpportunityName, Long userId) {
        return linkCrmOpportunity(id, crmOpportunityId, crmOpportunityName, null, userId);
    }

    /**
     * CO-310 修复：关联 CRM 商机并可选回填评估表数据。
     * <p>当提供 {@code evaluationPayload} 时，调用 {@link TenderEvaluationSubmissionService#backfillFromCrmLink}
     * 一步完成评估表回填，绕过 canFill 守卫（sales 角色关联商机是其核心职责）。
     * <p>不提供时保持原行为（仅关联商机），向后兼容。
     * <p>CO-501：此重载不带二次校验所需的 chanceGroupName/chanceTenderSubject，会跳过本地一致性校验，
     * 仅老调用方使用；新调用方请走 {@link #linkCrmOpportunity(Long, TenderCrmLinkRequest, Long)}。
     */
    @Auditable(action = "LINK_CRM", entityType = "TENDER", description = "关联商机")
    public TenderDTO linkCrmOpportunity(Long id, String crmOpportunityId, String crmOpportunityName,
                                          com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest evaluationPayload,
                                          Long userId) {
        TenderCrmLinkRequest req = TenderCrmLinkRequest.builder()
                .crmOpportunityId(crmOpportunityId)
                .crmOpportunityName(crmOpportunityName)
                .evaluationPayload(evaluationPayload)
                .build();
        return linkCrmOpportunity(id, req, userId);
    }

    /**
     * CO-501 主入口：关联 CRM 商机，含两步校验。
     *
     * <p>事务边界拆分（防 CO-325 类长事务）：CRM HTTP 校验在事务外执行，
     * 通过后进入 {@link TenderCrmLinkPersistService#persistCrmLink}（独立 @Service 的方法级事务）。
     *
     * <p>两步校验：
     * <ol>
     *   <li>调 CRM {@code check-tender-subject}（远程，事务外）—— 通过则返回 purchaserId 落库</li>
     *   <li>{@link TenderSubjectConsistencyPolicy}（本地，事务外）—— 校验标讯招标主体与商机一致</li>
     * </ol>
     */
    @Auditable(action = "LINK_CRM", entityType = "TENDER", description = "关联商机")
    public TenderDTO linkCrmOpportunity(Long id, TenderCrmLinkRequest req, Long userId) {
        String crmOpportunityId = req.getCrmOpportunityId();
        String crmOpportunityName = req.getCrmOpportunityName();
        log.debug("Linking CRM opportunity to tender id: {}", id);

        // ① 事务外：查 tender + 权限/状态/占位校验
        Tender existingTender = tenderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(409, "标讯已被删除，无法关联CRM商机"));
        commandAccessGuard.assertCanUpdateTender(existingTender, userId);
        assertCrmLinkAllowed(existingTender.getStatus()); // CO-269
        crmOccupancyChecker.assertCrmOpportunityNotOccupied(id, crmOpportunityId); // CO-297

        // ② 事务外：第一步 CRM 远程校验（CO-501）
        String purchaserName = existingTender.getPurchaserName();
        if (purchaserName == null || purchaserName.isBlank()) {
            throw new BusinessException(400, "标讯缺少招标主体，无法关联商机");
        }
        String username = resolveUsername(userId);
        CrmTenderSubjectChecker.CheckResult crmResult = crmTenderSubjectChecker.check(
                purchaserName, crmOpportunityId, username);
        if (!crmResult.passed()) {
            // 业务校验失败：按 msg 区分的 CO-501 原文文案
            throw new BusinessException(409, crmResult.errorMessage());
        }

        // ③ 事务外：第二步本地一致性校验（CO-501）
        TenderSubjectConsistencyPolicy.Result consistencyResult = TenderSubjectConsistencyPolicy.check(
                purchaserName, req.getChanceGroupName(), req.getChanceTenderSubject());
        if (!consistencyResult.allowed()) {
            throw new BusinessException(409, consistencyResult.errorMessage());
        }

        // ④ 事务内：落库（调独立 @Service 让 Spring AOP 代理生效，防自调用事务陷阱）
        return crmLinkPersistService.persistCrmLink(id, existingTender, crmOpportunityId, crmOpportunityName,
                req.getEvaluationPayload(), userId, crmResult.purchaserId());
    }

    private String resolveUsername(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getUsername).orElse(null);
    }

    /**
     * CO-310 两步流程：关联 CRM 商机时写一条 DISPATCH 分配记录 —— 已迁移到
     * {@link TenderCrmLinkPersistService#assignOnCrmLink}（独立 @Service，AOP 代理生效）。
     */

    @Auditable(action = "DELETE", entityType = "TENDER", description = "删除标讯")
    public void deleteTender(Long id, Long userId) {
        log.debug("Deleting tender with id: {}", id);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));

        commandAccessGuard.assertCanDeleteTender(tender, userId);

        List<com.xiyu.bid.entity.Project> linkedProjects = projectRepository.findByTenderId(id);
        if (!linkedProjects.isEmpty()) {
            log.warn("Cannot delete tender {}: {} linked projects exist", id, linkedProjects.size());
            throw new BusinessException("该标讯已关联 " + linkedProjects.size() + " 个项目，无法删除。请先解除项目关联后再操作。");
        }

        tenderRepository.delete(tender);
        log.info("Deleted tender with id: {}", id);

        // CO-332: 记录删除标讯操作日志
        String deleteUsername = userId != null ? userRepository.findById(userId).map(User::getUsername).orElse("system") : "system";
        String deleteUserId = userId != null ? String.valueOf(userId) : "system";
        tenderAuditService.logDelete(id, deleteUsername, deleteUserId, null);
    }

    private void resolveCreator(TenderDTO dto, Long userId) {
        if (dto.getCreatorId() == null && userId != null) {
            dto.setCreatorId(userId);
            userRepository.findById(userId).ifPresent(u ->
                    dto.setCreatorName(u.getFullName()));
        }
    }

    private TenderDTO withCommandDefaults(TenderDTO tenderDTO, Long userId) {
        if (tenderDTO.getStatus() == null) tenderDTO.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        if (tenderDTO.getSourceType() == null) tenderDTO.setSourceType(Tender.SourceType.MANUAL_SINGLE);
        if (tenderDTO.getPublishDate() == null) tenderDTO.setPublishDate(LocalDate.now());
        if (!hasText(tenderDTO.getPurchaserHash()) && hasText(tenderDTO.getPurchaserName())) {
            tenderDTO.setPurchaserHash(generatePurchaserHash(tenderDTO.getPurchaserName()));
        }
        return tenderDTO;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void validateAttachmentFileUrls(List<com.xiyu.bid.tender.dto.TenderAttachmentDTO> dtos) {
        if (dtos == null) return;
        for (com.xiyu.bid.tender.dto.TenderAttachmentDTO dto : dtos) {
            if (dto == null) continue;
            if (hasText(dto.getFileName()) && !hasText(dto.getFileUrl())) {
                throw new BusinessException(400, "标讯附件未完成上传，请重新上传后再保存");
            }
        }
    }

    private String generatePurchaserHash(String purchaserName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(purchaserName.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void saveAttachments(Long tenderId, List<com.xiyu.bid.tender.dto.TenderAttachmentDTO> dtos) {
        if (dtos == null) return;
        // 删除旧附件
        attachmentRepository.deleteByTenderId(tenderId);
        // 保存新附件（上限 10 个）
        int count = 0;
        for (com.xiyu.bid.tender.dto.TenderAttachmentDTO dto : dtos) {
            if (count >= 10) break;
            if (dto.getFileName() == null && dto.getFileUrl() == null) continue;
            TenderAttachment att = TenderAttachment.builder()
                    .tenderId(tenderId)
                    .fileName(dto.getFileName() != null ? dto.getFileName() : "")
                    .fileType(dto.getFileType())
                    .fileUrl(dto.getFileUrl() != null ? dto.getFileUrl() : "")
                    .build();
            attachmentRepository.save(att);
            count++;
        }
    }

    private static void assertCrmLinkAllowed(Tender.Status status) {
        if (status == Tender.Status.BIDDING || status == Tender.Status.WON
                || status == Tender.Status.LOST || status == Tender.Status.ABANDONED) {
            throw new BusinessException(409, "标讯已进入「" + status.name() + "」状态，无法更换CRM商机");
        }
    }
}
