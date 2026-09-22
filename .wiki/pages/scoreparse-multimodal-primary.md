---
title: 评分标准多模态主召回与异常审核契约
space: engineering
category: reference
tags: [scoreparse, multimodal, validation, review]
sources:
  - specs/066-scoreparse-multimodal-primary/spec.md
  - specs/066-scoreparse-multimodal-primary/contracts/multimodal-score-facts.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFactValidator.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandlerTest.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/AiProviderDiagnostics.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/RunSummaryAssembler.java
backlinks:
  - .wiki/pages/_index.md
created: 2026-09-20
updated: 2026-09-22
health_checked: 2026-09-22
archive: false
---

# 评分标准多模态主召回与异常审核契约

## 主链路

1. Sidecar 将包含表格的完整页面渲染为图像，同时返回表格网格和原始单元格。
2. `MultimodalScoreFactExtractor` 一次输出章节、序号、父子项、分值、完整规则、页码、表格与单元格坐标、主客观类型。
3. `MultimodalScoreFactValidator` 以同一份原文资产核验评分方案总分和每项最高分，再检查必填字段、序号、父子关系、逐字引用、重复项及类型。模型声明总分、原文总分、叶子项合计互不替代。
4. 首次、一次紧凑复核和确定性回补均须原文核验通过；发布入口再次要求核验资格、文档级 `AUTO_APPROVED` 与非空正式候选。失败时只保存复核事实/证据，不覆盖原评分项和已有打分。

## 审核与总分边界

- 审核模式由“每次必审”改为“异常才审”。
- 单表无表内声明时，可使用文档声明总分；多表包含未知声明时，`declaredTotal=null`。
- `declaredTotal=null` 必须在任何正式项、任务状态和审核记录写入前阻断采纳。
- 前端显示“声明总分待确认”并禁用采纳，禁止回退成 100 分造成伪闭合。

## 不完整多模态召回的安全回补

- 首次多模态请求失败，或返回事实未通过证据及总分校验时，最多增加一次模型独立复核。复核输入仅包含评分表的原始单元格、页码和表格标识，不重复发送大批页面图片与完整 Artifact JSON；模型必须从零输出全部正式项而非只补差额。
- 复核结果仍走同一个 `MultimodalScoreFactValidator`。只有其完整通过才能自动发布；复核失败或不完整继续保留异常复核，禁止伪造分值闭合。
- 多模态模型返回的事实不完整时，不得把候选事实直接当作正式评分项，也不得把候选合计强行归一化到 100 分。
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
- `PublicationProof` 对应本次校验的事实与正式候选集；删改候选或无证明时 `StructuredTableResult.originalVerified()` 为假，发布入口不会调用 `persistItems` 或预计得分。完整、原文有据的模型结果及确定性回补仍可发布。
- 旧的“两张表 60+40 可发布”合成夹具原文只有局部小计而没有方案总分，现应转人工；另有明确原文 100 的正向多表与 JPA 对照，不能以禁用自动发布替代校验。

### 原文证据角色与标题层级（PR #5 2026-09-22 续修）

- 在有表头的评分明细中，评分项规则行的“满分”是单项上限，不是方案声明；即使 OCR 仅留下一个规则单元格，也根据表头与行位置判断角色。只有明确正文或独立汇总等方案级来源才提供总分，单项最高分校验继续读取该规则行。
- 原文 `subtotals` 与候选无关；模型首次、一次复核和确定性回补均通过同一 `OriginalScoreEvidencePolicy` 比较表内叶子合计。分表小计冲突不会抹去已经确认的方案总分，拒绝原因和定位保留在任务运行摘要。
- Markdown 评分标题按层级理解：父评分办法下的商务/技术评分标准是子章节，非独立方案；同级新方案或不同标包仍转人工。汇总表不产出正式项，正确 100/80 分以及完整多表结果仍允许自动发布。

## 主客观分类

- 客观：明确阶梯、证书、数量、金额、日期或计算公式。
- 主观：横向比较、酌情评价、优良中差等依赖评委判断的规则。
- 销售额、证书或公式即使包含描述文字，也不得仅因措辞复杂被判为主观。

## 可审计预计得分

- 证书与业绩必须返回具体命中记录、每个条件的证明和缺失证据。
- 禁止只返回数量汇总或“满分”结论；缺少有效期、金额、日期等必要字段时必须标记证据不足。

## 跨模块影响

- Sidecar 图像协议变化影响 `SidecarImageExtractor` 与模型输入。
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
