package com.xiyu.bid.ai.client;

import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.DimensionScore;
import com.xiyu.bid.entity.Tender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Mock AI Provider
 * Provides realistic mock AI analysis responses for testing and development
 * Used as the local development fallback when no real AI key is configured.
 */
@Service
@Slf4j
public class MockAiProvider implements AiProvider {

    private static final List<String> COMMON_STRENGTHS = Arrays.asList(
        "技术团队实力雄厚，具备项目核心能力",
        "报价策略有竞争力，成本控制合理",
        "项目管理经验丰富，交付能力可靠",
        "财务状况稳健，履约保障能力强",
        "行业经验贴合本项目需求",
        "有类似项目优秀交付案例"
    );

    private static final List<String> COMMON_WEAKNESSES = Arrays.asList(
        "缺少同等规模超大型项目的实施经验",
        "项目周期紧张，资源投入需追加保障",
        "部分团队成员尚未取得专项资质认证",
        "距离客户现场较远，驻场成本偏高",
        "在该细分行业的项目储备相对有限"
    );

    private static final List<String> COMMON_RECOMMENDATIONS = Arrays.asList(
        "在标书中突出展示相关行业标杆案例",
        "准备详细的差异化报价与议价策略",
        "与本地合作伙伴联合投标以补齐区域覆盖",
        "投标前完成关键资质认证的补齐",
        "针对紧张工期追加人力与交付冗余",
        "强化团队资质与专家履历的呈现"
    );

    @Override
    public AiAnalysisResponse analyzeTender(String content, Map<String, Object> context) {
        log.debug("Mock AI analyzing tender content length: {}", content != null ? content.length() : 0);

        // Simulate AI processing delay
        simulateProcessingDelay();

        // Generate score based on content and context
        int score = calculateScore(content, context);
        Tender.RiskLevel riskLevel = calculateRiskLevel(score);

        return AiAnalysisResponse.builder()
                .score(score)
                .riskLevel(riskLevel)
                .strengths(selectRandomStrengths(score))
                .weaknesses(selectRandomWeaknesses(score))
                .recommendations(selectRandomRecommendations(score))
                .dimensionScores(generateDimensionScores(score))
                .build();
    }

    @Override
    public AiAnalysisResponse analyzeProject(Long projectId, Map<String, Object> context) {
        log.debug("Mock AI analyzing project id: {}", projectId);

        // Simulate AI processing delay
        simulateProcessingDelay();

        // Generate score based on project context
        int score = calculateProjectScore(context);
        Tender.RiskLevel riskLevel = calculateRiskLevel(score);

        return AiAnalysisResponse.builder()
                .score(score)
                .riskLevel(riskLevel)
                .strengths(selectRandomStrengths(score))
                .weaknesses(selectRandomWeaknesses(score))
                .recommendations(selectRandomRecommendations(score))
                .dimensionScores(generateProjectDimensionScores(score))
                .build();
    }

    /**
     * Calculate score based on content and context
     */
    private int calculateScore(String content, Map<String, Object> context) {
        int baseScore = 60;

        // Adjust based on content length
        if (content != null && !content.isEmpty()) {
            if (content.length() > 1000) {
                baseScore += 10;
            } else if (content.length() > 500) {
                baseScore += 5;
            }
        }

        // Adjust based on context
        if (context != null) {
            Object budget = context.get("budget");
            if (budget instanceof Number) {
                double budgetValue = ((Number) budget).doubleValue();
                if (budgetValue > 1000000) {
                    baseScore += 15;
                } else if (budgetValue > 500000) {
                    baseScore += 10;
                }
            }
        }

        return Math.min(100, Math.max(0, baseScore + getRandomVariation()));
    }

    /**
     * Calculate project-specific score
     */
    private int calculateProjectScore(Map<String, Object> context) {
        int baseScore = 55;

        // Adjust based on context
        if (context != null) {
            Object teamSize = context.get("teamSize");
            if (teamSize instanceof Number) {
                int size = ((Number) teamSize).intValue();
                if (size >= 5) {
                    baseScore += 15;
                } else if (size >= 3) {
                    baseScore += 10;
                }
            }
        }

        return Math.min(100, Math.max(0, baseScore + getRandomVariation()));
    }

