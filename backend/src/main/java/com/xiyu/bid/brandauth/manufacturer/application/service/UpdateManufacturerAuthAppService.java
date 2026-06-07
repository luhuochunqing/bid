package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.command.UpdateManufacturerAuthCommand;
import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.mapper.ManufacturerAuthMapper;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.service.AuthorizationExpiryPolicy;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import com.xiyu.bid.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class UpdateManufacturerAuthAppService {

    private final ManufacturerAuthorizationRepository repository;
    private final BrandAuthAttachmentJpaRepository attachmentRepository;
    private final com.xiyu.bid.repository.UserRepository userRepository;
    private final com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository logRepository;

    @Transactional
    public ManufacturerAuthorizationDTO update(Long id, UpdateManufacturerAuthCommand cmd, Long userId) {
        ManufacturerAuthorization existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("授权记录不存在: " + id));

        if (existing.isRevoked())
            throw new BusinessException("已作废的授权不可修改");
        if (existing.status() == AuthStatus.EXPIRED && cmd.manufacturerName() != null)
            throw new BusinessException("已失效的授权仅可修改备注，请使用续期功能");

        ManufacturerAuthorization updated = new ManufacturerAuthorization(
                existing.id(), existing.authorizationType(),
                cmd.productLine() != null ? cmd.productLine() : existing.productLine(),
                cmd.brandId() != null ? cmd.brandId() : existing.brandId(),
                cmd.brandName() != null ? cmd.brandName() : existing.brandName(),
                cmd.importDomestic() != null ? cmd.importDomestic() : existing.importDomestic(),
                cmd.manufacturerName() != null ? cmd.manufacturerName() : existing.manufacturerName(),
                cmd.agentName() != null ? cmd.agentName() : existing.agentName(),
                cmd.authStartDate() != null ? cmd.authStartDate() : existing.authStartDate(),
                cmd.authEndDate() != null ? cmd.authEndDate() : existing.authEndDate(),
                cmd.auth1StartDate() != null ? cmd.auth1StartDate() : existing.auth1StartDate(),
                cmd.auth1EndDate() != null ? cmd.auth1EndDate() : existing.auth1EndDate(),
                cmd.auth1Remarks() != null ? cmd.auth1Remarks() : existing.auth1Remarks(),
                cmd.auth2StartDate() != null ? cmd.auth2StartDate() : existing.auth2StartDate(),
                cmd.auth2EndDate() != null ? cmd.auth2EndDate() : existing.auth2EndDate(),
                cmd.auth2Remarks() != null ? cmd.auth2Remarks() : existing.auth2Remarks(),
                cmd.remarks() != null ? cmd.remarks() : existing.remarks(),
                AuthorizationExpiryPolicy.refreshStatus(existing),
                existing.revokeReason(), existing.createdBy(),
                existing.createdAt(), existing.updatedAt(), existing.version());

        if (!updated.authEndDate().isAfter(updated.authStartDate()))
            throw new BusinessException("结束时间须晚于开始时间");

        if ("AGENT".equals(updated.authorizationType())) {
            validateAgentTimeChain(updated);
        }

        // Calculate diff for operation log
        StringBuilder diff = new StringBuilder();
        appendDiff(diff, "一级产线", existing.productLine(), updated.productLine());
        appendDiff(diff, "品牌ID", existing.brandId(), updated.brandId());
        appendDiff(diff, "品牌", existing.brandName(), updated.brandName());
        appendDiff(diff, "进口/国产", existing.importDomestic(), updated.importDomestic());
        appendDiff(diff, "品牌原厂名称", existing.manufacturerName(), updated.manufacturerName());
        appendDiff(diff, "代理商名称", existing.agentName(), updated.agentName());
        appendDiff(diff, "授权开始时间", existing.authStartDate(), updated.authStartDate());
        appendDiff(diff, "授权结束时间", existing.authEndDate(), updated.authEndDate());
        appendDiff(diff, "授权1开始时间", existing.auth1StartDate(), updated.auth1StartDate());
        appendDiff(diff, "授权1结束时间", existing.auth1EndDate(), updated.auth1EndDate());
        appendDiff(diff, "授权2开始时间", existing.auth2StartDate(), updated.auth2StartDate());
        appendDiff(diff, "授权2结束时间", existing.auth2EndDate(), updated.auth2EndDate());
        appendDiff(diff, "备注", existing.remarks(), updated.remarks());

        ManufacturerAuthorization saved = repository.save(updated);

        // Record operation log
        String operatorUsername = "system";
        if (userId != null) {
            operatorUsername = userRepository.findById(userId)
                    .map(u -> u.getFullName() + "(" + u.getUsername() + ")")
                    .orElse("system");
        }

        if (!diff.isEmpty()) {
            com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity opLog = 
                com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity.builder()
                    .authorizationId(saved.id())
                    .operatorId(userId)
                    .operatorUsername(operatorUsername)
                    .actionType("UPDATE")
                    .summary("修改授权")
                    .details(diff.toString())
                    .remarks(cmd.remarks())
                    .build();
            logRepository.save(opLog);
        }

        List<BrandAuthAttachmentEntity> atts = attachmentRepository.findByAuthorizationId(id);
        return ManufacturerAuthMapper.toDTO(saved, atts);
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

    private void appendDiff(StringBuilder sb, String fieldName, Object oldVal, Object newVal) {
        if (oldVal == null && newVal == null) return;
        if (oldVal != null && oldVal.equals(newVal)) return;
        if (sb.length() > 0) sb.append("; ");
        sb.append(fieldName).append("：[").append(oldVal != null ? oldVal : "无").append("] → [").append(newVal != null ? newVal : "无").append("]");
    }
}
