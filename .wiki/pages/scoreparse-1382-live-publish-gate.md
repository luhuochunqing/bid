---
title: 项目1382线上100分被0分指引和空模型总分否决
space: engineering
category: debugging
tags: [scoreparse, 1382, publication-gate]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreWeightEvidencePolicy.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/LiveScorePublicationGateTest.java
backlinks:
  - .wiki/pages/scoreparse-1382-post2767-runtime-rca.md
created: 2026-09-28
updated: 2026-10-02
health_checked: 2026-10-25
archive: false
---

# 项目1382线上100分被0分指引和空模型总分否决

## 结论

测试机项目 1382 在 2026-09-28 的三次重新解析里，叶子合计和原文总分都已经是 100，正式评分项仍是 9 月 27 日人工采纳的 91 分。发布门禁只要还剩一条违规，就不替换 `score_item`。

前几轮 PR 的全链路测试在进入解析器之前做了四件线上不会发生的事：删掉表号为空的事实、把引文改成短句、把模型总分写成 100、把复核标记成已经完成。所以测试能自动通过，页面不变。

## 线上共同否决与 Task 290 根因复盘

- 「见评审办法前附表」和报价公式说明被收成权重 0 的方案内事实，原文里没有「0分」。
- 抽取提示允许模型把 `declaredTotal` 留空；原文总分已是 100 时，留空仍被记成 `MODEL_TOTAL_MISSING`。
- 模型把「商务 / 技术 / 报价」写进 dimension，所引行的分值已经对上，仍被记成 `ITEM_SOURCE_SCOPE_MISMATCH`。
- 紧凑复核三次都在调用模型前抛 `IllegalStateException`，第一次抽取的结果被留下。
- **Task 290 关键阻断（2026-09-29）**：
  1. **父项编码歧义降级（0权重说明句 vs 15分正式父项）**：模型在抽取大纲说明文本与表格项时，将说明行（如“3.2.3(1) 商务评分标准说明...”）与正式父项同时赋予相同编码，由于原本按纯编码做唯一性校验，两者冲突触发 `\u0000AMBIGUOUS\u0000` 标记，导致所有商务子项在查找父项时降级失败；修复方案：在 `MultimodalFactParentCodeNormalizer` 中实施“正权重优先”防歧义规则。
  2. **0权重合同项与表外说明项范围逃逸**：`MultimodalExtractionReviewPolicy.assignScope` 因过早判断 `!fact.scopeStatus().isBlank()`，导致分片盖章的表外事实逃逸了 `OUT_OF_SCHEME` 兜底守卫；修复方案：前置范围守卫截断逃逸，同时在 `OriginalScoreQualificationPolicy` 中放行 0 权重 `OUT_OF_SCHEME` 项的严格逐字引文比对。
  3. **0权重非计分项证据误报**：`OriginalScoreEvidencePolicy` 对 0 权重事实放宽权重单元格强校验，避免非计分项误报单元格缺失。

91 分是任务 275 的 10 条：报价父项 40 和子项 28、12 一起计入，其余只有网点 3、财务 1、两个服务方案 3 和 4。

## 发布规则与 Task 290 回放基准

非正权重且不是父项的事实仍记录资格，但不进入可发布集合，也不能否决已闭合的叶子。原文总分已核验时，模型总分为空不再否决；模型总分和原文总分不一致仍然拒绝。只有 dimension 是父类词、并且所引行分值与声明权重一致时，才不记范围不匹配。具体项名对到另一行仍然拒绝。发布名称取原文标题「名称（N分）」，去掉前面的父类词。

端到端验收基准以现场从服务器提取的 Task 290 真实 51 项无清洗事实为准（`Project1382FullPipelineIntegrationTest.verifyTask290LiveOnlineFacts`），验证通过全链路 `AUTO_APPROVED` 判定，14 项有效评分项（13 项叶子项 + 1 项扣分清单规则项，总分 100.00 分）自动持久化落库。

## Task 291 四大阻断根因（2026-09-29，commit 41bdd3447）

