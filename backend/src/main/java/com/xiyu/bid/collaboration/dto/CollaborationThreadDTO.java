package com.xiyu.bid.collaboration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 协作讨论线程数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationThreadDTO {

    private Long id;
    private Long projectId;
    private String title;
    private ThreadStatus status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
