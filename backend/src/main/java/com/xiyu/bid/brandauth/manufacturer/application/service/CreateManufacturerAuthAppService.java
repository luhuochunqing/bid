package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.command.CreateManufacturerAuthCommand;
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

/** Application service for creating brand authorizations. */
@Service
@RequiredArgsConstructor
public class CreateManufacturerAuthAppService {

    /** Domain repository. */
    private final ManufacturerAuthorizationRepository repository;
    /** Attachment JPA repository. */
    private final BrandAuthAttachmentJpaRepository attachmentRepository;

    /**
     * Create a manufacturer or agent authorization.
     *
     * @param cmd    the create command
     * @param userId the creating user ID
     * @return result with DTO and optional duplicate warning
     */
    @Transactional
    public CreateResult create(final CreateManufacturerAuthCommand cmd,
            final Long userId) {
        boolean isAgent = "AGENT".equals(cmd.authorizationType());
        if (!cmd.authEndDate().isAfter(cmd.authStartDate())) {
            throw new IllegalArgumentException("结束时间须晚于开始时间");
        }
        if (isAgent) {
            validateAgentTimeChain(cmd);
        }

        boolean dup = repository
                .existsByBrandIdAndManufacturerNameAndProductLineAndStatusIn(
                        cmd.brandId(), cmd.manufacturerName(),
                        cmd.productLine(),
                        List.of(AuthStatus.ACTIVE, AuthStatus.EXPIRING_SOON));

        ManufacturerAuthorization auth = isAgent
                ? ManufacturerAuthorization.createAgent(
                        cmd.productLine(), cmd.brandId(), cmd.brandName(),
                        cmd.importDomestic(), cmd.manufacturerName(),
                        cmd.agentName(),
                        cmd.authStartDate(), cmd.authEndDate(),
                        cmd.auth1StartDate(), cmd.auth1EndDate(),
                        cmd.auth1Remarks(),
                        cmd.auth2StartDate(), cmd.auth2EndDate(),
                        cmd.auth2Remarks(),
                        cmd.remarks(), userId)
                : ManufacturerAuthorization.create(
                        cmd.productLine(), cmd.brandId(), cmd.brandName(),
                        cmd.importDomestic(), cmd.manufacturerName(),
                        cmd.authStartDate(), cmd.authEndDate(),
                        cmd.remarks(), userId);

        ManufacturerAuthorization saved = repository.save(auth);
        List<BrandAuthAttachmentEntity> atts =
                attachmentRepository.findByAuthorizationId(saved.id());
        ManufacturerAuthorizationDTO dto =
                ManufacturerAuthMapper.toDTO(saved, atts);

        return new CreateResult(dto, dup
                ? "已存在重叠授权，已继续保存"
                : null);
    }

    private void validateAgentTimeChain(
            final CreateManufacturerAuthCommand cmd) {
        if (cmd.agentName() == null || cmd.agentName().isBlank()) {
            throw new IllegalArgumentException("代理商名称不能为空");
        }
        if (cmd.auth1StartDate() == null || cmd.auth1EndDate() == null
                || cmd.auth2StartDate() == null
                || cmd.auth2EndDate() == null) {
            throw new IllegalArgumentException(
                    "代理商授权两段时间必须填写完整");
        }
        if (!cmd.auth1EndDate().isAfter(cmd.auth1StartDate())) {
            throw new IllegalArgumentException(
                    "授权1结束时间须晚于开始时间");
        }
        if (!cmd.auth2EndDate().isAfter(cmd.auth2StartDate())) {
            throw new IllegalArgumentException(
                    "授权2结束时间须晚于开始时间");
        }
        if (cmd.auth2StartDate().isBefore(cmd.auth1StartDate())) {
            throw new IllegalArgumentException(
                    "转授权2开始时间不能早于原厂授权1开始时间");
        }
        if (cmd.auth2EndDate().isAfter(cmd.auth1EndDate())) {
            throw new IllegalArgumentException(
                    "转授权2结束时间不能晚于原厂授权1结束时间");
        }
    }

    /**
     * Result of create operation with optional duplicate warning.
     *
     * @param dto     the created authorization DTO
     * @param warning duplicate warning message or null
     */
    public record CreateResult(ManufacturerAuthorizationDTO dto,
            String warning) {
    }
}
