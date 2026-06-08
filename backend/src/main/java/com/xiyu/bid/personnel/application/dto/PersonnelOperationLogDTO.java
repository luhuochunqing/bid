package com.xiyu.bid.personnel.application.dto;

import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;

import java.time.LocalDateTime;
import java.util.List;

public record PersonnelOperationLogDTO(
        Long id,
        Long personnelId,
        Long operatorId,
        String operatorName,
        String operationType,
        List<PersonnelOperationLog.ChangeDetail> changeDetails,
        LocalDateTime createdAt
) {

    public static PersonnelOperationLogDTO fromDomain(PersonnelOperationLog log) {
        return new PersonnelOperationLogDTO(
                log.id(),
                log.personnelId(),
                log.operatorId(),
                log.operatorName(),
                log.operationType(),
                log.changeDetails(),
                log.createdAt()
        );
    }
}
