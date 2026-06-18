package com.xiyu.bid.integration.organization.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * OSS 菜单树节点，对应 GET /sysMenuUrl/getUserMenuTree 返回的 data 数组元素。
 *
 * @param id                    节点 id
 * @param menuCode              菜单编码（映射到内部权限码的主要键）
 * @param menuName              菜单名称
 * @param menuType              菜单类型：M-目录，C-菜单，F-按钮
 * @param parentId              父节点 id
 * @param orderNum              显示顺序
 * @param reqUrl                菜单接口地址
 * @param iconUrl               图标地址
 * @param systemUrl             系统地址
 * @param isSso                 是否单点登录系统
 * @param visible               是否可见
 * @param children              子节点列表
 * @param component             组件路径
 * @param path                  路由地址
 * @param menuAliasName         菜单别名
 * @param serviceId             服务 id
 * @param systemId              系统 id
 * @param serviceMenuId         服务菜单 id
 * @param serviceName           服务名称
 * @param serviceMenuParentId   服务菜单父级 id
 * @param serviceMenuParentName 服务菜单父级名称
 * @param sysMenuCode           系统菜单编码
 */
public record OssMenuTreeNode(
        Long id,
        String menuCode,
        String menuName,
        String menuType,
        Long parentId,
        Integer orderNum,
        String reqUrl,
        String iconUrl,
        String systemUrl,
        Integer isSso,
        Boolean visible,
        List<OssMenuTreeNode> children,
        String component,
        String path,
        String menuAliasName,
        Long serviceId,
        Long systemId,
        Long serviceMenuId,
        String serviceName,
        String serviceMenuParentId,
        String serviceMenuParentName,
        String sysMenuCode
) {
    public OssMenuTreeNode {
        if (children == null) {
            children = new ArrayList<>();
        }
    }

    public String normalizedMenuCode() {
        return menuCode == null ? "" : menuCode.trim().toLowerCase();
    }
}
