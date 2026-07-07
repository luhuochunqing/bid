# 复盘报告支持 Excel 上传

## 背景

项目详情页「项目复盘」阶段的「复盘报告」上传区当前仅接受 Word/PDF 文件（`.doc`、`.docx`、`.pdf`）。业务方希望复盘报告也能上传 Excel 文件（`.xls`、`.xlsx`），以便直接上传结构化数据形式的复盘材料。

## 目标

在不改动后端的前提下，扩展复盘报告上传组件的文件类型限制，使其同时支持 `.xls` 与 `.xlsx`。

## 现状分析

- 前端上传组件：`src/views/Project/stages/RetrospectiveStage.vue`
  - `accept=".doc,.docx,.pdf"`
  - `beforeUpload` MIME 白名单仅包含 Word/PDF
  - 提示文字为「支持 Word/PDF 格式」
  - 限制：最多 3 个文件，单文件 ≤20MB
- 后端文档上传校验：`backend/src/main/java/com/xiyu/bid/projectworkflow/core/UploadValidationPolicy.java`
  - 已允许扩展名：`png, jpg, jpeg, pdf, doc, docx, xls, xlsx`
  - 已允许 MIME：`application/vnd.ms-excel`、
    `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- 后端存储解析：`ProjectDocumentUploadWorkflowService.java` 已能正确将上述两种 MIME 映射为 `xls`/`xlsx`

结论：后端无需改动，只需前端放开文件类型限制。

## 方案：仅扩展文件类型

### 改动清单

1. `RetrospectiveStage.vue`
   - `el-upload` 的 `accept` 改为 `".doc,.docx,.pdf,.xls,.xlsx"`。
   - `beforeUpload` 的 MIME 白名单增加：
     - `application/vnd.ms-excel`（.xls）
     - `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`（.xlsx）
   - 上传提示改为「支持 Word/Excel/PDF 格式，单文件≤20MB，最多3个」。
   - 保持现有 3 文件、20MB 限制不变。

### 数据流

用户选择 Excel 文件 → `el-upload` 触发 `beforeUpload` → 前端校验扩展名/MIME/大小 → 通过则调用 `/api/projects/{projectId}/documents` → 后端 `UploadValidationPolicy` 再次校验 → 存储并返回文档 ID → `handleUploadSuccess` 将 ID 写入 `form.reportFileIds` → 提交复盘时随 `reportFileIds` 一并保存。

### 错误处理

- 扩展名/MIME 不匹配：前端 `ElMessage.error('仅支持 Word/Excel/PDF 格式')`。
- 大小超过 20MB：前端 `ElMessage.error('文件不能超过 20MB')`。
- 后端校验失败：由 `on-error` 统一提示。

## 测试计划

1. 单元测试：`src/views/Project/stages/RetrospectiveStage.spec.js`
   - 增加 `.xlsx` 文件通过 `beforeUpload` 的用例。
   - 增加 `.xls` 文件通过 `beforeUpload` 的用例。
   - 保持非允许格式（如 `.png`）仍被拒绝的用例。
2. E2E：`e2e/project-retrospective-flow.spec.js`
   - 如提示文字断言与本次改动冲突，同步更新为「支持 Word/Excel/PDF 格式」。
3. 手动验证：在项目复盘页面上传 `.xlsx` 与 `.xls`，确认能正常提交并回填。

## 风险与回滚

- 风险：浏览器/操作系统对 `.xls` 的 MIME 识别不一致（可能报告为 `application/octet-stream`）。缓解：同时保留扩展名校验，允许扩展名通过时放行。
- 回滚：还原 `RetrospectiveStage.vue` 中 `accept`、`beforeUpload`、提示文字三处修改即可。
