package com.xiyu.bid.tender.listener;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.service.TenderEvaluationNotificationService;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * TenderEvaluatedNotificationListener 单测（CO-304）。
 * <p>覆盖 4 个场景：
 * <ul>
 *   <li>newStatus == EVALUATED → 应发通知</li>
 *   <li>newStatus != EVALUATED → 不发通知</li>
 *   <li>tender 不存在 → 不抛异常，不发通知</li>
 *   <li>tender.status != EVALUATED（状态回退）→ 不发通知</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TenderEvaluatedNotificationListenerTest {

    @Mock
    private TenderEvaluationNotificationService notificationService;

    @Mock
    private TenderRepository tenderRepository;

    @InjectMocks
    private TenderEvaluatedNotificationListener listener;

    private Tender tender;

    @BeforeEach
    void setUp() {
        tender = new Tender();
        tender.setId(1L);
        tender.setTitle("测试标讯");
        tender.setStatus(Tender.Status.EVALUATED);
        tender.setCreatorId(100L);
    }

    @Test
    @DisplayName("newStatus == EVALUATED → 应发通知")
    void whenNewStatusIsEvaluated_shouldCreateNotification() {
        // given
        TenderStatusChangedEvent event = new TenderStatusChangedEvent(
                1L, "EXT-001", Tender.Status.TRACKING, Tender.Status.EVALUATED,
                "测试标讯", LocalDateTime.now(), null, null, null, null, null);
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        // when
        listener.onTenderEvaluated(event);

        // then
        verify(notificationService, times(1)).createEvaluationNotifications(tender);
    }

    @Test
    @DisplayName("newStatus != EVALUATED → 不发通知")
    void whenNewStatusIsNotEvaluated_shouldNotCreateNotification() {
        // given
        TenderStatusChangedEvent event = new TenderStatusChangedEvent(
                1L, "EXT-001", Tender.Status.TRACKING, Tender.Status.BIDDING,
                "测试标讯", LocalDateTime.now(), null, null, null, null, null);

        // when
        listener.onTenderEvaluated(event);

        // then
        verify(notificationService, never()).createEvaluationNotifications(any());
        verify(tenderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("tender 不存在 → 不抛异常，不发通知")
    void whenTenderNotFound_shouldNotThrowAndNotCreateNotification() {
        // given
        TenderStatusChangedEvent event = new TenderStatusChangedEvent(
                1L, "EXT-001", Tender.Status.TRACKING, Tender.Status.EVALUATED,
                "测试标讯", LocalDateTime.now(), null, null, null, null, null);
        when(tenderRepository.findById(1L)).thenReturn(Optional.empty());

        // when
        listener.onTenderEvaluated(event);

        // then
        verify(notificationService, never()).createEvaluationNotifications(any());
    }

    @Test
    @DisplayName("tender.status != EVALUATED（状态回退）→ 不发通知")
    void whenTenderStatusIsNotEvaluated_shouldNotCreateNotification() {
        // given
        TenderStatusChangedEvent event = new TenderStatusChangedEvent(
                1L, "EXT-001", Tender.Status.TRACKING, Tender.Status.EVALUATED,
                "测试标讯", LocalDateTime.now(), null, null, null, null, null);
        tender.setStatus(Tender.Status.TRACKING); // 状态已回退
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        // when
        listener.onTenderEvaluated(event);

        // then
        verify(notificationService, never()).createEvaluationNotifications(any());
    }
}