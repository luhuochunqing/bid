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
backlinks:
  - .wiki/pages/_index.md
created: 2026-09-20
updated: 2026-09-21
health_checked: 2026-09-21
archive: false
---

# 评分标准多模态主召回与异常审核契约

## 主链路

1. Sidecar 将包含表格的完整页面渲染为图像，同时返回表格网格和原始单元格。
2. `MultimodalScoreFactExtractor` 一次输出章节、序号、父子项、分值、完整规则、页码、表格与单元格坐标、主客观类型。
3. `MultimodalScoreFactValidator` 只做确定性验证：必填字段、序号连续、总分闭合、父子分值、单元格逐字引用、重复项和类型枚举。
4. 全部通过才自动写正式评分项；任一失败则保存完整候选、差异和证据，进入异常复核。

## 审核与总分边界

- 审核模式由“每次必审”改为“异常才审”。
- 单表无表内声明时，可使用文档声明总分；多表包含未知声明时，`declaredTotal=null`。
- `declaredTotal=null` 必须在任何正式项、任务状态和审核记录写入前阻断采纳。
- 前端显示“声明总分待确认”并禁用采纳，禁止回退成 100 分造成伪闭合。

## 不完整多模态召回的安全回补

- 首次多模态请求失败，或返回事实未通过证据及总分校验时，最多增加一次模型独立复核。复核输入仅包含评分表的原始单元格、页码和表格标识，不重复发送大批页面图片与完整 Artifact JSON；模型必须从零输出全部正式项而非只补差额。
- 复核结果仍走同一个 `MultimodalScoreFactValidator`。只有其完整通过才能自动发布；复核失败或不完整继续保留异常复核，禁止伪造分值闭合。
- 多模态模型返回的事实不完整时，不得把候选事实直接当作正式评分项，也不得把候选合计强行归一化到 100 分。
- `MultimodalStructuredExtractionHandler` 会把同一份 `DocumentArtifact` 和标书评分章节文本交给 `ScoreFactShadowPipeline`；只有确定性管道产出 `AUTO_APPROVED`、叶子项非空且声明总分闭合时，才用完整回补结果继续正式入库链路。
- 确定性回补仍未闭合时保留 `REVIEW_REQUIRED`，前端候选复核卡必须同时展示序号、评分项、权重、评分类别、标书原文与详情，避免审核者只能看到总分或摘要。
- 回归夹具：`backend/src/test/resources/scoreparse-golden/case-multimodal-incomplete-recall-fallback.{md,expected.json}`，并在 `GoldenCasesReplayTest#cases()` 注册。

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
- 证书/业绩匹配 DTO 变化影响预计得分展示与审计。
- UI 编辑后的序号、维度、规则、父项和主客观类型必须原样回传采纳接口。
- 多模态召回与确定性回补的差异必须记录为可审计日志；生产问题排查需同时核对页面、候选/正式接口和解析决策状态。
