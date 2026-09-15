# Implementation Notes: 工作台 AI 商机预测功能增强（搜索/1年窗口/导出）

> 分支：`agent/gemini/opportunity-prediction-search-export`
> 任务：工作台 AI 商机预测卡片功能增强：
> 1. 支持按项目名称和招标主体搜索过滤
> 2. 商机预测可见时间窗口从 6 个月扩大到 1 年
> 3. 支持将当前商机预测数据导出为 CSV/Excel 文件

## 核心变更

1. **商机可见预测窗口扩至 1 年**
   - 后端纯策略 `OpportunityPredictionPolicy.VISIBLE_WINDOW_MONTHS` 从 6 修改为 12。
   - 相关接口文档与服务注释同步更新为近 1 年商机。
   - 前端卡片副标题调整为 "只显示近 1 年商机"，空状态文案调整为 "暂无近 1 年商机预测"。

2. **前端本地双字段模糊搜索**
   - 新增 `projectNameFilter`（项目名称）与 `customerFilter`（招标主体）输入框。
   - 通过 `computed(filteredRows)` 进行双字段不区分大小写、去除首尾空格的模糊匹配。
   - 过滤条件变动时自动重置分页至第一页；提供重置按钮一键清空条件。
   - 遵循 `src/styles/tokens.css` 规范，输入框保持 32px 标准高度与统一间距。

3. **商机预测数据导出**
   - 新增导出按钮，使用已封装的 `exportApi.exportExcel` 工具，生成带 UTF-8 BOM 的 CSV 文件。
   - 导出列共 11 列，与表格展示字段完全一一对应：
     - 序号、项目名称、项目状态、招标主体、项目负责人、客户类型、项目类型、开标时间、项目服务周期（年）、服务周期截止时间、预测招标时间。
   - 包含空数据轻提示防护（若无可用数据导出则进行友好提示）。

4. **行预算与架构门禁**
   - `OpportunityPredictionCard.vue` 控制在 271 行（<= 300 门禁）。
   - `OpportunityPredictionCard.spec.js` 控制在 288 行（<= 300 门禁）。
   - 后端 FP-Java 策略类保持纯函数无框架依赖，ArchUnit 架构校验 100% 通过。

5. **表头两端对齐与跨两排导出按钮改造**
   - **根因修复**：Element Plus 的 `<el-date-picker>` 属于 Fragment/内部多层包装组件，Vue 3 的 `<style scoped>` 无法将单层 Grid 的 `grid-column: span 3` 正确作用到该组件，导致其退化为单列占宽，重置按钮挤在第 2 列、第 3-4 列留白；
   - **架构重构**：改造为显式双行网格架构：
     - 第 1 排：`repeat(4, 1fr)` 4 列等宽，项目名称、招标主体、客户类型、项目类型各占 25%；
     - 第 2 排：`3fr 1fr` 网格，预测日期范围占 3fr（75%）并通过 `:deep(.el-date-editor)` 设置 `width: 100% !important; box-sizing: border-box;` 完整充满前 3 列对应区域；重置按钮占 1fr（25%），与上方的项目类型左右边缘 100% 垂直重合；
     - 右侧导出按钮：`display: flex; align-items: stretch` + `height: 100% !important; min-height: 68px;`，纵向充满两排高度，与左侧两排形成严密规整的封闭矩形。
