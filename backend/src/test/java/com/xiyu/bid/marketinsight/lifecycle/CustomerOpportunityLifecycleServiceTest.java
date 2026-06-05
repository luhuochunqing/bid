package com.xiyu.bid.marketinsight.lifecycle;

import com.xiyu.bid.marketinsight.dto.CustomerPredictionDTO;
import com.xiyu.bid.marketinsight.entity.CustomerPrediction;
import com.xiyu.bid.marketinsight.support.CustomerOpportunityTenderSupport;
import com.xiyu.bid.marketinsight.repository.CustomerPredictionRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOpportunityLifecycleServiceTest {

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private CustomerPredictionRepository customerPredictionRepository;

    @Mock
    private CustomerOpportunityTenderSupport tenderSupport;

    @InjectMocks
    private CustomerOpportunityLifecycleService lifecycleService;

    @Test
    void convertPrediction_shouldPreserveExistingProjectIdWhenCalledRepeatedly() {
        CustomerPrediction prediction = CustomerPrediction.builder()
                .id(77L)
                .purchaserHash("HASH-77")
                .purchaserName("华北集团")
                .status(CustomerPrediction.Status.CONVERTED)
                .convertedProjectId(9001L)
                .build();
        when(customerPredictionRepository.findById(77L)).thenReturn(Optional.of(prediction));

        CustomerPredictionDTO result = lifecycleService.convertPrediction(77L, null);

        verify(customerPredictionRepository, never()).save(prediction);
        assertThat(result.getConvertedProjectId()).isEqualTo(9001L);
        assertThat(result.getCustomerId()).isEqualTo("HASH-77");
    }

    @Test
    void convertPrediction_shouldWriteBackProjectIdForFreshConversion() {
        CustomerPrediction prediction = CustomerPrediction.builder()
                .id(78L)
                .purchaserHash("HASH-78")
                .purchaserName("华东集团")
                .status(CustomerPrediction.Status.RECOMMEND)
                .build();
        when(customerPredictionRepository.findById(78L)).thenReturn(Optional.of(prediction));
        when(customerPredictionRepository.save(prediction)).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerPredictionDTO result = lifecycleService.convertPrediction(78L, 500L);

        verify(customerPredictionRepository).save(prediction);
        assertThat(result.getConvertedProjectId()).isEqualTo(500L);
        assertThat(result.getSuggestedProjectName()).contains("华东集团");
    }

    @Test
    void transitionPrediction_shouldAcceptLowercaseStatusPayload() {
        CustomerPrediction prediction = CustomerPrediction.builder()
                .id(79L)
                .purchaserHash("HASH-79")
                .purchaserName("华南集团")
                .status(CustomerPrediction.Status.WATCH)
                .build();
        when(customerPredictionRepository.findById(79L)).thenReturn(Optional.of(prediction));
        when(customerPredictionRepository.save(prediction)).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerPredictionDTO result = lifecycleService.transitionPrediction(79L, "recommend");

        verify(customerPredictionRepository).save(prediction);
        assertThat(result.getOpportunityId()).isEqualTo(79L);
        assertThat(prediction.getStatus()).isEqualTo(CustomerPrediction.Status.RECOMMEND);
    }

    @Test
    void transitionPrediction_shouldRejectBlankStatusPayload() {
        assertThatThrownBy(() -> lifecycleService.transitionPrediction(79L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status 不能为空");
    }
}
