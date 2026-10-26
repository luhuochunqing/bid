---
updated: 2026-10-26
health_checked: 2026-10-26
title: 评分项跨章节穿透资格项业绩检索与基础分递增计分规范
space: engineering
category: scoreparse
tags: [scoreparse, qualification, performance, fp-java, golden-case]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/policy/CrossChapterPerformancePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/IncrementalCountScorePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java
  - docs/lessons/lessons-learned.md
---

# 评分项跨章节穿透资格项业绩检索与基础分递增计分规范

## 1. 背景与历史困境

在生产招标文件中，评分项关于业绩的表述常常呈现“空泛引用”形态：
> 例：“供应商业绩 | 满足资格条件的业绩得基础分60分，在此基础上每增加一个满足资格条件的业绩（2023年1月1日至首次报价截止日，以合同签订时间为准）加20分，满分100分。 | 5%”

历史上对此类问题的修复历经 200+ PR 仍然反复，核心根因在于：
1. **单点正则修复的局限性**：过去的修补仅从评分项单行文本做正则提取。然而真实白纸黑字的品类（如“工业品/MRO”）、单项金额门槛（“不低于1000万元”）和凭证要求（“合同关键页及发票”）完整记载在《第三章 资格审查表》中。只看评分单行是“无米之炊”；
2. **非零基数阶梯计分模型缺失**：原 `IncrementalCountScorePolicy` 仅支持从 0 开始递增的模型，遇到“基础分60分 + 递增20分”时直接解析失败，导致 `ScoreTypeClassificationPolicy` 将该量化项误判为 `SUBJECTIVE`（主观项），短路了阶段 1 的预计得分；
3. **安全门禁拦截**：`ProjectMatchService` 含有严格防护：严禁在没有任何品类/金额约束下直接查询全表给满分。

## 2. 根治架构设计 (FP-Java & 编排层解耦)

### 2.1 基础分递增计分纯策略扩展 (`IncrementalCountScorePolicy`)
- 扩展正则表达式识别 `BASE_SCORE_PATTERN` 与 `BASE_INCREMENT_AND_CAP`；
- 在 `Rule` record 中增加 `baseScore` 属性，准确导出 `requiredCountForMaximumScore()`（基础分60 + 2个增项 = 满分需3个业绩）；
- 支持百分制分值与权重（如 5%）的比例折算（`weight * cappedScore / maxScore`）。

### 2.2 跨章节业绩穿透纯核心策略 (`CrossChapterPerformancePolicy`)
- 位于 `com.xiyu.bid.scoreparse.domain.policy`，严格遵循 FP-Java 规范，零框架依赖；
- 识别评分项中引用资格条件的信号词（“满足资格条件”、“初步评审”等）；
- 穿透检索资格审查条款或招标文件原文，提取出结构化的金额下限、品类关键词、发票/合同凭证要求。

### 2.3 编排层注入与安全透传 (`EstimatedScoreService`)
- 注入可选端口 `TenderQualificationQueryPort`，在主构造函数上显式声明 `@Autowired` 并设置 `@Autowired(required = false)`；
- 识别到评分项引用资格条件时，调用纯策略穿透提取约束，将品类与金额装配入 `ProjectMatchRequest`；
- 在 `EstimatedScoreBasisFormatter` 中格式化穿透依据文案（清晰记录穿透出的品类与金额），为最终用户提供透明完整的因果证据链。

## 3. 黄金样本固化与回归纪律

在 `scoreparse-golden` 夹具库中固化真实生产标书样本：
- 夹具文件：`case-production-cross-chapter-perf.md` 与 `case-production-cross-chapter-perf.expected.json`；
- 自动化门禁测试：`ProductionCrossChapterPerformanceGoldenReplayTest` 全链路回放验证（主客观判定、阶梯分值换算、穿透提取、0~4份业绩计分），严防后续迭代回退。
