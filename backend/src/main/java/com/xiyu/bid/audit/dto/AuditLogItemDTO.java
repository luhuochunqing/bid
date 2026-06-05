package com.xiyu.bid.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogItemDTO {
    private Long id;
    private String time;
    private String operator;
    private String department;
    private String role;
    private String actionType;
    private String module;
    private String target;
    private String detail;
    private String ip;
    private String status;
}
