// Input: 项目 id、主/副负责人、当前用户
// Output: ProjectDraftingViewDto（包含 leads 与 gate 状态）
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectDraftingViewDto {
    private Long projectId;
    private Long primaryLeadUserId;
    private Long secondaryLeadUserId;
    private Integer incompleteTaskCount;
    private Boolean gateReady;
}
