package com.xiyu.bid.export.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {

    @NotBlank(message = "Data type is required")
    @Pattern(regexp = "^(tenders|projects|qualifications|cases|templates|ai-analysis|competition|roi|compliance)$",
            message = "Invalid data type")
    private String dataType;

    @Size(max = 5000, message = "Export params too large")
    private Map<String, Object> params;

    @NotNull(message = "Async flag is required")
    private Boolean async;
}
