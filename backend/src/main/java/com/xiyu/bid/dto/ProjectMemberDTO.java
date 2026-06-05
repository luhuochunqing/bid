package com.xiyu.bid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberDTO {
    private Long id;
    private Long projectId;
    private Long userId;
    private String username;
    private String fullName;
    private String memberRole;
    private String permissionLevel;
    private boolean isInherited;
    private LocalDateTime createdAt;
}
