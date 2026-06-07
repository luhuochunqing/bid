package com.xiyu.bid.scoreanalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 维度分数数据传输对象
 * 用于返回各个维度的详细得分
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionScoreDTO {

    /**
     * 维度分数ID
     */
    private Long id;

    /**
     * 关联的分析ID
     */
    private Long analysisId;

    /**
     * 维度名称
     * 标准维度：技术能力、财务实力、团队经验、历史业绩、合规性、价格竞争力
     */
    private String dimensionName;

    /**
     * 维度分数
     */
    private Integer score;

    /**
     * 权重
     */
    private BigDecimal weight;

    /**
     * 评语/评论
     */
    private String comments;
}
