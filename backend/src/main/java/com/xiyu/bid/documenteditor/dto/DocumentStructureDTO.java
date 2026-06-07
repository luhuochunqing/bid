package com.xiyu.bid.documenteditor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档结构数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStructureDTO {

    private Long id;
    private Long projectId;
    private String name;
    private Long rootSectionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
