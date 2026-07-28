// Input: none (constant holder)
// Output: assertions that PurchaserAliases covers business-mandated 7 labels + legacy compatibility
// Pos: biddraftagent/infrastructure/openai — constant contract test
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 招标主体别名常量类契约测试。
 *
 * <p>业务约定招标文件中可识别为"招标主体"的字段共 7 种：
 * 招标人、招标单位、采购人、采购单位、项目单位、实施单位、需求单位。
 * 另保留历史口径"业主单位"作为兼容别名。
 *
 * <p>该测试作为防回归门禁：常量类被修改时必须同步更新 Prompt 字段口径与
 * INTAKE_KEYWORDS 关键词列表，否则会出现"AI 看到候选文本但 prompt 没告诉它
 * 是 purchaserName"或"关键词没保留该行导致 AI 根本看不到"的同步漂移问题。
 */
class PurchaserAliasesTest {

    @Test
    void shouldContainAllBusinessMandatedLabels() {
        // 业务约定 7 种招标主体别名
        List<String> businessMandated = List.of(
                "招标人",
                "招标单位",
                "采购人",
                "采购单位",
                "项目单位",
                "实施单位",
                "需求单位"
        );

        for (String alias : businessMandated) {
            assertThat(PurchaserAliases.ALL)
                    .as("业务约定招标主体别名必须包含：%s", alias)
                    .contains(alias);
        }
    }

    @Test
    void shouldContainLegacyCompatibilityLabel() {
        // 历史口径兼容（曾经出现在 prompt 中的标签）
        assertThat(PurchaserAliases.ALL).contains("业主单位");
    }

    @Test
    void shouldContainExactlyEightAliases() {
        // 7 业务 + 1 兼容 = 8
        // 多了或少了必须显式更新此测试，避免静默漂移
        assertThat(PurchaserAliases.ALL).hasSize(8);
    }

    @Test
    void displayShouldJoinAllAliasesWithSlash() {
        assertThat(PurchaserAliases.DISPLAY)
                .isEqualTo("招标人/招标单位/采购人/采购单位/项目单位/实施单位/需求单位/业主单位");
    }

    @Test
    void allAliasesShouldBeNonBlankAndUnique() {
        for (String alias : PurchaserAliases.ALL) {
            assertThat(alias).as("别名不得为空白").isNotBlank();
        }
        long distinctCount = PurchaserAliases.ALL.stream().distinct().count();
        assertThat(PurchaserAliases.ALL)
                .as("别名不得重复")
                .hasSize((int) distinctCount);
    }
}
