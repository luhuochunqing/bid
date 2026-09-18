package com.xiyu.bid.scoreparse.application;

/**
 * 异步解析 Worker 专用执行源：包含解析用纯文本源、一次性物化的文件句柄（内存或磁盘）与失败原因。
 * 必须在调用方同一任务生命周期的 finally 块中释放 loaded。
 */
public record TenderExecutionSource(
        TenderTextSource source,
        LoadedBidFile loaded,
        String emptyReason
) {
    public static TenderExecutionSource found(TenderTextSource source, LoadedBidFile loaded) {
        return new TenderExecutionSource(source, loaded, null);
    }

    public static TenderExecutionSource unavailable(String emptyReason) {
        return new TenderExecutionSource(null, null, emptyReason);
    }
}
