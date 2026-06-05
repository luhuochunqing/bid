package com.xiyu.bid.documenteditor.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 重新排序章节请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionReorderRequest {

    @NotNull(message = "Structure ID is required")
    private Long structureId;

    @NotEmpty(message = "Section orders map cannot be empty")
    private Map<Long, Integer> sectionOrders;
}
