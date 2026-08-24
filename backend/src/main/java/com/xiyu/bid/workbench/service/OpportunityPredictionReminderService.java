// Input: 全量项目 + 关联结果/标讯 + 接收人角色
// Output: 商机预测提醒（站内信 + 企微镜像 + 事件库）+ 每项目一次提醒日志
// Pos: Service/业务层 - workbench 商机预测提醒编排
// 维护声明:
//   - 纯规则（预测时机/是否提醒/模板文案）下沉到 workbench/domain；
//   - 本类只做编排：查数据、解析接收人、调通知、落日志、发事件；
//   - 定时任务无登录态，不使用带权限过滤的 ProjectQueryService.getAllProjects()，
//     直接用 repository 全量直查；
//   - 单项目异常不拖垮整个扫描（try-catch 隔离）。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import com.xiyu.bid.integration.tenderevent.infrastructure.sdk.OpportunityPredictionEventPublisher;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.workbench.domain.OpportunityPredictionAlertMessage;
import com.xiyu.bid.workbench.domain.OpportunityPredictionPolicy;
import com.xiyu.bid.workbench.domain.OpportunityPredictionReminderPolicy;
import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import com.xiyu.bid.workbench.repository.OpportunityPredictionReminderLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商机预测提醒编排：扫描全部项目 → 预测下次招标时间 → 到点时通知相关角色。
 *
 * <p>触发语义：{@link OpportunityPredictionPolicy#predict} 返回的"预测招标时间"
 * 即"该创建商机的时点"。每日扫描，凡今天已到该时点且从未提醒过的项目发通知；
 * 每项目只提醒一次（{@code opportunity_prediction_reminder_log} 唯一键 project_id）。
 *
 * <p>接收人：项目负责人（{@code Project.managerId}，即自动分配落库的 CRM 查得项目经理，
 * 启用状态过滤）+ 投标管理员 + 投标组长。
 *
 * <p>渠道：站内信（{@link NotificationApplicationService#createNotification}，
 * 企微经 NotificationCreatedEvent 自动镜像）+ 西域事件库（{@link OpportunityPredictionEventPublisher}）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpportunityPredictionReminderService {

    /** 通知接收人角色：投标管理员、投标组长。 */
    static final List<String> RECIPIENT_ROLE_CODES = List.of(
            RoleProfileCatalog.BID_ADMIN_CODE,
            RoleProfileCatalog.BID_LEAD_CODE);

    /** 商机中心前端路由（通知深链 targetUrl）。 */
    private static final String OPPORTUNITY_CENTER_PATH = "/bidding/customer-opportunities";

    private final ProjectRepository projectRepository;
    private final ProjectResultRepository projectResultRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final NotificationApplicationService notificationApplicationService;
    private final OpportunityPredictionReminderLogRepository reminderLogRepository;
    private final OpportunityPredictionEventPublisher eventPublisher;
    private final SystemActorResolver systemActorResolver;

    /**
     * 主入口：扫描并发送商机预测提醒。
     *
     * @param today 当前日期（提醒判定基准）
     * @return 扫描结果（已发送数 / 跳过数）
     */
    @Transactional
    public ScanOutcome runScan(final LocalDate today) {
        List<Project> projects = projectRepository.findAll();
        if (projects.isEmpty()) {
            return ScanOutcome.empty();
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, ProjectResult> resultByProject = projectResultRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ProjectResult::getProjectId, r -> r, (a, b) -> a));

        Set<Long> tenderIds = projects.stream().map(Project::getTenderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Tender> tenderById = tenderIds.isEmpty()
                ? Map.of()
                : tenderRepository.findAllById(tenderIds).stream()
                        .collect(Collectors.toMap(Tender::getId, t -> t, (a, b) -> a));

        List<Long> roleRecipients = recipientResolver.getUserIdsByRoleCodes(RECIPIENT_ROLE_CODES);
        Map<Long, User> enabledManagerById = loadEnabledManagers(projects);

        Long systemActor = systemActorResolver.resolveCached();
        if (systemActor == null) {
            log.warn("[商机预测提醒] system actor unresolved, aborting scan");
            return new ScanOutcome(projects.size(), 0, projects.size());
        }

        int notified = 0;
        int skipped = 0;
        for (Project project : projects) {
            try {
                if (processProject(project, resultByProject.get(project.getId()),
                        tenderById.get(project.getTenderId()), today, roleRecipients,
                        enabledManagerById, systemActor)) {
                    notified++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                log.error("[商机预测提醒] failed to dispatch for project id={}: {}",
                        project.getId(), ex.getMessage(), ex);
                skipped++;
            }
        }
        log.info("[商机预测提醒] scan done. scanned={} notified={} skipped={} roleRecipients={}",
                projects.size(), notified, skipped, roleRecipients.size());
        return new ScanOutcome(projects.size(), notified, skipped);
    }

    /** 处理单个项目：命中提醒窗口则发通知 + 落日志 + 发事件。 */
    private boolean processProject(Project project, ProjectResult result, Tender tender,
                                   LocalDate today, List<Long> roleRecipients,
                                   Map<Long, User> enabledManagerById, Long systemActor) {
        String resultType = resolveResultType(project, result);
        LocalDate bidOpenTime = tender != null && tender.getBidOpeningTime() != null
                ? tender.getBidOpeningTime().toLocalDate()
                : null;
        LocalDate servicePeriodEndDate = result != null ? result.getServicePeriodEndDate() : null;
        BigDecimal servicePeriodYears = result != null ? result.getServicePeriodYears() : null;
        var input = new OpportunityPredictionPolicy.Input(
                resultType, project.getCustomerType(), bidOpenTime, servicePeriodYears, servicePeriodEndDate);
        LocalDate predicted = OpportunityPredictionPolicy.predict(input).orElse(null);

        LocalDateTime lastNotifiedAt = reminderLogRepository.findByProjectId(project.getId())
                .map(OpportunityPredictionReminderLog::getNotifiedAt)
                .orElse(null);
        if (!OpportunityPredictionReminderPolicy.shouldNotify(predicted, today, lastNotifiedAt)) {
            return false;
        }

        String purchaserName = resolvePurchaserName(project, tender);
        List<Long> recipients = resolveRecipients(project, roleRecipients, enabledManagerById);
        if (recipients.isEmpty()) {
            log.warn("[商机预测提醒] no recipients for project id={}, skip", project.getId());
            return false;
        }

        OpportunityPredictionAlertMessage msg = OpportunityPredictionAlertMessage.from(
                purchaserName, servicePeriodEndDate, predicted);
        Map<String, Object> payload = buildPayload(project, purchaserName, servicePeriodEndDate, predicted);

        notificationApplicationService.createNotification(new CreateNotificationRequest(
                NotificationType.OPPORTUNITY_PREDICTION.name(),
                "Project",
                project.getId(),
                msg.title(),
                msg.body(),
                payload,
                recipients), systemActor);

        reminderLogRepository.save(OpportunityPredictionReminderLog.builder()
                .projectId(project.getId())
                .predictedBidDate(predicted)
                .purchaserName(purchaserName)
                .notifiedAt(LocalDateTime.now())
                .build());

        publishEvent(project, purchaserName, servicePeriodEndDate, predicted);
        return true;
    }

    /**
     * 结果类型：结果登记（project_result.resultType）优先，
     * 未登记结果的历史项目回退 Project.status。
     */
    private static String resolveResultType(Project project, ProjectResult result) {
        if (result != null && result.getResultType() != null && !result.getResultType().isBlank()) {
            return result.getResultType();
        }
        return project.getStatus() != null ? project.getStatus().name() : null;
    }

    /**
     * 招标主体名称：标讯采购人名称优先，缺失回退项目客户名称。
     */
    private static String resolvePurchaserName(Project project, Tender tender) {
        if (tender != null && StringUtils.hasText(tender.getPurchaserName())) {
            return tender.getPurchaserName().trim();
        }
        return StringUtils.hasText(project.getCustomer()) ? project.getCustomer() : null;
    }

    /** 接收人：项目经理（enabled 已过滤）+ 投标管理员 + 投标组长，去重保序。 */
    private List<Long> resolveRecipients(Project project, List<Long> roleRecipients,
                                         Map<Long, User> enabledManagerById) {
        Set<Long> recipients = new LinkedHashSet<>(roleRecipients);
        Long managerId = project.getManagerId();
        if (managerId != null && enabledManagerById.containsKey(managerId)) {
            recipients.add(managerId);
        }
        return new ArrayList<>(recipients);
    }

    /** 批量加载启用状态的项目负责人（一次 user 查询，避免 N+1）。 */
    private Map<Long, User> loadEnabledManagers(List<Project> projects) {
        Set<Long> managerIds = projects.stream().map(Project::getManagerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (managerIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIdIn(managerIds).stream()
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private static Map<String, Object> buildPayload(Project project, String purchaserName,
                                                    LocalDate servicePeriodEndDate, LocalDate predicted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", project.getId());
        payload.put("projectName", project.getName());
        payload.put("purchaserName", purchaserName);
        if (servicePeriodEndDate != null) {
            payload.put("servicePeriodEndDate", servicePeriodEndDate.toString());
        }
        payload.put("predictedBidDate", predicted.toString());
        payload.put("targetUrl", OPPORTUNITY_CENTER_PATH);
        return payload;
    }

    /** 事件库发布：失败只记日志，不拖垮通知主流程。 */
    private void publishEvent(Project project, String purchaserName,
                              LocalDate servicePeriodEndDate, LocalDate predicted) {
        try {
            eventPublisher.publish(new OpportunityPredictionEventPayload(
                    project.getId(), project.getName(), purchaserName, servicePeriodEndDate, predicted));
        } catch (RuntimeException ex) {
            log.warn("[商机预测提醒] event publish failed for project id={}: {}",
                    project.getId(), ex.getMessage());
        }
    }

    /** 扫描结果。 */
    public record ScanOutcome(int scanned, int notified, int skipped) {
        public static ScanOutcome empty() {
            return new ScanOutcome(0, 0, 0);
        }
    }
}
