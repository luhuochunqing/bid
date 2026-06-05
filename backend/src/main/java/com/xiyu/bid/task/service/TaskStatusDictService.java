// Input: TaskStatusDictRepository 持久化查询
// Output: TaskStatusDictDTO 投影列表（只读查询场景）
// Pos: Service/查询用例层
// 维护声明: 只做启用字典读取；写入/校验规则（例如 is_initial 全表唯一）后续任务再引入。
package com.xiyu.bid.task.service;

import com.xiyu.bid.task.dto.TaskStatusDictDTO;
import com.xiyu.bid.task.entity.TaskStatusDict;
import com.xiyu.bid.task.repository.TaskStatusDictRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 任务状态字典查询服务。
 *
 * <p>当前仅暴露"列出启用项"用例，供看板列渲染与下拉筛选使用。
 * 返回顺序完全委托给 {@link TaskStatusDictRepository#findByEnabledTrueOrderBySortOrderAsc()}，
 * 保持"sort_order 升序"的外部契约。</p>
 */
@Service
public class TaskStatusDictService {

    private final TaskStatusDictRepository repository;

    public TaskStatusDictService(TaskStatusDictRepository repository) {
        this.repository = repository;
    }

    /**
     * 列出所有启用中的状态字典项。
     *
     * <p>将实体投影为 {@link TaskStatusDictDTO}：</p>
     * <ul>
     *   <li>{@code category} 用 {@code enum.name()} 序列化为字符串，避免前端依赖 Java 枚举</li>
     *   <li>{@code isInitial}/{@code isTerminal} 为 {@link Boolean} 包装类型，使用
     *       {@code Boolean.TRUE.equals(...)} 做空安全转换（尽管列非空，这里保持纵深防御）</li>
     * </ul>
     *
     * @return 启用状态 DTO 列表，顺序与仓储保持一致
     */
    @Transactional(readOnly = true)
    public List<TaskStatusDictDTO> listEnabled() {
        return repository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(TaskStatusDictService::toDto)
                .toList();
    }

    private static TaskStatusDictDTO toDto(TaskStatusDict entity) {
        return new TaskStatusDictDTO(
                entity.getCode(),
                entity.getName(),
                entity.getCategory().name(),
                entity.getColor(),
                entity.getSortOrder(),
                Boolean.TRUE.equals(entity.getIsInitial()),
                Boolean.TRUE.equals(entity.getIsTerminal())
        );
    }
}
