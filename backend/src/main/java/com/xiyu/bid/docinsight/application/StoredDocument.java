package com.xiyu.bid.docinsight.application;

public record StoredDocument(
        String fileUrl,
        String storagePath,
        String contentSha256
) {
}
