# 8模块实现计划

> 创建时间: 2026-03-04
> 项目: 西域智慧供应链投标管理平台

---

## 模块清单

| # | 模块 | 优先级 | 复杂度 | 阶段 |
|---|------|-------|--------|------|
| 1 | 日历模块 (Calendar) | P2 | Low | Phase 1 |
| 2 | 协作记录 (Collaboration) | P2 | Low-Medium | Phase 1 |
| 3 | 竞争情报 (CompetitionIntel) | P2 | Medium | Phase 2 |
| 4 | 评分分析 (ScoreAnalysis) | P2 | Medium-High | Phase 2 |
| 5 | ROI分析 (ROIAnalysis) | P2 | Medium-High | Phase 2 |
| 6 | 版本历史 (VersionHistory) | P2 | Medium-High | Phase 3 |
| 7 | 文档编辑器 (DocumentEditor) | P3 | High | Phase 3 |
| 8 | 文档组装 (DocumentAssembly) | P3 | High | Phase 3 |

---

## Phase 1: 基础模块 (独立，低复杂度)

### 模块1: 日历模块 (Calendar)

**文件清单:**
- `calendar/entity/CalendarEvent.java`
- `calendar/repository/CalendarEventRepository.java`
- `calendar/dto/CalendarEventDTO.java`
- `calendar/dto/CalendarEventCreateRequest.java`
- `calendar/dto/CalendarEventUpdateRequest.java`
- `calendar/service/CalendarService.java`
- `calendar/controller/CalendarController.java`
- 测试文件

**API端点:**
- `GET /api/calendar` - 按日期范围查询
- `GET /api/calendar/month/{year}/{month}` - 月视图
- `GET /api/calendar/project/{projectId}` - 按项目查询
- `GET /api/calendar/urgent` - 紧急事件
- `POST /api/calendar` - 创建事件
- `PUT /api/calendar/{id}` - 更新事件
- `DELETE /api/calendar/{id}` - 删除事件

### 模块2: 协作记录 (Collaboration)

**文件清单:**
- `collaboration/entity/Comment.java`
- `collaboration/entity/CollaborationThread.java`
- `collaboration/repository/CommentRepository.java`
- `collaboration/repository/CollaborationThreadRepository.java`
- `collaboration/dto/*`
- `collaboration/service/CollaborationService.java`
- `collaboration/controller/CollaborationController.java`
- 测试文件

**API端点:**
- `GET /api/collaboration/threads` - 讨论线程列表
- `GET /api/collaboration/threads/{id}` - 获取线程和评论
- `POST /api/collaboration/threads` - 创建线程
- `POST /api/collaboration/threads/{id}/comments` - 添加评论
- `PUT /api/collaboration/comments/{id}` - 更新评论
- `DELETE /api/collaboration/comments/{id}` - 删除评论
- `GET /api/collaboration/mentions` - 获取@提及

---

## Phase 2: AI分析模块 (中等复杂度)

### 模块3: 竞争情报 (CompetitionIntel)

**文件清单:**
- `ai/entity/Competitor.java`
- `ai/entity/CompetitionAnalysis.java`
- `ai/repository/CompetitorRepository.java`
- `ai/repository/CompetitionAnalysisRepository.java`
- `ai/dto/*`
- `ai/service/CompetitionIntelService.java`
- `ai/controller/CompetitionIntelController.java`
- 测试文件

**API端点:**
- `GET /api/ai/competition/competitors` - 竞争对手列表
- `POST /api/ai/competition/competitors` - 创建竞争对手
- `GET /api/ai/competition/project/{projectId}` - 获取分析
- `POST /api/ai/competition/project/{projectId}/analyze` - 触发分析
- `GET /api/ai/competition/competitor/{id}/history` - 历史业绩

### 模块4: 评分分析 (ScoreAnalysis)

**文件清单:**
- `ai/entity/ScoreAnalysis.java`
- `ai/entity/DimensionScore.java`
- `ai/repository/ScoreAnalysisRepository.java`
- `ai/repository/DimensionScoreRepository.java`
- `ai/dto/*`
- `ai/service/ScoreAnalysisService.java`
- `ai/controller/ScoreAnalysisController.java`
- 测试文件

