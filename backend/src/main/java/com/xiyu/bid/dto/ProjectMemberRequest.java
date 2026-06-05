package com.xiyu.bid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberRequest {
    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "memberRole 不能为空")
    private String memberRole;

    @NotBlank(message = "permissionLevel 不能为空")
    private String permissionLevel;
}
