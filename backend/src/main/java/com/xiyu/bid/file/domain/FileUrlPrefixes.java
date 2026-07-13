package com.xiyu.bid.file.domain;

/**
 * 文件 URL 伪协议前缀常量（跨模块共享的领域契约）。
 *
 * <p>集中定义 OBS 直传、本地 bid-agent 存储、DocInsight 解析存储的前缀，
 * 避免在 {@code ObsShareUrlSigner}、{@code ProjectDocumentStorageType}、
 * {@code LocalTenderDocumentStorage} 等处重复硬编码。
 *
 * <p>FP-Java Profile：纯常量，无副作用，可被纯核心与基础设施共同引用。
 */
public final class FileUrlPrefixes {

    /** OBS 直传伪协议前缀。 */
    public static final String OBS_DIRECT = "obs-direct:";

    /** 本地 bid-agent 存储前缀。 */
    public static final String LOCAL_BID_AGENT = "bid-agent://tender-documents/";

    /** DocInsight 解析存储前缀。 */
    public static final String DOC_INSIGHT = "doc-insight://";

    private FileUrlPrefixes() {
    }
}
