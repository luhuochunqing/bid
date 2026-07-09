// Output: NotificationMessagePolicy 6 条系统通知模板 + 降级 + payload 字段覆盖
// Pos: notification/core/ - 消息模板策略纯核心单元测试
package com.xiyu.bid.notification.core;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.project.core.ProjectStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@DisplayName("NotificationMessagePolicy — 系统通知类 6 条消息模板策略")
class NotificationMessagePolicyTest {

    private static final Long PROJECT_ID = 100L;
    private static final String PROJECT_NAME = "西域智能投标项目";
    private static final String CUSTOMER_NAME = "西域股份";
    private static final Long TASK_ID = 200L;
    private static final String TASK_NAME = "编制技术标书";
    private static final Long DOCUMENT_ID = 300L;
    private static final String DOCUMENT_NAME = "技术标书.pdf";
    private static final String TARGET_URL = "/project/100/drafting";

    private static Project project() {
        return Project.builder()
                .id(PROJECT_ID)
                .name(PROJECT_NAME)
                .build();
    }

    private static Task task() {
        return Task.builder()
                .id(TASK_ID)
                .projectId(PROJECT_ID)
                .title(TASK_NAME)
                .build();
    }

    @Test
    @DisplayName("项目结项归档：生成 SYSTEM 类型消息")
    void forProjectArchived_shouldGenerateArchiveMessage() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forProjectArchived(project(), CUSTOMER_NAME, TARGET_URL);

