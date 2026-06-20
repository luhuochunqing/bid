package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 决策表测试：TenderIntegrationResolver.resolveTender 的输入组合覆盖。
 * <p>背景：PR #877 修复了 sourceSystem="_" + sourceId="_" + tenderId 非空 时
 * 交叉校验误触发 400 的 bug。本测试用决策表锁定所有边界组合，防止回归。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenderIntegrationResolverTest {

    @Mock private TenderRepository tenderRepository;

    private TenderIntegrationResolver resolver;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        resolver = new TenderIntegrationResolver(tenderRepository);
    }

    private Tender tenderWithExternalId(Long id, String externalId) {
        Tender t = new Tender();
        t.setId(id);
        t.setExternalId(externalId);
        return t;
    }

    // ── 决策表：tenderId 非空分支 ────────────────────────────────────────────

    @Test
    @DisplayName("tenderId 非空 + sourceSystem/sourceId 为占位符 _ → 跳过交叉校验，直接返回（PR #877 修复场景）")
    void resolveTender_placeholderSource_skipCrossCheck() {
        Tender tender = tenderWithExternalId(295L, "CRM:265");
        when(tenderRepository.findById(295L)).thenReturn(Optional.of(tender));

        Tender result = resolver.resolveTender("_", "_", 295L);

        assertThat(result).isSameAs(tender);
    }

    @Test
    @DisplayName("tenderId 非空 + sourceSystem/sourceId 为 null → 跳过交叉校验，直接返回")
    void resolveTender_nullSource_skipCrossCheck() {
        Tender tender = tenderWithExternalId(295L, "CRM:265");
        when(tenderRepository.findById(295L)).thenReturn(Optional.of(tender));

        Tender result = resolver.resolveTender(null, null, 295L);

        assertThat(result).isSameAs(tender);
    }

    @Test
    @DisplayName("tenderId 非空 + 有效 sourceSystem/sourceId + externalId 匹配 → 交叉校验通过")
    void resolveTender_validSourceMatched_crossCheckPasses() {
        Tender tender = tenderWithExternalId(295L, "CRM:265");
        when(tenderRepository.findById(295L)).thenReturn(Optional.of(tender));

        Tender result = resolver.resolveTender("CRM", "265", 295L);

        assertThat(result).isSameAs(tender);
    }

    @Test
    @DisplayName("tenderId 非空 + 有效 sourceSystem/sourceId + externalId 不匹配 → 抛 IllegalArgumentException")
    void resolveTender_validSourceMismatched_throws() {
        Tender tender = tenderWithExternalId(295L, "CRM:265");
        when(tenderRepository.findById(295L)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> resolver.resolveTender("CRM", "999", 295L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不匹配")
                .hasMessageContaining("295");
    }

    @Test
    @DisplayName("tenderId 非空 + tender 不存在 → 抛 ResourceNotFoundException")
    void resolveTender_tenderIdNotFound_throws() {
        when(tenderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveTender("_", "_", 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── 决策表：tenderId 为空分支 ────────────────────────────────────────────

    @Test
    @DisplayName("tenderId 为空 + 有效 sourceSystem/sourceId + externalId 存在 → 按 externalId 查询")
    void resolveTender_validSourceNoTenderId_queryByExternalId() {
        Tender tender = tenderWithExternalId(295L, "CRM:265");
        when(tenderRepository.findByExternalId("CRM:265")).thenReturn(Optional.of(tender));

        Tender result = resolver.resolveTender("CRM", "265", null);

        assertThat(result).isSameAs(tender);
    }

    @Test
    @DisplayName("tenderId 为空 + 有效 sourceSystem/sourceId + externalId 不存在 → 抛 ResourceNotFoundException")
    void resolveTender_validSourceNoTenderId_notFound_throws() {
        when(tenderRepository.findByExternalId("CRM:999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveTender("CRM", "999", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CRM:999");
    }

    // ── 决策表：占位符参数化（防 PR #877 回归） ───────────────────────────────

    /**
     * 占位符组合矩阵：tenderId 非空时，所有占位符变体都应跳过交叉校验。
     * 若有人回退 hasExternalSource 逻辑（如删掉 !"_".equals(...)），本测试会立即失败。
     */
    @ParameterizedTest(name = "[{index}] sourceSystem=\"{0}\", sourceId=\"{1}\" → 跳过交叉校验")
    @CsvSource({
        "_, _",
        "'', ''",
        "_, null",
        "null, _",
        "'  ', '  '"
    })
    @DisplayName("占位符/空白组合 + tenderId 非空 → 全部跳过交叉校验")
    void resolveTender_allPlaceholderVariants_skipCrossCheck(String sourceSystem, String sourceId) {
        Tender tender = tenderWithExternalId(295L, "CRM:265");
        when(tenderRepository.findById(295L)).thenReturn(Optional.of(tender));

        Tender result = resolver.resolveTender(sourceSystem, sourceId, 295L);

        assertThat(result).isSameAs(tender);
    }

    @Test
    @DisplayName("tenderId 为空 + 占位符 sourceSystem/sourceId → 抛'至少传一组'")
    void resolveTender_placeholderNoTenderId_throws() {
        assertThatThrownBy(() -> resolver.resolveTender("_", "_", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要传一组");
    }
}
