package com.xiyu.bid.platform.dto;

/**
 * Bean Validation group marker：标识"创建"场景的校验。
 * <p>用于 {@link PlatformAccountCreateRequest} 的字段分组，例如 password 在创建时必填
 * （{@code @NotBlank(groups = OnCreate.class)}），编辑时（{@link OnUpdate}）不校验。
 * <p>CO-545：解决 update 接口复用 create DTO 时 password 的 @NotBlank 误伤编辑场景。
 */
public interface OnCreate {
}
