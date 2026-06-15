package com.xiyu.bid.integration.organization.controller;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织架构查询控制器（提供给前端组织架构管理页面使用）。
 * <p>副作用层：只做查询委托。
 */
@RestController
@RequestMapping("/api/admin/organization")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class OrganizationQueryController {

    private final OrganizationDepartmentRepository departmentRepository;

    public OrganizationQueryController(OrganizationDepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * 查询启用的部门列表。支持按 source_app 过滤，避免多来源数据重复展示。
     */
    @GetMapping("/departments")
    public List<OrganizationDepartmentEntity> listDepartments(@RequestParam(name = "sourceApp", required = false) String sourceApp) {
        if (sourceApp != null && !sourceApp.isBlank()) {
            return departmentRepository.findBySourceAppAndEnabledTrueOrderByDepartmentCode(sourceApp.trim());
        }
        return departmentRepository.findByEnabledTrueOrderByDepartmentCode();
    }
}
