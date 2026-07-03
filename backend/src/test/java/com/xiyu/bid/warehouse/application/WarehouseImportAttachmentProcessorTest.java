package com.xiyu.bid.warehouse.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 WarehouseImportAttachmentProcessor 的 NAMING_PATTERN 正则与 KNOWN_TYPE_LABELS
 * 包含"租赁合同"（CO-493 P0 Bug #1 回归测试）。
 */
class WarehouseImportAttachmentProcessorTest {

    @Test
    @DisplayName("NAMING_PATTERN 正则匹配「租赁合同」类型文件名")
    void namingPatternMatchesLeaseContract() throws Exception {
        Pattern pattern = (Pattern) getStaticField("NAMING_PATTERN");
        assertThat(pattern.matcher("WH_北京仓_租赁合同.pdf").matches()).isTrue();
        assertThat(pattern.matcher("WH_北京仓_租赁合同_01.pdf").matches()).isTrue();
    }

    @Test
    @DisplayName("KNOWN_TYPE_LABELS 包含「租赁合同」")
    void knownTypeLabelsContainsLeaseContract() throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> labels = (Set<String>) getStaticField("KNOWN_TYPE_LABELS");
        assertThat(labels).contains("租赁合同");
    }

    @Test
    @DisplayName("NAMING_PATTERN 仍然匹配原有三种类型")
    void namingPatternStillMatchesOriginalTypes() throws Exception {
        Pattern pattern = (Pattern) getStaticField("NAMING_PATTERN");
        assertThat(pattern.matcher("WH_北京仓_产权证.pdf").matches()).isTrue();
        assertThat(pattern.matcher("WH_北京仓_发票.jpg").matches()).isTrue();
        assertThat(pattern.matcher("WH_北京仓_内外照片.png").matches()).isTrue();
    }

    private static Object getStaticField(String name) throws Exception {
        Field f = WarehouseImportAttachmentProcessor.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }
}
