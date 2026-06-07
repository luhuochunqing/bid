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
public class CaseShareRecordDTO {

    private Long id;
    private Long caseId;
    private String token;
    private String url;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
