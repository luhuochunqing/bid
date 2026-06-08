package com.xiyu.bid.personnel.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public record PersonnelOperationLog(
        Long id,
        Long personnelId,
        Long operatorId,
        String operatorName,
        String operationType,
        List<ChangeDetail> changeDetails,
        LocalDateTime createdAt
) {

    public record ChangeDetail(
            String field,
            String oldValue,
            String newValue
    ) {}

    public enum OperationType {
        CREATE,
        UPDATE,
        DELETE,
        RESTORE,
        CERTIFICATE_ADD,
        CERTIFICATE_REMOVE,
        CERTIFICATE_UPDATE,
        EDUCATION_ADD,
        EDUCATION_REMOVE,
        EDUCATION_UPDATE
    }

    public static PersonnelOperationLog create(
            Long personnelId,
            Long operatorId,
            String operatorName,
            OperationType operationType,
            List<ChangeDetail> changes
    ) {
        return new PersonnelOperationLog(
                null,
                personnelId,
                operatorId,
                operatorName,
                operationType.name(),
                changes,
                LocalDateTime.now()
        );
    }
}
