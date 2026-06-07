package com.xiyu.bid.dashboard.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashboardEntityTest {

    @Test
    void testDashboardWidget() {
        DashboardWidget widget = DashboardWidget.builder()
                .id(1L)
                .code("test_code")
                .name("test_name")
                .description("test_description")
                .defaultPropsJson("{}")
                .build();
        
        assertEquals(1L, widget.getId());
        assertEquals("test_code", widget.getCode());
        assertEquals("test_name", widget.getName());
        assertEquals("test_description", widget.getDescription());
        assertEquals("{}", widget.getDefaultPropsJson());
    }

    @Test
    void testDashboardLayout() {
        DashboardLayout layout = DashboardLayout.builder()
                .id(2L)
                .code("test_layout")
                .name("test_layout_name")
                .roleCode("ADMIN")
                .layoutJson("[]")
                .build();
        
        assertEquals(2L, layout.getId());
        assertEquals("test_layout", layout.getCode());
        assertEquals("test_layout_name", layout.getName());
        assertEquals("ADMIN", layout.getRoleCode());
        assertEquals("[]", layout.getLayoutJson());
    }
}
