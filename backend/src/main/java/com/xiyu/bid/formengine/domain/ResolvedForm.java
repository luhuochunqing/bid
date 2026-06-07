package com.xiyu.bid.formengine.domain;

import java.util.List;

/**
 * 表单解析结果，包含 scope、label、字段列表和版本号。
 */
public record ResolvedForm(
        String scope,
        String scopeLabel,
        List<ResolvedField> fields,
        Integer version
) {

    public ResolvedForm {
        if (fields == null) fields = List.of();
    }
}
