package com.xiyu.bid.ai.service;

import com.xiyu.bid.ai.client.MockAiProvider;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Spy
    private MockAiProvider aiProvider;

    @InjectMocks
    private AiService aiService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("AI分析准确性 - 小额项目验证")
    void analyzeTender_SmallProject_ShouldHaveBaseScore() {
        // Budget 100k (no boost), short content (no boost) -> Base 60
        Tender tender = Tender.builder()
                .id(1L)
                .title("小型标讯")
                .budget(new BigDecimal("100000"))
                .source("来源")
                .build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        AiAnalysisResponse response = aiService.analyzeTenderSync(1L, Map.of("budget", tender.getBudget()));

        // Base 60, variation +/- 10 -> [50, 70]
        assertThat(response.getScore()).isBetween(50, 70);
        assertThat(response.getRiskLevel()).isIn(Tender.RiskLevel.LOW, Tender.RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("AI分析准确性 - 大额且详细项目验证")
    void analyzeTender_LargeAndDetailed_ShouldHaveHighScore() {
        // Budget 1.5M (+15), Long content (> 1000 chars, +10) -> Base 60 + 25 = 85
        StringBuilder longContent = new StringBuilder();
        longContent.append("这是一个非常详细的标讯标题，包含了大量的业务需求和技术规范。".repeat(20)); // ~600 chars
        longContent.append("这里是更多的细节，包括对供应商的资质要求、项目实施的里程碑、以及长期的维护计划和技术支持。".repeat(20)); // ~800 chars
        
        Tender tender = Tender.builder()
                .id(2L)
                .title(longContent.toString())
                .budget(new BigDecimal("1500000"))
                .source("官方平台详细来源说明".repeat(10))
                .build();

        when(tenderRepository.findById(2L)).thenReturn(Optional.of(tender));

        AiAnalysisResponse response = aiService.analyzeTenderSync(2L, Map.of("budget", tender.getBudget()));

        // Base 85, variation +/- 10 -> [75, 95]
        assertThat(response.getScore()).isGreaterThanOrEqualTo(75);
        assertThat(response.getRiskLevel()).isEqualTo(Tender.RiskLevel.LOW);
    }

    @Test
    @DisplayName("AI分析准确性 - 中等规模项目验证")
    void analyzeTender_MediumProject_ShouldHaveMediumBoost() {
        // Budget 600k (+10), short content -> Base 60 + 10 = 70
        Tender tender = Tender.builder()
                .id(3L)
                .title("中型系统集成项目")
                .budget(new BigDecimal("600000"))
                .source("省级采购平台")
                .build();

        when(tenderRepository.findById(3L)).thenReturn(Optional.of(tender));

        AiAnalysisResponse response = aiService.analyzeTenderSync(3L, Map.of("budget", tender.getBudget()));

        // Base 70, variation +/- 10 -> [60, 80]
        assertThat(response.getScore()).isBetween(60, 80);
    }
}
