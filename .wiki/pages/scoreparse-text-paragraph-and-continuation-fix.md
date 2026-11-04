---
title: 纯文本段落评分事实穿透还原与表格跨行续行识别契约
space: engineering
category: architecture
tags: [scoreparse, 评分解析, 文本穿透, 表格折行, 证据还原]
sources:
  - document-converter-sidecar/converter.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnit.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestoration.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceSpanResolver.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceRecheckPlan.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreFactChunkMerger.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/baidu/BaiduTableArtifactEnricher.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreUnitPolicy.java
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-26
updated: 2026-11-04
health_checked: 2026-11-04

archive: false
---

# 纯文本段落评分事实穿透还原与表格跨行续行识别契约

## 业务痛点与技术现场

本节及下述契约描述2026-10-26的历史欠分问题；2026-11-04生产任务仍未通过完整验收，见末节。

在项目 43（中粮集团综合消费品招标文件）AI 评分标准解析中，招标文件声明总分 100 分，但页面仅解析出 61 分，丢失 39 分。
排查定位到两处底层解析与还原机制缺陷：

1. **纯文本段落引用丢失（缺陷 A，丢失 30 分价格条款）**：
   - 商务报价条款位于招标文件正文段落第 673~695 行（`text:line[693]`），不在 `tables` 中。
   - `SourceSpanResolver.resolve` 仅检索 `artifact.tables()` 与 `artifact.textBlocks()`，导致未还原出 `restored.text`（返回空字符串）。
   - 空字符串触发 `TextPrimaryScoreFactMapper` 的 `!restored.text().isBlank()` 门禁，将 30 分合法价格条款降级为 `REVIEW_REQUIRED` 且被 candidate 提取过滤。

2. **表格跨行续行识别过严（缺陷 B，丢失 5 分商务业绩与 4 分技术团队）**：
   - 表格中评审标准较长发生跨行折行，相邻续行仅第 2 列（评审标准列）有文本，分值列（第 3 列）和序号列（第 0 列）为空。
   - `LogicalRuleSourceUnit.isContinuationRow()` 原先死板假定续行文本必在最后一列 `maxCol`，导致中间列折行判定失败，无法合并为逻辑行，规则还原同样置空降级。

3. **短 quote 截断编号材料退化风险**：
   - 在支持纯文本正文引用穿透还原时，如果事实仅引用了短 quote（如“方案最高3分。”）而模型提取的规则包含后续编号材料（如“1、须提供合同首页... 2、须提供验收证明...”），盲目以短 quote 覆盖 `restored.text` 会导致编号材料被抹杀。

## 核心架构契约

### 1. SourceSpanResolver 兜底 spans 穿透机制
- 重载 `resolve(DocumentArtifact artifact, List<String> ids, List<SourceSpan> fallbackSpans)`。
- 当 artifact 的表格与 textBlocks 无法解析正文 locator 时，从 facts 绑定的 `fallbackSpans` 中按 locator/sourceId 提取，确保纯文本段落事实能成功还原 spans。

### 2. OriginalRuleRestoration 编号材料防截断守卫
- 纯文本正文引用无表格物理行边界约束，通过 `erasesNumberedMaterials` 正则检测：
  `(?m)^\s*(?:(?:\d+|[一二三四五六七八九十]+)[、.）\)\]]|[（\(\[]\d+[）\)\]])`
- 当模型规则包含后续编号条款，而原文 quote 仅为局部短引用且未覆盖编号材料时，置空 `restored.text`，回退至 `MODEL_CLAIM` 并标记 `FULL_RULE_UNIT_UNRESOLVED`，严禁截断擦除后续材料要求。

### 3. LogicalRuleSourceUnit 中间列续行与表头角色扩展
- 扩充表头 `ID`、`NAME`、`SCORE` 角色正则，支持常见招投标表头（条款号、条目、评审内容、评分内容、评审要素、指标名称、权重、满分等）以及空白/换行清洗。
- 重构 `isContinuationRow`：支持结构列为空且包含正文的中间列续行判定，并在 `logicalRow` 中双向合并续行，形成完整逻辑行单元。

### 4. Sidecar 跨页与表内续行动态规则列识别 (`converter.py`)
- **根除硬编码末列假设**：原 `_link_pdf_logical_rows` 硬编码要求末列非空且前缀全空，但真实招标文件表格常采用 `[序号, 评分项名称, 评分标准, 分值]` 排版，分值在最后一列，规则在倒数第二列。
- **表头语义感知**：新增 `_detect_pdf_table_rule_col`，扫描表头行识别规则列索引；
- **自适应续行判定引擎**：新增 `_is_pdf_row_continuation`，确保规则列前置列（序号/名称）与后置列（分值）均为空，且当前行与上一行规则列均有文字时稳定聚合；若表头缺失，自动根据单列文本自适应推断续行，彻底打通跨页截断规则（如项目 1392 跨页 9.1 细则）的 `logicalRowId` 缝合。

## 验证基线

