// Input: PATCH leads 请求体
// Output: 主/副负责人入参
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectLeadAssignmentRequest {
    private Long primaryLeadUserId;
    private Long secondaryLeadUserId;
}
