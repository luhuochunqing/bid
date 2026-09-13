# Feature Specification: 多表评分标准自动拆分（score-multi-table-split）

**Feature Branch**: `agent/trae/score-multi-table-split`

**Created**: 2026-09-13

**Status**: Draft

**Input**: 按方案 A 推进，开新任务落地多表自动拆分逻辑

## 背景与问题定义

招标文件的评分标准由多张独立评分表组成时（如技术评分表、商务评分表，每张表各有自己的汇总行声明总分），
当前 Evidence Pipeline（spec 059）把所有评分表的 `DeclaredTotalEvidence` 合并后一次性传入
`ScoreCompletenessPolicy.evaluateCompleteness`。当不同表的汇总行声明了不同总分（例如技术表 60 分、商务表 40 分），
`resolveDeclaredTotal` 检测到多个不同分值，判定为"结构化声明总分冲突"，整体决策 `REVIEW_REQUIRED`。
`StructuredTableExtractionHandler.markEvidencePipelineCompleted` 对 `REVIEW_REQUIRED` 只保存 facts/复核快照、
不覆盖 `score_item`，导致**正式评分项全部为空**，投标人无法直接查看任何评分项，必须人工复核。

**方案 A（多表自动拆分）**：把每张独立评分表识别为一个独立评分标准，各自按本表声明总分独立校验；
通过校验的表自动落库为正式评分项，未通过的表保留人工复核。多张表不再互相拖累。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 招标文件含多张独立评分表时自动解析出全部评分标准并落库 (Priority: P1)

投标人上传一份招标文件，其评分标准由多张独立评分表组成（如技术评分表、商务评分表，各表有独立的
汇总行声明总分，表内叶子权重合计精确等于本表声明总分）。系统解析后应能按表识别出多套评分标准，
每套标准内部分项自动进入正式评分项，投标人可直接查看，无需人工复核。

**Why this priority**: 这是本次方案 A 的核心价值——直接修复"多表小计冲突导致正式评分项为空"的线上缺陷。
多表文档在当前实现下解析结果不可用，阻塞投标人正常查阅评分项。

**Independent Test**: 用含两张独立评分表（各表声明总分不同、表内权重自洽）的 docx 跑
`ScoreFactShadowPipeline.run`，断言两张表各自产出 `AUTO_APPROVED` 的完整性结果；端到端解析后
`score_item` 表中包含两张表的全部评分项。

**Acceptance Scenarios**:

1. **Given** 招标文件含两张独立评分表（技术表声明 60 分、商务表声明 40 分，表内叶子权重各自等于声明总分），
   **When** 执行评分解析，**Then** 系统识别出 2 套评分标准，两张表的评分项全部自动落库为正式评分项，
   任务决策为自动通过，无需人工复核。
2. **Given** 上述多表文档中某一张表内部权重不自洽（叶子权重合计 ≠ 本表声明总分），
   **When** 执行评分解析，**Then** 自洽的表自动落库，不自洽的表保持人工复核，任务级决策反映"部分通过"。
3. **Given** 招标文件只有一张评分表（单表场景），**When** 执行评分解析，**Then** 行为与改造前完全一致
   （回归零变化）。

---

### User Story 2 - 任务元数据可追溯多表拆分结果 (Priority: P2)

投标管理员在解析任务详情中应能看到多表拆分的可审计信息：本次文档识别出几套评分标准、
其中几套自动通过落库、几套待人工复核，各套对应哪张来源表及各自声明总分。

**Why this priority**: 拆分后任务级决策不再是一个笼统的 `AUTO_APPROVED`/`REVIEW_REQUIRED`，
需要可追溯的分表决策，支撑人工复核定位"哪张表出了问题"，也符合 spec 059 证据化可审计主线。

**Independent Test**: 断言 `run_summary` / 任务复核快照中包含多表拆分明细（每张表的 tableId、
声明总分、决策、fallbackReason），且与 `score_parse_fact.source_table_id` 可对应。

**Acceptance Scenarios**:

1. **Given** 一张多表文档解析完成，**When** 查看任务 run_summary，**Then** 能看到每张表的
   tableId、声明总分、独立决策（AUTO_APPROVED / REVIEW_REQUIRED）与 fallbackReason。
