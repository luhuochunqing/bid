# Refactor Safety Governance

## 1. Objective

把"refactor 是否破坏了行为"变成 CI 可判定的事实，而不是 reviewer 印象。具体三件事：

1. **行为基线对比测试**：用 fixture-based golden snapshots 锁住 DocInsight 的 18 字段抽取契约 + `buildPrompt()` 字符串结构 + 前端高亮交互。下次 refactor 任何环节退化，PR 直接红。
2. **多 Agent 目录所有权 SOP**：在已有的端口/DB 物理隔离之上，加一层**代码域逻辑隔离**，把"两个 session 同时改同一文件"的事前禁止。
3. **抽象 Ablation 准入门**：通过 stub 第二个 `DocumentAnalyzer` profile（CONTRACT，5 字段）实测"加新 profile = ≤60 min / ≤3 新文件 / 0 旁路改动"。跑不通就拆现有抽象、不再叠层。

## 2. Background

`document_intelligence_engine_20260425` 把 `biddraftagent` 的 evidence-driven parsing 抽象为通用 `docinsight` 模块。架构方向正确（DocumentAnalyzer SPI、Schema-driven workbench、三层分包），但本次 refactor 暴露三类回归全靠人工 review 抓：

| 严重度 | Issue | 结构性根因 |
|--------|-------|-----------|
| 🔴 CRIT-1 | `mapToProfile` 丢 11 字段 | 强类型 `TenderRequirementProfile` (record, 18 字段) → `Map<String,Object>` → 强类型双跳，编译期无警告 |
| 🔴 CRIT-2 | Prompt 10 条规则全部丢失，被简化为 7 行 | `BaseOpenAiDocumentAnalyzer` 只抽 chunking + loop，让子类自写 `buildPrompt`，安全围栏未强制 |
| 🟠 HIGH-2 | `highlightInMarkdown` 留 stub | refactor 期间没有"输入相同 → 输出相同"的契约测试 |

P0 全部修复（详见 review 表 ✅），但**结构性预防机制未建**：下一次 refactor 仍然要靠人工 review。

并行问题：在 docinsight 重构期间，多 session 互改同一文件、index 抢占、文件反复出现/消失。`infrastructure_sop_setup_20260426` 已解决端口/DB 物理冲突，但**代码域冲突没有事前协议**。

## 3. Key Goals

### 3.1 行为基线对比测试（Behavioral Baseline）

- **后端 fixture**：`backend/src/test/resources/docinsight/fixtures/<name>.{input,expected.json}` + `@ParameterizedTest`，断言每份 fixture 的 18 字段提取结果与 golden 一致。
- **Prompt 字符串快照**：单独的 `BuildPromptSnapshotTest`，喂确定性输入（去时间戳、固定 chunk），断言 `buildPrompt()` 输出与 `expected-prompt.txt` 字符级一致。任何安全围栏 / 规则槽改动都会触发红灯。
- **前端高亮 e2e**：Playwright 真跑 DOM walk + scrollIntoView + 闪烁动画，断言点击字段后 markdown 区域出现 `.highlight-active`。
- **双轨**：
  - **离线**（mock LLM）：每 PR 必跑，断言 prompt 字符串 + parse 链路 + 字段结构。
  - **在线**（真 LLM）：nightly 跑，断言 18 字段命中率 ≥ baseline-3pp，结果写进 PR 描述当 cost/perf benchmark。

### 3.2 多 Agent 目录所有权 SOP（Code-Domain Isolation）

在现有 worktree 端口/DB 隔离之上，引入**目录所有权表**，事前禁止跨域同写。

#### 3.2.1 Ownership Table（**提案 — 待 user 确认后升级到 CLAUDE.md**）

| Agent | Backend 主域 | Frontend 主域 | 域职责 |
|-------|--------------|---------------|--------|
| **Claude** | `docinsight/`, `documenteditor/`, `documentexport/` | `src/components/common/DocVerificationWorkbench.vue` 及关联 view | 文档智能与编辑核心 |
| **Codex** | `biddraftagent/`, `templatecatalog/`, `template/`, `tenderupload/` | `src/views/Bidding/`, `src/components/ai/` | 投标稿件生成与模板 |
| **Gemini** | `tender/`, `bidmatch/`, `bidresult/`, `marketinsight/`, `competitionintel/` | `src/views/Tender/`, `src/views/Project/Match*` | 标讯与匹配引擎 |
| **Cursor** | `project/`, `projectworkflow/`, `projectquality/`, `historyproject/`, `casework/`, `approval/` | `src/views/Project/`, `src/views/Workflow/` | 项目生命周期 |
| **Integrator** | 跨域基础设施：`auth/`, `access/`, `audit/`, `platform/`, `integration/`, `config/`, `bootstrap/`, `exception/`, `controller/`, `dto/`, `entity/`, `repository/`, `service/`, `aspect/`, `util/`, `compliance/`, `qualification/`, `businessqualification/`, `contractborrow/` | `src/router/`, `src/stores/user.js`, `src/api/client.js`, 全局样式 | 跨域基础设施 + 集成验收 |

**未列出小域**（`alerts`, `alertdispatch`, `analytics`, `annotation`, `batch`, `calendar`, `collaboration`, `demo`, `fees`, `roi`, `roleprofile`, `scoreanalysis`, `settings`, `task`, `versionhistory`, `workbench`, `dto`, `entity`, `documents`, `admin`, `ai`）当前未活跃 — 谁先动谁负责在 PR 描述声明，无主默认归 Integrator 兜底。

#### 3.2.2 冲突协议

