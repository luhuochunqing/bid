package com.xiyu.bid.personnel.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.personnel.domain.model.importtask.ImportErrorDetail;
import com.xiyu.bid.personnel.domain.model.importtask.ImportTaskStatus;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.domain.port.PersonnelImportTaskRepository;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelImportTaskEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelImportTaskJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonnelImportTaskRepositoryAdapter implements PersonnelImportTaskRepository {

    private final PersonnelImportTaskJpaRepository jpaRepository;
    // CO-469 第八轮：ObjectMapper 是线程安全的，可作为静态共享实例
    private static final ObjectMapper ERROR_DETAIL_MAPPER = new ObjectMapper();

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

    // CO-469 第八轮：用 Jackson 序列化替代 List.toString()，输出合法 JSON
    // 历史根因：List.toString() 输出格式 [ImportErrorDetail[sheetName=系统, ...]] 不是合法 JSON，
    // MySQL cast(? as json) 必失败，导致 failImportTask 自身抛 DataIntegrityViolationException，
    // 二次异常被 SimpleAsyncUncaughtExceptionHandler 吞掉，任务状态永久停在 PROCESSING/5%。
    private String serializeErrorDetails(List<ImportErrorDetail> details) {
        if (details == null || details.isEmpty()) return "[]";
        try {
            return ERROR_DETAIL_MAPPER.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            // 极端情况：errorMessage 包含非法 UTF-8 序列等导致 Jackson 失败
            // 降级为空数组，确保至少 status=FAILED 能写入数据库
            log.warn("序列化 ImportErrorDetail 失败，降级为空数组: {}", e.getMessage());
            return "[]";
        }
    }

    private List<ImportErrorDetail> deserializeErrorDetails(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            ImportErrorDetail[] array = ERROR_DETAIL_MAPPER.readValue(json, ImportErrorDetail[].class);
            return List.of(array);
        } catch (JsonProcessingException e) {
            // 历史脏数据兼容：早期 List.toString() 写入的非法 JSON 在数据库中可能仍然存在
            // 这些记录无法反序列化，返回空列表（不影响读取 status 等关键字段）
            log.warn("反序列化 ImportErrorDetail 失败，返回空列表: {}", e.getMessage());
            return List.of();
        }
    }
}
