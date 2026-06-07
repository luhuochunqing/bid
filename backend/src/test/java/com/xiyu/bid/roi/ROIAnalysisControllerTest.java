package com.xiyu.bid.roi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.roi.dto.ROIAnalysisCreateRequest;
import com.xiyu.bid.roi.dto.ROIAnalysisDTO;
import com.xiyu.bid.roi.dto.SensitivityAnalysisRequest;
import com.xiyu.bid.roi.dto.SensitivityAnalysisResult;
import com.xiyu.bid.roi.controller.ROIAnalysisController;
import com.xiyu.bid.roi.service.ROIAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * ROI分析控制器测试类
 * 测试HTTP端点的请求处理和响应
 */
@ExtendWith(MockitoExtension.class)
class ROIAnalysisControllerTest {

    @Mock
    private ROIAnalysisService roiAnalysisService;

    @InjectMocks
    private ROIAnalysisController roiAnalysisController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private ROIAnalysisDTO testDTO;
    private ROIAnalysisCreateRequest createRequest;
    private SensitivityAnalysisRequest sensitivityRequest;
    private SensitivityAnalysisResult sensitivityResult;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roiAnalysisController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();

        testDTO = ROIAnalysisDTO.builder()
                .id(1L)
                .projectId(100L)
                .analysisDate(LocalDateTime.of(2024, 3, 1, 10, 0))
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("800000.00"))
                .estimatedProfit(new BigDecimal("300000.00"))
                .roiPercentage(new BigDecimal("60.00"))
                .paybackPeriodMonths(24)
                .riskFactors("Market volatility")
                .assumptions("On time completion")
                .build();

        createRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("800000.00"))
                .paybackPeriodMonths(24)
                .riskFactors("Market volatility")
                .assumptions("On time completion")
                .createdBy(1L)
                .build();

        sensitivityRequest = SensitivityAnalysisRequest.builder()
                .projectId(100L)
                .costVariations(Arrays.asList(-10.0, 0.0, 10.0))
                .revenueVariations(Arrays.asList(-10.0, 0.0, 10.0))
                .build();

        SensitivityAnalysisResult.Scenario scenario = SensitivityAnalysisResult.Scenario.builder()
                .costVariation(0.0)
                .revenueVariation(0.0)
                .adjustedCost(new BigDecimal("500000.00"))
                .adjustedRevenue(new BigDecimal("800000.00"))
                .adjustedProfit(new BigDecimal("300000.00"))
                .adjustedROI(new BigDecimal("60.00"))
                .description("Base case")
                .build();

        sensitivityResult = SensitivityAnalysisResult.builder()
                .projectId(100L)
                .baseCost(new BigDecimal("500000.00"))
                .baseRevenue(new BigDecimal("800000.00"))
                .baseProfit(new BigDecimal("300000.00"))
                .baseROI(new BigDecimal("60.00"))
                .scenarios(Arrays.asList(scenario))
                .build();
    }

    // ==================== GET /api/ai/roi/project/{projectId} Tests ====================

    @Test
    void getAnalysisByProject_WithValidProjectId_ShouldReturn200() throws Exception {
        // Given
        when(roiAnalysisService.getAnalysisByProject(100L)).thenReturn(testDTO);

        // When & Then
        mockMvc.perform(get("/api/ai/roi/project/{projectId}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(100))
                .andExpect(jsonPath("$.data.estimatedCost").value(500000.00))
                .andExpect(jsonPath("$.data.roiPercentage").value(60.00));

        verify(roiAnalysisService).getAnalysisByProject(100L);
    }

    @Test
    void getAnalysisByProject_WithInvalidProjectId_ShouldReturn404() throws Exception {
        // Given
        when(roiAnalysisService.getAnalysisByProject(999L))
                .thenThrow(new ResourceNotFoundException("ROI analysis not found"));

        // When & Then
        mockMvc.perform(get("/api/ai/roi/project/{projectId}", 999L))
                .andExpect(status().isNotFound());

        verify(roiAnalysisService).getAnalysisByProject(999L);
    }

    // ==================== POST /api/ai/roi Tests ====================

    @Test
    void createAnalysis_WithValidData_ShouldReturn201() throws Exception {
        // Given
        when(roiAnalysisService.createAnalysis(any(ROIAnalysisCreateRequest.class))).thenReturn(testDTO);

        // When & Then
        mockMvc.perform(post("/api/ai/roi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(100))
                .andExpect(jsonPath("$.data.roiPercentage").value(60.00));

        verify(roiAnalysisService).createAnalysis(any(ROIAnalysisCreateRequest.class));
    }

    @Test
    void createAnalysis_WithNullProjectId_ShouldReturn400() throws Exception {
        // Given
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(null)
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("800000.00"))
                .createdBy(1L)
                .build();

        // When & Then
        mockMvc.perform(post("/api/ai/roi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(roiAnalysisService, never()).createAnalysis(any());
    }

    @Test
    void createAnalysis_WithNegativeCost_ShouldReturn400() throws Exception {
        // Given
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("-100000.00"))
                .estimatedRevenue(new BigDecimal("800000.00"))
                .createdBy(1L)
                .build();

        // When & Then
        mockMvc.perform(post("/api/ai/roi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(roiAnalysisService, never()).createAnalysis(any());
    }

    // ==================== POST /api/ai/roi/project/{projectId}/calculate Tests ====================

    @Test
    void calculateROI_WithValidProjectId_ShouldReturn200() throws Exception {
        // Given
        when(roiAnalysisService.calculateROI(eq(100L), any(ROIAnalysisCreateRequest.class))).thenReturn(testDTO);

        // When & Then
        mockMvc.perform(post("/api/ai/roi/project/{projectId}/calculate", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(100))
                .andExpect(jsonPath("$.data.roiPercentage").value(60.00));

        verify(roiAnalysisService).calculateROI(eq(100L), any(ROIAnalysisCreateRequest.class));
    }

    @Test
    void calculateROI_WithNullCost_ShouldReturn400() throws Exception {
        // Given
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(null)
                .estimatedRevenue(new BigDecimal("800000.00"))
                .createdBy(1L)
                .build();

        // When & Then
        mockMvc.perform(post("/api/ai/roi/project/{projectId}/calculate", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(roiAnalysisService, never()).calculateROI(any(), any());
    }

    // ==================== POST /api/ai/roi/sensitivity Tests ====================

    @Test
    void performSensitivityAnalysis_WithValidData_ShouldReturn200() throws Exception {
        // Given
        when(roiAnalysisService.performSensitivityAnalysis(eq(100L), any(SensitivityAnalysisRequest.class)))
                .thenReturn(sensitivityResult);

        // When & Then
        mockMvc.perform(post("/api/ai/roi/sensitivity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sensitivityRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(100))
                .andExpect(jsonPath("$.data.scenarios").isArray());

        verify(roiAnalysisService).performSensitivityAnalysis(eq(100L), any(SensitivityAnalysisRequest.class));
    }

    @Test
    void performSensitivityAnalysis_WithEmptyVariations_ShouldReturn400() throws Exception {
        // Given
        SensitivityAnalysisRequest invalidRequest = SensitivityAnalysisRequest.builder()
                .projectId(100L)
                .costVariations(Arrays.asList())
                .revenueVariations(Arrays.asList())
                .build();

        // When & Then
        mockMvc.perform(post("/api/ai/roi/sensitivity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(roiAnalysisService, never()).performSensitivityAnalysis(any(), any());
    }

    @Test
    void performSensitivityAnalysis_WithNonExistentProject_ShouldReturn404() throws Exception {
        // Given
        when(roiAnalysisService.performSensitivityAnalysis(eq(100L), any(SensitivityAnalysisRequest.class)))
                .thenThrow(new ResourceNotFoundException("ROI analysis not found"));

        // When & Then
        mockMvc.perform(post("/api/ai/roi/sensitivity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sensitivityRequest)))
                .andExpect(status().isNotFound());

        verify(roiAnalysisService).performSensitivityAnalysis(eq(100L), any(SensitivityAnalysisRequest.class));
    }
}
