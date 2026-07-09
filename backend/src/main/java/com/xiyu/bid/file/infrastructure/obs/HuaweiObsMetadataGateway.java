package com.xiyu.bid.file.infrastructure.obs;

import com.obs.services.ObsClient;
import com.obs.services.model.ObjectMetadata;
import com.xiyu.bid.file.domain.gateway.ObsMetadataGateway;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
 *
 * <p>D4-1 修复：ObsClient 改为单例复用，@PostConstruct 初始化，@PreDestroy 关闭。
 * 避免每次请求都 new ObsClient 的开销（连接池建立/释放）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HuaweiObsMetadataGateway implements ObsMetadataGateway {

    private final ObsProperties obsProperties;
    private ObsClient obsClient;

    @PostConstruct
    void initObsClient() {
        if (obsProperties.isEnabled()) {
            this.obsClient = new ObsClient(
                    obsProperties.getAccessKey(),
                    obsProperties.getSecretKey(),
                    obsProperties.getEndpoint());
        }
    }

    @PreDestroy
    void closeObsClient() {
        if (obsClient != null) {
            try {
                obsClient.close();
            } catch (IOException | RuntimeException e) {
                log.warn("关闭 ObsClient 失败", e);
            }
        }
    }

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
        try {
            return obsClient.getObjectMetadata(bucket, objectKey);
        } catch (RuntimeException e) {
            log.warn("获取 OBS 对象元数据失败，bucket={}, objectKey={}", bucket, objectKey, e);
            return null;
        }
    }
}
