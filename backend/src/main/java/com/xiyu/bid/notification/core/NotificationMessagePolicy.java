// Input: 项目 / 任务 / 文档 / 阶段等核心领域对象
// Output: 蓝图"消息中心 §系统通知类" 6 条通知统一生成的 title/body/payload
// Pos: Pure Core/通知消息模板策略
package com.xiyu.bid.notification.core;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.ProjectStage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统通知类消息模板策略 —— 纯核心工厂。
 *
 * <p>无 Spring、无 Repository、无 IO、无副作用。所有方法静态、参数显式传入，
 * 可在单元测试中直接验证。负责把领域对象转换为通知消息需要的 title/body/payload，
 * 不处理规则计算、数据访问、DTO 转换或状态写入。
 *
 * <p>覆盖蓝图 6 条系统通知模板：
 * <ul>
 *   <li>项目结项归档</li>
 *   <li>任务状态变更</li>
 *   <li>任务分配</li>
 *   <li>@ 提及</li>
 *   <li>文档变更</li>
 *   <li>阶段自动推进</li>
 * </ul>
 */
public final class NotificationMessagePolicy {
    private static final String TYPE_SYSTEM = NotificationType.SYSTEM.name();
    private static final String TYPE_TASK_UPDATE = NotificationType.TASK_UPDATE.name();
    private static final String TYPE_MENTION = NotificationType.MENTION.name();
    private static final String TYPE_DOCUMENT_CHANGE = NotificationType.DOCUMENT_CHANGE.name();
    private static final String ENTITY_PROJECT = "PROJECT";
    private static final String ENTITY_TASK = "TASK";
    private static final String ENTITY_DOCUMENT = "DOCUMENT";

    private NotificationMessagePolicy() {
    }

    /**
     * 通知消息值对象。
     *
     * @param type             通知类型，对应 {@link NotificationType} 名称
     * @param sourceEntityType 来源实体类型，如 PROJECT / TASK / DOCUMENT
     * @param sourceEntityId   来源实体 ID
     * @param title            通知标题
     * @param body             通知正文
     * @param payload          扩展字段，至少包含 projectId / projectName / targetUrl
     */
    public record NotificationMessage(
            String type,
            String sourceEntityType,
            Long sourceEntityId,
            String title,
            String body,
            Map<String, Object> payload) {
    }

    /**
     * 项目结项归档。
     *
     * <p>文案：{@code 【{customer} - {projectName}】已结项归档，所有字段锁定，资料已自动归档}
     */
    public static NotificationMessage forProjectArchived(
            final Project project, final String customerName, final String targetUrl) {
        Long projectId = projectId(project);
        String projectName = projectName(project);
        String prefix = bracketProject(customerName, projectName);
        String title = "项目结项归档 - " + projectName;
        String body = prefix + "已结项归档，所有字段锁定，资料已自动归档";
        Map<String, Object> payload = basePayload(projectId, projectName, targetUrl);
        return new NotificationMessage(TYPE_SYSTEM, ENTITY_PROJECT, projectId, title, body, payload);
    }

    /**
     * 任务状态变更。
     *
     * <p>文案：{@code 【{projectName}】任务「{taskName}」状态发生变更：{fromStatus} → {toStatus}}
     */
    public static NotificationMessage forTaskStatusChanged(
            final Project project,
            final Task task,
            final String fromStatus,
            final String toStatus,
            final String targetUrl) {
        Long projectId = projectId(project);
        String projectName = projectName(project);
        Long taskId = taskId(task);
        String taskName = taskName(task);
        String title = "任务状态变更 - " + projectName + " - " + taskName;
        String body = "【" + projectName + "】任务「" + taskName + "」状态发生变更："
                + nullToEmpty(fromStatus) + " → " + nullToEmpty(toStatus);
        Map<String, Object> payload = taskPayload(projectId, projectName, taskId, taskName, targetUrl);
        return new NotificationMessage(TYPE_TASK_UPDATE, ENTITY_PROJECT, projectId, title, body, payload);
    }

