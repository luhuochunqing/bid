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

@Service
@RequiredArgsConstructor
@Slf4j
public class ROIAnalysisService {

    private final ROIAnalysisRepository roiAnalysisRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final int MAX_TEXT_FIELD_LENGTH = 5000;

    @Auditable(action = "CREATE", entityType = "ROI_ANALYSIS", description = "Create ROI analysis")
    @Transactional
    public ROIAnalysisDTO createAnalysis(ROIAnalysisCreateRequest request) {
        log.info("Creating ROI analysis for project: {}", request.getProjectId());
        validateRequest(request);
        projectAccessScopeService.assertCurrentUserCanAccessProject(request.getProjectId());
        ROIAnalysis saved = roiAnalysisRepository.save(buildAnalysisEntity(request.getProjectId(), request));
        log.info("ROI analysis created: id={}, ROI={}%", saved.getId(), saved.getRoiPercentage());
        return toDTO(saved);
    }

    public ROIAnalysisDTO getAnalysisByProject(Long projectId) {
        if (projectId == null) throw new IllegalArgumentException("Project ID cannot be null");
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return toDTO(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ROIAnalysis", String.valueOf(projectId))));
    }

    @Auditable(action = "CALCULATE", entityType = "ROI_ANALYSIS", description = "Calculate ROI for project")
    @Transactional
    public ROIAnalysisDTO calculateROI(Long projectId, ROIAnalysisCreateRequest request) {
        if (projectId == null) throw new IllegalArgumentException("Project ID cannot be null");
        if (request == null) throw new IllegalArgumentException("Request cannot be null");
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        validateRequest(request);
        ROIAnalysis existing = roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId).orElse(null);
        ROIAnalysis analysis = existing != null ? updateAnalysisEntity(existing, request) : buildAnalysisEntity(projectId, request);
        return toDTO(roiAnalysisRepository.save(analysis));
    }

    public SensitivityAnalysisResult performSensitivityAnalysis(Long projectId, SensitivityAnalysisRequest request) {
        if (projectId == null) throw new IllegalArgumentException("Project ID cannot be null");
        if (request == null) throw new IllegalArgumentException("Sensitivity analysis request cannot be null");
        if (request.getCostVariations() == null || request.getCostVariations().isEmpty() ||
            request.getRevenueVariations() == null || request.getRevenueVariations().isEmpty()) {
            throw new IllegalArgumentException("Cost and revenue variations cannot be empty");
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ROIAnalysis base = roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ROI analysis not found for project: " + projectId));
        return ROISensitivityCalculator.calculate(base, projectId, request);
    }

    private ROIAnalysis buildAnalysisEntity(Long projectId, ROIAnalysisCreateRequest request) {
        BigDecimal profit = request.getEstimatedRevenue().subtract(request.getEstimatedCost()).setScale(SCALE, ROUNDING_MODE);
        BigDecimal roi = request.getEstimatedCost().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : profit.divide(request.getEstimatedCost(), SCALE, ROUNDING_MODE).multiply(BigDecimal.valueOf(100)).setScale(SCALE, ROUNDING_MODE);
        return ROIAnalysis.builder().projectId(projectId).estimatedCost(request.getEstimatedCost()).estimatedRevenue(request.getEstimatedRevenue())
                .estimatedProfit(profit).roiPercentage(roi).paybackPeriodMonths(request.getPaybackPeriodMonths())
                .riskFactors(request.getRiskFactors()).assumptions(request.getAssumptions()).createdBy(request.getCreatedBy()).build();
    }

    private ROIAnalysis updateAnalysisEntity(ROIAnalysis existing, ROIAnalysisCreateRequest request) {
        BigDecimal profit = request.getEstimatedRevenue().subtract(request.getEstimatedCost()).setScale(SCALE, ROUNDING_MODE);
        BigDecimal roi = request.getEstimatedCost().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : profit.divide(request.getEstimatedCost(), SCALE, ROUNDING_MODE).multiply(BigDecimal.valueOf(100)).setScale(SCALE, ROUNDING_MODE);
        existing.setEstimatedCost(request.getEstimatedCost()); existing.setEstimatedRevenue(request.getEstimatedRevenue());
        existing.setEstimatedProfit(profit); existing.setRoiPercentage(roi);
        existing.setPaybackPeriodMonths(request.getPaybackPeriodMonths());
        existing.setRiskFactors(request.getRiskFactors()); existing.setAssumptions(request.getAssumptions());
        return existing;
    }

    private void validateRequest(ROIAnalysisCreateRequest request) {
        if (request.getProjectId() == null) throw new IllegalArgumentException("Project ID is required");
        if (request.getEstimatedCost() == null || request.getEstimatedCost().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Estimated cost must be greater than zero");
        if (request.getEstimatedRevenue() == null || request.getEstimatedRevenue().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Estimated revenue cannot be negative");
        if (request.getRiskFactors() != null && request.getRiskFactors().length() > MAX_TEXT_FIELD_LENGTH)
            throw new IllegalArgumentException("Risk factors exceeds " + MAX_TEXT_FIELD_LENGTH + " characters");
        if (request.getAssumptions() != null && request.getAssumptions().length() > MAX_TEXT_FIELD_LENGTH)
            throw new IllegalArgumentException("Assumptions exceeds " + MAX_TEXT_FIELD_LENGTH + " characters");
    }

    private ROIAnalysisDTO toDTO(ROIAnalysis a) {
        return ROIAnalysisDTO.builder().id(a.getId()).projectId(a.getProjectId()).analysisDate(a.getAnalysisDate())
                .estimatedCost(a.getEstimatedCost()).estimatedRevenue(a.getEstimatedRevenue()).estimatedProfit(a.getEstimatedProfit())
                .roiPercentage(a.getRoiPercentage()).paybackPeriodMonths(a.getPaybackPeriodMonths())
                .riskFactors(a.getRiskFactors()).assumptions(a.getAssumptions()).build();
    }
}
