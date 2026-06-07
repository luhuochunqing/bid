package com.xiyu.bid.projectworkflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectShareLinkCreateRequest {

    private Long createdBy;

    private String createdByName;

    @NotBlank(message = "分享基础地址不能为空")
    private String baseUrl;

    private LocalDateTime expiresAt;
}