    /**
     * 任务分配。
     *
     * <p>文案：{@code 【{projectName}】新任务「{taskName}」已指派给您，请尽快处理}
     */
    public static NotificationMessage forTaskAssigned(
            final Project project, final Task task, final String targetUrl) {
        Long projectId = projectId(project);
        String projectName = projectName(project);
        Long taskId = taskId(task);
        String taskName = taskName(task);
        String title = "任务分配 - " + projectName + " - " + taskName;
        String body = "【" + projectName + "】新任务「" + taskName + "」已指派给您，请尽快处理";
        Map<String, Object> payload = taskPayload(projectId, projectName, taskId, taskName, targetUrl);
        return new NotificationMessage(TYPE_TASK_UPDATE, ENTITY_PROJECT, projectId, title, body, payload);
    }

    /**
     * @ 提及。
     *
     * <p>文案：{@code 【{projectName}】{mentioner} 在「{scene}」中 @ 了您}
     */
    public static NotificationMessage forMention(
            final String projectName,
            final String mentionerName,
            final String scene,
            final String sourceEntityType,
            final Long sourceEntityId,
            final String targetUrl) {
        String safeProjectName = nullToEmpty(projectName);
        String title = "@ 提及 - " + safeProjectName;
        String body = "【" + safeProjectName + "】" + nullToEmpty(mentionerName)
                + " 在「" + nullToEmpty(scene) + "」中 @ 了您";
        Map<String, Object> payload = basePayload(null, safeProjectName, targetUrl);
        return new NotificationMessage(
                TYPE_MENTION, nullToEmpty(sourceEntityType), sourceEntityId, title, body, payload);
    }

    /**
     * 文档变更。
     *
     * <p>文案：{@code 【{projectName}】文档「{documentName}」被 {operatorName} {operationLabel}}
     */
    public static NotificationMessage forDocumentChanged(
            final Project project,
            final Long documentId,
            final String documentName,
            final String operatorName,
            final String operationLabel,
            final String targetUrl) {
        Long projectId = projectId(project);
        String projectName = projectName(project);
        String safeDocumentName = nullToEmpty(documentName);
        String safeOperator = nullToEmpty(operatorName);
        String safeOperationLabel = nullToEmpty(operationLabel);
        String title = "文档变更 - " + projectName;
        String body = "【" + projectName + "】文档「" + safeDocumentName + "」被 "
                + safeOperator + " " + safeOperationLabel;
        Map<String, Object> payload = basePayload(projectId, projectName, targetUrl);
        payload.put("documentId", documentId);
        payload.put("documentName", safeDocumentName);
        payload.put("operationType", safeOperationLabel);
        return new NotificationMessage(
                TYPE_DOCUMENT_CHANGE, ENTITY_DOCUMENT, documentId, title, body, payload);
    }

    /**
     * 阶段自动推进。
     *
     * <p>文案：{@code 【{customer} - {projectName}】阶段发生自动流转：{fromStage} → {toStage}}
     */
    public static NotificationMessage forStageTransition(
            final Project project,
            final String customerName,
            final ProjectStage fromStage,
            final ProjectStage toStage,
            final String targetUrl) {
        Long projectId = projectId(project);
        String projectName = projectName(project);
        String prefix = bracketProject(customerName, projectName);
        String title = "阶段自动推进 - " + projectName;
        String body = prefix + "阶段发生自动流转："
                + stageName(fromStage) + " → " + stageName(toStage);
        Map<String, Object> payload = basePayload(projectId, projectName, targetUrl);
        return new NotificationMessage(TYPE_SYSTEM, ENTITY_PROJECT, projectId, title, body, payload);
    }

