package com.xiyu.bid.casework.application.port;

import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendResponse.CertificateItem;

import java.util.List;

/**
 * 企业资质与认证证书查询端口（六边形架构端口）。
 */
public interface EnterpriseQualificationQueryPort {

    /**
     * 根据评分项维度、细则与关键词，在企业资质库中检索匹配的证书。
     *
     * @param dim 评分维度/标题
     * @param detail 评分细则（标书原文）
     * @param keyword 过滤关键词
     * @return 匹配的企业资质证书列表（含原件附件图片URL）
     */
    List<CertificateItem> findMatchingCertificates(String dim, String detail, String keyword);
}
