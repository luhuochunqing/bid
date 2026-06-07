package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.command.CreateManufacturerAuthCommand;
import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.mapper.ManufacturerAuthMapper;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import com.xiyu.bid.exception.BusinessException;
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
    /** User repository. */
    private final com.xiyu.bid.repository.UserRepository userRepository;
    /** Log JPA repository. */
    private final com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository logRepository;

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
            throw new BusinessException("结束时间须晚于开始时间");
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

        if (isAgent) {
            validateAgentTimeChain(auth);
        }

        ManufacturerAuthorization saved = repository.save(auth);
        List<BrandAuthAttachmentEntity> atts =
                attachmentRepository.findByAuthorizationId(saved.id());
        ManufacturerAuthorizationDTO dto =
                ManufacturerAuthMapper.toDTO(saved, atts);

        // Record operation log
        String operatorUsername = "system";
        if (userId != null) {
            operatorUsername = userRepository.findById(userId)
                    .map(u -> u.getFullName() + "(" + u.getUsername() + ")")
                    .orElse("system");
        }
        
        String detailsJson = String.format(
            "{\"authorizationType\":\"%s\",\"productLine\":\"%s\",\"brandId\":\"%s\",\"brandName\":\"%s\",\"importDomestic\":\"%s\",\"manufacturerName\":\"%s\",\"agentName\":\"%s\",\"authStartDate\":\"%s\",\"authEndDate\":\"%s\"}",
            cmd.authorizationType(), cmd.productLine(), cmd.brandId(), cmd.brandName(),
            cmd.importDomestic(), cmd.manufacturerName(), cmd.agentName() != null ? cmd.agentName() : "",
            cmd.authStartDate(), cmd.authEndDate()
        );

        com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity opLog = 
            com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity.builder()
                .authorizationId(saved.id())
                .operatorId(userId)
                .operatorUsername(operatorUsername)
                .actionType("CREATE")
                .summary("新增" + (isAgent ? "代理商授权" : "原厂授权"))
                .details(detailsJson)
                .remarks(cmd.remarks())
                .build();
        logRepository.save(opLog);

        return new CreateResult(dto, dup
                ? "已存在重叠授权，已继续保存"
                : null);
    }

    private void validateAgentTimeChain(ManufacturerAuthorization auth) {
        if (auth.agentName() == null || auth.agentName().isBlank()) {
            throw new BusinessException("代理商名称不能为空");
        }
        if (auth.auth1StartDate() == null || auth.auth1EndDate() == null
                || auth.auth2StartDate() == null
                || auth.auth2EndDate() == null) {
            throw new BusinessException("代理商授权两段时间必须填写完整");
        }
        if (!auth.auth1EndDate().isAfter(auth.auth1StartDate())) {
            throw new BusinessException("授权1结束时间须晚于开始时间");
        }
        if (!auth.auth2EndDate().isAfter(auth.auth2StartDate())) {
            throw new BusinessException("授权2结束时间须晚于开始时间");
        }
        if (auth.auth2StartDate().isBefore(auth.auth1StartDate())) {
            throw new BusinessException("转授权2开始时间不能早于原厂授权1开始时间");
        }
        if (auth.auth2EndDate().isAfter(auth.auth1EndDate())) {
            throw new BusinessException("转授权2结束时间不能晚于原厂授权1结束时间");
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
