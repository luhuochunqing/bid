package com.xiyu.bid.file.application;

import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.DownloadPolicy;
import com.xiyu.bid.file.domain.gateway.ObsDownloadUrlGateway;
import com.xiyu.bid.file.domain.model.SignedDownloadUrl;
import com.xiyu.bid.file.entity.BidFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * OBS 分享 URL 签发器（系统级，无 operatorId 校验）。
 *
 * <p>专为 CRM 回调等系统级场景设计：识别 {@code obs-direct:{uploadId}} 伪协议，
 * 查 BidFile 获取 bucket + objectKey，调 {@link ObsDownloadUrlGateway} 生成预签名 URL。
 *
 * <p>与 {@link GetDownloadUrlUseCase} 的区别：
 * <ul>
 *   <li>{@code GetDownloadUrlUseCase}：面向用户操作，校验 operatorId 归属权限</li>
 *   <li>{@code ObsShareUrlSigner}：面向系统回调（如中标结果回传 CRM），不校验归属权限，
 *       仅查 BidFile + 调 Gateway 签名</li>
 * </ul>
 *
 * <p>过期时间使用 {@link DownloadPolicy#MAX_EXPIRE_SECONDS}（3600 秒），
 * 让 CRM 有足够时间下载文件。</p>
 *
 * <p>设计原则：失败不抛异常，返回 {@link Optional#empty()}，避免阻塞回调入队。
 * 调用方（如 {@code TenderAttachmentUrlResolver}）对空值回退为原 fileUrl。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObsShareUrlSigner {

    /** obs-direct: 伪协议前缀，与前端 useObsUploadFallback.js 的 OBS_DIRECT_PREFIX 保持一致。 */
    public static final String OBS_DIRECT_PREFIX = "obs-direct:";

    /** CRM 回调场景的预签名 URL 有效期（秒），取 DownloadPolicy 允许的最大值。 */
    private static final int SHARE_URL_EXPIRE_SECONDS = DownloadPolicy.MAX_EXPIRE_SECONDS;

    private final BidFileRepository bidFileRepository;
    private final ObsDownloadUrlGateway obsDownloadUrlGateway;
    private final DownloadPolicy downloadPolicy = new DownloadPolicy();

    /**
     * 尝试将 {@code obs-direct:{uploadId}} 转换为 OBS 预签名 URL。
     *
     * <p>非 obs-direct: 前缀的 URL 原样返回（包装在 Optional 中）。
     * 查不到 BidFile 或 OBS 故障时返回 Optional.empty()。
     *
     * @param fileUrl 原始 fileUrl，可能为 obs-direct:{uploadId} 或其他格式
     * @return 预签名 URL（Optional 包装）；非 obs-direct: 前缀的原样返回；
     *         查不到/OBS 故障时返回 Optional.empty()
     */
    public Optional<String> trySign(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return Optional.empty();
        }
        if (!fileUrl.startsWith(OBS_DIRECT_PREFIX)) {
            // 非 obs-direct: 前缀，原样返回，让调用方继续处理
            return Optional.of(fileUrl);
        }

        String uploadId = fileUrl.substring(OBS_DIRECT_PREFIX.length());
        if (uploadId.isBlank()) {
            log.warn("obs-direct URL 缺少 uploadId: {}", fileUrl);
            return Optional.empty();
        }

        Optional<BidFile> bidFileOpt = bidFileRepository.findByUploadId(uploadId);
        if (bidFileOpt.isEmpty()) {
            log.warn("obs-direct URL 对应的 BidFile 不存在: uploadId={}", uploadId);
            return Optional.empty();
        }
        BidFile bidFile = bidFileOpt.get();

        int expireSeconds = downloadPolicy.clampExpireSeconds(SHARE_URL_EXPIRE_SECONDS);

        try {
            SignedDownloadUrl signed = obsDownloadUrlGateway.signDownloadUrl(
                    bidFile.getBucket(), bidFile.getObjectKey(), expireSeconds);
            log.debug("obs-direct URL 已签发: uploadId={} -> {} (expireSeconds={})",
                    uploadId, signed.url(), expireSeconds);
            return Optional.of(signed.url());
        } catch (RuntimeException e) {
            log.error("签发 OBS 预签名 URL 失败: uploadId={}", uploadId, e);
            return Optional.empty();
        }
    }
}
