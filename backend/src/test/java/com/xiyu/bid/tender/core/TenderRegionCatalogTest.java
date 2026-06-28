package com.xiyu.bid.tender.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderRegionCatalog - 总部所在地格式校验（CO-381 第四次修复）")
class TenderRegionCatalogTest {

    @Nested
    @DisplayName("普通省/自治区：省+市拼接")
    class NormalProvince {
        @Test
        @DisplayName("广东省深圳市 通过校验")
        void provincePlusCityIsValid() {
            assertThat(TenderRegionCatalog.isValid("广东省深圳市")).isTrue();
        }

        @Test
        @DisplayName("纯省名（广东省）不通过")
        void plainProvinceNameIsInvalid() {
            assertThat(TenderRegionCatalog.isValid("广东省")).isFalse();
        }
    }

    @Nested
    @DisplayName("直辖市：一级+二级拼接（CO-381 统一格式）")
    class Municipality {
        @Test
        @DisplayName("北京市北京市 通过校验（CO-381 新统一格式）")
        void municipalityConcatIsValid() {
            assertThat(TenderRegionCatalog.isValid("北京市北京市")).isTrue();
            assertThat(TenderRegionCatalog.isValid("天津市天津市")).isTrue();
            assertThat(TenderRegionCatalog.isValid("上海市上海市")).isTrue();
            assertThat(TenderRegionCatalog.isValid("重庆市重庆市")).isTrue();
        }

        @Test
        @DisplayName("旧市-市格式（北京市-北京市）兼容通过")
        void legacyDashFormatIsValid() {
            assertThat(TenderRegionCatalog.isValid("北京市-北京市")).isTrue();
        }

        @Test
        @DisplayName("旧单名（北京市）兼容通过")
        void legacySingleNameIsValid() {
            assertThat(TenderRegionCatalog.isValid("北京市")).isTrue();
        }
    }

    @Nested
    @DisplayName("港澳台：一级+二级拼接（CO-381 统一格式）")
    class Hmt {
        @Test
        @DisplayName("台湾省台北市 通过校验")
        void taiwanCityIsValid() {
            assertThat(TenderRegionCatalog.isValid("台湾省台北市")).isTrue();
            assertThat(TenderRegionCatalog.isValid("台湾省高雄市")).isTrue();
        }

        @Test
        @DisplayName("香港特别行政区中西区 通过校验")
        void hongKongDistrictIsValid() {
            assertThat(TenderRegionCatalog.isValid("香港特别行政区中西区")).isTrue();
            assertThat(TenderRegionCatalog.isValid("香港特别行政区湾仔区")).isTrue();
        }

        @Test
        @DisplayName("澳门特别行政区花地玛堂区 通过校验")
        void macauDistrictIsValid() {
            assertThat(TenderRegionCatalog.isValid("澳门特别行政区花地玛堂区")).isTrue();
        }

        @Test
        @DisplayName("旧单名（台湾省/香港特别行政区/澳门特别行政区）兼容通过")
        void legacySingleNameIsValid() {
            assertThat(TenderRegionCatalog.isValid("台湾省")).isTrue();
            assertThat(TenderRegionCatalog.isValid("香港特别行政区")).isTrue();
            assertThat(TenderRegionCatalog.isValid("澳门特别行政区")).isTrue();
        }
    }

    @Nested
    @DisplayName("非法值")
    class Invalid {
        @Test
        @DisplayName("null 不通过（由 @NotBlank 控制必填）")
        void nullIsInvalid() {
            assertThat(TenderRegionCatalog.isValid(null)).isFalse();
        }

        @Test
        @DisplayName("空字符串不通过")
        void emptyIsInvalid() {
            assertThat(TenderRegionCatalog.isValid("")).isFalse();
        }

        @Test
        @DisplayName("直辖市带区（北京市东城区）不通过")
        void municipalityWithDistrictIsInvalid() {
            assertThat(TenderRegionCatalog.isValid("北京市东城区")).isFalse();
        }

        @Test
        @DisplayName("纯市名（北京）不通过")
        void plainCityNameIsInvalid() {
            assertThat(TenderRegionCatalog.isValid("北京")).isFalse();
        }
    }

    @Test
    @DisplayName("REGIONS 白名单包含所有格式（新统一 + 旧兼容）")
    void regionsWhitelistContainsAllFormats() {
        assertThat(TenderRegionCatalog.REGIONS)
                .contains("广东省深圳市")
                .contains("北京市北京市", "北京市-北京市", "北京市")
                .contains("台湾省台北市", "台湾省")
                .contains("香港特别行政区中西区", "香港特别行政区");
    }
}
