package com.xiyu.bid.task.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.task.dto.TaskExtendedFieldDTO;
import com.xiyu.bid.task.entity.TaskExtendedField;
import com.xiyu.bid.task.repository.TaskExtendedFieldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 公开读取任务扩展字段 schema 的服务层。
 *
 * <p>当前仅承担 "列出启用字段" 用例，供前端 TaskForm 渲染扩展字段输入控件。
 * 管理端写接口（CRUD / reorder）由后续任务 N4-A4/A5 引入，不在本服务范围内。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskExtendedFieldService {

    private final TaskExtendedFieldRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * 列出所有 {@code enabled = true} 的扩展字段，按 {@code sortOrder} 升序。
     *
     * <p>{@code optionsJson} 在此处一次性反序列化为结构化 {@link TaskExtendedFieldDTO.OptionItem}
     * 列表，避免前端二次解析；解析失败会打 warn 并返回空列表，不影响整体列表输出。</p>
     *
     * @return 启用字段 DTO 列表
     */
    public List<TaskExtendedFieldDTO> listEnabled() {
        return repository.findByEnabledTrueOrderBySortOrderAsc().stream()
            .map(this::toDto)
            .toList();
    }

    private TaskExtendedFieldDTO toDto(TaskExtendedField f) {
        List<TaskExtendedFieldDTO.OptionItem> options = parseOptions(f.getOptionsJson());
        return new TaskExtendedFieldDTO(
            f.getFieldKey(),
            f.getLabel(),
            f.getFieldType().name(),
            Boolean.TRUE.equals(f.getRequired()),
            f.getPlaceholder(),
            options,
            f.getSortOrder() == null ? 0 : f.getSortOrder()
        );
    }

    private List<TaskExtendedFieldDTO.OptionItem> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TaskExtendedFieldDTO.OptionItem>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse optionsJson: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
