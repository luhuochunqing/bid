// Input: RoleProfileRepository and RoleProfileCatalog seed definitions
// Output: persisted built-in role profiles without overwriting admin-managed permissions
// Pos: roleprofile/ - neutral role profile bootstrap component
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.roleprofile;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.repository.RoleProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleProfileBootstrap {

    private final RoleProfileRepository roleProfileRepository;

    @Transactional
    public void ensureSystemRoles() {
        for (RoleProfileCatalog.SeedDefinition definition : RoleProfileCatalog.seedDefinitions()) {
            roleProfileRepository.findByCodeIgnoreCase(definition.code())
                    .ifPresentOrElse(
                            existing -> {
                                if (!Boolean.TRUE.equals(existing.getIsSystem())) {
                                    existing.setIsSystem(true);
                                    roleProfileRepository.save(existing);
                                }
                            },
                            () -> roleProfileRepository.save(buildRole(definition))
                    );
        }
    }

    private RoleProfile buildRole(RoleProfileCatalog.SeedDefinition definition) {
        RoleProfile role = RoleProfile.builder()
                .code(definition.code())
                .name(definition.name())
                .description(definition.description())
                .isSystem(definition.system())
                .enabled(true)
                .dataScope(definition.dataScope())
                .build();
        role.setMenuPermissions(definition.menuPermissions());
        role.setAllowedProjects(List.of());
        role.setAllowedDepts(List.of());
        return role;
    }
}
