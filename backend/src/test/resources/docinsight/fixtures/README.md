# DocInsight Behavioral Baseline Fixtures

> Track owner: `refactor_safety_governance_20260426`
> 创建于 Phase 1 任务 1.1（占位骨架；fixtures 由任务 1.2 填充）

## 目的

锁定 `DocumentAnalyzer` 输入 → 输出契约。任何 refactor（包括 prompt、chunking、parser、mapper）破坏 18 字段提取行为时，CI 直接红，不再依赖 reviewer 看 diff 抓回归。

## 三件套格式

每份 fixture 由 2-3 个文件组成，文件名共享 `<name>` 前缀：

```
<name>.input.{pdf,docx,md}        # 真实样本（脱敏）
<name>.expected.json              # 期望提取结果（强类型字段全列）
<name>.notes.md                   # （可选）字段级特殊处理说明
```

### `<name>.expected.json` 结构

按 `TenderRequirementProfile` 18 字段全展开，缺省字段写 `null` 而非省略键，避免"少 put 一行"的回归被静默吞掉：

```json
{
  "profile": "TENDER",
  "fields": {
    "projectName": "...",
    "projectCode": "...",
    "tenderee": "...",
    "agency": null,
    "budgetAmount": "...",
    "budgetCurrency": "CNY",
    "bidOpenTime": "...",
    "bidSubmissionDeadline": "...",
    "qualificationRequirements": [...],
    "technicalRequirements": [...],
    "commercialTerms": [...],
    "evaluationMethod": "...",
    "evaluationCriteria": [...],
    "deliveryRequirements": "...",
    "paymentTerms": "...",
    "performanceBond": null,
    "tenderBond": "...",
    "contactInfo": {...}
  },
  "evidenceAnchors": [
    { "field": "projectName", "chunkIndex": 0, "snippet": "..." }
  ]
}
```

> 注：`fields` 必须列全 18 个键，`evidenceAnchors` 至少覆盖 5 个字段以验证证据链未断。

### `<name>.notes.md`（可选）

记录字段级特殊判断，给后续 reviewer / 加 fixture 的人参考：

```markdown
## 特殊处理
- `budgetAmount`: 原文写"￥500万元含税"，期望规范化为 "5000000.00"
- `tenderBond`: 多处提及，取最早一处（2026 标识符在前）
```

## 双轨运行模式

### 离线（Mock LLM）— 每 PR 必跑

入口：`mvn test -Dtest=DocInsightBaselineTest`
机制：用 `expected.json` 反向构造 mock LLM 响应，断言 parse + map + 端到端响应链路与期望一致。
作用：抓"代码层"回归（mapper 丢字段、prompt 拼装错误、chunker 越界）。

### 在线（真 LLM）— Nightly + PR 描述

入口：`mvn test -Dtest=DocInsightOnlineFidelityTest -Dprofile=online`
机制：真调 LLM，按 fixture 期望计算 18 字段命中率（精确匹配 + 数值容差 + 文本相似度）。
基线：每月初一次重置 baseline，PR 必须 ≥ baseline-3pp（点位）。
作用：抓"prompt 质量"回归（规则被简化、安全围栏丢失但代码路径还通）。
输出：JSON 报告写入 PR 描述固定段位。

## 添加新 fixture

1. 在本目录放入 `<name>.input.<ext>` + `<name>.expected.json`（必要时加 `.notes.md`）
2. 跑 `mvn test -Dtest=DocInsightBaselineTest -Dfixture=<name>` 确认离线绿
3. 必要时跑 `mvn test -Dtest=DocInsightOnlineFidelityTest -Dprofile=online -Dfixture=<name>` 确认在线命中率达标
4. 在 PR 描述说明 fixture 来源、脱敏处理、期望来源

## 当前 fixtures

（占位 — 由 Phase 1 任务 1.2 填充。目标 5-10 份覆盖：简单 PDF / 复杂 DOCX / 多 chunk / 字段稀疏 / 跨页表格）

## 反向测试要求

每个新 fixture 必须配一个**故意改坏 expected.json 的反向 case**，证明保护机制真的会失败 — 否则等于把 fixture 当摆设。详见 `plan.md` 任务 1.5。

## 邻接资源

- Prompt 字符串快照：`backend/src/test/resources/docinsight/prompts/<profile>.expected-prompt.txt`
- 前端高亮 e2e：`e2e/highlight.spec.ts`
- 在线 fidelity 报告模板：`.github/PULL_REQUEST_TEMPLATE.md`（Phase 4 加）
