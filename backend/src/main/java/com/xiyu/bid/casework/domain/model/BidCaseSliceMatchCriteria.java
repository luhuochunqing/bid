package com.xiyu.bid.casework.domain.model;

import java.util.Set;

/**
 * AI 案例切片推荐的精排输入（不可变值对象）。
 *
 * @param queryText      查询文本（评分项标题 + 规则），已截断至模型上下文长度内
 * @param queryVector    查询文本的 embedding 向量
 * @param preferredLabel 期望的文件类别，如 "技术" / "商务" / "报价"，可为 null
 * @param queryTokens    查询文本分词集合，用于标题 Jaccard 相似度计算
 */
public record BidCaseSliceMatchCriteria(
        String queryText,
        float[] queryVector,
        String preferredLabel,
        Set<String> queryTokens
) {
}
