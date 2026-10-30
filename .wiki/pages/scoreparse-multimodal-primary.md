---
title: 评分标准文本分片主召回与异常审核契约
space: engineering
category: reference
tags: [scoreparse, text-extraction, chunking, validation, review]
sources:
  - specs/066-scoreparse-multimodal-primary/spec.md
  - specs/066-scoreparse-multimodal-primary/contracts/multimodal-score-facts.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFactValidator.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandlerTest.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ChildAggregation.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreUnitPolicy.java
  - backend/src/test/resources/scoreparse-golden/case-page-split-parent-child.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/AiProviderDiagnostics.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/RunSummaryAssembler.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreTextChunker.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreFactChunkMerger.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/TenderExecutionSource.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/TextPrimaryEvidenceBinder.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreQualificationPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/TextPrimaryScoreFactMapper.java
  - backend/src/test/resources/scoreparse-golden/case-long-text-scoring-table.md
  - backend/src/test/resources/scoreparse-golden/case-long-text-scoring-table.expected.json
  - backend/src/test/resources/scoreparse-golden/case-text-primary-multi-document.md
  - backend/src/test/resources/scoreparse-golden/case-text-primary-multi-document.expected.json
  - backend/src/test/resources/scoreparse-golden/case-docx-run-boundary-score.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalFactParentCodeNormalizer.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreQualificationPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringSchemePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewFactMapper.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewTotalPolicy.java
  - backend/src/test/resources/scoreparse-golden/case-project-1382-scheme-eligibility.expected.json
  - backend/src/test/resources/scoreparse-golden/case-project-1384-logical-scheme.expected.json
  - specs/071-scoreparse-scheme-eligibility/spec.md
  - specs/071-scoreparse-scheme-eligibility/contracts/score-parse-review-schemes.md
  - specs/072-scoreparse-partial-adoption-ux/spec.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreReviewAdoptionPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java
  - src/views/Project/stages/components/ScoreCandidateReviewCard.vue
  - document-converter-sidecar/converter.py
  - document-converter-sidecar/table_extractor.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/DualTrackScoreExtractor.java
  - backend/src/test/resources/scoreparse-golden/case-1392-tree-aware.md
backlinks:
  - .wiki/pages/_index.md
  - docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md
created: 2026-09-20
updated: 2026-09-25
health_checked: 2026-10-25
archive: false
---

# 评分标准文本分片主召回与异常审核契约

## 主链路

1. 评分识别从招标文件完整可提取文本开始；不将页面渲染成图像。Sidecar/文档转换层提供的表格网格和原始单元格仍作为结构化来源证据。
2. `ScoreTextChunker` 将完整招标正文按有重叠的窗口切分，优先在换行处断开并保护 Unicode 代理对；文字主链路只把正文分片交给模型，不把完整 `DocumentArtifact` 放入每个 prompt。每个实际模型请求限制在 24,000 字符以内。
3. 每段识别章节、序号、父子项、分值、完整规则、页码、表格与单元格坐标、主客观类型；`ScoreFactChunkMerger` 只合并完全相同的事实键，保留较高置信度，并拒绝互相冲突的声明总分。
4. `MultimodalScoreFactValidator` 在文字抽取完成后，以合并后的 Sidecar 原文资产核验评分方案总分和每项最高分，再检查必填字段、序号、父子关系、逐字引用、重复项及类型。模型声明总分、原文总分、叶子项合计互不替代。
5. 首次完整分片识别、一次完整分片复核和确定性回补均须原文核验通过；发布入口再次要求核验资格、文档级 `AUTO_APPROVED` 与非空正式候选。失败时只保存复核事实/证据，不覆盖原评分项和已有打分。

## 多文件正文与后校验证据

- `InitiationTenderTextResolver.findTenderDocuments()` 按稳定顺序读取全部招标文件，`TenderExecutionSource` 同时保留每个正文源和全部文件句柄；解析结束统一释放所有句柄，不再只取最新文件。
- 文字模型先识别父项、子项、原文权重、最高分、规则和 `quotedText`；不折算权重，也不把父项与子项预先合并成一个新分值。
- `TextPrimaryEvidenceBinder` 在模型返回后才调用 Sidecar，给各文件的表格/单元格加文档前缀并按逐字引用、页码回指。无法回指时保留候选但降级人工复核。
- 结构化证据不可用时不走确定性自动回补；原文追溯和审核保护继续生效。
- 合成回归夹具 `case-text-primary-multi-document` 锁定多文件正文、父项组织和四个叶子计分单元完整保留。

