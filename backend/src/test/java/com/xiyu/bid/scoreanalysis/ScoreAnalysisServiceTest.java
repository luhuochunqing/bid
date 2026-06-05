package com.xiyu.bid.scoreanalysis;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisDTO;
import com.xiyu.bid.scoreanalysis.dto.DimensionScoreDTO;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisCreateRequest;
import com.xiyu.bid.scoreanalysis.entity.ScoreAnalysis;
import com.xiyu.bid.scoreanalysis.entity.DimensionScore;
import com.xiyu.bid.scoreanalysis.repository.ScoreAnalysisRepository;
import com.xiyu.bid.scoreanalysis.repository.DimensionScoreRepository;
import com.xiyu.bid.scoreanalysis.service.ScoreAnalysisService;
import com.xiyu.bid.service.ProjectAccessScopeService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ScoreAnalysis Service单元测试
 * 测试评分分析服务的业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreAnalysis Service测试")
class ScoreAnalysisServiceTest {

    @Mock
    private ScoreAnalysisRepository scoreAnalysisRepository;

    @Mock
    private DimensionScoreRepository dimensionScoreRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private com.xiyu.bid.tender.service.TenderCommandService tenderCommandService;

    @Mock
    private com.xiyu.bid.scoreanalysis.core.ScoreAnalysisCalculationPolicy calculationPolicy;

    @Mock
    private com.xiyu.bid.scoreanalysis.service.ScoreAnalysisQueryService queryService;

    @InjectMocks
    private ScoreAnalysisService scoreAnalysisService;

    private ScoreAnalysis testAnalysis;
    private DimensionScore testDimension;
    private ScoreAnalysisCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        testDimension = DimensionScore.builder()
                .id(1L)
                .analysisId(1L)
                .dimensionName("技术能力")
                .score(90)
                .weight(new BigDecimal("0.30"))
                .comments("技术团队经验丰富")
                .build();

        testAnalysis = ScoreAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .analysisDate(LocalDateTime.now())
                .overallScore(85)
                .riskLevel(RiskLevel.LOW)
                .analystId(10L)
                .isAiGenerated(true)
                .summary("优秀的技术方案")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<DimensionScoreDTO> dimensions = Arrays.asList(
                DimensionScoreDTO.builder()
                        .dimensionName("技术能力")
                        .score(90)
                        .weight(new BigDecimal("0.30"))
                        .build(),
                DimensionScoreDTO.builder()
                        .dimensionName("财务实力")
                        .score(85)
                        .weight(new BigDecimal("0.25"))
                        .build()
        );

        createRequest = ScoreAnalysisCreateRequest.builder()
                .projectId(100L)
                .analystId(10L)
                .isAiGenerated(true)
                .summary("综合评估优秀")
                .dimensions(dimensions)
                .build();
    }

    @Test
    @DisplayName("应该成功创建评分分析")
    void shouldCreateAnalysisSuccessfully() {
        // Given
        when(calculationPolicy.calculateWeightedScoreFromDTOs(any())).thenReturn(new BigDecimal("85"));
        when(calculationPolicy.determineRiskLevel(85)).thenReturn(RiskLevel.LOW);
        when(scoreAnalysisRepository.save(any(ScoreAnalysis.class))).thenReturn(testAnalysis);
        when(queryService.convertToDTO(any())).thenReturn(ScoreAnalysisDTO.builder()
                .projectId(100L)
                .build());

        // When
        ApiResponse<ScoreAnalysisDTO> response = scoreAnalysisService.createAnalysis(createRequest);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(100L, response.getData().getProjectId());
        verify(scoreAnalysisRepository, times(1)).save(any(ScoreAnalysis.class));
    }

    @Test
    @DisplayName("应该获取项目的评分分析")
    void shouldGetAnalysisByProjectSuccessfully() {
        // Given
        ScoreAnalysisDTO expectedDto = ScoreAnalysisDTO.builder()
                .projectId(100L)
                .overallScore(85)
                .build();
        when(queryService.getAnalysisByProject(100L))
                .thenReturn(ApiResponse.success("获取评分分析成功", expectedDto));

        // When
        ApiResponse<ScoreAnalysisDTO> response = scoreAnalysisService.getAnalysisByProject(100L);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(100L, response.getData().getProjectId());
        assertEquals(85, response.getData().getOverallScore());
    }

    @Test
    @DisplayName("应该获取项目不存在的分析时返回错误")
    void shouldReturnErrorWhenAnalysisNotFound() {
        // Given
        when(queryService.getAnalysisByProject(999L))
                .thenReturn(ApiResponse.error("未找到项目的评分分析"));

        // When
        ApiResponse<ScoreAnalysisDTO> response = scoreAnalysisService.getAnalysisByProject(999L);

        // Then
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("未找到项目的评分分析", response.getMessage());
    }
}
