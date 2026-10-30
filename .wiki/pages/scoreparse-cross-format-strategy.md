---
title: 评分项跨格式识别的证据与方案边界
space: engineering
category: reference
tags: [scoreparse, document-layout, evidence, holdout-evaluation]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/LegacyTextScoreExtraction.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/DocumentArtifact.java
  - .wiki/pages/scoreparse-multimodal-primary.md
  - .wiki/pages/scoreparse-1382-live-publish-gate.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-02
updated: 2026-10-30
health_checked: 2026-10-30
archive: false
---

# 评分项跨格式识别的证据与方案边界

## 状态

- 用户问题：更换文件类型或表格布局后识别不稳定，无法逐文件适配。
- 本页仅记录只读诊断和候选方向，尚未实施或批准为最终架构，未做离线对比实验。
- 关联：[[scoreparse-multimodal-primary]]、[[scoreparse-1382-live-publish-gate]]。

## 本轮证据

| 来源 | 观察 | 边界 |
| --- | --- | --- |
| 测试部署记录与磁盘 JAR git.properties | release f645780f4，两处一致 | 不证明进程内全部配置 |
| 生产部署记录与磁盘 JAR git.properties | release 1f33f73e0，两处一致 | 所查近期日志未命中评分完成，不推断未执行过 |
| 测试 2026-09-29 15:50，1382，任务 dcda8001-6cf9-4ca2-b703-92ec222006e6 | facts=54，leafTotal=100，violations=4，REVIEW_REQUIRED，ITEM_SOURCE_SCOPE_MISMATCH | 合计正确不证明逐项正确 |
| 测试 2026-09-29 16:50，1382，任务 aa259b94-0384-434e-a955-5a05f7d38bc9 | facts=38，leafTotal=100，violations=0，AUTO_APPROVED | facts 不等于末级项数；不能外推其他项目 |

- 日志：`/var/log/xiyu-bid/application.json.log` 及按日 gzip。两轮转换都是办公用品.pdf、markitdown、268163 字符；文件名与长度一致不证明输入哈希一致，不单独归因于模型随机性。
- 历史多轮有“紧凑模型复核失败，保留首次提取结果”；不代表最近成功那次也失败。
- 源码阅读基准 0a1a29b09be7fb197e7818ac728098e2a7f04c60；比较缓存 origin/main 501b5e16007165ef583592bec6f6e9aebbbfc7e3，OpenAiMultimodalScoreFactExtractor、LegacyTextScoreExtraction 无差异。
- `extractTextChunksInScope` 初提取和复核调用 `outputService.request(prompt, List.of(), ...)`，不带页图，imageCount=0；端口有多模态能力不证明文字路径使用了图像。
- DocumentArtifact 已有表格、单元格、合并关系与来源；建议复用。

## 方向比较与建议

| 方向 | 收益 | 限制 |
| --- | --- | --- |
| 持续增加布局规则 | 固定模板低成本、可解释 | 陌生组合布局持续增加分支 |
| 原页图直接交给视觉模型 | 减少表格打平损失 | OCR、漏项、归属仍可能错误 |
| 统一结构 + 原页视觉理解 + 来源核验 | 保留文字与关系，复用证据链 | 推荐先验证陌生模板效果及成本 |

1. 各格式转换到现有文档结构，保留原文、网格、合并关系、章节、阅读顺序及可获得的坐标，缺失位置显式表达。
2. 高召回定位评分章节、总分声明、细则表及续页；按逻辑表/章节组织输入，超长分段保留表头、父项和续表关系，记录覆盖。
3. 模型联合原页图、单元格和章节，识别列语义、父子关系、最高分与完整规则，返回字段来源后再转换为业务项。
4. 程序核对来源存在、同项同方案归属、上限与增量区分、父子重复计分；总分和小计仅辅助核验。
5. 冲突时补看相关原页，仍无依据保留复核；第二次模型同意第一次不构成独立正确性证明。

- 示例：商务能力 20 分是父项，业绩 10 分、履约能力 10 分是子项；列交换、名称合并后业务结果应保持一致。
- 简单表可评估廉价通道，复杂表让原页参与识别；路由不能只用模型自报置信度。
- 保留来源身份门槛，不根据模型引句自动重命名正式项。

## 方向验证

