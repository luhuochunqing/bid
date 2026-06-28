package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenderStatusSyncService 标讯状态同步测试")
class TenderStatusSyncServiceTest {

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TenderStatusSyncService service;

    // === 映射规则测试 ===

    @Test
    @DisplayName("BidResultType.WON → Tender.Status.WON")
    void mapWon() {
        assertThat(TenderStatusSyncService.mapToTenderStatus(BidResultType.WON))
                .isEqualTo(Tender.Status.WON);
    }

    @Test
    @DisplayName("BidResultType.LOST → Tender.Status.LOST")
    void mapLost() {
        assertThat(TenderStatusSyncService.mapToTenderStatus(BidResultType.LOST))
                .isEqualTo(Tender.Status.LOST);
    }

    @Test
    @DisplayName("BidResultType.FAILED → Tender.Status.LOST（Tender 无 FAILED，归一）")
    void mapFailed() {
        assertThat(TenderStatusSyncService.mapToTenderStatus(BidResultType.FAILED))
                .isEqualTo(Tender.Status.LOST);
    }

    @Test
    @DisplayName("BidResultType.ABANDONED → Tender.Status.ABANDONED")
    void mapAbandoned() {
        assertThat(TenderStatusSyncService.mapToTenderStatus(BidResultType.ABANDONED))
                .isEqualTo(Tender.Status.ABANDONED);
    }

    // === 同步逻辑测试 ===

    @Test
    @DisplayName("BIDDING 状态标讯 + WON 结果 → 同步为 WON 并发事件")
    void sync_biddingToWon() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.BIDDING).title("测试标讯").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.WON);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.WON);
        verify(tenderRepository).save(tender);
        ArgumentCaptor<TenderStatusChangedEvent> captor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().newStatus()).isEqualTo(Tender.Status.WON);
        assertThat(captor.getValue().oldStatus()).isEqualTo(Tender.Status.BIDDING);
    }

    @Test
    @DisplayName("BIDDING 状态标讯 + FAILED 结果 → 同步为 LOST")
    void sync_biddingFailedToLost() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.BIDDING).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.FAILED);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.LOST);
        verify(tenderRepository).save(tender);
        verify(eventPublisher).publishEvent(any(TenderStatusChangedEvent.class));
    }

    @Test
    @DisplayName("BIDDING 状态标讯 + ABANDONED 结果 → 同步为 ABANDONED")
    void sync_biddingToAbandoned() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.BIDDING).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.ABANDONED);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.ABANDONED);
        verify(tenderRepository).save(tender);
    }

    @Test
    @DisplayName("已是目标状态 → 幂等跳过，不发事件不写库")
    void sync_alreadyInTargetStatus_skip() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.WON).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.WON);

        verify(tenderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("已是其他终态 → 幂等跳过，不抛异常")
    void sync_alreadyInOtherTerminal_skip() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.LOST).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.WON);

        verify(tenderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("tenderId 为 null → 跳过")
    void sync_nullTenderId_skip() {
        service.syncFromProjectResult(null, BidResultType.WON);

        verify(tenderRepository, never()).findById(any());
        verify(tenderRepository, never()).save(any());
    }

    @Test
    @DisplayName("bidResult 为 null → 跳过")
    void sync_nullBidResult_skip() {
        service.syncFromProjectResult(1L, null);

        verify(tenderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("标讯不存在 → 跳过")
    void sync_tenderNotFound_skip() {
        when(tenderRepository.findById(99L)).thenReturn(Optional.empty());

        service.syncFromProjectResult(99L, BidResultType.WON);

        verify(tenderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("TRACKING 状态尝试同步 WON → 强制同步（系统内部绕过状态机）")
    void sync_invalidTransition_forcesSync() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.TRACKING).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.WON);

        // 系统内部同步：非终态标讯直接 setStatus 到目标终态，不抛异常
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.WON);
        verify(tenderRepository).save(tender);
        verify(eventPublisher).publishEvent(any(TenderStatusChangedEvent.class));
    }
}
