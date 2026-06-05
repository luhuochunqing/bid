package com.xiyu.bid.casework.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveDomainTest {

    private final ArchiveFileCategoryPolicy policy = new ArchiveFileCategoryPolicy();

    @Test
    void should_classify_tender_related_files() {
        assertThat(policy.calculateCategory("2026西域招标文件.pdf")).isEqualTo(DocumentCategory.TENDER);
        assertThat(policy.calculateCategory("招标公告-V2.docx")).isEqualTo(DocumentCategory.TENDER);
        assertThat(policy.calculateCategory("tender_specs.pdf")).isEqualTo(DocumentCategory.TENDER);
    }

    @Test
    void should_classify_bid_related_files() {
        assertThat(policy.calculateCategory("西域投标文件-商务部分.pdf")).isEqualTo(DocumentCategory.BID);
        assertThat(policy.calculateCategory("应答书-技术偏差表.xlsx")).isEqualTo(DocumentCategory.BID);
        assertThat(policy.calculateCategory("bid_response.docx")).isEqualTo(DocumentCategory.BID);
    }

    @Test
    void should_classify_open_list_related_files() {
        assertThat(policy.calculateCategory("开标一览表-2026.pdf")).isEqualTo(DocumentCategory.OPEN_LIST);
        assertThat(policy.calculateCategory("开标记录表.xlsx")).isEqualTo(DocumentCategory.OPEN_LIST);
    }

    @Test
    void should_classify_win_notice_related_files() {
        assertThat(policy.calculateCategory("中标通知书.pdf")).isEqualTo(DocumentCategory.WIN_NOTICE);
        assertThat(policy.calculateCategory("中标通知-正式版.docx")).isEqualTo(DocumentCategory.WIN_NOTICE);
    }

    @Test
    void should_classify_deposit_receipt_related_files() {
        assertThat(policy.calculateCategory("保证金银行回单.pdf")).isEqualTo(DocumentCategory.DEPOSIT_RECEIPT);
        assertThat(policy.calculateCategory("投标保证金回执.png")).isEqualTo(DocumentCategory.DEPOSIT_RECEIPT);
    }

    @Test
    void should_classify_other_for_legacy_names() {
        assertThat(policy.calculateCategory("采购合同正式版.pdf")).isEqualTo(DocumentCategory.OTHER);
        assertThat(policy.calculateCategory("Teaming会议纪要.txt")).isEqualTo(DocumentCategory.OTHER);
        assertThat(policy.calculateCategory("投标复盘报告.pptx")).isEqualTo(DocumentCategory.OTHER);
    }

    @Test
    void should_classify_other_files() {
        assertThat(policy.calculateCategory("系统架构图.png")).isEqualTo(DocumentCategory.OTHER);
        assertThat(policy.calculateCategory("备份数据.zip")).isEqualTo(DocumentCategory.OTHER);
    }

    @Test
    void should_throw_exception_when_filename_is_null() {
        assertThatThrownBy(() -> policy.calculateCategory(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("File name must not be null");
    }
}
