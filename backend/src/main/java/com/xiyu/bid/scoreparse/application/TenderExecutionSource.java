package com.xiyu.bid.scoreparse.application;

/**
 * 异步解析 Worker 专用执行源：包含项目级合并正文、每个文件的正文和一次性文件句柄。
 * 必须在调用方同一任务生命周期的 finally 块中释放 loadedFiles。
 */
public final class TenderExecutionSource {
    private final TenderTextSource source;
    private final LoadedBidFile loaded;
    private final String emptyReason;
    private final java.util.List<TenderTextSource> sources;
    private final java.util.List<LoadedBidFile> loadedFiles;

    private TenderExecutionSource(TenderTextSource source, LoadedBidFile loaded, String emptyReason,
                                  java.util.List<TenderTextSource> sources,
                                  java.util.List<LoadedBidFile> loadedFiles) {
        this.source = source;
        this.loaded = loaded;
        this.emptyReason = emptyReason;
        this.sources = sources == null ? java.util.List.of() : java.util.List.copyOf(sources);
        this.loadedFiles = loadedFiles == null ? java.util.List.of() : java.util.List.copyOf(loadedFiles);
    }

    public static TenderExecutionSource found(TenderTextSource source, LoadedBidFile loaded) {
        return found(java.util.List.of(source), loaded == null ? java.util.List.of() : java.util.List.of(loaded));
    }

    public static TenderExecutionSource found(java.util.List<TenderTextSource> sources,
                                              java.util.List<LoadedBidFile> loadedFiles) {
        if (sources == null || sources.isEmpty()) return unavailable(InitiationTenderTextResolver.NO_TENDER_MESSAGE);
        TenderTextSource first = sources.get(0);
        String merged = sources.size() == 1
                ? (first.text() == null ? "" : first.text())
                : sources.stream().map(item -> "=== 招标文件: " + item.fileName() + " ===\n"
                        + (item.text() == null ? "" : item.text()))
                .reduce((left, right) -> left + "\n\n" + right).orElse("");
        TenderTextSource combined = new TenderTextSource(first.fileName(), first.fileUrl(), merged);
        LoadedBidFile firstLoaded = loadedFiles == null || loadedFiles.isEmpty() ? null : loadedFiles.get(0);
        return new TenderExecutionSource(combined, firstLoaded, null, sources,
                loadedFiles == null ? java.util.List.of() : loadedFiles);
    }

    public static TenderExecutionSource unavailable(String emptyReason) {
        return new TenderExecutionSource(null, null, emptyReason, java.util.List.of(), java.util.List.of());
    }

    public TenderTextSource source() { return source; }
    public LoadedBidFile loaded() { return loaded; }
    public String emptyReason() { return emptyReason; }
    public java.util.List<TenderTextSource> sources() { return sources; }
    public java.util.List<LoadedBidFile> loadedFiles() { return loadedFiles; }
}
