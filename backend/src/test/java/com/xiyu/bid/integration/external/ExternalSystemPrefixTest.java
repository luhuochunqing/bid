// Input: ExternalSystemPrefix 枚举
// Output: 前缀解析与匹配行为验证
// Pos: Test/integration/external/
package com.xiyu.bid.integration.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSystemPrefixTest {

    @Test
    @DisplayName("formatExternalId 生成 CRM:sourceId 格式")
    void formatExternalId_buildsPrefix() {
        assertThat(ExternalSystemPrefix.CRM.formatExternalId("17"))
                .isEqualTo("CRM:17");
    }

    @Test
    @DisplayName("matches 对 CRM: 前缀大小写不敏感")
    void matches_isCaseInsensitive() {
        assertThat(ExternalSystemPrefix.CRM.matches("CRM:17")).isTrue();
        assertThat(ExternalSystemPrefix.CRM.matches("crm:17")).isTrue();
        assertThat(ExternalSystemPrefix.CRM.matches("Crm:17")).isTrue();
    }

    @Test
    @DisplayName("matches 对非 CRM 来源返回 false")
    void matches_rejectsOtherSource() {
        assertThat(ExternalSystemPrefix.CRM.matches("SRC:17")).isFalse();
        assertThat(ExternalSystemPrefix.CRM.matches("17")).isFalse();
        assertThat(ExternalSystemPrefix.CRM.matches(null)).isFalse();
        assertThat(ExternalSystemPrefix.CRM.matches(":")).isFalse();
    }

    @Test
    @DisplayName("extractSourceId 复用 ExternalIdParser 提取数字 sourceId")
    void extractSourceId_returnsSourceId() {
        assertThat(ExternalSystemPrefix.CRM.extractSourceId("CRM:17")).isEqualTo("17");
        assertThat(ExternalSystemPrefix.CRM.extractSourceId("crm:17")).isEqualTo("17");
    }

    @Test
    @DisplayName("extractSourceId 不匹配时返回 null")
    void extractSourceId_returnsNullWhenNotMatches() {
        assertThat(ExternalSystemPrefix.CRM.extractSourceId("SRC:17")).isNull();
        assertThat(ExternalSystemPrefix.CRM.extractSourceId(null)).isNull();
    }

    @Test
    @DisplayName("extractSourceId 对空 sourceId 返回空字符串")
    void extractSourceId_returnsEmptyForBlankSourceId() {
        assertThat(ExternalSystemPrefix.CRM.extractSourceId("CRM:")).isEmpty();
    }
}
