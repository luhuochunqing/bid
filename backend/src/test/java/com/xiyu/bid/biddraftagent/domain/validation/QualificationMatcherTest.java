// Input: QualificationMatcher（isSmartMatch + match 三态）
// Output: 资质匹配行为验证
// Pos: Test/biddraftagent/domain/validation
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.biddraftagent.domain.validation;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationMatcherTest {

    private QualificationMatcher matcher;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        matcher = new QualificationMatcher();
        today = LocalDate.of(2026, 6, 1);
    }

    // ── isSmartMatch 单元测试（委托给 SmartMatchUtils） ─────────────────────

    @Test
    void isSmartMatch_shortName_shouldNotMatchSubstring() {
        assertThat(matcher.isSmartMatch("ISOLATED system", "ISO")).isFalse();
    }

    @Test
    void isSmartMatch_shortName_exactWord_shouldMatch() {
        assertThat(matcher.isSmartMatch("符合 ISO 认证要求", "ISO")).isTrue();
    }

    @Test
    void isSmartMatch_shortName_caseInsensitive_shouldMatch() {
        assertThat(matcher.isSmartMatch("已取得 iso 认证", "ISO")).isTrue();
    }

    @Test
    void isSmartMatch_longName_substringMatch_shouldMatch() {
        assertThat(matcher.isSmartMatch("需具备ISO9001质量管理体系认证", "ISO9001质量管理体系")).isTrue();
    }

    @Test
    void isSmartMatch_longName_caseInsensitive_shouldMatch() {
        assertThat(matcher.isSmartMatch("需具备iso9001质量管理体系认证", "ISO9001质量管理体系")).isTrue();
    }

    @Test
    void isSmartMatch_longName_notPresent_shouldNotMatch() {
        assertThat(matcher.isSmartMatch("需要安全生产许可证", "ISO9001质量管理体系")).isFalse();
    }

    @Test
    void isSmartMatch_chineseQualification_shouldMatch() {
        assertThat(matcher.isSmartMatch("投标人须持有建筑工程施工总承包一级资质", "建筑工程施工总承包一级")).isTrue();
    }

    @Test
    void isSmartMatch_chineseQualification_notPresent_shouldNotMatch() {
        assertThat(matcher.isSmartMatch("投标人须持有市政工程总承包一级资质", "建筑工程施工总承包一级")).isFalse();
    }

    // ── match() 三态场景 ─────────────────────────────────────────────────────

    @Test
    void match_emptyRequirements_shouldReturnEmptyItems() {
        QualificationMatchResult result = matcher.match(List.of(), List.of(), today);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void match_nullRequirements_shouldReturnEmptyItems() {
        QualificationMatchResult result = matcher.match(null, List.of(), today);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void match_exactMatch_shouldBeSatisfied() {
        QualificationSummary qual = qual(1L, "建筑工程施工总承包一级", null);

        QualificationMatchResult result = matcher.match(
                List.of("投标人须持有建筑工程施工总承包一级资质"),
                List.of(qual), today);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).status()).isEqualTo(QualificationMatchStatus.SATISFIED);
        assertThat(result.items().get(0).matchedQualificationName()).isEqualTo("建筑工程施工总承包一级");
    }

    @Test
    void match_noMatchingQualification_shouldBeUnsatisfied() {
        QualificationSummary qual = qual(1L, "市政工程总承包一级", null);

        QualificationMatchResult result = matcher.match(
                List.of("需持有建筑工程施工总承包一级资质"),
                List.of(qual), today);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).status()).isEqualTo(QualificationMatchStatus.UNSATISFIED);
    }

    @Test
    void match_certificateExpiringSoon_shouldBeAttention() {
        // 30天后到期 ≤ 60天阈值
        QualificationSummary qual = qual(1L, "建筑工程施工总承包一级", today.plusDays(30));

        QualificationMatchResult result = matcher.match(
                List.of("投标人须持有建筑工程施工总承包一级资质"),
                List.of(qual), today);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).status()).isEqualTo(QualificationMatchStatus.ATTENTION);
        assertThat(result.items().get(0).remainingDays()).isNotNull();
        assertThat(result.items().get(0).remainingDays()).isLessThanOrEqualTo(QualificationMatcher.EXPIRY_WARN_DAYS);
    }

    @Test
    void match_certificateExpiryJustOverThreshold_shouldBeSatisfied() {
        // 90天后到期 > 60天阈值
        QualificationSummary qual = qual(1L, "建筑工程施工总承包一级", today.plusDays(90));

        QualificationMatchResult result = matcher.match(
                List.of("投标人须持有建筑工程施工总承包一级资质"),
                List.of(qual), today);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).status()).isEqualTo(QualificationMatchStatus.SATISFIED);
    }

    @Test
    void match_mixedCaseShortName_shouldNotFalselyMatch() {
        QualificationSummary qual = qual(1L, "ISO", null);

        QualificationMatchResult result = matcher.match(
                List.of("ISOLATED system requirement"),
                List.of(qual), today);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).status()).isEqualTo(QualificationMatchStatus.UNSATISFIED);
    }

    @Test
    void match_emptyQualificationList_allShouldBeUnsatisfied() {
        QualificationMatchResult result = matcher.match(
                List.of("需持有资质A", "需持有资质B"),
                List.of(), today);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allMatch(item -> item.status() == QualificationMatchStatus.UNSATISFIED);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static QualificationSummary qual(Long id, String name, LocalDate expiryDate) {
        return new QualificationSummary(id, name, expiryDate);
    }
}
