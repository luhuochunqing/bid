package com.xiyu.bid.bidmatch.domain;

import java.util.Optional;

public record BidMatchModelSnapshotResult(
        Optional<BidMatchModelVersionSnapshot> snapshot,
        ValidationResult validation
) {

    public BidMatchModelSnapshotResult {
        snapshot = snapshot == null ? Optional.empty() : snapshot;
        validation = validation == null ? ValidationResult.failed(java.util.List.of("模型校验结果缺失")) : validation;
    }

    public static BidMatchModelSnapshotResult created(BidMatchModelVersionSnapshot snapshot) {
        return new BidMatchModelSnapshotResult(Optional.of(snapshot), ValidationResult.ok());
    }

    public static BidMatchModelSnapshotResult rejected(ValidationResult validation) {
        return new BidMatchModelSnapshotResult(Optional.empty(), validation);
    }
}
