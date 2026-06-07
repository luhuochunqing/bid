package com.xiyu.bid.personnel.domain.model.importtask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人员证书批量导入任务（纯核心领域模型）
 * 采用 Record 实现不变性
 */
public record PersonnelImportTask(
        Long id,
        String taskNo,
        String module,
        ImportTaskStatus status,
        int totalCount,
        int successCount,
        int failureCount,
        int warningCount,
        List<ImportErrorDetail> errorDetails,
        String correctionFileUrl,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public PersonnelImportTask {
        errorDetails = errorDetails == null ? List.of() : List.copyOf(errorDetails);
    }

    public static PersonnelImportTask createNew(String taskNo, Long createdBy) {
        return new PersonnelImportTask(
                null,
                taskNo,
                "PERSONNEL_CERTIFICATE",
                ImportTaskStatus.PENDING,
                0, 0, 0, 0,
                List.of(),
                null,
                createdBy,
                LocalDateTime.now(),
                null
        );
    }

    public PersonnelImportTask withStatus(ImportTaskStatus newStatus) {
        return new PersonnelImportTask(
                id, taskNo, module, newStatus,
                totalCount, successCount, failureCount, warningCount,
                errorDetails, correctionFileUrl, createdBy, createdAt, completedAt
        );
    }
}
