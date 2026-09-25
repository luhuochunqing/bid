---
title: 项目 1384 DOCX 评分方案资格失效根因
space: engineering
category: debugging
tags: [scoreparse, docx, sidecar, source-evidence, scheme, pr-2757]
sources:
  - document-converter-sidecar/table_extractor.py
  - document-converter-sidecar/converter.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringSchemePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreQualificationPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/TextPrimaryEvidenceBinder.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/golden/GoldenCasesReplayTest.java
  - docs/release/deploy-report-2026-09-25-205th-test.md
backlinks:
  - .wiki/pages/_index.md
  - .wiki/pages/scoreparse-multimodal-primary.md
created: 2026-09-25
updated: 2026-09-25
health_checked: 2026-09-25
archive: false
---

# 项目 1384 DOCX 评分方案资格失效根因

## 状态与证据边界

- PR #2757 已合入；test release `8245c4da8` 包含该 PR。项目 1384 的解析任务 261 于 2026-09-25 08:59:48 完成，但决策为 `REVIEW_REQUIRED`，`fallbackReason=ORIGINAL_TOTAL_UNKNOWN`。
- 测试站 `/api/projects/1384/score-parse/review` 只读快照：13 条事实、0 条有候选资格、`schemes=[]`、仅 3 条有来源单元格坐标；全部事实的排除原因是 `SOURCE_SCHEME_NOT_VERIFIED`。这解释页面的“本方案内 0 项，合计 0 分”。
- 原始 DOCX 下载至仓库外做本地只读转换。正文含“第三章 评审标准”和“本项目总分为 100 分”，报价 30、商务 40、技术 30。测试站原文与本地转换结果对应；本文未读取服务器 Sidecar 日志或持久化的转换产物。

## 必然性链

| 边界 | 当前输入/输出 | 结果 |
|---|---|---|
| DOCX → Sidecar Markdown | 评分标题成为 `**第三章** **评审标准**`；总分成为 `本项目总分为 **100** 分`；全文 0 个 `#` 标题 | `converter.extract_headings()` 得到空列表；Java 的 `OriginalScoringTextScopePolicy` 也只识别 `#` 标题 |
| DOCX → Sidecar 表格 | 24 张表的 `sourceSection` 全为 `null`；9 张表被宽泛关键词识别为评分表 | 表格无法与原文评分章建立可靠归属，资格审查表和末尾评审索引表也被误标 |
| 方案及总分解析 | Java 总分正则要求“总分为 100 分”中的数字直接出现，无法匹配 `**100**`；标题和表格章节均不存在 | `OriginalScoringSchemePolicy.schemes()` 的标题来源为空，降级分支又要求已解析总分，因此返回空清单 |
| 候选资格 | `OriginalScoreQualificationPolicy.qualify()` 在方案 ID 为空时先返回 `SOURCE_SCHEME_NOT_VERIFIED` | 已抽出的 13 条事实全部不可采纳，后端和前端均显示 0 分 |

## PR #2757 的设计与回归盲区

- `GoldenCasesReplayTest#project1384ReplayKeepsOneHundredPointSchemeAndCrossTableParent` 使用五行简化 Markdown：自带 `# 第三章 评审标准` 和未加粗的 `100`；构造 `DocumentArtifact` 时主动填入 `sourceSection`，构造事实时主动填入 `schemeId`、`IN_SCHEME`、单元格坐标。它只证明下游规则在理想输入下能运行，不能验证真实 DOCX 入口。
- `OriginalScoringSchemePolicy.tableBelongsTo()` 在仅发现一个方案而表格章节为空时，以 `isScoreTable` 把所有命中的表归入该方案；对项目 1384 会把非计分表带入。只补标题正则将打开此误归属路径。
- `TextPrimaryEvidenceBinder` 仅折叠空白，不处理 Markdown `**` 与原始单元格的表示差异，解释了大多数模型事实仍缺来源定位的独立风险。
- `OriginalScoringContext.parseText()` 在没有总分时逐行调用 `textScopeAt()`，后者每次重新分割全文并计算全部 scope；长文档落入二次复杂度路径。

## 修复判据

1. 在转换边界产出唯一的、带正文偏移/章节层级/表格位置的原文索引；从 DOCX 大纲和 body 顺序确定物理表归属，同一索引供总分、事实定位和方案资格使用。Markdown 加粗仅作展示，数值匹配使用保留偏移映射的规范化文本。
2. `isScoreTable` 仅是候选召回提示；任何无原文章节锚点的表不得仅凭该布尔值获方案资格。独立保存方案清单，复核页面不从事实资格反推方案存在。
3. 使用真实 1384 DOCX 在隔离环境验证完整链路：转换 → 表格归属 → 100 分原文定位 → 13 条事实逐项来源校验 → 有效计分单元闭合 100 → Review DTO/前端。脱敏金标保留真实转换形态，禁止预填 `schemeId`、`IN_SCHEME` 或 `sourceSection` 来代替入口。
4. 在测试站重新解析 1384，记录任务 ID、release、候选数/分数、原文定位和排除项；本地回归、部署成功与真实项目验收分别报告。

## 相关知识

- [[scoreparse-multimodal-primary]]：现有文字主链路、原文证据和发布保护契约。
