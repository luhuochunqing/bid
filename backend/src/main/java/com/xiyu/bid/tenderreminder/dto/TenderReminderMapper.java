package com.xiyu.bid.tenderreminder.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.tenderreminder.domain.TenderReminderPolicy;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;

import java.util.Collections;
import java.util.List;

/**
 * 提醒设置DTO映射器（纯核心）
 */
public record TenderReminderMapper(ObjectMapper objectMapper) {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    /**
     * 将实体转换为DTO
     */
    public ReminderSettingDTO toDTO(TenderReminderSetting entity, String tenderTitle) {
        if (entity == null) {
            return null;
        }
        return ReminderSettingDTO.builder()
                .id(entity.getId())
                .tenderId(entity.getTenderId())
                .tenderTitle(tenderTitle)
                .reminderType(entity.getReminderType())
                .reminderTypeDesc(getReminderTypeDesc(entity.getReminderType()))
                .remindBeforeHours(entity.getRemindBeforeHours())
                .reminderTargets(parseReminderTargets(entity.getReminderTargets()))
                .enabled(entity.getEnabled())
                .lastNotifiedAt(entity.getLastNotifiedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 将请求转换为实体
     */
    public TenderReminderSetting toEntity(CreateReminderRequest request, Long userId) {
        if (request == null) {
            return null;
        }
        return TenderReminderSetting.builder()
                .reminderType(request.getReminderType())
                .remindBeforeHours(TenderReminderPolicy.getEffectiveRemindBeforeHours(request.getRemindBeforeHours()))
                .reminderTargets(serializeReminderTargets(request.getReminderTargets()))
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .createdBy(userId)
                .build();
    }

    /**
     * 解析JSON通知目标
     */
    public List<ReminderSettingDTO.ReminderTarget> parseReminderTargets(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReminderSettingDTO.ReminderTarget>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 序列化通知目标为JSON
     */
    public String serializeReminderTargets(List<CreateReminderRequest.ReminderTargetDTO> targets) {
        if (targets == null || targets.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(targets);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * 获取提醒类型描述
     */
    private String getReminderTypeDesc(com.xiyu.bid.tenderreminder.entity.ReminderType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case REGISTRATION_DEADLINE -> "报名截止提醒";
            case BID_OPENING -> "开标提醒";
        };
    }
}
