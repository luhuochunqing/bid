package com.xiyu.bid.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建评论请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateRequest {

    @NotNull(message = "Thread ID is required")
    private Long threadId;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Content is required")
    private String content;

    private String mentions;

    private Long parentId;
}
