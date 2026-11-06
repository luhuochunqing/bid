---
title: 百度表格文字识别 (Table OCR) 旁路对照管线
space: engineering
category: architecture
tags: [scoreparse, baidu-ocr, table-extraction, shadow-pipeline, settings]
created: 2026-10-28
updated: 2026-11-06
health_checked: 2026-11-06
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/baidu/
  - backend/src/main/java/com/xiyu/bid/settings/service/BaiduOcrSettingsService.java
  - src/views/System/settings/AiModelSettingsPanel.vue
backlinks:
  - .wiki/pages/ai-capabilities.md
---

# 百度表格文字识别 (Table OCR) 旁路对照管线

## 2026-11-06 实测成功格式

- 实测 APIHub 成功码为 `"0"`，无 `success` 字段，登录 `token/tokenRemainTime` 在顶层；search 仍从 `data.isSuccess/result` 读取百度结果。
- 原实现仅接受文档 `success=true/code="000000"/data.token`，导致成功响应被包装为“登录失败”。现同时兼容两种格式，明确失败和畸形结果不放行。
- 副作用：同一客户端修复同时覆盖设置连接测试及实际 OCR；账号、开关、缓存与 PDF 参数不变。部署更新与业务文档验收仍需单独确认。

## 2026-11-05 APIHub 中转

- `BaiduTableOcrClient` 改为 APIHub `/user/login` → `/search`，`satoken` 请求头鉴权；`params` 为原参数 JSON 字符串，`noCache=1`。
- `data.result` 支持字符串/对象；外层失败与 `isSuccess!=1` 报错，不能当作零表格成功。
- 前端 OCR 卡片配置用户名/密码并加密保存，未配置时回退 `APP_SCORE_PARSE_BAIDU_OCR_USERNAME/PASSWORD`；旧百度 Key 不作为中转登录凭据；原动态开关保留。
- 副作用：前端或环境配置必须改为中转账号；保持 PDF 单页切片、结果适配器及评分提取不变。
- 用户确认只能在测试环境访问；本地契约验证与测试环境真实 OCR 验收分开。

## 2026-11-02 固定真实文件对照与局部修复

- 基线`8c6188b74`，五份固定真实文件、296个冻结输入；只移植`ShadowLogicalRowRestoration`未消除验收失败。实验树16处续文恢复收益不可外推主线，候选已撤回。
- 已有合并评分表cell来源页被`BaiduScanPageSelector`忽略；两行补充可恢复已知页的OCR获取，但不是完整评分修复。恢复页后仍可能把公共说明当事实。
- 无表头续表即使保留正确OCR文字，也可能因`isScoreTable=false`在合并/事实入口之前被跳过；下游续文恢复不能替代上游行归属。
- 继续沿断点复用原表归属、列映射及闭合单元：prod-1133恢复4项（8→12，逐项分值/来源匹配、合计100）及两处续文；test-1386相对仅选页实验消除1条公共说明误计。总分100不能替代原文验收，prod-1133仍有OCR字符差异，其余样本仍有未解决失败。
- 八个既有生产文件的局部修复与整体准确性分开交付；独立审查补充经真实适配器分类的公共说明负例，逻辑单元物理行扩展也须共用判据，不能仅阻止显式逻辑行连接。不宣称未见泛化或项目43生产恢复。完整对照见`specs/20261102-ocr-continuation-mainline/research.md`；无部署/生产重算。

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
- **评分扫描区间选择器**：`BaiduScanPageSelector.java`
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


## 9. 同行中文句号子项切分与评审因素名称列隔离 (Project 1392 标书原文全闭环)

在 `project1130.pdf` 恢复 100 分后，序号 3 (`2.1`)、7 (`4.1`)、10 (`6.1`)、11 (`6.2`)、15 (`9.1`)、16 (`9.2`) 曾出现“标书原文”为空（`—`），且紧随其后的序号 4 (`2.2`)、8 (`4.2`)、12 (`6.3`)、17 (`9.3`) 开头多吞入上一条末句：
1. **百度 OCR 同行子项中文句号与行尾序号切分 (`OriginalRuleUnitText.resolveStart`)**：
   - 百度 OCR 将同一单元格内紧跟在上一句句号 `。` 或 `。)` 后的同行子项序号识别为 `2。投标人...`、`3。投标人...`（或行尾 `3。\n投标人...`）。
   - `OriginalRuleUnitText` 将同行子项标点从 `[.、]` 扩展为 `[.、。．]`，并通过 `resolveStart` 同时支持 `quote` 自带序号与 `quote` 省略前导序号（此时 `num + [.、。．]\s*` 紧邻在 `quote.start()` 之前）两种形态，将后一子项起点精确锚定在编号起始处，避免回退至当前物理行行首导致前一项 `quote.end() <= end` 失败及后一项吞入前项尾句；
   - 当行尾仅含短序号（如 `3。\n`）时自动跨换行截取 `heading`，并在 `identified` 中匹配首个归一化长度 $\ge 2$ 的有效正文行。
