package com.xiyu.bid.marketinsight.query;

import com.xiyu.bid.marketinsight.dto.CustomerInsightDTO;
import com.xiyu.bid.marketinsight.dto.request.CustomerInsightQuery;
import com.xiyu.bid.marketinsight.entity.CustomerPrediction;
import com.xiyu.bid.marketinsight.mapper.CustomerOpportunitySnapshotMapper;
import com.xiyu.bid.marketinsight.repository.CustomerPredictionRepository;
import com.xiyu.bid.marketinsight.support.CustomerOpportunityTenderSupport;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOpportunityQueryServiceTest {

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private CustomerPredictionRepository customerPredictionRepository;

    @Mock
    private CustomerOpportunityTenderSupport tenderSupport;

    @InjectMocks
    private CustomerOpportunityQueryService queryService;

    @Test
    void getCustomerInsights_shouldFilterByStatusAndSortByScoreDescending() {
        CustomerPrediction recommend = prediction(10L, "华东智造", CustomerPrediction.Status.RECOMMEND, 92, "A 省", "制造", "李经理");
        CustomerPrediction watch = prediction(11L, "华南能源", CustomerPrediction.Status.WATCH, 60, "B 省", "能源", "王经理");
        when(customerPredictionRepository.findByStatus(CustomerPrediction.Status.RECOMMEND)).thenReturn(List.of(watch, recommend));

        CustomerInsightQuery query = new CustomerInsightQuery();
        query.setStatus("recommend");
        query.setKeyword("华东");
        query.setRegion("A 省");
        query.setIndustry("制造");
        query.setSalesRep("李经理");

        List<CustomerInsightDTO> result = queryService.getCustomerInsights(query);

        verify(customerPredictionRepository).findByStatus(CustomerPrediction.Status.RECOMMEND);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerName()).isEqualTo("华东智造");
        assertThat(result.get(0).getOpportunityScore()).isEqualTo(92);
        assertThat(result.get(0).getStatus()).isEqualTo("recommend");
    }

    @Test
    void getCustomerPurchases_shouldMapSnapshotBackedPurchaseRecords() {
        Tender tender = Tender.builder()
                .id(11L)
                .title("国网江苏省电力办公设备采购项目")
                .build();
        when(tenderRepository.findAll()).thenReturn(List.of(tender));
        when(tenderSupport.createSnapshots(List.of(tender))).thenReturn(List.of(
                new com.xiyu.bid.marketinsight.core.CustomerOpportunityTenderSnapshot(
                        11L,
                        "国网江苏省电力办公设备采购项目",
                        "国网江苏省电力",
                        "hash-1",
                        "能源电力",
                        new BigDecimal("1280000"),
                        LocalDateTime.of(2026, 4, 10, 9, 0))));

        var purchases = queryService.getCustomerPurchases("hash-1");

        assertThat(purchases).singleElement().satisfies(item -> {
            assertThat(item.getRecordId()).isEqualTo(11L);
            assertThat(item.getCustomerId()).isEqualTo("hash-1");
            assertThat(item.getPublishDate()).isEqualTo("2026-04-10");
            assertThat(item.getBudget()).isEqualTo(128L);
            assertThat(item.isKey()).isTrue();
        });
    }

    private CustomerPrediction prediction(Long id, String name, CustomerPrediction.Status status, int score, String region, String industry, String salesRep) {
        return CustomerPrediction.builder()
                .id(id)
                .purchaserHash("HASH-" + id)
                .purchaserName(name)
                .region(region)
                .industry(industry)
                .salesRep(salesRep)
                .opportunityScore(score)
                .status(status)
                .build();
    }
}
