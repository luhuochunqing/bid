// Input: 字段定义 + 可见性规则
// Output: 解析后的字段（用于前端渲染）
// Pos: Domain 层（纯数据，不含框架依赖）
// 维护声明: 纯记录对象，业务规则在 application 层.
package com.xiyu.bid.formengine.domain;

/**
 * 解析后的表单字段，反映了可见性/只读/隐藏规则。
 */
public record ResolvedField(
        String key,
        String label,
        String type,
        boolean required,
        boolean hidden,
        boolean readonly,
        Object defaultValue,
        Object options  // for select fields: List<FieldOption>
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String key;
        private String label;
        private String type;
        private boolean required;
        private boolean hidden;
        private boolean readonly;
        private Object defaultValue;
        private Object options;

        public Builder key(String key) { this.key = key; return this; }
        public Builder label(String label) { this.label = label; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder required(boolean required) { this.required = required; return this; }
        public Builder hidden(boolean hidden) { this.hidden = hidden; return this; }
        public Builder readonly(boolean readonly) { this.readonly = readonly; return this; }
        public Builder defaultValue(Object defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder options(Object options) { this.options = options; return this; }

        public ResolvedField build() {
            return new ResolvedField(key, label, type, required, hidden, readonly, defaultValue, options);
        }
    }
}
