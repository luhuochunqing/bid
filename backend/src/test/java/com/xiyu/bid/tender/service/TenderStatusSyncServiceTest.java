package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    // === 同步逻辑测试（CO-570：项目结果驱动的同步只做状态数据同步，不发布 TenderStatusChangedEvent）===

    @Test
    @DisplayName("BIDDING 状态标讯 + WON 结果 → 同步为 WON（不发事件）")
    void sync_biddingToWon() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.BIDDING).title("测试标讯").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.WON);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.WON);
        verify(tenderRepository).save(tender);
    }

    @Test
    @DisplayName("BIDDING 状态标讯 + FAILED 结果 → 同步为 LOST（不发事件）")
    void sync_biddingFailedToLost() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.BIDDING).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.FAILED);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.LOST);
        verify(tenderRepository).save(tender);
    }

    @Test
    @DisplayName("BIDDING 状态标讯 + ABANDONED 结果 → 同步为 ABANDONED，且不触发标讯级 CRM 回调（CO-570）")
    void sync_biddingToAbandoned() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.BIDDING).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.ABANDONED);

        // 状态确实同步到 ABANDONED（数据一致性兜底）
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.ABANDONED);
        verify(tenderRepository).save(tender);
        // CO-570: 不发布 TenderStatusChangedEvent，CRM 弃标回调由项目级
        // ProjectResultConfirmedWebhookListener 统一承担，避免 WebhookEventListener 重复推送。
        // 本服务已移除 ApplicationEventPublisher 依赖，结构上保证不会发布事件。
    }

    @Test
    @DisplayName("已是目标状态 → 幂等跳过，不写库")
    void sync_alreadyInTargetStatus_skip() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.WON).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.WON);

        verify(tenderRepository, never()).save(any());
    }

    @Test
    @DisplayName("已是其他终态 → 幂等跳过，不抛异常")
    void sync_alreadyInOtherTerminal_skip() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.LOST).title("测试").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        service.syncFromProjectResult(1L, BidResultType.WON);

        verify(tenderRepository, never()).save(any());
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
    }
}
