package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 外部标讯集成查询服务。
 * 负责按 externalId 或 tenderId 查询标讯详情。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TenderIntegrationQueryService {

    private final TenderRepository tenderRepository;
    private final TenderAttachmentRepository attachmentRepository;
    private final TenderIntegrationMapper mapper;

    /**
     * 按 externalId 或 tenderId 查询标讯详情（二选一必传）。
     */
    public TenderDTO getByExternalId(String sourceSystem, String sourceId, Long tenderId) {
        Tender tender = resolveTender(sourceSystem, sourceId, tenderId);
        List<TenderAttachment> attachments = attachmentRepository.findByTenderId(tender.getId());
        return mapper.toDTO(tender, attachments);
    }

    /**
     * 解析标讯实体（支持 tenderId 或 externalId 两种方式）。
     */
    private Tender resolveTender(String sourceSystem, String sourceId, Long tenderId) {
        if (tenderId != null) {
            Tender tender = tenderRepository.findById(tenderId)
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: id=" + tenderId));
            // 若同时传了 sourceSystem/sourceId，做交叉校验
            if (sourceSystem != null && !sourceSystem.isBlank()
                    && sourceId != null && !sourceId.isBlank()) {
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
