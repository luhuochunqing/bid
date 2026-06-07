# Task 模块 (任务协同模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
任务模块负责项目任务的查询、流转与协同，是项目执行过程中的任务拆解和进度跟踪后端入口。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/TaskController.java` | Controller | 任务接口，包含评论和动态读取入口 |
| `service/TaskService.java` | Service | 任务业务编排，更新任务时记录历史快照 |
| `service/TaskActivityService.java` | Service | 任务评论与动态聚合编排，复用 mention/inbox 通知 |
| `service/TaskHistoryRecorder.java` | Component | 任务更新历史快照记录与 TASK 更新事件发布 |
| `service/TaskDtoMapper.java` | Mapper | Task 实体与 TaskDTO、扩展字段 JSON 转换 |
| `service/TaskSnapshots.java` | Helper | 更新前后 Task 脱离 JPA 可变对象的快照复制 |
| `service/TaskDeliverableService.java` | Service | 任务交付物元数据创建、删除和覆盖度查询；交付物 URL 来自真实项目文档上传结果 |
| `dto/TaskDTO.java` | DTO | 任务视图对象 |
| `dto/TaskActivityDTO.java` | DTO | 评论/历史统一动态视图 |
| `dto/TaskCommentCreateRequest.java` | DTO | 任务评论创建请求 |
| `dto/TaskDeliverableCreateRequest.java` | DTO | 任务交付物创建请求，包含名称、类型、文件信息和真实附件 URL |
| `entity/TaskComment.java` | Entity | `task_comment` 评论表映射 |
| `entity/TaskHistory.java` | Entity | `task_history` 历史快照与归档标记表映射 |
