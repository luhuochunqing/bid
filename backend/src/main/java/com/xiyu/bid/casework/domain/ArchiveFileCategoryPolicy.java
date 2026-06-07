package com.xiyu.bid.casework.domain;

import java.util.Objects;

public final class ArchiveFileCategoryPolicy {

    public DocumentCategory calculateCategory(String fileName) {
        Objects.requireNonNull(fileName, "File name must not be null");
        String lowerName = fileName.toLowerCase();

        if (lowerName.contains("开标") || lowerName.contains("一览表") || lowerName.contains("开标一览")) {
            return DocumentCategory.OPEN_LIST;
        }
        if (lowerName.contains("中标通知") || lowerName.contains("中标书") || lowerName.contains("win notice")) {
            return DocumentCategory.WIN_NOTICE;
        }
        if (lowerName.contains("保证金") || lowerName.contains("银行回单") || lowerName.contains("deposit") || lowerName.contains("回单")) {
            return DocumentCategory.DEPOSIT_RECEIPT;
        }
        if (lowerName.contains("复盘") || lowerName.contains("retrospective") || lowerName.contains("总结")) {
            return DocumentCategory.OTHER;
        }
        if (lowerName.contains("过程") || lowerName.contains("process") || lowerName.contains("会议") || lowerName.contains("周报")) {
            return DocumentCategory.OTHER;
        }
        if (lowerName.contains("合同") || lowerName.contains("contract") || lowerName.contains("协议") || lowerName.contains("agreement")) {
            return DocumentCategory.OTHER;
        }
        if (lowerName.contains("招标") || lowerName.contains("tender") || lowerName.contains("公告")) {
            return DocumentCategory.TENDER;
        }
        if (lowerName.contains("投标") || lowerName.contains("bid") || lowerName.contains("应答") || lowerName.contains("标书")) {
            return DocumentCategory.BID;
        }
        return DocumentCategory.OTHER;
    }
}
