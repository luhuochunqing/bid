package com.xiyu.bid.platform.dto;

/**
 * Bean Validation group marker：标识"创建"场景的校验。
 * <p>CO-567 后 password 改为非必填，本组已无约束字段；保留是因为
 * {@code PlatformAccountController} 的 create 端点仍以 {@code @Validated(OnCreate.class)}
 * 区分创建场景，便于将来按需追加创建专属校验。
 */
public interface OnCreate {
}