PR #2800 部署后，Task 291 重新解析仍停留在 91 分。服务器日志（traceId: `05e7fd5ad85e4d0a978fe093c2135567`）实测定位到新的四条阻断链：

### 根因 1：数字与汉字交界强插空格（EvidenceText.java）

`EvidenceText.java` 换行规整算法仅在"连续两个汉字间"去换行，数字 `'8'` 不是汉字，导致 `"28\n分"` 被强制插入空格变成 `"28 分"`，Fact 15（28分项）单元格绑定失败。
- **修复**：扩展 `noSpace` 谓词，数字与汉字交界不强插空格；`TextPrimaryEvidenceBinder` 增加空白字符归一化比对 fallback。

### 根因 2：正规计分项引文暂缺被划入 OUT_OF_SCHEME（MultimodalExtractionReviewPolicy.java）

PR #2800 移除的前置短路使未绑定引文的正规计分项（编码如 `3.2.3(3)_1`）被粗暴划入 `OUT_OF_SCHEME`，28分项脱落出计分池。
- **修复**：正规评分编码项保护——带 `_N` 后缀编码的项在引文暂缺时保持 `IN_SCHEME`，不触发范围逃逸。

### 根因 3：维度别名前缀剥离错误（OriginalScoreEvidencePolicy.java）

`publishedDimension` 计算时错误地剥离了"运营经验"、"财务状况"前缀，导致维度变成空串，与行内标题匹配失败，产生 2 条 `ITEM_SOURCE_SCOPE_MISMATCH`（运营经验 & 财务状况）。
- **修复**：移除错误的 `财务|运营` 前缀剥离逻辑，保留项名完整性。

### 根因 4：复核锚点序列化超预算崩溃（ScoreAnchorPrompt.java）

8个未决锚点序列化 8,752 字符超出 8,000 字符硬预算，`ScoreAnchorPrompt` 抛出 `IllegalStateException`，复核通道完全中断，降级保留首次失败结果。
- **修复**：安全截断替代抛异常——超预算时截断锚点列表并记录 WARN 日志，复核正常进行。

### 关键教训

- `diff "28\n分"` 与 `"28 分"` 是肉眼难察觉的空格差异，但足以导致 exact-match 引文绑定完全失败；测试必须覆盖数字汉字交界边界。
- `merge-base..HEAD` 与 `origin/main..HEAD` 在多 commit 分支上计算的 diff_hash 不同，entry-gate-check.py 使用 merge-base 方式。
- 金标门禁（SCOREPARSE_GATE_SKIP）仅在当次 fix 任务中豁免，后续需补充 golden fixture。

## Task 292 阻断根因与现场闭环（2026-09-29）

PR #2802 部署后，用户重新触发 Task 292（54 条模型事实），叶子总分达到 100 分，但因 `decision=REVIEW_REQUIRED, violations=4` 触发安全保护未自动落库，页面仍显示 91 分。

### 根因 1：根父项“分值构成”总分声明维度未覆盖（`3.2.1` 100分）

模型提取 `3.2.1`（分值构成，100分），引用单元格 `doc0-p33-t0009-r18-c1`（`分值构成\n（总分100分）`）。
- `OriginalScoreWeightEvidencePolicy.declaredDimensionSubtotal` 原本维度匹配正则仅匹配具体维度（`商务|技术|报价|价格`），未覆盖“综合/总分/合计/满分/分值构成”这类总体章节维度，导致 100 分根父项被误判 `ITEM_WEIGHT_SOURCE_UNKNOWN`。
- `OriginalScoreEvidencePolicy.sectionCategory` 缺少“方案/实施/服务”分类，导致维度匹配报 `ITEM_SOURCE_SCOPE_MISMATCH`。
- **修复**：`declaredDimensionSubtotal` 中的 `dimPattern` 增加 `(?:综合|总分|合计|满分|分值构成)`；`sectionCategory` 支持服务/实施/方案。

### 根因 2：“针对办公用品的综合服务方案”同义词别名缺失（`3.2.3(2)_1` 10分）

