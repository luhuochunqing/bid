// Input: CreateMentionRequest + mentioner id + mocked deps
// Output: MentionResult value; notification dispatched; mention rows saved
// Pos: Test/提及编排服务门禁
package com.xiyu.bid.mention.service;

import com.xiyu.bid.mention.dto.CreateMentionRequest;
import com.xiyu.bid.mention.entity.Mention;
import com.xiyu.bid.mention.repository.MentionRepository;
import com.xiyu.bid.notification.core.NotificationDispatchPolicy;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private MentionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MentionApplicationService(mentionRepository, notificationService);
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
            .thenReturn(NotificationDispatchPolicy.DispatchResult.validWithId(100L));

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), anyLong());
        CreateNotificationRequest captured = captor.getValue();

        assertThat(captured.type()).isEqualTo("MENTION");
        assertThat(captured.recipientUserIds()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(captured.sourceEntityType()).isEqualTo("comment");
        assertThat(captured.sourceEntityId()).isEqualTo(42L);
        assertThat(result.mentionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("two mentions save two Mention rows with correct metadata")
    void twoMentions_SaveTwoMentionRows() {
        CreateMentionRequest req = new CreateMentionRequest(
            "hi @[a](7) and @[b](8)", "comment", 42L, "Comment");
        when(notificationService.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(NotificationDispatchPolicy.DispatchResult.validWithId(100L));

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
            .thenReturn(NotificationDispatchPolicy.DispatchResult.validWithId(100L));

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
            .thenReturn(NotificationDispatchPolicy.DispatchResult.invalid("INVALID_TITLE", "blank"));

        MentionApplicationService.MentionResult result = service.createMention(req, 1L);

        assertThat(result.mentionCount()).isZero();
        assertThat(result.notificationId()).isNull();
        verify(mentionRepository, never()).saveAll(anyIterable());
    }

    private static List<Mention> toList(Iterable<Mention> iter) {
        java.util.List<Mention> out = new java.util.ArrayList<>();
        iter.forEach(out::add);
        return out;
    }
}
