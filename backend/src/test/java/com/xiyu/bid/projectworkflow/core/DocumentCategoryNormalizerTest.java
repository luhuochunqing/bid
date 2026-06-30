package com.xiyu.bid.projectworkflow.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-420: 项目档案文档分类归集规则 — 验证 DocumentCategoryNormalizer 映射正确。
 */
@DisplayName("CO-420 DocumentCategoryNormalizer 分类归集规则")
class DocumentCategoryNormalizerTest {

    @Test
    @DisplayName("null/blank → null")
    void normalize_nullOrBlank_returnsNull() {
        assertThat(DocumentCategoryNormalizer.normalize(null)).isNull();
        assertThat(DocumentCategoryNormalizer.normalize("")).isNull();
        assertThat(DocumentCategoryNormalizer.normalize("   ")).isNull();
    }

    @Test
    @DisplayName("标准枚举名原样返回（大小写归一）")
    void normalize_standardName_keptAsIs() {
        assertThat(DocumentCategoryNormalizer.normalize("TENDER")).isEqualTo("TENDER");
        assertThat(DocumentCategoryNormalizer.normalize("tender")).isEqualTo("TENDER");
        assertThat(DocumentCategoryNormalizer.normalize("BID")).isEqualTo("BID");
        assertThat(DocumentCategoryNormalizer.normalize("OPEN_LIST")).isEqualTo("OPEN_LIST");
        assertThat(DocumentCategoryNormalizer.normalize("WIN_NOTICE")).isEqualTo("WIN_NOTICE");
        assertThat(DocumentCategoryNormalizer.normalize("DEPOSIT_RECEIPT")).isEqualTo("DEPOSIT_RECEIPT");
        assertThat(DocumentCategoryNormalizer.normalize("OTHER")).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("历史前端别名 TENDER_DOCUMENT → TENDER")
    void normalize_tenderDocumentAlias() {
        assertThat(DocumentCategoryNormalizer.normalize("TENDER_DOCUMENT")).isEqualTo("TENDER");
        assertThat(DocumentCategoryNormalizer.normalize("tender_document")).isEqualTo("TENDER");
    }

    @Test
    @DisplayName("历史前端别名 BID_DOCUMENT → BID")
    void normalize_bidDocumentAlias() {
        assertThat(DocumentCategoryNormalizer.normalize("BID_DOCUMENT")).isEqualTo("BID");
        assertThat(DocumentCategoryNormalizer.normalize("BID_DOCUMENT")).isEqualTo("BID");
    }

    @Test
    @DisplayName("评标别名 EVALUATION_EVIDENCE → OPEN_LIST")
    void normalize_evaluationEvidenceAlias() {
        assertThat(DocumentCategoryNormalizer.normalize("EVALUATION_EVIDENCE")).isEqualTo("OPEN_LIST");
    }

    @Test
    @DisplayName("结果确认别名 RESULT_EVIDENCE → WIN_NOTICE")
    void normalize_resultEvidenceAlias() {
        assertThat(DocumentCategoryNormalizer.normalize("RESULT_EVIDENCE")).isEqualTo("WIN_NOTICE");
    }

    @Test
    @DisplayName("结项别名 CLOSURE_EVIDENCE → DEPOSIT_RECEIPT")
    void normalize_closureEvidenceAlias() {
        assertThat(DocumentCategoryNormalizer.normalize("CLOSURE_EVIDENCE")).isEqualTo("DEPOSIT_RECEIPT");
    }

    @Test
    @DisplayName("复盘别名 RETROSPECTIVE_REPORT → OTHER")
    void normalize_retrospectiveReportAlias() {
        assertThat(DocumentCategoryNormalizer.normalize("RETROSPECTIVE_REPORT")).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("未知值 → OTHER")
    void normalize_unknownValue_returnsOther() {
        assertThat(DocumentCategoryNormalizer.normalize("SOMETHING_NEW")).isEqualTo("OTHER");
        assertThat(DocumentCategoryNormalizer.normalize("random")).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("defaultByStage: 各阶段推导默认分类")
    void defaultByStage() {
        assertThat(DocumentCategoryNormalizer.defaultByStage("INITIATION")).isEqualTo("TENDER");
        assertThat(DocumentCategoryNormalizer.defaultByStage("DRAFTING")).isEqualTo("BID");
        assertThat(DocumentCategoryNormalizer.defaultByStage("EVALUATION")).isEqualTo("OPEN_LIST");
        assertThat(DocumentCategoryNormalizer.defaultByStage("RESULT")).isEqualTo("WIN_NOTICE");
        assertThat(DocumentCategoryNormalizer.defaultByStage("RETROSPECTIVE")).isEqualTo("OTHER");
        assertThat(DocumentCategoryNormalizer.defaultByStage("CLOSURE")).isEqualTo("DEPOSIT_RECEIPT");
        // 未知阶段 → OTHER
        assertThat(DocumentCategoryNormalizer.defaultByStage("UNKNOWN")).isEqualTo("OTHER");
        assertThat(DocumentCategoryNormalizer.defaultByStage(null)).isEqualTo("OTHER");
    }
}
