package com.xiyu.bid.tender.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Tender;
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

    public TenderDTO createTender(TenderDTO tenderDTO) {
        return createTender(tenderDTO, null);
    }

    public TenderDTO createTender(TenderDTO tenderDTO, Long userId) {
        log.debug("Creating new tender: {}", tenderDTO.getTitle());

        var validation = TenderBasicInfoValidator.validateBasicInfo(tenderDTO);
        if (validation.hasErrors()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }

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
                    List.of("BID_ADMIN", "BID_LEAD"));
            if (managers.isEmpty()) {
                log.warn("No bid_admin or bid_lead users found for tender {} pending assignment",
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
                assignmentNotifier.notifyAutoAssigned(tender);
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("Auto-assignment failed for tender {}, keeping PENDING_ASSIGNMENT: {}", tender.getId(), e.getMessage());
        }
        return false;
    }

    private void applyAssignmentResult(Tender tender, AssignmentResult result) {
        if (result.projectManagerId() != null) {
            try {
                tender.setProjectManagerId(Long.valueOf(result.projectManagerId()));
            } catch (NumberFormatException e) {
                log.warn("Cannot convert projectManagerId '{}' to Long for tender {}",
                        result.projectManagerId(), tender.getId());
            }
        }
        tender.setProjectManagerName(result.projectManagerName());
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

    public TenderDTO updateTender(Long id, TenderDTO tenderDTO, Long userId) {
        log.debug("Updating tender with id: {}", id);
        Tender existingTender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));

        commandAccessGuard.assertCanUpdateTender(existingTender, userId);

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

        return tenderMapper.toDTO(updatedTender);
    }

    public void deleteTender(Long id) {
        deleteTender(id, null);
    }

    public TenderDTO linkCrmOpportunity(Long id, String crmOpportunityId, String crmOpportunityName, Long userId) {
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
        // CO-305: 人工关联商机时统一走 TenderStatusChangedEvent 事件流
        if (existingTender.getStatus() == com.xiyu.bid.entity.Tender.Status.TRACKING) {
            Tender.Status previousStatus = existingTender.getStatus();
            existingTender.setStatus(com.xiyu.bid.entity.Tender.Status.EVALUATED);
            eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                    existingTender.getId(), existingTender.getExternalId(),
                    previousStatus, Tender.Status.EVALUATED, existingTender.getTitle()));
        }
        Tender updatedTender;
        try {
            updatedTender = tenderRepository.save(existingTender);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            crmOccupancyChecker.translateUniqueConstraintViolation(ex);
            throw new com.xiyu.bid.exception.BusinessException(409,
                    "CRM 商机已被其他标讯关联（并发冲突），请刷新后重试");
        }
        log.info("Linked CRM opportunity {} to tender id: {}", crmOpportunityId, id);
        return tenderMapper.toDTO(updatedTender);
    }

    public void deleteTender(Long id, Long userId) {
        log.debug("Deleting tender with id: {}", id);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));

        commandAccessGuard.assertCanDeleteTender(tender, userId);

        tenderRepository.delete(tender);
        log.info("Deleted tender with id: {}", id);
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
