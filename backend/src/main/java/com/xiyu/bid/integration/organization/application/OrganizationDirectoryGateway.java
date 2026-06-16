package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationJobSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrganizationDirectoryGateway {
    Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(String deptId);

    default Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(
            String deptId,
            OrganizationDirectoryLookupContext context
    ) {
        return fetchDepartmentByDeptId(deptId);
    }

    Optional<OrganizationUserSnapshot> fetchUserByUserId(String userId);

    default Optional<OrganizationUserSnapshot> fetchUserByUserId(String userId, OrganizationDirectoryLookupContext context) {
        return fetchUserByUserId(userId);
    }

    List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(LocalDateTime startAt, LocalDateTime endAt);

    default List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(
            LocalDateTime startAt,
            LocalDateTime endAt,
            OrganizationDirectoryLookupContext context
    ) {
        return listDepartmentsByWindow(startAt, endAt);
    }

    List<OrganizationUserSnapshot> listUsersByWindow(LocalDateTime startAt, LocalDateTime endAt);

    Optional<OrganizationJobSnapshot> fetchJobByJobId(String jobId);

    default Optional<OrganizationJobSnapshot> fetchJobByJobId(String jobId, OrganizationDirectoryLookupContext context) {
        return fetchJobByJobId(jobId);
    }

    default List<OrganizationUserSnapshot> listUsersByWindow(
            LocalDateTime startAt,
            LocalDateTime endAt,
            OrganizationDirectoryLookupContext context
    ) {
        return listUsersByWindow(startAt, endAt);
    }
}