## 审核与总分边界

- 审核模式由“每次必审”改为“异常才审”。
- 单表无表内声明时，可使用文档声明总分；多表包含未知声明时，`declaredTotal=null`。
- `declaredTotal=null` 必须在任何正式项、任务状态和审核记录写入前阻断采纳。
- 前端显示“声明总分待确认”并禁用采纳，禁止回退成 100 分造成伪闭合。

## 长文本完整识别与安全回补

- 初次抽取与独立复核均遍历所有文本分片，不因文件长度静默截断；分片间保留重叠内容以降低跨边界表格/规则遗漏，事实合并后由全局 validator 校验。
- 任一分片请求失败、无法为完整结构化证据留出至少 2,000 字符文本上下文，或合并事实声明总分冲突时，走既有人工复核路径，不把部分结果当成完整正式评分项。
- 每个成功分片请求后更新解析任务进度时间，避免长文档多个串行请求被既有超时扫描误判为失联；不改变任务总超时配置。
- 初次或复核合并结果未通过证据及总分校验时，保持 `REVIEW_REQUIRED`；禁止把候选事实直接当正式项，也不得把候选合计强行归一化到 100 分。
- `MultimodalStructuredExtractionHandler` 会把同一份 `DocumentArtifact` 和标书评分章节文本交给 `ScoreFactShadowPipeline`；回补即使自报 `AUTO_APPROVED`，仍要用 `OriginalScoreEvidencePolicy` 核验原文总分、单项最高分与叶子项合计，才能继续正式入库链路。默认 100 分、错误作用域及分值证据冲突不能获准发布。
- 确定性回补仍未闭合时保留 `REVIEW_REQUIRED`，前端候选复核卡必须同时展示序号、评分项、权重、评分类别、标书原文与详情，避免审核者只能看到总分或摘要。
- 回归夹具：`backend/src/test/resources/scoreparse-golden/case-multimodal-incomplete-recall-fallback.{md,expected.json}`，并在 `GoldenCasesReplayTest#cases()` 注册。

### 原文约束的适用范围

- 原文总分须有评分方案/标包作用域和单元格或正文定位；同值重复不冲突，作用域不明、同作用域异值、只有模型声明值时转人工。商务/技术/报价小计、其他标包和非评分表数字不能充作整份方案总分。
- 单项最高分优先按同一评分项的分值列、上限语句、标题分值核验；“每证 2 分”的增量和“100 万元”的金额门槛不是最高分。名称、规则、分值允许分处同一行的不同单元格，来源不得指向其他评分项。
- `BigDecimal` 直接作数值比较，`10` 与 `10.0` 等值；未知值序列化为 `null`，不以 100 补空。复杂合并/公式无法清楚定位时保存事实并转人工。
- 当前回归使用合成资产和已有金标；真实项目 1386 的原文件、部署提交、模型响应和正式发布结果仍需授权环境单独验证，不把本次单测等同于线上修复。

### 方案范围与物理分表门禁（PR #5 续修）

- `OriginalScoringContext.resolve` 在任何模型响应之前，只从当前原始 `DocumentArtifact`、正文和来源章节确定方案总分及定位。模型只返回商务 20、返回商务 20+技术 80 或返回空事实时，同一份原文汇总表仍是 100 分。逗号连接句与标题下换行声明保留方案上下文；多个独立标包而无目标范围时转人工。
- 不被模型引用的独立汇总表仍参与原文总分核验；商务/技术/报价分表的合计仅为局部小计。`isScoreTable` 影响确定性提取，不决定哪些原文总分证据可被忽略。
- `OriginalSourcePublicationGuard` 不再对多表抛弃正文，也不逐表将局部通过当成方案通过：它先核验每项最高分及可确认的小计，再对当前全部叶子与已确认方案总分做一次闭合。成功时文档级 `declaredTotalScore` 投影为原文方案总分，不沿用第一张表的小计。
- 正文评分项可以使用 `text:line[...]` 原文定位；多张物理评分表没有表内小计时，只有在正文明确总分且全部末级分值闭合时才允许自动发布。
- `PublicationProof` 对应本次校验的事实与正式候选集；删改候选或无证明时 `StructuredTableResult.originalVerified()` 为假，发布入口不会调用 `persistItems` 或预计得分。完整、原文有据的模型结果及确定性回补仍可发布。
- 旧的“两张表 60+40 可发布”合成夹具原文只有局部小计而没有方案总分，现应转人工；另有明确原文 100 的正向多表与 JPA 对照，不能以禁用自动发布替代校验。