**API端点:**
- `GET /api/ai/score-analysis/project/{projectId}` - 获取分析
- `GET /api/ai/score-analysis/project/{projectId}/history` - 历史数据
- `POST /api/ai/score-analysis` - 创建分析
- `POST /api/ai/score-analysis/project/{projectId}/auto` - AI自动分析
- `GET /api/ai/score-analysis/compare/{id1}/{id2}` - 项目对比

### 模块5: ROI分析 (ROIAnalysis)

**文件清单:**
- `ai/entity/ROIAnalysis.java`
- `ai/repository/ROIAnalysisRepository.java`
- `ai/dto/*`
- `ai/service/ROIAnalysisService.java`
- `ai/controller/ROIAnalysisController.java`
- 测试文件

**API端点:**
- `GET /api/ai/roi/project/{projectId}` - 获取分析
- `POST /api/ai/roi` - 创建分析
- `POST /api/ai/roi/project/{projectId}/calculate` - 自动计算
- `POST /api/ai/roi/sensitivity` - 敏感性分析

---

## Phase 3: 文档模块 (高复杂度)

### 模块6: 版本历史 (VersionHistory)

**文件清单:**
- `documents/entity/DocumentVersion.java`
- `documents/repository/DocumentVersionRepository.java`
- `documents/dto/*`
- `documents/service/VersionHistoryService.java`
- `documents/controller/DocumentVersionController.java`
- 测试文件

**API端点:**
- `GET /api/documents/{projectId}/versions` - 版本列表
- `GET /api/documents/{projectId}/versions/{versionId}` - 获取版本
- `POST /api/documents/{projectId}/versions` - 创建版本
- `GET /api/documents/{projectId}/versions/{v1}/compare/{v2}` - 版本对比
- `POST /api/documents/{projectId}/versions/{versionId}/rollback` - 版本回滚

### 模块7: 文档编辑器 (DocumentEditor)

**文件清单:**
- `documents/entity/DocumentSection.java`
- `documents/entity/DocumentStructure.java`
- `documents/repository/*`
- `documents/dto/*`
- `documents/service/DocumentEditorService.java`
- `documents/controller/DocumentEditorController.java`
- 测试文件

**API端点:**
- `GET /api/documents/{projectId}/editor/structure` - 获取结构
- `POST /api/documents/{projectId}/editor/structure` - 创建结构
- `POST /api/documents/{projectId}/editor/sections` - 添加章节
- `PUT /api/documents/{projectId}/editor/sections/{id}` - 更新章节
- `DELETE /api/documents/{projectId}/editor/sections/{id}` - 删除章节
- `PUT /api/documents/{projectId}/editor/sections/reorder` - 重新排序

### 模块8: 文档组装 (DocumentAssembly)

**文件清单:**
- `documents/entity/AssemblyTemplate.java`
- `documents/entity/DocumentAssembly.java`
- `documents/repository/*`
- `documents/dto/*`
- `documents/service/DocumentAssemblyService.java`
- `documents/controller/DocumentAssemblyController.java`
- 测试文件

**API端点:**
- `GET /api/documents/assembly/templates` - 模板列表
- `POST /api/documents/assembly/templates` - 创建模板
- `GET /api/documents/{projectId}/assembly` - 获取组装结果
- `POST /api/documents/{projectId}/assembly` - 从模板组装
- `PUT /api/documents/assembly/{id}/regenerate` - 重新生成

---

## 技术规范

### Entity 规范
- 使用 `@Builder`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- `@PrePersist` 和 `@PreUpdate` 自动设置时间戳
- 合理的数据库索引

### Service 规范
- `@Transactional` 事务管理
- `@Auditable` 关键操作审计
- `IAuditLogService` 接口用于测试

### Controller 规范
- 返回 `ApiResponse<T>`
- `@PreAuthorize` 角色控制
- 统一异常处理

### 测试规范
- JUnit 5 + Mockito
- 80%+ 覆盖率
- 使用 `IAuditLogService` 避免 @Slf4j mock 问题

---

## 预计工作量

- 总文件数: ~96个
- 预计时间: 15-20 开发日
- 并行团队: 2-3 人

---

*计划版本: 1.0*
*创建时间: 2026-03-04*
