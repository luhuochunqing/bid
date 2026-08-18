package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 部门树形结构 DTO — 前端 el-tree-select 使用 label/value/children 三字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDepartmentTreeDTO {
    private String label;
    private String value;
    private List<AnalyticsDepartmentTreeDTO> children;
}