2. **Given** 单表文档解析完成，**When** 查看任务 run_summary，**Then** 拆分明细仅包含一张表，
   输出格式与既有 `decisionStatus`/`fallbackReason` 语义兼容。

---

### Edge Cases

- **单张评分表**：不存在拆分，必须保持现有校验与落库行为完全不变（回归底线）。
- **多表中某张表无独立汇总行**（无结构化总分证据）：该表不满足"独立评分标准"判定，
  不单独拆出，按现有合并校验路径处理（正文/默认 100 兜底），并在拆分明细中标注"无独立总分证据"。
- **多张表声明总分相同**（如各表都声明 100 分）：当前冲突判定只认"不同分值"，同分值不冲突；
  拆分后仍按表独立校验，行为更明确，不引入新的冲突。
- **部分表通过、部分表失败**：通过的表照常落库；失败的表保留 facts 与复核快照，互不影响。
- **所有表都失败**：整体决策 `REVIEW_REQUIRED`，与现状一致。
- **表间编号重复**（技术表与商务表都有 A1/B2）：拆分校验不受影响，但落库指纹与旧打分保留逻辑
  按 `code` 匹配可能串号，需在 plan 阶段评估是否需要引入表维度区分（见 Assumptions）。
- **结构破损表**：某张表 `structurallyValid=false` 时按 `STRUCTURE_INCOMPLETE` 处理该表，
  其余自洽表照常自动落库，不得因单表破损拖垮整份文档。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 按 `ScoreFact.sourceTableId` 将评分事实分组，每张独立评分表视为一个评分标准。
- **FR-002**: 系统 MUST 为每张评分表独立收集本表 `DeclaredTotalEvidence`（`ScoreTableSummaryPolicy.extractDeclaredTotals` 按表过滤）。
- **FR-003**: 系统 MUST 对每张评分表独立执行完整性校验（叶子权重合计 == 本表声明总分），互不干扰。
- **FR-004**: 校验通过（`AUTO_APPROVED`）的评分表 MUST 自动落库为正式评分项（`score_item`），与既有 `persistItems` 路径一致。
- **FR-005**: 校验未通过的评分表 MUST 保留 facts 与复核快照，不覆盖正式评分项，但不得阻止其他表落库。
- **FR-006**: 单张评分表场景 MUST 保持改造前完全一致的行为与决策。
- **FR-007**: 任务元数据（`run_summary` / 复核快照）MUST 记录多表拆分明细：每张表的 tableId、声明总分、独立决策、fallbackReason。
- **FR-008**: 结构破损的评分表 MUST 按 `STRUCTURE_INCOMPLETE` 单独处理，不影响其他自洽表自动落库。

### Key Entities *(include if feature involves data)*

- **ScoreFact**：不可变评分事实，已含 `sourceTableId`（来源评分表 ID）、`parentFactId`（父子结构）。
  拆分以 `sourceTableId` 为分组键，`factId` 已含 tableId 前缀，跨表唯一。
- **DeclaredTotalEvidence**：单表汇总行的声明总分证据，含 `tableId`、`row`、`sourceCellId`、`quotedText`。
  拆分后按 `tableId` 归属到对应评分表。
- **ScoreCompletenessResult**：完整性校验结果。方案 A 下由"整份文档一份"扩展为"每张表一份 +
  文档级聚合"（聚合决策 = 最差子表决策）。
- **score_item**：正式评分项落库表。**不加**"评分标准"维度字段（见 Assumptions），仍按项目平铺落库。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 含多张独立评分表的文档解析后，通过校验的每张表评分项全部出现在正式评分项中，
  `score_item` 不再为空（修复当前"多表小计冲突导致正式评分项为空"缺陷）。
- **SC-002**: 单表文档解析结果与改造前逐项一致（回归测试全绿，行为零变化）。
- **SC-003**: 任务 run_summary / 复核快照可追溯每张表的独立决策与声明总分（分表决策可审计）。
- **SC-004**: 自动落库的评分项全部带单元格证据（sourceCellIds + weightEvidence），满足
  `persistFactsAndEvidence` 的拒绝无证据自动通过项约束，无需人工复核即可使用。
