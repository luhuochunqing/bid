package com.xiyu.bid.competitionintel;

import com.xiyu.bid.competitionintel.dto.AnalysisCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitorCreateRequest;
import com.xiyu.bid.competitionintel.entity.CompetitionAnalysis;
import com.xiyu.bid.competitionintel.entity.Competitor;
import com.xiyu.bid.competitionintel.repository.CompetitionAnalysisRepository;
import com.xiyu.bid.competitionintel.repository.CompetitorRepository;
import com.xiyu.bid.competitionintel.service.CompetitionIntelService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
abstract class AbstractCompetitionIntelServiceTest {

    @Mock
    protected CompetitorRepository competitorRepository;

    @Mock
    protected CompetitionAnalysisRepository analysisRepository;

    @Mock
    protected ProjectAccessScopeService projectAccessScopeService;

    protected CompetitionIntelService service;
    protected Competitor testCompetitor;
    protected CompetitionAnalysis testAnalysis;
    protected CompetitorCreateRequest competitorRequest;
    protected AnalysisCreateRequest analysisRequest;

    @BeforeEach
    void setUpCompetitionIntelFixture() {
        service = new CompetitionIntelService(competitorRepository, analysisRepository, projectAccessScopeService);

        testCompetitor = Competitor.builder()
                .id(1L)
                .name("竞企A")
                .industry("建筑业")
                .strengths("资质齐全，技术实力强")
                .weaknesses("报价偏高，响应速度慢")
                .marketShare(new BigDecimal("25.5"))
                .typicalBidRangeMin(new BigDecimal("1000000"))
                .typicalBidRangeMax(new BigDecimal("1500000"))
                .createdAt(LocalDateTime.now())
                .build();

        testAnalysis = CompetitionAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .competitorId(1L)
                .analysisDate(LocalDateTime.now())
                .winProbability(new BigDecimal("65.5"))
                .competitiveAdvantage("资质齐全，类似项目经验丰富")
                .recommendedStrategy("突出技术优势，适当降低报价")
                .riskFactors("对手可能采取低价策略")
                .build();

        competitorRequest = CompetitorCreateRequest.builder()
                .name("竞企B")
                .industry("建筑业")
                .strengths("成本控制好")
                .weaknesses("技术实力一般")
                .marketShare(new BigDecimal("15.0"))
                .typicalBidRangeMin(new BigDecimal("800000"))
                .typicalBidRangeMax(new BigDecimal("1200000"))
                .build();

        analysisRequest = AnalysisCreateRequest.builder()
                .projectId(100L)
                .competitorId(1L)
                .winProbability(new BigDecimal("70.0"))
                .competitiveAdvantage("技术领先")
                .recommendedStrategy("强调创新")
                .riskFactors("价格竞争")
                .build();
    }
}
