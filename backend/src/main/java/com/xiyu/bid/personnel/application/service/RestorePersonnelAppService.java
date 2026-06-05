package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「删除人员」h5 - 恢复功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestorePersonnelAppService {

    private final PersonnelRepository personnelRepository;

    /**
     * 恢复已停用人员
     */
    @Transactional
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead')")
    public void restore(Long id, Long currentUserId) {
        Personnel person = personnelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Personnel", String.valueOf(id)));

        if (person.status() != PersonnelStatus.INACTIVE) {
            log.warn("人员[{}] 当前状态不是已停用，无法恢复", id);
            return;
        }

        Personnel restored = person.restore();
        personnelRepository.save(restored);

        log.info("人员[{}] 已恢复，操作人[{}]", id, currentUserId);

        // 恢复后，证书提醒将在下一次扫描中自动恢复（因为状态变回 ACTIVE）
    }
}
