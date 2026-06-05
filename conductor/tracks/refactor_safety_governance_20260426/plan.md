# Implementation Plan: Refactor Safety Governance

## 目标
把 docinsight refactor 暴露的三类结构性回归（CRIT-1 字段丢失 / CRIT-2 prompt 退化 / HIGH-2 高亮 stub）转为 CI 可判定事实；同时把多 agent 代码域冲突由事中协调升级为事前禁止；并通过 ablation 实测验证 docinsight 抽象的 RIO。

## 关键上下文与约束
* **依赖完成的前置**：document_intelligence_engine_20260425（已完结）、infrastructure_sop_setup_20260426（已完结）、testing-governance Phase 1-3（已完结）。
* **仓库路径**：fixture 落 `backend/src/test/resources/docinsight/`；prompt 快照落同目录 `prompts/`；ownership 表落 `CLAUDE.md` 与本 spec 同步。
* **TDD 强约束**：每个任务先写失败测试（红），再实现（绿），最后 refactor。沿用 conductor/workflow.md 标准任务生命周期。
* **不动**：不重写已存在的 `OpenAiTenderDocumentAnalyzer`、`DocVerificationWorkbench.vue`、`DocumentAnalysisResult` schema —— 本 track 只加防护和门禁。

## 实施步骤

### Phase 1: 行为基线落地 (Behavioral Baseline)
- [ ] 任务 1.1: 创建 `backend/src/test/resources/docinsight/fixtures/` 目录与 README，定义 input/expected/notes 三件套格式
- [ ] 任务 1.2: 引入 5 份 fixture（3 PDF + 2 DOCX，覆盖现存 e2e 样本），生成对应 expected.json（先跑当前实现一次锁住）
- [ ] 任务 1.3: 编写 `DocInsightBaselineTest`（@ParameterizedTest 读 fixture，mock LLM 返回 expected.json，断言端到端 18 字段一致）— 先红再绿
- [ ] 任务 1.4: 编写 `BuildPromptSnapshotTest`（确定性输入 → `buildPrompt()` 字符级 assertEquals），生成 `tender.expected-prompt.txt` 锁基线
- [ ] 任务 1.5: 编写 `BuildPromptSafetyGuardTest`（用故意删掉 safety preamble 的 fake 子类，断言 base 拼装机制无法被绕过）
- [ ] 任务 1.6: 编写 `e2e/highlight.spec.ts`（Playwright，DOC 上传 → 点击字段 → 断言 markdown 区出现 `.highlight-active` + scrollIntoView 触发）
- [ ] 任务 1.7: 接入 nightly online 通道（`mvn test -Dtest=DocInsightOnlineFidelityTest -Dprofile=online`），断言 18 字段命中率 ≥ baseline-3pp，结果输出 JSON 入 PR 描述

### Phase 2: 目录所有权 SOP 落字 (Ownership Codification)
- [ ] 任务 2.1: 把 spec.md 的 ownership table 升级到 `CLAUDE.md` 的多 Agent SOP 段（先 user 确认提案）
- [ ] 任务 2.2: 在 `CLAUDE.md` 加冲突协议章节（crosses-domain 前缀、24h LGTM、紧急 hand-off note 规则）
- [ ] 任务 2.3: 写 `scripts/check-ownership.sh`，对当前 git diff 做"路径 → 域 → 当前 agent" 校验，输出违规清单
- [ ] 任务 2.4: 接入本地 pre-push hook（软提醒）：cross-domain 时打印警告 + 提示加 PR 标签
- [ ] 任务 2.5: 接入 GitHub Action（硬门禁）：`.github/workflows/ownership-check.yml`，PR 跨域且无 `[crosses-domain:]` 标签 → 红
- [ ] 任务 2.6: 文档化未列出小域的兜底规则（"无主域归 Integrator + PR 描述声明"），写进 CLAUDE.md

### Phase 3: 抽象 Ablation 实施 (CONTRACT Profile Stub)
- [ ] 任务 3.1: 写 `ContractRequirementProfile` record（5 字段：合同方、金额、签订日期、生效日期、违约条款）
- [ ] 任务 3.2: 写 `ContractDocumentAnalyzer implements DocumentAnalyzer`，`supports("CONTRACT")` 返回 true，提取逻辑用 fake LLM 返回固定 JSON 即可
- [ ] 任务 3.3: 加 1 份 fixture（`contract_simple.input.pdf` + `expected.json`）走通 `DocInsightBaselineTest`
- [ ] 任务 3.4: 前端验证 `DocVerificationWorkbench` 用 schema 渲染 CONTRACT 5 字段表单（无需改组件代码）
- [ ] 任务 3.5: **Acceptance check**：统计本 phase 改动 — 新文件 ≤3，已有文件改动 = 0，工时 ≤60 min。任一超额 → ablation 失败，进 Phase 3.5
- [ ] 任务 3.5（条件触发）: ablation 失败时拆抽象：把 `buildPrompt()` 改为 base class final，子类只 override `rulesSlot()`；把跨 profile 共性提到 base；重跑 3.1-3.5

### Phase 4: 制度化 (Institutionalization)
- [ ] 任务 4.1: 把 nightly online fidelity 报告嵌入 PR 描述模板（`.github/PULL_REQUEST_TEMPLATE.md` 加固定段位）
- [ ] 任务 4.2: 在 `conductor/workflow.md` 加"Refactor Safety Checklist"章节：refactor 前必跑 baseline、refactor 中必加 fixture、refactor 后必更新 expected
- [ ] 任务 4.3: 把 fixture 目录纳入 `check:doc-governance`（或新增 `check:fixture-coverage`），断言任何新 profile 必须配套 fixture
- [ ] 任务 4.4: 归档本 track，更新 `tracks.md` 状态为 `[x]`，输出"refactor regressions caught"指标占位（首次实测在下一个 refactor）

## 验证与测试

### Phase 完成口径
- **Phase 1 绿**：`mvn test` + `npm run test:e2e -- highlight.spec.ts` 全绿，nightly online 通道至少跑通一次（手动触发）
- **Phase 2 绿**：故意起一个跨域 PR（不打标签）→ Action 红；打标签 → 绿。本地 hook 软提醒可见
- **Phase 3 绿**：CONTRACT profile 端到端跑通，且 `git diff --stat origin/main...HEAD` 显示 ≤3 新文件 / 0 已有文件改动
- **Phase 4 绿**：随机抽下一个 PR 检查 PR 描述是否带 fidelity 报告段位

### 反向测试（关键）
本 track 必须验证**保护机制真的会失败**：
- 故意删一个 expected.json 字段 → `DocInsightBaselineTest` 必须红
- 故意改 `buildPrompt()` 删 safety preamble → `BuildPromptSafetyGuardTest` 必须红
- 故意把 highlight 函数清空 → e2e 必须红
- 没有反向测试就不能算 phase 完成

## 回滚策略

- Phase 1 fixture 误锁了坏行为 → 删 expected.json，重跑当前实现重新生成
- Phase 2 ownership 表划得太细导致正常 PR 都跨域 → CLAUDE.md 缩域、合并相邻 agent 的次要域
- Phase 3 ablation 失败 → 进 3.5 拆抽象，不绕过门禁
- Phase 4 PR 模板太重导致 reviewer 抱怨 → 砍到只剩"是否跨域 / fidelity 数字"两行
