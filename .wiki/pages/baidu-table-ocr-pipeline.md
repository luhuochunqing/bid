---
title: 百度表格文字识别 (Table OCR) 旁路对照管线
space: engineering
category: architecture
tags: [scoreparse, baidu-ocr, table-extraction, shadow-pipeline, settings]
created: 2026-10-28
updated: 2026-10-29
health_checked: 2026-10-29
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
- **网格拓扑构建**：`BaiduTableGridBuilder.java`
- **跨页逻辑行链接**：`BaiduCrossPageTableLinker.java`
- **网络客户端**：`BaiduTableOcrClient.java`（带内存 Token 缓存及提前 10 分钟自动刷新）
- **文档增强器**：`BaiduTableArtifactEnricher.java`（基于关键词与续表探测按需增强 DocumentArtifact）
- **凭据提供 SPI**：`BaiduOcrCredentialsProvider.java`
- **动态凭证服务**：`BaiduOcrSettingsService.java` & `DatabaseBaiduOcrCredentialsProvider.java`
- **后台设置控制器**：`SettingsController.java` (`POST /api/settings/baidu-ocr/test`)
- **前端配置卡片**：`src/views/System/settings/AiModelSettingsPanel.vue` & `useAiModelSettings.js`
- **细则对账回补**：`CellSubRuleRecovery.java` & `CellSubRuleRecoveryTest.java`
- **测试验证**：`BaiduTableOcrTest.java`、`BaiduTableArtifactEnricherTest.java`、`BaiduTableArtifactAdapterTest.java`

## 4. 跨页断行与细则分值解耦机制

在复杂版式中，单元格文本跨越页面底端物理断句，且表格右侧往往包含父级大项合并总分（如“服务响应 10分”），而单元格内部包含得分子项（如“得5分”）。
1. **长引文前缀弹性匹配**：在 `OriginalScoreWeightEvidencePolicy.containsQuote` 中引入前缀容错，引文 $\ge 20$ 字时允许前 25 字符前缀匹配，容忍换页截断；
2. **正文细则得分与父级整行总分解耦**：对叶子节点细则项，当单元格正文中提取出具体分值（`awarded`）时，权威分值仲裁逻辑允许采纳正文分值，避免被整行合并总分列误判为分值失配。

## 5. 单元格内细则确定性对账回补机制 (CellSubRuleRecovery)

针对大模型在处理单单元格包含多细则时存在的非确定性遗漏（如父项10分，单元格内有“1.ICP备案...得5分”和“2.三年信用良好...得5分”，大模型偶发性仅抽出细则1，导致子项合计仅5分产生5分缺口）：
1. **确定性对账发现缺口**：当父项声明为 `SUM` 且直接子项合计小于父项权重（产生 `deficit > 0`）；
2. **物理单元格证据定位与文本细则分解**：通过父项/子项引用的物理单元格，解析单元格内的编号格式（如 `1.`、`2.`、`（1）`）及对应的分值（`得X分`）；
3. **精准增量回补**：过滤已抽取的细则，当未抽取的单条或多条细则分值之和恰好等于缺口分值时，在 Java 领域层确定性构造补齐 `MultimodalScoreFact`，并织入 `MultimodalExtractionReviewPolicy` 归一化流，实现端到端 100 分确定性闭合。

## 6. 评分核心区间第一权威主干（Primary Authority）接管模式

从仅作为“Sidecar缺失页补漏”升级为“评分区间第一主干权威（Primary Authority）”：
1. **全区间主动接管与替换**：当开启百度 OCR 时，在评分表区间内的所有关键页全部由百度 OCR 提取，并在 `mergeArtifact` 时全面覆盖替换原 Sidecar 破碎表格；
2. **2D 网格坐标重叠收缩（BaiduTableGridBuilder）**：百度视觉模型在复杂表头时偶尔出现坐标轻微重叠，适配器在构建网格前自适应收缩合并跨度，绝不吞并相邻已声明的真实单元格，100% 满足 `CanonicalTableGridFactory` 拓扑不变式；
3. **跨页首行续行无缝关联（BaiduCrossPageTableLinker）**：当跨页首行（第 0 行）前置列为空且规则列有文字时，自动将其 `logicalRowId` 缝合到前一页最后一行的 `logicalRowId`。

## 7. 续表页扫描区间连续候选覆盖（免疫 Sidecar isScoreTable 误标）

