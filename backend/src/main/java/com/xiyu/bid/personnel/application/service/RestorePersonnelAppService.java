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
 * 「删除人员」应用服务 - 恢复功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestorePersonnelAppService {

    private final PersonnelRepository personnelRepository;
    private final PersonnelOperationLogService logService;

    /**
     * 恢复已停用人员
     */
    @Transactional
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead')")
    public void restore(Long id, Long currentUserId, String operatorName) {
        Personnel person = personnelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Personnel", String.valueOf(id)));

        if (person.status() != PersonnelStatus.INACTIVE) {
            log.warn("人员[{}] 当前状态不是已停用，无法恢复", id);
            return;
        }

        Personnel restored = person.restore();
        personnelRepository.save(restored);

        // 记录操作日志（PRD 4.3.1.8: 恢复人员）
        logService.save(PersonnelOperationLog.create(
                id,
                currentUserId,
                operatorName,
                PersonnelOperationLog.OperationType.RESTORE,
                List.of(new ChangeDetail("status", "INACTIVE", "ACTIVE"))
        ));

        log.info("人员[{}] 已恢复，操作人[{}]", id, currentUserId);

        // 恢复后，证书提醒将在下一次扫描中自动恢复（因为状态变回 ACTIVE）
    }
}
