// Input: collaboration repositories, DTOs, and support services
// Output: Collaboration business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.collaboration.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.collaboration.dto.CommentCreateRequest;
import com.xiyu.bid.collaboration.dto.CommentDTO;
import com.xiyu.bid.collaboration.dto.CommentUpdateRequest;
import com.xiyu.bid.collaboration.dto.ThreadCreateRequest;
import com.xiyu.bid.collaboration.dto.CollaborationThreadDTO;
import com.xiyu.bid.collaboration.dto.ThreadStatus;
import com.xiyu.bid.collaboration.entity.Comment;
import com.xiyu.bid.collaboration.entity.CollaborationThread;
import com.xiyu.bid.collaboration.repository.CommentRepository;
import com.xiyu.bid.collaboration.repository.CollaborationThreadRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 协作服务
 * 处理协作讨论和评论的业务逻辑
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollaborationService {

    private final CollaborationThreadRepository threadRepository;
    private final CommentRepository commentRepository;
    private final IAuditLogService auditLogService;

    private static final int MAX_COMMENT_LENGTH = 10000;

    /**
     * 创建讨论线程
     */
    @Auditable(action = "CREATE", entityType = "CollaborationThread", description = "Create new collaboration thread")
    @Transactional
    public CollaborationThreadDTO createThread(ThreadCreateRequest request) {
        log.info("Creating collaboration thread for project: {}", request.getProjectId());

        // Validate input
        validateThreadRequest(request);

        CollaborationThread thread = CollaborationThread.builder()
                .projectId(request.getProjectId())
                .title(InputSanitizer.stripHtml(request.getTitle()))
                .status(CollaborationThread.ThreadStatus.OPEN)
                .createdBy(request.getCreatedBy())
                .build();

        CollaborationThread savedThread = threadRepository.save(thread);
        log.info("Created collaboration thread with id: {}", savedThread.getId());

        return convertToThreadDTO(savedThread);
    }

    /**
     * 添加评论
     */
    @Auditable(action = "CREATE", entityType = "Comment", description = "Add comment to thread")
    @Transactional
    public CommentDTO addComment(Long threadId, CommentCreateRequest request) {
        log.info("Adding comment to thread: {}", threadId);

        CollaborationThread thread = loadThread(threadId);
        ensureThreadCanAcceptComments(thread);

        // Validate input
        validateCommentRequest(request);

        Comment comment = Comment.builder()
                .threadId(threadId)
                .userId(request.getUserId())
                .content(InputSanitizer.stripHtml(request.getContent()))
                .mentions(request.getMentions())
                .parentId(request.getParentId())
                .isDeleted(false)
                .build();

        Comment savedComment = commentRepository.save(comment);
        log.info("Added comment with id: {} to thread: {}", savedComment.getId(), threadId);

        return convertToCommentDTO(savedComment);
    }

    /**
     * 更新评论
     */
    @Auditable(action = "UPDATE", entityType = "Comment", description = "Update comment")
    @Transactional
    public CommentDTO updateComment(Long commentId, CommentUpdateRequest request) {
        log.info("Updating comment: {}", commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));

        // Check if comment is deleted
        if (comment.getIsDeleted()) {
            throw new IllegalStateException("Cannot update deleted comment");
        }

        // Validate input
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        comment.setContent(InputSanitizer.stripHtml(request.getContent()));

        Comment updatedComment = commentRepository.save(comment);
        log.info("Updated comment: {}", commentId);

        return convertToCommentDTO(updatedComment);
    }

    /**
     * 删除评论（软删除）
     */
    @Auditable(action = "DELETE", entityType = "Comment", description = "Soft delete comment")
    @Transactional
    public void deleteComment(Long commentId) {
        log.info("Deleting comment: {}", commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));

        // Check if already deleted
        if (comment.getIsDeleted()) {
            throw new IllegalStateException("Comment already deleted");
        }

        comment.setIsDeleted(true);
        commentRepository.save(comment);

        log.info("Deleted comment: {}", commentId);
    }

    /**
     * 根据项目ID获取讨论线程列表
     */
    @Transactional(readOnly = true)
    public List<CollaborationThreadDTO> getThreadsByProject(Long projectId) {
        log.debug("Fetching threads for project: {}", projectId);
        return threadRepository.findByProjectId(projectId).stream()
                .map(this::convertToThreadDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据线程ID获取评论列表
     */
    @Transactional(readOnly = true)
    public List<CommentDTO> getCommentsByThread(Long threadId) {
        log.debug("Fetching comments for thread: {}", threadId);
        loadThread(threadId);
        return commentRepository.findByThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(threadId).stream()
                .map(this::convertToCommentDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取提及特定用户的评论
     */
    @Transactional(readOnly = true)
    public List<CommentDTO> getMentionsForUser(Long userId) {
        log.debug("Fetching mentions for user: {}", userId);
        String mentionPattern = "[" + userId + "]";
        return commentRepository.findByMentionsContainingAndIsDeletedFalse(mentionPattern).stream()
                .map(this::convertToCommentDTO)
                .collect(Collectors.toList());
    }

    /**
     * 更新线程状态
     */
    @Auditable(action = "UPDATE", entityType = "CollaborationThread", description = "Update thread status")
    @Transactional
    public CollaborationThreadDTO updateThreadStatus(Long threadId, ThreadStatus status) {
        log.info("Updating thread {} status to: {}", threadId, status);

        CollaborationThread thread = loadThread(threadId);

        // 转换DTO枚举到Entity枚举
        CollaborationThread.ThreadStatus entityStatus = convertToEntityStatus(status);
        thread.setStatus(entityStatus);
        CollaborationThread updatedThread = threadRepository.save(thread);

        log.info("Updated thread {} status to: {}", threadId, status);

        return convertToThreadDTO(updatedThread);
    }

    /**
     * 根据ID获取线程
     */
    @Transactional(readOnly = true)
    public CollaborationThreadDTO getThreadById(Long threadId) {
        log.debug("Fetching thread by id: {}", threadId);
        CollaborationThread thread = loadThread(threadId);
        return convertToThreadDTO(thread);
    }

    /**
     * 验证线程请求
     */
    private void validateThreadRequest(ThreadCreateRequest request) {
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
    }

    /**
     * 验证评论请求
     */
    private void validateCommentRequest(CommentCreateRequest request) {
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }
        if (request.getContent().length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(
                "Content length exceeds maximum allowed length of " + MAX_COMMENT_LENGTH + " characters");
        }
    }

    private CollaborationThread loadThread(Long threadId) {
        return threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found with id: " + threadId));
    }

    private void ensureThreadCanAcceptComments(CollaborationThread thread) {
        if (thread.getStatus() == CollaborationThread.ThreadStatus.CLOSED) {
            throw new IllegalStateException("Cannot add comments to a closed thread");
        }
    }

    /**
     * 转换线程实体为DTO
     */
    private CollaborationThreadDTO convertToThreadDTO(CollaborationThread thread) {
        return CollaborationThreadDTO.builder()
                .id(thread.getId())
                .projectId(thread.getProjectId())
                .title(thread.getTitle())
                .status(convertToDTOStatus(thread.getStatus()))
                .createdBy(thread.getCreatedBy())
                .createdAt(thread.getCreatedAt())
                .updatedAt(thread.getUpdatedAt())
                .build();
    }

    /**
     * 转换评论实体为DTO
     */
    private CommentDTO convertToCommentDTO(Comment comment) {
        return CommentDTO.builder()
                .id(comment.getId())
                .threadId(comment.getThreadId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .mentions(comment.getMentions())
                .parentId(comment.getParentId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .isDeleted(comment.getIsDeleted())
                .build();
    }

    /**
     * 转换Entity枚举到DTO枚举
     */
    private ThreadStatus convertToDTOStatus(CollaborationThread.ThreadStatus entityStatus) {
        return ThreadStatus.valueOf(entityStatus.name());
    }

    /**
     * 转换DTO枚举到Entity枚举
     */
    private CollaborationThread.ThreadStatus convertToEntityStatus(ThreadStatus dtoStatus) {
        return CollaborationThread.ThreadStatus.valueOf(dtoStatus.name());
    }
}