2. **多子项同行时隔离评审因素名称列 (`OriginalRuleRestoration.collectSourceIds`)**：
   - 当 `structural.applicable()` / `peerUnit.applicable()` 为真时，仅收集 `LogicalRuleSourceUnit.resolve` 过滤后的规则列 `spans()`，避免将 `SourceItemNameRestoration` 追加的 `评审因素` 名称列单元格（`rX_c1`）混入规则原文 `raw`。

## 10. 内存单页切片与分词器边界防护（2026-10-30 演化）

1. **大文件内存单页切片 (`BaiduTableOcrClient.slicePdfPage`)**：
   - 针对多页大型 PDF，通过 PDFBox 将目标单页内存抽取为独立 PDF 字节数组（~50KB），将传输体积从 30MB+ 缩减 99.8%，彻底终结每次重复上传全量大文件引起的 Base64 编码耗时与 JVM 内存雪崩。
2. **评分编号领域分词器 (`HierarchicalCodeTokenizer`)**：
   - 彻底重构粗暴正则 `^(?:\d+).*`，建立专用的层级分词与编号校验器，支持单数字、层级数字（`1.1`、`3.2.1`）、数字接标点（`1、`、`1.`）、括号（`（1）`、`1-1(1)`）、中文序号（`一、`）、罗马数字（`I`、`II.`）、带圈数字（`①`）与业务编码（`TECH_01`）；
   - 严格拦截“2024年度”、“3年期”、“5家”、“500万元”等普通正文，从源头杜绝过拟合误判为评分编号。

## 11. 跨页评分表多区间双向连通与入口路由防倒退仲裁 (2026-11-01)

针对项目 35、43、1117、1118 在部署百度 OCR 后出现的集体欠分缺陷，进行了两项核心演进：
1. **全区间多簇连通、中间续页补齐与长表零截断 (`BaiduScanPageSelector`)**：
   - 彻底废除旧版基于 `minScorePage` 的单向硬截断 (`if (p < minScorePage) continue;`)；
   - 全量收集所有与 baseline `scoreTablePages` 相交的连通候选簇（相邻差 $\le 2$），同时纳入分散区间（如 [20,21] 技术标与 [40,41] 商务标），严禁单选；
   - 候选页聚类时自动补齐相邻候选页间隙（如 [20,21,23,24] 中补全 22），杜绝无关键词中间续页漏扫；
   - 确证评分大表全量保护：凡属于包含评分锚点的连通区间，全量保留（支持 55 页以上超长评分大表），严禁任何静默截断。
2. **方案总分核验、防倒退与冲突诊断保全 (`ScoreParseEntryRouter`)**：
   - 区分超额/重复项真实证据保护（`leafWeightSum >= declaredTotal` 严禁 fallback 覆盖）与严重欠分缺口（`leafWeightSum < declaredTotal` 允许 fallback 尝试补救）；
   - 增加全案目标总分（如 100 分）一致性核验：杜绝局部子方案（如 20/20 分）以自身 complete=true 绕过防倒退去覆盖 48/100 分全案主结果；
   - 单调更优与防倒退门禁：仅当 fallback 证明分值严格更优且更接近目标总分时才允许采纳，坚决拦截如 20 分 fallback 覆盖 48 分原事实倒退；
   - 诊断与冲突保全：采纳更优 fallback 时完整合并保留原主结果的 `sourceDiagnostics`；若存在方案总分冲突，记录 `FALLBACK_SCHEME_CONFLICT` 证据。

## 评分结果展示的首批独立提取（基线38c34354b，2026-11-01）

