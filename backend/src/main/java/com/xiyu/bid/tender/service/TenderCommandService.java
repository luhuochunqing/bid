package com.xiyu.bid.tender.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.integration.external.ProjectManagerIdResolver;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.tender.core.TenderBasicInfoValidator;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.repository.ProjectRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;

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
    private final TaskService taskService;
    private final TenderCommandAccessGuard commandAccessGuard;
    private final TenderAutoAssignmentService autoAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final NotificationApplicationService notificationAppService;
    private final TenderAssignmentNotifier assignmentNotifier;
    private final TenderAttachmentRepository attachmentRepository;
    private final TenderCrmOccupancyChecker crmOccupancyChecker;
    private final TenderEvaluationBackfillService evaluationBackfillService;
    private final ProjectManagerIdResolver projectManagerIdResolver;
    private final TenderAssignmentRecordRepository assignmentRecordRepository;
    private final TenderAuditService tenderAuditService;

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
        boolean assigned = tryAutoAssign(savedTender);
        if (!assigned) {
            String taskTitle = "【待分配】" + savedTender.getTitle();
            String taskDesc = "标讯「" + savedTender.getTitle()
                    + "」待分配负责人，请尽快处理。创建人："
                    + (savedTender.getCreatorName() != null
                        ? savedTender.getCreatorName() : "系统");
            List<User> managers = userRepository.findEnabledByRoleProfileCodes(
                    List.of(RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE));
            if (managers.isEmpty()) {
                log.warn("No bidAdmin or bid-TeamLeader users found for tender {} pending assignment",
                        savedTender.getId());
            }
            for (User manager : managers) {
                try {
                    TaskDTO task = taskService.createTask(
                            TaskDTO.builder()
                                    .projectId(savedTender.getId())
                                    .title(taskTitle)
                                    .description(taskDesc)
                                    .status(Task.Status.TODO)
                                    .priority(Task.Priority.HIGH)
                                    .assigneeId(manager.getId())
                                    .build());
                    log.info("Tender {} pending assignment, created task {} for {}",
                            savedTender.getId(), task.getId(), manager.getUsername());
                    try {
                        var result = notificationAppService.createNotification(
                                new CreateNotificationRequest(
                                        "APPROVAL", "TENDER",
                                        savedTender.getId(),
                                        taskTitle, taskDesc,
                                        null,
                                        List.of(manager.getId())),
                                userId != null ? userId : 1L);
                        if (!result.isValid()) {
                            log.warn("Notification validation failed for tender {} to user {}: {}",
                                    savedTender.getId(), manager.getUsername(), result.errorMessage());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to send notification for tender {} to user {}: {}",
                                savedTender.getId(), manager.getUsername(), e.getMessage());
                    }
                } catch (Exception e) {
                    log.warn("Failed to create task for tender {} for user {}: {}",
                            savedTender.getId(), manager.getUsername(), e.getMessage());
                }
            }
        }

        // CO-332: 记录创建标讯操作日志
        String createUsername = userId != null ? userRepository.findById(userId).map(User::getUsername).orElse("system") : "system";
        String createUserId = userId != null ? String.valueOf(userId) : "system";
        tenderAuditService.logCreate(savedTender.getId(), createUsername, createUserId, null);

        return tenderMapper.toDTO(savedTender);
    }

    private boolean tryAutoAssign(Tender tender) {
        try {
            AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);
            if (result.isMatched()) {
                applyAssignmentResult(tender, result);
                com.xiyu.bid.batch.core.TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), Tender.Status.TRACKING);
                tender.setStatus(Tender.Status.TRACKING);
                eventPublisher.publishEvent(TenderStatusChangedEvent.of(tender.getId(), tender.getExternalId(), Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING, tender.getTitle()));
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
        tender.setDepartment(result.departmentName());
    }

    public TenderDTO updateStatus(Long id, Tender.Status targetStatus) {
        log.debug("Updating tender status, id: {}, target: {}", id, targetStatus);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        accessGuard.assertCanAccessTender(tender);

        com.xiyu.bid.batch.core.TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), targetStatus);
        Tender.Status previousStatus = tender.getStatus();

        tender.setStatus(targetStatus);
        eventPublisher.publishEvent(TenderStatusChangedEvent.of(tender.getId(), tender.getExternalId(), previousStatus, targetStatus, tender.getTitle()));
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
     */
    @Auditable(action = "LINK_CRM", entityType = "TENDER", description = "关联商机")
    public TenderDTO linkCrmOpportunity(Long id, String crmOpportunityId, String crmOpportunityName,
                                          com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest evaluationPayload,
                                          Long userId) {
        log.debug("Linking CRM opportunity to tender id: {}", id);
        Tender existingTender = tenderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(409, "标讯已被删除，无法关联CRM商机"));
        commandAccessGuard.assertCanUpdateTender(existingTender, userId);
        // CO-269: 投标中/已中标/未中标/已放弃状态不允许更换CRM商机
        assertCrmLinkAllowed(existingTender.getStatus());
        crmOccupancyChecker.assertCrmOpportunityNotOccupied(id, crmOpportunityId); // CO-297: CRM 商机唯一性前置检查（应用层 + 数据库 UNIQUE 双层防御）
        existingTender.setCrmOpportunityId(crmOpportunityId);
        existingTender.setCrmOpportunityName(crmOpportunityName);
        existingTender.setEvaluationSource(com.xiyu.bid.entity.Tender.EvaluationSource.BID_SYSTEM_LINK);
        // CO-310 两步流程：关联商机不再立即切 EVALUATED/发事件——标讯保持 TRACKING，
        // 评估表以 DRAFT 回填；由项目负责人填"是否投标"并点"提交"后，submit() 才推进 EVALUATED + 发事件。
        Tender updatedTender;
        try {
            updatedTender = tenderRepository.save(existingTender);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            crmOccupancyChecker.translateUniqueConstraintViolation(ex);
            throw new com.xiyu.bid.exception.BusinessException(409,
                    "CRM 商机已被其他标讯关联（并发冲突），请刷新后重试");
        }
        // CO-310 两步流程：写 DISPATCH 分配记录，让关联人(sales)成为 latest assignee，
        // 从而通过后续 submit() 的 canFill 实例守卫（AssignmentPermissionRules.canFill）。
        assignOnCrmLink(id, userId);
        log.info("Linked CRM opportunity {} to tender id: {}", crmOpportunityId, id);

        // CO-310: 关联成功后回填评估表数据（如果提供）
        // 回填失败不阻塞 CRM 关联（关联是核心操作，回填是附加操作）
        // 评估表数据不完整时跳过回填，用户可后续手动补充
        if (evaluationPayload != null) {
            try {
                evaluationBackfillService.backfillFromCrmLink(id, evaluationPayload, userId);
                log.info("CO-310: Backfilled evaluation for tender {} from CRM link", id);
            } catch (BusinessException | IllegalStateException ex) {
                log.warn("CO-310: Skipped evaluation backfill for tender {} from CRM link (validation failed): {}",
                        id, ex.getMessage());
            }
        }

        // CO-332: 记录关联CRM商机操作日志（用商机名，避免显示 UUID/英文 ID）
        String linkUsername = userId != null ? userRepository.findById(userId).map(User::getUsername).orElse("system") : "system";
        String linkUserId = userId != null ? String.valueOf(userId) : "system";
        tenderAuditService.logLinkCrm(id, crmOpportunityName, linkUsername, linkUserId, null);

        return tenderMapper.toDTO(updatedTender);
    }

    /**
     * CO-310 两步流程：关联 CRM 商机时写一条 DISPATCH 分配记录，让关联人成为 latest assignee。
     * <p>照搬 {@link TenderTransferService#transfer} 的 record builder 模式。sales 关联商机即视为
     * 接手该标讯的评估，需通过后续 submit() 的 canFill 实例守卫。
     */
    private void assignOnCrmLink(Long tenderId, Long assigneeId) {
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", String.valueOf(assigneeId)));
        String assigneeName = assignee.getFullName() != null
                ? assignee.getFullName() : assignee.getUsername();
        TenderAssignmentRecord record = TenderAssignmentRecord.builder()
                .tenderId(tenderId)
                .assigneeId(assigneeId)
                .assigneeName(assigneeName)
                .assignedById(assigneeId)
                .assignedByName(assigneeName)
                .type(TenderAssignmentRecord.AssignmentType.DISPATCH)
                .remark("CRM商机关联，自动接手评估")
                .assignedAt(LocalDateTime.now())
                .build();
        assignmentRecordRepository.save(record);
        log.info("CO-310: Tender {} auto-assigned to {} (id={}) on CRM link",
                tenderId, assignee.getFullName(), assigneeId);
    }

    @Auditable(action = "DELETE", entityType = "TENDER", description = "删除标讯")
    public void deleteTender(Long id, Long userId) {
        log.debug("Deleting tender with id: {}", id);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));

        commandAccessGuard.assertCanDeleteTender(tender, userId);

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