        assertThat(message.type()).isEqualTo(NotificationType.SYSTEM.name());
        assertThat(message.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(message.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(message.title()).isEqualTo("项目结项归档 - " + PROJECT_NAME);
        assertThat(message.body()).isEqualTo(
                "【" + CUSTOMER_NAME + " - " + PROJECT_NAME + "】已结项归档，所有字段锁定，资料已自动归档");
        assertThat(message.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("项目结项归档：customerName 为空时降级为仅 projectName")
    void forProjectArchived_shouldFallback_whenCustomerNameBlank() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forProjectArchived(project(), "  ", TARGET_URL);

        assertThat(message.body()).isEqualTo(
                "【" + PROJECT_NAME + "】已结项归档，所有字段锁定，资料已自动归档");
    }

    @Test
    @DisplayName("任务状态变更：生成 TASK_UPDATE 类型消息")
    void forTaskStatusChanged_shouldGenerateTaskUpdateMessage() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forTaskStatusChanged(
                        project(), task(), "待处理", "已完成", TARGET_URL);

        assertThat(message.type()).isEqualTo(NotificationType.TASK_UPDATE.name());
        assertThat(message.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(message.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(message.title()).isEqualTo("任务状态变更 - " + PROJECT_NAME + " - " + TASK_NAME);
        assertThat(message.body()).isEqualTo(
                "【" + PROJECT_NAME + "】任务「" + TASK_NAME + "」状态发生变更：待处理 → 已完成");
        assertThat(message.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("taskId", TASK_ID),
                entry("taskName", TASK_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("任务状态变更：状态为空时降级为空字符串")
    void forTaskStatusChanged_shouldFallback_whenStatusBlank() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forTaskStatusChanged(
                        project(), task(), null, "已完成", TARGET_URL);

        assertThat(message.body()).isEqualTo(
                "【" + PROJECT_NAME + "】任务「" + TASK_NAME + "」状态发生变更： → 已完成");
    }

    @Test
    @DisplayName("任务分配：生成 TASK_UPDATE 类型消息")
    void forTaskAssigned_shouldGenerateTaskAssignedMessage() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forTaskAssigned(project(), task(), TARGET_URL);

        assertThat(message.type()).isEqualTo(NotificationType.TASK_UPDATE.name());
        assertThat(message.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(message.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(message.title()).isEqualTo("任务分配 - " + PROJECT_NAME + " - " + TASK_NAME);
        assertThat(message.body()).isEqualTo(
                "【" + PROJECT_NAME + "】新任务「" + TASK_NAME + "」已指派给您，请尽快处理");
        assertThat(message.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("taskId", TASK_ID),
                entry("taskName", TASK_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("@ 提及：生成 MENTION 类型消息")
    void forMention_shouldGenerateMentionMessage() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forMention(
                        PROJECT_NAME, "张三", "技术讨论", "COMMENT", 42L, TARGET_URL);

        assertThat(message.type()).isEqualTo(NotificationType.MENTION.name());
        assertThat(message.sourceEntityType()).isEqualTo("COMMENT");
        assertThat(message.sourceEntityId()).isEqualTo(42L);
        assertThat(message.title()).isEqualTo("@ 提及 - " + PROJECT_NAME);
        assertThat(message.body()).isEqualTo(
                "【" + PROJECT_NAME + "】张三 在「技术讨论」中 @ 了您");
        assertThat(message.payload()).containsOnly(
                entry("projectId", null),
                entry("projectName", PROJECT_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("@ 提及：输入为空时降级")
    void forMention_shouldFallback_whenInputsBlank() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forMention(null, null, null, null, null, null);

        assertThat(message.title()).isEqualTo("@ 提及 - ");
        assertThat(message.body()).isEqualTo("【】 在「」中 @ 了您");
        assertThat(message.sourceEntityType()).isEmpty();
        assertThat(message.sourceEntityId()).isNull();
    }

    @Test
    @DisplayName("文档变更：生成 DOCUMENT_CHANGE 类型消息")
    void forDocumentChanged_shouldGenerateDocumentChangeMessage() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forDocumentChanged(
                        project(), DOCUMENT_ID, DOCUMENT_NAME, "李四", "更新", TARGET_URL);

        assertThat(message.type()).isEqualTo(NotificationType.DOCUMENT_CHANGE.name());
        assertThat(message.sourceEntityType()).isEqualTo("DOCUMENT");
        assertThat(message.sourceEntityId()).isEqualTo(DOCUMENT_ID);
        assertThat(message.title()).isEqualTo("文档变更 - " + PROJECT_NAME);
        assertThat(message.body()).isEqualTo(
                "【" + PROJECT_NAME + "】文档「" + DOCUMENT_NAME + "」被 李四 更新");
        assertThat(message.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("targetUrl", TARGET_URL),
                entry("documentId", DOCUMENT_ID),
                entry("documentName", DOCUMENT_NAME),
                entry("operationType", "更新"));
    }

    @Test
    @DisplayName("阶段自动推进：生成 SYSTEM 类型消息")
    void forStageTransition_shouldGenerateStageTransitionMessage() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forStageTransition(
                        project(), CUSTOMER_NAME, ProjectStage.DRAFTING, ProjectStage.EVALUATING, TARGET_URL);

        assertThat(message.type()).isEqualTo(NotificationType.SYSTEM.name());
        assertThat(message.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(message.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(message.title()).isEqualTo("阶段自动推进 - " + PROJECT_NAME);
        assertThat(message.body()).isEqualTo(
                "【" + CUSTOMER_NAME + " - " + PROJECT_NAME + "】阶段发生自动流转：标书编制 → 评标");
        assertThat(message.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("阶段自动推进：customerName 为空时降级为仅 projectName")
    void forStageTransition_shouldFallback_whenCustomerNameBlank() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forStageTransition(
                        project(), null, ProjectStage.DRAFTING, ProjectStage.EVALUATING, TARGET_URL);

        assertThat(message.body()).isEqualTo(
                "【" + PROJECT_NAME + "】阶段发生自动流转：标书编制 → 评标");
    }

    @Test
    @DisplayName("任务提交审核：生成 TASK_UPDATE 类型消息")
    void forTaskReviewSubmitted_shouldGenerateTaskReviewSubmittedMessage() {
        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forTaskReviewSubmitted(
                        project(), TASK_ID, TASK_NAME, "张三", TARGET_URL);

        assertThat(message.type()).isEqualTo(NotificationType.TASK_UPDATE.name());
        assertThat(message.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(message.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(message.title()).isEqualTo("任务审核通知 - " + PROJECT_NAME + " - " + TASK_NAME);
        assertThat(message.body()).isEqualTo(
                "任务：" + TASK_NAME + "\n提交人：张三\n\n该任务已提交审核，请尽快处理。");
        assertThat(message.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("taskId", TASK_ID),
                entry("taskName", TASK_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("任务审核结果：生成 TASK_UPDATE 类型消息（通过/驳回）")
    void forTaskReviewResult_shouldGenerateTaskReviewResultMessage() {
        NotificationMessagePolicy.NotificationMessage approved =
                NotificationMessagePolicy.forTaskReviewResult(
                        project(), TASK_ID, TASK_NAME, true, TARGET_URL);
        NotificationMessagePolicy.NotificationMessage rejected =
                NotificationMessagePolicy.forTaskReviewResult(
                        project(), TASK_ID, TASK_NAME, false, TARGET_URL);

        assertThat(approved.title()).isEqualTo("任务审核通过 - " + PROJECT_NAME + " - " + TASK_NAME);
        assertThat(approved.body()).isEqualTo(
                "任务：" + TASK_NAME + "\n审核结果：通过\n\n您的任务已审核通过，请查看。");
        assertThat(rejected.title()).isEqualTo("任务审核驳回 - " + PROJECT_NAME + " - " + TASK_NAME);
        assertThat(rejected.body()).isEqualTo(
                "任务：" + TASK_NAME + "\n审核结果：驳回\n\n您的任务已审核驳回，请查看。");
    }

    @Test
    @DisplayName("所有工厂方法：targetUrl 原样透传")
    void allFactories_shouldPassThroughTargetUrl() {
        String url = "/custom/url";

        assertThat(NotificationMessagePolicy.forProjectArchived(project(), CUSTOMER_NAME, url).payload())
                .containsEntry("targetUrl", url);
        assertThat(NotificationMessagePolicy.forTaskStatusChanged(project(), task(), "a", "b", url).payload())
                .containsEntry("targetUrl", url);
        assertThat(NotificationMessagePolicy.forTaskAssigned(project(), task(), url).payload())
                .containsEntry("targetUrl", url);
        assertThat(NotificationMessagePolicy.forMention(PROJECT_NAME, "a", "b", "C", 1L, url).payload())
                .containsEntry("targetUrl", url);
        assertThat(NotificationMessagePolicy.forDocumentChanged(project(), 1L, "d", "e", "f", url).payload())
                .containsEntry("targetUrl", url);
        assertThat(NotificationMessagePolicy.forStageTransition(project(), CUSTOMER_NAME, ProjectStage.INITIATED, ProjectStage.DRAFTING, url).payload())
                .containsEntry("targetUrl", url);
        assertThat(NotificationMessagePolicy.forTaskReviewSubmitted(project(), TASK_ID, TASK_NAME, "a", url).payload())
                .containsEntry("targetUrl", url);
        assertThat(NotificationMessagePolicy.forTaskReviewResult(project(), TASK_ID, TASK_NAME, true, url).payload())
                .containsEntry("targetUrl", url);
    }
}
