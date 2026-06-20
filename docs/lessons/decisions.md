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
