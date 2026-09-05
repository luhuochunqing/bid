---
title: 案例详情"查看源项目 / 标书原文"操作契约与导入数据陷阱
space: engineering
category: guide
tags: [案例库, knowledge_case, 源项目, 标书原文, blob下载, 枚举混用]
created: 2026-08-31
updated: 2026-09-03
health_checked: 2026-09-05
sources:
  - src/views/Knowledge/views/useBidCaseSourceActions.js
  - src/views/Knowledge/views/CaseGrid.vue
  - src/views/Project/stages/components/AiRecommendDrawer.vue
  - src/utils/download.js
  - scripts/slice_all_v9.py
  - scripts/import-case-slices-to-knowledge-case.py
backlinks:
  - _index
---
# 案例详情"查看源项目 / 标书原文"操作契约与导入数据陷阱

> **一句话**：案例详情两个操作按钮的共享契约收敛在 `useBidCaseSourceActions.js`
> （CaseGrid 与 AiRecommendDrawer 复用）；本页同时记录历史切片导入的两个数据陷阱
> （300 字截断 / 枚举混填），防止重导数据时复发。

## 操作契约（2026-09-03 修复后事实）

### 1. 查看源项目
- 跳转路由是 **`/project/{sourceProjectId}`**（投标项目详情）。
- 旧实现跳 `/bidding/detail/{id}`——该路由不存在，打开必空白；且 `bidding/:id`
  是标讯详情，语义也不对（源项目是投标项目不是标讯）。

### 2. 标书原文下载（三段防护 + 单标书兜底）
1. **预检未归档与原版单标书兜底**：先 `GET /api/archive/files?projectId={id}&size=1`，
   若 `totalElements > 0` 则走项目归档 ZIP 导出；
   若 `totalElements === 0`，兜底查询项目原始文件（`GET /api/projects/{id}/documents?documentCategory=BID`），
   若存在已上传的投标文件则直接下载单标书文件；皆无才提示"标书原文未归档：该项目暂无档案附件"。
2. **错误体识别**：`export-zip/{id}` 带项目成员校验（403/404），
   `responseType:'blob'` 会把错误 JSON 体也包成 blob。下载前用
   `isJsonErrorBlob()`（content-type 含 application/json）拦截并解析错误消息，
   不再落盘假 ZIP（`readJsonErrorMessage` 带 FileReader 兜底）。
3. **`res.data` 构造**：blob 下载必须用 `res.data`，`new Blob([res])` 会把
   整个 AxiosResponse 序列化成 `[object Object]`（CO-429 同款，
   ArchiveDetailDrawer 先修过，`ProjectArchive.spec.js` 有锁定用例）。

### 3. 项目阶段标书下载守卫（CO-442 对齐）
- 标书制作页（`DraftingStage.vue`）对齐后端 `ProjectDocumentDownloadService`：
  允许 `DRAFTING`（含审核子状态）与 `CLOSED`（结项后知识积累）下载；
  中间阶段（`EVALUATING` / `RESULT_PENDING` / `RETROSPECTIVE`）给出明确的“阶段只读不可下载”提示。

## 导入数据陷阱（重跑切片导入前必读）

### 陷阱 1：切片正文 300 字截断
`slice_all_v9.py` 解析 docx 时有 `full_text`，但 JSONL 原来只写
`text_preview[:300]`——导入的案例全是半截话，无实战复用价值。
修复：JSONL 增加 `text` 全文字段；导入脚本 `response_text` 取
`sl.get('text') or sl['text_preview']`（`knowledge_case.response_text` 是 TEXT 型）。

### 陷阱 2：枚举混填导致前台显示原始码
`knowledge_case.project_type`（项目类型：办公/综合/集采/工业品/其他，
见 `src/constants/projectTypes.js`）与客户类型（`LOCAL_SOE` 地方国企等）
是**两套不同枚举**。早期导入脚本在 `industry` 为空时回退取 `customer_type`，
导致前台项目类型标签查不到映射、原样显示 `LOCAL_SOE`。
修复：`effective_project_type` 仅取 `industry`，为空置 None（前台兜底显示"常规项目"）。
**通用教训：跨表灌数时字段语义归属必须按目标字段枚举校验，禁止跨枚举回退。**

## 回归锁定
- `useBidCaseSourceActions.spec.js`：6 用例（路由 / 无 id / 未归档 / 错误体 / 正常下载）
- `AiRecommendDrawer.spec.js`：res.data 构造 + 未归档中止 2 用例
