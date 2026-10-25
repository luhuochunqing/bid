---
title: AI 文档扫描件不可识别提示契约
space: engineering
category: pitfalls
tags: [扫描件, PDF, AI 评分项检测, 标书质量核查, 错误提示]
sources:
  - src/utils/aiDocumentError.js
  - src/views/Project/stages/components/ScoreParseDrawer.vue
  - src/views/Project/stages/components/QualityCheckDrawer.vue
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckTaskAppService.java
backlinks:
  - src/views/Project/stages/composables/useQualityCheckDrawer.js
  - src/composables/projectDetail/useScoreParseDrawer.js
created: 2026-09-21
updated: 2026-09-21
health_checked: 2026-10-25
archive: false
---

# AI 文档扫描件不可识别提示契约

## 触发条件

- Sidecar 返回 `low_text_density`、空文本、无文本层或扫描件相关失败信息。
- 适用场景：AI 评分项检测、AI 标书质量检查。

## 前后端契约

| 层 | 约束 |
|---|---|
| 后端质量检查报告 | `QualityCheckReportDTO.errorMessage` 必须透传持久化失败原因，不能只返回 FAILED 状态。 |
| 前端错误归一化 | `src/utils/aiDocumentError.js` 将扫描件错误归一化为 `scanned=true`，保留原始 detail。 |
| 前端展示 | 两个抽屉均使用明显告警：`PDF 扫描件暂不支持识别`，并指导用户上传可复制文字 PDF 或先 OCR。 |
| 普通错误 | 保留原有错误详情与重试入口，不将所有解析失败误标为扫描件。 |

## 副作用（Cross-Module Impact）

- 质量检查报告接口字段变化只增加错误信息透传，不改变任务状态机或数据库结构。
- 扫描件提示只改善失败反馈，不自动 OCR、不自动重算历史任务，也不触发生产部署。
- 新增错误归一化工具应优先复用，避免各抽屉再次维护不同的扫描件关键词和文案。

## 验证基线

- `src/utils/aiDocumentError.spec.js`
- `src/views/Project/stages/composables/useQualityCheckDrawer.spec.js`
- `BidQualityCheckTaskAppServiceTest#testToReportDTO_ExposesErrorMessage`
