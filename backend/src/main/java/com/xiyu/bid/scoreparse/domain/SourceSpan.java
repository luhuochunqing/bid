package com.xiyu.bid.scoreparse.domain;

/** Exact physical source range. Offsets are UTF-16 offsets into the original cell or text block. */
public record SourceSpan(String documentId, String fileSha256, String fileType, String schemeId,
                         Integer pageNumber, String tableId, String sourceId,
                         int startOffset, int endOffset, String rawText, String locator) {}
