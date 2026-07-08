package com.xiyu.bid.file.infrastructure.obs;

import com.huaweicloud.sdk.obs.v1.ObsClient;
import com.huaweicloud.sdk.obs.v1.model.GetObjectMetadataRequest;
import com.huaweicloud.sdk.obs.v1.model.GetObjectMetadataResponse;
import com.huaweicloud.sdk.obs.v1.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 华为云 OBS 对象元数据查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObsMetadataService {

    private final ObsProperties obsProperties;

    /**
     * 获取对象大小（字节）。
     *
     * @param bucket     桶名
     * @param objectKey  对象键
     * @return 对象大小，对象不存在时返回 null
     */
    public Long getContentLength(String bucket, String objectKey) {
        ObjectMetadata metadata = getObjectMetadata(bucket, objectKey);
        return metadata != null ? metadata.getContentLength() : null;
    }

    /**
     * 获取对象 ETag（非加密上传时即为文件 MD5）。
     *
     * @param bucket     桶名
     * @param objectKey  对象键
     * @return ETag，对象不存在时返回 null
     */
    public String getEtag(String bucket, String objectKey) {
        ObjectMetadata metadata = getObjectMetadata(bucket, objectKey);
        return metadata != null ? metadata.getEtag() : null;
    }

    private ObjectMetadata getObjectMetadata(String bucket, String objectKey) {
        if (!obsProperties.isEnabled()) {
            throw new IllegalStateException("OBS 直传未启用");
        }

        try (ObsClient client = new ObsClient(
                obsProperties.getAccessKey(),
                obsProperties.getSecretKey(),
                obsProperties.getEndpoint())) {

            GetObjectMetadataRequest request = new GetObjectMetadataRequest(bucket, objectKey);
            GetObjectMetadataResponse response = client.getObjectMetadata(request);
            return response.getObjectMetadata();
        } catch (Exception e) {
            log.warn("获取 OBS 对象元数据失败，bucket={}, objectKey={}", bucket, objectKey, e);
            return null;
        }
    }
}
