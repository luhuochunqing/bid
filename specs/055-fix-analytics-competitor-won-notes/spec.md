# Feature Specification: M1 多维度趋势分析竞品维度「中标数」口径修复 (055)

**Feature Branch**: `agent/ox/fix-analytics-competitor-won-notes`

**Created**: 2026-08-26

**Status**: Draft

**Input**: 用户报告：数据分析 → 多维度趋势分析，X 轴选「竞品公司」时，图中「中标数」与「投标数」完全相等。根因：中标数错误地按项目级状态 `projects.status = WON` 推导，而正确数据源是「结果确认 → 竞争对手情况」表格的「是否中标」列（语义漂移存储于 `project_result_competitor.notes`，枚举 `已中标/未中标`）。

## 业务背景与问题定义

竞品维度的「中标数」业务含义是：**该竞品公司在多少个项目里中标了（竞品视角的中标事实）**，而非「我司在这些项目里中标了几个」（项目视角）。

当前实现（`TrendAnalysisComputationService.computeDimensionTrend`）对所有维度统一用 `projects.status == WON` 计入中标数。对竞品维度而言这是语义层级错配：

- `projects.status` 是我司的项目级中标结果，无法推导每个竞品各自的中标状态；项目 WON 时所有竞品的「中标数」被整列抬高。
- 该错配已在 M4 竞品分析模块修过一次（lessons-learned §127，PR !2339），本次属**同一根因在 M1 模块复发**。
- 项目既有口径纪律（`.wiki/pages/data-analysis-revamp.md` §10）：竞品维度取「是否中标」必须读 `prc.notes` 并精确匹配解析；禁止用 `projects.status` / `ProjectResult.resultType` / `pid.bidResultStatus` 推导。

同时，M1 图表支持下钻（PRD 6.6），点击竞品柱子的「中标数」系列会打开项目明细列表并展示汇总（totalWins/winRate）。下钻链路（`TrendDrillDownService`）同样按 `p.status = 'WON'` 过滤，若只改图表计算会出现「图上数字与点开明细对不上」的新不一致。

## 用户故事与使用场景

### User Story 1: 竞品维度图表「中标数」反映竞品真实中标次数 (P1)
**作为** 数据分析模块的使用者，
**我希望** X 轴选择「竞品公司」时，「中标数」等于该竞品在「竞争对手情况」表格中被标记为「已中标」的次数，
**以便于** 准确评估各竞品对我司项目的威胁程度。

**Why this priority**: 这是本需求的全部价值所在；当前数字系统性失真（全部等于投标数）。

**Independent Test**: 构造一个竞品 A 参与 3 个项目、其中 2 条记录标记「已中标」的数据，图表中竞品 A 应显示 投标数=3、中标数=2（与项目状态无关）。

**Acceptance Scenarios**:

1. **Given** 竞品 A 在项目 P1（status=WON）的记录标记「已中标」，在项目 P2（status=WON）的记录标记「未中标」，**When** 查看 X 轴=竞品公司的趋势图，**Then** 竞品 A 显示 投标数=2、中标数=1（当前错误实现显示 2/2）。
2. **Given** 竞品 B 在项目 P3（status=LOST）的记录标记「已中标」，**When** 查看趋势图，**Then** 竞品 B 中标数 ≥ 1（项目级 LOST 不再压制竞品自身的中标事实）。
3. **Given** 某竞品行 notes 为空、「未中标」或历史「其他说明」自由文本，**When** 统计，**Then** 一律不计入中标数（与前端下拉为空的展示行为对齐）。

### User Story 2: 下钻明细与图表数字一致 (P2)
**作为** 点击图表柱子下钻的用户，
**我希望** 竞品维度点「中标数」柱子看到的明细列表和汇总总数，与图上该柱的数字完全一致，
**以便于** 追溯每一个被计入的中标事实。

**Why this priority**: 下钻是图表的可信度锚点，不同步修复会产生新的口径分裂 bug。

**Independent Test**: 对同一竞品的「中标数」柱执行下钻，返回的 summary.totalWins 必须等于图表 winSeries 中该竞品的值。

**Acceptance Scenarios**:

1. **Given** 竞品 A 图表中标数=1（来自 notes），**When** 点击该柱下钻 seriesName=中标数，**Then** 明细仅含该条记录所属项目，summary.totalWins=1。
2. **Given** 非竞品维度（部门/人员等 6 个维度），**When** 下钻中标数，**Then** 行为与现状完全一致（仍按 projects.status=WON，不受本次改动影响）。

### User Story 3: 口径单一来源，杜绝第三处定义 (P3)
**作为** 后续维护者，
**我希望** 「已中标」枚举值与判定逻辑只有一处定义，M4 与 M1 共享，
**以便于** 未来口径调整（如新增枚举值）只改一处。

**Why this priority**: 工程健康度项，不影响功能正确性，但缺失会让 §127 教训继续复发。

