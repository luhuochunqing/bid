package com.xiyu.bid.file.domain.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObsObjectKeyPolicyTest {

    @Test
    void isValidHistoricalKey_acceptsTypicalHistoricalKey() {
        assertTrue(ObsObjectKeyPolicy.isValidHistoricalKey(
                "2026年（投标文件）/2026.01.05-中广核办公/项目复盘-中广核.docx"));
    }

    @Test
    void isValidHistoricalKey_rejectsNullBlankAbsolutePathTraversalAndForeignPrefix() {
        assertFalse(ObsObjectKeyPolicy.isValidHistoricalKey(null));
        assertFalse(ObsObjectKeyPolicy.isValidHistoricalKey("  "));
        assertFalse(ObsObjectKeyPolicy.isValidHistoricalKey("/2026年（投标文件）/a.docx"));
        assertFalse(ObsObjectKeyPolicy.isValidHistoricalKey("2026年（投标文件）/../etc/passwd"));
        assertFalse(ObsObjectKeyPolicy.isValidHistoricalKey("bids/2026/07/a.docx"));
        assertFalse(ObsObjectKeyPolicy.isValidHistoricalKey(ObsObjectKeyPolicy.HISTORICAL_ROOT_PREFIX));
    }

    @Test
    void filenameOf_extractsBasenameWithChineseSpacesAndParentheses() {
        assertEquals("2026投标文件(定稿 2).docx", ObsObjectKeyPolicy.filenameOf(
                "2026年（投标文件）/项目A/2026投标文件(定稿 2).docx"));
    }

    @Test
    void filenameOf_decodesPercentEncodedBasename() {
        assertEquals("项目复盘-中广核.docx", ObsObjectKeyPolicy.filenameOf(
                "2026年（投标文件）/项目A/%E9%A1%B9%E7%9B%AE%E5%A4%8D%E7%9B%98-%E4%B8%AD%E5%B9%BF%E6%A0%B8.docx"));
    }

    @Test
    void filenameOf_returnsKeyItselfWhenNoSlash() {
        assertEquals("a.docx", ObsObjectKeyPolicy.filenameOf("a.docx"));
    }

    @Test
    void filenameOf_rejectsBlankKey() {
        assertThrows(IllegalArgumentException.class, () -> ObsObjectKeyPolicy.filenameOf(" "));
    }
}
