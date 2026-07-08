package com.xiyu.bid.file.infrastructure.obs;

import com.obs.services.ObsClient;
import com.obs.services.model.ObjectMetadata;
import com.xiyu.bid.file.domain.gateway.ObsMetadataGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

/**
 * 华为云 OBS 对象元数据查询适配器。
 *
 * <p>实现 {@link ObsMetadataGateway} 端口接口，封装 OBS SDK 访问细节。
 * application 层依赖接口而非本实现类。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HuaweiObsMetadataGateway implements ObsMetadataGateway {

    private final ObsProperties obsProperties;

    @Override
    public Optional<Long> getContentLength(String bucket, String objectKey) {
        ObjectMetadata metadata = getObjectMetadata(bucket, objectKey);
        return metadata != null ? Optional.ofNullable(metadata.getContentLength()) : Optional.empty();
    }

    @Override
    public Optional<String> getEtag(String bucket, String objectKey) {
        ObjectMetadata metadata = getObjectMetadata(bucket, objectKey);
        return metadata != null ? Optional.ofNullable(metadata.getEtag()) : Optional.empty();
    }

    private ObjectMetadata getObjectMetadata(String bucket, String objectKey) {
        if (!obsProperties.isEnabled()) {
            throw new IllegalStateException("OBS 直传未启用");
        }

        try (ObsClient client = new ObsClient(
                obsProperties.getAccessKey(),
                obsProperties.getSecretKey(),
                obsProperties.getEndpoint())) {

            return client.getObjectMetadata(bucket, objectKey);
        } catch (RuntimeException | IOException e) {
            log.warn("获取 OBS 对象元数据失败，bucket={}, objectKey={}", bucket, objectKey, e);
            return null;
        }
    }
}