### 原文证据角色与标题层级（PR #5 2026-09-22 续修）

- 在有表头的评分明细中，评分项规则行的“满分”是单项上限，不是方案声明；即使 OCR 仅留下一个规则单元格，也根据表头与行位置判断角色。只有明确正文或独立汇总等方案级来源才提供总分，单项最高分校验继续读取该规则行。
- 原文 `subtotals` 与候选无关；模型首次、一次复核和确定性回补均通过同一 `OriginalScoreEvidencePolicy` 比较表内叶子合计。分表小计冲突不会抹去已经确认的方案总分，拒绝原因和定位保留在任务运行摘要。
- Markdown 评分标题按层级理解：父评分办法下的商务/技术评分标准是子章节，非独立方案；同级新方案或不同标包仍转人工。汇总表不产出正式项，正确 100/80 分以及完整多表结果仍允许自动发布。

### 文本分片覆盖、页码溯源与评分单元

- 每个文本分片都必须进入初次识别与复核；提交给模型的每次正文请求受 24,000 字符上限约束，全部分片合并后再用 Sidecar 做结构化回指与统一校验。原始页码、表格和单元格位置继续用于证据追溯；页图覆盖不再是评分识别的完整性门槛。
- 分片返回的声明总分属于文档级元数据；若不同分片返回不同值，合并器将总分标记为未知但保留全部评分事实，由下游原文证据校验决定是否进入审核或正式链路，禁止因总分冲突丢弃整批事实。
- 父项分值仅在原文明确声明“取子项之和”等聚合关系时，作为组织/校验关系；无显式规则或关系不明时禁止把父项与子项重复累加，也不把子项分值直接冒充父项分值。
- 正式计分单元按明确规则聚合后的叶子项计算。原文引文、页码与审核保护仍然生效，但完整性与总分闭合要按该计分单元口径校验。
- 合成回归夹具 `case-page-split-parent-child` 锁定跨页商务/技术评分组与叶子分值闭合；`case-long-text-scoring-table` 锁定长篇正文后仍完整召回末尾评分表；`ChildAggregationTest` 锁定关系策略。长文本分片与事实合并由 `ScoreTextChunkerTest`、`ScoreFactChunkMergerTest` 及抽取器测试覆盖。真实招标文件仍按既有脱敏纪律留在仓库外。

## 主客观分类

## DOCX 原文边界与分片父项

- DOCX 转换器可能把“本项目总分为 100 分”拆到相邻 run 或换行中；总分解析先统一空白，再只接受带“本项目/本标包/评分办法/评标办法”作用域的明确总分。
- 模型引用允许转换器造成的换行、非断行空格差异，但仍必须在完整原文中连续回指，原始引用文字不改写。
- 分片缺少父标题时，仅清理可证明的结构前缀父引用；任意其他悬空父编号仍转人工复核。
- 合成夹具不替代部署后真实项目重解析验收。

- 客观：明确阶梯、证书、数量、金额、日期或计算公式。
- 主观：横向比较、酌情评价、优良中差等依赖评委判断的规则。
- 销售额、证书或公式即使包含描述文字，也不得仅因措辞复杂被判为主观。

## 可审计预计得分

- 证书与业绩必须返回具体命中记录、每个条件的证明和缺失证据。
- 禁止只返回数量汇总或“满分”结论；缺少有效期、金额、日期等必要字段时必须标记证据不足。

## 跨模块影响

- 文档转换层的网格与原始单元格继续提供引文坐标；评分主识别采用完整文本分片，无需页面图像输入。
- 解析决策影响正式评分项写入和复核 DTO。
- 方案总分来自原文方案范围，不来自模型返回的表集合；单项“满分”与商务/技术/报价局部小计不得升格为方案总分。首次、一次复核与确定性回补共用同一原文上下文，异常复核也保留已核验总分。不同标包评分范围不得混合，无关资格表不参与方案歧义判定。
- 证书/业绩匹配 DTO 变化影响预计得分展示与审计。
- 本轮验收新增 `OriginalScoreEvidenceRoleAndHeadingTest` 与 `ScoreTotalEvidencePublicationAcceptanceTest`：未知总分保持 `REVIEW_REQUIRED`，明确正文总分与子章节可进入 `AUTO_APPROVED`；真实项目1386原文件、同配置模型响应与人工确认结果仍不可得，保持未验证。
- UI 编辑后的序号、维度、规则、父项和主客观类型必须原样回传采纳接口。
- 多模态召回与确定性回补的差异必须记录为可审计日志；生产问题排查需同时核对页面、候选/正式接口和解析决策状态。

