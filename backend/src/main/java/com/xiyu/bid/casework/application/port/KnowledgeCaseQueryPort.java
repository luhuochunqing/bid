package com.xiyu.bid.casework.application.port;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.List;

/**
 * 案例库查询端口（Application 层驱动端口）。
 *
 * <p>Application 层通过此端口获取案例数据，不直接依赖 infrastructure 层的
 * JPA Entity / Repository。实现由 infrastructure 层的 Adapter 提供。
 */
public interface KnowledgeCaseQueryPort {

    /**
     * 查询所有激活状态（ACTIVE）的案例。
     */
    List<KnowledgeCaseReadModel> findAllActive();
}