**Independent Test**: 全仓 grep「已中标」字面量，后端 Java 主代码中仅共享 support 类一处持有该常量。

---

### Edge Cases

- 同一项目同一竞品出现多条重复行（JOIN 扇出/脏数据）：去重键 `(category, projectId)` 先去重再计数，重复行不重复计入投标数与中标数。
- 一个项目多个竞品各自标记不同：每个竞品独立计数（竞品 A 已中标 + 竞品 B 未中标互不影响）。
- 下钻请求 axisValue 为空（异常调用）：回退到既有 `competitorNames` 筛选条件 + notes 条件，不抛错。
- notes 值含首尾空白（如 `" 已中标 "`）：SQL 端 `TRIM()` 与 Java 端 `String.trim()` 双侧对齐后再精确匹配。
- 其他 6 个维度（部门/人员/区域/客户类型/项目类型/招标主体）与时间维度：计算逻辑零变化，回归由既有单测保障。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-01 (唯一数据源)**: 竞品维度的中标数 MUST 仅依据 `project_result_competitor.notes` 字段判定，值为「已中标」（trim 后精确匹配）计 1；MUST NOT 使用 `projects.status` / `ProjectResult.resultType` / `pid.bidResultStatus` 推导。
- **FR-02 (投标数不变)**: 竞品维度投标数 MUST 维持现口径不变 = 该竞品参与的项目数（INNER JOIN ProjectResult 链路，按 `(category, projectId)` 去重）；中标率 = notes「已中标」次数 ÷ 参与项目数，即「该竞品对我司的中标率」。
- **FR-03 (查询层)**: `fetchCompetitorRows` MUST 在 JPQL 中带出 `prc.notes` 并传递至计算层；其余维度查询 MUST NOT 受影响（`DimensionRow` 通过便捷构造器保持既有调用点零改动）。
- **FR-04 (计算层)**: 竞品维度 MUST 走独立纯函数计算路径（winCount 按 notes 解析），不得向共用方法塞布尔分支参数污染其他 6 个维度。
- **FR-05 (下钻同步)**: `TrendDrillDownService` 在 dimension=competitor 且 seriesName=中标数时，明细过滤与 summary.totalWins MUST 改用 EXISTS 子查询 `prc.name = :axisVal AND TRIM(prc.notes) = :wonLabel`；非竞品维度保持 `p.status='WON'` 不变。
- **FR-06 (常量单一来源)**: 「已中标」字面量与判定函数 MUST 提取到 analytics 包共享 support 类；M4 既有入口改为委托调用且保留原方法签名（不破坏 M4 测试）。
- **FR-07 (期望分类补零)**: 竞品维度的 expectedCategories 补零行为（筛选器选中但数量为 0 的竞品仍在 X 轴展示）MUST 保持现状。

### Key Entities

- **project_result_competitor.notes**: 结果确认「竞争对手情况」表格「是否中标」列的实际存储字段（原名「其他说明」，前端语义漂移复用，`ResultConfirmStage.vue` 有注释锚点）；枚举 `已中标/未中标`，历史行可能为自由文本或 null。
- **DimensionRow**: 趋势分析维度查询单行 record，需扩展可选的竞品中标状态字段。
- **TrendDrillDownService**: 下钻原生 SQL 组装器，seriesName/dimension 条件分支所在。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 竞品维度图表中，任一竞品的「中标数」≤「投标数」，且当存在「未中标」记录时严格小于（不再恒等）。
- **SC-002**: 新增纯计算单测覆盖：已中标计入、未中标/null/空白/历史自由文本不计入、去重、winRate 计算、非竞品维度回归，全部通过。
- **SC-003**: M4 竞品分析既有测试全绿（委托重构零行为变化的证据）。
- **SC-004**: 下钻汇总 totalWins 与图表 winSeries 数值一致（同一数据源交叉核对）。
- **SC-005**: 后端主代码中竞品「是否中标」判定的字面量与解析逻辑仅存在于共享 support 一处；项目级状态 `WON→已中标` 的展示标签（如 Dashboard 下钻状态翻译）属另一语义维度，不在收敛范围。

## Assumptions

- 投标数维持现状口径（用户已于会话中确认），本 spec 不触碰 INNER JOIN ProjectResult 的取数范围。
- 本任务为单口径 bugfix，不走 Phase 级开发计划（PLANS.md 门禁判断依据：无新表、无迁移、无跨模块接口变更），按原子提交流程推进；测试证据与 wiki 回填不可省。
- 手工目视验证受 worktree 限制无法在本机启动开发环境（AGENTS.md 环境纪律），以纯计算单测 + 合入后在主工作区（trae）目视验收为准；本 worktree 可提供只读 SQL 交叉校验证据。
- 前端无需改动：图例文案「投标数/中标数/中标率」不感知口径变化。
