# CRM 商机关联回填 GAP 附件未持久化 根因分析

> Issue: CO-262
> 日期: 2026-06-20
> 排查者: trae
> 修复 PR: `!897` (commit `49895bf3d`)

---

## 现场还原

**症状素描**：CRM 商机关联回填标讯评估表后，投标评估表详情页加载时"GAP 附件"列表为空。前端控制台无报错，网络请求返回 200，但 `evaluationBasic.projectPlanGapFiles` 字段为 null。

**边界划定**：
- CRM 回填的其他字段（客户信息、推荐意见）显示正常 ✅
- 手动上传的 GAP 附件显示正常 ✅
- 仅 CRM 回填的 GAP 附件在详情页加载时丢失 ❌

---

## 剥洋葱：逆向调用链

### Layer 1 — 前端覆盖后端数据

`useEvaluationReview.js` 的 `loadCurrentEvaluation()` 在加载评估表后，又调用了 `getEvaluationDocuments(id)` 获取附件列表，并覆盖后端返回的 `projectPlanGapFiles`：

```javascript
// 修复前：src/views/Bidding/detail/useEvaluationReview.js
const docsResult = await tendersApi.getEvaluationDocuments(id)
if (docsResult?.success !== false) {
  const docs = docsResult?.data || []
  if (!evalData.evaluationBasic) evalData.evaluationBasic = {}
  evalData.evaluationBasic.projectPlanGapFiles = docs  // ← 覆盖后端返回的 GapFileRef 列表
}
```

**问题**：`getEvaluationDocuments` 返回的是 `ProjectDocument` 实体数组（含 `id`/`name`/`fileUrl` 等字段），而后端 `projectPlanGapFiles` 返回的是 `GapFileRef` record（含 `fileName`/`fileUrl` 字段）。数据结构不一致导致前端组件无法正确渲染。

### Layer 2 — 后端 toDTO 调用旧版 mapper

`TenderEvaluationService.toDTO()` 调用的是 4 参数版本 `mapper.toDTO(evaluation, tender, canFill, canDecide)`，该版本不加载 GAP 附件，`projectPlanGapFiles` 始终为 null：

```java
// 修复前：backend/src/main/java/com/xiyu/bid/tender/service/TenderEvaluationService.java
private TenderEvaluationDTO toDTO(TenderEvaluation evaluation, Tender tender, boolean canFill, boolean canDecide) {
    return mapper.toDTO(evaluation, tender, canFill, canDecide);  // ← 4 参数，gapFiles=null
}
```

同样问题也存在于 `TenderEvaluationReviewService.toDTO()`。

### Layer 3 — 持久化层缺失

CRM 商机关联回填时，`TenderEvaluationSubmissionService.saveDraft()` / `submit()` 没有将 `projectPlanGapFiles` 持久化到 `project_documents` 表。请求中的 GAP 附件引用只在前端透传，未落库。

### Layer 4 — applyGapFiles 语义不严谨

`TenderEvaluationGapFilesSync.applyGapFiles()` 对 null 和 empty 不区分，null 会误删已有附件：

```java
// 修复前：basic == null || projectPlanGapFiles == null || isEmpty() 都走删除逻辑
if (basic == null || basic.projectPlanGapFiles() == null || basic.projectPlanGapFiles().isEmpty()) {
    // 删除已有记录 ← null 时不应删除
}
```

---

## 零号病人

**零号病人**：`TenderEvaluationSubmissionService` 缺少 GAP 附件持久化逻辑 + `TenderEvaluationService.toDTO()` 调用旧版 4 参数 mapper。

根因是 CO-262 修复时只在前端透传了 `projectPlanGapFiles`，但后端没有对应的持久化和回填逻辑。

---

## 修复方案

### 后端

1. **新增 `TenderEvaluationGapFilesSync`**：GAP 附件同步器，`applyGapFiles` 区分三种语义：
   - `basic == null` 或 `projectPlanGapFiles == null` → 保留已有附件（不删除、不新增）
   - `projectPlanGapFiles` 为空列表 → 明确清空，删除已有附件
   - `projectPlanGapFiles` 非空 → 替换：先删除已有后重建

2. **`TenderEvaluationService` / `TenderEvaluationReviewService`**：注入 `TenderEvaluationDocumentService`，`toDTO()` 加载 GAP 附件并调用 5 参数 `mapper.toDTO`

3. **`TenderEvaluationSubmissionService`**：集成 `GapFilesSync`，`loadOrInitDraft`/`saveDraft`/`submit` 统一通过 `DocumentService.getDocuments()` 加载附件

4. **`EvaluationBasicDTO`**：新增 `projectPlanGapFiles` 字段和 `GapFileRef` record

5. **`TenderEvaluationSubmissionMapper`**：新增 5 参数 `toDTO` 重载，支持 GAP 附件回填

### 前端

1. **`useEvaluationReview.js`**：删除冗余 `getEvaluationDocuments` 调用，统一由后端返回 `projectPlanGapFiles`
2. **`DetailPage.vue` / `useTenderEvaluationForm.js`**：透传 `projectPlanGapFiles`
3. **`ProjectPlanGapUpload.vue`**：只读模式附件改为可点击 `el-link` 下载，`resolveFileUrl` 过滤 `javascript:` 等危险协议防 XSS

---

## 防复发测试

| 测试 | 覆盖场景 |
|------|---------|
| `reviewTender_returnsDtoWithGapFiles` | 审核路径返回的 DTO 必须填充 GAP 附件 |
| `getEvaluation_returnsDtoWithGapFiles` | 详情路径返回的 DTO 必须填充 GAP 附件 |
| `saveDraft_basicNull_preservesExistingGapFiles` | basic 为 null 时不删除已有附件 |
| `saveDraft_gapFilesNull_preservesExistingGapFiles` | projectPlanGapFiles 为 null 时不删除已有附件 |
| `saveDraft_gapFilesEmptyList_clearsExisting` | projectPlanGapFiles 为空列表时清空已有附件 |

---

## 教训

1. **新增字段时必须打通"持久化 → 加载 → 回填"全链路**。只在前端透传而不落库，会导致详情页加载时数据丢失。
2. **DTO 转换方法新增参数后，必须更新所有调用方**。旧版重载容易成为隐形陷阱，导致新字段始终为 null。
3. **null 和 empty 语义必须区分**。null 表示"未提供，保留原状"；empty 表示"明确清空"。混用会导致误删数据。
4. **前端不应覆盖后端返回的结构化数据**。后端已返回 `GapFileRef` 列表，前端又用 `ProjectDocument` 实体覆盖，数据结构不一致导致渲染失败。

---

## 相关文档

- `docs/lessons/crm-integration-lessons.md` — CRM 集成经验（含本次 GAP 附件处理）
- `docs/lessons/decisions.md` — GAP 附件加载统一入口决策
