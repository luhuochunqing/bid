package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.mapper.ManufacturerAuthMapper;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RevokeManufacturerAuthAppService {

    private final ManufacturerAuthorizationRepository repository;
    private final BrandAuthAttachmentJpaRepository attachmentRepository;

    @Transactional
    public ManufacturerAuthorizationDTO revoke(Long id, String reason) {
        if (reason == null || reason.trim().length() < 10)
            throw new IllegalArgumentException("作废原因不能少于10个字");

        ManufacturerAuthorization existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("授权记录不存在: " + id));

        if (existing.isRevoked())
            throw new IllegalStateException("该授权已作废");

        ManufacturerAuthorization revoked = existing.withRevokeReason(reason.trim());
        ManufacturerAuthorization saved = repository.save(revoked);

        // TODO: Schedule cleanup of revoked attachments older than 90 days via batch job
        // Retaining files preserves audit trail for historical tender references

        List<BrandAuthAttachmentEntity> atts = attachmentRepository.findByAuthorizationId(id);
        return ManufacturerAuthMapper.toDTO(saved, atts);
    }
}
