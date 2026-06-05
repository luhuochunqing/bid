package com.xiyu.bid.biddraftagent.application;

public record LoadedTenderDocument(
        StoredTenderDocument storedDocument,
        byte[] content
) {
}
