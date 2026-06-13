package com.xiyu.bid.systems.external;

import org.springframework.stereotype.Service;

/**
 * 提供外部统一组织架构系统的菜单树结构（已清空，不再返回菜单数据）.
 */
@Service
public class ExternalMenuService {

    /**
     * 返回空的外部菜单响应.
     *
     * @return 空响应
     */
    public ExternalMenuResponse getMenus() {
        return new ExternalMenuResponse();
    }
}
