package com.xiyu.bid.competitionintel;

import com.xiyu.bid.competitionintel.dto.CompetitorCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitorDTO;
import com.xiyu.bid.competitionintel.entity.Competitor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionIntelCompetitorServiceTest extends AbstractCompetitionIntelServiceTest {

    @Test
    void createCompetitor_WithValidData_ShouldReturnSavedCompetitor() {
        Competitor savedCompetitor = Competitor.builder()
                .id(2L)
                .name(competitorRequest.getName())
                .industry(competitorRequest.getIndustry())
                .strengths(competitorRequest.getStrengths())
                .weaknesses(competitorRequest.getWeaknesses())
                .marketShare(competitorRequest.getMarketShare())
                .typicalBidRangeMin(competitorRequest.getTypicalBidRangeMin())
                .typicalBidRangeMax(competitorRequest.getTypicalBidRangeMax())
                .createdAt(LocalDateTime.now())
                .build();
        when(competitorRepository.save(any(Competitor.class))).thenReturn(savedCompetitor);

        CompetitorDTO result = service.createCompetitor(competitorRequest);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getName()).isEqualTo("竞企B");
        assertThat(result.getIndustry()).isEqualTo("建筑业");
        assertThat(result.getMarketShare()).isEqualByComparingTo("15.0");
        verify(competitorRepository).save(any(Competitor.class));
    }

    @Test
    void createCompetitor_WithNullName_ShouldThrowException() {
        CompetitorCreateRequest invalidRequest = CompetitorCreateRequest.builder()
                .name(null)
                .industry("建筑业")
                .build();

        assertThatThrownBy(() -> service.createCompetitor(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Competitor name is required");

        verify(competitorRepository, never()).save(any(Competitor.class));
    }

    @Test
    void createCompetitor_WithEmptyName_ShouldThrowException() {
        CompetitorCreateRequest invalidRequest = CompetitorCreateRequest.builder()
                .name("")
                .industry("建筑业")
                .build();

        assertThatThrownBy(() -> service.createCompetitor(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Competitor name is required");

        verify(competitorRepository, never()).save(any(Competitor.class));
    }

    @Test
    void createCompetitor_WithNegativeMarketShare_ShouldThrowException() {
        CompetitorCreateRequest invalidRequest = CompetitorCreateRequest.builder()
                .name("竞企C")
                .marketShare(new BigDecimal("-10.0"))
                .build();

        assertThatThrownBy(() -> service.createCompetitor(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Market share cannot be negative");

        verify(competitorRepository, never()).save(any(Competitor.class));
    }

    @Test
    void createCompetitor_WithMarketShareOver100_ShouldThrowException() {
        CompetitorCreateRequest invalidRequest = CompetitorCreateRequest.builder()
                .name("竞企C")
                .marketShare(new BigDecimal("150.0"))
                .build();

        assertThatThrownBy(() -> service.createCompetitor(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Market share cannot exceed 100");

        verify(competitorRepository, never()).save(any(Competitor.class));
    }

    @Test
    void createCompetitor_WithInvalidBidRange_ShouldThrowException() {
        CompetitorCreateRequest invalidRequest = CompetitorCreateRequest.builder()
                .name("竞企C")
                .typicalBidRangeMin(new BigDecimal("2000000"))
                .typicalBidRangeMax(new BigDecimal("1000000"))
                .build();

        assertThatThrownBy(() -> service.createCompetitor(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bid range minimum cannot be greater than maximum");

        verify(competitorRepository, never()).save(any(Competitor.class));
    }

    @Test
    void createCompetitor_WithNegativeBidRange_ShouldThrowException() {
        CompetitorCreateRequest invalidRequest = CompetitorCreateRequest.builder()
                .name("竞企C")
                .typicalBidRangeMin(new BigDecimal("-1000"))
                .build();

        assertThatThrownBy(() -> service.createCompetitor(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bid range values cannot be negative");

        verify(competitorRepository, never()).save(any(Competitor.class));
    }

    @Test
    void getAllCompetitors_ShouldReturnListOfCompetitors() {
        Competitor competitor2 = Competitor.builder()
                .id(2L)
                .name("竞企B")
                .industry("制造业")
                .build();
        when(competitorRepository.findAll()).thenReturn(Arrays.asList(testCompetitor, competitor2));

        List<CompetitorDTO> result = service.getAllCompetitors();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("竞企A");
        assertThat(result.get(1).getName()).isEqualTo("竞企B");
        verify(competitorRepository).findAll();
    }

    @Test
    void getAllCompetitors_WithEmptyResult_ShouldReturnEmptyList() {
        when(competitorRepository.findAll()).thenReturn(List.of());

        List<CompetitorDTO> result = service.getAllCompetitors();

        assertThat(result).isEmpty();
        verify(competitorRepository).findAll();
    }
}
