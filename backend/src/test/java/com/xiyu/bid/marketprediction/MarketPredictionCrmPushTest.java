package com.xiyu.bid.marketprediction;

import com.xiyu.bid.crm.application.CrmMessageService;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.marketprediction.domain.MarketPredictionPolicy;
import com.xiyu.bid.marketprediction.domain.MarketPredictionResult;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商机预测 CRM 推送最小可感知实现测试。
 *
 * 锁定行为：
 * 1. 预测有效时 → 调用 CrmMessageService.sendMessage 推送到 CRM 事件库
 * 2. 历史数据不足时 → 不推送
 * 3. CRM 调用失败/抛异常时 → 降级返回 false，不传播异常
 */
@ExtendWith(MockitoExtension.class)
class MarketPredictionCrmPushTest {

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private MarketPredictionPolicy predictionPolicy;

    @Mock
    private CrmMessageService crmMessageService;

    @InjectMocks
    private MarketPredictionService predictionService;

    @Test
    void pushPredictionToCrm_whenPredictionValid_sendsMessageToCrm() {
        String hash = "HASH-001";
        List<String> recipients = List.of("admin001");
        List<Tender> tenders = List.of(
                buildTender(LocalDate.of(2026, 1, 1)),
                buildTender(LocalDate.of(2026, 6, 1)),
                buildTender(LocalDate.of(2026, 12, 1)));
        when(tenderRepository.findByPurchaserHashOrderByPublishDateDesc(hash)).thenReturn(tenders);
        when(predictionPolicy.minimumHistoricalCount()).thenReturn(2);
        MarketPredictionResult result = new MarketPredictionResult(
                LocalDate.of(2027, 6, 1), 0.85, "基于 3 条历史数据预测", 3, 180.0);
        when(predictionPolicy.predict(tenders)).thenReturn(result);
        when(crmMessageService.sendMessage(eq(recipients), anyString(), anyString(), anyInt()))
                .thenReturn(new CrmResponseHandler.CrmApiResponse(0, "ok", null, true));

        boolean pushed = predictionService.pushPredictionToCrm(hash, recipients);

        assertThat(pushed).isTrue();
        verify(crmMessageService).sendMessage(
                eq(recipients),
                contains("AI"),
                contains("2027-06-01"),
                anyInt());
    }

    @Test
    void pushPredictionToCrm_whenInsufficientData_doesNotPush() {
        String hash = "HASH-EMPTY";
        when(tenderRepository.findByPurchaserHashOrderByPublishDateDesc(hash)).thenReturn(List.of());
        when(predictionPolicy.minimumHistoricalCount()).thenReturn(3);

        boolean pushed = predictionService.pushPredictionToCrm(hash, List.of("admin001"));

        assertThat(pushed).isFalse();
        verify(crmMessageService, never()).sendMessage(
                anyList(), anyString(), anyString(), anyInt());
    }

    @Test
    void pushPredictionToCrm_whenCrmThrowsException_returnsFalseAndDoesNotPropagate() {
        String hash = "HASH-002";
        List<Tender> tenders = List.of(
                buildTender(LocalDate.of(2026, 1, 1)),
                buildTender(LocalDate.of(2026, 6, 1)));
        when(tenderRepository.findByPurchaserHashOrderByPublishDateDesc(hash)).thenReturn(tenders);
        when(predictionPolicy.minimumHistoricalCount()).thenReturn(2);
        MarketPredictionResult result = new MarketPredictionResult(
                LocalDate.of(2027, 1, 1), 0.7, "基于 2 条历史数据预测", 2, 180.0);
        when(predictionPolicy.predict(tenders)).thenReturn(result);
        when(crmMessageService.sendMessage(anyList(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("CRM service unavailable"));

        boolean pushed = predictionService.pushPredictionToCrm(hash, List.of("admin001"));

        assertThat(pushed).isFalse();
    }

    @Test
    void pushPredictionToCrm_whenCrmReturnsFailure_returnsFalse() {
        String hash = "HASH-003";
        List<Tender> tenders = List.of(
                buildTender(LocalDate.of(2026, 1, 1)),
                buildTender(LocalDate.of(2026, 6, 1)));
        when(tenderRepository.findByPurchaserHashOrderByPublishDateDesc(hash)).thenReturn(tenders);
        when(predictionPolicy.minimumHistoricalCount()).thenReturn(2);
        MarketPredictionResult result = new MarketPredictionResult(
                LocalDate.of(2027, 1, 1), 0.7, "基于 2 条历史数据预测", 2, 180.0);
        when(predictionPolicy.predict(tenders)).thenReturn(result);
        when(crmMessageService.sendMessage(anyList(), anyString(), anyString(), anyInt()))
                .thenReturn(new CrmResponseHandler.CrmApiResponse(500, "internal error", null, false));

        boolean pushed = predictionService.pushPredictionToCrm(hash, List.of("admin001"));

        assertThat(pushed).isFalse();
    }

    @Test
    void pushPredictionToCrm_whenNullHash_returnsFalseWithoutCallingCrm() {
        boolean pushed = predictionService.pushPredictionToCrm(null, List.of("admin001"));

        assertThat(pushed).isFalse();
        verify(crmMessageService, never()).sendMessage(
                anyList(), anyString(), anyString(), anyInt());
    }

    private static Tender buildTender(LocalDate publishDate) {
        Tender tender = new Tender();
        tender.setPublishDate(publishDate);
        return tender;
    }

    private static String contains(String substring) {
        return org.mockito.ArgumentMatchers.contains(substring);
    }
}
