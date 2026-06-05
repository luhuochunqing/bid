package com.xiyu.bid.dashboard.service;

import com.xiyu.bid.dashboard.dto.DashboardLayoutDTO;
import com.xiyu.bid.dashboard.entity.DashboardLayout;
import com.xiyu.bid.dashboard.repository.DashboardLayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardLayoutService {

    private final DashboardLayoutRepository dashboardLayoutRepository;

    @Transactional(readOnly = true)
    public DashboardLayoutDTO getLayoutByRole(String roleCode) {
        DashboardLayout layout = dashboardLayoutRepository.findByRoleCode(roleCode)
                .orElseGet(() -> dashboardLayoutRepository.findByCode("default").orElse(null));

        if (layout == null) {
            return DashboardLayoutDTO.builder()
                    .code("default_empty")
                    .name("Default Empty Layout")
                    .layoutJson("[]")
                    .build();
        }

        return DashboardLayoutDTO.builder()
                .code(layout.getCode())
                .name(layout.getName())
                .layoutJson(layout.getLayoutJson())
                .build();
    }
}
