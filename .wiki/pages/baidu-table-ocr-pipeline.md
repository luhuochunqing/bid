---
title: 百度表格文字识别 (Table OCR) 旁路对照管线
space: engineering
category: architecture
tags: [scoreparse, baidu-ocr, table-extraction, shadow-pipeline, settings]
created: 2026-10-28
updated: 2026-10-28
health_checked: 2026-10-28
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/baidu/
  - backend/src/main/java/com/xiyu/bid/settings/service/BaiduOcrSettingsService.java
  - src/views/System/settings/AiModelSettingsPanel.vue
backlinks:
  - .wiki/pages/ai-capabilities.md
---

# 百度表格文字识别 (Table OCR) 旁路对照管线

## 1. 架构背景

在标讯评分标准提取（`scoreparse`）中，长期存在“用确定性几何规则硬抗人类复杂自然排版”的技术痛点。为了验证商业成熟成熟文档解析模型在真实招标文件中的效果，系统在 Java 后端扩展了一条基于百度智能云表格识别（`ocr/v1/table`）的旁路对照抽取管线，并在管理后台提供前端可视化配置面板（方案 A）。

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
4. **前端可视化动态配置与依赖倒置 (DIP)**：
   - **免数据库迁移**：复用 `SystemSetting` JSON payload 存储，免去 Flyway 建表开销；
   - **密钥安全脱敏**：采用 `PasswordEncryptionUtil` AES 密文落库，前端仅下发前4后4脱敏掩码（如 `VJ8Z****sWlY`），掩码提交不覆盖已有密文；
   - **解耦 SPI 契约**：定义 `BaiduOcrCredentialsProvider` 接口，由 `settings` 模块的 `DatabaseBaiduOcrCredentialsProvider` 动态提供解密后凭据，优先读库，无则降级回退到 `application.yml`；
   - **即时连通性测试**：管理后台点击“测试连通性”直接探测百度 OAuth Token 接口并回显测试结果。
5. **PDF 跨页续表增强与大单元格表格补齐 (BaiduTableArtifactEnricher)**：
   - **根治跨页断裂**：Sidecar 处理复杂 PDF 续表时容易跳页（如项目 1392 遗漏第 14 页整个续表），导致下游证据回指失败；
   - **智能定向提取**：`BaiduTableArtifactEnricher` 基于 PDFBox 智能探测评分关键词页及续表候选页，按需定向调用百度 OCR；
   - **优雅融合与兜底**：在 `SidecarDocumentArtifactExtractor` 中接入增强器，将高保真表格补齐并赋予合规 `sourceSection`；若百度 OCR 未开启或发生异常，平滑降级回 baseline。

## 3. 核心组件与配置

- **配置属性**：`BaiduOcrProperties.java`
- **传输模型**：`BaiduTableModels.java`
- **适配转换器**：`BaiduTableArtifactAdapter.java`
- **网络客户端**：`BaiduTableOcrClient.java`（带内存 Token 缓存及提前 10 分钟自动刷新）
- **文档增强器**：`BaiduTableArtifactEnricher.java`（基于关键词与续表探测按需增强 DocumentArtifact）
- **凭据提供 SPI**：`BaiduOcrCredentialsProvider.java`
- **动态凭证服务**：`BaiduOcrSettingsService.java` & `DatabaseBaiduOcrCredentialsProvider.java`
- **后台设置控制器**：`SettingsController.java` (`POST /api/settings/baidu-ocr/test`)
- **前端配置卡片**：`src/views/System/settings/AiModelSettingsPanel.vue` & `useAiModelSettings.js`
- **测试验证**：`BaiduTableOcrTest.java`、`BaiduTableArtifactEnricherTest.java`、`BaiduOcrSettingsServiceTest.java`、`useAiModelSettings.spec.js`
