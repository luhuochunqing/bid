package com.xiyu.bid.documenteditor.dto;

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
public class SectionReminderRequest {

    @NotNull(message = "Section ID is required")
    private Long sectionId;

    @NotBlank(message = "Recipient is required")
    private String recipient;

    @NotNull(message = "Reminded by is required")
    private Long remindedBy;

    private String message;
}
