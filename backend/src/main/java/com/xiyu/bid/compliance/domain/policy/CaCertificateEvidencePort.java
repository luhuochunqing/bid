// Input: 目标交易平台关键词（可选）与数量上限
// Output: 电子标 CA 证书证据与知识库匹配记录列表
// Pos: compliance/domain/policy/ - CA 证书证据查询端口（依赖倒置）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.List;

/**
 * 电子标 CA 数字证书证据查询端口（依赖倒置）.
 *
 * <p>compliance 定义契约、resources 模块提供实现（{@code CaCertificateEvidenceQueryAdapter}），
 * 避免 compliance 直接依赖 resources 实体或仓储，符合 ArchitectureTest 架构规范。
 */
public interface CaCertificateEvidencePort {

    /**
     * 根据交易平台名称关键词查询匹配的 CA 证书记录并转为结构化匹配结果.
     *
     * @param platformKeyword 平台关键词（可为 null）
     * @param limit 匹配记录数量上限
     * @return 匹配记录列表（KnowledgeMatch，source 为 ca-certificate，recordId 为 CA 记录 ID）
     */
    List<KnowledgeMatch> findCaMatches(String platformKeyword, int limit);

    /**
     * 查询所有可用 CA 证书领域证据.
     *
     * @return 可用 CA 证书证据列表
     */
    List<CaCertificateEvidence> findAvailableCertificates();
}
