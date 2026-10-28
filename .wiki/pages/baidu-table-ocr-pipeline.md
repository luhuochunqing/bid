---
title: 百度表格文字识别 (Table OCR) 旁路对照管线
space: engineering
category: architecture
tags: [scoreparse, baidu-ocr, table-extraction, shadow-pipeline]
created: 2026-10-28
updated: 2026-10-28
health_checked: 2026-10-28
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/baidu/
backlinks:
  - .wiki/pages/ai-capabilities.md
---

# 百度表格文字识别 (Table OCR) 旁路对照管线

## 1. 架构背景

在标讯评分标准提取（`scoreparse`）中，长期存在“用确定性几何规则硬抗人类复杂自然排版”的技术痛点。为了验证商业成熟成熟文档解析模型在真实招标文件中的效果，系统在 Java 后端扩展了一条基于百度智能云表格识别（`ocr/v1/table`）的旁路对照抽取管线。

## 2. 核心设计原则

1. **零侵入与默认安全**：
   - 配置属性 `app.score-parse.baidu-ocr.enabled` 默认为 `false`，不改动现有生产的主链路，完全作为独立旁路（Shadow / Comparison）存在；
   - 凭据支持 Spring `@ConfigurationProperties` 环境变量占位符机制，生产环境安全注入。
2. **纯函数式适配契约 (FP-Java)**：
   - 适配器 `BaiduTableArtifactAdapter` 为纯静态工具类，输入 `BaiduTableResponse`，单向映射输出系统通用的 `SidecarTableExtraction` 和 `DocumentArtifact`；
   - 严格遵循 `ScoreParseArchitectureTest` 规则 2（infrastructure 层不得构造 `ScoreFact` 领域实体）。
3. **全页边框伪表免疫与单元格保真**：
   - 百度视觉模型在服务端将全页边框与装饰线识别为独立的 `header` 和 `footer` 元数据，不污染正文打分表；
   - 适配器自动构建标准二维网格，并在合并子单元格填充 `isMergedContinue=true` 占位格子，确保与现有下游评分提取逻辑 100% 契合。

## 3. 核心组件与配置

- **配置组件**：`BaiduOcrProperties.java`
- **传输模型**：`BaiduTableModels.java`
- **适配转换器**：`BaiduTableArtifactAdapter.java`
- **网络客户端**：`BaiduTableOcrClient.java`（带内存 Token 缓存及提前 10 分钟自动刷新）
- **测试验证**：`BaiduTableOcrTest.java`（含真实金标 PDF `page_border_continuation.pdf` 集成用例）
