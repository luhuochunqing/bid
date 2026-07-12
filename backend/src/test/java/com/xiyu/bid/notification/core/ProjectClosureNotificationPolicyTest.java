// Output: ProjectClosureNotificationPolicy deduplication + CreateNotificationRequest assembly
// Pos: backend test source / notification core pure-core unit test
package com.xiyu.bid.notification.core;

import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@DisplayName("ProjectClosureNotificationPolicy — 待结项申请通知纯核心策略")
class ProjectClosureNotificationPolicyTest {

    private static final Long PROJECT_ID = 42L;
    private static final String PROJECT_NAME = "西域智能投标项目";
    private static final String TARGET_URL = "/projects/42/closure";
    private static final Long TRIGGERED_BY = 7L;
    private static final List<Long> RECIPIENTS = List.of(100L, 101L);
    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Test
    @DisplayName("无历史通知时生成 SYSTEM 类型待结项申请请求")
    void createRequest_withoutHistory_shouldGenerateRequest() {
        Optional<CreateNotificationRequest> result = ProjectClosureNotificationPolicy.createRequest(
                PROJECT_ID, PROJECT_NAME, TARGET_URL, TRIGGERED_BY, NOW, List.of(), RECIPIENTS);

        assertThat(result).isPresent();
        CreateNotificationRequest request = result.get();
        assertThat(request.type()).isEqualTo(NotificationType.SYSTEM.name());
        assertThat(request.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(request.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(request.title()).isEqualTo("待结项申请 - " + PROJECT_NAME);
        assertThat(request.body()).isEqualTo(
                "【" + PROJECT_NAME + "】已进入结项阶段，请尽快提交结项申请。");
        assertThat(request.recipientUserIds()).containsExactlyElementsOf(RECIPIENTS);
        assertThat(request.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("5 分钟窗口内存在历史通知时返回 empty（去重）")
    void createRequest_withinDedupWindow_shouldReturnEmpty() {
        List<Instant> existing = List.of(Instant.parse("2026-07-12T09:55:00Z"));

        Optional<CreateNotificationRequest> result = ProjectClosureNotificationPolicy.createRequest(
                PROJECT_ID, PROJECT_NAME, TARGET_URL, TRIGGERED_BY, NOW, existing, RECIPIENTS);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("5 分钟窗口外存在历史通知时允许生成新请求")
    void createRequest_outsideDedupWindow_shouldGenerateRequest() {
        List<Instant> existing = List.of(Instant.parse("2026-07-12T09:54:59Z"));

        Optional<CreateNotificationRequest> result = ProjectClosureNotificationPolicy.createRequest(
                PROJECT_ID, PROJECT_NAME, TARGET_URL, TRIGGERED_BY, NOW, existing, RECIPIENTS);

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("待结项申请 - " + PROJECT_NAME);
    }

    @Test
    @DisplayName("空白 projectName 时原样透传（由调用方决定清洗策略）")
    void createRequest_blankProjectName_shouldPassThrough() {
        Optional<CreateNotificationRequest> result = ProjectClosureNotificationPolicy.createRequest(
                PROJECT_ID, "  ", TARGET_URL, TRIGGERED_BY, NOW, List.of(), RECIPIENTS);

        assertThat(result).isPresent();
        CreateNotificationRequest request = result.get();
        assertThat(request.title()).isEqualTo("待结项申请 -   ");
        assertThat(request.body()).isEqualTo("【  】已进入结项阶段，请尽快提交结项申请。");
        assertThat(request.payload()).containsEntry("projectName", "  ");
    }

    @Test
    @DisplayName("空接收人列表时仍生成请求（由外层过滤）")
    void createRequest_emptyRecipients_shouldStillGenerateRequest() {
        Optional<CreateNotificationRequest> result = ProjectClosureNotificationPolicy.createRequest(
                PROJECT_ID, PROJECT_NAME, TARGET_URL, TRIGGERED_BY, NOW, List.of(), List.of());

        assertThat(result).isPresent();
        assertThat(result.get().recipientUserIds()).isEmpty();
    }
}
