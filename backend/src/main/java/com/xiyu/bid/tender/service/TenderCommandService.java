package com.xiyu.bid.tender.service;

import com.xiyu.bid.ai.service.AiService;
import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.tender.dto.TenderAbandonRequest;
import com.xiyu.bid.tender.dto.TenderBidResponse;
import com.xiyu.bid.tender.core.TenderBasicInfoValidator;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.repository.ProjectRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenderCommandService {

    private final TenderDeduplicationService tenderDeduplicationService;
    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final AiService aiService;
    private final TenderMapper tenderMapper;
    private final TenderProjectAccessGuard accessGuard;
    private final com.xiyu.bid.batch.core.TenderStatusTransitionPolicy statusTransitionPolicy;
    private final TaskService taskService;
    private final TenderAssignmentPermissions permissions;
    private final TenderCommandAccessGuard commandAccessGuard;
    private final TenderAutoAssignmentService autoAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final NotificationApplicationService notificationAppService;

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

        // 自动分配：根据业主单位匹配 CRM 项目负责人
        // 匹配成功 → 状态变为 TRACKING；匹配失败 → 保持 PENDING_ASSIGNMENT
        boolean assigned = tryAutoAssign(savedTender);

        // 未匹配到 CRM 项目负责人时，为投标管理员和投标组长创建分配待办和通知
        if (!assigned) {
            String taskTitle = "【待分配】" + savedTender.getTitle();
            String taskDesc = "标讯「" + savedTender.getTitle()
                    + "」待分配负责人，请尽快处理。创建人："
                    + (savedTender.getCreatorName() != null
                        ? savedTender.getCreatorName() : "系统");
            List<User> managers = userRepository.findEnabledByRoleProfileCodes(
                    List.of(RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE));
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

    /**
     * 尝试自动分配标讯。
     * CRM 接口异常时记录日志，不影响标讯创建。
     */

    /**
     * 尝试自动分配标讯。
     *
     * @param tender 已保存的标讯
     * @return true 匹配成功（状态已更新为 TRACKING），false 保持 PENDING_ASSIGNMENT
     */
    private boolean tryAutoAssign(Tender tender) {
        try {
            if (autoAssignmentService.autoAssignIfPossible(tender)) {
                // 匹配成功，更新状态为 TRACKING
                statusTransitionPolicy.assertTransition(tender.getStatus(), Tender.Status.TRACKING);
                tender.setStatus(Tender.Status.TRACKING);
                eventPublisher.publishEvent(TenderStatusChangedEvent.of(tender.getId(), tender.getExternalId(), Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING, tender.getTitle()));
                tenderRepository.save(tender);
                log.info("Tender {} auto-assigned, status changed to TRACKING", tender.getId());
                return true;
            }
        } catch (RuntimeException e) {
            // CRM 接口异常不影响标讯创建，保持 PENDING_ASSIGNMENT
            log.warn("Auto-assignment failed for tender {}, keeping PENDING_ASSIGNMENT: {}",
                    tender.getId(), e.getMessage());
        }
        return false;
    }

    public TenderDTO updateStatus(Long id, Tender.Status targetStatus) {
        log.debug("Updating tender status, id: {}, target: {}", id, targetStatus);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        accessGuard.assertCanAccessTender(tender);

        statusTransitionPolicy.assertTransition(tender.getStatus(), targetStatus);
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
        return tenderMapper.toDTO(updatedTender);
    }

    public void deleteTender(Long id) {
        deleteTender(id, null);
    }

    public void deleteTender(Long id, Long userId) {
        log.debug("Deleting tender with id: {}", id);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));

        commandAccessGuard.assertCanDeleteTender(tender, userId);

        tenderRepository.delete(tender);
        log.info("Deleted tender with id: {}", id);
    }

    @Auditable(action = "AI_ANALYZE", entityType = "Tender", description = "AI分析标讯")
    public TenderDTO analyzeTender(Long id) {
        log.debug("Analyzing tender with id: {}", id);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        accessGuard.assertCanAccessTender(tender);
        CompletableFuture<Void> analysisFuture = aiService.analyzeTender(id, buildAiContext(tender));
        try {
            analysisFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("AI analysis wait interrupted for tender id: {}", id, e);
            throw new RuntimeException("AI analysis wait interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Error waiting for AI analysis completion for tender id: {}", id, e);
            throw new RuntimeException("Failed to complete AI analysis", e);
        }
        Tender analyzedTender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        log.info("Analyzed tender with id: {}, AI Score: {}", id, analyzedTender.getAiScore());
        return tenderMapper.toDTO(analyzedTender);
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

    private Map<String, Object> buildAiContext(Tender tender) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (tender.getBudget() != null) context.put("budget", tender.getBudget());
        if (tender.getDeadline() != null) context.put("deadline", tender.getDeadline());
        if (tender.getBidOpeningTime() != null) context.put("bidOpeningTime", tender.getBidOpeningTime());
        if (tender.getSource() != null) context.put("source", tender.getSource());
        if (tender.getRegion() != null) context.put("region", tender.getRegion());
        if (tender.getIndustry() != null) context.put("industry", tender.getIndustry());
        if (tender.getTenderAgency() != null) context.put("tenderAgency", tender.getTenderAgency());
        if (tender.getPurchaserName() != null) context.put("purchaserName", tender.getPurchaserName());
        if (tender.getCustomerType() != null) context.put("customerType", tender.getCustomerType());
        if (tender.getPriority() != null) context.put("priority", tender.getPriority());
        if (tender.getPublishDate() != null) context.put("publishDate", tender.getPublishDate());
        if (tender.getDescription() != null) context.put("description", tender.getDescription());
        if (tender.getTags() != null) context.put("tags", tender.getTags());
        return context;
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

    private static TenderBidResponse rejectedBidResponse(boolean accepted, String message) {
        return TenderBidResponse.builder().accepted(accepted).message(message).build();
    }

    @Transactional
    @Auditable(action = "BID", entityType = "Tender", description = "投标标讯")
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
        eventPublisher.publishEvent(TenderStatusChangedEvent.of(tender.getId(), tender.getExternalId(), oldBidStatus, Tender.Status.BIDDING, tender.getTitle()));
        tenderRepository.save(tender);

        TaskDTO createdTodo = taskService.createTask(
                TaskDTO.builder()
                        .projectId(tenderId)
                        .title("【待立项】" + tender.getTitle())
                        .description("标讯「" + tender.getTitle() + "」已投标，需进行项目立项。预算：" + tender.getBudget() + "万元。")
                        .status(Task.Status.TODO)
                        .priority(Task.Priority.HIGH)
                        .assigneeId(userId)
                        .dueDate(LocalDateTime.now().plusDays(7))
                        .build());
        log.info("Tender {} participated, created todo {} for user {}", tenderId, createdTodo.getId(), userId);
        return TenderBidResponse.builder()
                .accepted(true)
                .message("投标成功，已生成项目立项待办")
                .projectId(tenderId)
                .todoId(createdTodo.getId())
                .todoTitle(createdTodo.getTitle())
                .build();
    }

    @Transactional
    @Auditable(action = "ABANDON", entityType = "Tender", description = "弃标标讯")
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
            default -> { }
        }

        Tender.Status oldStatus = tender.getStatus();
        tender.setStatus(Tender.Status.ABANDONED);
        tender.setAbandonmentReason(req.getReason());
        eventPublisher.publishEvent(TenderStatusChangedEvent.of(tender.getId(), tender.getExternalId(), oldStatus, Tender.Status.ABANDONED, tender.getTitle(), req.getReason()));
        tenderRepository.save(tender);
        log.info("Tender {} abandoned by user {}, reason: {}", tenderId, userId, req.getReason());
        return rejectedBidResponse(true, "已放弃该标讯");
    }

    /**
     * 标讯转项目：从已投标标讯创建投标项目，携带标讯全部信息。
     * 幂等：已存在项目时直接返回已有项目。
     */
    @Transactional
    @Auditable(action = "PROCEED_TO_BID", entityType = "Tender", description = "标讯转项目立项")
    public TenderBidResponse proceedToBid(Long tenderId, Long userId) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        accessGuard.assertCanAccessTender(tender);

        if (!permissions.canDecide(tenderId, userId)) {
            throw new AccessDeniedException(
                    "user " + userId + " is not the assigner of tender " + tenderId);
        }

        if (tender.getStatus() != Tender.Status.BIDDING) {
            return rejectedBidResponse(false, "仅有已投标的标讯可转为项目");
        }

        // 幂等：已有关联项目时直接返回；若引用已删除的项目则清理后重建
        if (tender.getProjectId() != null) {
            java.util.Optional<Project> existing = projectRepository.findById(tender.getProjectId());
            if (existing.isPresent()) {
                return TenderBidResponse.builder()
                        .accepted(true).message("项目已存在")
                        .projectId(existing.get().getId()).build();
            }
            log.warn("Tender {} references deleted project {}, clearing and recreating",
                    tenderId, tender.getProjectId());
            tender.setProjectId(null);
        }

        // 从标讯字段构建项目实体
        Project project = Project.builder()
                .name(tender.getTitle())
                .tenderId(tender.getId())
                .status(Project.Status.PENDING_INITIATION)
                .managerId(tender.getProjectManagerId() != null ? tender.getProjectManagerId() : userId)
                .startDate(LocalDateTime.now())
                .endDate(tender.getDeadline() != null ? tender.getDeadline() : LocalDateTime.now().plusDays(90))
                .customer(tender.getPurchaserName())
                .budget(tender.getBudget())
                .region(tender.getRegion())
                .industry(tender.getIndustry())
                .customerType(tender.getCustomerType())
                .deadline(tender.getDeadline() != null ? tender.getDeadline().toLocalDate() : null)
                .description(tender.getDescription())
                .tagsJson(tender.getTags())
                .platform(tender.getSourcePlatform() != null ? tender.getSourcePlatform() : tender.getSource())
                .build();

        Project saved = projectRepository.save(project);
        tender.setProjectId(saved.getId());
        tenderRepository.save(tender);

        log.info("Tender {} converted to project {}, manager={}", tenderId, saved.getId(), saved.getManagerId());
        return TenderBidResponse.builder()
                .accepted(true).message("项目立项成功")
                .projectId(saved.getId()).build();
    }
}
