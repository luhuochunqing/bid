// Input: 管理端 upsert/disable/enable/reorder 命令
// Output: TaskStatusDictAdminDTO（含审计字段）
// Pos: Service/状态字典管理端业务规则
// 维护声明: 在 service 层强制以下不变量：
//          - code 大写格式校验
//          - code 唯一性
//          - is_initial 全表至多一条
//          - 不能停用唯一启用的初始/终态
package com.xiyu.bid.task.service;

import com.xiyu.bid.task.dto.TaskStatusDictAdminDTO;
import com.xiyu.bid.task.dto.TaskStatusDictReorderRequest;
import com.xiyu.bid.task.dto.TaskStatusDictUpsertRequest;
import com.xiyu.bid.task.entity.TaskStatusDict;
import com.xiyu.bid.task.repository.TaskStatusDictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 任务状态字典管理端服务。
 *
 * <p>承担管理后台的写入路径（新建/更新/启停/排序），并在 service 层强制
 * 数据库无法表达的业务不变量（如 {@code is_initial} 全表唯一、不能停用
 * 最后一个终态等）。</p>
 *
 * <p>所有方法默认事务边界，错误以 {@link IllegalArgumentException} /
 * {@link IllegalStateException} 抛出，错误信息为面向最终用户的中文描述，
 * 由 controller 层翻译为 HTTP 错误响应。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskStatusDictAdminService {

    /** Code 命名规范：大写字母开头，仅含大写字母/数字/下划线。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    /** 自动 sortOrder 步长，新增条目 sortOrder = max + STEP。 */
    private static final int SORT_STEP = 10;

    /** 默认色值（创建时未指定 color 时回退）。 */
    private static final String DEFAULT_COLOR = "#909399";

    private final TaskStatusDictRepository repo;

    /** 列出全部字典项（含已停用），按 sortOrder 升序。 */
    @Transactional(readOnly = true)
    public List<TaskStatusDictAdminDTO> listAll() {
        return repo.findAll(Sort.by("sortOrder").ascending()).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 新建字典项。
     *
     * <p>校验 code 格式与唯一性；若 {@code isInitial=true}，自动清除其它项的
     * initial 标记；未指定 sortOrder 时取当前最大值 + 步长。新建项默认启用。</p>
     */
    public TaskStatusDictAdminDTO create(TaskStatusDictUpsertRequest req) {
        validateCode(req.getCode());
        if (repo.existsById(req.getCode())) {
            throw new IllegalArgumentException("Code 已存在: " + req.getCode());
        }
        if (Boolean.TRUE.equals(req.getIsInitial())) {
            clearOtherInitials(req.getCode());
        }
        TaskStatusDict entity = new TaskStatusDict();
        entity.setCode(req.getCode());
        applyFields(entity, req);
        if (req.getSortOrder() == null) {
            int max = repo.findAll().stream()
                    .mapToInt(TaskStatusDict::getSortOrder)
                    .max()
                    .orElse(0);
            entity.setSortOrder(max + SORT_STEP);
        }
        if (entity.getColor() == null) {
            entity.setColor(DEFAULT_COLOR);
        }
        if (entity.getIsInitial() == null) {
            entity.setIsInitial(Boolean.FALSE);
        }
        if (entity.getIsTerminal() == null) {
            entity.setIsTerminal(Boolean.FALSE);
        }
        entity.setEnabled(Boolean.TRUE);
        return toDto(repo.save(entity));
    }

    /**
     * 覆盖更新字典项（PATCH 语义：null 字段保持原值）。
     *
     * <p>当 {@code isInitial} 由 false → true 时自动清除其它项的 initial 标记。</p>
     */
    public TaskStatusDictAdminDTO update(String code, TaskStatusDictUpsertRequest req) {
        TaskStatusDict entity = repo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("字典项不存在: " + code));
        if (Boolean.TRUE.equals(req.getIsInitial())
                && !Boolean.TRUE.equals(entity.getIsInitial())) {
            clearOtherInitials(code);
        }
        applyFields(entity, req);
        return toDto(repo.save(entity));
    }

    /**
     * 停用字典项。
     *
     * <p>不变量：</p>
     * <ul>
     *   <li>不能停用初始状态（先把另一个项设为 initial 再停用）</li>
     *   <li>不能停用唯一一个启用中的终态</li>
     * </ul>
     */
    public TaskStatusDictAdminDTO disable(String code) {
        TaskStatusDict entity = repo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("字典项不存在: " + code));
        if (Boolean.TRUE.equals(entity.getIsInitial())) {
            throw new IllegalStateException(
                    "不能停用初始状态。请先把另一个字典项设为初始再停用此项。");
        }
        if (Boolean.TRUE.equals(entity.getIsTerminal())) {
            long enabledTerminalsExcludingMe = repo.findAll().stream()
                    .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                    .filter(s -> Boolean.TRUE.equals(s.getIsTerminal()))
                    .filter(s -> !s.getCode().equals(code))
                    .count();
            if (enabledTerminalsExcludingMe == 0) {
                throw new IllegalStateException(
                        "不能停用唯一的终态。请先新增另一个终态字典项。");
            }
        }
        entity.setEnabled(Boolean.FALSE);
        return toDto(repo.save(entity));
    }

    /** 启用字典项。 */
    public TaskStatusDictAdminDTO enable(String code) {
        TaskStatusDict entity = repo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("字典项不存在: " + code));
        entity.setEnabled(Boolean.TRUE);
        return toDto(repo.save(entity));
    }

    /** 批量更新 sortOrder（单事务）。任何 code 不存在则整体回滚。 */
    public void reorder(TaskStatusDictReorderRequest req) {
        var items = req.getItems();
        var codes = items.stream()
                .map(TaskStatusDictReorderRequest.Item::code)
                .toList();
        Map<String, TaskStatusDict> byCode = repo.findAllById(codes).stream()
                .collect(Collectors.toMap(TaskStatusDict::getCode, Function.identity()));
        for (var item : items) {
            TaskStatusDict entity = byCode.get(item.code());
            if (entity == null) {
                throw new IllegalArgumentException("字典项不存在: " + item.code());
            }
            entity.setSortOrder(item.sortOrder());
        }
        repo.saveAll(byCode.values());
    }

    private void clearOtherInitials(String exceptCode) {
        repo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsInitial()))
                .filter(s -> !s.getCode().equals(exceptCode))
                .forEach(s -> {
                    s.setIsInitial(Boolean.FALSE);
                    repo.save(s);
                });
    }

    private void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Code 必须大写字母开头，仅含大写字母/数字/下划线: " + code);
        }
    }

    private void applyFields(TaskStatusDict entity, TaskStatusDictUpsertRequest req) {
        if (req.getName() != null) {
            entity.setName(req.getName());
        }
        if (req.getCategory() != null) {
            entity.setCategory(req.getCategory());
        }
        if (req.getColor() != null) {
            entity.setColor(req.getColor());
        }
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }
        if (req.getIsInitial() != null) {
            entity.setIsInitial(req.getIsInitial());
        }
        if (req.getIsTerminal() != null) {
            entity.setIsTerminal(req.getIsTerminal());
        }
    }

    private TaskStatusDictAdminDTO toDto(TaskStatusDict s) {
        return new TaskStatusDictAdminDTO(
                s.getCode(),
                s.getName(),
                s.getCategory() != null ? s.getCategory().name() : null,
                s.getColor(),
                s.getSortOrder(),
                Boolean.TRUE.equals(s.getIsInitial()),
                Boolean.TRUE.equals(s.getIsTerminal()),
                Boolean.TRUE.equals(s.getEnabled()),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
