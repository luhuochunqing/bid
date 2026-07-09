// Input: CreateMentionRequest + mentioner id + mocked deps
// Output: MentionResult value; notification dispatched; mention rows saved
// Pos: Test/提及编排服务门禁
package com.xiyu.bid.mention.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.CreateMentionRequest;
import com.xiyu.bid.mention.entity.Mention;
import com.xiyu.bid.mention.repository.MentionRepository;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MentionApplicationService — parse + dispatch + persist")
class MentionApplicationServiceTest {

    @Mock
    private MentionRepository mentionRepository;

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRecipientResolver recipientResolver;

    private MentionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MentionApplicationService(mentionRepository, notificationService, userRepository, recipientResolver);
        User mentioner = new User();
        mentioner.setId(1L);
        mentioner.setFullName("张三");
        // lenient：部分测试在调用 resolveMentionerName 前已提前返回
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(mentioner));
        // 默认不过滤（保持既有用例语义）
        lenient().when(recipientResolver.filterByProjectAccess(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("content with no @ ids returns no-op, no notification, no mention rows")
    void noMentions_ReturnsNoOp() {
        CreateMentionRequest req = new CreateMentionRequest(
            "hello world no mention here", "comment", 42L, "Comment");

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        assertThat(result.mentionCount()).isZero();
        assertThat(result.notificationId()).isNull();
        verify(notificationService, never()).createNotification(any(), anyLong());
        verify(mentionRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("two mentions dispatch one notification with both recipient ids")
    void twoMentions_DispatchOneNotification() {
        CreateMentionRequest req = new CreateMentionRequest(
            "hi @[a](7) and @[b](8)", "comment", 42L, "Comment");
        when(notificationService.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.validWithId(100L));

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), anyLong());
        CreateNotificationRequest captured = captor.getValue();

        assertThat(captured.type()).isEqualTo("MENTION");
        assertThat(captured.recipientUserIds()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(captured.sourceEntityType()).isEqualTo("comment");
        assertThat(captured.sourceEntityId()).isEqualTo(42L);
        assertThat(captured.title()).isEqualTo("@ 提及 - ");
        assertThat(captured.body()).isEqualTo("【】张三 在「comment」中 @ 了您");
        assertThat(result.mentionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("two mentions save two Mention rows with correct metadata")
    void twoMentions_SaveTwoMentionRows() {
        CreateMentionRequest req = new CreateMentionRequest(
            "hi @[a](7) and @[b](8)", "comment", 42L, "Comment");
        when(notificationService.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.validWithId(100L));

        service.createMention(req, 1L);

        ArgumentCaptor<Iterable<Mention>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(mentionRepository).saveAll(captor.capture());
        List<Mention> saved = toList(captor.getValue());
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(m -> {
            assertThat(m.getMentionerUserId()).isEqualTo(1L);
            assertThat(m.getSourceEntityType()).isEqualTo("comment");
            assertThat(m.getSourceEntityId()).isEqualTo(42L);
        });
        assertThat(saved.stream().map(Mention::getMentionedUserId).toList())
            .containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    @DisplayName("self-mention is skipped — user 1 mentioning themselves yields no-op")
    void selfMention_IsSkipped() {
        CreateMentionRequest req = new CreateMentionRequest(
            "ping myself @[me](1)", "comment", 42L, "Comment");

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        assertThat(result.mentionCount()).isZero();
        verify(notificationService, never()).createNotification(any(), anyLong());
        verify(mentionRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("mixed self + other: only non-self recipient is dispatched")
    void mixedSelfAndOther_FiltersSelf() {
        CreateMentionRequest req = new CreateMentionRequest(
            "@[me](1) @[other](9)", "comment", 42L, "Comment");
        when(notificationService.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.validWithId(100L));

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), anyLong());
        assertThat(captor.getValue().recipientUserIds()).containsExactly(9L);
        assertThat(result.mentionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("notification dispatch error propagates — no mention rows are persisted")
    void dispatchError_DoesNotPersistMentions() {
        CreateMentionRequest req = new CreateMentionRequest(
            "@[a](7)", "comment", 42L, "Comment");
        when(notificationService.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.invalid("INVALID_TITLE", "blank"));

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        assertThat(result.mentionCount()).isZero();
        assertThat(result.notificationId()).isNull();
        verify(mentionRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("TASK mention uses policy title/body and generates task drafting targetUrl")
    void taskMention_GeneratesTargetUrlAndBlueprintMessage() {
        Map<String, Object> payload = Map.of("projectId", "123");
        CreateMentionRequest req = new CreateMentionRequest(
            "@[a](7)", "TASK", 42L, "Task Comment", payload);
        when(notificationService.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.validWithId(100L));

        service.createMention(req, 1L);

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), anyLong());
        CreateNotificationRequest captured = captor.getValue();

        assertThat(captured.type()).isEqualTo("MENTION");
        assertThat(captured.sourceEntityType()).isEqualTo("TASK");
        assertThat(captured.sourceEntityId()).isEqualTo(42L);
        assertThat(captured.title()).isEqualTo("@ 提及 - ");
        assertThat(captured.body()).isEqualTo("【】张三 在「任务评论」中 @ 了您");
        assertThat(captured.payload()).containsEntry("projectId", "123");
        assertThat(captured.payload()).containsEntry("targetUrl", "/project/123/drafting?taskId=42");
        assertThat(captured.payload()).containsEntry("plainText", "@a");
    }

    @Test
    @DisplayName("mentions are filtered by project access when projectId is present")
    void mentions_AreFilteredByProjectAccess() {
        Map<String, Object> payload = Map.of("projectId", 123L);
        CreateMentionRequest req = new CreateMentionRequest(
            "@[a](7) @[b](8) @[c](9)", "comment", 42L, "Comment", payload);
        when(notificationService.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.validWithId(100L));
        when(recipientResolver.filterByProjectAccess(List.of(7L, 8L, 9L), 123L))
            .thenReturn(List.of(7L, 9L));

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), anyLong());
        assertThat(captor.getValue().recipientUserIds()).containsExactlyInAnyOrder(7L, 9L);
        assertThat(result.mentionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("all mentions filtered out by project access → no notification")
    void allMentionsFilteredOut_ReturnsNoOp() {
        Map<String, Object> payload = Map.of("projectId", 123L);
        CreateMentionRequest req = new CreateMentionRequest(
            "@[a](7) @[b](8)", "comment", 42L, "Comment", payload);
        when(recipientResolver.filterByProjectAccess(List.of(7L, 8L), 123L))
            .thenReturn(List.of());

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        assertThat(result.mentionCount()).isZero();
        verify(notificationService, never()).createNotification(any(), anyLong());
        verify(mentionRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("payload is forwarded and merged with policy fields")
    void payload_IsForwardedAndMerged() {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("projectId", "123");
        payload.put("projectName", "历史项目名");
        payload.put("targetUrl", "/custom/url");
        CreateMentionRequest req = new CreateMentionRequest(
            "@[a](7)", "comment", 42L, "Comment", payload);
        when(notificationService.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.validWithId(100L));

        service.createMention(req, 1L);

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), anyLong());
        CreateNotificationRequest captured = captor.getValue();

        assertThat(captured.payload()).isNotNull();
        assertThat(captured.payload()).containsEntry("projectId", "123");
        assertThat(captured.payload()).containsEntry("targetUrl", "/custom/url");
        assertThat(captured.payload()).containsEntry("plainText", "@a");
    }

    private static List<Mention> toList(Iterable<Mention> iter) {
        java.util.List<Mention> out = new java.util.ArrayList<>();
        iter.forEach(out::add);
        return out;
    }
}
