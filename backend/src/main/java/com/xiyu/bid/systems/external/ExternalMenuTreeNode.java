package com.xiyu.bid.systems.external;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部系统菜单树节点.
 *
 * <p>供统一组织架构系统拉取菜单列表。
 * 结构为扁平/树形两层（父菜单 + 子菜单），child 最多一级。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalMenuTreeNode {

    /** 菜单 code. */
    private String code;

    /** 菜单名称. */
    private String name;

    /** 前端路由路径. */
    private String path;

    /** 图标标识. */
    private String icon;

    /** 权限键列表. */
    private List<String> permissionKeys;

    /** 子菜单. */
    private List<ExternalMenuTreeNode> children;
}
