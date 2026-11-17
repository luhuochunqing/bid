# Implementation Plan: 标书证据硬条件传递修复

## Summary
复用ScoreItemConstraintExtractor、IncrementalCountScorePolicy及现有match服务过滤谓词，先验证硬条件再计数。纯领域规则不引入框架，应用服务负责读写。证书复用等级判定，旧预判增加规则版本封印。正式评分与资格核查按反例修补，不新增通用引擎。

## Technical Context
Java21/Spring/JPA/MySQL8；JUnit/Mockito只用于隔离单测、真实MySQL验证持久化；无表迁移和新HTTP接口。保持既有粗估/正式评分分离。

## Constitution Check
FP-Java规则留纯核心；真实API无模拟业务路径；RED/GREEN；保护其他工作区和线上；文件长度尽量低于200；无无关重构。

## Project Structure / File Scope
- scoreparse/domain/ScoreItemConstraintExtractor.java, IncrementalCountScorePolicy.java, CertificateScorePolicy.java, CertificateScoreRule.java
- scoreparse/application/EstimatedScoreService.java, EstimatedScoreBasisFormatter.java, ScoreEstimateFreshness.java, ScoreParseItemsReadSupport.java
- scoreparse/application/match/{Project,Person,Brand,Warehouse}MatchService.java
- NEW scoreparse/domain/KnowledgeEvidenceConstraints.java；NEW scoreparse/application/EstimateEvidenceSupport.java（纯条件与DTO编排适配）
- scoreparse/application/ScoreItemAssessor.java及已定位的compliance证据边界
- 对应测试、合成金标、现有Wiki及教训库

## Verification
先回归RED；修后受影响domain/application/match/golden测试；真实MySQL刷新保存回读；FPJava/Maintainability架构测试；git diff --check及三技能独立自动审查。变更不含前端，按仓库要求尝试前端构建。未解析自由文本或现场上界未知如实记录。

## Alternatives
仅改1120数据会复发；恢复所有严格全文禁算违背粗估口径；新建通用规则引擎超出需求。选择复用现有条件和明确未核验项。


## 追加发现与范围
独立反例证明年度区间上界、名称+品牌授权、省级仓库、数量修饰词、至今未来记录也会漏筛；纳入原硬条件边界。WarehouseMatchedItem增加province并保留旧构造器，所有生产写入处透传；ScoringRunReportCodec版本v4阻断旧规则复用。ScoreScoringVersionService对资源类封印也做保存前核验。

## Remaining Limits
非证书预判封印核验规则版本及当天计算，不覆盖资料库后续每次增删的实时指纹。复杂资格适用条件、材料真实性和跨合同事实归属仍待业务逐项核验。首次报价截止日只标未核验，不取项目开标日期替代。
