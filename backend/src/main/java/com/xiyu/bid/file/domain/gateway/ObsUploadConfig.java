package com.xiyu.bid.file.domain.gateway;

/**
 * OBS 上传配置读取端口（Hexagonal 端口接口）。
 *
 * <p>application 层通过此接口读取 OBS 配置，不直接依赖 infrastructure 层的 ObsProperties。
 * infrastructure 层的 ObsProperties 实现此接口。</p>
 *
 * <p>D2-1 修复：消除 IssueUploadTokenUseCase 对 ObsProperties 的直接依赖。</p>
 */
public interface ObsUploadConfig {

    boolean isEnabled();

    String getBucket();

    String getEndpoint();

    String getRegion();

    String getObjectKeyPrefix();
}
