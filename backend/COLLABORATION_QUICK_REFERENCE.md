# Collaboration Module - Quick Reference

## 快速参考指南

## API端点一览

### 讨论线程 API
```bash
# 获取项目的所有讨论
GET /api/collaboration/threads?projectId=100

# 获取特定讨论
GET /api/collaboration/threads/1

# 创建新讨论（仅管理员和经理）
POST /api/collaboration/threads
{
  "projectId": 100,
  "title": "讨论标题",
  "createdBy": 10
}

# 更新讨论状态（仅管理员和经理）
PUT /api/collaboration/threads/1/status?status=IN_PROGRESS
# 状态: OPEN, IN_PROGRESS, RESOLVED, CLOSED
```

### 评论 API
```bash
# 添加评论到讨论
POST /api/collaboration/threads/1/comments
{
  "threadId": 1,
  "userId": 10,
  "content": "评论内容",
  "mentions": "[11, 12]",
  "parentId": null  // 填写父评论ID实现嵌套回复
}

# 更新评论
PUT /api/collaboration/comments/1
{
  "content": "更新后的评论内容"
}

# 删除评论（软删除）
DELETE /api/collaboration/comments/1

# 获取讨论的所有评论
GET /api/collaboration/threads/1/comments  # 通过service调用
```

### 提及 API
```bash
# 获取用户的所有提及
GET /api/collaboration/mentions?userId=10
```

## 服务层方法

### CollaborationService

```java
// === 讨论线程管理 ===
CollaborationThreadDTO createThread(ThreadCreateRequest request);
List<CollaborationThreadDTO> getThreadsByProject(Long projectId);
CollaborationThreadDTO getThreadById(Long threadId);
CollaborationThreadDTO updateThreadStatus(Long threadId, ThreadStatus status);

// === 评论管理 ===
CommentDTO addComment(Long threadId, CommentCreateRequest request);
CommentDTO updateComment(Long commentId, CommentUpdateRequest request);
void deleteComment(Long commentId);  // 软删除
List<CommentDTO> getCommentsByThread(Long threadId);

// === 提及查询 ===
List<CommentDTO> getMentionsForUser(Long userId);
```

## 数据模型

### CollaborationThread（讨论线程）
```java
@Entity
@Table(name = "collaboration_threads")
public class CollaborationThread {
    private Long id;
    private Long projectId;           // 关联项目
    private String title;             // 讨论标题
    private ThreadStatus status;      // 状态: OPEN, IN_PROGRESS, RESOLVED, CLOSED
    private Long createdBy;           // 创建人
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Comment（评论）
```java
@Entity
@Table(name = "comments")
public class Comment {
    private Long id;
    private Long threadId;            // 所属讨论
    private Long userId;              // 评论作者
    private String content;           // 评论内容（TEXT类型）
    private String mentions;          // 提及的用户ID列表（JSON数组）
    private Long parentId;            // 父评论ID（支持嵌套）
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;        // 软删除标记
}
```

## DTO类

### 请求DTO
```java
// ThreadCreateRequest
{
    Long projectId;      // @NotNull
    String title;        // @NotBlank
    Long createdBy;
}

// CommentCreateRequest
{
    Long threadId;       // @NotNull
    Long userId;         // @NotNull
    String content;      // @NotBlank
    String mentions;     // JSON数组，如"[1,2,3]"
    Long parentId;       // 父评论ID
}

