package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CO-333: {@link ProjectManagerIdResolver} 单元测试。
 *
 * <p>覆盖唯一匹配/无匹配/重名/null/空字符串 5 个场景，
 * 与 {@link TenderIntegrationServiceMapToEntityTest} 中 mock resolver 的用例互补：
 * mapper 侧验证"调用 resolver + 写入 id"，本类验证 resolver 内部匹配规则。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CO-333: ProjectManagerIdResolver 姓名匹配规则")
class ProjectManagerIdResolverTest {

    @Mock private UserRepository userRepository;
    private ProjectManagerIdResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProjectManagerIdResolver(userRepository);
    }

    @Test
    @DisplayName("唯一匹配 → 返回 user_id")
    void resolveByFullName_uniqueMatch_returnsId() {
        when(userRepository.findByFullName("韩超"))
                .thenReturn(List.of(User.builder().id(25L).fullName("韩超").build()));

        assertThat(resolver.resolveByFullName("韩超")).isEqualTo(25L);
    }

    @Test
    @DisplayName("无匹配 → 返回 null（不阻断主流程）")
    void resolveByFullName_noMatch_returnsNull() {
        when(userRepository.findByFullName("不存在的人")).thenReturn(List.of());

        assertThat(resolver.resolveByFullName("不存在的人")).isNull();
    }

    @Test
    @DisplayName("重名 → 返回 null 避免误绑")
    void resolveByFullName_duplicateName_returnsNull() {
        when(userRepository.findByFullName("张伟")).thenReturn(List.of(
                User.builder().id(1L).fullName("张伟").build(),
                User.builder().id(2L).fullName("张伟").build()));

        assertThat(resolver.resolveByFullName("张伟")).isNull();
    }

    @Test
    @DisplayName("null 全名 → 返回 null（不查库）")
    void resolveByFullName_null_returnsNull() {
        assertThat(resolver.resolveByFullName(null)).isNull();
    }

    @Test
    @DisplayName("空白全名 → 返回 null（不查库）")
    void resolveByFullName_blank_returnsNull() {
        assertThat(resolver.resolveByFullName("   ")).isNull();
    }

    @Test
    @DisplayName("前后带空格的全名 → trim 后查库")
    void resolveByFullName_padded_trimsBeforeLookup() {
        when(userRepository.findByFullName("韩超"))
                .thenReturn(List.of(User.builder().id(25L).fullName("韩超").build()));

        assertThat(resolver.resolveByFullName("  韩超  ")).isEqualTo(25L);
    }

    // ── CO-333 模糊匹配增强测试 ────────────────────────────────────────────────

    @Test
    @DisplayName("CO-333: 姓名含全角中间点，库中存半角点 → 标准化后唯一匹配成功")
    void resolveByFullName_fullWidthMiddleDot_matchesHalfWidth() {
        // 推送来的姓名含全角中间点，库里存的是半角点
        when(userRepository.findByFullName("伊合巴来木.伊尼哈木"))
                .thenReturn(List.of(User.builder().id(100L).fullName("伊合巴来木.伊尼哈木").build()));

        assertThat(resolver.resolveByFullName("伊合巴来木·伊尼哈木")).isEqualTo(100L);
    }

    @Test
    @DisplayName("CO-333: 姓名含全角空格，库中存半角无空格 → 标准化后唯一匹配成功")
    void resolveByFullName_fullWidthSpaces_normalizedMatches() {
        when(userRepository.findByFullName("李雷"))
                .thenReturn(List.of(User.builder().id(101L).fullName("李雷").build()));

        assertThat(resolver.resolveByFullName("李　雷")).isEqualTo(101L);
    }

    @Test
    @DisplayName("CO-333: 姓名中间含多余空格 → 去空格后唯一匹配成功")
    void resolveByFullName_innerSpaces_removedMatches() {
        when(userRepository.findByFullName("张三"))
                .thenReturn(List.of(User.builder().id(102L).fullName("张三").build()));

        assertThat(resolver.resolveByFullName("张   三")).isEqualTo(102L);
        assertThat(resolver.resolveByFullName("张 三")).isEqualTo(102L);
    }

    @Test
    @DisplayName("CO-333: 全角字母/数字混在半角姓名中 → 标准化后唯一匹配成功")
    void resolveByFullName_fullWidthChars_normalizedMatches() {
        when(userRepository.findByFullName("TOM01"))
                .thenReturn(List.of(User.builder().id(103L).fullName("TOM01").build()));

        assertThat(resolver.resolveByFullName("TOM01")).isEqualTo(103L);
        assertThat(resolver.resolveByFullName("ＴＯＭ０１")).isEqualTo(103L);
    }

    @Test
    @DisplayName("CO-333: 标准化后出现重名 → 返回 null 避免误绑（谨慎优先）")
    void resolveByFullName_standardizationCausesDuplicate_returnsNull() {
        // "张 三" 和 "张山" 标准化后都是 "张 三"（去空格后碰巧相同，或标准化后重名）
        when(userRepository.findByFullName("张 三")).thenReturn(List.of(
                User.builder().id(104L).fullName("张 三").build(),
                User.builder().id(105L).fullName("张山").build()));

        assertThat(resolver.resolveByFullName("张 三")).isNull();
    }

    @Test
    @DisplayName("CO-333: 精确匹配失败，标准化后唯一匹配成功（trim + 去空格 + 中间点标准化）")
    void resolveByFullName_exactFails_standardizedMatches_returnsId() {
        // 输入 "王凯 毅 " → trim 后 "王凯 毅"，标准化后 "王凯毅"
        // 精确匹配 "王凯 毅" 失败，标准化匹配 "王凯毅" 成功
        when(userRepository.findByFullName("王凯 毅")).thenReturn(List.of());  // 精确匹配失败
        when(userRepository.findByFullName("王凯毅")).thenReturn(List.of(User.builder().id(106L).fullName("王凯毅").build()));  // 标准化后匹配成功

        assertThat(resolver.resolveByFullName("王凯 毅 ")).isEqualTo(106L);
    }
}
