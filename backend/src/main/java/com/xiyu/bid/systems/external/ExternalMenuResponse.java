package com.xiyu.bid.systems.external;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部系统菜单响应体.
 *
 * <p>包含系统标识和信息，供统一组织架构系统注册时使用。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalMenuResponse {

    /** 系统code. */
    private String systemCode;

    /** 系统名称. */
    private String systemName;

    /** 菜单列表. */
    private List<ExternalMenuTreeNode> menus;
}
