package com.xiyu.bid.file.domain.gateway;

import java.util.Optional;

/**
 * OBS 对象元数据查询端口（Hexagonal 入站端口）。
 *
 * <p>application 层依赖此接口而非 infra 实现类，避免 SDK 泄漏到 application 层。
 * 实现由 {@code com.xiyu.bid.file.infrastructure.obs} 提供。</p>
 */
public interface ObsMetadataGateway {

    /**
     * 获取对象大小（字节）。
     *
     * @param bucket     桶名
     * @param objectKey  对象键
     * @return 对象大小，对象不存在时返回 empty
     */
    Optional<Long> getContentLength(String bucket, String objectKey);

    /**
     * 获取对象 ETag（非加密上传时即为文件 MD5）。
     *
     * @param bucket     桶名
     * @param objectKey  对象键
     * @return ETag，对象不存在时返回 empty
     */
    Optional<String> getEtag(String bucket, String objectKey);
}
