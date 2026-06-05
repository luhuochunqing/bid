package com.xiyu.bid.documenteditor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionAssignmentRequest {

    @NotNull(message = "Section ID is required")
    private Long sectionId;

    @NotBlank(message = "Owner is required")
    private String owner;

    @NotNull(message = "Assigned by is required")
    private Long assignedBy;

    private LocalDate dueDate;
}
