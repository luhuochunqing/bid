package com.xiyu.bid.collaboration;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.collaboration.dto.CommentCreateRequest;
import com.xiyu.bid.collaboration.dto.CommentUpdateRequest;
import com.xiyu.bid.collaboration.dto.ThreadCreateRequest;
import com.xiyu.bid.collaboration.entity.Comment;
import com.xiyu.bid.collaboration.entity.CollaborationThread;
import com.xiyu.bid.collaboration.repository.CommentRepository;
import com.xiyu.bid.collaboration.repository.CollaborationThreadRepository;
import com.xiyu.bid.collaboration.service.CollaborationService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
abstract class AbstractCollaborationServiceTest {

    @Mock
    protected CollaborationThreadRepository threadRepository;

    @Mock
    protected CommentRepository commentRepository;

    @Mock
    protected IAuditLogService auditLogService;

    protected CollaborationService collaborationService;
    protected CollaborationThread testThread;
    protected Comment testComment;
    protected ThreadCreateRequest threadCreateRequest;
    protected CommentCreateRequest commentCreateRequest;
    protected CommentUpdateRequest commentUpdateRequest;

    @BeforeEach
    void setUp() {
        collaborationService = new CollaborationService(
                threadRepository,
                commentRepository,
                auditLogService
        );

        testThread = thread(
                1L,
                100L,
                "Discussion about bid strategy",
                CollaborationThread.ThreadStatus.OPEN
        );
        testComment = activeComment(1L, 1L, 10L, "This is a test comment");
        threadCreateRequest = ThreadCreateRequest.builder()
                .projectId(100L)
                .title("Discussion about bid strategy")
                .createdBy(10L)
                .build();
        commentCreateRequest = CommentCreateRequest.builder()
                .threadId(1L)
                .userId(10L)
                .content("This is a test comment")
                .mentions(null)
                .parentId(null)
                .build();
        commentUpdateRequest = CommentUpdateRequest.builder()
                .content("Updated comment content")
                .build();
    }

    protected CollaborationThread thread(
            Long id,
            Long projectId,
            String title,
            CollaborationThread.ThreadStatus status
    ) {
        LocalDateTime now = LocalDateTime.now();
        return CollaborationThread.builder()
                .id(id)
                .projectId(projectId)
                .title(title)
                .status(status)
                .createdBy(10L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    protected Comment activeComment(Long id, Long threadId, Long userId, String content) {
        LocalDateTime now = LocalDateTime.now();
        return Comment.builder()
                .id(id)
                .threadId(threadId)
                .userId(userId)
                .content(content)
                .mentions(null)
                .parentId(null)
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(false)
                .build();
    }
}
