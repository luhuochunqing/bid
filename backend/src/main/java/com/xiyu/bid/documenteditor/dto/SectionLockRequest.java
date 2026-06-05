package com.xiyu.bid.documenteditor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionLockRequest {

    @NotNull(message = "Section ID is required")
    private Long sectionId;

    @NotNull(message = "Locked state is required")
    private Boolean locked;

    @NotNull(message = "User ID is required")
    private Long userId;
}
