package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

/** 保证金回单页面视觉事实提取端口。 */
@FunctionalInterface
public interface DepositReceiptVisionDetector {
    DepositReceiptVisionResult detect(DocumentContent document);
}
