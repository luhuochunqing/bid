package com.xiyu.bid.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenderSourceTypeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("@JsonValue 序列化")
    class Serialization {

        @Test
        @DisplayName("EXTERNAL_PLATFORM 序列化为中文标签 '第三方平台'")
        void externalPlatformSerializesToChinese() throws Exception {
            String json = mapper.writeValueAsString(Tender.SourceType.EXTERNAL_PLATFORM);
            assertThat(json).isEqualTo("\"第三方平台\"");
        }

        @Test
        @DisplayName("CRM_OPPORTUNITY 序列化为中文标签 'CRM 创建'")
        void crmOpportunitySerializesToChinese() throws Exception {
            String json = mapper.writeValueAsString(Tender.SourceType.CRM_OPPORTUNITY);
            assertThat(json).isEqualTo("\"CRM 创建\"");
        }

        @Test
        @DisplayName("MANUAL_SINGLE 序列化为中文标签 '人工录入'")
        void manualSingleSerializesToChinese() throws Exception {
            String json = mapper.writeValueAsString(Tender.SourceType.MANUAL_SINGLE);
            assertThat(json).isEqualTo("\"人工录入\"");
        }

        @Test
        @DisplayName("BULK_IMPORT 序列化为中文标签 '人工录入'（与 MANUAL_SINGLE 同属人工录入大类）")
        void bulkImportSerializesToChinese() throws Exception {
            String json = mapper.writeValueAsString(Tender.SourceType.BULK_IMPORT);
            assertThat(json).isEqualTo("\"人工录入\"");
        }
    }

    @Nested
    @DisplayName("@JsonCreator 反序列化")
    class Deserialization {

        @Test
        @DisplayName("中文标签 '第三方平台' 反序列化为 EXTERNAL_PLATFORM")
        void chineseLabelDeserializes() throws Exception {
            Tender.SourceType result = mapper.readValue("\"第三方平台\"", Tender.SourceType.class);
            assertThat(result).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        }

        @Test
        @DisplayName("英文枚举名 'EXTERNAL_PLATFORM' 反序列化仍兼容")
        void englishNameDeserializes() throws Exception {
            Tender.SourceType result = mapper.readValue("\"EXTERNAL_PLATFORM\"", Tender.SourceType.class);
            assertThat(result).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        }

        @Test
        @DisplayName("中文标签 'CRM 创建' 反序列化为 CRM_OPPORTUNITY")
        void crmChineseLabelDeserializes() throws Exception {
            Tender.SourceType result = mapper.readValue("\"CRM 创建\"", Tender.SourceType.class);
            assertThat(result).isEqualTo(Tender.SourceType.CRM_OPPORTUNITY);
        }

        @Test
        @DisplayName("英文枚举名 'CRM_OPPORTUNITY' 反序列化仍兼容")
        void crmEnglishNameDeserializes() throws Exception {
            Tender.SourceType result = mapper.readValue("\"CRM_OPPORTUNITY\"", Tender.SourceType.class);
            assertThat(result).isEqualTo(Tender.SourceType.CRM_OPPORTUNITY);
        }

        @Test
        @DisplayName("null 输入返回 null")
        void nullReturnsNull() throws Exception {
            Tender.SourceType result = mapper.readValue("null", Tender.SourceType.class);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("空白字符串返回 null")
        void blankReturnsNull() throws Exception {
            Tender.SourceType result = mapper.readValue("\"  \"", Tender.SourceType.class);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("不支持的值抛出 IllegalArgumentException")
        void unsupportedValueThrows() {
            assertThatThrownBy(() -> mapper.readValue("\"INVALID_TYPE\"", Tender.SourceType.class))
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("fromValue 直接调用")
    class FromValue {

        @Test
        @DisplayName("中文和英文均能映射到同一枚举常量")
        void bothFormatsResolveToSameEnum() {
            assertThat(Tender.SourceType.fromValue("EXTERNAL_PLATFORM"))
                    .isSameAs(Tender.SourceType.fromValue("第三方平台"));
            assertThat(Tender.SourceType.fromValue("CRM_OPPORTUNITY"))
                    .isSameAs(Tender.SourceType.fromValue("CRM 创建"));
            assertThat(Tender.SourceType.fromValue("MANUAL_SINGLE"))
                    .isSameAs(Tender.SourceType.fromValue("人工录入"));
            // BULK_IMPORT label 与 MANUAL_SINGLE 同为"人工录入"（a484bdaa0 业务决策：批量导入归为人工录入大类），
            // fromValue("人工录入") 遍历 values() 时先命中 MANUAL_SINGLE，故 BULK_IMPORT 无独立中文反查能力，
            // 此处仅断言英文枚举名自匹配。
            assertThat(Tender.SourceType.fromValue("BULK_IMPORT"))
                    .isSameAs(Tender.SourceType.BULK_IMPORT);
        }
    }
}