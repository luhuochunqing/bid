// Input: Task + Project + User (assignee/manager) + policy 计算结果
// Output: 通知 title + body 字符串
// Pos: task/reminder - 消息构造纯工具，不持有业务规则
// 维护声明:
//   - 仅做消息模板渲染，无副作用、无 IO；
//   - R1: 任务执行人显示 user.fullName（id）格式，避免直接展示 assigneeId；
//   - R2: 任务审核人字段显示项目负责人姓名 + 语义标注（Task 实体暂无 reviewerId）。
package com.xiyu.bid.task.reminder;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;

/**
 * CO-533 任务到期/逾期提醒消息构造器。
 *
 * <p>从 {@link TaskDueReminderService} 拆出，避免编排服务超过 300 行预算。
 * 仅做模板渲染，无副作用、无 IO。
 */
public final class TaskReminderMessageBuilder {

    private TaskReminderMessageBuilder() {
    }

    /**
     * 构造通知标题。
     *
     * @param taskTitle    任务标题
     * @param days         剩余/逾期天数
     * @param overdueMode  true=逾期扫描，false=即将到期扫描
     */
    public static String buildTitle(final String taskTitle, final long days, final boolean overdueMode) {
        String safeTitle = taskTitle != null ? taskTitle : "";
        if (overdueMode) {
            return String.format("【任务逾期提醒】《%s》已逾期 %d 天", safeTitle, days);
        }
        return String.format("【任务到期提醒】《%s》还有 %d 天到期", safeTitle, days);
    }

    /**
     * 构造通知正文。
     *
     * @param task          任务实体
     * @param projectName   项目名称
     * @param days          剩余/逾期天数
     * @param overdueMode   true=逾期扫描
     * @param detailUrlBase 详情链接前缀
     * @param assigneeUser  任务执行人 User（用于显示姓名）
     * @param managerUser   项目负责人 User（用于显示审核人姓名）
     */
    public static String buildBody(final Task task, final String projectName, final long days,
                                   final boolean overdueMode, final String detailUrlBase,
                                   final User assigneeUser, final User managerUser) {
        String safeTitle = task.getTitle() != null ? task.getTitle() : "";
        String statusText = task.getStatus() != null ? task.getStatus().name() : "";
        String daysLabel = overdueMode ? "逾期天数" : "剩余天数";
        String daysValue = overdueMode ? String.valueOf(days) : days + " 天";
        String link = String.format("/project/%d/drafting?taskId=%d", task.getProjectId(), task.getId());
        if (detailUrlBase != null && !detailUrlBase.isBlank()) {
            link = detailUrlBase + link;
        }
        String assigneeText = formatUserLabel(assigneeUser, task.getAssigneeId());
        String reviewerText = formatReviewerLabel(managerUser);
        return String.format(
                "任务名称：%s\n所属项目：%s\n任务执行人：%s\n任务审核人：%s\n任务状态：%s\n截止日期：%s\n%s：%s\n跳转详情：%s",
                safeTitle,
                projectName,
                assigneeText,
                reviewerText,
                statusText,
                task.getDueDate() != null ? task.getDueDate().toLocalDate() : "",
                daysLabel,
                daysValue,
                link
        );
    }

    /** 格式化用户标签：fullName（id）；fullName 缺失时回退 username（id）；再缺失显示 id。 */
    private static String formatUserLabel(final User user, final Long userId) {
        if (userId == null) {
            return "";
        }
        if (user == null) {
            return String.valueOf(userId);
        }
        String name = user.getFullName();
        if (name == null || name.isBlank()) {
            name = user.getUsername();
        }
        if (name == null || name.isBlank()) {
            return String.valueOf(userId);
        }
        return String.format("%s（%d）", name, userId);
    }

    /**
     * 格式化审核人标签。
     * Task 实体暂无 reviewerId 字段，项目负责人(managerId) 作为参考显示；
     * 若项目负责人也缺失，显示"暂未接入"避免误导。
     */
    private static String formatReviewerLabel(final User managerUser) {
        if (managerUser == null) {
            return "暂未接入";
        }
        String name = managerUser.getFullName();
        if (name == null || name.isBlank()) {
            name = managerUser.getUsername();
        }
        if (name == null || name.isBlank()) {
            return "暂未接入";
        }
        return String.format("%s（项目负责人）", name);
    }
}
