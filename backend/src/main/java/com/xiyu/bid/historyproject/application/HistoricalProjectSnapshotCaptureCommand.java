package com.xiyu.bid.historyproject.application;

public record HistoricalProjectSnapshotCaptureCommand(
        Long projectId,
        Long archiveRecordId,
        Long exportId,
        String projectName,
        String customerName,
        String productLine,
        String sourceReasoningSummary,
        String exportContent
) {
}
