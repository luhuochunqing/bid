package com.xiyu.bid.service;

import com.xiyu.bid.dto.RoleDTO;
import com.xiyu.bid.dto.UpdateRoleRequest;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.roleprofile.RoleProfileBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RoleProfileServicePersistenceTest {

    @Mock
    private RoleProfileRepository roleProfileRepository;

    @Mock
    private UserRepository userRepository;

    private final Map<Long, RoleProfile> rolesById = new LinkedHashMap<>();
    private final Map<String, RoleProfile> rolesByCode = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    private RoleProfileService roleProfileService;

    @BeforeEach
    void setUp() {
        RoleProfileCatalog.seedDefinitions().forEach(this::seedRole);
        RoleProfileBootstrap bootstrap = new RoleProfileBootstrap(roleProfileRepository);
        roleProfileService = new RoleProfileService(roleProfileRepository, userRepository, bootstrap);

        lenient().when(roleProfileRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(rolesById.get(invocation.getArgument(0))));
        lenient().when(roleProfileRepository.findByCodeIgnoreCase(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(rolesByCode.get(normalize(invocation.getArgument(0)))));
        lenient().when(roleProfileRepository.findAll()).thenAnswer(invocation -> List.copyOf(rolesById.values()));
        lenient().when(roleProfileRepository.save(any(RoleProfile.class)))
                .thenAnswer(invocation -> saveRole(invocation.getArgument(0)));
        lenient().when(userRepository.countByRoleProfile_Id(anyLong())).thenReturn(0L);
    }

    @Test
    void updateRoleShouldStayStableAfterListRefreshTriggersBootstrap() {
        RoleProfile staff = rolesByCode.get(RoleProfileCatalog.STAFF_CODE);
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setName("员工");
        request.setDescription("管理员自定义员工权限");
        request.setEnabled(false);
        request.setDataScope("dept");
        request.setMenuPermissions(List.of("dashboard", "bidding"));
        request.setAllowedProjects(List.of(42L));
        request.setAllowedDepts(List.of("SALES"));

        roleProfileService.updateRole(staff.getId(), request);
        RoleDTO reloaded = roleProfileService.listRoles().stream()
                .filter(role -> RoleProfileCatalog.STAFF_CODE.equals(role.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(reloaded.getMenuPermissions())
                .containsExactly("dashboard", "bidding")
                .doesNotContain(
                        "operation-logs",
                        RoleProfileCatalog.QUICK_START_PERMISSION,
                        RoleProfileCatalog.AI_CENTER_PERMISSION
                );
        assertThat(reloaded.getDataScope()).isEqualTo("dept");
        assertThat(reloaded.getEnabled()).isFalse();
        assertThat(reloaded.getAllowedProjects()).containsExactly(42L);
        assertThat(reloaded.getAllowedDepts()).containsExactly("SALES");
    }

    @Test
    void resetRoleIsTheExplicitPathForRestoringSeedPermissions() {
        RoleProfile staff = rolesByCode.get(RoleProfileCatalog.STAFF_CODE);
        staff.setMenuPermissions(List.of("dashboard", "bidding"));

        RoleDTO reset = roleProfileService.resetRole(staff.getId());

        assertThat(reset.getMenuPermissions())
                .contains(
                        RoleProfileCatalog.QUICK_START_PERMISSION,
                        RoleProfileCatalog.AI_CENTER_PERMISSION,
                        "operation-logs"
                );
    }

    private void seedRole(RoleProfileCatalog.SeedDefinition definition) {
        RoleProfile role = RoleProfile.builder()
                .id(nextId.getAndIncrement())
                .code(definition.code())
                .name(definition.name())
                .description(definition.description())
                .isSystem(definition.system())
                .enabled(true)
                .dataScope(definition.dataScope())
                .build();
        role.setMenuPermissions(definition.menuPermissions());
        saveRole(role);
    }

    private RoleProfile saveRole(RoleProfile role) {
        rolesById.put(role.getId(), role);
        rolesByCode.put(normalize(role.getCode()), role);
        return role;
    }

    private String normalize(String code) {
        return String.valueOf(code).trim().toLowerCase(Locale.ROOT);
    }
}
