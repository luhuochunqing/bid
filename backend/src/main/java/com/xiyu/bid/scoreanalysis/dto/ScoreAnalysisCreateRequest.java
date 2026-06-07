package com.xiyu.bid.scoreanalysis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评分分析创建请求
 * 用于创建新的评分分析
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreAnalysisCreateRequest {

    /**
     * 关联的项目ID
     */
    private Long projectId;

    /**
     * 关联的标讯ID
     */
    private Long tenderId;

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
