package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.core.NotificationRecipientFilter;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文档变更通知服务（蓝图 §消息中心-系统通知 序号 5）。
 * <p>触发时机：项目文档被上传/删除（未来扩展修改）。
 * 接收人：项目团队成员（投标管理员/投标组长/投标负责人/投标辅助人员），
 * 排除操作人自己。渠道：通知中心 + 企微镜像。频率：实时。</p>
 *
 * <p>设计决策：不走 {@link com.xiyu.bid.changetracking.listener.EntityChangedNotificationListener}
 * 的订阅扇出机制——订阅扇出要求用户主动订阅文档，与蓝图"团队成员实时接收"不符。
 * 直接调用 NotificationApplicationService 与 {@link ProjectNotificationService#notifyTaskAssigned}、
 * {@link ProjectNotificationService#notifyStageTransition} 等同模式。</p>
 *
 * <p><b>Spec 030 对齐</b>：派发前按 {@link ProjectAccessScopeService#canAccessProject(Long, Long)}
 * 过滤候选接收人，剔除对该项目无访问权的用户（避免被通知的人点击跳转后被 403 拦截）。
 * 与 {@link TaskReviewNotificationService#notifyTaskReviewSubmitted} 同模式。过滤失败降级为
 * 原候选广播——优先保证通知送达而非精准。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentChangeNotificationService {

    private static final Long SYSTEM_USER_ID = 0L;

    private final NotificationApplicationService notificationService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * 派发文档变更通知。
     *
     * @param projectId     项目 ID
     * @param documentId    文档 ID（sourceEntityId，用于通知深链）
     * @param documentName  文档名称（出现在正文）
     * @param operatorName  操作人显示名（出现在正文，如"王工（1001）"）
     * @param operationType 操作类型："上传"/"修改"/"删除"
     * @param actorUserId   操作人用户 ID（用于审计 + 排除自己接收）
     */
    public void notifyDocumentChanged(Long projectId, Long documentId, String documentName,
                                      String operatorName, String operationType, Long actorUserId) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;
            String projectName = project.getName();

            // 候选接收人：项目团队成员，排除操作人自己
            List<Long> candidateIds = projectMemberRepository.findByProjectId(projectId).stream()
                    .map(ProjectMember::getUserId)
                    .filter(id -> !Objects.equals(id, actorUserId))
                    .collect(Collectors.toList());
            if (candidateIds.isEmpty()) return;

            // Spec 030：按项目可见性过滤（避免 403）
            List<Long> recipientIds = filterRecipientsSafe(candidateIds, projectId);
            if (recipientIds.isEmpty()) {
                log.info("DocumentChange notification skipped - no accessible recipients for project {} document {}",
                        projectId, documentId);
                return;
            }

            String body = String.format("项目名称：%s\n文档「%s」被 %s %s",
                    projectName, documentName, operatorName, operationType);
            Map<String, Object> payload = new HashMap<>();
            payload.put("projectId", String.valueOf(projectId));
            payload.put("projectName", projectName);
            payload.put("documentId", String.valueOf(documentId));
            payload.put("documentName", documentName);
            payload.put("operatorName", operatorName);
            payload.put("operationType", operationType);
            // payload targetUrl 用于企微外发精确跳转（P0-1：避免 /document/editor/{id} 错误跳转）
            payload.put("targetUrl", "/project/" + projectId + "/drafting");

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.DOCUMENT_CHANGE.name(),
                    "DOCUMENT",
                    documentId,
                    "文档变更 - " + projectName,
                    body,
                    payload,
                    recipientIds
            ), actorUserId == null ? SYSTEM_USER_ID : actorUserId);
        } catch (RuntimeException e) {
            log.warn("notifyDocumentChanged failed for project={}, document={}, op={}: {}",
                    projectId, documentId, operationType, e.getMessage());
        }
    }

    /**
     * Spec 030: 用 NotificationRecipientFilter 过滤候选接收人，按项目可见性剔除无访问权的用户。
     *
     * <p>降级策略：当 {@link ProjectAccessScopeService#canAccessProject(Long, Long)} 抛异常时
     * （DB 故障、OSS 同步异常等），返回原候选集合，保留原广播行为——优先保证通知送达，
     * 而非精准。与 {@link TaskReviewNotificationService#filterRecipientsSafe} 同模式。</p>
     */
    private List<Long> filterRecipientsSafe(List<Long> candidateIds, Long projectId) {
        try {
            return NotificationRecipientFilter.filterRecipients(
                    candidateIds,
                    uid -> projectAccessScopeService.canAccessProject(uid, projectId));
        } catch (RuntimeException e) {
            log.warn("Recipient filter failed for project {}, falling back to unfiltered broadcast: {}",
                    projectId, e.getMessage());
            return candidateIds;
        }
    }
}

