package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.dto.RoleDTO;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.policy.OssMenuPermissionMapper;
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import com.xiyu.bid.service.RoleProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OrganizationRoleMenuSyncAppService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationRoleMenuSyncAppService.class);

    private final OrganizationDirectoryGateway gateway;
    private final RoleProfileService roleProfileService;
    private final OrganizationIntegrationProperties.Directory directory;

    public OrganizationRoleMenuSyncAppService(
            OrganizationDirectoryGateway gateway,
            RoleProfileService roleProfileService,
            OrganizationIntegrationProperties properties) {
        this.gateway = gateway;
        this.roleProfileService = roleProfileService;
        this.directory = properties.getDirectory();
    }

    @Transactional
    public RoleDTO syncRoleMenuPermissions(Long roleId, String jobNumber) {
        if (jobNumber == null || jobNumber.isBlank()) {
            throw new IllegalArgumentException("Job number is required");
        }
        OrganizationDirectoryLookupContext context = OrganizationDirectoryLookupContext.empty();
        Optional<List<OssMenuTreeNode>> menuTree = gateway.fetchUserMenuTree(jobNumber, context);
        if (menuTree.isEmpty()) {
            log.warn("OSS 菜单树返回为空: roleId={}, jobNumber={}", roleId, jobNumber);
            return roleProfileService.updateMenuPermissions(roleId, List.of());
        }
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                directory.getMenuCodeToPermissionKeyMappings(),
                directory.getUnmappedMenuCodeBehavior()
        );
        Set<String> permissions = mapper.map(menuTree.get());
        log.info("OSS 菜单权限同步: roleId={}, jobNumber={}, mapped={}, totalNodes={}",
            roleId, jobNumber, permissions.size(), countNodes(menuTree.get()));
        return roleProfileService.updateMenuPermissions(roleId, new ArrayList<>(permissions));
    }

    private int countNodes(List<OssMenuTreeNode> nodes) {
        int count = 0;
        List<OssMenuTreeNode> stack = new ArrayList<>(nodes);
        while (!stack.isEmpty()) {
            OssMenuTreeNode node = stack.removeLast();
            count++;
            if (node.children() != null) {
                stack.addAll(node.children());
            }
        }
        return count;
    }
}
