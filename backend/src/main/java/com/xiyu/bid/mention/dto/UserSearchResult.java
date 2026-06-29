package com.xiyu.bid.mention.dto;

/**
 * 用户搜索结果 DTO（纯数据载体）。
 *
 * <p>CO-392: 新增 phone / email 字段，供绑定联系人场景联动回填。
 */
public record UserSearchResult(Long id, String name, String employeeNumber, String role, String departmentName,
                               String roleCode, String phone, String email) {
}
