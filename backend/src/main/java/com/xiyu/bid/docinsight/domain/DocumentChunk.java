package com.xiyu.bid.docinsight.domain;

import java.util.List;

public record DocumentChunk(
        String text,
        List<String> sectionPath
) {
}
