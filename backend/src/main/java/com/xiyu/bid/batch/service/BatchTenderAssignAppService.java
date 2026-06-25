package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.dto.BatchTenderAssignRequest;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchTenderAssignAppService {

    private final TenderRepository tenderRepository;
    private final BatchProjectAccessGuard projectAccessGuard;
    private final BatchTenderAssignmentSupport assignmentSupport;
    private final BatchOperationLogService batchOperationLogService;
    private final NotificationApplicationService notificationAppService;
    private final com.xiyu.bid.tender.service.TenderAuditService tenderAuditService;

    @Transactional
    public BatchOperationResponse batchAssign(BatchTenderAssignRequest request, User currentUser) {
        validateRequest(request);

        User assignee = assignmentSupport.resolveAssignee(request.getAssigneeId());

        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("TENDER_ASSIGN")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(request.getTenderIds().size());

        List<Tender> changedTenders = new ArrayList<>();
        List<TenderAssignmentRecord> records = new ArrayList<>();
        for (Long tenderId : request.getTenderIds()) {
            tenderRepository.findById(tenderId).ifPresentOrElse(
                    tender -> collectAssignment(tender, assignee, request, currentUser, changedTenders, records, response),
                    () -> response.addError(tenderId, "Tender not found", "NOT_FOUND")
            );
        }

        if (!changedTenders.isEmpty()) {
            tenderRepository.saveAll(changedTenders);
        }
        assignmentSupport.saveRecords(records);
        response.setSuccess(response.getFailureCount() == 0);
        batchOperationLogService.record(response, "TENDER", "ASSIGN", currentUser == null ? null : currentUser.getId());

        // 分配成功后发送站内通知给被分配人（独立 try-catch，不影响事务提交）
        if (!changedTenders.isEmpty()) {
            try {
                String firstTitle = changedTenders.get(0).getTitle();
                int count = changedTenders.size();
                String title = "【标讯分配】" + firstTitle + (count > 1 ? " 等" + count + "条" : "");
                String desc = "您已被分配负责" + count + "条标讯，请尽快处理。分配人："
                        + (currentUser != null ? currentUser.getFullName() : "系统");
                var notificationResult = notificationAppService.createNotification(
                        new CreateNotificationRequest("APPROVAL", "TENDER",
                                changedTenders.get(0).getId(), title, desc, null,
                                List.of(assignee.getId())),
                        currentUser != null ? currentUser.getId() : 0L);
                if (!notificationResult.isValid()) {
                    log.warn("Assignment notification validation failed: {}", notificationResult.errorMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to send assignment notification: {}", e.getMessage());
            }
        }

        return response;
    }

    private void collectAssignment(
            Tender tender,
            User assignee,
            BatchTenderAssignRequest request,
            User currentUser,
            List<Tender> changedTenders,
            List<TenderAssignmentRecord> records,
            BatchOperationResponse response
    ) {
        try {
            projectAccessGuard.requireTender(tender.getId());
            TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), Tender.Status.TRACKING);
            String oldManagerName = tender.getProjectManagerName();
            tender.setProjectManagerId(assignee.getId());
            tender.setProjectManagerName(assignee.getFullName());
            tender.setDepartment(assignee.getDepartmentName());
            tender.setStatus(Tender.Status.TRACKING);
            changedTenders.add(tender);
            records.add(assignmentSupport.buildRecord(tender.getId(), assignee, request, currentUser));
            response.addSuccess(tender.getId());
            // CO-332: 记录标讯分配审计日志
            tenderAuditService.logAssign(tender.getId(), oldManagerName, assignee.getFullName(),
                    currentUser == null ? "system" : currentUser.getUsername(),
                    currentUser == null ? "system" : String.valueOf(currentUser.getId()), null);
        } catch (IllegalArgumentException exception) {
            response.addError(tender.getId(), exception.getMessage(), "INVALID_STATUS_TRANSITION");
        } catch (RuntimeException exception) {
            addRuntimeError(response, tender.getId(), exception);
        }
    }

    private void validateRequest(BatchTenderAssignRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Batch tender assign request cannot be null");
        }
        List<Long> tenderIds = request.getTenderIds();
        if (tenderIds == null || tenderIds.isEmpty()) {
            throw new IllegalArgumentException("Tender IDs cannot be null or empty");
        }
        if (tenderIds.size() > 100) {
            throw new IllegalArgumentException("Batch size cannot exceed 100 items");
        }
        if (request.getAssigneeId() == null || request.getAssigneeId() <= 0) {
            throw new IllegalArgumentException("Assignee ID must be a positive number");
        }
    }

    private void addRuntimeError(BatchOperationResponse response, Long itemId, RuntimeException exception) {
        String code = BatchProjectAccessGuard.isAccessDenied(exception) ? "PERMISSION_DENIED" : "ASSIGN_ERROR";
        response.addError(itemId, exception.getMessage(), code);
    }
}
