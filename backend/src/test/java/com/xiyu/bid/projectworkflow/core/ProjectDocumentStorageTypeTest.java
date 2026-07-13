package com.xiyu.bid.projectworkflow.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProjectDocumentStorageType} 纯核心单测。
 * 覆盖三种已知前缀 + null/空/未知前缀边界。
 */
class ProjectDocumentStorageTypeTest {

    @Test
    void obsDirectPrefixShouldClassifyAsObsDirect() {
        assertThat(ProjectDocumentStorageType.fromFileUrl("obs-direct:11187dca-4473-4972-ac89-dedd899983aa"))
                .isEqualTo(ProjectDocumentStorageType.OBS_DIRECT);
    }

    @Test
    void localBidAgentPrefixShouldClassifyAsLocalBidAgent() {
        assertThat(ProjectDocumentStorageType.fromFileUrl("bid-agent://tender-documents/210/file.pdf"))
                .isEqualTo(ProjectDocumentStorageType.LOCAL_BID_AGENT);
    }

    @Test
    void docInsightPrefixShouldClassifyAsDocInsight() {
        assertThat(ProjectDocumentStorageType.fromFileUrl("doc-insight://tender-file/210/hash-file.pdf"))
                .isEqualTo(ProjectDocumentStorageType.DOC_INSIGHT);
    }

    @Test
    void nullShouldClassifyAsUnknown() {
        assertThat(ProjectDocumentStorageType.fromFileUrl(null))
                .isEqualTo(ProjectDocumentStorageType.UNKNOWN);
    }

    @Test
    void emptyStringShouldClassifyAsUnknown() {
        assertThat(ProjectDocumentStorageType.fromFileUrl(""))
                .isEqualTo(ProjectDocumentStorageType.UNKNOWN);
    }

    @Test
    void unknownPrefixShouldClassifyAsUnknown() {
        assertThat(ProjectDocumentStorageType.fromFileUrl("https://example.com/file.pdf"))
                .isEqualTo(ProjectDocumentStorageType.UNKNOWN);
        assertThat(ProjectDocumentStorageType.fromFileUrl("/local/path/file.pdf"))
                .isEqualTo(ProjectDocumentStorageType.UNKNOWN);
    }

    @Test
    void prefixIsCaseSensitive() {
        // 大小写不一致应归为 UNKNOWN（前缀匹配严格）
        assertThat(ProjectDocumentStorageType.fromFileUrl("OBS-DIRECT:abc"))
                .isEqualTo(ProjectDocumentStorageType.UNKNOWN);
    }
}
