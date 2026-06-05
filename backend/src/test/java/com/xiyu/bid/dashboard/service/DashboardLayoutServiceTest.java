package com.xiyu.bid.dashboard.service;

import com.xiyu.bid.dashboard.dto.DashboardLayoutDTO;
import com.xiyu.bid.dashboard.entity.DashboardLayout;
import com.xiyu.bid.dashboard.repository.DashboardLayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardLayoutServiceTest {

    @Mock
    private DashboardLayoutRepository dashboardLayoutRepository;

    @InjectMocks
    private DashboardLayoutService dashboardLayoutService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testGetLayoutByRole_Found() {
        DashboardLayout layout = DashboardLayout.builder()
                .code("mgr_layout")
                .name("Manager Layout")
                .roleCode("MANAGER")
                .layoutJson("[{\"widget\": \"ProjectList\"}]")
                .build();

        when(dashboardLayoutRepository.findByRoleCode("MANAGER")).thenReturn(Optional.of(layout));

        DashboardLayoutDTO dto = dashboardLayoutService.getLayoutByRole("MANAGER");

        assertEquals("mgr_layout", dto.getCode());
        assertEquals("Manager Layout", dto.getName());
        assertEquals("[{\"widget\": \"ProjectList\"}]", dto.getLayoutJson());
    }

    @Test
    void testGetLayoutByRole_FallbackToDefault() {
        DashboardLayout defaultLayout = DashboardLayout.builder()
                .code("default")
                .name("Default Layout")
                .roleCode(null)
                .layoutJson("[{\"widget\": \"TenderList\"}]")
                .build();

        when(dashboardLayoutRepository.findByRoleCode("UNKNOWN")).thenReturn(Optional.empty());
        when(dashboardLayoutRepository.findByCode("default")).thenReturn(Optional.of(defaultLayout));

        DashboardLayoutDTO dto = dashboardLayoutService.getLayoutByRole("UNKNOWN");

        assertEquals("default", dto.getCode());
        assertEquals("Default Layout", dto.getName());
        assertEquals("[{\"widget\": \"TenderList\"}]", dto.getLayoutJson());
    }

    @Test
    void testGetLayoutByRole_NotFound() {
        when(dashboardLayoutRepository.findByRoleCode("UNKNOWN")).thenReturn(Optional.empty());
        when(dashboardLayoutRepository.findByCode("default")).thenReturn(Optional.empty());

        DashboardLayoutDTO dto = dashboardLayoutService.getLayoutByRole("UNKNOWN");

        assertEquals("default_empty", dto.getCode());
        assertEquals("Default Empty Layout", dto.getName());
        assertEquals("[]", dto.getLayoutJson());
    }
}
