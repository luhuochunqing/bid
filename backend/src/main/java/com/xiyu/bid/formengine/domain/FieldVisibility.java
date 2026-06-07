// Input: 表单定义 JSON + 字段可见性规则列表
// Output: 可见性状态
// Pos: Domain 层（纯数据，不含框架依赖）
// 维护声明: 纯记录对象，评估逻辑在 application 层.
package com.xiyu.bid.formengine.domain;

/**
 * 字段可见性规则。
 */
public record FieldVisibility(
        Long id,
        String fieldKey,
        String rolePattern,
        Long orgId,
        boolean visible,
        boolean readonly,
        boolean hidden
) {

    public static FieldVisibility of(String fieldKey, boolean visible, boolean readonly, boolean hidden) {
        return new FieldVisibility(null, fieldKey, null, null, visible, readonly, hidden);
    }

    public static FieldVisibility withRole(String fieldKey, String rolePattern,
            boolean visible, boolean readonly, boolean hidden) {
        return new FieldVisibility(null, fieldKey, rolePattern, null, visible, readonly, hidden);
    }

    public static FieldVisibility withOrg(String fieldKey, Long orgId,
            boolean visible, boolean readonly, boolean hidden) {
        return new FieldVisibility(null, fieldKey, null, orgId, visible, readonly, hidden);
    }
}
