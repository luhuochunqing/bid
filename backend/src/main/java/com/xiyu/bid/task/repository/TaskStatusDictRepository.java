package com.xiyu.bid.task.repository;

import com.xiyu.bid.task.entity.TaskStatusDict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TaskStatusDict} entities.
 *
 * <p>Primary key is the status {@code code} (VARCHAR, not Long).</p>
 */
@Repository
public interface TaskStatusDictRepository
        extends JpaRepository<TaskStatusDict, String> {

    /**
     * 查询全部启用中的状态字典项，按 {@code sort_order} 升序排列。
     *
     * <p>用于看板列渲染与下拉筛选器。</p>
     *
     * @return 启用中的状态列表，按 sortOrder 升序
     */
    List<TaskStatusDict> findByEnabledTrueOrderBySortOrderAsc();

    /**
     * Find the single row marked as initial status.
     *
     * <p>Uniqueness is enforced at service layer on write;
     * if multiple exist due to data corruption, behavior is
     * undefined — callers should treat this as a system
     * invariant violation.</p>
     *
     * @return optional holding the initial status, if any
     */
    Optional<TaskStatusDict> findByIsInitialTrue();
}
