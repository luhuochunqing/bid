# 架构决策记录

> 记录项目中重要的架构/设计决策，包括选型、取舍和拍板的方案。按 session 追加条目。

---

## 1. GAP 附件加载统一通过 DocumentService.getDocuments() 入口

**日期**: 2026-06-20
**决策者**: trae
**相关 Issue**: CO-262

### 背景

CO-262 修复 CRM 商机关联回填的 GAP 附件未持久化问题时，最初存在两套 GAP 附件加载代码路径：

1. `TenderEvaluationService.toDTO()` 和 `TenderEvaluationReviewService.toDTO()` 调用 `documentService.getDocuments(tenderId)`
2. `TenderEvaluationSubmissionService.loadOrInitDraft()` 调用 `gapFilesSync.loadGapFiles(tenderId)`

两者内部都是调用同一个 `projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(ENTITY_TYPE_EVALUATION_GAP, tenderId)`，完全相同。

### 问题

- **重复代码**：两套路径做完全相同的事
- **维护风险**：未来查询逻辑变更（如加缓存、改排序）容易漏改其中一个
- **职责不清**：`GapFilesSync` 既负责"写"（applyGapFiles）又负责"读"（loadGapFiles），但"读"已经有 `DocumentService.getDocuments()` 负责

### 决策

统一用 `TenderEvaluationDocumentService.getDocuments()` 作为 GAP 附件加载的唯一入口：

- `TenderEvaluationService` / `TenderEvaluationReviewService` / `TenderEvaluationSubmissionService` 三个 Service 都注入 `TenderEvaluationDocumentService`，调用 `getDocuments(tenderId)`
- 删除 `TenderEvaluationGapFilesSync.loadGapFiles()` 方法
- `TenderEvaluationGapFilesSync` 只保留"写"职责（`applyGapFiles`）

### 取舍

| 方案 | 优点 | 缺点 | 是否采纳 |
|------|------|------|---------|
| 统一用 `DocumentService.getDocuments()` | 单一入口，职责清晰 | `GapFilesSync` 丧失"读"能力 | ✅ 采纳 |
| 统一用 `GapFilesSync.loadGapFiles()` | 读写都在一个类 | 需要将 `GapFilesSync` 改为 Spring Bean，调整可见性 | ❌ 改动更大 |
| 保持两套路径 | 无需改动 | 重复代码，维护风险 | ❌ 不解决技术债 |

### 验证

- 三个 Service 的 `toDTO()` / `loadOrInitDraft()` 都调用 `documentService.getDocuments()`
- `TenderEvaluationGapFilesSync` 只剩 `applyGapFiles` 一个 public 方法
- 80 个后端测试全绿，33 个架构测试全绿

### 相关文档

- `docs/lessons/root-cause-analysis-co262-crm-eval-gap-files.md` — 完整根因分析
- `docs/lessons/crm-integration-lessons.md` §9 — CRM 集成经验

---

## 2. 阶段变更通知必须携带明确 actor，旧签名使用系统 actor 兜底

> 决策日期：2026-06-21
> 决策者：zcode
> 状态：已采纳

### 背景

`POST /api/projects/{id}/drafting/submit-bid` 在阶段成功切到 `EVALUATING` 后，发送阶段变更通知时触发数据库错误：`Column 'created_by' cannot be null`。根因是 `ProjectNotificationService.notifyStageTransition(projectId, fromStage, toStage)` 的旧三参签名没有 actor 参数，通知创建最终把 null 写入 `notification.created_by`。

### 决策

新增 actor-aware 的四参 `notifyStageTransition(projectId, fromStage, toStage, userId)`；`submitBid` 调用四参方法并传入 `currentUserId`。保留旧三参方法以兼容既有调用，但旧签名统一委托到 `SYSTEM_USER_ID = 0L`，禁止再向通知创建链路传 null actor。

### 备选方案（及否决理由）

| 方案 | 优点 | 缺点 | 是否采纳 |
|------|------|------|---------|
| 四参方法传真实 actor，旧三参用系统 actor 兜底 | 最小改动；保留兼容；submitBid 审计主体准确 | `0L` 仍是约定值，不一定有真实用户记录 | ✅ |
| 全量修改所有调用方，删除三参签名 | 语义最清晰，编译期强制 actor | 改动范围大，超出本次 500 修复范围 | ❌ 本次只做直接相关最小修复 |
| 放宽 `notification.created_by` 数据库约束 | 可避免 null 插入失败 | 破坏审计完整性，掩盖调用方问题 | ❌ 不符合审计字段非空语义 |
| 在 `sendNotification` catch 后吞掉异常 | 表面避免接口 500 | JPA/事务可能已被污染，且 null createdBy 仍未解决 | ❌ 治标不治本 |

### 权衡与约束

- `submitBid` 这类用户触发动作必须传真实 `currentUserId`，保证通知审计可追溯。
- 旧三参方法只作为兼容入口；新代码应优先使用四参签名。
- `SYSTEM_USER_ID = 0L` 是最小兼容方案。如果未来外键或审计要求 `created_by` 必须对应真实用户，应引入正式系统用户账号或调整通知创建人模型。

### 影响范围

- `backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java`
- `backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java`
- `backend/src/test/java/com/xiyu/bid/project/notification/ProjectNotificationServiceTest.java`
- `backend/src/test/java/com/xiyu/bid/project/service/ProjectDraftingServiceTest.java`

### 相关文档

- `docs/lessons/root-cause-analysis-stage-notification-created-by.md` — 完整根因分析
- `docs/lessons/lessons-learned.md` §10 — 同一接口错误形态变化时的日志排查教训
