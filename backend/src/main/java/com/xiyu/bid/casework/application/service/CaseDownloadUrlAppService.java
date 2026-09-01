// Input: 案例 id（签发历史案例原文下载链接）
// Output: 预签名 URL + 文件名 + 有效期，或业务异常（404/409/502）
// Pos: casework 模块应用服务——历史案例原文下载编排
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.domain.gateway.HistoricalCaseObsConfig;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.domain.core.ObsObjectKeyPolicy;
import com.xiyu.bid.file.domain.gateway.ObsDownloadUrlGateway;
import com.xiyu.bid.file.domain.model.SignedDownloadUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 历史案例原文下载链接签发编排。
 *
 * <p>与 {@code ObsShareUrlSigner}（obs-direct:{uploadId} 伪协议）语义分离：
 * 本服务面向历史导入资产——案例没有 {@code bid_file} 记录，
 * 直接按 {@code knowledge_case.obs_bid_file_key} 签发，只信库内值（FR-005）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseDownloadUrlAppService {

    private final KnowledgeCaseRepository knowledgeCaseRepository;
    private final ObsDownloadUrlGateway obsDownloadUrlGateway;
    private final HistoricalCaseObsConfig obsConfig;

    /** 签发结果 DTO。 */
    public record DownloadUrlResult(String url, String filename, int expireSeconds) {
    }

    /**
     * 为案例签发原版标书预签名下载链接。
     *
     * @param caseId 案例 id
     * @return 预签名 URL、下载文件名、有效期
     * @throws BusinessException 404 案例不存在 / 409 无原版标书 / 502 签发失败
     */
    public DownloadUrlResult signDownloadUrl(Long caseId) {
        KnowledgeCase knowledgeCase = knowledgeCaseRepository.findById(caseId)
                .orElseThrow(() -> new BusinessException(404, "案例不存在"));

        String objectKey = knowledgeCase.getObsBidFileKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(409, "该案例暂无原版标书");
        }

        int expireSeconds = obsConfig.downloadUrlExpireSeconds();
        try {
            SignedDownloadUrl signed = obsDownloadUrlGateway.signDownloadUrl(
                    obsConfig.bucket(), objectKey, expireSeconds);
            return new DownloadUrlResult(signed.url(), ObsObjectKeyPolicy.filenameOf(objectKey), expireSeconds);
        } catch (RuntimeException e) {
            log.error("历史案例原文签发失败: caseId={} objectKey={}", caseId, objectKey, e);
            throw new BusinessException(502, "下载链接生成失败，请稍后重试", e);
        }
    }
}
