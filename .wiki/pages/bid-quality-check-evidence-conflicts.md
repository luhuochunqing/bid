---
title: 标书质量核查证据冲突与组合资质裁决
space: engineering
category: lessons
tags: [质量核查, compliance, CA, 保证金, ISO, 评分解析]
sources:
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BasicInfoCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicCaCertificateExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/QualificationMatchPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SubItemAndAttachmentRules.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/QualificationQueryDelegate.java
  - specs/077-fix-ai-quality-1117/spec.md
backlinks:
  - .wiki/pages/_index.md
  - .wiki/log.md
created: 2026-10-23
updated: 2026-10-23
health_checked: 2026-10-23
archive: false
---

# 标书质量核查证据冲突与组合资质裁决

## 根因矩阵

| 现象 | 根因 | 裁决契约 |
|---|---|---|
| 空格日期报缺失 | 只识别数字紧邻“年” | 识别完整年月日并允许空白 |
| 电子标被判纸质标 | 负向模板词首次命中即返回 | 汇总正反证据，明确电子/CA 证据优先 |
| 保证金回单报缺失 | 文本未抽取等同附件不存在 | 声明已缴纳但图片不可读时转人工 |
| 三项 ISO 只关联一项 | 长要求未原子化 | 三项标准分别匹配有效证书 |
| 评分类型错误 | 页面读取旧激活解析 | 分类器与生产激活版本分别核验 |

## 不变量

- `MANUAL_REVIEW` 表示证据通道不足，不是业务材料缺失。
- 单张证书不能代表多个 ISO 标准，过期证书不得关联。
- 新解析候选不等于生产事实；部署后必须核验并显式激活。

## 副作用（Cross-Module Impact）

- CA 提取同时影响招标要求融合与 CA 台账联动。
- ISO 原子化同时服务知识库查询和结构化明细，禁止双轨漂移。
- 代码修复不会回填已持久化报告或评分项。

## 验证入口

```bash
cd backend
mvn -Dtest=BasicInfoAndAttachmentRulesTest,DeterministicCaCertificateExtractorTest,QualificationMatchPolicyTest,QualificationQueryDelegateTest,ScoreTypeClassificationPolicyTest,ResponsibilityArchitectureTest test
```
