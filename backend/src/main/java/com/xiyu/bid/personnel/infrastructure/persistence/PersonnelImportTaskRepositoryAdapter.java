package com.xiyu.bid.personnel.infrastructure.persistence;

import com.xiyu.bid.personnel.domain.model.importtask.ImportErrorDetail;
import com.xiyu.bid.personnel.domain.model.importtask.ImportTaskStatus;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.domain.port.PersonnelImportTaskRepository;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelImportTaskEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelImportTaskJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PersonnelImportTaskRepositoryAdapter implements PersonnelImportTaskRepository {

    private final PersonnelImportTaskJpaRepository jpaRepository;

    @Override
    @Transactional
    public PersonnelImportTask save(PersonnelImportTask task) {
        PersonnelImportTaskEntity entity = toEntity(task);
        PersonnelImportTaskEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonnelImportTask> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonnelImportTask> findByTaskNo(String taskNo) {
        return jpaRepository.findByTaskNo(taskNo).map(this::toDomain);
    }

    @Override
    @Transactional
    public PersonnelImportTask updateStatus(Long id, String newStatus) {
        PersonnelImportTaskEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Import task not found: " + id));
        entity.setStatus(newStatus);
        PersonnelImportTaskEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    // ========== 映射方法 ==========

    private PersonnelImportTaskEntity toEntity(PersonnelImportTask task) {
        return PersonnelImportTaskEntity.builder()
                .id(task.id())
                .taskNo(task.taskNo())
                .module(task.module())
                .status(task.status() != null ? task.status().name() : ImportTaskStatus.PENDING.name())
                .totalCount(task.totalCount())
                .successCount(task.successCount())
                .failureCount(task.failureCount())
                .warningCount(task.warningCount())
                .errorDetails(serializeErrorDetails(task.errorDetails()))
                .correctionFileUrl(task.correctionFileUrl())
                .createdBy(task.createdBy())
                .createdAt(task.createdAt())
                .completedAt(task.completedAt())
                .build();
    }

    private PersonnelImportTask toDomain(PersonnelImportTaskEntity e) {
        if (e == null) return null;
        return new PersonnelImportTask(
                e.getId(),
                e.getTaskNo(),
                e.getModule(),
                ImportTaskStatus.valueOf(e.getStatus()),
                e.getTotalCount() != null ? e.getTotalCount() : 0,
                e.getSuccessCount() != null ? e.getSuccessCount() : 0,
                e.getFailureCount() != null ? e.getFailureCount() : 0,
                e.getWarningCount() != null ? e.getWarningCount() : 0,
                deserializeErrorDetails(e.getErrorDetails()),
                e.getCorrectionFileUrl(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getCompletedAt()
        );
    }

    private String serializeErrorDetails(List<ImportErrorDetail> details) {
        if (details == null || details.isEmpty()) return "[]";
        // 简化实现，后续可换成 Jackson
        return details.toString();
    }

    private List<ImportErrorDetail> deserializeErrorDetails(String json) {
        // 简化实现，后续完善
        return List.of();
    }
}
