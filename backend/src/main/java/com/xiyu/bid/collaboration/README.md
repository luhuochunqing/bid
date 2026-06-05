# Collaboration 模块 (协作记录模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
协作模块负责讨论线程、评论、提及和协作状态流转，支撑项目团队的实时协作决策。这里是项目内多人协作的轻量领域边界。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/CollaborationController.java` | Controller | 协作接口 |
| `service/CollaborationService.java` | Service | 协作用例编排 |
| `entity/CollaborationThread.java` | Entity | 讨论线程实体 |
| `entity/Comment.java` | Entity | 评论实体 |
| `repository/CollaborationThreadRepository.java` | Repository | 线程数据访问 |
| `repository/CommentRepository.java` | Repository | 评论数据访问 |
| `dto/CollaborationThreadDTO.java` | DTO | 线程视图对象 |
| `dto/CommentDTO.java` | DTO | 评论视图对象 |
| `dto/ThreadCreateRequest.java` | DTO | 创建线程请求 |
| `dto/CommentCreateRequest.java` | DTO | 创建评论请求 |
| `dto/CommentUpdateRequest.java` | DTO | 更新评论请求 |
| `dto/ThreadStatus.java` | Enum | 线程状态 |