    /**
     * Calculate risk level based on score
     */
    private Tender.RiskLevel calculateRiskLevel(int score) {
        if (score >= 70) {
            return Tender.RiskLevel.LOW;
        } else if (score >= 50) {
            return Tender.RiskLevel.MEDIUM;
        } else {
            return Tender.RiskLevel.HIGH;
        }
    }

    /**
     * Select appropriate strengths based on score
     */
    private List<String> selectRandomStrengths(int score) {
        int count = score >= 70 ? 3 : (score >= 50 ? 2 : 1);
        return selectRandomItems(COMMON_STRENGTHS, count);
    }

    /**
     * Select appropriate weaknesses based on score
     */
    private List<String> selectRandomWeaknesses(int score) {
        int count = score >= 70 ? 1 : (score >= 50 ? 2 : 3);
        return selectRandomItems(COMMON_WEAKNESSES, count);
    }

    /**
     * Select appropriate recommendations based on score
     */
    private List<String> selectRandomRecommendations(int score) {
        int count = score >= 70 ? 2 : (score >= 50 ? 3 : 4);
        return selectRandomItems(COMMON_RECOMMENDATIONS, count);
    }

    /**
     * Generate dimension scores for tender analysis
     */
    private List<DimensionScore> generateDimensionScores(int overallScore) {
        int technicalScore = Math.min(100, overallScore + getRandomVariation());
        int financialScore = Math.min(100, overallScore + getRandomVariation());
        int timingScore = Math.min(100, overallScore + getRandomVariation());

        return Arrays.asList(
            DimensionScore.builder()
                    .dimension("技术")
                    .score(normalizeScore(technicalScore))
                    .details("技术能力与专业度综合评估")
                    .build(),
            DimensionScore.builder()
                    .dimension("财务")
                    .score(normalizeScore(financialScore))
                    .details("财务健康度与报价竞争力")
                    .build(),
            DimensionScore.builder()
                    .dimension("进度")
                    .score(normalizeScore(timingScore))
                    .details("项目周期与交付可行性")
                    .build()
        );
    }

    /**
     * Generate dimension scores for project analysis
     */
    private List<DimensionScore> generateProjectDimensionScores(int overallScore) {
        int teamScore = Math.min(100, overallScore + getRandomVariation());
        int resourceScore = Math.min(100, overallScore + getRandomVariation());
        int riskScore = Math.min(100, overallScore + getRandomVariation());

        return Arrays.asList(
            DimensionScore.builder()
                    .dimension("团队")
                    .score(normalizeScore(teamScore))
                    .details("团队配置与协作能力")
                    .build(),
            DimensionScore.builder()
                    .dimension("资源")
                    .score(normalizeScore(resourceScore))
                    .details("资源投入与可用性")
                    .build(),
            DimensionScore.builder()
                    .dimension("风险")
                    .score(normalizeScore(riskScore))
                    .details("风险识别与应对策略")
                    .build()
        );
    }

    /**
     * Select random items from a list
     */
    private List<String> selectRandomItems(List<String> items, int count) {
        if (items.isEmpty() || count <= 0) {
            return List.of();
        }

        int actualCount = Math.min(count, items.size());
        java.util.Collections.shuffle(items);
        return items.subList(0, actualCount);
    }

    /**
     * Get random score variation (-10 to +10)
     */
    private int getRandomVariation() {
        return (int) (Math.random() * 21) - 10;
    }

    /**
     * Normalize score to 0-100 range
     */
    private int normalizeScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Simulate AI processing delay (100-500ms)
     */
    private void simulateProcessingDelay() {
        try {
            int delay = 100 + (int) (Math.random() * 400);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Mock AI processing delay interrupted", e);
        }
    }
}
