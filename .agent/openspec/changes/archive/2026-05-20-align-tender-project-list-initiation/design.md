## Context
后端项目生命周期 6 阶段 API 已完备（INITIATED → DRAFTING → EVALUATING → RESULT_PENDING → RETROSPECTIVE → CLOSED），Policy/Entity/Controller 齐全。前端 `src/views/Project/stages/` 有 6 个独立阶段组件，但字段和布局与 PRD §4.3 差距大。

## Goals
- 列表页：16 列展示 + 多维度筛选 + Excel 导出
- 立项页：4 分区表单（基本信息/投标信息/客户信息/招标文件）+ AI 风险评估

## Non-Goals
- 不改动阶段流转逻辑（backend 已正确实现）
- 不重构旧版 ProjectDetailShell（本次仅更新 stages 组件）
- 不引入新的 AI 模型（复用现有 AI 检查接口）

## Decisions
- **客户信息表格用 JSON 存储**：15 列 × 14 行 = 210 个字段，不适合平铺为 DB 列。使用 `customer_info_json` JSON 列存储表格数据，前端以表格组件编辑。
- **导出走后端流式生成**：前端直接调用 `/api/projects/export?filter=xxx`，后端用 Apache POI 生成并流式返回，避免大数据量前端处理。
- **表单布局用 CSS Grid**：基本信息 3-per-row 用 `grid-template-columns: repeat(3, 1fr)`；投标信息前 6 个同样，后 6 个用 `grid-template-columns: 1fr` 单列。
- **AI 风险评估复用现有能力**：项目已有 `useProjectDetailAI` composable 和 `/api/ai/check` 端点，扩展为支持招标文件解析即可。

## Risks / Trade-offs
- **JSON 列查询性能**：客户信息表格不需要单独查询/索引，作为项目详情的一部分整体读写，JSON 列足够。
- **列数过多导致渲染慢**：16 列 + 横向滚动，使用 `el-table` 的 `virtual-scroll` 或固定列优化（首列固定）。
- **向后兼容**：所有新字段 nullable，现有数据无需迁移。

## Migration Plan
1. 先合并后端迁移 + DTO（不影响现有功能）
2. 再合并前端列表页（可见性高，独立验证）
3. 最后合并前端立项页（依赖后端 DTO）
4. 每步都可独立回滚
