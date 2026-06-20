package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 外部标讯集成标讯解析器。
 * 提取 QueryService 和 CommandService 共用的 tender 解析逻辑。
 */
@Component
@RequiredArgsConstructor
public class TenderIntegrationResolver {

    private final TenderRepository tenderRepository;

    /**
     * 解析标讯实体（支持 tenderId 或 externalId 两种方式）。
     * 
     * @param sourceSystem 来源系统（如 CRM）
     * @param sourceId 来源系统中的 ID
     * @param tenderId 标讯主键
     * @return 解析到的 Tender 实体
     * @throws IllegalArgumentException 参数不足
     * @throws com.xiyu.bid.exception.ResourceNotFoundException 标讯不存在
     */
    public Tender resolveTender(String sourceSystem, String sourceId, Long tenderId) {
        if (tenderId != null) {
            Tender tender = tenderRepository.findById(tenderId)
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: id=" + tenderId));
            // 若同时传了 sourceSystem/sourceId，做交叉校验（"_"/"_" 占位符表示手动创建，跳过校验）
            if (sourceSystem != null && !sourceSystem.isBlank() && !"_".equals(sourceSystem)
                    && sourceId != null && !sourceId.isBlank() && !"_".equals(sourceId)) {
                String expectedExternalId = TenderIntegrationMapper.buildExternalId(sourceSystem, sourceId);
                if (tender.getExternalId() != null && !tender.getExternalId().equals(expectedExternalId)) {
                    throw new IllegalArgumentException(
                            "tenderId=" + tenderId + " 的 externalId="
                            + tender.getExternalId() + " 与路径 sourceSystem=" + sourceSystem
                            + " sourceId=" + sourceId + " 不匹配");
                }
            }
            return tender;
        }

        if (sourceSystem != null && !sourceSystem.isBlank() && !"_".equals(sourceSystem)
                && sourceId != null && !sourceId.isBlank() && !"_".equals(sourceId)) {
            String externalId = TenderIntegrationMapper.buildExternalId(sourceSystem, sourceId);
            return tenderRepository.findByExternalId(externalId)
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: " + externalId));
        }

        throw new IllegalArgumentException("tenderId 与 (sourceSystem, sourceId) 至少需要传一组");
    }
}
