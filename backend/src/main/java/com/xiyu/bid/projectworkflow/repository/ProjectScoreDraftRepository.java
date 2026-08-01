package com.xiyu.bid.projectworkflow.repository;

import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectScoreDraftRepository extends JpaRepository<ProjectScoreDraft, Long> {

    List<ProjectScoreDraft> findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(Long projectId);

    void deleteByProjectIdAndStatusIn(Long projectId, List<ProjectScoreDraft.Status> statuses);

    /**
     * 清除项目中未生成正式任务的草稿（DRAFT / READY / SKIPPED 状态）。
     * 多个导入路径共用此语义，避免在各 service 中重复定义状态列表。
     */
    default void clearNonGeneratedDrafts(Long projectId) {
        deleteByProjectIdAndStatusIn(projectId, List.of(
                ProjectScoreDraft.Status.DRAFT,
                ProjectScoreDraft.Status.READY,
                ProjectScoreDraft.Status.SKIPPED
        ));
    }
}
