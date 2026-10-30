---
title: 标讯批量导入校验失败计数回写与错误表格防御
space: engineering
category: pitfalls
tags: [标讯, 批量导入, Excel, TenderImportTask, BulkImportDialog, 排障]
created: 2026-10-30
updated: 2026-10-30
health_checked: 2026-10-30
sources:
  - backend/src/main/java/com/xiyu/bid/tender/service/TenderImportTaskStateService.java
  - backend/src/main/java/com/xiyu/bid/tender/service/TenderImportAppService.java
  - backend/src/main/java/com/xiyu/bid/tender/service/TenderExcelParser.java
  - src/views/Bidding/list/components/BulkImportDialog.vue
backlinks:
  - _index
---
# 标讯批量导入校验失败计数回写与错误表格防御

> 现场案例（2026-10-29，winbid-01 测试环境）：用户上传 `标讯批量导入模板 (1).xlsx` 失败，界面显示「导入失败：共 0 行，失败 0 行（未写入任何数据）」，且下方原本用于提示修改的错误明细表格完全不渲染。

## 1. 根因剖析（前后端连环缺陷）

1. **业务校验拦截**：
   Excel 缺少了必填字段（`客户类型*`、`优先级*`、`项目类型*`）。这些字段位于模板靠后位置（第 16/17/18 列），用户填表未向右滚动或使用旧模板漏填。
2. **后端计数漏回写与缓存清除叠加**：
   - `TenderImportTaskStateService.markFailed` 原实现只更新了 `status="FAILED"` 和 `errorDetails`，未设置 `totalRows` 和 `failureCount`，数据库保留了创建初始值 0；
   - `TenderImportAppService.finalizeProgress` 执行完毕后立即清除了 Redis 缓存，强制前端轮询走 DB fallback，读取到 `totalRows=0, failureCount=0`。
3. **前端展示门禁强耦合失败计数**：
   - `BulkImportDialog.vue` 原展示条件为 `v-if="result.failureCount > 0 && result.errors?.length"`；
   - 即使后端正确返回了 `errors` 明细列表，因 `failureCount === 0`，整个错误表格被直接隐藏，形成“既提示看表格修正，但表格空无一物”的 UX 阻断。

## 2. 防御与修复方案

1. **后端状态机回写补齐**：
   - `TenderImportTaskStateService` 提供带计数的 `markFailed(taskId, totalRows, failureCount, errors)` 重载，确保行数与去重后的失败行数持久化；
   - 两参数重载与降级兜底层自动根据 errors 列表补算计数，杜绝 0 计数持久化。
2. **空文件防御守卫**：
   - `TenderExcelParser` 在总数据行数为 0 且无解析错误时，主动追加 `RowError(0, "file", "Excel 文件中未包含任何有效数据行，请填写数据后再上传")`，避免空模板静默通过。
3. **前端展示解耦与字典补全**：
   - `BulkImportDialog.vue` 表格条件调整为 `v-if="Boolean(result.errors?.length)"`，只要有错误明细即展示；
   - 提示文案对 `totalRows` 和 `failureCount` 增加自动兜底计算；
   - 补全 `FIELD_LABELS` 常见字段中文标签，错误类型不再直接暴露英文驼峰。