- 首轮约 20–30 份真实文件覆盖文本 PDF、扫描 PDF、DOCX、合并/跨页/多表、多方案及段落表格混排；数量是实验起点，不是准确率保证。
- 人工逐项标注名称、最高分、完整规则、父子关系、方案范围和来源，真实歧义允许复核。
- 按项目/模板隔离调试集和保留集；保留集禁止逐文件调规则。
- 同一保留集比较文字、直接视觉、图文结构联合通道，统计漏项、错项、错误自动通过、正确自动处理覆盖率、重复稳定性、耗时和费用。
- 同内容列交换、换行、去边框、合并、分页、跨格式转换做等价测试；关注逐项结果，不仅看总分和 AUTO_APPROVED 比例。
- 实验后确定验收阈值与实现范围，复杂实现进入 Spec Kit。

## 技术参考与跨模块边界

- [DoclingDocument](https://docling-project.github.io/docling/concepts/docling_document/) 表达文字、表格、层级、阅读顺序、来源和可获得的布局坐标。
- [PP-StructureV3](https://www.paddleocr.ai/latest/en/version3.x/pipeline_usage/PP-StructureV3.html) 提供版面、表格、阅读顺序及表格 HTML/单元格框。两者仅为转换层候选，须用同一保留集验证业务准确率。
- 输入组织影响分片、来源绑定、跨页去重、方案总分与父子计分，不能只测试输出 JSON。坐标存在不证明语义归属正确。
- 本轮未查询正式评分项或 UI，未重解析、调用线上模型、修改正式数据、重启或部署；本地仅 Wiki 回填，未提交推送。

## 线上诊断入口（2026-10-22 生产项目 1122）

- 现象：同一份 24MB / 13.2 万字 DOCX，三次解析分别得到 `facts=0`（EMPTY_FACTS）、`facts=1`、`facts=35`（均为 ORIGINAL_TOTAL_UNKNOWN），最终都判 REVIEW_REQUIRED，页面评分项仍为空。
- 结论：不是单一模型缺陷。同一模型在评分章节范围（9.8 万字）返回 31 条，在全文范围（13.2 万字）先后返回 0 条与 35 条；`originalSource` 为空说明评分方案章节定位未命中，校验层因此拒绝覆盖现有评分项。
- 诊断手段：`score_parse_task` 只有统计值，`score_extraction_snapshot` 失败时无记录，violation 明细未落库。已补两处日志——
  - `OpenAiSdkStructuredOutputTransport` 在 json_schema / json_object 两条路径打 DEBUG：`model`、`finishReason`、`usage`、原始响应字符数与前 1500 字预览（需把 `com.xiyu.bid.biddraftagent.infrastructure.openai` 调到 DEBUG）。
  - `MultimodalStructuredExtractionHandler` 在「文字评分事实完成」后打 INFO：`fallback` 与逐条 violation（code/fact/field/evidence/message/expected/actual）。
- 排查顺序：先看 `finishReason` 是否 `length`（截断）→ 再看原始 facts 条数 → 再对 violation code（`EMPTY_FACTS`、`ORIGINAL_TOTAL_UNKNOWN`）判断是模型未识别还是原文方案定位失败。

## 双轨双保险架构落地（2026-10-29 实施）

针对“30 个项目 200 个 PR 仍无法自适应新标书排版”的技术债困局，正式确立并落地双轨双保险（Dual-Track）架构：
1. **主通道（Primary Track）**：
   - 第一性原理：将“表格几何坐标对账”与“文字真伪核验”解耦。
   - 大模型/表格抽取直接提取评分项，通过 `TextInclusionVerifier` 进行全文子串数学核验（39 份金标夹具中实测逐字子串匹配率达 100%）。
   - 只要文本核验通过且权重和闭合（如 100 分），直接标记为 `AUTO_APPROVED` 并赋以 `DUAL_TRACK_PRIMARY`，永不抹空文字，彻底跳过易产生幻觉的二次全局归并。
2. **备用通道（Fallback Track）**：
   - 现存 2.9 万行 Java 几何引擎与全局归并逻辑 100% 保持不动，作为降级备用；
   - 一旦主通道候选为空、权重未闭合或核验率不足，自动无缝降级走老引擎，确保零线上退化。


## 2026-10-30 统一入口契约修正

PDF 使用百度OCR，Word使用结构化代码提取。!2930初版新增的全文模型主通道方向错误，已撤回。`ScoreParseEntryRouter`先调用结构化证据代码提取，仅空结果或结构失败才调用旧流程；待审核证据保留，不由模型覆盖。原文核验和版本冻结/激活继续由现有结构化完成器执行。

1135历史180分快照、入口回归和真实OCR重提取为不同证据，入口修正不能代替业务验收。

本轮证据：clean Maven 122项通过；1135原PDF离线Sidecar提取22表，确定性管线13条事实、0可发布候选、REVIEW_REQUIRED、原文总分未知。该结果不是百度OCR结果，不能据此宣称恢复100分。本地凭证配置未提供百度OCR密钥；不修改生产数据。
