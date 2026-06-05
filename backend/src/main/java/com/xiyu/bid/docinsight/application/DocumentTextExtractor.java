package com.xiyu.bid.docinsight.application;

public interface DocumentTextExtractor {
    ExtractedDocument extract(String fileName, String contentType, byte[] content);
}
