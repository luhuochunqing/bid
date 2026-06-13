package com.xiyu.bid.roi.service;

import com.xiyu.bid.roi.dto.SensitivityAnalysisRequest;
import com.xiyu.bid.roi.dto.SensitivityAnalysisResult;
import com.xiyu.bid.roi.entity.ROIAnalysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ROISensitivityCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private ROISensitivityCalculator() {}

    public static SensitivityAnalysisResult calculate(ROIAnalysis base, Long projectId, SensitivityAnalysisRequest request) {
        List<SensitivityAnalysisResult.Scenario> scenarios = new ArrayList<>();
        for (Double costVar : request.getCostVariations()) {
            for (Double revenueVar : request.getRevenueVariations()) {
                scenarios.add(buildScenario(base, costVar, revenueVar));
            }
        }
        scenarios.sort(Comparator.comparing(SensitivityAnalysisResult.Scenario::getAdjustedROI).reversed());
        return SensitivityAnalysisResult.builder()
                .projectId(projectId)
                .baseCost(base.getEstimatedCost()).baseRevenue(base.getEstimatedRevenue())
                .baseProfit(base.getEstimatedProfit()).baseROI(base.getRoiPercentage())
                .scenarios(scenarios).build();
    }

    private static SensitivityAnalysisResult.Scenario buildScenario(ROIAnalysis base, Double costVar, Double revVar) {
        BigDecimal adjCost = base.getEstimatedCost().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(costVar / 100))).setScale(SCALE, ROUNDING);
        BigDecimal adjRev = base.getEstimatedRevenue().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(revVar / 100))).setScale(SCALE, ROUNDING);
        BigDecimal adjProfit = adjRev.subtract(adjCost).setScale(SCALE, ROUNDING);
        BigDecimal adjROI = adjCost.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : adjProfit.divide(adjCost, SCALE, ROUNDING).multiply(BigDecimal.valueOf(100)).setScale(SCALE, ROUNDING);
        return SensitivityAnalysisResult.Scenario.builder()
                .costVariation(costVar).revenueVariation(revVar)
                .adjustedCost(adjCost).adjustedRevenue(adjRev).adjustedProfit(adjProfit).adjustedROI(adjROI)
                .description(describe(costVar, revVar)).build();
    }

    private static String describe(Double costVar, Double revVar) {
        if (costVar == 0 && revVar == 0) return "Base case";
        if (costVar <= 0 && revVar >= 0) return "Best case";
        if (costVar >= 0 && revVar <= 0) return "Worst case";
        return String.format("Cost: %s%.1f%%, Revenue: %s%.1f%%", costVar >= 0 ? "+" : "", costVar, revVar >= 0 ? "+" : "", revVar);
    }
}
