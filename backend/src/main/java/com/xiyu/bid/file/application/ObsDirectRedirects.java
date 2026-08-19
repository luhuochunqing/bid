// Input: ObsShareUrlSigner + 原始 fileUrl（可能为 obs-direct:{uploadId} 或其他格式）
// Output: Optional<ResponseEntity<Resource>> — obs-direct 且签发成功返回 302；非 obs-direct 返回 empty；失败抛 ResponseStatusException（404/502）
// Pos: file/application/ - obs-direct 302 重定向的统一收口组件（消除 casework/integration 等处的重复实现）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.file.application;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Optional;

/**
 * obs-direct:{uploadId} 文件 302 重定向响应的统一构建器。
 *
 * <p>背景：项目档案（ArchiveFileResponseFactory）、CRM 集成下载端点
 * （TenderAttachmentDownloadService）等多处都需要"obs-direct → 实时签发预签名 URL
 * → 302 重定向"的同一行为，此前各自复制实现。本组件统一收口，并精确区分失败语义：
 *
 * <ul>
 *   <li>BidFile 不存在 → 404（资源确实不存在）</li>
 *   <li>OBS 签发故障 → 502（上游依赖故障，可重试）</li>
 * </ul>
 */
public final class ObsDirectRedirects {

    private ObsDirectRedirects() {
    }

    /**
     * 若 rawPath 是 obs-direct:{uploadId}，签发预签名 URL 并构建 302 重定向响应。
     *
     * @param signer  OBS 分享 URL 签发器
     * @param rawPath 原始文件引用，可能为 obs-direct: 伪协议或其他格式
     * @return 302 重定向响应（obs-direct 且签发成功）；非 obs-direct: 前缀返回 empty，由调用方继续处理
     * @throws ResponseStatusException 404（BidFile 不存在）/ 502（OBS 签发故障）
     */
    public static Optional<ResponseEntity<Resource>> tryRedirect(ObsShareUrlSigner signer, String rawPath) {
        if (rawPath == null || !rawPath.startsWith(ObsShareUrlSigner.OBS_DIRECT_PREFIX)) {
            return Optional.empty();
        }
        ObsShareUrlSigner.SignResult result = signer.signObsDirect(rawPath);
        return switch (result.outcome()) {
            case SIGNED -> Optional.of(ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(result.url())).build());
            case FILE_NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "OBS 文件不存在: " + rawPath);
            case SIGN_FAILED -> throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "OBS 预签名 URL 签发失败，请稍后重试: " + rawPath);
            case NOT_OBS_DIRECT -> Optional.empty();
        };
    }
}
