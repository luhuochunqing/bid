package com.xiyu.bid.batch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeleteRequest {
    @NotEmpty(message = "项目ID列表不能为空")
    private List<@NotNull(message = "项目ID不能为空") Long> itemIds;
    @NotNull(message = "操作用户ID不能为空")
    private Long userId;
    private String reason;
    private Boolean forceDelete = false;
}
