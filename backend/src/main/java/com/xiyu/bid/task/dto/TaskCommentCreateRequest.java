// Input: task comment body from REST request
// Output: validated immutable comment create command
// Pos: DTO/任务评论创建请求
package com.xiyu.bid.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskCommentCreateRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 5000, message = "评论内容不能超过5000字符")
        String content
) {
}
