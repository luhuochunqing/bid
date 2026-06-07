package com.xiyu.bid.casework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseReferenceRecordDTO {

    private Long id;
    private Long caseId;
    private Long referencedBy;
    private String referencedByName;
    private String referenceTarget;
    private String referenceContext;
    private String sourceProjectName;
    private LocalDateTime referencedAt;
}
