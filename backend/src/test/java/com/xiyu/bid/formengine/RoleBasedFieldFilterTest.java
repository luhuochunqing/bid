package com.xiyu.bid.formengine;

import com.xiyu.bid.formengine.application.RoleBasedFieldFilter;
import com.xiyu.bid.formengine.domain.FieldVisibility;
import com.xiyu.bid.formengine.domain.ResolvedField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoleBasedFieldFilter 单元测试。
 * 测试角色模式匹配规则：
 * - exact role match: "admin" matches "admin"
 * - wildcard: "*" matches any role
 * - no match: "admin" doesn't match "staff" → use default rule
 * - priority: exact match > wildcard > default
 * - hidden overrides readonly overrides visible
 */
@DisplayName("RoleBasedFieldFilter")
class RoleBasedFieldFilterTest {

    private RoleBasedFieldFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RoleBasedFieldFilter();
    }

    private ResolvedField field(String key) {
        return ResolvedField.builder()
                .key(key)
                .label(key + " label")
                .type("TEXT")
                .required(false)
                .hidden(false)
                .readonly(false)
                .build();
    }

    // ==================== Priority Tests ====================

    @Nested
    @DisplayName("优先级规则")
    class Priority {

        @Test
        @DisplayName("精确匹配优先于模糊匹配")
        void exactMatchWinsOverFuzzy() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "admin", true, false, false),    // exact match → score=10
                    FieldVisibility.withRole("f1", "ad", true, false, true)          // fuzzy match → score=5, hidden=true
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("admin"));

            assertThat(result.get(0).hidden()).isFalse();
            assertThat(result.get(0).readonly()).isFalse();
        }

        @Test
        @DisplayName("无规则字段保持原样")
        void noRules_fieldUnchanged() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<ResolvedField> result = filter.apply(fields, List.of(), Set.of("admin"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).hidden()).isFalse();
            assertThat(result.get(0).readonly()).isFalse();
        }

        @Test
        @DisplayName("空规则列表保持原样")
        void emptyRules_fieldUnchanged() {
            List<ResolvedField> fields = List.of(field("f1"), field("f2"));
            List<ResolvedField> result = filter.apply(fields, List.of(), Set.of("admin"));

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(f -> !f.hidden() && !f.readonly());
        }
    }

    // ==================== Role Pattern Matching ====================

    @Nested
    @DisplayName("角色模式匹配")
    class RolePatternMatching {

        @Test
        @DisplayName("精确匹配：admin 匹配 admin")
        void exactRoleMatch() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "admin", true, false, false)
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("admin"));

            assertThat(result.get(0).readonly()).isFalse();
            assertThat(result.get(0).hidden()).isFalse();
        }

        @Test
        @DisplayName("通配符 * 匹配任何角色")
        void wildcardMatchesAnyRole() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "*", true, false, false)
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("staff"));
            assertThat(result.get(0).readonly()).isFalse();

            result = filter.apply(fields, rules, Set.of("admin"));
            assertThat(result.get(0).readonly()).isFalse();
        }

        @Test
        @DisplayName("admin 不匹配 staff → 使用全局规则")
        void noMatch_usesGlobalRule() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "staff", true, false, false),  // admin 不匹配 staff
                    FieldVisibility.of("f1", false, true, false)                    // 全局规则：readonly=true
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("admin"));

            assertThat(result.get(0).readonly()).isTrue();
            assertThat(result.get(0).hidden()).isFalse();
        }

        @Test
        @DisplayName("模糊匹配：role 包含 pattern")
        void fuzzyMatch_contains() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "bid-Team", true, false, false)
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("staff", "bid-Team"));

            // exact match → score=10
            assertThat(result.get(0).readonly()).isFalse();
            assertThat(result.get(0).hidden()).isFalse();
        }

        @Test
        @DisplayName("模糊匹配：pattern 包含 role")
        void fuzzyMatch_patternContainsRole() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "Team", true, false, false)
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("bid-Team"));

            assertThat(result.get(0).readonly()).isFalse();
        }

        @Test
        @DisplayName("无匹配角色且无全局规则 → 保持原样")
        void noMatchNoGlobal_unchanged() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "admin", true, false, false)
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("staff"));

            assertThat(result.get(0).hidden()).isFalse();
            assertThat(result.get(0).readonly()).isFalse();
        }
    }

    // ==================== State Override ====================

    @Nested
    @DisplayName("状态覆盖规则")
    class StateOverride {

        @Test
        @DisplayName("hidden 覆盖 readonly")
        void hiddenOverridesReadonly() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "admin", false, true, true)  // hidden=true
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("admin"));

            assertThat(result.get(0).hidden()).isTrue();
            assertThat(result.get(0).readonly()).isFalse();  // readonly 被 hidden 覆盖
        }

        @Test
        @DisplayName("readonly 在 hidden=false 时生效")
        void readonlyWorksWhenNotHidden() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "admin", false, true, false)  // readonly=true, hidden=false
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("admin"));

            assertThat(result.get(0).hidden()).isFalse();
            assertThat(result.get(0).readonly()).isTrue();
        }

        @Test
        @DisplayName("全局 hidden=true 覆盖任何状态")
        void globalHiddenOverrides() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.of("f1", false, false, true)  // 全局 hidden=true
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("admin"));

            assertThat(result.get(0).hidden()).isTrue();
        }

        @Test
        @DisplayName("多条全局规则合并取最严格")
        void multipleGlobalRulesMerge() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.of("f1", true, false, false),    // visible=true
                    FieldVisibility.of("f1", false, true, false)    // readonly=true
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("admin"));

            assertThat(result.get(0).readonly()).isTrue();
            assertThat(result.get(0).hidden()).isFalse();
        }
    }

    // ==================== Multi-field ====================

    @Nested
    @DisplayName("多字段处理")
    class MultiField {

        @Test
        @DisplayName("多个字段各自应用规则")
        void multipleFields() {
            List<ResolvedField> fields = List.of(field("f1"), field("f2"), field("f3"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "admin", true, false, false),
                    FieldVisibility.withRole("f2", "admin", false, true, false),
                    FieldVisibility.of("f3", false, false, true)
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("admin"));

            assertThat(result).hasSize(3);
            assertThat(result.get(0).readonly()).isFalse();  // f1: visible
            assertThat(result.get(1).readonly()).isTrue();   // f2: readonly
            assertThat(result.get(2).hidden()).isTrue();    // f3: hidden
        }

        @Test
        @DisplayName("多角色用户：得分最高的规则生效")
        void multiRoleUser_bestMatchWins() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "manager", false, true, false),       // score=10 for "manager"
                    FieldVisibility.withRole("f1", "staff", true, false, true)           // score=10 for "staff", hidden=true
            );
            // "manager" appears first → checked first → score=10, bestScore=10
            // "staff" is checked second → exact match → score=10, NOT > bestScore, skipped
            // So manager (readonly=true, hidden=false) wins

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("staff", "manager"));

            assertThat(result.get(0).readonly()).isTrue();
            assertThat(result.get(0).hidden()).isFalse();
        }
    }

    // ==================== Null Safety ====================

    @Nested
    @DisplayName("空安全")
    class NullSafety {

        @Test
        @DisplayName("null rolePattern → 全局规则")
        void nullRolePattern_isGlobalRule() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.of("f1", false, true, false)  // rolePattern=null → 全局
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of("any_role"));

            assertThat(result.get(0).readonly()).isTrue();
        }

        @Test
        @DisplayName("空角色集合 → 仅匹配全局规则")
        void emptyUserRoles_matchesGlobalOnly() {
            List<ResolvedField> fields = List.of(field("f1"));
            List<FieldVisibility> rules = List.of(
                    FieldVisibility.withRole("f1", "admin", true, false, false),
                    FieldVisibility.of("f1", false, true, false)
            );

            List<ResolvedField> result = filter.apply(fields, rules, Set.of());

            // 无角色匹配，使用全局规则
            assertThat(result.get(0).readonly()).isTrue();
        }
    }
}
