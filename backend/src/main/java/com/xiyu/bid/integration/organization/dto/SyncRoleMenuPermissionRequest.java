package com.xiyu.bid.integration.organization.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员触发从 OSS 同步角色菜单权限的请求。
 */
public record SyncRoleMenuPermissionRequest(
        @NotBlank(message = "工号不能为空")
        String jobNumber
) {
}
