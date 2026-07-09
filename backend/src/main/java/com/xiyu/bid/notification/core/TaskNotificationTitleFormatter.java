package com.xiyu.bid.notification.core;

/**
 * 任务通知标题格式化纯核心工具。
 *
 * <p>无 Spring、无 IO、无副作用。统一模板：{动作} - {项目名} - {任务名}。</p>
 */
public final class TaskNotificationTitleFormatter {

    private static final int DEFAULT_MAX_LENGTH = 200;

    private TaskNotificationTitleFormatter() {
    }

    /**
     * 格式化任务通知标题，超过默认 200 字符时截断。
     *
     * @param action      场景动作，如"任务分配"
     * @param projectName 项目名称，允许 null
     * @param taskName    任务名称，允许 null
     */
    public static String format(String action, String projectName, String taskName) {
        return format(action, projectName, taskName, DEFAULT_MAX_LENGTH);
    }

    /**
     * 格式化任务通知标题，超过 maxLength 时截断。
     */
    public static String format(String action, String projectName, String taskName, int maxLength) {
        String safeAction = action == null ? "任务通知" : action;
        String safeProject = projectName == null ? "" : projectName;
        String safeTask = taskName == null ? "" : taskName;
        String base = safeProject.isBlank()
                ? safeAction + " - " + safeTask
                : safeAction + " - " + safeProject + " - " + safeTask;
        if (base.length() <= maxLength) {
            return base;
        }
        return base.substring(0, maxLength);
    }
}
