// Input: 历史提醒窗口 + 既有提醒/项目 + OSS 实时收件人快照
// Output: 零写入 preview 或逐项目短事务创建的独立历史补发通知
// Pos: workbench/service - 一次性商机预测历史补发编排
// 维护声明: 不改 reminder log、不发布商机事件；完成生产补发后可连同调度壳整体删除。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.workbench.domain.OpportunityPredictionAlertMessage;
import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import com.xiyu.bid.workbench.repository.OpportunityPredictionReminderLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 一次性历史补发；preview 与 execute 共用完全相同的候选和收件人计算。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpportunityPredictionHistoryReplayService {

    public static final String SOURCE_ENTITY_TYPE = "OpportunityPredictionHistoryReplay";

    private final OpportunityPredictionReminderLogRepository reminderLogRepository;
    private final ProjectRepository projectRepository;
    private final OpportunityPredictionRecipientResolver recipientResolver;
    private final OpportunityPredictionReminderTransactionService transactionService;
    private final SystemActorResolver systemActorResolver;

    public ReplayOutcome run(LocalDate runDate, LocalDate cutoff, ReplayMode mode) {
        validate(runDate, cutoff, mode);
        List<OpportunityPredictionReminderLog> reminders = reminderLogRepository
                .findByPredictedBidDateLessThanEqualAndNotifiedAtBeforeOrderByProjectIdAsc(
                        cutoff, runDate.atStartOfDay());
        if (reminders.isEmpty()) {
            return ReplayOutcome.empty();
        }

        List<Long> projectIds = reminders.stream()
                .map(OpportunityPredictionReminderLog::getProjectId)
                .distinct()
                .toList();
        Map<Long, Project> projects = indexProjects(projectRepository.findAllById(projectIds));
        Set<Long> replayedProjectIds = transactionService.findExistingNotificationSourceIds(
                SOURCE_ENTITY_TYPE, NotificationType.OPPORTUNITY_PREDICTION.name(), projectIds);
        OpportunityPredictionRecipientResolver.RecipientSnapshot recipients = recipientResolver.resolve();
        if (!recipients.lookupSucceeded()) {
            throw new IllegalStateException("OSS live recipient lookup failed; history replay aborted");
        }
        Long systemActor = resolveSystemActor(mode);

        MutableOutcome outcome = new MutableOutcome(reminders.size(), recipients.roleRecipientIds().size(),
                recipients.requestedJobNumbers(), recipients.returnedJobNumbers());
        for (OpportunityPredictionReminderLog reminder : reminders) {
            process(reminder, projects.get(reminder.getProjectId()), replayedProjectIds,
                    recipients, runDate, cutoff, mode, systemActor, outcome);
        }
        ReplayOutcome result = outcome.toValue();
        log.info("[商机预测历史补发] done mode={} candidates={} eligible={} created={} relations={} "
                        + "alreadyReplayed={} missingProjects={} noRecipients={} failed={} "
                        + "activeManagers={} roleRecipients={} ossRequested={} ossReturned={}",
                mode, result.candidates(), result.eligible(), result.created(),
                result.targetRecipientRelations(), result.alreadyReplayed(), result.missingProjects(),
                result.noRecipients(), result.failed(), result.projectsWithActiveManager(),
                result.roleRecipients(), result.ossRequested(), result.ossReturned());
        return result;
    }

    private void process(OpportunityPredictionReminderLog reminder, Project project,
                         Set<Long> replayedProjectIds,
                         OpportunityPredictionRecipientResolver.RecipientSnapshot snapshot,
                         LocalDate runDate, LocalDate cutoff, ReplayMode mode, Long systemActor,
                         MutableOutcome outcome) {
        Long projectId = reminder.getProjectId();
        if (replayedProjectIds.contains(projectId)) {
            outcome.alreadyReplayed++;
            return;
        }
        if (project == null) {
            outcome.missingProjects++;
            return;
        }
        if (snapshot.activeUserIds().contains(project.getManagerId())) {
            outcome.projectsWithActiveManager++;
        }
        List<Long> recipientIds = snapshot.recipientsFor(project.getManagerId());
        if (recipientIds.isEmpty()) {
            outcome.noRecipients++;
            return;
        }
        outcome.eligible++;
        if (mode == ReplayMode.PREVIEW) {
            outcome.targetRecipientRelations += recipientIds.size();
            return;
        }
        try {
            if (transactionService.createNotificationIfAbsent(
                    toRequest(reminder, project, recipientIds, runDate, cutoff), systemActor)) {
                outcome.created++;
                outcome.targetRecipientRelations += recipientIds.size();
            } else {
                outcome.alreadyReplayed++;
            }
        } catch (RuntimeException ex) {
            outcome.failed++;
            log.error("[商机预测历史补发] failed projectId={}: {}", projectId, ex.getMessage(), ex);
        }
    }

    private CreateNotificationRequest toRequest(OpportunityPredictionReminderLog reminder,
                                                Project project, List<Long> recipientIds,
                                                LocalDate runDate, LocalDate cutoff) {
        OpportunityPredictionAlertMessage message = OpportunityPredictionAlertMessage.historicalReplay(
                reminder.getPurchaserName(), project.getName(), reminder.getPredictedBidDate());
        return new CreateNotificationRequest(
                NotificationType.OPPORTUNITY_PREDICTION.name(), SOURCE_ENTITY_TYPE, project.getId(),
                message.title(), message.body(),
                OpportunityPredictionReminderSupport.buildHistoricalReplayPayload(
                        project, reminder.getPurchaserName(), reminder.getPredictedBidDate(), runDate, cutoff),
                recipientIds);
    }

    private Long resolveSystemActor(ReplayMode mode) {
        if (mode == ReplayMode.PREVIEW) {
            return null;
        }
        Long actor = systemActorResolver.resolveCached();
        if (actor == null) {
            throw new IllegalStateException("system actor unresolved; history replay aborted before writes");
        }
        return actor;
    }

    private static Map<Long, Project> indexProjects(Collection<Project> projects) {
        Map<Long, Project> indexed = new LinkedHashMap<>();
        for (Project project : projects) {
            if (project != null && project.getId() != null) {
                indexed.putIfAbsent(project.getId(), project);
            }
        }
        return indexed;
    }

    private static void validate(LocalDate runDate, LocalDate cutoff, ReplayMode mode) {
        if (runDate == null || cutoff == null || mode == null) {
            throw new IllegalArgumentException("runDate, cutoff and mode are required");
        }
        if (cutoff.isAfter(runDate)) {
            throw new IllegalArgumentException("cutoff must not be after runDate");
        }
    }

    public enum ReplayMode {
        PREVIEW,
        EXECUTE
    }

    public record ReplayOutcome(int candidates, int roleRecipients, int ossRequested, int ossReturned,
                                int projectsWithActiveManager, int eligible,
                                int targetRecipientRelations, int created, int alreadyReplayed,
                                int missingProjects, int noRecipients, int failed) {
        static ReplayOutcome empty() {
            return new ReplayOutcome(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static final class MutableOutcome {
        private final int candidates;
        private final int roleRecipients;
        private final int ossRequested;
        private final int ossReturned;
        private int projectsWithActiveManager;
        private int eligible;
        private int targetRecipientRelations;
        private int created;
        private int alreadyReplayed;
        private int missingProjects;
        private int noRecipients;
        private int failed;

        private MutableOutcome(int candidates, int roleRecipients, int ossRequested, int ossReturned) {
            this.candidates = candidates;
            this.roleRecipients = roleRecipients;
            this.ossRequested = ossRequested;
            this.ossReturned = ossReturned;
        }

        private ReplayOutcome toValue() {
            return new ReplayOutcome(candidates, roleRecipients, ossRequested, ossReturned,
                    projectsWithActiveManager, eligible, targetRecipientRelations, created,
                    alreadyReplayed, missingProjects, noRecipients, failed);
        }
    }
}
