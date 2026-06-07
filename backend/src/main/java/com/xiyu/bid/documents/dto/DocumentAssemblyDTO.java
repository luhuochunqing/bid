package com.xiyu.bid.documents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档组装记录数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAssemblyDTO {

    private Long id;
    private Long projectId;
    private Long templateId;
    private String assembledContent;
    private String variables;
    private Long assembledBy;
    private LocalDateTime assembledAt;
}
