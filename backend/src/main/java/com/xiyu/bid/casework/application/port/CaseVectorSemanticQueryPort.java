package com.xiyu.bid.casework.application.port;

import java.util.Map;

/**
 * 案例向量语义检索端口（Application 层驱动端口）。
 *
 * <p>负责根据评分项检索文本查询候选案例的向量余弦相似度映射。
 * 实现类可接入向量切片缓存或向量检索服务，在无向量服务或异常时安全优雅降级返回空 Map。</p>
 */
public interface CaseVectorSemanticQueryPort {

    /**
     * 查询评分项检索文本与候选案例的向量相似度映射（caseId -> cosSim）。
     *
     * @param queryText 评分项合并检索文本
     * @return 案例 ID 到向量相似度（0.0 ~ 1.0）的映射；若无匹配或服务不可用返回空 Map，绝不为 null
     */
    Map<Long, Double> querySimilarities(String queryText);
}
