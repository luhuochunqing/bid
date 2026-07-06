package com.xiyu.bid.notification.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 030: 通知接收人过滤器单元测试。
 *
 * <p>验证 {@link NotificationRecipientFilter#filterRecipients} 在通知派发前按
 * "接收人对该资源是否有访问权"过滤候选接收人集合的行为。
 *
 * <p>对应纯核心：{@link NotificationRecipientFilter}。
 * 该纯函数供 TaskReviewNotificationService（以及未来其他通知派发器）共用，
 * 避免接收人过滤逻辑在多个 Service 中复制。
 *
 * <p>详细契约见 specs/030-fix-task-review-notify-403/contracts/notification-filter-api.md。
 */
class NotificationRecipientFilterTest {

    @Test
    @DisplayName("null 候选集合：返回空列表")
    void shouldReturnEmpty_whenCandidatesNull() {
        List<Long> result = NotificationRecipientFilter.filterRecipients(null, uid -> true);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空候选集合：返回空列表")
    void shouldReturnEmpty_whenCandidatesEmpty() {
        List<Long> result = NotificationRecipientFilter.filterRecipients(List.of(), uid -> true);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("全部通过：保留所有候选")
    void shouldReturnAll_whenAllAccessible() {
        List<Long> result = NotificationRecipientFilter.filterRecipients(
                Arrays.asList(1L, 2L, 3L), uid -> true);

        assertThat(result).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("全部被过滤：返回空列表")
    void shouldReturnEmpty_whenNoneAccessible() {
        List<Long> result = NotificationRecipientFilter.filterRecipients(
                Arrays.asList(1L, 2L, 3L), uid -> false);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("部分过滤：仅保留可访问的接收人（如偶数 id）")
    void shouldPreserveOnlyAccessible_whenPartialMatch() {
        List<Long> result = NotificationRecipientFilter.filterRecipients(
                Arrays.asList(1L, 2L, 3L, 4L), uid -> uid % 2 == 0);

        assertThat(result).containsExactly(2L, 4L);
    }

    @Test
    @DisplayName("null 元素跳过：候选集合中的 null 不传给 predicate，不抛异常")
    void shouldSkipNullElements() {
        List<Long> result = NotificationRecipientFilter.filterRecipients(
                Arrays.asList(1L, null, 3L), uid -> true);

        assertThat(result).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("去重：候选集合中重复的 user_id 仅保留首次出现")
    void shouldDeduplicateCandidates() {
        List<Long> result = NotificationRecipientFilter.filterRecipients(
                Arrays.asList(1L, 2L, 1L, 3L), uid -> true);

        assertThat(result).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("顺序保留：输出顺序与候选输入顺序一致")
    void shouldPreserveInputOrder() {
        List<Long> result = NotificationRecipientFilter.filterRecipients(
                Arrays.asList(3L, 1L, 2L), uid -> true);

        assertThat(result).containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("predicate 为 null：抛 NullPointerException（让调用方编程错误早暴露）")
    void shouldThrowNpe_whenPredicateNull() {
        assertThatThrownBy(() ->
                NotificationRecipientFilter.filterRecipients(Arrays.asList(1L, 2L), null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("predicate 抛异常：异常向上透传，不在纯函数内吞错")
    void shouldPropagatePredicateException() {
        assertThatThrownBy(() ->
                NotificationRecipientFilter.filterRecipients(
                        Arrays.asList(1L, 2L),
                        uid -> { throw new RuntimeException("db down"); })
        ).isInstanceOf(RuntimeException.class)
         .hasMessage("db down");
    }
}
