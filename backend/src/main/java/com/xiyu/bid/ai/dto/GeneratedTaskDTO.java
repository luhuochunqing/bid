package com.xiyu.bid.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedTaskDTO {
    private String name;
    private String priority;
    private String suggestion;
    private Boolean selected;
}
