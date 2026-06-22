package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.CallerContext;
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

    private final TenderIntegrationResolver helper;
    private final TenderAttachmentRepository attachmentRepository;
    private final TenderIntegrationMapper mapper;

    /**
     * 按 externalId 或 tenderId 查询标讯详情（二选一必传）。
     */
    public TenderDTO getByExternalId(String sourceSystem, String sourceId, Long tenderId) {
        return getByExternalId(sourceSystem, sourceId, tenderId, CallerContext.externalSystem(null));
    }

    public TenderDTO getByExternalId(String sourceSystem, String sourceId, Long tenderId, CallerContext context) {
        Tender tender = helper.resolveTender(sourceSystem, sourceId, tenderId);
        List<TenderAttachment> attachments = attachmentRepository.findByTenderId(tender.getId());
        return mapper.toDTO(tender, attachments, context);
    }
}
