package com.xiyu.bid.tenderupload.service;

import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import org.springframework.stereotype.Component;

@Component
public class TenderTaskFailureClassifier {
    public AsyncFailureKind classify(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("未找到") || message.contains("missing file path") || message.contains("文件去重冲突")) {
            return AsyncFailureKind.DATA_CORRUPTION;
        }
        if (message.contains("reused-task=")) {
            return AsyncFailureKind.IDEMPOTENT_DUPLICATE;
        }
        if (message.contains("timeout") || message.contains("超时")) {
            return AsyncFailureKind.TRANSIENT_DEPENDENCY;
        }
        if (message.contains("非法") || message.contains("invalid")) {
            return AsyncFailureKind.CONTRACT_INVALID;
        }
        return AsyncFailureKind.BUG;
    }
}
