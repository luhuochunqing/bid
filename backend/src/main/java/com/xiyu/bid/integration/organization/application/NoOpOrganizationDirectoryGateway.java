package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationJobSnapshot;
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * No-op fallback for OrganizationDirectoryGateway when no real gateway is configured.
 * <p>
 * This bean is only instantiated when:
 * <ol>
 *   <li>No {@code OrganizationDirectoryBaseUrlConfiguredCondition} gateway is available
 *       (i.e., {@code xiyu.integrations.organization.directory.base-url} is not set), AND</li>
 *   <li>SDK adapter is also not available (e.g., {@code com.ehsy.eventlibrary:ClientSDK}
 *       not on classpath or {@code xiyu.integrations.organization.sdk.enabled=false})</li>
 * </ol>
 * <p>
 * In these cases the local department/user tables are maintained exclusively through
 * SDK push events — there is no need for the HTTP pull gateway.  This stub prevents
 * {@link OrganizationDirectorySyncAppService} from failing to start when the gateway
 * bean is absent.
 */
@Component
@ConditionalOnMissingBean(OrganizationDirectoryGateway.class)
public class NoOpOrganizationDirectoryGateway implements OrganizationDirectoryGateway {

    @Override
    public Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(String deptId) {
        return Optional.empty();
    }

    @Override
    public Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(
            String deptId,
            OrganizationDirectoryLookupContext context
    ) {
        return Optional.empty();
    }

    @Override
    public Optional<OrganizationUserSnapshot> fetchUserByUserId(String userId) {
        return Optional.empty();
    }

    @Override
    public Optional<OrganizationUserSnapshot> fetchUserByUserId(
            String userId,
            OrganizationDirectoryLookupContext context
    ) {
        return Optional.empty();
    }

    @Override
    public List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        return List.of();
    }

    @Override
    public List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(
            LocalDateTime startAt,
            LocalDateTime endAt,
            OrganizationDirectoryLookupContext context
    ) {
        return List.of();
    }

    @Override
    public List<OrganizationUserSnapshot> listUsersByWindow(LocalDateTime startAt, LocalDateTime endAt) {
        return List.of();
    }

    @Override
    public Optional<OrganizationJobSnapshot> fetchJobByJobId(String jobId) {
        return Optional.empty();
    }

    @Override
    public Optional<OrganizationJobSnapshot> fetchJobByJobId(
            String jobId,
            OrganizationDirectoryLookupContext context
    ) {
        return Optional.empty();
    }

    @Override
    public List<OrganizationUserSnapshot> listUsersByWindow(
            LocalDateTime startAt,
            LocalDateTime endAt,
            OrganizationDirectoryLookupContext context
    ) {
        return List.of();
    }

    @Override
    public java.util.Map<String, OssUserJobAndRoleDto> getUserJobAndRoleListByJobNumbers(
            List<String> jobNumbers,
            OrganizationDirectoryLookupContext context
    ) {
        return Map.of();
    }

    @Override
    public Optional<List<OssMenuTreeNode>> fetchUserMenuTree(
            String jobNumber,
            OrganizationDirectoryLookupContext context
    ) {
        return Optional.empty();
    }
}
