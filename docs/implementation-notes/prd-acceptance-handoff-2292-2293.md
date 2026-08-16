# Handoff：PR #2292 + #2293 验收缺口跟踪

> 用途：按「PRD × 原型 × 代码」验收结果改代码，不要重新发明需求。  
> 验收日期：2026-08-16  
> 最终结论：**[FAIL]** — 78 项里 PASS 25 / FAIL 52 / UNVERIFIED 1  
> 状态：`[ ]` 未做　`[x]` 已修并有证据　`[-]` 明确不做（须写原因）

---

## 0. 开工前必读

### 0.1 审的是哪份代码

| PR | 分支 | 必须对齐的 tip | 角色 |
| --- | --- | --- | --- |
| [#2292](https://gitee.com/allinai888/bid/pulls/2292) | `origin/agent/gemini/ai-score-parse-v3` | `7cf9ae76585c9c9d99470707b7de863d6ae3f171` | 前端抽屉 + 真接口对接 |
| [#2293](https://gitee.com/allinai888/bid/pulls/2293) | `origin/agent/mimo/ai-score-parse-backend` | `efe8b1f47dece2611f94158d0223745fabf9fafb` | 后端 scoreparse |

**陷阱：** gemini 本地 worktree 曾停在 `7f356cf`，比 2292 少 4 个提交，仍是旧 `bidAgent` 假打分。改之前先：

```bash
git fetch origin
git checkout agent/gemini/ai-score-parse-v3   # 或自己的修复分支
git merge-base --is-ancestor 7cf9ae7 HEAD && echo "已含 2292 tip" || echo "落后，先 rebase/cherry-pick"
```

两个 PR 都还 **open**，且 2292 依赖先合 2293。联调必须把两边合成一条工作分支再验。

### 0.2 唯一验收依据

| 材料 | 路径 / 链接 |
| --- | --- |
| 新版 PRD | https://my.feishu.cn/docx/D6fUdxPpJojW5Tx6cW5cyHcAnCe |
| 原型 | `docs/prototypes/AI评分标准解析-V3.html` |
| 后端契约（辅助，不能压过 PRD） | `specs/041-ai-score-parse-backend/contracts/score-parse-api.md` |

规则：

- PRD 与原型冲突时，**业务规则以 PRD 为准**（例如报价类归主观）。
- 原型明确画出、PRD 没写的交互，也要做（入口徽标、自动打分时机、占位态）。
- 「看起来能跑 / 测试绿」不算完成。每条要能指回 PRD/原型 + 代码/运行证据。

### 0.3 建议改法

1. 先合出一条 `fix/score-parse-v3-acceptance`（2293 底 + 2292 头）。
2. 按下面 P0 → P1 → P2 → P3 勾。
3. 每修完一块，在本文件该行改 `[x]`，并在「验证」列写命令或截图说明。
4. 不要顺手做导出美化、导入草稿重构、架构重写。

---

## 1. 进度看板

| 优先级 | 总数 | 未做 | 已修 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| P0 生产不可用 | 3 | 3 | 0 | 0 |
| P1 核心流程错 | 8 | 8 | 0 | 0 |
| P2 重要不一致 | 8 | 8 | 0 | 0 |
| P3 文案/对齐细节 | 6 | 6 | 0 | 0 |

完成定义：P0 + P1 全部 `[x]`，且第 7 节回归清单全绿，才允许重新提验。P2/P3 未清仍是 FAIL。

---

## 2. P0 — 不修不能演示 / 不能上线

### P0-1 阶段 2 表格实际得分恒为「待评审」

- [ ] **R056**
- 现象：打分接口有数字，表格全员「待评审」。合计高亮有时有数，单元格没有。
- 根因：`runScoring` 写入 `result.score`，`ScoreParseTable.getScoreText` 读 `result.actualScore`（`undefined` 被当成主观）。
- 改：

```
src/composables/projectDetail/useScoreParseDrawer.js
src/views/Project/stages/components/ScoreParseTable.vue
src/views/Project/stages/components/ScoreItemDetailModal.vue
```

- 正确：全链路统一 `actualScore`。客观项显示数字；主观项显示「待确认」（见 P3-6）。
- 验证：mock 一条 `actualScore: 9` 的客观项，表格单元格必须是 `9`，不能是「待评审」。

### P0-2 现网投标文件打分判定为「未上传」

- [ ] **R042 / R054**
- 现象：用户在标书制作阶段上传的投标文件，打分仍 `NO_BID_DOCUMENT`。
- 根因：现网 `documentCategory` 标准值是 **`BID`**（`DocumentCategoryNormalizer`）。打分只查 **`BID_FILE`**。前端也没调 `POST /score-parse/bid-documents`。
- 改：

```
backend/.../scoreparse/application/ScoreScoringAppService.java
（评估）BidDocumentUploadService / POST /bid-documents — 无调用方可删或改写成写 BID
```

- 正确：识别 `BID`（兼容历史 `BID_DOCUMENT`）。不要另造分类。
- 验证：只走现网文档上传（分类 `BID`），不走新接口，打分前置必须通过。

### P0-3 打开抽屉强制重新打分，读不到旧结果

- [ ] **R011 / R012 / R071**
- 现象：每次点入口都 `POST /scoring`，覆盖旧分、烧 LLM；失败时 `scored` 仍可能为 true。
- 根因：

```js
currentStage.value = options.stage ?? 2
scored.value = options.scored ?? (currentStage.value === 2)
// fetchAnalysisData 在 stage===2 时无条件 runScoring
```

生产调用是 `scoreParseRef?.open()`，不传参数。
- 改：

```
src/composables/projectDetail/useScoreParseDrawer.js
src/components/project/detail/ProjectDetailMainColumn.vue
```

- 正确打开顺序：
  1. `GET /items`
  2. 有投标文件？→ 阶段 2，否则阶段 1
  3. `GET /results`（或从 items/summary 判断是否已打分）
  4. **仅当** 阶段 2 且还没有打分结果时，才自动 `POST /scoring`
  5. 已有结果只展示，「重新打分」才覆盖
- 验证：已打过分后关闭再开，网络面板只能看到 GET，不能出现 POST `/scoring`。

---

## 3. P1 — 核心流程 / 计分对错

### P1-1 阶段机与按钮状态

- [ ] **R007 / R008 / R009 / R010**
- 正确：
  - 无投标文件 → 阶段 1；打分按钮置灰，文案 **「AI 实际打分（需先上传标书）」**
  - 有文件未打分 → 「AI 实际打分」
  - 已打分 → 「重新打分」
- 改：抽屉打开参数 + 后端返回文件名/是否已上传（`GET /items` 或 status 补字段）。
- 验证：三种数据态各开一次抽屉，按钮文案与 disabled 必须对。

### P1-2 打分输入被截成 12000 字

- [ ] **R053**
- 位置：`ScoreScoringAppService.BID_DOC_EXCERPT_MAX_CHARS = 12000`
- 正确：按评分项检索相关段落，或分段对标。禁止整份标书一刀切。
- 验证：构造 >12000 字、关键证据在后半段的投标文本，该项必须能引用后半段。

### P1-3 阶段 2 满足状态不更新

- [ ] **R057 / R058**
- 现象：打分后表格「满足状态」仍是阶段 1 预判；合计与单元格字段分裂。
- 正确：阶段 2 用 `result.status` + `actualScore` 画状态、统计、高亮。
- 验证：阶段 1「不满足」、阶段 2 满分的项，阶段 2 表必须显示「✓ 满足」。

### P1-4 资质匹配：等级未用 + 过期被当成未命中

- [ ] **R034 / R035**
- 位置：`EstimatedScoreService` 传 `CertMatchRequest(keywords, null, today, null)`；`CertMatchService` 用 `expiry >= today` 过滤。
- 正确：抽出要求等级并匹配；过期证 **算命中**、`kbHit=true`、状态 **待确认**、依据写「证书已过期，标书需补充说明或更新证书」。
- 验证：库里只有过期证书 → 不得 0 分「不满足」。

### P1-5 超时时间与文案

- [ ] **R028 / R051 / R075**
- 现状：前端 `pollTask` 150×2s = **5 分钟**就失败；后端 30 分钟文案是「任务超时终止，保留上次成功结果」。
- PRD 指定句：
  - 解析：「解析超时，请检查文件大小或稍后重试」
  - 打分：「打分超时，请检查文件大小或稍后重试」
- 改：`src/composables/projectDetail/scoreParseTask.js`；`ScoreParseTaskStateService.TIMEOUT_MESSAGE`；前端 catch 分支。
- 验证：轮询上限 ≥ 30 分钟；超时 toast/错误态必须是 PRD 原句。

### P1-6 四路召回不完整

- [ ] **R016**
- 现状：正则+结构 一路，再串行 chunk LLM 一路。召回三/四挤在一个 prompt。
- PRD：关键词规则 / 文档结构 / 评分语义 / LLM 全文 **四路并行入候选池，互不覆盖**。
- 验证：无「评分标准」关键词、但有「每提供一个得 2 分」的段落必须进候选池。

### P1-7 维度级分值闭环缺失

- [ ] **R020**
- 现状：只有总分 `WeightSumCheck`。
- PRD：按维度（商务/技术/价格等）分别比对声明分值，差了针对该维回补。
- 改：`WeightSumCheck` 或新的维度校验 + `recheckGaps` 带维度上下文。

### P1-8 数量 / 编号连续性未校验

- [ ] **R021**
- 现状：`ItemCountCheck` 只判 `count <= 0`。
- PRD：表格行数、编号连续性、分值合计；不连续要回补。
- 验证：编号 A1、A2、A4（缺 A3）必须触发回补，不能静默过。

---

## 4. P2 — 重要不一致

### P2-1 重新解析无条件删除打分

- [ ] **R014**
- 位置：`ScoreItemPersistenceService.invalidatePreviousResults`
- PRD §3.7：重解析默认 **不影响** 已有打分；仅编号/数量/权重变化才需重打。
- 正确：items 指纹未变则保留 `score_result`；变了再清并提示重打。

### P2-2 权重合计 ≠ 100 无前端标注

- [ ] **R023**
- 后端已有 `summary.weightWarning`，前端 `getItems` 只用 `items`。
- 正确：不阻断；合计行显示实际总分 + **「权重合计与 100 分不符」**。

### P2-3 详情 / 来源 / 建议

- [ ] **R048** quote 为空必须显示 **「标书引用：无」**（现在整行不渲染）
- [ ] **R063** 阶段 1 详情紫色 pill **「知识库命中」**（`normalizeScoreItem` 丢掉了 `kbHit`）
- [ ] **R065** 修改建议 **仅阶段 2** 且仅不满足/待确认；禁止阶段 1 出建议、禁止硬编码兜底句
- [ ] **R069** 工具栏展示真实招标/投标文件名 + 解析/评分时间；无文件保持 `—`，但有文件必须回填（`GET /items` 或 status 补字段）

文件：

```
src/views/Project/stages/components/ScoreItemDetailModal.vue
src/composables/projectDetail/useScoreParseDrawer.js
backend ScoreParseItemsDTO / ProgressDTO（补 fileName、parseTime）
```

### P2-4 异常文案与上传校验

- [ ] **R027** 0 项失败全句：「未在文件中识别到评分标准章节，请确认文件内容或手动联系管理员」
- [ ] **R043** 未解析就打分：「请等待招标文件解析完成后再进行打分」（不要把 `SCORE_ITEMS_NOT_READY` 直接甩给用户）
- [ ] **R050** 投标文件解析失败：客观项 `actualScore=null`、状态待确认 +「投标文件解析失败，无法完成打分，请检查文件内容或重新上传」。现在是整任务 FAILED、不写 null。
- [ ] **R074** 无权限：「您无权限查看此任务的评分解析结果」（现在是「权限不足，无法访问该项目/资源」）
- [ ] **R030 / R031** 主上传路径 50MB + 格式提示。招标 PRD 允许 PDF/Word/Excel；投标 PDF/Word。现网招标导入是 **30MB**，与 PRD 50MB 不一致，要按 PRD 改或在产品侧确认后回写 PRD（未确认前按 PRD）。

### P2-5 入口风险徽标

- [ ] **R005**
- 生产入口是 `ProjectTaskBoardCard`，无红数字。`TaskKanban` 有徽标但是死代码。`emit('parsed')` 父组件没听。
- 正确：阶段 1 不满足数量显示红标，与原型一致。

### P2-6 评分类别不是 AI 判定

- [ ] **R025**
- 现状：`ScoreTypeClassificationPolicy` 正则；`scoreTypeGuess` 不落库。
- PRD：由 AI 按量化/描述判定；报价类强制主观可作后置覆盖。
- 正确：以 LLM 结构化字段为准，正则只做报价兜底，不要反过来盖掉 AI。

### P2-7 阶段 2 未按类型公式计分

- [ ] **R044**
- 现状：阶段 2 直接信 LLM `actualScore`，只做区间守卫。
- PRD §3.4：资质/仓库/品牌分档，人员比例，业绩数量比；部分分 = weight × 匹配比例，四舍五入，开区间 (0, weight)。
- 正确：LLM 出证据与 `matchRatio`，分数用与阶段 1 同构的 `PartialScorePolicy`（或等价）计算。

### P2-8 完整性回补触发面不够

- [ ] **R022**
- 现状：仅总分异常时一次 `recheckGaps` prompt。
- PRD：分值闭环 **或** 数量校验发现差异都要触发；重点扫脚注/备注/跨页。
- 与 P1-7 / P1-8 一起做。

---

## 5. P3 — 文案与原型对齐

- [ ] **R002** 生产按钮文案改成 **「AI 评分标准解析」**（现在少空格）— `ProjectTaskBoardCard.vue`
- [ ] **R059** 合计行列对齐原型：满足统计在状态列，客观/主观在类别列，高亮在得分列，依据列留空
- [ ] **R060** 空状态用书名号：「尚未解析到评分标准，请上传招标文件后点击「重新解析」」
- [ ] **R066** 符号：`✓ 满足` / `✗ 不满足` / 待确认灰字+蓝点（不要 `✕`）
- [ ] **R067** 遮罩副文案按 PRD §6.6：`读取评分项 → 解析标书内容 → 比对知识库资质证书、仓库信息、品牌授权资料 → 计算客观项得分`
- [ ] **R070** 主观项得分展示 **「待确认」**（代码里的「待评审」按 PRD 改；原型用「待评审」，验收以 PRD 为准）

---

## 6. 不要当缺口去改

这些 **不是 FAIL**，不要扩 scope：

| 项 | 原因 |
| --- | --- |
| 行内编辑 / 确认 / 保存评分项 | PRD、原型都没有 |
| 「导入到评分草稿」「导出报告」 | 额外能力，不是本轮验收项 |
| Design Token / 行数预算 / 测试 skipIf | 工程项，与产品验收无关 |
| 把正则分类「改得更漂亮」但不接 LLM | 那是继续偏离 R025 |

UNVERIFIED（不能靠改一行代码勾掉）：

| ID | 内容 | 怎样才能变 PASS |
| --- | --- | --- |
| R078 | 真实招标文件零遗漏 | 拿一份真实标书跑解析，回对原文评分表；召回/闭环修完后再验 |

---

## 7. 回归清单（重新提验前）

- [ ] `git rev-parse HEAD` 已包含 2292 tip `7cf9ae7` 与 2293 tip `efe8b1f`（或等价修复提交）
- [ ] 打开抽屉：**无** 多余 `POST /scoring`
- [ ] 现网分类 `BID` 的投标文件可以打分
- [ ] 阶段 2 表格客观项显示数字分
- [ ] 无投标文件时按钮置灰且文案正确
- [ ] 过期证书 → 待确认，不是 0 分不满足
- [ ] quote 空 → 「标书引用：无」
- [ ] 阶段 1 详情有「知识库命中」；阶段 2 没有
- [ ] 权重合计 98 时合计行有警告文案
- [ ] 超时 / 0 项 / 未解析 / 无权限 四句 PRD 原文能对上
- [ ] 前端：相关 spec + 抽屉交互
- [ ] 后端：`scoreparse` 模块测试 + 分类 `BID` 的打分前置用例

---

## 8. 关键文件速查

### 前端（2292）

| 文件 | 管什么 |
| --- | --- |
| `src/api/modules/scoreParse.js` | 6 个真接口 |
| `src/composables/projectDetail/scoreParseTask.js` | 枚举映射 + 轮询上限 |
| `src/composables/projectDetail/useScoreParseDrawer.js` | 阶段机 / 打开 / 打分 / 重解析 |
| `src/views/Project/stages/components/ScoreParseDrawer.vue` | 抽屉 UI、按钮、遮罩、图例 |
| `src/views/Project/stages/components/ScoreParseTable.vue` | 8 列表 + 合计 + **actualScore bug** |
| `src/views/Project/stages/components/ScoreItemDetailModal.vue` | 详情 / quote / 建议 / kbHit |
| `src/components/project/ProjectTaskBoardCard.vue` | **真正的**入口按钮 |
| `src/components/project/detail/ProjectDetailMainColumn.vue` | `open()` 无参数 |
| `src/views/Project/stages/components/TaskKanban.vue` | 死入口，不要只改这里 |

### 后端（2293）

| 文件 | 管什么 |
| --- | --- |
| `ScoreParseController` / `KnowledgeMatchController` | HTTP |
| `ScoreParseAppService` | 解析编排 |
| `ScoreScoringAppService` | 打分编排、**BID_FILE**、**12k 截断** |
| `EstimatedScoreService` / `CertMatchService` | 过期证 / 等级 |
| `ScoreItemPersistenceService` | 重解析删打分 |
| `ScoreTypeClassificationPolicy` | 正则当 AI |
| `PartialScorePolicy` / `ScoreAssessmentGuard` | 计分与守卫 |
| `WeightSumCheck` / `ItemCountCheck` | 闭环过窄 |
| `OpenAiScoreAnalyzer` | 召回只有两路 |
| `ScoreParseTimeoutScanJob` / `ScoreParseTaskRecoveryRunner` | 超时与恢复 |
| `DocumentCategoryNormalizer` | 现网分类真相：`BID` |
| `V1187` / `V1188` | 三表 + 业绩金额 |

---

## 9. 改完怎么回填本文件

1. 把对应 `- [ ]` 改成 `- [x]`。
2. 在该条下追加一行：`验证：<命令或现象> — <日期/作者>`。
3. 第 1 节看板数字跟着改。
4. P0+P1 全绿后，重新跑一次「PRD × 原型」逐条验收，不要只说「bug 修了」。

上一轮验收统计备忘（修之前的基线，不要改）：

```
总需求 78 | PASS 25 | FAIL 52 | UNVERIFIED 1 | 结论 [FAIL]
```
