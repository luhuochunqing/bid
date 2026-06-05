package com.xiyu.bid.casework.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseReferenceRecordCreateRequest {

    private Long referencedBy;

    private String referencedByName;

    @NotBlank(message = "引用目标不能为空")
    private String referenceTarget;

    private String referenceContext;
}
