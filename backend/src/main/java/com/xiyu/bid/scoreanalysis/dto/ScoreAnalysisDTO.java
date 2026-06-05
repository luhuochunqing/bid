package com.xiyu.bid.scoreanalysis.dto;

import com.xiyu.bid.scoreanalysis.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评分分析数据传输对象
 * 用于返回评分分析的详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreAnalysisDTO {

    /**
     * 分析ID
     */
    private Long id;

    /**
     * 关联的项目ID
     */
    private Long projectId;

    /**
     * 关联的标讯ID
     */
    private Long tenderId;

    /**
     * 分析日期
     */
    private LocalDateTime analysisDate;

    /**
     * 综合评分
     */
    private Integer overallScore;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;

    /**
     * 分析人ID
     */
    private Long analystId;

    /**
     * 是否由AI生成
     */
    private Boolean isAiGenerated;

    /**
     * 分析总结
     */
    private String summary;

    /**
     * 维度分数列表
     */
    private List<DimensionScoreDTO> dimensions;
}
