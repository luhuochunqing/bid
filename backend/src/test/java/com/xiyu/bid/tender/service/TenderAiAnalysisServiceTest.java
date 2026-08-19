package com.xiyu.bid.tender.service;

import com.xiyu.bid.ai.service.AiService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.IntegrationEndpointUrls;
import com.xiyu.bid.integration.external.TenderAttachmentUrlResolver;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenderAiAnalysisServiceTest {

    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private AiService aiService;
    @Mock
    private TenderProjectAccessGuard accessGuard;

    private TenderAiAnalysisService aiAnalysisService;
    private TenderMapper tenderMapper;
    private Tender tender;

    @BeforeEach
    void setUp() {
        tenderMapper = new TenderMapper(new TenderAttachmentUrlResolver(new IntegrationEndpointUrls(""), null));
        aiAnalysisService = new TenderAiAnalysisService(tenderRepository, tenderMapper, accessGuard, aiService);

        tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .budget(new BigDecimal("100.00"))
                .region("上海")
                .industry("制造业")
                .status(Tender.Status.TRACKING)
                .build();
    }

    @Test
    @DisplayName("AI分析标讯 - 携带真实字段上下文")
    void analyzeTender_ShouldCallAiServiceAndUpdate() {
        Tender analyzedTender = Tender.builder()
                .id(1L)
                .title(tender.getTitle())
                .aiScore(85)
                .riskLevel(Tender.RiskLevel.LOW)
                .build();

        when(tenderRepository.findById(1L))
                .thenReturn(Optional.of(tender))
                .thenReturn(Optional.of(analyzedTender));
        when(aiService.analyzeTender(eq(1L), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        TenderDTO result = aiAnalysisService.analyzeTender(1L);

        assertThat(result.getAiScore()).isEqualTo(85);
        assertThat(result.getRiskLevel()).isEqualTo(Tender.RiskLevel.LOW);
        verify(aiService, times(1)).analyzeTender(eq(1L), any());
    }

    @Test
    @DisplayName("AI分析标讯 - 上下文包含必要字段")
    void analyzeTender_ShouldIncludeNecessaryFieldsInContext() {
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(aiService.analyzeTender(eq(1L), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        try {
            aiAnalysisService.analyzeTender(1L);
        } catch (Exception ignored) {
        }

        verify(aiService).analyzeTender(eq(1L), argThat(context ->
                context.containsKey("budget") &&
                context.containsKey("region") &&
                context.containsKey("industry")
        ));
    }
}