    /**
     * 任务提交审核。
     *
     * <p>文案：{@code 任务：{taskTitle}\n提交人：{submitterName}\n\n该任务已提交审核，请尽快处理。}
     */
    public static NotificationMessage forTaskReviewSubmitted(
            final Project project,
            final Long taskId,
            final String taskTitle,
            final String submitterName,
            final String targetUrl) {
        Long projectId = projectId(project);
        String projectName = projectName(project);
        String safeTitle = nullToEmpty(taskTitle);
        String safeName = nullToEmpty(submitterName);
        String title = "任务审核通知 - " + projectName + " - " + safeTitle;
        String body = "任务：" + safeTitle + "\n提交人：" + safeName
                + "\n\n该任务已提交审核，请尽快处理。";
        Map<String, Object> payload = taskPayload(projectId, projectName, taskId, safeTitle, targetUrl);
        return new NotificationMessage(TYPE_TASK_UPDATE, ENTITY_PROJECT, projectId, title, body, payload);
    }

    /**
     * 任务审核结果。
     *
     * <p>文案：{@code 任务：{taskTitle}\n审核结果：{action}\n\n您的任务已审核{action}，请查看。}
     */
    public static NotificationMessage forTaskReviewResult(
            final Project project,
            final Long taskId,
            final String taskTitle,
            final boolean approved,
            final String targetUrl) {
        Long projectId = projectId(project);
        String projectName = projectName(project);
        String safeTitle = nullToEmpty(taskTitle);
        String action = approved ? "通过" : "驳回";
        String title = "任务审核" + action + " - " + projectName + " - " + safeTitle;
        String body = "任务：" + safeTitle + "\n审核结果：" + action
                + "\n\n您的任务已审核" + action + "，请查看。";
        Map<String, Object> payload = taskPayload(projectId, projectName, taskId, safeTitle, targetUrl);
        return new NotificationMessage(TYPE_TASK_UPDATE, ENTITY_PROJECT, projectId, title, body, payload);
    }

    /**
     * 投标立项后待立项通知。
     * <p>文案：{@code 待立项 - {projectName}} / {@code 【{tenderName}】已投标，项目「{projectName}」待立项，请尽快处理。}
     */
    public static NotificationMessage forPendingInitiation(
            final Project project, final Tender tender, final String targetUrl) {
        Long projectId = project == null ? null : project.getId();
        String projectName = project == null ? "" : nullToEmpty(project.getName());
        Long tenderId = tender == null ? null : tender.getId();
        String tenderName = tender == null ? "" : nullToEmpty(tender.getTitle());
        Map<String, Object> payload = basePayload(projectId, projectName, targetUrl);
        payload.put("tenderId", tenderId);
        payload.put("tenderName", tenderName);
        return new NotificationMessage(TYPE_SYSTEM, ENTITY_PROJECT, projectId,
                "待立项 - " + projectName,
                "【" + tenderName + "】已投标，项目「" + projectName + "」待立项，请尽快处理。", payload);
    }

    private static Map<String, Object> basePayload(
            final Long projectId, final String projectName, final String targetUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("projectName", projectName);
        payload.put("targetUrl", targetUrl);
        return payload;
    }

    private static Map<String, Object> taskPayload(
            final Long projectId,
            final String projectName,
            final Long taskId,
            final String taskName,
            final String targetUrl) {
        Map<String, Object> payload = basePayload(projectId, projectName, targetUrl);
        payload.put("taskId", taskId);
        payload.put("taskName", taskName);
        return payload;
    }

    private static String bracketProject(final String customerName, final String projectName) {
        if (customerName != null && !customerName.isBlank()) {
            return "【" + customerName + " - " + projectName + "】";
        }
        return "【" + projectName + "】";
    }

    private static Long projectId(final Project project) {
        return project == null ? null : project.getId();
    }

    private static String projectName(final Project project) {
        return project == null ? "" : nullToEmpty(project.getName());
    }

    private static Long taskId(final Task task) {
        return task == null ? null : task.getId();
    }

    private static String taskName(final Task task) {
        return task == null ? "" : nullToEmpty(task.getTitle());
    }

    private static String stageName(final ProjectStage stage) {
        return stage == null ? "" : stage.getDisplayName();
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }
}
