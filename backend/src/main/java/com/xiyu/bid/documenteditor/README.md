# DocumentEditor 模块 (文档编辑器模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
文档编辑器模块负责文档结构、章节、锁定、分配和提醒等编辑流程。这里是投标文档结构化编辑的边界，重点管理树形结构和编辑约束。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/DocumentEditorController.java` | Controller | 文档编辑接口 |
| `service/DocumentEditorService.java` | Service | 文档编辑 facade，维持原有 API |
| `service/DocumentStructureService.java` | Service | 文档结构创建与查询 |
| `service/DocumentSectionCommandService.java` | Service | 章节新增、更新、删除、重排 |
| `service/DocumentSectionCollaborationService.java` | Service | 分配、锁定、提醒等协作动作 |
| `service/DocumentSectionTreeService.java` | Service | 章节树查询与富化组装 |
| `service/DocumentDraftTreeImportService.java` | Service | 草稿树批量导入与章节上修 |
| `service/DocumentEditorGuard.java` | Guard | 项目/结构/章节归属校验 |
| `service/DocumentEditorMapper.java` | Mapper | 结构、章节、提醒 DTO 组装 |
| `entity/DocumentStructure.java` | Entity | 文档结构实体 |
| `entity/DocumentSection.java` | Entity | 文档章节实体 |
| `entity/DocumentReminder.java` | Entity | 文档提醒实体 |
| `entity/DocumentSectionAssignment.java` | Entity | 章节分配实体 |
| `entity/DocumentSectionLock.java` | Entity | 章节锁定实体 |
| `repository/DocumentStructureRepository.java` | Repository | 文档结构数据访问 |
| `repository/DocumentSectionRepository.java` | Repository | 章节数据访问 |
| `repository/DocumentReminderRepository.java` | Repository | 提醒数据访问 |
| `repository/DocumentSectionAssignmentRepository.java` | Repository | 章节分配数据访问 |
| `repository/DocumentSectionLockRepository.java` | Repository | 章节锁定数据访问 |
| `dto/DocumentStructureDTO.java` | DTO | 文档结构视图对象 |
| `dto/DocumentSectionDTO.java` | DTO | 章节视图对象 |
| `dto/StructureCreateRequest.java` | DTO | 创建结构请求 |
| `dto/SectionCreateRequest.java` | DTO | 创建章节请求 |
| `dto/SectionUpdateRequest.java` | DTO | 更新章节请求 |
| `dto/SectionReorderRequest.java` | DTO | 章节排序请求 |
| `dto/SectionAssignmentRequest.java` | DTO | 章节分配请求 |
| `dto/SectionLockRequest.java` | DTO | 章节锁定请求 |
| `dto/SectionReminderRequest.java` | DTO | 章节提醒请求 |
| `dto/DraftTreeUpsertRequest.java` | DTO | 草稿树导入请求 |
| `dto/DraftTreeUpsertNodeRequest.java` | DTO | 草稿树节点请求 |
| `dto/DraftTreeUpsertResultDTO.java` | DTO | 草稿树导入结果 |
| `dto/DraftTreeSkippedSectionDTO.java` | DTO | 草稿树跳过项 |
| `dto/DocumentReminderDTO.java` | DTO | 提醒视图对象 |
| `dto/SectionType.java` | Enum | 章节类型 |
