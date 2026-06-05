// Input: 任务完成事件 (projectId, taskId) + 交付物列表
// Output: 幂等地把任务交付物归集为项目文档（ProjectDocument）
// Pos: projectworkflow/service/ - 编排辅助组件
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.task.entity.TaskDeliverable;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务交付物归集到项目文档的辅助组件。
 * <p>幂等：以 (linkedEntityType=TASK, linkedEntityId=taskId) 判重，避免任务重新完成时重复归集。</p>
 * <p>性能：批量 saveAll，避免 N+1 写入。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectTaskDeliverableCollector {

    private static final String LINKED_ENTITY_TYPE_TASK = "TASK";
    private static final String DOCUMENT_CATEGORY = "TASK_DELIVERABLE";

    private final TaskDeliverableRepository taskDeliverableRepository;
    private final ProjectDocumentRepository projectDocumentRepository;

    /**
     * 把指定任务的交付物批量归集为项目文档（幂等）。
     *
     * @param projectId 项目 ID
     * @param taskId 任务 ID
     */
    public void collect(Long projectId, Long taskId) {
        if (projectDocumentRepository.existsByLinkedEntityTypeAndLinkedEntityId(
                LINKED_ENTITY_TYPE_TASK, taskId)) {
            log.debug("Task {} deliverables already collected, skip", taskId);
            return;
        }
        List<TaskDeliverable> deliverables =
                taskDeliverableRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        if (deliverables.isEmpty()) {
            return;
        }
        List<ProjectDocument> docs = deliverables.stream()
                .map(d -> toDoc(projectId, taskId, d))
                .toList();
        projectDocumentRepository.saveAll(docs);
        log.info("Collected {} deliverables from task {} to project {} documents",
                docs.size(), taskId, projectId);
    }

    private ProjectDocument toDoc(Long projectId, Long taskId, TaskDeliverable d) {
        return ProjectDocument.builder()
                .projectId(projectId)
                .name(d.getName())
                .size(d.getSize())
                .fileType(d.getFileType())
                .documentCategory(DOCUMENT_CATEGORY)
                .linkedEntityType(LINKED_ENTITY_TYPE_TASK)
                .linkedEntityId(taskId)
                .fileUrl(d.getStoragePath())
                .uploaderId(d.getUploaderId())
                .uploaderName(d.getUploaderName() == null || d.getUploaderName().isBlank()
                        ? "未知上传者" : d.getUploaderName())
                .build();
    }
}
