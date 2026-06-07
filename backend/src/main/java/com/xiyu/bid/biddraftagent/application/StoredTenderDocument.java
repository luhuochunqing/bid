package com.xiyu.bid.biddraftagent.application;

public record StoredTenderDocument(
        String fileUrl,
        String storagePath,
        String contentSha256
) {
}
