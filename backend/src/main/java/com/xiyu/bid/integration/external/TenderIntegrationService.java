package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部标讯同步核心服务（接口规范 v2.0）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderIntegrationService {

    private final TenderRepository tenderRepository;
    private final TenderMapper tenderMapper;

    /**
     * 幂等推送标讯。
     * 按 (sourceSystem, sourceId) 组合 externalId 进行幂等判断：
     * - 已存在：返回 DUPLICATE
     * - 不存在：创建并返回 CREATED
     */
    @Transactional
    public TenderPushResponse pushTender(TenderPushRequest request, Long userId) {
        String externalId = buildExternalId(request.getSourceSystem(), request.getSourceId());
        return tenderRepository.findByExternalId(externalId)
                .map(existing -> {
                    if (Boolean.TRUE.equals(request.getForceUpdate())) {
                        applyUpdate(existing, request);
                        if (userId != null) {
                            existing.setCreatorId(userId);
                        }
                        Tender saved = tenderRepository.save(existing);
                        log.info("Force-updated tender id={} externalId={}", saved.getId(), externalId);
                        return TenderPushResponse.builder()
                                .tenderId(saved.getId())
                                .status("UPDATED")
                                .message("标讯已覆盖更新")
                                .build();
                    }
                    return TenderPushResponse.builder()
                            .tenderId(existing.getId())
                            .status("DUPLICATE")
                            .message("标讯已存在")
                            .build();
                })
                .orElseGet(() -> {
                    Tender tender = mapToEntity(request);
                    tender.setExternalId(externalId);
                    if (userId != null) {
                        tender.setCreatorId(userId);
                    }
                    Tender saved = tenderRepository.save(tender);
                    log.info("Created tender id={} externalId={}", saved.getId(), externalId);
                    return TenderPushResponse.builder()
                            .tenderId(saved.getId())
                            .status("CREATED")
                            .message("标讯创建成功")
                            .build();
                });
    }

    private void applyUpdate(Tender tender, TenderPushRequest r) {
        if (r.getTitle() != null) tender.setTitle(InputSanitizer.sanitizeString(r.getTitle(), 500));
        if (r.getCustomerName() != null) tender.setPurchaserName(InputSanitizer.sanitizeString(r.getCustomerName(), 500));
        if (r.getPublishDate() != null) tender.setPublishDate(r.getPublishDate());
        if (r.getDueDate() != null) {
            try {
                tender.setDeadline(java.time.LocalDateTime.parse(r.getDueDate()));
            } catch (java.time.format.DateTimeParseException e) {
                log.warn("Cannot parse dueDate '{}': {}", r.getDueDate(), e.getMessage());
            }
        }
        if (r.getBudgetAmount() != null) tender.setBudget(r.getBudgetAmount());
        if (r.getContactPerson() != null) tender.setContactName(InputSanitizer.sanitizeString(r.getContactPerson(), 100));
        if (r.getContactPhone() != null) tender.setContactPhone(InputSanitizer.sanitizeString(r.getContactPhone(), 50));
        if (r.getContactTel() != null) tender.setContactTel(InputSanitizer.sanitizeString(r.getContactTel(), 50));
        if (r.getContactMail() != null) tender.setContactMail(InputSanitizer.sanitizeString(r.getContactMail(), 100));
        if (r.getContentDesc() != null) tender.setDescription(InputSanitizer.sanitizeString(r.getContentDesc(), 5000));
    }

    /**
     * 按 externalId 查询标讯详情。
     */
    @Transactional(readOnly = true)
    public TenderDTO getByExternalId(String sourceSystem, String sourceId) {
        String externalId = buildExternalId(sourceSystem, sourceId);
        Tender tender = tenderRepository.findByExternalId(externalId)
                .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                        "标讯不存在: " + externalId));
        return tenderMapper.toDTO(tender);
    }

    /**
     * 按 externalId 更新标讯字段。
     */
    @Transactional
    public TenderDTO updateByExternalId(String sourceSystem, String sourceId, TenderUpdateRequest request) {
        String externalId = buildExternalId(sourceSystem, sourceId);
        Tender tender = tenderRepository.findByExternalId(externalId)
                .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                        "标讯不存在: " + externalId));

        if (request.getTitle() != null) {
            tender.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 500));
        }
        if (request.getCustomerName() != null) {
            tender.setPurchaserName(InputSanitizer.sanitizeString(request.getCustomerName(), 500));
        }
        if (request.getPublishDate() != null) {
            tender.setPublishDate(request.getPublishDate());
        }
        if (request.getDueDate() != null) {
            tender.setDeadline(java.time.LocalDateTime.parse(request.getDueDate(),
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (request.getBudgetAmount() != null) {
            tender.setBudget(request.getBudgetAmount());
        }
        if (request.getContactPerson() != null) {
            tender.setContactName(InputSanitizer.sanitizeString(request.getContactPerson(), 100));
        }
        if (request.getContactPhone() != null) {
            tender.setContactPhone(InputSanitizer.sanitizeString(request.getContactPhone(), 50));
        }
        if (request.getContactTel() != null) {
            tender.setContactTel(InputSanitizer.sanitizeString(request.getContactTel(), 50));
        }
        if (request.getContactMail() != null) {
            tender.setContactMail(InputSanitizer.sanitizeString(request.getContactMail(), 100));
        }
        if (request.getContentDesc() != null) {
            tender.setDescription(InputSanitizer.sanitizeString(request.getContentDesc(), 5000));
        }

        Tender saved = tenderRepository.save(tender);
        log.info("Updated tender id={} externalId={}", saved.getId(), externalId);
        return tenderMapper.toDTO(saved);
    }

    private String buildExternalId(String sourceSystem, String sourceId) {
        return sourceSystem + ":" + sourceId;
    }

    private Tender mapToEntity(TenderPushRequest r) {
        Tender t = new Tender();
        t.setTitle(InputSanitizer.sanitizeString(r.getTitle(), 500));
        if (r.getCustomerName() != null) {
            t.setPurchaserName(InputSanitizer.sanitizeString(r.getCustomerName(), 500));
        }
        if (r.getPublishDate() != null) {
            t.setPublishDate(r.getPublishDate());
        }
        if (r.getDueDate() != null) {
            try {
                t.setDeadline(java.time.LocalDateTime.parse(r.getDueDate()));
            } catch (java.time.format.DateTimeParseException e) {
                log.warn("Cannot parse dueDate '{}': {}", r.getDueDate(), e.getMessage());
            }
        }
        if (r.getBudgetAmount() != null) {
            t.setBudget(r.getBudgetAmount());
        }
        if (r.getContactPerson() != null) {
            t.setContactName(InputSanitizer.sanitizeString(r.getContactPerson(), 100));
        }
        if (r.getContactPhone() != null) {
            t.setContactPhone(InputSanitizer.sanitizeString(r.getContactPhone(), 50));
        }
        if (r.getContactTel() != null) {
            t.setContactTel(InputSanitizer.sanitizeString(r.getContactTel(), 50));
        }
        if (r.getContactMail() != null) {
            t.setContactMail(InputSanitizer.sanitizeString(r.getContactMail(), 100));
        }
        if (r.getContentDesc() != null) {
            t.setDescription(InputSanitizer.sanitizeString(r.getContentDesc(), 5000));
        }
        t.setSourceType(com.xiyu.bid.entity.Tender.SourceType.EXTERNAL_PLATFORM);
        t.setStatus(com.xiyu.bid.entity.Tender.Status.PENDING_ASSIGNMENT);
        return t;
    }
}
