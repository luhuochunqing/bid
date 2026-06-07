package com.xiyu.bid.task.repository;

import com.xiyu.bid.task.entity.TaskExtendedField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link TaskExtendedField} entities.
 *
 * <p>Primary key is the field {@code key} (VARCHAR, mapped via the
 * {@code fieldKey} Java property — DB column name is a MySQL reserved
 * word and is quoted on the entity).</p>
 */
@Repository
public interface TaskExtendedFieldRepository
        extends JpaRepository<TaskExtendedField, String> {

    /**
     * 查询全部启用中的扩展字段定义，按 {@code sort_order} 升序排列。
     *
     * <p>用于任务表单渲染扩展字段输入控件的顺序。</p>
     *
     * @return 启用中的扩展字段列表，按 sortOrder 升序
     */
    List<TaskExtendedField> findByEnabledTrueOrderBySortOrderAsc();
}
