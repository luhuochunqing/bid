// Output: PendingInitiationNotificationMessagePolicy 待立项消息模板覆盖
// Pos: notification/core/ - 待立项通知消息模板策略纯核心单元测试
package com.xiyu.bid.notification.core;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@DisplayName("PendingInitiationNotificationMessagePolicy — 待立项通知消息模板")
class PendingInitiationNotificationMessagePolicyTest {

    private static final Long PROJECT_ID = 100L;
    private static final String PROJECT_NAME = "西域智能投标项目";
    private static final Long TENDER_ID = 10L;
    private static final String TENDER_NAME = "西域智能标讯";
    private static final String TARGET_URL = "/project/100/initiation";

    @Test
    @DisplayName("生成 PENDING_INITIATION 类型待立项通知消息")
    void forPendingInitiation_shouldGenerateMessage() {
        NotificationMessagePolicy.NotificationMessage message =
                PendingInitiationNotificationMessagePolicy.forPendingInitiation(
                        Project.builder().id(PROJECT_ID).name(PROJECT_NAME).build(),
                        Tender.builder().id(TENDER_ID).title(TENDER_NAME).build(),
                        TARGET_URL);

        assertThat(message.type()).isEqualTo(NotificationType.PENDING_INITIATION.name());
        assertThat(message.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(message.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(message.title()).isEqualTo("待立项 - " + PROJECT_NAME);
        assertThat(message.body()).isEqualTo(
                "【" + TENDER_NAME + "】已投标，项目「" + PROJECT_NAME + "」待立项，请尽快处理。");
        assertThat(message.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("tenderId", TENDER_ID),
                entry("tenderName", TENDER_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("project/tender 为空时降级为空字符串/null")
    void forPendingInitiation_shouldFallback_whenInputsNull() {
        NotificationMessagePolicy.NotificationMessage message =
                PendingInitiationNotificationMessagePolicy.forPendingInitiation(null, null, null);

        assertThat(message.type()).isEqualTo(NotificationType.PENDING_INITIATION.name());
        assertThat(message.sourceEntityId()).isNull();
        assertThat(message.title()).isEqualTo("待立项 - ");
        assertThat(message.body()).isEqualTo("【】已投标，项目「」待立项，请尽快处理。");
        assertThat(message.payload()).containsOnly(
                entry("projectId", null),
                entry("projectName", ""),
                entry("tenderId", null),
                entry("tenderName", ""),
                entry("targetUrl", null));
    }
}
