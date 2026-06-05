// Input: 解析后的字段列表 + 可见性规则 + 用户角色集合
// Output: 应用角色权限后的字段列表
// Pos: Application 层（纯函数，无状态，可单测）
// 维护声明: 角色匹配与优先级逻辑全部在此，无框架依赖.
package com.xiyu.bid.formengine.application;

import com.xiyu.bid.formengine.domain.FieldVisibility;
import com.xiyu.bid.formengine.domain.ResolvedField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于角色的字段权限过滤器。
 * <p>
 * 优先级规则：
 * <ol>
 *   <li>hidden = true 的规则优先于所有其他状态</li>
 *   <li>同一字段存在多条规则时，role_pattern 精确匹配的优先于模糊匹配的</li>
 *   <li>没有 role_pattern（全局规则）的优先级最低</li>
 * </ol>
 */
@Component
public class RoleBasedFieldFilter {

    /**
     * 应用角色权限到字段列表。
     *
     * @param fields          原始解析字段
     * @param visibilityRules  可见性规则列表
     * @param userRoles        用户角色集合（从 JWT 或用户服务获取）
     * @return 应用权限后的字段列表（全新实例，不修改原字段）
     */
    public List<ResolvedField> apply(
            List<ResolvedField> fields,
            List<FieldVisibility> visibilityRules,
            Set<String> userRoles) {

        // 按 fieldKey 分组规则
        Map<String, List<FieldVisibility>> rulesByField = visibilityRules.stream()
                .collect(Collectors.groupingBy(FieldVisibility::fieldKey));

        return fields.stream()
                .map(field -> applyToField(field, rulesByField.get(field.key()), userRoles))
                .toList();
    }

    private ResolvedField applyToField(
            ResolvedField field,
            List<FieldVisibility> rules,
            Set<String> userRoles) {

        boolean hidden = field.hidden();
        boolean readonly = field.readonly();

        if (rules == null || rules.isEmpty()) {
            return field; // 无规则，保持原样
        }

        // 分离：有角色匹配的规则 vs 全局规则（rolePattern == null）
        List<FieldVisibility> roleRules = rules.stream()
                .filter(r -> r.rolePattern() != null)
                .toList();
        List<FieldVisibility> globalRules = rules.stream()
                .filter(r -> r.rolePattern() == null)
                .toList();

        // 找出匹配当前用户角色的最具体规则
        FieldVisibility matchedRoleRule = findBestMatchingRoleRule(roleRules, userRoles);

        // 决定最终状态
        FieldVisibility primaryRule = matchedRoleRule != null ? matchedRoleRule : null;
        if (primaryRule == null && !globalRules.isEmpty()) {
            // 合并所有全局规则（取最严格的）
            primaryRule = mergeGlobalRules(globalRules);
        }

        if (primaryRule != null) {
            // hidden 优先级最高
            if (primaryRule.hidden()) {
                hidden = true;
            }
            // readonly 在 hidden=false 时生效
            if (!hidden && primaryRule.readonly()) {
                readonly = true;
            }
        }

        return ResolvedField.builder()
                .key(field.key())
                .label(field.label())
                .type(field.type())
                .required(field.required())
                .hidden(hidden)
                .readonly(readonly)
                .defaultValue(field.defaultValue())
                .options(field.options())
                .build();
    }

    /**
     * 查找最佳匹配的角色规则。
     * 匹配策略：精确匹配 rolePattern > 通配符匹配（包含）
     */
    private FieldVisibility findBestMatchingRoleRule(List<FieldVisibility> roleRules, Set<String> userRoles) {
        FieldVisibility best = null;
        int bestScore = -1;

        for (FieldVisibility rule : roleRules) {
            String pattern = rule.rolePattern();
            int score = 0;

            if (userRoles.contains(pattern)) {
                score = 10; // 精确匹配
            } else if (pattern != null && userRoles.stream().anyMatch(role -> role.contains(pattern) || pattern.contains(role))) {
                score = 5; // 模糊匹配
            }

            if (score > bestScore) {
                bestScore = score;
                best = rule;
            }
        }

        return bestScore > 0 ? best : null;
    }

    /**
     * 合并全局规则：hidden 取 OR，readonly 取 OR（取最严格）。
     */
    private FieldVisibility mergeGlobalRules(List<FieldVisibility> globalRules) {
        boolean anyHidden = globalRules.stream().anyMatch(FieldVisibility::hidden);
        boolean anyReadonly = globalRules.stream().anyMatch(FieldVisibility::readonly);
        // visible 为 false 时语义不清晰，仅在有规则明确指定 visible=true 时才设为 true
        boolean hasVisibleTrue = globalRules.stream().anyMatch(FieldVisibility::visible);
        boolean computedVisible = !anyHidden && hasVisibleTrue;

        return FieldVisibility.of(
                globalRules.get(0).fieldKey(),
                computedVisible,
                anyReadonly,
                anyHidden
        );
    }
}
