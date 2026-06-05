package com.xiyu.bid.documenteditor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReminderDTO {

    private Long id;
    private Long projectId;
    private Long sectionId;
    private String recipient;
    private String message;
    private Long remindedBy;
    private LocalDateTime remindedAt;
}
