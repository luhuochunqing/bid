// Output: DocumentChangeTargetUrlResolver 按 documentCategory 分流的分支覆盖
// Pos: notification/core/ - 文档变更 targetUrl 解析测试
package com.xiyu.bid.notification.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentChangeTargetUrlResolver — 按 documentCategory 分流到项目阶段页")
class DocumentChangeTargetUrlResolverTest {

    private static final Long PID = 100L;

    @Test
    void tender_MapsToInitiation() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "TENDER"))
                .isEqualTo("/project/100/initiation");
    }

    @Test
    void bid_MapsToDrafting() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "BID"))
                .isEqualTo("/project/100/drafting");
    }

    @Test
    void openList_MapsToEvaluation() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "OPEN_LIST"))
                .isEqualTo("/project/100/evaluation");
    }

    @Test
    void winNotice_MapsToResult() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "WIN_NOTICE"))
                .isEqualTo("/project/100/result");
    }

    @Test
    void depositReceipt_MapsToClosure() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "DEPOSIT_RECEIPT"))
                .isEqualTo("/project/100/closure");
    }

    @Test
    void bidResultNotice_MapsToResult() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "BID_RESULT_NOTICE"))
                .isEqualTo("/project/100/result");
    }

    @Test
    void bidResultAnalysis_MapsToResult() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "BID_RESULT_ANALYSIS"))
                .isEqualTo("/project/100/result");
    }

    @Test
    void other_MapsToDraftingAsFallback() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "OTHER"))
                .isEqualTo("/project/100/drafting");
    }

    @Test
    void nullCategory_MapsToDraftingAsFallback() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, null))
                .isEqualTo("/project/100/drafting");
    }

    @Test
    void blankCategory_MapsToDraftingAsFallback() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "  "))
                .isEqualTo("/project/100/drafting");
    }

    @Test
    void unknownCategory_MapsToDraftingAsFallback() {
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "UNKNOWN_TYPE"))
                .isEqualTo("/project/100/drafting");
    }

    @Test
    void retrospectiveReport_MapsToDraftingBecauseNormalizedToOther() {
        // RETROSPECTIVE_REPORT 会被 DocumentCategoryNormalizer 归一化为 OTHER → 兜底 drafting
        assertThat(DocumentChangeTargetUrlResolver.resolveTargetUrl(PID, "OTHER"))
                .isEqualTo("/project/100/drafting");
    }
}
