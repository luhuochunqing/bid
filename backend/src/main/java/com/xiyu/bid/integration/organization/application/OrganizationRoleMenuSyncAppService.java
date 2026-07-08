package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.dto.RoleDTO;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.policy.OssMenuPermissionMapper;
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import com.xiyu.bid.service.RoleProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OrganizationRoleMenuSyncAppService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationRoleMenuSyncAppService.class);

    private final ObjectProvider<OrganizationDirectoryGateway> gatewayProvider;
    private final RoleProfileService roleProfileService;
    private final OssMenuPermissionMapper ossMenuPermissionMapper;

    public OrganizationRoleMenuSyncAppService(
            ObjectProvider<OrganizationDirectoryGateway> gatewayProvider,
            RoleProfileService roleProfileService,
            OssMenuPermissionMapper ossMenuPermissionMapper) {
        this.gatewayProvider = gatewayProvider;
        this.roleProfileService = roleProfileService;
        this.ossMenuPermissionMapper = ossMenuPermissionMapper;
    }

    @Transactional
    public RoleDTO syncRoleMenuPermissions(Long roleId, String jobNumber) {
        if (jobNumber == null || jobNumber.isBlank()) {
            throw new IllegalArgumentException("Job number is required");
        }
        OrganizationDirectoryGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            log.warn("OrganizationDirectoryGateway 不可用，无法同步菜单权限: roleId={}, jobNumber={}", roleId, jobNumber);
            return roleProfileService.updateMenuPermissions(roleId, List.of());
        }
        OrganizationDirectoryLookupContext context = OrganizationDirectoryLookupContext.empty();
        Optional<List<OssMenuTreeNode>> menuTree = gateway.fetchUserMenuTree(jobNumber, context);
        if (menuTree.isEmpty()) {
            log.warn("OSS 菜单树返回为空: roleId={}, jobNumber={}", roleId, jobNumber);
            return roleProfileService.updateMenuPermissions(roleId, List.of());
        }
        Set<String> permissions = ossMenuPermissionMapper.map(menuTree.get());
        log.info("OSS 菜单权限同步: roleId={}, jobNumber={}, mapped={}, totalNodes={}",
            roleId, jobNumber, permissions.size(), countNodes(menuTree.get()));
        return roleProfileService.updateMenuPermissions(roleId, new ArrayList<>(permissions));
    }

    private int countNodes(List<OssMenuTreeNode> nodes) {
        return OssMenuTreeNode.flatten(nodes).size();
    }
}
