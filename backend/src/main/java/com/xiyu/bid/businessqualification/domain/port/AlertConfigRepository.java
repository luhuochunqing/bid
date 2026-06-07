package com.xiyu.bid.businessqualification.domain.port;

import com.xiyu.bid.businessqualification.domain.model.AlertConfig;

import java.util.Optional;

/**
 * AlertConfig 的仓储接口。
 * 遵循纯核心 / 应用服务分离：接口定义在 domain.port 包，实现在 infrastructure 层。
 */
public interface AlertConfigRepository {

    /**
     * 查找当前激活的告警配置（约定只有一条生效配置）。
     */
    Optional<AlertConfig> findActive();

    /**
     * 保存告警配置（新增或更新）。
     */
    AlertConfig save(AlertConfig config);
}
