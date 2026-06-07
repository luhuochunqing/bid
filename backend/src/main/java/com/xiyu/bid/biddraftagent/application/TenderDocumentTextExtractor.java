package com.xiyu.bid.biddraftagent.application;

public interface TenderDocumentTextExtractor {

    ExtractedTenderDocument extract(String fileName, String contentType, byte[] content);
}
