package com.xiyu.bid.systems.external;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 外部菜单服务测试（已清空字段后仅保留占位验证）.
 */
class ExternalMenuServiceTest {

    private final ExternalMenuService service = new ExternalMenuService();

    @Test
    void should_return_empty_response() {
        ExternalMenuResponse response = service.getMenus();
        assertNotNull(response);
    }
}
