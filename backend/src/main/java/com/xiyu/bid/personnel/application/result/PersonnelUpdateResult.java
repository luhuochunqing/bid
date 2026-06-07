package com.xiyu.bid.personnel.application.result;

import com.xiyu.bid.personnel.application.dto.PersonnelDTO;

import java.util.List;

/**
 * 编辑人员返回结果（编辑证书子节）。
 * 包含更新后的数据 + 执行过程中产生的警示信息（非阻塞）。
 */
public record PersonnelUpdateResult(
        PersonnelDTO personnel,
        List<String> warnings   // 例如工号变更警示等
) {
    public PersonnelUpdateResult(PersonnelDTO personnel) {
        this(personnel, List.of());
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