在真实复杂 PDF（如 `project1130.pdf`）中，续表页（如第 15 页）往往以断开的半句（如“显示视频。）”）开头，不含显式表头，导致 Sidecar 常将其误标为 `isScoreTable: false`。
1. **根因**：原 `determinePagesToScan()` 仅从 baseline 中收集 `isScoreTable=true` 的页面，若第 15 页为 false，则收集到的 `maxScorePage` 停留在 13，扫描上限截断于 $13+1=14$，第 15 页（包含 9.2 项 3分、9.3 项 2分、10 项 10分，合计 15分）被完全跳过，直接导致总分从 100 分跌落至 85 分；
2. **确定性连续扩展算法**：修改为从 `minScorePage` 出发，在 PDFBox 检测出的 `candidatePages` 中向后连续提取所有候选页（相邻差值 $\le 2$，容许中间有不含关键词但承接评分的物理页），直到遇到明显跨章断裂（差值 $>2$）或达到最大页面预算，不再受 Sidecar `isScoreTable` 标记误导；
3. **范围守卫纯净性**：`MultimodalExtractionReviewPolicy` 中对无表格且无单元格事实的 `IN_SCHEME` 认定必须保留条款号（`hasScoringCode`）强约束，避免将纯文本履约考核指标（如 `system_response_metrics`）误放行入评分方案。

## 8. 跨页物理表格归一、OCR 异形标点对齐与分值/名称全闭环 (Project 1392 100分根治)

在 `project1130.pdf`（项目 1392，Task 345）实测中，仅扩大页面扫描范围仍因以下四重断层卡在 85 分并伴随名称丢失与多表警告：
1. **百度半开区间坐标 `[row_start, row_end)` 幽灵尾行/尾列与跨页续行断裂**：
   - `BaiduTableGridBuilder` 改用 `rowStart` / `colStart` 最大值计算网格维度（而非 `rowEnd` / `colEnd`），消除第 13 页底部的全空幽灵行 `r5`，使第 14 页续行 `r0` 精准链接至第 13 页末行 `r4`；
   - `isContinuationRow` 增加首列为空但次列（评审因素）非空时的非续行守卫，防止首列跨行合并的新大项被误判为上一行的续行。
2. **跨页无表头续表物理合并与非评分小表隔离**：
   - `BaiduCrossPageTableLinker.mergeCrossPageScoreTables` 将连续页、同列数、无表头的跨页评分续表（`baidu_tbl_14_0`、`baidu_tbl_15_0`）物理合并入首表 `baidu_tbl_13_1`（统一 `tableId`、平移 row/cellId、保留各单元格真实 `sourcePage` 并串联跨页 `logicalRowId`），消除 `MULTI_TABLE_SCHEME` 警告与多物理表分流（17 项全部归入同一主表）；
   - `BaiduTableArtifactAdapter.isLikelyScoreTable` 要求同时具备评分表头关键词与分值/权重指示符，且非评分表（如第 13 页 `[资质要求 | 详见采购公告]`、第 15 页 `[开标内容 | 开标要求]`）赋予 `sourceSection = "招标文件正文"`，杜绝混入评分方案。
3. **OCR 异形标点与中西文边界空格归一化 (`EvidenceText` & `TextPrimaryEvidenceBinder`)**：
   - `EvidenceText.normalize` 将半角括号 `'('`/`')'` 归一为全角 `'（'`/`'）'`，将引号变体 `'‘'`/`'’'`/`'“'` 归一为 `'”'`，并将数字后紧跟汉字时的句号误识（如 `3。系统架构`、`1．投标人`）归一为 `'.'`（保留小数如 `2。5分` 不变，且严守 1:1 原文字符偏移）；
   - `TextPrimaryEvidenceBinder.restoredQuote` 在 `uniqueSpan` 匹配时支持剥离 PDFBox/LLM 在汉字与拉丁字母间插入的排版空格（如 `"1.进行 ICP 备案"` 对齐百度 OCR `"1.进行ICP备案"`），确保回填原文子串通过 `OriginalScoreQualificationPolicy` 校验。
4. **分项累加分值识别与评审因素列名恢复 (`OriginalScoreWeightEvidencePolicy` & `SourceItemNameRestoration`)**：
   - `OriginalScoreWeightEvidencePolicy` 在行内查找单元格时纳入同一 `logicalRowId` 的跨页续行单元格，并新增 `quoteSubscoresSumToWeight` 识别叶子细则内括号分项累加（如 `2.2` 引文内 `（2分）...（3分）` 合计 `5分`），解决整行仅标大项总分 `20` 时子项 `5分` 被误判 `SOURCE_UNVERIFIED` 的问题；
   - `SourceItemNameRestoration` 支持当模型输出泛化章节名 `"详细评审"` 时，从唯一 `评审因素` 列（含跨页 `logicalRowId` 锚点行及 `claim.sourceCellIds()` 为空的回绑事实）确定性恢复真实评分项名称。
