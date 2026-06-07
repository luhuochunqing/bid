package com.xiyu.bid.tenderreminder.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tenderreminder.domain.TenderReminderPolicy;
import com.xiyu.bid.tenderreminder.dto.CreateReminderRequest;
import com.xiyu.bid.tenderreminder.dto.ReminderSettingDTO;
import com.xiyu.bid.tenderreminder.dto.TenderReminderMapper;
import com.xiyu.bid.tenderreminder.dto.UpdateReminderRequest;
import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;
import com.xiyu.bid.tenderreminder.repository.TenderReminderSettingRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 标讯提醒服务
 * Pure Core: 业务逻辑与策略
 * Imperative Shell: 事务控制、持久化
 */
@Service
@RequiredArgsConstructor
public class TenderReminderService {

    private static final Logger log = LoggerFactory.getLogger(TenderReminderService.class);

    private final TenderReminderSettingRepository reminderRepository;
    private final TenderRepository tenderRepository;
    private final TenderReminderMapper mapper;

    /**
     * 获取标讯的所有提醒设置
     */
    @Transactional(readOnly = true)
    public List<ReminderSettingDTO> getRemindersByTenderId(Long tenderId) {
        if (tenderId == null) {
            return List.of();
        }
        Optional<Tender> tenderOpt = tenderRepository.findById(tenderId);
        String tenderTitle = tenderOpt.map(Tender::getTitle).orElse("未知标讯");

        return reminderRepository.findByTenderId(tenderId).stream()
                .map(entity -> mapper.toDTO(entity, tenderTitle))
                .toList();
    }

    /**
     * 获取标讯的单个提醒设置
     */
    @Transactional(readOnly = true)
    public Optional<ReminderSettingDTO> getReminderById(Long reminderId) {
        return reminderRepository.findById(reminderId)
                .map(entity -> {
                    String tenderTitle = tenderRepository.findById(entity.getTenderId())
                            .map(Tender::getTitle)
                            .orElse("未知标讯");
                    return mapper.toDTO(entity, tenderTitle);
                });
    }

    /**
     * 创建提醒设置
     */
    @Transactional
    public ReminderSettingDTO createReminder(Long tenderId, CreateReminderRequest request, Long userId) {
        validateTenderExists(tenderId);
        validateRequest(request);

        // 检查是否已存在同类型提醒
        Optional<TenderReminderSetting> existing = reminderRepository
                .findByTenderIdAndReminderType(tenderId, request.getReminderType());

        if (existing.isPresent()) {
            throw new IllegalStateException("该标讯已存在相同类型的提醒设置");
        }

        TenderReminderSetting entity = mapper.toEntity(request, userId);
        entity.setTenderId(tenderId);
        entity = reminderRepository.save(entity);

        log.info("创建提醒设置成功: tenderId={}, type={}, id={}",
                tenderId, request.getReminderType(), entity.getId());

        String tenderTitle = tenderRepository.findById(tenderId)
                .map(Tender::getTitle)
                .orElse("未知标讯");
        return mapper.toDTO(entity, tenderTitle);
    }

    /**
     * 更新提醒设置
     */
    @Transactional
    public Optional<ReminderSettingDTO> updateReminder(Long reminderId, UpdateReminderRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        return reminderRepository.findById(reminderId)
                .map(entity -> {
                    if (request.getRemindBeforeHours() != null) {
                        entity.setRemindBeforeHours(TenderReminderPolicy.getEffectiveRemindBeforeHours(
                                request.getRemindBeforeHours()));
                    }
                    if (request.getReminderTargets() != null) {
                        entity.setReminderTargets(mapper.serializeReminderTargets(request.getReminderTargets()));
                    }
                    if (request.getEnabled() != null) {
                        entity.setEnabled(request.getEnabled());
                    }
                    entity = reminderRepository.save(entity);

                    log.info("更新提醒设置成功: id={}", reminderId);

                    String tenderTitle = tenderRepository.findById(entity.getTenderId())
                            .map(Tender::getTitle)
                            .orElse("未知标讯");
                    return mapper.toDTO(entity, tenderTitle);
                });
    }

    /**
     * 删除提醒设置
     */
    @Transactional
    public void deleteReminder(Long reminderId) {
        if (!reminderRepository.existsById(reminderId)) {
            throw new IllegalArgumentException("提醒设置不存在");
        }
        reminderRepository.deleteById(reminderId);
        log.info("删除提醒设置成功: id={}", reminderId);
    }

    /**
     * 切换提醒启用状态
     */
    @Transactional
    public Optional<ReminderSettingDTO> toggleReminder(Long reminderId) {
        return reminderRepository.findById(reminderId)
                .map(entity -> {
                    entity.setEnabled(!Boolean.TRUE.equals(entity.getEnabled()));
                    entity = reminderRepository.save(entity);

                    log.info("切换提醒状态: id={}, enabled={}", reminderId, entity.getEnabled());

                    String tenderTitle = tenderRepository.findById(entity.getTenderId())
                            .map(Tender::getTitle)
                            .orElse("未知标讯");
                    return mapper.toDTO(entity, tenderTitle);
                });
    }

    /**
     * 验证标讯存在
     */
    private void validateTenderExists(Long tenderId) {
        if (!tenderRepository.existsById(tenderId)) {
            throw new IllegalArgumentException("标讯不存在: " + tenderId);
        }
    }

    /**
     * 验证请求参数
     */
    private void validateRequest(CreateReminderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (request.getReminderType() == null) {
            throw new IllegalArgumentException("提醒类型不能为空");
        }
        if (!TenderReminderPolicy.isValidRemindBeforeHours(request.getRemindBeforeHours())) {
            throw new IllegalArgumentException("提前提醒小时数必须在1-168之间");
        }
        if (request.getReminderTargets() == null || request.getReminderTargets().isEmpty()) {
            throw new IllegalArgumentException("通知对象不能为空");
        }
    }
}
