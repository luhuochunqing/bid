package com.xiyu.bid.personnel.application.response;

import com.xiyu.bid.personnel.application.dto.PersonnelDTO;

import java.util.List;

/**
 * 编辑人员接口返回结果（编辑证书子节专用）。
 * 比通用 DTO 多带了执行过程中的警示信息（非阻塞）。
 */
public record PersonnelEditResponse(
        PersonnelDTO personnel,
        List<String> warnings
) {
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
