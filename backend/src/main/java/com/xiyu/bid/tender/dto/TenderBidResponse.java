// Input: 投标成功后的项目信息
// Output: 投标申请响应（包含创建的项目信息和待办信息）
// Pos: tender/dto/
package com.xiyu.bid.tender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderBidResponse {

    private boolean accepted;
    private String message;
    private Long projectId;
    private Long todoId;
    private String todoTitle;
}
