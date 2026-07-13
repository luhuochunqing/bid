package com.xiyu.bid.projectworkflow.core;

import com.xiyu.bid.file.domain.FileUrlPrefixes;

/**
 * 项目文档存储类型（纯核心枚举）。
 *
 * <p>按 fileUrl 前缀判定，决定下载分派路径：
 * <ul>
 *   <li>{@link #OBS_DIRECT}：OBS 直传（obs-direct:{uploadId}），委托 {@code ObsShareUrlSigner} 生成预签名 URL，Controller 返回 302 重定向。</li>
 *   <li>{@link #LOCAL_BID_AGENT}：本地存储（bid-agent://tender-documents/...），走 {@code ProjectDocumentFileStorage.load} 流式下载。</li>
 *   <li>{@link #DOC_INSIGHT}：DocInsight 解析存储（doc-insight://...），同样走 {@code fileStorage.load}。</li>
 *   <li>{@link #UNKNOWN}：未知前缀，下载链路按既有逻辑抛 404。</li>
 * </ul>
 *
 * <p>前缀常量统一引用 {@link FileUrlPrefixes}，避免与 {@code LocalTenderDocumentStorage}、
 * {@code ObsShareUrlSigner} 重复硬编码。
 */
public enum ProjectDocumentStorageType {

    OBS_DIRECT(FileUrlPrefixes.OBS_DIRECT),
    LOCAL_BID_AGENT(FileUrlPrefixes.LOCAL_BID_AGENT),
    DOC_INSIGHT(FileUrlPrefixes.DOC_INSIGHT),
    UNKNOWN(null);

    private final String prefix;

    ProjectDocumentStorageType(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 按 fileUrl 前缀判定存储类型。
     *
     * @param fileUrl 项目文档的 fileUrl（可能为 null/空）
     * @return 对应存储类型；null/空/未知前缀返回 {@link #UNKNOWN}
     */
    public static ProjectDocumentStorageType fromFileUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return UNKNOWN;
        }
        for (ProjectDocumentStorageType type : values()) {
            if (type.prefix != null && fileUrl.startsWith(type.prefix)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
