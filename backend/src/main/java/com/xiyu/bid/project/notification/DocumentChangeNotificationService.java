package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.core.DocumentChangeTargetUrlResolver;
import com.xiyu.bid.notification.core.NotificationMessagePolicy;
import com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicy.ProjectRole;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 文档变更通知服务（蓝图 §消息中心-系统通知 序号 5）。
 * <p>触发时机：项目文档被上传/删除；{@link DocumentOperationType#MODIFY} 当前无产品层
 * 调用方（不存在文档修改 API），保留为占位，未来新增修改入口时直接接入。
 * 接收人：投标管理员 / 投标组长 / 主投标负责人 / 投标辅助人员，
 * 排除操作人自己。渠道：通知中心 + 企微镜像。频率：实时。</p>
 *
 * <p>设计决策：不走 {@link com.xiyu.bid.changetracking.listener.EntityChangedNotificationListener}
 * 的订阅扇出机制——订阅扇出要求用户主动订阅文档，与蓝图"团队成员实时接收"不符。
 * 直接调用 NotificationApplicationService 与 {@link ProjectNotificationService#notifyTaskAssigned}、
 * {@link ProjectNotificationService#notifyStageTransition} 等同模式。</p>
 *
 * <p><b>Spec 030 对齐</b>：派发前按 {@link NotificationRecipientResolver#filterByProjectAccess}
 * 过滤候选接收人，剔除对该项目无访问权的用户（避免被通知的人点击跳转后被 403 拦截）。
 * 过滤失败降级为原候选广播——优先保证通知送达而非精准。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentChangeNotificationService {

    private static final Long SYSTEM_USER_ID = 0L;

    private final NotificationApplicationService notificationService;
    private final ProjectRepository projectRepository;
    private final NotificationRecipientResolver recipientResolver;

    /**
     * 派发文档变更通知。
     *
     * @param projectId        项目 ID
     * @param documentId       文档 ID（sourceEntityId，用于通知深链）
     * @param documentName     文档名称（出现在正文）
     * @param documentCategory 文档分类（已归一化值，用于 targetUrl 按阶段分流；可为 null 兜底 drafting）
     * @param operatorName     操作人显示名（出现在正文，如"王工（1001）"）
     * @param operationType    操作类型（{@link DocumentOperationType#UPLOAD}/{@link DocumentOperationType#DELETE}；
     *                         {@link DocumentOperationType#MODIFY} 为占位，待未来文档修改 API 接入）
     * @param actorUserId      操作人用户 ID（用于审计 + 排除自己接收）
     */
    public void notifyDocumentChanged(Long projectId, Long documentId, String documentName,
                                      String documentCategory, String operatorName,
                                      DocumentOperationType operationType, Long actorUserId) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            Set<ProjectRole> roles = Set.of(
                    ProjectRole.BID_ADMIN,
                    ProjectRole.BID_TEAM_LEADER,
                    ProjectRole.BID_LEAD,
                    ProjectRole.BID_ASSISTANT);
            List<Long> candidateIds = recipientResolver.resolveProjectRecipients(projectId, roles, actorUserId);
            if (candidateIds.isEmpty()) return;

            // Spec 030：按项目可见性过滤（D 组复用）
            List<Long> recipientIds = recipientResolver.filterByProjectAccess(candidateIds, projectId);
            if (recipientIds.isEmpty()) {
                log.info("DocumentChange notification skipped - no accessible recipients for project {} document {}",
                        projectId, documentId);
                return;
            }

            String targetUrl = DocumentChangeTargetUrlResolver.resolveTargetUrl(projectId, documentCategory);
            NotificationMessagePolicy.NotificationMessage message =
                    NotificationMessagePolicy.forDocumentChanged(
                            project, documentId, documentName, operatorName, operationType.getLabel(), targetUrl);

            notificationService.createNotification(new CreateNotificationRequest(
                    message.type(),
                    message.sourceEntityType(),
                    message.sourceEntityId(),
                    message.title(),
                    message.body(),
                    message.payload(),
                    recipientIds
            ), actorUserId == null ? SYSTEM_USER_ID : actorUserId);
        } catch (RuntimeException e) {
            log.warn("notifyDocumentChanged failed for project={}, document={}, op={}: {}",
                    projectId, documentId, operationType, e.getMessage());
        }
    }
}
