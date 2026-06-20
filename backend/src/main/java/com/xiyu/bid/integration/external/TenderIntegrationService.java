package com.xiyu.bid.integration.external;

import com.xiyu.bid.tender.dto.TenderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 外部标讯同步核心服务（接口规范 v2.0）。
 * 
 * 重构说明：职责已拆分为三个独立服务：
 * - {@link TenderIntegrationQueryService}：查询职责
 * - {@link TenderIntegrationCommandService}：写入职责
 * - {@link TenderIntegrationMapper}：转换职责
 * 
 * 本类保留为委托入口，保持 API 向后兼容。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderIntegrationService {

    private final TenderIntegrationQueryService queryService;
    private final TenderIntegrationCommandService commandService;

    /**
     * 幂等推送标讯。
     * 按 (sourceSystem, sourceId) 组合 externalId 进行幂等判断：
     * - 已存在：返回 DUPLICATE
     * - 不存在：创建并返回 CREATED
     */
    public TenderPushResponse pushTender(TenderPushRequest request, Long userId) {
        return commandService.pushTender(request, userId);
    }

    /**
     * 按 externalId 或 tenderId 查询标讯详情（二选一必传）。
     */
    public TenderDTO getByExternalId(String sourceSystem, String sourceId, Long tenderId) {
        return queryService.getByExternalId(sourceSystem, sourceId, tenderId);
    }

    /**
     * 按 externalId 或 tenderId 更新标讯字段（二选一必传）。
     */
    public TenderDTO updateByExternalId(String sourceSystem, String sourceId, TenderUpdateRequest request) {
        return commandService.updateByExternalId(sourceSystem, sourceId, request);
    }
}
