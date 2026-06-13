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

    /** 外部系统菜单ID. */
    private String id;

    /** 外部系统菜单名称. */
    private String menuName;

    /** 父菜单ID（"0"表示根节点）. */
    private String parentId;

    /** 外部系统菜单编码. */
    private String menuCode;

    /** 前端菜单编码（与menuCode同义）. */
    private String code;

    /** 前端菜单名称（与menuName同义）. */
    private String name;

    /** 前端路由路径. */
    private String path;

    /** 权限键列表. */
    private List<String> permissionKeys;

    /** 子菜单. */
    private List<ExternalMenuTreeNode> children;
}
