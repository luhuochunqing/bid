package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationJobSnapshot;
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;

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

    /**
     * 按工号列表批量回查岗位名称与系统角色列表。
     *
     * @param jobNumbers 工号列表
     * @return 按工号索引的查询结果
     */
    default java.util.Map<String, OssUserJobAndRoleDto> getUserJobAndRoleListByJobNumbers(List<String> jobNumbers) {
        return getUserJobAndRoleListByJobNumbers(jobNumbers, OrganizationDirectoryLookupContext.empty());
    }

    /**
     * 按工号列表批量回查岗位名称与系统角色列表（带调用上下文）。
     *
     * @param jobNumbers 工号列表
     * @param context    调用上下文
     * @return 按工号索引的查询结果
     */
    default java.util.Map<String, OssUserJobAndRoleDto> getUserJobAndRoleListByJobNumbers(
            List<String> jobNumbers,
            OrganizationDirectoryLookupContext context
    ) {
        return java.util.Map.of();
    }

    default List<OrganizationUserSnapshot> listUsersByWindow(
            LocalDateTime startAt,
            LocalDateTime endAt,
            OrganizationDirectoryLookupContext context
    ) {
        return listUsersByWindow(startAt, endAt);
    }

    /**
     * 查询指定用户在 OSS 侧的菜单树。
     *
     * @param jobNumber 用户工号
     * @return OSS 菜单树节点列表
     */
    default Optional<List<OssMenuTreeNode>> fetchUserMenuTree(String jobNumber) {
        return fetchUserMenuTree(jobNumber, OrganizationDirectoryLookupContext.empty());
    }

    /**
     * 查询指定用户在 OSS 侧的菜单树（带调用上下文）。
     *
     * @param jobNumber 用户工号
     * @param context   调用上下文
     * @return OSS 菜单树节点列表
     */
    default Optional<List<OssMenuTreeNode>> fetchUserMenuTree(
            String jobNumber,
            OrganizationDirectoryLookupContext context
    ) {
        return Optional.empty();
    }
}
