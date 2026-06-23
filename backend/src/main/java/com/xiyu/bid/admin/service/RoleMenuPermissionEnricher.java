package com.xiyu.bid.admin.service;

import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.List;
import java.util.stream.Stream;

final class RoleMenuPermissionEnricher {

    private RoleMenuPermissionEnricher() {
    }

    static List<String> enrich(List<String> cachedPermissions, String roleCode) {
        if (roleCode == null || roleCode.isBlank() || !RoleProfileCatalog.isRegisteredCode(roleCode)) {
            return normalize(cachedPermissions);
        }
        if (RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(roleCode)) {
            Stream<String> catalogPermissions = RoleProfileCatalog.seedDefinitions().stream()
                    .flatMap(definition -> definition.menuPermissions() == null
                            ? Stream.<String>empty()
                            : definition.menuPermissions().stream());
            return normalize(Stream.concat(cachedStream(cachedPermissions), Stream.concat(Stream.of("all"), catalogPermissions)));
        }
        RoleProfileCatalog.SeedDefinition definition = RoleProfileCatalog.definitionForCode(roleCode);
        return normalize(Stream.concat(cachedStream(cachedPermissions), cachedStream(definition.menuPermissions())));
    }

    private static Stream<String> cachedStream(List<String> permissions) {
        return permissions == null ? Stream.empty() : permissions.stream();
    }

    private static List<String> normalize(List<String> permissions) {
        return normalize(cachedStream(permissions));
    }

    private static List<String> normalize(Stream<String> permissions) {
        return permissions
                .filter(permission -> permission != null && !permission.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