// CommentUpdateRequest
{
    String content;      // @NotBlank
}
```

### 响应DTO
```java
// CollaborationThreadDTO
{
    Long id;
    Long projectId;
    String title;
    ThreadStatus status;
    Long createdBy;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

// CommentDTO
{
    Long id;
    Long threadId;
    Long userId;
    String content;
    String mentions;
    Long parentId;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Boolean isDeleted;
}
```

## Repository查询方法

### CollaborationThreadRepository
```java
List<CollaborationThread> findByProjectId(Long projectId);
```

### CommentRepository
```java
// 获取线程的所有未删除评论（按创建时间升序）
List<Comment> findByThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(Long threadId);

// 获取提及特定用户的未删除评论
List<Comment> findByMentionsContainingAndIsDeletedFalse(String mentions);
```

## 权限矩阵

| 操作 | ADMIN | MANAGER | STAFF |
|------|:-----:|:-------:|:-----:|
| 创建讨论 | ✅ | ✅ | ❌ |
| 查看讨论 | ✅ | ✅ | ✅ |
| 更新状态 | ✅ | ✅ | ❌ |
| 添加评论 | ✅ | ✅ | ✅ |
| 更新评论 | ✅ | ✅ | ✅ |
| 删除评论 | ✅ | ✅ | ✅ |
| 查看提及 | ✅ | ✅ | ✅ |

## 状态流转图

```
    ┌─────────┐
    │  OPEN   │  初始状态
    └────┬────┘
         │
         v
    ┌─────────────┐
    │ IN_PROGRESS │  进行中
    └──────┬──────┘
           │
           v
    ┌─────────────┐
    │  RESOLVED   │  已解决
    └──────┬──────┘
           │
           v
    ┌─────────────┐
    │   CLOSED    │  已关闭
    └─────────────┘
```

注意：状态可以双向转换（例如从RESOLVED回到IN_PROGRESS）

## 常见使用场景

### 场景1：创建项目讨论
```java
// 1. 创建讨论线程
ThreadCreateRequest request = ThreadCreateRequest.builder()
    .projectId(100L)
    .title("关于技术方案的讨论")
    .createdBy(10L)
    .build();
CollaborationThreadDTO thread = collaborationService.createThread(request);

// 2. 添加初始评论
CommentCreateRequest commentReq = CommentCreateRequest.builder()
    .threadId(thread.getId())
    .userId(10L)
    .content("大家对技术方案有什么建议？")
    .build();
collaborationService.addComment(thread.getId(), commentReq);
```

### 场景2：嵌套回复
```java
// 父评论
CommentDTO parentComment = collaborationService.addComment(threadId,
    CommentCreateRequest.builder()
        .threadId(threadId)
        .userId(10L)
        .content("建议采用Spring Boot")
        .build()
);

// 子评论（回复）
CommentDTO childComment = collaborationService.addComment(threadId,
    CommentCreateRequest.builder()
        .threadId(threadId)
        .userId(11L)
        .content("同意，Spring Boot确实是个好选择")
        .parentId(parentComment.getId())  // 指定父评论
        .build()
);
```

### 场景3：使用提及功能
```java
CommentDTO comment = collaborationService.addComment(threadId,
    CommentCreateRequest.builder()
        .threadId(threadId)
        .userId(10L)
        .content("@张经理 @李工 请审核方案")
        .mentions("[11, 12]")  // 张经理ID=11, 李工ID=12
        .build()
);

// 查询张经理的所有提及
List<CommentDTO> mentionsForZhang = collaborationService.getMentionsForUser(11L);
```

### 场景4：更新讨论状态
```java
// 初始状态：OPEN
CollaborationThreadDTO thread = collaborationService.createThread(...);

// 开始讨论
thread = collaborationService.updateThreadStatus(thread.getId(),
    CollaborationThread.ThreadStatus.IN_PROGRESS);

// 达成一致
thread = collaborationService.updateThreadStatus(thread.getId(),
    CollaborationThread.ThreadStatus.RESOLVED);

// 关闭讨论
thread = collaborationService.updateThreadStatus(thread.getId(),
    CollaborationThread.ThreadStatus.CLOSED);
```

### 场景5：软删除评论
```java
// 删除评论（不实际删除数据）
collaborationService.deleteComment(commentId);

// 查询评论时自动过滤已删除的
List<CommentDTO> comments = collaborationService.getCommentsByThread(threadId);
// 返回的列表中不包含isDeleted=true的评论
```

## 错误处理

### 常见异常
```java
// 资源不存在
throw new ResourceNotFoundException("Thread not found with id: " + threadId);

// 非法参数
throw new IllegalArgumentException("Project ID is required");
throw new IllegalArgumentException("Title cannot be empty");
throw new IllegalArgumentException("Content cannot be empty");

// 状态冲突
throw new IllegalStateException("Cannot update deleted comment");
throw new IllegalStateException("Comment already deleted");
```

## 安全特性

### XSS防护
```java
// 自动移除HTML标签
InputSanitizer.stripHtml(userInput);  // <script>alert('xss')</script> → ""
```

### 审计日志
```java
@Auditable(action = "CREATE", entityType = "CollaborationThread", description = "Create new collaboration thread")
public CollaborationThreadDTO createThread(ThreadCreateRequest request) {
    // 自动记录审计日志
}
```

## 数据库表结构

### collaboration_threads
```sql
CREATE TABLE collaboration_threads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_thread_project (project_id),
    INDEX idx_thread_status (status),
    INDEX idx_thread_project_status (project_id, status)
);
```

### comments
```sql
CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    thread_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    mentions VARCHAR(255),
    parent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    INDEX idx_comment_thread (thread_id),
    INDEX idx_comment_user (user_id),
    INDEX idx_comment_parent (parent_id),
    INDEX idx_comment_deleted (is_deleted),
    INDEX idx_comment_thread_deleted (thread_id, is_deleted)
);
```

## 测试覆盖

- ✅ 实体单元测试（CommentTest, CollaborationThreadTest）
- ✅ 服务层单元测试（CollaborationServiceTest）
- ✅ 控制器集成测试（CollaborationControllerIntegrationTest）
- ✅ 边界条件测试
- ✅ 异常处理测试
- ✅ 权限验证测试

## 文件位置

```
src/main/java/com/xiyu/bid/collaboration/
├── entity/
│   ├── CollaborationThread.java
│   └── Comment.java
├── dto/
│   ├── CollaborationThreadDTO.java
│   ├── CommentDTO.java
│   ├── ThreadCreateRequest.java
│   ├── CommentCreateRequest.java
│   └── CommentUpdateRequest.java
├── repository/
│   ├── CollaborationThreadRepository.java
│   └── CommentRepository.java
├── service/
│   └── CollaborationService.java
├── controller/
│   └── CollaborationController.java
└── README.md

src/test/java/com/xiyu/bid/collaboration/
├── entity/
│   ├── CommentTest.java
│   └── CollaborationThreadTest.java
├── CollaborationServiceTest.java
└── integration/
    └── CollaborationControllerIntegrationTest.java
```

## 版本信息

- **实现日期**: 2026-03-04
- **开发方法**: TDD（测试驱动开发）
- **测试覆盖率**: 80%+
- **Spring Boot版本**: 3.x
- **Java版本**: 21

## 相关文档

- [COLLABORATION_MODULE_SUMMARY.md](./COLLABORATION_MODULE_SUMMARY.md) - 详细实现总结
- [README.md](./src/main/java/com/xiyu/bid/collaboration/README.md) - 模块说明文档
