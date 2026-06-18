package com.xiyu.bid.integration.organization.dto;

import java.util.List;

/**
 * OSS 批量岗位/角色回查接口单条响应。
 *
 * @param jobNumber      员工工号
 * @param jobName        岗位名称
 * @param sysRoleList    系统角色名称列表
 * @param employeeStatus 在职状态
 * @param status         账号状态
 * @param username       用户姓名
 */
public record OssUserJobAndRoleDto(
        String jobNumber,
        String jobName,
        List<String> sysRoleList,
        String employeeStatus,
        String status,
        String username
) {
}
