// Input: 四知识库 Repository 真实查询结果字段（source/recordId/content/matched）
// Output: 不可变结构化匹配记录；非法构造参数快速失败（recordId 非空为硬约束）
// Pos: compliance/domain/policy/ - 知识库真实查询纯核心模型（spec 049 US3 / T032，data-model.md §3.2）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

/**
 * 四知识库真实查询的结构化匹配结果（spec 049 第三步产物）.
 *
 * <p>每条记录可回答"证据来自哪里"：knowledgeSource 定位库、recordId 定位真实
 * DB 记录、content 为 DB 记录字段拼接摘要（禁止常量文案）；未命中关键词不产生
 * 记录（缺失即无匹配，由调用方按 absence 裁决）.
 *
 * @param knowledgeSource 知识库来源（qualification / brand-authorization / personnel / performance）
 * @param recordId 真实记录 ID（非空）
 * @param content 记录摘要（来自 DB 字段拼接）
 * @param matched 匹配结论
 */
public record KnowledgeMatch(
        String knowledgeSource,
        String recordId,
        String content,
        boolean matched) {

    /** 资质库来源标识. */
    public static final String SOURCE_QUALIFICATION = "qualification";
    /** 品牌授权库来源标识. */
    public static final String SOURCE_BRAND_AUTHORIZATION = "brand-authorization";
    /** 人员库来源标识. */
    public static final String SOURCE_PERSONNEL = "personnel";
    /** 业绩库来源标识. */
    public static final String SOURCE_PERFORMANCE = "performance";
    /** CA证书资源库来源标识. */
    public static final String SOURCE_CA_CERTIFICATE = "ca-certificate";

    public KnowledgeMatch {
        if (knowledgeSource == null || knowledgeSource.isBlank()) {
            throw new IllegalArgumentException("KnowledgeMatch.knowledgeSource 不得为空");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("KnowledgeMatch.recordId 不得为空（必须指向真实 DB 记录）");
        }
        content = content == null ? "" : content;
    }
}
