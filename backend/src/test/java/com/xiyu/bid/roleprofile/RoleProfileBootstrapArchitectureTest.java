package com.xiyu.bid.roleprofile;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleProfileBootstrapArchitectureTest {

    private static final Path SOURCE = Path.of("src/main/java/com/xiyu/bid/roleprofile/RoleProfileBootstrap.java");
    private static final List<String> USER_OWNED_ROLE_SETTERS = List.of(
            ".setMenuPermissions(",
            ".setDataScope(",
            ".setAllowedProjects(",
            ".setAllowedDepts(",
            ".setEnabled("
    );

    @Test
    void bootstrapShouldOnlyWriteUserOwnedFieldsWhenBuildingMissingRoles() throws IOException {
        String sourceOutsideFactory = sourceOutsideBuildRoleFactory();

        assertThat(USER_OWNED_ROLE_SETTERS)
                .filteredOn(sourceOutsideFactory::contains)
                .as("RoleProfileBootstrap 不得在已有角色上覆盖管理员持久化配置；"
                        + "新增默认值请走迁移脚本，恢复默认值请走显式 resetRole")
                .isEmpty();
    }

    private static String sourceOutsideBuildRoleFactory() throws IOException {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("    private RoleProfile buildRole(");
        int end = source.indexOf("\n    }\n}", start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("RoleProfileBootstrap buildRole factory not found");
        }
        return source.substring(0, start) + source.substring(end + "\n    }\n".length());
    }
}