- **跨域改动**必须在 PR 标题加前缀 `[crosses-domain: <list>]`
- 跨域 PR 必须 @ 该域所有者评审，**24h 未响应视为 LGTM**
- **紧急跨域**允许先动手，但 24h 内必须补 hand-off note（PR 描述里写明动了别人地盘的原因）
- 同域同时间最多一个活跃 agent — 第二个 agent 必须先 fetch 主分支看 in-flight PR，发现冲突就等或换域

#### 3.2.3 CI 门禁

- GitHub Action 检查 PR diff 跨域时是否有 `[crosses-domain:]` 标签，缺则失败
- 标签必须列出真实跨入的域，可由脚本验证（diff 路径 → 表里查所属 → 不在自己域且未声明 = 红）

### 3.3 抽象 Ablation 准入门（Abstraction RIO Validation）

在宣布 docinsight 抽象 RIO 兑现**之前**，必须 stub 一个 CONTRACT profile 跑通端到端：

- **CONTRACT profile**：5 个字段（合同方、合同金额、签订日期、生效日期、违约条款），不接真 LLM、用 fake analyzer 返回固定 JSON 也可以
- **配额**：≤60 min 工时、≤3 个新文件、**0 个修改**已有文件（修了就证明抽象漏）
- **acceptance**：`/api/doc-insight/parse?profile=CONTRACT` 端到端通，前端 schema 渲染出 5 字段表单

跑不通的处置：
- **写不进 60 min** → 抽象抽得不够，回去把 `BaseOpenAiDocumentAnalyzer` 模板方法补强（强制 prompt 结构 = `safety preamble` + `<rules-slot/>` + `<document>fence`，子类只能填 rules-slot）
- **必须改已有文件** → 抽象漏在哪个文件就在哪个文件改，把跨 profile 共性提到 base
- **无法只用 schema 驱动前端** → 前端 `DocVerificationWorkbench` 的字段中立度有水分，需要补真泛型测试

## 4. Technical Architecture

### 4.1 Fixture 目录结构

```
backend/src/test/resources/docinsight/fixtures/
├── README.md                       # 格式 + 添加流程
├── tender_simple.input.pdf         # 真实最简标书（公开样本）
├── tender_simple.expected.json     # 18 字段期望
├── tender_simple.notes.md          # 字段级特殊处理说明（可选）
├── tender_complex.input.docx
├── tender_complex.expected.json
└── ...（5-10 份起步）

backend/src/test/resources/docinsight/prompts/
├── tender.expected-prompt.txt      # buildPrompt() 字符级快照
└── contract.expected-prompt.txt    # ablation 后新增
```

### 4.2 Prompt 结构强制

`BaseOpenAiDocumentAnalyzer.buildPrompt()` 由 base class 实现，强制结构：

```
<safety-preamble>
  ...固定 anti-injection 文案...
</safety-preamble>

<extraction-rules>
  ${childClass.rulesSlot()}    <!-- 子类唯一可填的位置 -->
</extraction-rules>

<document>
  ${chunkContent}
</document>
```

子类只能 override `rulesSlot()` 返回 String，整段 prompt 由 base 拼装。CRIT-2 类回归从此**编译不过**而不是 reviewer 看 diff。

### 4.3 Map<String,Object> 收口（关联但不在本 track 范围）

CRIT-1 的真正根治是把 `DocumentAnalysisResult.extractedData()` 从 `Map<String,Object>` 升级为参数化 `<T>`（强类型沿穿）。本 track **不**做这个改动 — 太大。但 fixture 测试会抓到任何"少 put 一个字段"的回归，作为 stop-gap。彻底收口由后续独立 track 承担。

## 5. Non-Goals

- ❌ **不重做 testing-governance**：TDD 闭环、Jacoco 覆盖率、CI hook 已落地，本 track 只加"refactor-specific 行为基线"这一类。
- ❌ **不重做 infrastructure_sop_setup**：worktree + 端口 + DB 物理隔离已就绪，本 track 只加代码域逻辑隔离。
- ❌ **不重写 docinsight 抽象**：除非 ablation 失败，才回去改 `BaseOpenAiDocumentAnalyzer`。
- ❌ **不引入新测试框架**：JUnit5 + Vitest + Playwright 沿用现有栈。
- ❌ **不做 Map<String,Object> → 强类型 `<T>` 的彻底收口**：留给独立 track。

## 6. Acceptance Criteria

| 维度 | 标准 |
|------|------|
| 行为基线 | `mvn test -Dtest=DocInsightBaselineTest` 全绿，复现当前 18 字段 + prompt 字符串 |
| Prompt 强制 | `BuildPromptSnapshotTest` 红灯能在子类绕过 base 安全围栏时触发（用故意改坏的子类做反向测试） |
| 前端高亮 | `npm run test:e2e -- highlight.spec.ts` 全绿 |
| Ownership 标签 | 跨域 PR 缺 `[crosses-domain:]` 时 CI 红 |
| Ablation 通过 | CONTRACT profile stub 跑通 `/api/doc-insight/parse?profile=CONTRACT`，且变更范围 ≤3 新文件 / 0 旁路改动 |
| 制度化 | nightly fixture run 入 PR 描述模板；CLAUDE.md 升级 ownership 表 |

## 7. Open Questions（待 user 决策）

1. **Ownership table 划分**是否合理？特别是 `biddraftagent`（旧）和 `docinsight`（新）的过渡 — 现在归 Codex 还是 Claude？建议过渡期共管，由 Claude 主导收尾删除 `@Deprecated(forRemoval=true)`，期间 Codex 改动需要 cross-domain 标签。
2. **CI 门禁**用 GitHub Action 还是本地 pre-push hook？Action 更硬但需要 push 权限；hook 可绕过但反馈快。建议两者都上：hook 是软提醒，Action 是硬门禁。
3. **Nightly fixture 跑哪 5-10 份样本**？需要 user 提供脱敏标书或同意用现有 e2e fixtures。
