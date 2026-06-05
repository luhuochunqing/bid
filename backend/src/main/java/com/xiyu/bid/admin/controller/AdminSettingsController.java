package com.xiyu.bid.admin.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.DataScopeConfigResponse;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.dto.DepartmentTreeUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final DataScopeConfigService dataScopeConfigService;

    @GetMapping("/data-scope")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DataScopeConfigResponse>> getDataScopeConfig() {
        log.info("GET /api/admin/settings/data-scope - fetching data scope config");
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved data scope config", dataScopeConfigService.getConfig()));
    }

    @PutMapping("/data-scope")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DataScopeConfigResponse>> saveDataScopeConfig(@RequestBody DataScopeConfigResponse request) {
        log.info("PUT /api/admin/settings/data-scope - saving data scope config");
        return ResponseEntity.ok(ApiResponse.success("Data scope config saved successfully", dataScopeConfigService.saveConfig(request)));
    }

    @PutMapping("/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DataScopeConfigResponse>> saveDepartmentTree(@RequestBody DepartmentTreeUpdateRequest request) {
        log.info("PUT /api/admin/settings/departments - saving department tree");
        return ResponseEntity.ok(ApiResponse.success(
                "Department tree saved successfully",
                dataScopeConfigService.saveDepartments(request == null ? null : request.getDeptTree())
        ));
    }
}