## AI Provider 诊断遥测与凭据安全契约

- **诊断信息载体与定位**：新增不可变领域 Record `AiProviderDiagnostics`，采集激活的 Provider 代码、模型名、调用延迟（毫秒）、执行状态、异常类与脱敏异常信息，并通过 `RunSummaryAssembler` 透出至 `run_summary.aiProviderDiagnostics`。
- **凭据脱敏安全红线**：严禁在 `errorMessage` 或诊断中透出未经脱敏的敏感凭据。所有针对 `sk-*`、`Bearer *` 或常见 Token 格式均由 `AiProviderDiagnostics.sanitize()` 正则统一脱敏为 `sk-***` / `Bearer ***`。
- **向后兼容与键序**：RunSummary 装配严格保持原有 11 项 JSON 键物理顺序，`aiProviderDiagnostics` 仅作为可选字段置于 `sourceDiagnostics` 之后透出，且 `llmModel` 为空时自动推导回填。

## 2026-09-24 采纳边界

单表评分表未能抽取原文声明总分时，人工确认采纳可以使用候选叶子项合计作为正式总分；多表存在冲突声明总分时仍必须阻止采纳。后端通过 adoptionStatus 将 `SINGLE_UNKNOWN_TOTAL` 与 `CONFLICTING_TOTAL` 传给前端，避免页面把两种状态显示为同一类待确认。

## 2026-09-24 方案资格、逻辑方案与跨表计分

- 评分范围校验是资格门槛，不是提示文案：只有原文引用有效且属于当前逻辑方案的事实才能进入默认候选、候选总分和正式采纳。方案外、来源未验证的事实仍留在审计快照。
- 评分方案身份先于事实合并。后续章节的履约考核事实不能与当前方案计分；同一个方案可以跨章节明细和多张物理表，物理表数本身不产生多个方案。
- 方案总分必须由原文段落或表格单元格回指；无证据为 `UNKNOWN`，同作用域不同值为 `CONFLICT`，同方案跨多表仍可有一个已验证总分。不同方案分开显示并显式选择。
- 父子关系按方案内稳定身份解析。跨物理表且标题不同的父子项，只在同方案、同范围且父编号唯一时自动连接；歧义保留复核。
- 聚合关系不可混淆：`SUM` 累计有效子项，`MUTUALLY_EXCLUSIVE` 取备选最大分，未知关系不生成可采纳的总分。
- 1382/1384 脱敏真实项目形状回放分别固定后章排除和同方案跨表闭环；它们不替代测试部署后的真实项目重解析。

## 2026-09-25 真实 DOCX 重解析发现的证据断点

- MarkItDown 会把 Word 强调权重写成 Markdown `**30**`；证据绑定和资格核验必须使用一致的 Markdown 强调归一化，同时保留原文行偏移。归一化只消除格式标记与空白，不放宽词句内容。
- DOCX 可能把同一评分行拆到相邻物理表。当前 Sidecar v2 逐物理表输出，Java 资格引用要求来源表/单元格可核实；完整引用跨越表边界时应保持未验证，不能用模糊匹配或相似分值自动放行。
- 真实项目 1384 重解析已核实方案总分 100，但资格候选只有 8 项、56 分。报价 30 分受 Markdown 强调格式影响；技术 10 分模型引用有文字改写；团队 4 分来源跨两张物理表。真实验收必须记录 `/review`、`/items` 和页面结果，Golden 回放不能替代。
- 后续修复优先扩展转换契约，让逻辑评分行可关联有序原文片段，再让模型事实引用这些来源；精确出处仍是资格门槛。

## 2026-09-25 人工采纳部分候选与校验信息展示

- `REVIEW_REQUIRED` 表示事实还需人工核对，不等于前端必须禁用人工采纳；已验证方案总分为 100、当前候选为 91 时，允许操作者核对后采纳 91，并将实际采纳合计保存为 91。
- 采纳资格仍要求当前逻辑方案有效、总分证据无冲突且候选来源通过既有校验；候选合计超过原文声明总分必须在持久化前拒绝。任何单项负分也必须拒绝，避免通过正负抵消绕过上限。
- 校验诊断、内部 locator、事实/方案 ID 和资格 JSON 属于机器证据，不应直接输出给业务用户。前端将已知差异翻译为中文说明，来源区域只显示可读原文引句。
- 91/100 人工采纳和 UI 文案的本地回归通过；测试站真实 DOCX 重解析与用户界面验收仍需合并部署后完成。

## 2026-09-25 PDF OCR 引文定位边界