- `document-converter-sidecar/tests/test_pdf_logical_rows.py`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnitTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestorationTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/golden/GoldenCasesReplayTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/application/FullRuleSourceReplayTest.java`

## 2026-11-04：项目43任务182的119分诊断（生产尚未修复）

- 现场：生产release `d74e0f644`，09:04:07启动；任务182（`8ff9b6f3-e5ca-4510-883a-6aa0fac08503`）11:10:05开始、11:17:21完成，`REVIEW_REQUIRED`。当前主线与该版本的backend/src/sidecar业务实现无差异。
- 输入：project_documents 195 → bid_file 63，2,270,318字节；下载原件SHA-256 `25b0d0b01744b65020e56dea59f632c3801b4fca99c48981427da1c1d0da3af5`。原PDF物理23—26页核对为报价30、综合实力40、技术30，共11个计分单元。任务未存artifact hash，文件身份由对象键、记录和日志尺寸关联，不能宣称有任务时点的字节哈希。
- 后端：17条候选、14个计分叶子、119分；当前生效8项来自旧任务149、合计61分。新任务没有自动覆盖旧项。

| 重复项 | 同一原文格 | 重复增加 |
|---|---|---:|
| 服务 | `doc0-p24-t0008-r6-c2` | 5 |
| 整体方案 | `doc0-p25-t0009-r1-c2` | 10 |
| 项目团队 | `doc0-p25-t0009-r2-c2` | 4 |

### 已证实的机制与实验

1. **候选身份漂移**：`SourceRecheckPlan.merge`用scheme/scope/section/code/parent作为key；同一原文逻辑项改名、换编号或父关系后进入追加分支。`ScoreFactChunkMerger`按含模型可变字段的完整元组去重，也不能统一同源重复表述。生产JAR隔离探针中，同源改名在两入口均由1变2；身份不变为1，不同来源为2。
2. **OCR不可用**：trace `b96b059042384e57bd29e889198c13dc`中22—26页两轮共10次OAuth请求全部`401 invalid_client`，没有成功OCR表格。设置enabled=true但未保存两项凭证，进程环境也无对应变量，走配置属性回退；不推断凭证过期、撤销或错配。
3. **失败告警丢失**：`BaiduTableArtifactEnricher`收集页失败warnings后，在`ocrTables.isEmpty()`直接返回baseline。生产JAR隔离模拟401确认返回warnings为空。
4. **差额闭合**：生产JAR的`ScoreUnitPolicy`计算真实17条快照为119；仅移除上述三条重复事实后为100。仅证明19分来自重复项，不代表名称、规则文本和父关系正确：原快照仍有7条sourceText为空及技术父项30/子项14的诊断。

### 边界与下一次修复

- 未保存分片/初次抽取/复核的原始模型响应，无法确定本次重复项最早在分片合并还是复核追加产生；两个入口的同源身份漏洞均已执行复现。
- PR !2948验证扫描页覆盖；!2952/!2953修分值与原文边界；!2954明确声明项目43生产未恢复；!2957仅修人工采纳。局部测试与发布健康不等于同一原件逐项验收。
- 修复边界：有效OCR配置及鉴权失败显式传播；两合并入口共用基于文档、方案、闭合逻辑评分单元的稳定身份，冲突候选留作审计但不能重复计分；同步父关系并验原文规则。不能仅按cellId去重，因为同一格可能包含多个合法子项。
- 验收固定原件、部署SHA和新任务ID，逐项验证11个计分单元的名称/分值/完整规则/来源/父关系，覆盖同源改名、跨页、父子同格、同名异源和OCR401反例。总分100单独不构成通过。
- 私有证据位于隔离任务工作区`.runtime/score43/evidence.json`，不提交原件或日志。后续用户授权“立刻修复”，已在隔离分支实现来源身份与诊断通路修复；生产配置、事实和正式标准未改，未部署。

### 隔离修复与验收状态

- `SourceFactIdentity` / `SourceFactDeduplication` 接入分片、来源复核和最终handler，限制同文档/方案/闭合评分单元的别名合并；空规则必须由原件名称、唯一分值列和完整单元共同证明。同来源跨层级时保留歧义，不把子项吞入父项别名。
- `BaiduTableArtifactEnricher` 全页失败仍保留安全错误码；结构化空结果、备用路由与summary重建均保留诊断，页面空结果和旧候选也能说明原因。通用OCR token有效但真实V2无权限，用户明确V2不能使用，本修复不依赖V2。
- 完整入口黄金回归同一断言：旧生产类14计分项119分，修复类11项100分。原始task182持久化事实配合实际完整Sidecar产物回放得到11项100分，但delivery_capacity的旧模型引文被改写，完整规则未获原件证明；不可把此回放当完整业务验收。
- 最新真实模型原件回放、全量影响域与人工审查结果记录于本任务spec及私有证据；代码修复、审查通过、部署和线上重算状态分别记录。


- 新原件真实模型按task182入口重放为11计分项100分；可逆br换行归一化后全部规则正文非空。版本化legacy冻结合并summary来源诊断，已补真实freeze边界RED。最终测试与审查状态以本任务验证记录为准，未部署。
