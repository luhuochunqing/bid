// Input: ObsProperties（xiyu.obs.* 配置绑定）
// Output: 历史案例原文下载所需桶名与有效期
// Pos: casework 模块基础设施适配层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.domain.gateway.HistoricalCaseObsConfig;
import com.xiyu.bid.file.infrastructure.obs.ObsProperties;
import org.springframework.stereotype.Component;

/**
 * {@link HistoricalCaseObsConfig} 的基础设施适配器：桥接 file 模块的 {@link ObsProperties}。
 */
@Component
public class HistoricalCaseObsConfigAdapter implements HistoricalCaseObsConfig {

    private final ObsProperties obsProperties;

    public HistoricalCaseObsConfigAdapter(ObsProperties obsProperties) {
        this.obsProperties = obsProperties;
    }

    @Override
    public String bucket() {
        return obsProperties.getBucket();
    }

    @Override
    public int downloadUrlExpireSeconds() {
        Integer seconds = obsProperties.getDownloadUrlExpireSeconds();
        return seconds != null ? seconds : 300;
    }
}
