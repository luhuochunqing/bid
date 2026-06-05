package com.xiyu.bid.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险评估DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentDTO {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 风险分数（0-100）
     */
    private Integer riskScore;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;

    /**
     * 风险描述
     */
    private String description;

    /**
     * 建议措施
     */
    private String recommendation;

    /**
     * 评估时间
     */
    private LocalDateTime assessedAt;

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        LOW(0, 30, "低风险"),
        MEDIUM(30, 60, "中等风险"),
        HIGH(60, 100, "高风险");

        private final int minScore;
        private final int maxScore;
        private final String description;

        RiskLevel(int pMinScore, int pMaxScore, String pDescription) {
            this.minScore = pMinScore;
            this.maxScore = pMaxScore;
            this.description = pDescription;
        }

        public static RiskLevel fromScore(int score) {
            if (score < 30) return LOW;
            if (score < 60) return MEDIUM;
            return HIGH;
        }

        public int getMinScore() {
            return minScore;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public String getDescription() {
            return description;
        }
    }
}
