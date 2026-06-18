package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.policy.OssMenuPermissionMapper;
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import com.xiyu.bid.repository.RoleProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 在组织架构同步流程中，按用户合并 OSS 菜单权限到其所属角色。
 */
@Service
public class OssRoleMenuPermissionAutoSync {

    private static final Logger log = LoggerFactory.getLogger(OssRoleMenuPermissionAutoSync.class);

    private final ObjectProvider<OrganizationDirectoryGateway> gatewayProvider;
    private final RoleProfileRepository roleProfileRepository;
    private final OrganizationIntegrationProperties.Directory directory;

    public OssRoleMenuPermissionAutoSync(
            ObjectProvider<OrganizationDirectoryGateway> gatewayProvider,
            RoleProfileRepository roleProfileRepository,
            OrganizationIntegrationProperties properties) {
        this.gatewayProvider = gatewayProvider;
        this.roleProfileRepository = roleProfileRepository;
        this.directory = properties.getDirectory();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergeUserMenuPermissionsIntoRole(String jobNumber, RoleProfile role) {
        if (jobNumber == null || jobNumber.isBlank() || role == null) {
            return;
        }
        OrganizationDirectoryGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            log.debug("OrganizationDirectoryGateway 不可用，跳过 OSS 菜单权限同步: jobNumber={}", jobNumber);
            return;
        }
        Optional<List<OssMenuTreeNode>> menuTree = gateway.fetchUserMenuTree(
                jobNumber, OrganizationDirectoryLookupContext.empty());
        if (menuTree.isEmpty()) {
            log.warn("自动同步 OSS 菜单树返回为空: jobNumber={}, roleCode={}", jobNumber, role.getCode());
            return;
        }
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                directory.getMenuCodeToPermissionKeyMappings(),
                directory.getUnmappedMenuCodeBehavior()
        );
        Set<String> merged = new HashSet<>(role.getMenuPermissions());
        merged.addAll(mapper.map(menuTree.get()));
        role.setMenuPermissions(new ArrayList<>(merged));
        roleProfileRepository.save(role);
        log.info("自动同步 OSS 菜单权限已合并: jobNumber={}, roleCode={}, permissionCount={}",
            jobNumber, role.getCode(), merged.size());
    }
}
