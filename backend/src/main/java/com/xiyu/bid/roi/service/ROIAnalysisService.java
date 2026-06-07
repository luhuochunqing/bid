// Input: ROI repositories, DTOs, and support services
// Output: R O I Analysis business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.roi.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.roi.dto.ROIAnalysisCreateRequest;
import com.xiyu.bid.roi.dto.ROIAnalysisDTO;
import com.xiyu.bid.roi.dto.SensitivityAnalysisRequest;
import com.xiyu.bid.roi.dto.SensitivityAnalysisResult;
import com.xiyu.bid.roi.entity.ROIAnalysis;
import com.xiyu.bid.roi.repository.ROIAnalysisRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ROI分析服务
 * 提供ROI计算、敏感性分析等功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ROIAnalysisService {

    private final ROIAnalysisRepository roiAnalysisRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final int MAX_TEXT_FIELD_LENGTH = 5000;

    /**
     * 创建ROI分析
     * @param request 创建请求
     * @return 创建的ROI分析
     */
    @Auditable(
        action = "CREATE",
        entityType = "ROI_ANALYSIS",
        description = "Create ROI analysis"
    )
    @Transactional
    public ROIAnalysisDTO createAnalysis(ROIAnalysisCreateRequest request) {
        log.info("Creating ROI analysis for project: {}", request.getProjectId());

        // 验证输入
        validateRequest(request);
        projectAccessScopeService.assertCurrentUserCanAccessProject(request.getProjectId());

        // 构建并保存分析实体
        ROIAnalysis analysis = buildAnalysisEntity(request.getProjectId(), request);
        ROIAnalysis saved = roiAnalysisRepository.save(analysis);

        log.info("ROI analysis created successfully with ID: {}, ROI: {}%",
                saved.getId(), saved.getRoiPercentage());

        return toDTO(saved);
    }

    /**
     * 根据项目ID获取ROI分析
     * @param projectId 项目ID
     * @return ROI分析数据
     */
    public ROIAnalysisDTO getAnalysisByProject(Long projectId) {
        log.info("Fetching ROI analysis for project: {}", projectId);

        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        ROIAnalysis analysis = roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ROI analysis not found for project: " + projectId));

        return toDTO(analysis);
    }

    /**
     * 计算项目ROI
     * @param projectId 项目ID
     * @param request ROI计算请求
     * @return 计算后的ROI分析
     */
    @Auditable(
        action = "CALCULATE",
        entityType = "ROI_ANALYSIS",
        description = "Calculate ROI for project"
    )
    @Transactional
    public ROIAnalysisDTO calculateROI(Long projectId, ROIAnalysisCreateRequest request) {
        log.info("Calculating ROI for project: {}", projectId);

        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        // 验证输入
        validateRequest(request);

        // 检查是否已存在分析
        ROIAnalysis existingAnalysis = roiAnalysisRepository
                .findFirstByProjectIdOrderByAnalysisDateDesc(projectId)
                .orElse(null);

        ROIAnalysis analysis;
        if (existingAnalysis != null) {
            // 更新现有分析
            analysis = updateAnalysisEntity(existingAnalysis, request);
            analysis = roiAnalysisRepository.save(analysis);
            log.info("Updated existing ROI analysis for project: {}", projectId);
        } else {
            // 创建新分析
            analysis = buildAnalysisEntity(projectId, request);
            analysis = roiAnalysisRepository.save(analysis);
            log.info("Created new ROI analysis for project: {}", projectId);
        }

        return toDTO(analysis);
    }

    /**
     * 执行敏感性分析
     * @param projectId 项目ID
     * @param request 敏感性分析请求
     * @return 敏感性分析结果
     */
    public SensitivityAnalysisResult performSensitivityAnalysis(Long projectId, SensitivityAnalysisRequest request) {
        log.info("Performing sensitivity analysis for project: {}", projectId);

        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Sensitivity analysis request cannot be null");
        }
        if (request.getCostVariations() == null || request.getCostVariations().isEmpty() ||
            request.getRevenueVariations() == null || request.getRevenueVariations().isEmpty()) {
            throw new IllegalArgumentException("Cost and revenue variations cannot be empty");
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        // 获取基础ROI分析
        ROIAnalysis baseAnalysis = roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ROI analysis not found for project: " + projectId));

        // 生成所有场景
        List<SensitivityAnalysisResult.Scenario> scenarios = new ArrayList<>();
        for (Double costVar : request.getCostVariations()) {
            for (Double revenueVar : request.getRevenueVariations()) {
                scenarios.add(calculateScenario(baseAnalysis, costVar, revenueVar));
            }
        }

        // 按ROI排序场景
        scenarios.sort(Comparator.comparing(SensitivityAnalysisResult.Scenario::getAdjustedROI).reversed());

        return SensitivityAnalysisResult.builder()
                .projectId(projectId)
                .baseCost(baseAnalysis.getEstimatedCost())
                .baseRevenue(baseAnalysis.getEstimatedRevenue())
                .baseProfit(baseAnalysis.getEstimatedProfit())
                .baseROI(baseAnalysis.getRoiPercentage())
                .scenarios(scenarios)
                .build();
    }

    /**
     * 计算单个敏感性分析场景
     */
    private SensitivityAnalysisResult.Scenario calculateScenario(
            ROIAnalysis baseAnalysis, Double costVariation, Double revenueVariation) {

        // 计算调整后的成本和收入
        BigDecimal adjustedCost = baseAnalysis.getEstimatedCost()
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(costVariation / 100)))
                .setScale(SCALE, ROUNDING_MODE);

        BigDecimal adjustedRevenue = baseAnalysis.getEstimatedRevenue()
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(revenueVariation / 100)))
                .setScale(SCALE, ROUNDING_MODE);

        // 计算调整后的利润和ROI
        BigDecimal adjustedProfit = calculateProfit(adjustedCost, adjustedRevenue);
        BigDecimal adjustedROI = calculateROI(adjustedCost, adjustedProfit);

        // 生成场景描述
        String description = generateScenarioDescription(costVariation, revenueVariation);

        return SensitivityAnalysisResult.Scenario.builder()
                .costVariation(costVariation)
                .revenueVariation(revenueVariation)
                .adjustedCost(adjustedCost)
                .adjustedRevenue(adjustedRevenue)
                .adjustedProfit(adjustedProfit)
                .adjustedROI(adjustedROI)
                .description(description)
                .build();
    }

    /**
     * 生成场景描述
     */
    private String generateScenarioDescription(Double costVariation, Double revenueVariation) {
        if (costVariation == 0 && revenueVariation == 0) {
            return "Base case";
        } else if (costVariation <= 0 && revenueVariation >= 0) {
            return "Best case";
        } else if (costVariation >= 0 && revenueVariation <= 0) {
            return "Worst case";
        } else {
            return String.format("Cost: %s%.1f%%, Revenue: %s%.1f%%",
                    costVariation >= 0 ? "+" : "", costVariation,
                    revenueVariation >= 0 ? "+" : "", revenueVariation);
        }
    }

    /**
     * 计算利润
     * Profit = Revenue - Cost
     */
    private BigDecimal calculateProfit(BigDecimal cost, BigDecimal revenue) {
        return revenue.subtract(cost).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 计算ROI百分比
     * ROI% = (Profit / Cost) * 100
     */
    private BigDecimal calculateROI(BigDecimal cost, BigDecimal profit) {
        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return profit.divide(cost, SCALE, ROUNDING_MODE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 构建ROI分析实体（用于创建新分析）
     */
    private ROIAnalysis buildAnalysisEntity(Long projectId, ROIAnalysisCreateRequest request) {
        BigDecimal profit = calculateProfit(request.getEstimatedCost(), request.getEstimatedRevenue());
        BigDecimal roiPercentage = calculateROI(request.getEstimatedCost(), profit);

        return ROIAnalysis.builder()
                .projectId(projectId)
                .estimatedCost(request.getEstimatedCost())
                .estimatedRevenue(request.getEstimatedRevenue())
                .estimatedProfit(profit)
                .roiPercentage(roiPercentage)
                .paybackPeriodMonths(request.getPaybackPeriodMonths())
                .riskFactors(request.getRiskFactors())
                .assumptions(request.getAssumptions())
                .createdBy(request.getCreatedBy())
                .build();
    }

    /**
     * 更新ROI分析实体（用于更新现有分析）
     */
    private ROIAnalysis updateAnalysisEntity(ROIAnalysis existingAnalysis, ROIAnalysisCreateRequest request) {
        BigDecimal profit = calculateProfit(request.getEstimatedCost(), request.getEstimatedRevenue());
        BigDecimal roiPercentage = calculateROI(request.getEstimatedCost(), profit);

        existingAnalysis.setEstimatedCost(request.getEstimatedCost());
        existingAnalysis.setEstimatedRevenue(request.getEstimatedRevenue());
        existingAnalysis.setEstimatedProfit(profit);
        existingAnalysis.setRoiPercentage(roiPercentage);
        existingAnalysis.setPaybackPeriodMonths(request.getPaybackPeriodMonths());
        existingAnalysis.setRiskFactors(request.getRiskFactors());
        existingAnalysis.setAssumptions(request.getAssumptions());

        return existingAnalysis;
    }

    /**
     * 验证请求参数
     */
    private void validateRequest(ROIAnalysisCreateRequest request) {
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        if (request.getEstimatedCost() == null) {
            throw new IllegalArgumentException("Estimated cost is required");
        }
        if (request.getEstimatedCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Estimated cost must be greater than zero");
        }
        if (request.getEstimatedRevenue() == null) {
            throw new IllegalArgumentException("Estimated revenue is required");
        }
        if (request.getEstimatedRevenue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Estimated revenue cannot be negative");
        }
        if (request.getRiskFactors() != null && request.getRiskFactors().length() > MAX_TEXT_FIELD_LENGTH) {
            throw new IllegalArgumentException(
                "Risk factors length exceeds maximum allowed length of " + MAX_TEXT_FIELD_LENGTH + " characters");
        }
        if (request.getAssumptions() != null && request.getAssumptions().length() > MAX_TEXT_FIELD_LENGTH) {
            throw new IllegalArgumentException(
                "Assumptions length exceeds maximum allowed length of " + MAX_TEXT_FIELD_LENGTH + " characters");
        }
    }

    /**
     * 转换实体为DTO
     */
    private ROIAnalysisDTO toDTO(ROIAnalysis analysis) {
        return ROIAnalysisDTO.builder()
                .id(analysis.getId())
                .projectId(analysis.getProjectId())
                .analysisDate(analysis.getAnalysisDate())
                .estimatedCost(analysis.getEstimatedCost())
                .estimatedRevenue(analysis.getEstimatedRevenue())
                .estimatedProfit(analysis.getEstimatedProfit())
                .roiPercentage(analysis.getRoiPercentage())
                .paybackPeriodMonths(analysis.getPaybackPeriodMonths())
                .riskFactors(analysis.getRiskFactors())
                .assumptions(analysis.getAssumptions())
                .build();
    }
}
