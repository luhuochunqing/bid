package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderAbandonRequest;
import com.xiyu.bid.tender.dto.TenderBidResponse;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenderSubmissionServiceTest {

    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenderAssignmentPermissions permissions;
    @Mock
    private TenderProjectAccessGuard accessGuard;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private com.xiyu.bid.notification.service.NotificationApplicationService notificationAppService;

    private TenderSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new TenderSubmissionService(
                tenderRepository, tenderEvaluationRepository,
                userRepository, permissions, accessGuard,
                objectMapper, eventPublisher, notificationAppService);
    }

    @Test
    @DisplayName("投标 - 成功投标")
    void participateBid_Success() {
        Tender pendingTender = Tender.builder()
                .id(1L).title("测试标讯").budget(new BigDecimal("100.00")).status(Tender.Status.PENDING_ASSIGNMENT).build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(pendingTender));
        doNothing().when(accessGuard).assertCanAccessTender(any());
        when(permissions.canDecide(1L, 10L)).thenReturn(true);
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));

        TenderBidResponse response = submissionService.participateBid(1L, 10L);

        assertThat(response.isAccepted()).isTrue();
        assertThat(response.getMessage()).isEqualTo("投标成功");
        assertThat(response.getTodoId()).isNull();
    }

    @Test
    @DisplayName("投标 - 已投标状态返回失败")
    void participateBid_AlreadyBidded() {
        Tender biddenTender = Tender.builder().id(1L).title("测试标讯").status(Tender.Status.BIDDING).build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(biddenTender));
        doNothing().when(accessGuard).assertCanAccessTender(any());
        when(permissions.canDecide(1L, 10L)).thenReturn(true);

        TenderBidResponse response = submissionService.participateBid(1L, 10L);

        assertThat(response.isAccepted()).isFalse();
        assertThat(response.getMessage()).isEqualTo("该标讯已投标");
    }

    @Test
    @DisplayName("弃标 - 成功弃标")
    void abandonBid_Success() {
        Tender trackingTender = Tender.builder().id(1L).title("测试标讯").status(Tender.Status.TRACKING).build();
        TenderAbandonRequest req = TenderAbandonRequest.builder().reason("预算超出预期").build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(trackingTender));
        doNothing().when(accessGuard).assertCanAccessTender(any());
        when(permissions.canDecide(1L, 10L)).thenReturn(true);
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));

        TenderBidResponse response = submissionService.abandonBid(1L, req, 10L);

        assertThat(response.isAccepted()).isTrue();
        assertThat(response.getMessage()).isEqualTo("已放弃该标讯");
    }

    @Test
    @DisplayName("弃标 - 已弃标状态返回失败")
    void abandonBid_AlreadyAbandoned() {
        Tender abandonedTender = Tender.builder().id(1L).title("测试标讯").status(Tender.Status.ABANDONED).build();
        TenderAbandonRequest req = TenderAbandonRequest.builder().reason("测试原因").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(abandonedTender));
        doNothing().when(accessGuard).assertCanAccessTender(any());
        when(permissions.canDecide(1L, 10L)).thenReturn(true);

        TenderBidResponse response = submissionService.abandonBid(1L, req, 10L);

        assertThat(response.isAccepted()).isFalse();
        assertThat(response.getMessage()).isEqualTo("该标讯已放弃");
    }
}
