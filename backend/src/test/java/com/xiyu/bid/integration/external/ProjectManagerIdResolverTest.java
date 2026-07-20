package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.service.UserEnabledStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock private UserEnabledStatusService userEnabledStatusService;
    private ProjectManagerIdResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProjectManagerIdResolver(userRepository, userEnabledStatusService);
        // 默认所有用户启用，停用测试用例单独覆盖
        when(userEnabledStatusService.isEnabled(any(User.class))).thenReturn(true);
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

    // ── v3.10 工号优先解析测试 ────────────────────────────────────────────────

    @Test
    @DisplayName("v3.10: resolveByEmployeeNumber 唯一匹配 → 返回 user_id")
    void resolveByEmployeeNumber_uniqueMatch_returnsId() {
        when(userRepository.findByEmployeeNumber("E001234"))
                .thenReturn(Optional.of(User.builder().id(200L).employeeNumber("E001234").fullName("张三").build()));

        assertThat(resolver.resolveByEmployeeNumber("E001234")).isEqualTo(200L);
    }

    @Test
    @DisplayName("v3.10: resolveByEmployeeNumber 无匹配 → 返回 null")
    void resolveByEmployeeNumber_noMatch_returnsNull() {
        when(userRepository.findByEmployeeNumber("NOT_EXIST")).thenReturn(Optional.empty());

        assertThat(resolver.resolveByEmployeeNumber("NOT_EXIST")).isNull();
    }

    @Test
    @DisplayName("v3.10: resolveByEmployeeNumber null/空白 → 返回 null（不查库）")
    void resolveByEmployeeNumber_blank_returnsNull() {
        assertThat(resolver.resolveByEmployeeNumber(null)).isNull();
        assertThat(resolver.resolveByEmployeeNumber("   ")).isNull();
    }

    @Test
    @DisplayName("v3.10: resolveByEmployeeNumber 前后带空格 → trim 后查库")
    void resolveByEmployeeNumber_padded_trimsBeforeLookup() {
        when(userRepository.findByEmployeeNumber("E001234"))
                .thenReturn(Optional.of(User.builder().id(200L).employeeNumber("E001234").build()));

        assertThat(resolver.resolveByEmployeeNumber("  E001234  ")).isEqualTo(200L);
    }

    @Test
    @DisplayName("v3.10: 工号优先组合 - 工号命中 + 姓名一致 → 返回工号对应 user_id")
    void resolveByEmployeeNumberThenName_employeeMatchedNameConsistent_returnsId() {
        when(userRepository.findByEmployeeNumber("E001234"))
                .thenReturn(Optional.of(User.builder().id(200L).employeeNumber("E001234").fullName("张三").build()));

        assertThat(resolver.resolveByEmployeeNumberThenName("E001234", "张三")).isEqualTo(200L);
    }

    @Test
    @DisplayName("v3.10: 工号优先组合 - 工号命中但姓名不符 → 仍按工号落库（仅告警）")
    void resolveByEmployeeNumberThenName_employeeMatchedNameMismatch_stillReturnsId() {
        // CRM 传工号 E001234 + 姓名 "李四"，但库里 E001234 对应 fullName="张三"
        // 工号优先级高于姓名，按工号落库 user_id=200
        when(userRepository.findByEmployeeNumber("E001234"))
                .thenReturn(Optional.of(User.builder().id(200L).employeeNumber("E001234").fullName("张三").build()));

        assertThat(resolver.resolveByEmployeeNumberThenName("E001234", "李四")).isEqualTo(200L);
    }

    @Test
    @DisplayName("v3.10: 工号优先组合 - 工号未命中 → 回落姓名匹配")
    void resolveByEmployeeNumberThenName_employeeNotFound_fallsBackToName() {
        when(userRepository.findByEmployeeNumber("NOT_EXIST")).thenReturn(Optional.empty());
        when(userRepository.findByFullName("韩超"))
                .thenReturn(List.of(User.builder().id(25L).fullName("韩超").build()));

        assertThat(resolver.resolveByEmployeeNumberThenName("NOT_EXIST", "韩超")).isEqualTo(25L);
    }

    @Test
    @DisplayName("v3.10: 工号优先组合 - 工号空 → 直接回落姓名匹配")
    void resolveByEmployeeNumberThenName_employeeBlank_fallsBackToName() {
        when(userRepository.findByFullName("韩超"))
                .thenReturn(List.of(User.builder().id(25L).fullName("韩超").build()));

        assertThat(resolver.resolveByEmployeeNumberThenName(null, "韩超")).isEqualTo(25L);
        assertThat(resolver.resolveByEmployeeNumberThenName("  ", "韩超")).isEqualTo(25L);
    }

    @Test
    @DisplayName("v3.10: 工号优先组合 - 工号未命中 + 姓名重名 → 回落姓名后返回 null（避免误绑）")
    void resolveByEmployeeNumberThenName_employeeNotFoundAndNameDuplicate_returnsNull() {
        when(userRepository.findByEmployeeNumber("NOT_EXIST")).thenReturn(Optional.empty());
        when(userRepository.findByFullName("张伟")).thenReturn(List.of(
                User.builder().id(1L).fullName("张伟").build(),
                User.builder().id(2L).fullName("张伟").build()));

        assertThat(resolver.resolveByEmployeeNumberThenName("NOT_EXIST", "张伟")).isNull();
    }

    @Test
    @DisplayName("v3.10: 工号优先组合 - 工号和姓名都空 → 返回 null（不查库）")
    void resolveByEmployeeNumberThenName_bothBlank_returnsNull() {
        assertThat(resolver.resolveByEmployeeNumberThenName(null, null)).isNull();
        assertThat(resolver.resolveByEmployeeNumberThenName("  ", "  ")).isNull();
    }

    // ── v3.10 P0-1: username 回落测试 ────────────────────────────────────────

    @Test
    @DisplayName("v3.10: 工号未命中 employeeNumber，但命中 username → 返回 username 对应 user_id")
    void resolveByEmployeeNumberThenName_employeeNumberMiss_usernameFallback_returnsId() {
        // 场景：CRM 传 saleNo="08687"，本地 employee_number 没存这个值，但 username="08687"
        when(userRepository.findByEmployeeNumber("08687")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("08687"))
                .thenReturn(Optional.of(User.builder().id(5052L).username("08687").fullName("王凯毅").build()));

        assertThat(resolver.resolveByEmployeeNumberThenName("08687", null)).isEqualTo(5052L);
    }

    @Test
    @DisplayName("v3.10: 工号和 username 都未命中 → 回落姓名匹配")
    void resolveByEmployeeNumberThenName_employeeAndUsernameBothMiss_fallsBackToName() {
        when(userRepository.findByEmployeeNumber("NOT_EXIST")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("NOT_EXIST")).thenReturn(Optional.empty());
        when(userRepository.findByFullName("韩超"))
                .thenReturn(List.of(User.builder().id(25L).fullName("韩超").build()));

        assertThat(resolver.resolveByEmployeeNumberThenName("NOT_EXIST", "韩超")).isEqualTo(25L);
    }

    @Test
    @DisplayName("v3.10: resolveByEmployeeNumber 单独方法 - 工号未命中 → 返回 null（不回落 username，纯工号语义）")
    void resolveByEmployeeNumber_employeeMiss_returnsNull_noUsernameFallback() {
        // resolveByEmployeeNumber 是纯工号语义，不做 username 回落（与组合方法的语义区分）
        when(userRepository.findByEmployeeNumber("NOT_EXIST")).thenReturn(Optional.empty());

        assertThat(resolver.resolveByEmployeeNumber("NOT_EXIST")).isNull();
        verify(userRepository, never()).findByUsername(anyString());
    }

    // ── v3.10 P1-2: 停用过滤测试 ─────────────────────────────────────────────

    @Test
    @DisplayName("v3.10: resolveByFullName 命中但用户已停用 → 返回 null（不绑定离职员工）")
    void resolveByFullName_userDisabled_returnsNull() {
        User disabledUser = User.builder().id(300L).fullName("张三").enabled(false).build();
        when(userRepository.findByFullName("张三")).thenReturn(List.of(disabledUser));
        when(userEnabledStatusService.isEnabled(disabledUser)).thenReturn(false);

        assertThat(resolver.resolveByFullName("张三")).isNull();
    }

    @Test
    @DisplayName("v3.10: resolveByEmployeeNumber 命中但用户已停用 → 返回 null")
    void resolveByEmployeeNumber_userDisabled_returnsNull() {
        User disabledUser = User.builder().id(300L).employeeNumber("E001234").enabled(false).build();
        when(userRepository.findByEmployeeNumber("E001234")).thenReturn(Optional.of(disabledUser));
        when(userEnabledStatusService.isEnabled(disabledUser)).thenReturn(false);

        assertThat(resolver.resolveByEmployeeNumber("E001234")).isNull();
    }

    @Test
    @DisplayName("v3.10: resolveByEmployeeNumberThenName 工号命中但停用 → 返回 null，不回落姓名")
    void resolveByEmployeeNumberThenName_employeeMatchedButDisabled_returnsNull_noNameFallback() {
        // 工号命中但停用：直接返回 null，不会回落到姓名匹配
        // 因为既然工号能命中说明推的就是这个员工，员工停用了不应该绑定到其他同姓名员工
        User disabledUser = User.builder().id(300L).employeeNumber("E001234").fullName("张三").enabled(false).build();
        when(userRepository.findByEmployeeNumber("E001234")).thenReturn(Optional.of(disabledUser));
        when(userEnabledStatusService.isEnabled(disabledUser)).thenReturn(false);

        assertThat(resolver.resolveByEmployeeNumberThenName("E001234", "张三")).isNull();
        // 不应该再尝试姓名匹配
        verify(userRepository, never()).findByFullName(anyString());
    }

    @Test
    @DisplayName("v3.10: resolveByEmployeeNumberThenName username 回落命中但停用 → 返回 null")
    void resolveByEmployeeNumberThenName_usernameFallbackButDisabled_returnsNull() {
        User disabledUser = User.builder().id(300L).username("08687").fullName("王凯毅").enabled(false).build();
        when(userRepository.findByEmployeeNumber("08687")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("08687")).thenReturn(Optional.of(disabledUser));
        when(userEnabledStatusService.isEnabled(disabledUser)).thenReturn(false);

        assertThat(resolver.resolveByEmployeeNumberThenName("08687", null)).isNull();
    }

    @Test
    @DisplayName("v3.10: OSS 用户始终启用（isEnabled 返回 true）→ 正常返回 user_id")
    void resolveByEmployeeNumber_ossUser_alwaysEnabled() {
        // OSS 用户即使 enabled=false 也算启用（UserEnabledStatusService 逻辑）
        User ossUser = User.builder().id(400L).employeeNumber("OSS001").enabled(false).build();
        when(userRepository.findByEmployeeNumber("OSS001")).thenReturn(Optional.of(ossUser));
        when(userEnabledStatusService.isEnabled(ossUser)).thenReturn(true);  // OSS 用户特殊处理

        assertThat(resolver.resolveByEmployeeNumber("OSS001")).isEqualTo(400L);
    }

    // ── v3.10 第二轮 Review 补充用例 ──────────────────────────────────────────

    @Test
    @DisplayName("v3.10: 工号命中 + fullName 参数为 null → 跳过姓名校验，按工号落库")
    void resolveByEmployeeNumberThenName_employeeMatchedNameNull_skipsNameCheck() {
        // 边界：CRM 只传工号不传姓名，不应触发 NPE 或姓名校验告警
        when(userRepository.findByEmployeeNumber("E001234"))
                .thenReturn(Optional.of(User.builder().id(200L).employeeNumber("E001234").fullName("张三").build()));

        assertThat(resolver.resolveByEmployeeNumberThenName("E001234", null)).isEqualTo(200L);
    }

    @Test
    @DisplayName("v3.10: 工号命中但库中 user.fullName 为 null → 不抛 NPE，姓名校验跳过")
    void resolveByEmployeeNumberThenName_userFullNameNull_noNpe() {
        // 边界：库中 OSS/历史脏数据 user.fullName=null，CRM 传了姓名 "张三"
        // 3.7 修复点：用 Objects.hashCode 兜底 null，避免 NPE
        User userWithNullName = User.builder().id(500L).employeeNumber("E001234").fullName(null).build();
        when(userRepository.findByEmployeeNumber("E001234")).thenReturn(Optional.of(userWithNullName));

        // 不应抛 NPE，应正常返回 user_id（工号优先）
        assertThat(resolver.resolveByEmployeeNumberThenName("E001234", "张三")).isEqualTo(500L);
    }

    @Test
    @DisplayName("v3.10: username 回落命中但姓名不符 → 仍按 username 落库（仅告警，与 employeeNumber 路径行为一致）")
    void resolveByEmployeeNumberThenName_usernameFallbackNameMismatch_stillReturnsId() {
        // 3.2-b 修复点：username 回落路径也做姓名校验，但仅告警不阻断
        User user = User.builder().id(5052L).username("08687").fullName("王凯毅").build();
        when(userRepository.findByEmployeeNumber("08687")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("08687")).thenReturn(Optional.of(user));

        // CRM 传 saleNo=08687 + 姓名=李四，但库里 08687 对应 fullName=王凯毅 → 按 username 落库
        assertThat(resolver.resolveByEmployeeNumberThenName("08687", "李四")).isEqualTo(5052L);
    }

    @Test
    @DisplayName("v3.10: username 回落命中 + 姓名 null → 跳过姓名校验，按 username 落库")
    void resolveByEmployeeNumberThenName_usernameFallbackNameNull_skipsNameCheck() {
        User user = User.builder().id(5052L).username("08687").fullName("王凯毅").build();
        when(userRepository.findByEmployeeNumber("08687")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("08687")).thenReturn(Optional.of(user));

        assertThat(resolver.resolveByEmployeeNumberThenName("08687", null)).isEqualTo(5052L);
    }

    // ── v3.10 applyTo 端到端测试（不 mock resolver 内部，验证真实落库行为）─────────

    @Test
    @DisplayName("v3.10: applyTo 工号 + 姓名 → 工号命中，姓名字段写入 + user_id 落库")
    void applyTo_employeeIdAndName_writesBothFields() {
        Tender tender = new Tender();
        when(userRepository.findByEmployeeNumber("E001234"))
                .thenReturn(Optional.of(User.builder().id(200L).employeeNumber("E001234").fullName("张三").build()));

        resolver.applyTo(tender, "E001234", "张三");

        assertThat(tender.getProjectManagerName()).isEqualTo("张三");
        assertThat(tender.getProjectManagerId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("v3.10: applyTo 仅工号 → 解析 user_id 落库，姓名保持 null")
    void applyTo_employeeIdOnly_writesIdOnly() {
        Tender tender = new Tender();
        when(userRepository.findByEmployeeNumber("E001234"))
                .thenReturn(Optional.of(User.builder().id(200L).employeeNumber("E001234").fullName("张三").build()));

        resolver.applyTo(tender, "E001234", null);

        assertThat(tender.getProjectManagerName()).isNull();
        assertThat(tender.getProjectManagerId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("v3.10: applyTo 仅姓名 → 走 resolveByFullName 路径，姓名 + user_id 都落库")
    void applyTo_nameOnly_writesBothViaFullName() {
        Tender tender = new Tender();
        when(userRepository.findByFullName("张三"))
                .thenReturn(List.of(User.builder().id(200L).fullName("张三").build()));

        resolver.applyTo(tender, null, "张三");

        assertThat(tender.getProjectManagerName()).isEqualTo("张三");
        assertThat(tender.getProjectManagerId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("v3.10: applyTo 工号和姓名都空 → 不解析，保持 tender 已有 projectManagerId 不变")
    void applyTo_bothBlank_keepsExistingId() {
        Tender tender = new Tender();
        tender.setProjectManagerId(99L);  // 已有 id 不应被覆盖

        resolver.applyTo(tender, null, null);

        assertThat(tender.getProjectManagerId()).isEqualTo(99L);
        assertThat(tender.getProjectManagerName()).isNull();
    }

    @Test
    @DisplayName("v3.10: applyTo 工号命中但用户停用 → user_id 不落库（保持 null），姓名仍写入")
    void applyTo_employeeMatchedButDisabled_writesNameButNotId() {
        Tender tender = new Tender();
        User disabledUser = User.builder().id(300L).employeeNumber("E001234").fullName("张三").enabled(false).build();
        when(userRepository.findByEmployeeNumber("E001234")).thenReturn(Optional.of(disabledUser));
        when(userEnabledStatusService.isEnabled(disabledUser)).thenReturn(false);

        resolver.applyTo(tender, "E001234", "张三");

        // 姓名仍应写入（CRM 推过来的姓名保留，便于人工排查）
        assertThat(tender.getProjectManagerName()).isEqualTo("张三");
        // user_id 不落库（停用员工不绑定）
        assertThat(tender.getProjectManagerId()).isNull();
    }
}
