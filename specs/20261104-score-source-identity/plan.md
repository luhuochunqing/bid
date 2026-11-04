# Implementation Plan: 同来源评分身份与 OCR 失败可见性

**Branch**: `agent/gemini/diagnose-score43-119` | **Date**: 2026-11-04 | **Spec**: [spec.md](spec.md)

## Summary
保留原文物理定位作为身份依据，在分块和定向重试两个入口共享纯函数同源判定。仅证明同一原文规则单元且权重相容时合并；保留首个稳定编号，修复关联边，保留不同子项。配置通过属性引用复用通用OCR凭证；enricher将失败写入artifact warnings，acquisition将OCR诊断送入现有StructuredSourceDiagnostics人工复核通路。

## Technical Context
Java21 / Spring Boot3.2 / JUnit5 / AssertJ，使用现有不可变record、SourceSpan、EvidenceText，无新依赖/表/API。影响scoreparse domain、OpenAI chunk merger、Baidu adapter、source acquisition。临时worktree只编译测试，不启动服务。OCR实测只向现有百度服务发送本任务已经授权解析的原PDF。

## Constitution Check
纯核心无Spring依赖；真实运行链路回放，不加产品Mock模式；先RED后GREEN；新Java文件控制在300行内；集合键冲突不抛异常。用户“立刻修复”确认之前诊断提出的修复方案及必要回归验证。Spec Kit自动分支hook已复用现有隔离分支；auto_commit配置全部disabled。无迁移、无权限变更。

## Project Structure
- `domain/SourceFactIdentity.java`：同源同规则边界判断，保护子项/父子/多方案。
- `domain/SourceFactDeduplication.java`：候选合并和关联保留。
- `domain/SourceRecheckPlan.java`、`infrastructure/openai/ScoreFactChunkMerger.java`：接入共享身份。
- `infrastructure/baidu/BaiduTableArtifactEnricher.java`、`application/SourceArtifactAcquisition.java`：错误透传。
- `backend/src/main/resources/application.yml`：专用配置回退到统一配置。
- 对应JUnit与handler集成回放、现有golden cases扩充。

## Verification
基线三个已复现失败进入JUnit RED；配置真实绑定补RED；修复GREEN；再跑评分域/提取应用相邻测试和架构约束。通过实际handler验证11个单元的分值/来源/原文，不只合计100。私有PDF真实OCR回放单独记录，不冒称生产重新解析。反事实重放旧入口仍应失败。最后ai-slop-cleaner ModeB、独立review和root-cause证据门禁。
