// Input: 原始 customerType 字符串（英文枚举/中文标准值/历史变体/null/空白）
// Output: 归一化到 5 种标准分类或 null 的断言
// Pos: Test/客户类型归一化纯函数单测（无 Spring 上下文）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户类型归一化纯函数单测：M1 趋势分组/筛选/下钻与 M2 统计共用的唯一口径入口，
 * 覆盖立项详情存的英文枚举名、主表快照的中文标准值与历史变体。
 */
@DisplayName("CustomerTypeNormalizer — 客户类型归一化纯函数")
class CustomerTypeNormalizerTest {

    @Test
    @DisplayName("英文枚举名：立项详情存储的 6 个枚举值全部归一到标准分类")
    void englishEnumNamesNormalizeToStandardCategories() {
        assertThat(CustomerTypeNormalizer.normalize("GOVERNMENT")).isEqualTo("政府机关/事业单位/高校");
        assertThat(CustomerTypeNormalizer.normalize("CENTRAL_SOE")).isEqualTo("央企");
        assertThat(CustomerTypeNormalizer.normalize("LOCAL_SOE")).isEqualTo("地方国企");
        assertThat(CustomerTypeNormalizer.normalize("PRIVATE")).isEqualTo("民企");
        assertThat(CustomerTypeNormalizer.normalize("FOREIGN")).isEqualTo("港澳台及外企");
        // OTHER 无对应标准分类，不计数
        assertThat(CustomerTypeNormalizer.normalize("OTHER")).isNull();
    }

    @Test
    @DisplayName("英文枚举大小写不敏感")
    void englishEnumNamesAreCaseInsensitive() {
        assertThat(CustomerTypeNormalizer.normalize("central_soe")).isEqualTo("央企");
        assertThat(CustomerTypeNormalizer.normalize("Government")).isEqualTo("政府机关/事业单位/高校");
    }

    @Test
    @DisplayName("中文标准值：自映射保持原值")
    void chineseStandardValuesMapToThemselves() {
        for (String standard : CustomerTypeNormalizer.ALLOWED_CATEGORIES) {
            assertThat(CustomerTypeNormalizer.normalize(standard)).isEqualTo(standard);
        }
    }

    @Test
    @DisplayName("中文变体：常见写法归一到对应标准分类")
    void chineseVariantsNormalizeToStandardCategories() {
        assertThat(CustomerTypeNormalizer.normalize("政府")).isEqualTo("政府机关/事业单位/高校");
        assertThat(CustomerTypeNormalizer.normalize("事业单位")).isEqualTo("政府机关/事业单位/高校");
        assertThat(CustomerTypeNormalizer.normalize("高校")).isEqualTo("政府机关/事业单位/高校");
        assertThat(CustomerTypeNormalizer.normalize("中央企业")).isEqualTo("央企");
        assertThat(CustomerTypeNormalizer.normalize("国有企业")).isEqualTo("地方国企");
        assertThat(CustomerTypeNormalizer.normalize("民营企业")).isEqualTo("民企");
        assertThat(CustomerTypeNormalizer.normalize("外资企业")).isEqualTo("港澳台及外企");
    }

    @Test
    @DisplayName("首尾空白：trim 后正常归一化")
    void surroundingWhitespaceIsTrimmed() {
        assertThat(CustomerTypeNormalizer.normalize("  央企  ")).isEqualTo("央企");
        assertThat(CustomerTypeNormalizer.normalize(" CENTRAL_SOE ")).isEqualTo("央企");
    }

    @Test
    @DisplayName("null/空白/无法识别：返回 null（调用方跳过不统计）")
    void nullBlankAndUnknownReturnNull() {
        assertThat(CustomerTypeNormalizer.normalize(null)).isNull();
        assertThat(CustomerTypeNormalizer.normalize("")).isNull();
        assertThat(CustomerTypeNormalizer.normalize("   ")).isNull();
        assertThat(CustomerTypeNormalizer.normalize("未知类型")).isNull();
    }
}
