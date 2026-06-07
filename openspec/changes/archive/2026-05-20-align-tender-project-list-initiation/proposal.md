# Change: Align Tender Project List & Initiation with PRD §4.3

## Why
PRD 简报-投标项目-2026-05-19.md 与代码实现对比显示：
- 项目列表页仅展示 5 列（项目信息/预算/状态/负责人/操作），PRD 要求 16 列 + 导出功能
- 项目立项表单严重简化，缺少基本信息/投标信息/客户信息三大分区及 AI 风险评估
- 这两个缺口是用户最直观可见的 P0 问题，且后端生命周期 API 已完备，只需前端补齐

## What Changes
1. **项目列表页重构** (`src/views/Project/List.vue`)
   - 表格列扩展至 PRD 要求的 16 列（业主单位、入闱家数、创建时间、开标时间、投标月份、项目类型、客户类型、客户等级、投标状态、项目负责人、负责人部门、投标负责人、中标状态、投标平台等）
   - 搜索区字段与表格列对齐
   - 新增导出 Excel 按钮（调用后端全量导出 API）
   - 表格最小宽度 1400px，支持横向滚动
2. **项目立项表单补全** (`src/views/Project/stages/InitiationStage.vue`)
   - 按 PRD 分区：基本信息区（3 个一排）、投标信息区（前 6 字段 3 个一排，后 6 单个一排）、客户信息表格（15 列 × 14 行）、招标文件上传区
   - 增加 AI 风险评估按钮（调用 `/api/ai/risk-assessment`）
   - 保留现有的提交/审核流程对接
3. **后端 DTO 对齐**
   - 扩展 `InitiationDto` / `InitiationViewDto` 以承载新增字段
   - 列表查询接口扩展 projection 字段

## Impact
- **Affected specs**: 新增 `tender-project` capability
- **Affected code**: 前端 `src/views/Project/List.vue`, `src/views/Project/stages/InitiationStage.vue`, 后端 DTOs
- **No breaking changes**: 新增字段 nullable，现有数据兼容
