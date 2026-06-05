package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.core.ReminderStateTransition;
import com.xiyu.bid.bidresult.core.ReminderTypeResolver;
import com.xiyu.bid.bidresult.dto.BidResultReminderAssembler;
import com.xiyu.bid.bidresult.dto.BidResultReminderDTO;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.bidresult.repository.BidResultReminderRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BidResultReminderAppService {

    private static final int MAX_BATCH_SIZE = 200;

    private final BidResultReminderRepository reminderRepository;
    private final BidResultFetchResultRepository fetchResultRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final BidResultProjectAccessGuard accessGuard;

    @Transactional
    public BidResultReminder ensurePendingReminderForResult(BidResultFetchResult result, String comment, Long operatorId, String operatorName) {
        BidResultReminder reminder = findOrCreate(result, operatorId, operatorName);
        reminder.setStatus(ReminderStateTransition.ensurePending(reminder.getStatus()));
        reminder.setRemindTime(reminder.getRemindTime() == null ? LocalDateTime.now() : reminder.getRemindTime());
        reminder.setLastReminderComment(ReminderStateTransition.normalizeComment(comment, "待上传资料"));
        reminder.setLastResultId(result.getId());
        reminder.setAttachmentDocumentId(currentAttachmentId(result, reminder.getReminderType()));
        return reminderRepository.save(reminder);
    }

    @Transactional
    public BidResultReminderDTO sendReminder(Long resultId, String comment, Long operatorId, String operatorName) {
        BidResultFetchResult result = fetchResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Bid result fetch record not found: " + resultId));
        accessGuard.assertCanAccess(result.getProjectId());
        BidResultReminder reminder = findOrCreate(result, operatorId, operatorName);
        ReminderStateTransition.ReminderSnapshot sent =
                ReminderStateTransition.send(comment, LocalDateTime.now());
        applySnapshot(reminder, sent);
        reminder.setLastResultId(result.getId());
        reminder.setAttachmentDocumentId(currentAttachmentId(result, reminder.getReminderType()));
        return BidResultReminderAssembler.toDto(reminderRepository.save(reminder));
    }

    @Transactional
    public int sendReminders(List<Long> resultIds, String comment, Long operatorId, String operatorName) {
        List<Long> ids = Optional.ofNullable(resultIds).orElse(List.of());
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("批量数量不得超过 " + MAX_BATCH_SIZE);
        }
        int count = 0;
        for (Long resultId : ids) {
            sendReminder(resultId, comment, operatorId, operatorName);
            count++;
        }
        return count;
    }

    @Transactional
    public BidResultReminderDTO markUploaded(Long reminderId, Long documentId, Long operatorId) {
        BidResultReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Bid result reminder not found: " + reminderId));
        accessGuard.assertCanAccess(reminder.getProjectId());
        applySnapshot(reminder, ReminderStateTransition.upload(documentId, operatorId, LocalDateTime.now()));
        return BidResultReminderAssembler.toDto(reminderRepository.save(reminder));
    }

    @Transactional
    public void markUploadedForResult(BidResultFetchResult result, Long documentId, Long operatorId, String operatorName) {
        BidResultReminder reminder = findOrCreate(result, operatorId, operatorName);
        applySnapshot(reminder, ReminderStateTransition.upload(documentId, operatorId, LocalDateTime.now()));
        reminder.setLastResultId(result.getId());
        reminderRepository.save(reminder);
    }

    @Transactional
    public void revertAttachmentRemoved(BidResultFetchResult result, com.xiyu.bid.bidresult.core.BidResultAttachmentRef.AttachmentType attachmentType) {
        BidResultReminder.ReminderType reminderType = attachmentType == com.xiyu.bid.bidresult.core.BidResultAttachmentRef.AttachmentType.NOTICE
                ? BidResultReminder.ReminderType.NOTICE
                : BidResultReminder.ReminderType.REPORT;
        reminderRepository.findFirstByProjectIdAndReminderTypeOrderByRemindTimeDesc(result.getProjectId(), reminderType)
                .ifPresent(reminder -> {
                    applySnapshot(reminder, ReminderStateTransition.revertAfterAttachmentRemoved(
                            reminder.getStatus(),
                            reminder.getLastReminderComment(),
                            reminder.getRemindTime()
                    ));
                    reminder.setLastResultId(result.getId());
                    reminderRepository.save(reminder);
                });
    }

    private BidResultReminder findOrCreate(BidResultFetchResult result, Long operatorId, String operatorName) {
        BidResultReminder.ReminderType type = ReminderTypeResolver.resolve(result.getResult());
        return reminderRepository
                .findFirstByProjectIdAndReminderTypeOrderByRemindTimeDesc(result.getProjectId(), type)
                .orElseGet(() -> BidResultReminder.builder()
                        .projectId(result.getProjectId())
                        .projectName(result.getProjectName())
                        .ownerId(resolveOwnerId(result.getProjectId()))
                        .ownerName(resolveOwnerName(result.getProjectId()))
                        .reminderType(type)
                        .createdBy(operatorId)
                        .createdByName(operatorName)
                        .build());
    }

    private void applySnapshot(BidResultReminder reminder, ReminderStateTransition.ReminderSnapshot snapshot) {
        reminder.setStatus(snapshot.status());
        reminder.setRemindTime(snapshot.remindTime());
        reminder.setLastReminderComment(snapshot.comment());
        reminder.setAttachmentDocumentId(snapshot.attachmentDocumentId());
        reminder.setUploadedBy(snapshot.uploadedBy());
        reminder.setUploadedAt(snapshot.uploadedAt());
    }

    private Long currentAttachmentId(BidResultFetchResult result, BidResultReminder.ReminderType type) {
        return type == BidResultReminder.ReminderType.NOTICE
                ? result.getNoticeDocumentId()
                : result.getAnalysisDocumentId();
    }

    private Long resolveOwnerId(Long projectId) {
        return projectRepository.findById(projectId).map(project -> project.getManagerId()).orElse(null);
    }

    private String resolveOwnerName(Long projectId) {
        return projectRepository.findById(projectId)
                .map(project -> userRepository.findById(project.getManagerId()).map(User::getFullName).orElse("待分配"))
                .orElse("待分配");
    }
}
