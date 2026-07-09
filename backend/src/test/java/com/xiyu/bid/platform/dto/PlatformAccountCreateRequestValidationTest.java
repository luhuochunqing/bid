// Input: PlatformAccountCreateRequest + Jakarta Validator
// Output: 验证 password 在 OnCreate/Default group 下的校验行为差异
// Pos: Test/纯核心校验验证（CO-545 回归保护）
package com.xiyu.bid.platform.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-545 / CO-567：验证 password 字段在 create/update 场景下的校验行为。
 * <p>CO-567：password 改为非必填，create/update 均允许为空（NULL 表示无密码）。
 * <p>update（Default group）：username / accountName 仍由 Default @NotBlank 校验。
 */
class PlatformAccountCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static PlatformAccountCreateRequest requestWithPassword(String password) {
        return PlatformAccountCreateRequest.builder()
                .username("user1")
                .password(password)
                .accountName("测试平台")
                .build();
    }

    @Test
    @DisplayName("CO-567: create 场景 password 为空 → 不再报密码校验错（已改为非必填）")
    void create_passwordBlank_doesNotViolate() {
        PlatformAccountCreateRequest req = requestWithPassword("");

        Set<ConstraintViolation<PlatformAccountCreateRequest>> violations =
                validator.validate(req, OnCreate.class, Default.class);

        assertThat(violations).noneMatch(v -> v.getMessage().contains("密码"));
    }

    @Test
    @DisplayName("CO-567: create 场景 password 非空 → 通过")
    void create_passwordNotBlank_passesOnCreateGroup() {
        PlatformAccountCreateRequest req = requestWithPassword("secret123");

        Set<ConstraintViolation<PlatformAccountCreateRequest>> violations =
                validator.validate(req, OnCreate.class, Default.class);

        assertThat(violations).noneMatch(v -> v.getMessage().contains("密码"));
    }

    @Test
    @DisplayName("CO-545: update 场景（仅 Default group）password 为空 → 不报密码校验错")
    void update_passwordBlank_doesNotViolateDefaultGroup() {
        PlatformAccountCreateRequest req = requestWithPassword("");

        // update 端点用 @Validated(Default.class)，不触发 OnCreate 的 password @NotBlank
        Set<ConstraintViolation<PlatformAccountCreateRequest>> violations =
                validator.validate(req, Default.class);

        assertThat(violations).noneMatch(v -> v.getMessage().contains("密码"));
    }

    @Test
    @DisplayName("CO-545: update 场景 username 为空 → 仍报「平台账号不能为空」（Default @NotBlank 生效）")
    void update_usernameBlank_stillViolatesDefaultGroup() {
        PlatformAccountCreateRequest req = PlatformAccountCreateRequest.builder()
                .username("")          // 空
                .password(null)        // 空（update 允许）
                .accountName("测试")
                .build();

        Set<ConstraintViolation<PlatformAccountCreateRequest>> violations =
                validator.validate(req, Default.class);

        assertThat(violations).anyMatch(v -> v.getMessage().equals("平台账号不能为空"));
    }

    @Test
    @DisplayName("CO-545: update 场景 accountName 为空 → 仍报「平台名称不能为空」（Default @NotBlank 生效）")
    void update_accountNameBlank_stillViolatesDefaultGroup() {
        PlatformAccountCreateRequest req = PlatformAccountCreateRequest.builder()
                .username("user1")
                .password(null)
                .accountName("")       // 空
                .build();

        Set<ConstraintViolation<PlatformAccountCreateRequest>> violations =
                validator.validate(req, Default.class);

        assertThat(violations).anyMatch(v -> v.getMessage().equals("平台名称不能为空"));
    }
}