- **SC-005**: 新增/修改的纯核心策略（分组、按表汇总证据、按表校验）全部有独立单元测试覆盖，
  关键场景（多表全通过 / 部分通过 / 全失败 / 单表回归 / 结构破损混合）均有测试证据。

## Assumptions

- **不引入 score_item 评分标准维度字段**：方案 A 核心是"拆分校验 + 全部落库"，前端评分项列表保持
  按项目平铺展示。若后续需要按标准分组展示，作为独立 feature 另行评估（表间编号重复的指纹串号风险
  在 plan 阶段评估，如确认会造成实际数据错误，再升级为加字段方案）。
- **"独立评分表"判定**：一张表必须在本表内存在结构化汇总行声明总分证据，才被识别为独立评分标准；
  否则沿用合并校验路径（正文/默认 100 兜底），不单独拆出。
- **影响范围仅限 Evidence Pipeline（结构化表格通道）**：legacy markdown 召回、LLM 非表格候选等
  其他通道不参与拆分，行为不变。
- **拆分决策语义**：文档级决策 = 各子表决策的最差结果（任一表 REVIEW_REQUIRED 则文档级
  REVIEW_REQUIRED，用于保留任务级既有字段语义）；自动落库按子表粒度执行，不受文档级决策阻塞。
- **依赖既有能力**：`ScoreTableSummaryPolicy` 的按表汇总行识别、`ScoreCompletenessPolicy` 的
  完整性校验、`ScoreItemPersistenceService.persistItems` 的落库均复用，不新建平行实现。

## 2026-09-13 兼容漂移：合并父标题与首个子项共行

项目 1377 的真实 DOCX 暴露了本规格原测试未覆盖的结构：维度列纵向合并时，起始行既包含
`供货业绩（22分）` 这类父级声明，又在评分细则/最高分列承载第一个 7 分子项。物理行只有一行，
语义事实却有两个。现有“一行一个 ScoreFact”会让起始行成为父项，首个子项从叶子集合消失，
因此表内实际 100 分被计算为 95/86 分，导致通过表也无法落库。

### User Story 3 - 合并起始行保留父级与首个叶子 (Priority: P1)

**Independent Test**：回放脱敏的 DocumentArtifact v2 表格，包含一个 10 分纵向合并分组，
其起始行和续行分别为 5 分；另有独立 90 分项和 100 分汇总行。抽取结果必须包含父项 10 分、
两个叶子各 5 分，完整性结果为 `AUTO_APPROVED`，叶子合计为 100 分。

**Acceptance Scenarios**：

1. **Given** 合并起始行同时有唯一标题总分、非空细则和显式最高分，**When** 确定性抽取，
   **Then** 生成一个不可自动计分的父事实和一个可自动计分的首个叶子事实，两者证据分别锚定标题格和最高分格。
2. **Given** 同一续行上编号列与维度列都存在纵向合并，**When** 解析父子关系，**Then** 只按维度列定位 origin，起始行叶子和所有续行叶子都指向同一父事实。
3. **Given** 普通非合并行，或合并标题不存在唯一括号总分，**When** 抽取，**Then** 保持 062 之前行为，不凭空创建父级总分。
4. **Given** 有效表与无上限价格公式表并存，**When** 分表校验，**Then** 有效表照常自动落库，价格公式表继续人工复核。

### 增量要求与成功标准

- **FR-009**：系统 MUST 在“合并起始格 + 唯一标题总分 + 非空首行细则 + 显式最高分 + 至少一个有效续行子项”同时成立时，将一行拆为父事实与首个叶子事实。
- **FR-010**：父事实 MUST 使用标题总分及标题证据并保持非自动计分；首个叶子 MUST 使用显式最高分及其单元格证据。
- **FR-011**：同一合并区域内全部叶子 MUST 仅按映射后的维度列 origin 指向同一稳定父 factId；父、首叶子 factId MUST 不同且可重复生成，其他列的合并区域不得覆盖父链索引。
- **SC-006**：1377 同形态的脱敏最小回放中，叶子合计恢复为声明的 100 分并进入 `AUTO_APPROVED`，且现有 scoreparse 回归与架构门禁保持全绿。
