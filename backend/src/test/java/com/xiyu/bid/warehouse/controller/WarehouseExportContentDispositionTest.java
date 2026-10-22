package com.xiyu.bid.warehouse.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseExportContentDispositionTest {

    @Test
    void chineseFilenameUsesRfc5987AndStaysLatin1() {
        String header = WarehouseExportController.attachmentDisposition(
                "仓库信息导出包_20261022094152.zip");

        assertThat(header.chars().allMatch(cp -> cp <= 255)).isTrue();
        assertThat(header).contains("filename*=UTF-8''");
        assertThat(header).contains(
                "%E4%BB%93%E5%BA%93%E4%BF%A1%E6%81%AF%E5%AF%BC%E5%87%BA%E5%8C%85_20261022094152.zip");
        assertThat(header).doesNotContain("仓库");
    }
}
