package com.xiyu.bid.platform.dto;

/**
 * Bean Validation group marker：标识"编辑"场景的校验。
 * <p>当前实现中 update 端点用 {@code @Validated(Default.class)}，默认 group 字段（如
 * username/accountName 的 @NotBlank）仍校验，而显式归属 {@link OnCreate} 的字段（password）不校验。
 * 本接口预留给未来"仅编辑时生效"的校验规则扩展。
 * <p>CO-545：配合 {@link OnCreate} 解决 update 复用 create DTO 的校验冲突。
 */
public interface OnUpdate {
}
