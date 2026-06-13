package com.xiyu.bid.systems.external;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部系统菜单响应（客户方统一组织架构系统拉取菜单的入参格式）.
 *
 * <p>与 {@link ExternalMenuService#getMenus()} 一一对应。</p>
 *
 * <p>CO-155 顺带修：main 上 !443 重构半完成，ExternalMenuServiceTest 调 getMenus() 期望返回
 * ExternalMenuResponse，但 service 当时只返 List&lt;TreeNode&gt;。补这个 DTO 把 service 的契约补全。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalMenuResponse {

    /** 客户方系统标识. */
    private String systemCode;

    /** 客户方系统展示名. */
    private String systemName;

    /** 菜单树（顶级 + 子级嵌套）.
     *  - code 与 menuCode 同义（外部规范字段名）
     *  - name 与 menuName 同义（外部规范字段名）
     *  - path 由 menuCode 按路由规则派生
     *  - permissionKeys 与 RoleProfileCatalog 中的 menuPermissions 对齐
     */
    private List<ExternalMenuTreeNode> menus;
}
