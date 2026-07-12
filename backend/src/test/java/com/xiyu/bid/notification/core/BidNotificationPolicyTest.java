// Output: BidNotificationPolicy 生成待立项通知请求、去重、targetUrl 透传
// Pos: notification/core/ - 投标立项通知纯核心策略单元测试
package com.xiyu.bid.notification.core;

import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@DisplayName("BidNotificationPolicy — 投标立项待立项通知策略")
class BidNotificationPolicyTest {

    private static final Long TENDER_ID = 10L;
    private static final Long PROJECT_ID = 100L;
    private static final String TENDER_NAME = "西域智能标讯";
    private static final String PROJECT_NAME = "西域智能投标项目";
    private static final String TARGET_URL = "/project/100/initiation";
    private static final Long TRIGGERED_BY = 99L;
    private static final List<Long> RECIPIENTS = List.of(7L);

    @Test
    @DisplayName("生成待立项通知请求：字段符合 CreateNotificationRequest 约定")
    void createRequest_shouldGeneratePendingInitiationRequest() {
        Optional<CreateNotificationRequest> result = BidNotificationPolicy.createRequest(
                TENDER_ID, PROJECT_ID, TENDER_NAME, PROJECT_NAME, TARGET_URL,
                TRIGGERED_BY, RECIPIENTS, Instant.now(), List.of());

        assertThat(result).isPresent();
        CreateNotificationRequest request = result.get();
        assertThat(request.type()).isEqualTo(NotificationType.SYSTEM.name());
        assertThat(request.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(request.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(request.title()).isEqualTo("待立项 - " + PROJECT_NAME);
        assertThat(request.body()).isEqualTo(
                "【" + TENDER_NAME + "】已投标，项目「" + PROJECT_NAME + "」待立项，请尽快处理。");
        assertThat(request.recipientUserIds()).containsExactly(7L);
        assertThat(request.payload()).containsOnly(
                entry("projectId", PROJECT_ID),
                entry("projectName", PROJECT_NAME),
                entry("tenderId", TENDER_ID),
                entry("tenderName", TENDER_NAME),
                entry("targetUrl", TARGET_URL));
    }

    @Test
    @DisplayName("5 分钟去重窗口内已存在通知时返回 empty")
    void createRequest_shouldSkip_whenDuplicateWithinWindow() {
        Instant now = Instant.parse("2026-07-12T09:00:00Z");
        Instant duplicateAt = now.minus(3, ChronoUnit.MINUTES);

        Optional<CreateNotificationRequest> result = BidNotificationPolicy.createRequest(
                TENDER_ID, PROJECT_ID, TENDER_NAME, PROJECT_NAME, TARGET_URL,
                TRIGGERED_BY, RECIPIENTS, now, List.of(duplicateAt));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("5 分钟窗口外的时间戳不触发去重")
    void createRequest_shouldCreate_whenTimestampOutsideWindow() {
        Instant now = Instant.parse("2026-07-12T09:00:00Z");
        Instant old = now.minus(6, ChronoUnit.MINUTES);

        Optional<CreateNotificationRequest> result = BidNotificationPolicy.createRequest(
                TENDER_ID, PROJECT_ID, TENDER_NAME, PROJECT_NAME, TARGET_URL,
                TRIGGERED_BY, RECIPIENTS, now, List.of(old));

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("空历史时间戳时不触发去重")
    void createRequest_shouldCreate_whenNoExistingTimestamps() {
        Optional<CreateNotificationRequest> result = BidNotificationPolicy.createRequest(
                TENDER_ID, PROJECT_ID, TENDER_NAME, PROJECT_NAME, TARGET_URL,
                TRIGGERED_BY, RECIPIENTS, Instant.now(), null);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("targetUrl 原样透传到 payload")
    void createRequest_shouldPassThroughTargetUrl() {
        String customUrl = "/project/999/initiation";

        Optional<CreateNotificationRequest> result = BidNotificationPolicy.createRequest(
                TENDER_ID, PROJECT_ID, TENDER_NAME, PROJECT_NAME, customUrl,
                TRIGGERED_BY, RECIPIENTS, Instant.now(), List.of());

        assertThat(result).isPresent();
        assertThat(result.get().payload()).containsEntry("targetUrl", customUrl);
    }

    @Test
    @DisplayName("多个接收人时原样保留顺序")
    void createRequest_shouldPreserveRecipientsOrder() {
        List<Long> recipients = List.of(7L, 8L, 9L);

        Optional<CreateNotificationRequest> result = BidNotificationPolicy.createRequest(
                TENDER_ID, PROJECT_ID, TENDER_NAME, PROJECT_NAME, TARGET_URL,
                TRIGGERED_BY, recipients, Instant.now(), List.of());

        assertThat(result).isPresent();
        assertThat(result.get().recipientUserIds()).containsExactly(7L, 8L, 9L);
    }
}