- Tesseract TSV 会把连续汉字分成 token，并在 token 间插入空格；Sidecar 必须保留原始 OCR 文本、物理页和 bbox。
- `TextPrimaryEvidenceBinder` 只在 OCR 匹配时折叠相邻汉字间空格。英文/数字词间空格仍是原文差异；不做语义相似或标点模糊匹配。
- OCR locator 仍须唯一精确引用、同一评分方案、高置信度和有效 bbox；重复引用不允许用模型页码提示消歧。
- 合成中文扫描件和跨层绑定回归通过。真实 task 267 测试站重解析因后端 Flyway V1218 故障仍未验收。

## 项目 1384 真实 DOCX 入口契约

- PR #2757 的方案资格逻辑遇到 Markdown 加粗标题/总分与无 `sourceSection` 的表格时，实际项目仍会得到 0 条合格候选；根因证据、误归属风险和真实文件回归判据见 [[scoreparse-1384-docx-scheme-contract-rca]]。

## 项目 1382 真实 PDF 入口契约

- 与 DOCX 入口同型、但致病点不同：PDF 表格此前被硬编码 `"sourceSection": ""`（只有 OCR textBlocks 走章节识别），导致表格派生事实无方案锚点 → `SOURCE_SCHEME_NOT_VERIFIED`；同时目录点线行会被当成方案根 → `ORIGINAL_SCHEME_AMBIGUOUS`。根因链、`pdf-table-v2` 语义变更与 D6 残留风险见 [[scoreparse-1382-pdf-scheme-gate-rca]]。
- 消费方注意：`DocumentArtifact` 的 `extractionVersion` 升级为 `pdf-table-v2`，此后 PDF 表格的 `sourceSection` 不再是恒空串。

## 2026-10-28 百度表格 OCR 增量补漏与章节名称继承

- **增量补漏（Missing-Only Enrichment）**：当 Sidecar 已经成功抽取部分评分表格（如第 13、15 页）而仅在特定复杂续表页（如第 14 页）发生跳页时，`BaiduTableArtifactEnricher` 仅针对评分表区间内缺失的候选页精准调用百度 OCR。绝不全量无差别覆盖已有健康表格，防止耗时过长触发前端/网关网络超时（Network Error）以及表格被破碎化切割。
- **章节名称动态继承**：百度 OCR 补齐的表格其 `sourceSection` 绝不得硬编码特定章节（如“第三章”），而必须优先继承自邻近已识别表格，或通过 PDFBox 实时提取当前页所在的真实章节大标题（如“第四章 评审办法”）。避免因章节名失配导致表格被 `OriginalScoringSchemePolicy.owns` 拒绝，防止残余孤立数字被误判为方案声明总分。

## 2026-10-30 双轨架构树形感知与父子防双计

- **树形感知分离（separateTree）**：在双轨双保险分流器（`DualTrackScoreExtractor`）中引入树形结构感知，结合 `isParentOnlyDim` 标识、子项 `parentDim` 引用及 `HierarchicalCodeTokenizer` 编号层级前缀推导，将平铺候选集精确拆分为“父级大类容器（parentCandidates）”与“末级细则（leafCandidates）”。
- **父子自洽与防双计**：
  - 若父大类声明了总分（如技术标 70 分），其直接子项细则分值之和必须精确自洽等于父项分值（容差 ±0.01），不自洽时立即触发降级老通道；
  - 计算主通道总分与放行时仅累加末级叶子项，彻底根除 30+70+10+20+40+30=200 分的双计假阳性；
  - `ScoreCandidatePostProcessor` 在主通道命中后将有效候选集纯化为清洗后的叶子项，使后续权重校验、大模型全局归并跳过与人工复核展示全链路畅通。

## 2026-10-30 异常静默吞没根治与可观测性保障

- **消灭排障黑洞**：在 `LegacyTextScoreExtraction` 与 `MultimodalStructuredExtractionHandler` 中，废除直接吞异常或仅打印类名（如 `exception.getClass().getSimpleName()`）的防御性静默代码。
- **全栈日志留痕**：
  - 首次模型失败打 `WARN`，复核或全局失败打 `ERROR`，且强制输出完整异常类名、异常 message 以及堆栈追踪（stack trace）；
  - `StructuredTableExtractionHandler.markIncomplete` 输出含 `taskId`、`projectId`、`fileName` 与 `failureReason` 的 `WARN` 日志；
  - 生产排查 1135 / 1136 结构异常时可直接通过 journalctl 获取完整错误根因（如网络超时、502 状态、JSON 序列化或越界）。



