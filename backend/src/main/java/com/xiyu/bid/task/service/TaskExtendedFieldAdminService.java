// Input: 管理端 upsert/disable/enable/reorder 命令
// Output: TaskExtendedFieldAdminDTO（含审计字段）
// Pos: Service/扩展字段管理端业务规则
// 维护声明: 在 service 层强制以下不变量：
//          - key 命名格式校验（小写字母开头）
//          - key 唯一性，且 key 一旦落库不可变
//          - fieldType=select 必须提供 options
//          - options_json 由 ObjectMapper 序列化/反序列化，解析失败退化为空列表
package com.xiyu.bid.task.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.task.dto.TaskExtendedFieldAdminDTO;
import com.xiyu.bid.task.dto.TaskExtendedFieldReorderRequest;
import com.xiyu.bid.task.dto.TaskExtendedFieldUpsertRequest;
import com.xiyu.bid.task.entity.TaskExtendedField;
import com.xiyu.bid.task.entity.TaskExtendedFieldType;
import com.xiyu.bid.task.repository.TaskExtendedFieldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 任务扩展字段管理端服务。
 *
 * <p>承担管理后台的写入路径（新建/更新/启停/排序），并在 service 层强制
 * 数据库 CHECK 约束无法表达的业务不变量（如 key 格式、key 一旦落库不可变、
 * select 类型必须提供 options 等）。</p>
 *
 * <p>所有方法默认事务边界，错误以 {@link IllegalArgumentException} 抛出，
 * 错误信息为面向最终用户的中文描述，由 controller 层翻译为 HTTP 错误响应。</p>
 *
 * <p><strong>与 {@link TaskStatusDictAdminService} 的差异</strong>：扩展字段没有
 * last-initial / last-terminal 之类的"唯一性"语义，{@code disable} 不需要额外守卫；
 * 但 {@code update} 需要特别保护 key 不可变（新 key 进入请求体时被忽略）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaskExtendedFieldAdminService {

    /** key 命名规范：小写字母开头，仅含小写字母/数字/下划线。 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    /** 自动 sortOrder 步长，新增条目 sortOrder = max + STEP。 */
    private static final int SORT_STEP = 10;

    private final TaskExtendedFieldRepository repository;
    private final ObjectMapper objectMapper;

    /** 列出全部扩展字段定义（含已停用），按 sortOrder 升序。 */
    @Transactional(readOnly = true)
    public List<TaskExtendedFieldAdminDTO> listAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder")).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 新建扩展字段。
     *
     * <p>校验：</p>
     * <ul>
     *   <li>key 符合 {@link #KEY_PATTERN}</li>
     *   <li>key 在表中唯一</li>
     *   <li>fieldType = select 时 options 非空</li>
     * </ul>
     * <p>未指定 sortOrder 时取当前最大值 + {@link #SORT_STEP}，新建项默认启用。</p>
     */
    public TaskExtendedFieldAdminDTO create(TaskExtendedFieldUpsertRequest req) {
        validateKey(req.getKey());
        if (repository.existsById(req.getKey())) {
            throw new IllegalArgumentException("扩展字段 key 已存在: " + req.getKey());
        }
        if (req.getFieldType() == TaskExtendedFieldType.select
                && (req.getOptions() == null || req.getOptions().isEmpty())) {
            throw new IllegalArgumentException("select 类型必须提供 options");
        }

        TaskExtendedField entity = new TaskExtendedField();
        entity.setFieldKey(req.getKey());
        entity.setLabel(req.getLabel());
        entity.setFieldType(req.getFieldType());
        entity.setRequired(Boolean.TRUE.equals(req.getRequired()));
        entity.setPlaceholder(req.getPlaceholder());
        entity.setOptionsJson(serializeOptions(req.getOptions()));

        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        } else {
            int nextOrder = repository.findAll().stream()
                    .map(TaskExtendedField::getSortOrder)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0) + SORT_STEP;
            entity.setSortOrder(nextOrder);
        }
        entity.setEnabled(Boolean.TRUE);

        return toDto(repository.save(entity));
    }

    /**
     * 覆盖更新扩展字段（PATCH 语义：null 字段保持原值）。
     *
     * <p>{@code key} 在更新时<strong>不可变</strong>：即便请求体传入了不同的 key，也会被忽略，
     * 主键由路径变量决定。若切换到 select 类型但既未提供新 options、原 options 也为空，
     * 会抛 {@link IllegalArgumentException}。</p>
     */
    public TaskExtendedFieldAdminDTO update(String key, TaskExtendedFieldUpsertRequest req) {
        TaskExtendedField entity = repository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("扩展字段不存在: " + key));

        // key 不可改，req.key 会被完全忽略。
        if (req.getLabel() != null) {
            entity.setLabel(req.getLabel());
        }
        if (req.getFieldType() != null) {
            if (req.getFieldType() == TaskExtendedFieldType.select
                    && (req.getOptions() == null || req.getOptions().isEmpty())
                    && (entity.getOptionsJson() == null || entity.getOptionsJson().isBlank())) {
                throw new IllegalArgumentException("select 类型必须提供 options");
            }
            entity.setFieldType(req.getFieldType());
        }
        if (req.getRequired() != null) {
            entity.setRequired(req.getRequired());
        }
        if (req.getPlaceholder() != null) {
            entity.setPlaceholder(req.getPlaceholder());
        }
        if (req.getOptions() != null) {
            entity.setOptionsJson(serializeOptions(req.getOptions()));
        }
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }

        return toDto(repository.save(entity));
    }

    /**
     * 停用扩展字段。
     *
     * <p>扩展字段没有 last-initial / last-terminal 语义，停用没有额外守卫。</p>
     */
    public TaskExtendedFieldAdminDTO disable(String key) {
        TaskExtendedField entity = repository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("扩展字段不存在: " + key));
        entity.setEnabled(Boolean.FALSE);
        return toDto(repository.save(entity));
    }

    /** 启用扩展字段。 */
    public TaskExtendedFieldAdminDTO enable(String key) {
        TaskExtendedField entity = repository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("扩展字段不存在: " + key));
        entity.setEnabled(Boolean.TRUE);
        return toDto(repository.save(entity));
    }

    /**
     * 批量更新 sortOrder（单事务）。
     *
     * <p>任何 key 不存在则整体回滚。返回更新后的 DTO 列表（按传入顺序）。</p>
     */
    public List<TaskExtendedFieldAdminDTO> reorder(TaskExtendedFieldReorderRequest req) {
        var items = req.getItems();
        var keys = items.stream()
                .map(TaskExtendedFieldReorderRequest.Item::key)
                .toList();
        Map<String, TaskExtendedField> byKey = repository.findAllById(keys).stream()
                .collect(Collectors.toMap(TaskExtendedField::getFieldKey, Function.identity()));

        if (byKey.size() != new HashSet<>(keys).size()) {
            var missing = new HashSet<>(keys);
            missing.removeAll(byKey.keySet());
            throw new IllegalArgumentException("reorder 列表中存在未知 key: " + missing);
        }

        for (var item : items) {
            TaskExtendedField entity = byKey.get(item.key());
            entity.setSortOrder(item.sortOrder());
        }
        return repository.saveAll(byKey.values()).stream()
                .map(this::toDto)
                .toList();
    }

    private void validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "非法 key（必须小写字母开头，仅含小写字母/数字/下划线）: " + key);
        }
    }

    private String serializeOptions(List<TaskExtendedFieldUpsertRequest.OptionItem> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new IllegalArgumentException("options 序列化失败: " + e.getMessage(), e);
        }
    }

    private List<TaskExtendedFieldAdminDTO.OptionItem> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<TaskExtendedFieldAdminDTO.OptionItem>>() {
                    });
        } catch (Exception e) {
            log.warn("Failed to parse optionsJson (key=?): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private TaskExtendedFieldAdminDTO toDto(TaskExtendedField f) {
        return new TaskExtendedFieldAdminDTO(
                f.getFieldKey(),
                f.getLabel(),
                f.getFieldType() != null ? f.getFieldType().name() : null,
                Boolean.TRUE.equals(f.getRequired()),
                f.getPlaceholder(),
                parseOptions(f.getOptionsJson()),
                f.getSortOrder(),
                Boolean.TRUE.equals(f.getEnabled()),
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }
}
