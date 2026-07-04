# Implementation Notes — CO-498 项目复盘阶段提交后解锁结项阶段 tab

> 本文件按 AGENTS.md 用户指令维护：记录实现过程中**不在 spec 内的决策、被迫变更、tradeoff、需要你知晓的任何事情**。
> 从根因排查阶段开始记录，贯穿 Spec Kit 全流程。

---

## 阶段 0：根因排查（2026-07-04 17:18 完成）

### 决策 1：根因判定 — 是"设计如此"，不是"代码 bug"

**不在 spec 的发现**：用户报告"未自动推进至结项"看似 bug，实为 2026-07-04 13:47 commit `5d1b36b53` 主动引入的设计变更。该 commit 删除了 `RetrospectiveService.submit()` 直达 CLOSED 的二次推进，动机是修 CO-443 `alreadyClosed` 误判 bug。

**Tradeoff**：直接告诉用户"这是设计如此，不修"，会让线上项目 157 永远卡在 RETROSPECTIVE——因为下游确实存在导航断层（CLOSED tab 不可点）。所以根因报告区分了两个层次：
1. `RetrospectiveService.submit()` 不自动推 CLOSED — **正确，不改**
2. `ProjectStageController` 不把 CLOSED 放进 `accessibleStages` — **是 5d1b36b53 暴露的下游断层，需要修**

**需要你知晓**：如果你期望"复盘提交直达 CLOSED"（即恢复 `d8b7fbf12` 之前的旧行为），那是方案 B（被否决）。本次实现的是方案 A，维持结项审核流程。

### 决策 2：SOP §23 三层证据全部走完

未跳过任何层：
- Layer 1 (Sentry): 生产 `sentryEnabled=false`，不可用（已查证 `deployed-release.json`）
- Layer 2 (DB + TraceId): 还原项目 157 完整时间轴（5 次 stage 推进 + 复盘提交），印证"无 RETROSPECTIVE→CLOSED 日志"
- Layer 3 (git): 定位 `5d1b36b53` 设计变更 + `d8b7fbf12` 历史前序

**需要你知晓**：用户 `06234` 是 admin（不是 bid-projectLeader），所以即使 tab 解锁，他自己也无法"提交结项"——只能由该项目指派的 bid-projectLeader 提交。**这暴露了另一个潜在风险**：如果某项目没有指派 bid-projectLeader，结项流程会死锁（无人能提交）。本次修复**不解决**这个问题，标记为后续 enhancement。

---

## 阶段 1：Spec Kit spec.md（2026-07-04 17:21 完成）

### 决策 3：跳过 Spec Kit 的 feature 分支名校验

**问题**：Spec Kit `check-prerequisites.sh` 强制要求分支名形如 `NNN-feature-name`，但 `agent-start-task.sh` 建的分支是 `agent/zcode/co498-...`（项目多 Agent 协作约定）。

**Tradeoff**：选项有 (a) 切到合规分支名，破坏 agent-start-task.sh 的分支策略；(b) 跳过 hook 直接跑；(c) 手动建 feature 目录绕过校验。

**选择 (c)**：在 `.specify/specs/024-co498-retrospective-closure-tab-unlock/` 手动建目录，让后续 Spec Kit 命令能识别 feature，但不改动任务分支名。这样既遵守 agent 协作约定，又能跑 Spec Kit 后续流程。

**需要你知晓**：
- 如果你后续想用 Spec Kit 的完整 git 集成（`/speckit-git-commit` 等），需要手动指明 feature 目录
- feature 编号选了 `024`（specs 目录当前最大是 023，`.specify/feature.json` 里写的 027 是陈旧数据，目录里没有）

### 决策 4：spec.md 包含完整的 SOP 取证

**偏离 CO-400 spec 模板的地方**：CO-400 spec 把"问题分析"放在了 spec.md 里（包含完整调用链和 DB 证据）。我沿用这个做法，把 SOP 三层证据写进 spec.md 的"问题分析"章节，而不是另开 root-cause-analysis 文档。

**理由**：
- 本 bug 同样依赖 SOP 取证建立可信根因
- spec.md 是 Spec Kit 流程的"单一真相源"，把根因放这里让 plan/tasks 阶段都能引用
- 后续可酌情把"问题分析"章节抽出来到 `docs/lessons/root-cause-analysis-co-498.md`（按 lessons-learned 惯例），但本次先内联

---

## 阶段 2：plan.md（2026-07-04 17:25 完成）

### 决策 5：复用 `service.hasClosureSubmission()` 而非新加 repository 调用

**问题**：controller 已注入 `closureRepository`（CO-443 用），但 CO-443 实际调用的是 `service.hasClosureSubmission()`（service 层封装）。我们判断"是否解锁 CLOSED"也需要这个信息，应该用哪个？

**选择**：用 `service.hasClosureSubmission()`，与 CO-443 完全一致。

**理由**：
- service 层封装若有缓存或聚合逻辑，直接调 repository 会绕过
- 单一真相源：项目"是否有 closure 提交"由 `ProjectStageService` 统一定义
- controller 已经调过一次 `service.hasClosureSubmission(projectId)`，可抽局部变量复用结果，**零额外 DB 查询**

### 决策 6：用 `actual`（真实 stage）而非 `current`（CO-443 调整后的 stage）做解锁判定

**问题**：controller 第 52 行把 `current` 在 closure 已提交时改成 CLOSED。我们判定"复盘未提交 closure"应该用哪个？

**选择**：用 `actual == ProjectStage.RETROSPECTIVE`，不用 `current`。

**理由**：
- `current` 在 closure 已提交时 = CLOSED，此时 `current == RETROSPECTIVE` 为 false，会漏判（不会解锁，但语义错位）
- 用 `actual == RETROSPECTIVE` 表达"真实仍在复盘阶段"，配合 `!hasClosureSubmission` 双重精确判定
- 两者在功能上等价（hasClosureSubmission 已守门），但语义清晰度更高

**Tradeoff**：引入 `actual` 与 `current` 两个变量的对比，读者需要理解 CO-443 才能看懂。已在代码注释中明确标注"用 actual 而非 current"的原因。

### 决策 7：测试用例覆盖"重复 CLOSED"风险

**新增的潜在风险**：closure 已存在（DRAFT）时，`current=CLOSED`，CLOSED 已在 `completed` 列表里。如果解锁逻辑误触发，CLOSED 会出现两次。

**测试覆盖**：`co498_retrospectiveWithClosureDraft_doesNotUnlockClosedTwice` 用 `accessibleStages.not(hasSize(7))` 断言无重复（6 个阶段是正常上限）。

**额外守护**：`actual == RETROSPECTIVE && !hasClosureSubmission` 在 closure 已存在时 `hasClosureSubmission=true`，整个条件为 false，不会触发重复追加。

---

## 阶段 3：tasks.md（待办）

<!-- /speckit-tasks 阶段会在这里追加任务清单 -->

---

## 阶段 3：tasks.md（待办）

<!-- /speckit-tasks 阶段会在这里追加任务清单 -->

---

## 阶段 4：实现（待办）

<!-- TDD: Red → Green → Refactor，每个 commit 在这里追加一行 -->

---

## 阶段 5：验证（待办）

<!-- 单测 + 项目 157 端到端验证记录 -->