模型提取事实维度为“实施方案”，原文单元格为“针对办公用品的综合服务方案（10分）”。
- **修复**：`OriginalScoreEvidencePolicy.matchesDimensionAlias` 增加 `((dim.contains("实施") || dim.contains("方案")) && normSource.contains("服务方案"))` 映射。

### 根因 3：单个根父项误报序列断号 `SEQUENCE_GAP`

- **修复**：`MultimodalScoreFactValidator.validateSequences` 增加 `if (rows.size() <= 1) return;` 保护，单项孤立或无同级项时不判定序列断号。

### 验收证据

现场导出 Task 292 全部 54 条真实事实（`task-292-live-facts.json`），在 `Project1382FullPipelineIntegrationTest.verifyTask292LiveOnlineFacts` 中端到端重放，确证 `decision=AUTO_APPROVED`, `violations=0`, `leafTotal=100.0`, 13 项有效叶子评分项全部自动入库。

## Task 291 项名改写与通用引文闭合规则（2026-09-29）

!2802/!2804 部署后 Task 293 侥幸 AUTO_APPROVED 落 100 分；但 Task 291 线上真实 50 项忠实重放仍剩 1 条 `ITEM_SOURCE_SCOPE_MISMATCH`（`3.2.3(3)_2` 项名被改写为"非一级集采商品上架折扣率"，原文"非一级集采商品价格分（满分12分）"）。逐对同义词表（`matchesDimensionAlias`）永远落后模型改写一轮。

- **第 7 轮评审定稿（2026-10-02）：撤除自动恢复，PR !2805 转为验证资产**。六版补丁（endsWith 容差→逐字全等→标点黑名单→边界白名单→结构回溯→引文声明验证）暴露同一认识论根因：**模型引文无法自证模型项名的身份与完整性**——凡"从原文再裁一次名称"的规则本质仍是字符分类，截断向量无穷；继续打补丁等于降低身份校验强度。已删除 `SourceItemName` 整套机制（mapper 自动覆盖项名、`identityRecoveredFromSource` 免检、池级守卫全部回退 origin/main）。**保留资产**：Task291 线上 50 项真实夹具与全链路回放（列入 `check-scoreparse-root-cause.sh` 执行名单）、历史反例断言、Sidecar Python 探测、recheck 完整堆栈日志。**已知局限钉死为正确结果**：`3.2.3(3)_2` 身份不可证 → 恰好 1 条 `ITEM_SOURCE_SCOPE_MISMATCH`、`REVIEW_REQUIRED`、0 可发布候选，进人工复核。**下一方向**：以原文评分表完整标题/条目结构建立项身份，模型只提候选，对应关系无法确定的项进人工确认；先验证"每项对应正确原文"再谈自动发布。
- **重放忠实性**：回放构造必须用模型原始输入形态（空 schemeId/scopeStatus）；此前 289/290 重放塞假值导致 binder 从未被真正验证。
- **recheck 可诊断性**：catch 输出完整堆栈（原先只打类名，自愈崩溃无法归因）。
- **撤除后严格审查两项 P2 门禁修复（2026-10-02）**：① `pre-push-gate.sh` FBR-I1 回放段旧写法 `|| true` 吞 Maven 退出码 + 读残留 surefire 旧报告，损坏 POM+旧绿报告场景误报 PASS——修复为先删本次目标类旧报告、检查 mvn 真实退出码、报告仅作二次防御；② `CrossFormatDocumentSourceReplayTest.sidecarPythonUsable` 旧写法先 `readAllBytes` 等 EOF 令 15s `waitFor` 失效（18s 慢 Python 实测 18.4s 返回 true）——修复为输出重定向 DISCARD、限时 waitFor、finally destroyForcibly（实测 15.0s 返回 false）。教训：bash 中文消息里 `$var` 紧贴全角标点会被 UTF-8 locale 解析进变量名（`set -u` 炸 unbound），一律写 `${var}`。
- 详见 lessons-learned.md §418。
