package com.xiyu.bid.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAutoTaskDTO {
    private String id;
    private String title;
    private String owner;
    private String dueDate;
    private String priority;
}
