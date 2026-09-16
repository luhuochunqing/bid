// Input: 全量项目 + 关联结果/标讯 + 接收人角色
// Output: 商机预测提醒（站内信 + 企微镜像 + 事件库）+ 每项目一次提醒日志
// Pos: Service/业务层 - workbench 商机预测提醒编排
// 维护声明: 纯规则位于 domain/support；本类无长事务，单项目异常隔离。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import com.xiyu.bid.integration.tenderevent.infrastructure.sdk.OpportunityPredictionEventPublisher;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.workbench.domain.OpportunityPredictionAlertMessage;
import com.xiyu.bid.workbench.domain.OpportunityPredictionPolicy;
import com.xiyu.bid.workbench.domain.OpportunityPredictionReminderPolicy;
import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 商机预测提醒编排：扫描全部项目 → 预测下次招标时间 → 到点时通知相关角色。
 *
 * <p>触发语义：{@link OpportunityPredictionPolicy#predict} 返回的"预测招标时间"
 * 即"该创建商机的时点"。每日扫描，凡今天已到该时点且从未提醒过的项目发通知；
 * 每项目只提醒一次（{@code opportunity_prediction_reminder_log} 唯一键 project_id）。
 *
 * <p>接收人：OSS 实时在职的项目负责人（{@code Project.managerId}）+
 * OSS 实时角色中的投标管理员和投标组长。
 *
 * <p>渠道：站内信及企微镜像 + 西域事件库（{@link OpportunityPredictionEventPublisher}）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpportunityPredictionReminderService {

    private static final Duration EVENT_CLAIM_TIMEOUT = Duration.ofMinutes(10);

    private final ProjectRepository projectRepository;
    private final ProjectResultRepository projectResultRepository;
    private final ProjectInitiationDetailsRepository projectInitiationDetailsRepository;
    private final TenderRepository tenderRepository;
    private final OpportunityPredictionRecipientResolver recipientResolver;
    private final OpportunityPredictionReminderTransactionService transactionService;
    private final OpportunityPredictionEventPublisher eventPublisher;
    private final SystemActorResolver systemActorResolver;

    /** 扫描并发送商机预测提醒，返回新提醒数、事件补发成功数和跳过数。 */
    public ScanOutcome runScan(final LocalDate today) {
        List<Project> projects = projectRepository.findAll();
        if (projects.isEmpty()) {
            return ScanOutcome.empty();
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, ProjectResult> resultByProject = projectResultRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ProjectResult::getProjectId, r -> r, (a, b) -> a));

        // 项目负责人和项目类型存于 project_initiation_details，批量取一次避免 N+1
        Map<Long, ProjectInitiationDetails> initiationByProject =
                projectInitiationDetailsRepository.findByProjectIdIn(projectIds).stream()
                        .filter(d -> d.getProjectId() != null)
                        .collect(Collectors.toMap(ProjectInitiationDetails::getProjectId,
                                d -> d, (a, b) -> a));

        Set<Long> tenderIds = projects.stream().map(Project::getTenderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Tender> tenderById = tenderIds.isEmpty()
                ? Map.of()
                : tenderRepository.findAllById(tenderIds).stream()
                        .collect(Collectors.toMap(Tender::getId, t -> t, (a, b) -> a));

        OpportunityPredictionRecipientResolver.RecipientSnapshot recipientSnapshot = recipientResolver.resolve();

        Long systemActor = systemActorResolver.resolveCached();
        if (systemActor == null) {
            log.warn("[商机预测提醒] system actor unresolved; new notifications will be skipped");
        }

        int notified = 0;
        int eventRetried = 0;
        int skipped = 0;
        for (Project project : projects) {
            try {
                ProcessOutcome outcome = processProject(project, resultByProject.get(project.getId()),
                        tenderById.get(project.getTenderId()),
                        initiationByProject.get(project.getId()), today, recipientSnapshot, systemActor);
                switch (outcome) {
                    case NOTIFIED -> notified++;
                    case EVENT_RETRIED -> eventRetried++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException ex) {
                log.error("[商机预测提醒] failed to dispatch for project id={}: {}",
                        project.getId(), ex.getMessage(), ex);
                skipped++;
            }
        }
        log.info("[商机预测提醒] scan done. scanned={} notified={} eventRetried={} skipped={} "
                        + "roleRecipients={} ossRequested={} ossReturned={}",
                projects.size(), notified, eventRetried, skipped,
                recipientSnapshot.roleRecipientIds().size(), recipientSnapshot.requestedJobNumbers(),
                recipientSnapshot.returnedJobNumbers());
        return new ScanOutcome(projects.size(), notified, eventRetried, skipped);
    }

    /** 处理单个项目：命中提醒窗口则发通知 + 落日志 + 发事件。 */
    private ProcessOutcome processProject(Project project, ProjectResult result, Tender tender,
                                          ProjectInitiationDetails initiationDetails, LocalDate today,
                                          OpportunityPredictionRecipientResolver.RecipientSnapshot recipientSnapshot,
                                          Long systemActor) {
        String resultType = OpportunityPredictionReminderSupport.resolveResultType(project, result);
        LocalDate bidOpenTime = tender != null && tender.getBidOpeningTime() != null
                ? tender.getBidOpeningTime().toLocalDate()
                : null;
        LocalDate servicePeriodEndDate = result != null ? result.getServicePeriodEndDate() : null;
        BigDecimal servicePeriodYears = result != null ? result.getServicePeriodYears() : null;
        String resolvedCustomerType = OpportunityPredictionReminderSupport.resolveCustomerType(project, tender);
        LocalDate predicted = OpportunityPredictionPolicy.predict(new OpportunityPredictionPolicy.Input(
                resultType, resolvedCustomerType, bidOpenTime, servicePeriodYears, servicePeriodEndDate))
                .orElse(null);

        String purchaserName = OpportunityPredictionReminderSupport.resolvePurchaserName(project, tender);
        String customer = OpportunityPredictionReminderSupport.resolveCustomer(project, tender);
        String resolvedLeaderName = OpportunityPredictionReminderSupport.resolveProjectLeaderName(
                initiationDetails != null ? initiationDetails.getProjectLeaderName() : null, tender);
        String resolvedProjectType = OpportunityPredictionReminderSupport.resolveProjectType(
                initiationDetails != null ? initiationDetails.getProjectType() : null, tender);

        var existingState = transactionService.findState(project.getId());
        if (existingState.isPresent()) {
            var state = existingState.get();
            if (state.eventPublishedAt() != null) {
                return ProcessOutcome.SKIPPED;
            }
            return retryClaimedEvent(project, OpportunityPredictionReminderSupport.buildEventPayload(
                    project, resultType, resolvedLeaderName, customer, resolvedCustomerType, resolvedProjectType,
                    bidOpenTime, servicePeriodYears, servicePeriodEndDate, state.predictedBidDate()));
        }
        if (!OpportunityPredictionReminderPolicy.shouldNotify(predicted, today, null)) {
            return ProcessOutcome.SKIPPED;
        }
        if (systemActor == null) {
            return ProcessOutcome.SKIPPED;
        }

        List<Long> recipients = recipientSnapshot.recipientsFor(project.getManagerId());
        if (recipients.isEmpty()) {
            log.warn("[商机预测提醒] no recipients for project id={}, skip", project.getId());
            return ProcessOutcome.SKIPPED;
        }

        OpportunityPredictionAlertMessage msg = OpportunityPredictionAlertMessage.from(
                purchaserName, servicePeriodEndDate, predicted);
        Map<String, Object> payload = OpportunityPredictionReminderSupport.buildPayload(
                project, purchaserName, servicePeriodEndDate, predicted);
        CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
                NotificationType.OPPORTUNITY_PREDICTION.name(),
                "Project",
                project.getId(),
                msg.title(),
                msg.body(),
                payload,
                recipients);

        String claimToken = newClaimToken();
        LocalDateTime claimedAt = LocalDateTime.now();
        OpportunityPredictionReminderLog reminderLog = OpportunityPredictionReminderLog.builder()
                .projectId(project.getId())
                .predictedBidDate(predicted)
                .purchaserName(purchaserName)
                .notifiedAt(claimedAt)
                .build();
        transactionService.createNotificationAndClaim(
                reminderLog, notificationRequest, systemActor, claimToken, claimedAt);

        OpportunityPredictionEventPayload eventPayload = OpportunityPredictionReminderSupport.buildEventPayload(
                project, resultType, resolvedLeaderName, customer, resolvedCustomerType, resolvedProjectType,
                bidOpenTime, servicePeriodYears, servicePeriodEndDate, predicted);
        publishClaimedEvent(project, eventPayload, claimToken);
        return ProcessOutcome.NOTIFIED;
    }
    /** 已有提醒只领取并补发事件；领取失败表示其他实例正在处理。 */
    private ProcessOutcome retryClaimedEvent(Project project, OpportunityPredictionEventPayload eventPayload) {
        String claimToken = newClaimToken();
        LocalDateTime claimedAt = LocalDateTime.now();
        if (!transactionService.claimPendingEvent(project.getId(), claimToken, claimedAt,
                claimedAt.minus(EVENT_CLAIM_TIMEOUT))) {
            return ProcessOutcome.SKIPPED;
        }
        if (publishClaimedEvent(project, eventPayload, claimToken)) {
            log.info("[商机预测提醒] event retry accepted for project id={}", project.getId());
            return ProcessOutcome.EVENT_RETRIED;
        }
        return ProcessOutcome.SKIPPED;
    }

    /** SDK 在事务外调用；令牌条件更新防止旧实例覆盖新领取者。 */
    private boolean publishClaimedEvent(Project project, OpportunityPredictionEventPayload eventPayload,
                                        String claimToken) {
        if (!publishEvent(eventPayload)) {
            transactionService.releaseEventClaim(project.getId(), claimToken);
            return false;
        }
        if (!transactionService.completeEventPublish(project.getId(), claimToken, LocalDateTime.now())) {
            throw new IllegalStateException("event accepted but publish state update lost for project "
                    + project.getId());
        }
        return true;
    }

    private static String newClaimToken() {
        return UUID.randomUUID().toString();
    }

    /** 事件库发布失败转换为值，由领取状态决定后续补偿。 */
    private boolean publishEvent(OpportunityPredictionEventPayload eventPayload) {
        try {
            return eventPublisher.publish(eventPayload);
        } catch (RuntimeException ex) {
            log.warn("[商机预测提醒] event publish failed for project id={}: {}",
                    eventPayload.projectId(), ex.getMessage());
            return false;
        }
    }

    private enum ProcessOutcome {
        NOTIFIED,
        EVENT_RETRIED,
        SKIPPED
    }

    /** 扫描结果。 */
    public record ScanOutcome(int scanned, int notified, int eventRetried, int skipped) {
        public static ScanOutcome empty() {
            return new ScanOutcome(0, 0, 0, 0);
        }
    }
}
