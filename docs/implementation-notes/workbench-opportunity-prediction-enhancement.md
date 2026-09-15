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
   - `OpportunityPredictionCard.vue` 控制在 284 行（<= 300 门禁）。
   - `OpportunityPredictionCard.spec.js` 控制在 288 行（<= 300 门禁）。
   - 后端 FP-Java 策略类保持纯函数无框架依赖，ArchUnit 架构校验 100% 通过。

5. **表头两端对齐与跨两排导出按钮改造**
   - 筛选控件采用 CSS Grid 4 列网格（`op-filters-grid`）铺满整行，彻底解决右侧大面积空置断层问题；
   - 第 1 排 4 控件（项目名称、招标主体、客户类型、项目类型）各占 1fr；第 2 排预测时间选择器跨 3 列（3fr），重置按钮占 1 列（1fr），两排两端严格齐平；
   - 导出按钮改造成跨越两排（`height: 100% !important; min-height: 68px`），图标居中置顶、文案置底，与左侧网格形成封闭规整矩形。
