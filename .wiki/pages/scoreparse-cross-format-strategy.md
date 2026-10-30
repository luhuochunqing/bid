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

## 双轨主通道的入口接管（2026-10-30修正）

- 2026-10-29的!2923只在旧候选后处理增加捷径，证据管线提前return时不会触达；当时不能据此认定统一入口已经切换。
- 项目1135任务159：61条候选、180分、正式项0；重复大项60分与平台父子并计20分组成差额。该任务走`EVIDENCE_PIPELINE_V1`。
- 新契约：`ScoreParseAppService → ScoreParseEntryRouter → 全文单次文本提取与核验 → 成功完成 / 失败调用旧流程`。主通道不得先获取旧网格。
- 核验：规则须可唯一回指原文且分值有依据；重复编号、同源重复、歧义父子、多方案、空规则或闭合失败进入兜底。默认100仅是兼容目标，不能冒充原文总分。
- 版本化结果须含可激活scheme和真实文本SourceSpan；通过既有冻结和激活流程保护手工结果。
- 本地回放、真实模型提取、生产部署和重解析是四种不同证据，不相互替代。

- 审查限制：同段闭合不证明跨空行的条件完整，当前自动生效契约仍有反例，整体NEEDS_FIXES。入口接通不等于1135业务修复完成。
