package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog.ChangeDetail;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 「删除人员」应用服务
 * 负责权限校验、软删除、停止证书提醒、记录删除原因
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeletePersonnelAppService {

    private final PersonnelRepository personnelRepository;
    private final PersonnelOperationLogService logService;

    /**
     * 删除人员（软删除）
     *
     * @param id           人员ID
     * @param reason       删除原因（必填，由前端保证）
     * @param currentUserId 当前操作人
     * @param operatorName  操作人姓名
     */
    @Transactional
    @PreAuthorize("hasAnyAuthority('/bidAdmin', 'bid-TeamLeader')")
    public void delete(Long id, String reason, Long currentUserId, String operatorName) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("删除原因不能为空");
        }

        Personnel person = personnelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Personnel", String.valueOf(id)));

        if (person.status() == PersonnelStatus.INACTIVE) {
            log.warn("人员[{}] 已经是已停用状态，无需重复删除", id);
            return;
        }

        // 1. 领域纯核心软删除
        Personnel deleted = person.softDelete();
        personnelRepository.save(deleted);

        // 2. 停止该人员所有证书的到期提醒（由应用层协调）
        disableCertificateRemindersForPerson(id);

        // 3. 记录人员操作日志（PRD 4.3.1.8: 删除人员-停用）
        logService.save(PersonnelOperationLog.create(
                id,
                currentUserId,
                operatorName,
                PersonnelOperationLog.OperationType.DELETE,
                List.of(new ChangeDetail("status", "ACTIVE", "INACTIVE"),
                        new ChangeDetail("reason", "", reason))
        ));
    }

    private void disableCertificateRemindersForPerson(Long personnelId) {
        // 当前实现策略：
        // 证书到期扫描服务（CertificateExpiryScanAppService）会通过 Personnel 状态过滤
        // 因此当人员变为 INACTIVE 后，后续扫描自动不再包含该人员的证书。
        //
        // 如果需要立即生效（不等下一次定时扫描），可在此处扩展：
        // 调用证书仓储批量将该人员证书的提醒状态置为关闭（如果未来增加 per-certificate 提醒开关字段）。
        log.info("人员[{}] 已软删除，关联证书的到期提醒将在下一次扫描中自动停止", personnelId);
    }
}