- 从旧OCR工作树中仅提取展示语义，原有OCR、表格转换、后端DTO与迁移均未带入。
- `ScoreCandidateReviewCard`：本次提取记录数=`candidateFacts.length`；当前方案候选数=`eligibleFacts.length`；有效合计仍由原父子去重逻辑提供。方案外记录继续在核对区展示，不因候选为0而隐藏。
- `ScoreExtractionPanel`：读取版本化接口已存在的`sourceDiagnostics`，零items仍显示中文原因及原始详情；诊断按文本插值渲染。旧接口不存在的新证据字段不假造，活动版本历史来源口径保持主线不变。
- 关联：[[pr2948-scoreparse-scope-and-fallback-risk]]。本批不改2948路由或扫描页选择，不改变评分/采纳资格，不代表整文件准确性验收。

## PR2950 诊断字典边界修复与基线对照（2026-11-01）

- 诊断码提取冒号前缀后，以`Object.hasOwn`检查字典自有属性，防止`constructor`、`toString`、`__proto__`及带后缀输入显示函数或对象；六个边界用例修复前失败、修复后通过，已知诊断后缀映射保持。
- 评分前端20文件233项、独立审查44项及构建通过。此前后端全量11,466项中91失败、22跳过；独立基线d6d6fa454复跑11个失败套件：209项中91失败、4跳过，逐用例失败集合完全一致，无PR独有失败。后端树也与基线一致，不能把主线失败表述为本PR引入。
- 结论仅覆盖展示修复及失败归属，不表示后端全量通过、真实文件准确性通过或已部署。详细本地报告：`.omc/evidence/xiyu-code-review-2950.md`。

## 评分单元边界透传与分值核验（2026-11-02）

同一原文格可能包含父项40及子项28/12。已有闭合规则Result经内部ScoreEvidenceClaim完整传到模型与确定性核验入口，列值/标题/上限/奖励/档次统一在当前评分单元取证；真实同源父链仅按唯一包含关系缩小边界。不能按候选分值选边界，也不能把单列相等作为忽略规则冲突的理由。

合成金标case-score-unit-authority同时核验正确28/12与错误40应指向原文28的诊断；负例阻断不代表OCR提取成功，正向资格和真实离线整链路另验。五项独立测试基线修正、Python脚本及真实百度配置不在本次提取PR范围，验证记录必须将已知基线失败与新增回归分开。

确定性ScoreFact投影须保留sourceText的具体引文，不能以共享dimension替代锚点；verifyFacts与verifyModel必须分别跑正反例。前者三个回归曾RED，不能只凭模型入口金标通过宣称双入口已覆盖。

## 原文归属、稳定父边与档位序数的回归约束

- 原文决定行归属，数字格只能在该行内核验。相同名称的28/12跨行交换仍可合计100，因此总分不能替代逐项一致性。模型引用、短引用多处命中、跨单元格引用，以及确定性ScoreEvidence单独指定分值格，都必须覆盖正反对照。
- 确定性事实保留factId/parentFactId父链；展示编号保留用于原文分段，不能重新作为全局父节点唯一标识。跨表重号不应改变已证实的子项边界。
- 第10/11、20/21、100/101档与中文序数采用相同互斥最大值语义；独立整项上限冲突仍保留拒绝。
- 入口行为回归见`OriginalScoreWeightBoundaryRegressionTest`。这些是系统转换核验测试，不等同于百度OCR原始文字或生产整文件准确率验收。

- 部署边界：`APP_SCORE_PARSE_BAIDU_OCR_GATEWAY_ENABLED` 默认 false；仅测试部署显式设为 true。已有数据库 OCR 启用状态不能绕过该开关，生产原直连路径不再自动使用，不会静默回落直连。
- 真实 OCR 诊断另需 `XIYU_OCR_GATEWAY_LIVE_TEST=true` 及外部中转账号；普通本地测试不访问真实 OCR。真实诊断保留原断言，不将跳过记作验收通过。

- 双开关能力判断：`BaiduTableOcrClient.isEnabled()` 先检查部署开关，再读取数据库动态开关（无提供者时读属性开关）。部署关闭时保留 Sidecar 原成功结果和失败异常，不进入扫描或 OCR fallback。

- **本次 PR 验证边界（2026-11-05 用户确认）**：用户对真实页面 Playwright 验证明确回复“不需要验证”，因此本 PR 不将其作为前置门禁；记录为用户要求延期，不能写成通过。组件测试、真实设置服务/数据库测试、HTTP 协议测试与构建分别记录，均不能替代真实 OCR 识别验收。

- APIHub 结果完整性：仅明确非零整数 `error_code` 可直接作为业务失败返回；成功结果要求非负整数计数和一致的表格数组，合法零表格可省略数组。不完整结果抛 IOException，不能被模型默认空列表掩盖。
