package com.xiyu.bid.template.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TemplateCopyRequest {

    @NotBlank
    private String name;

    private Long createdBy;
}
