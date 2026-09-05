---
title: AI 能力
space: engineering
category: module
tags: [AI, 智能, 大模型, 合规, 评分, 竞争情报, 智能装配, 案例库]
created: 2026-04-15
updated: 2026-08-21
health_checked: 2026-09-05
sources:
  - src/config/ai-prompts.js
  - backend/AI_MODULE_SUMMARY.md
  - backend/src/main/java/com/xiyu/bid/ai/README.md
  - backend/src/main/java/com/xiyu/bid/ai/client/RoutingAiProvider.java
  - backend/src/main/java/com/xiyu/bid/settings/controller/SettingsController.java
  - backend/COMPLIANCE_MODULE_SUMMARY.md
  - backend/SCORE_ANALYSIS_MODULE.md
  - backend/COMPETITION_INTEL_MODULE.md
  - docs/architecture/技术架构方案.md
backlinks:
  - _index
  - ai-provider-configuration
  - business-process
  - contract-constraints
  - implementation/attachment4-gap-matrix
  - implementation/attachment4-requirement-task-book
  - implementation/attachment6-function-list-trace
  - modules
  - requirements
---
# AI 能力

## 1. AI 能力总览

平台内置 **4 大类 10 项** AI 能力，贯穿投标全生命周期：

| 类别 | 能力 | 核心价值 |
|------|------|----------|
| **底层引擎** | **DocInsight** | **通用文档智能解析（证据驱动）** |
| **投标准备**（4 项） | AI 分析 | 招标文件智能解析与项目级需求拆解 |
| | 评分点覆盖 | 评分标准与标书内容逐项匹配 |
| | 竞争情报 | 竞对策略预测与历史数据分析 |
| | ROI 核算 | 投标投入产出分析与决策建议 |
| **标书编制**（2 项） | 智能装配 | 7 步流程自动组装标书章节 |
| | 合规雷达 | 6 维度合规检测，规避废标风险 |
| **团队协作**（3 项） | 版本管理 | 文档变更追踪与差异摘要 |
| | 协作中心 | 5 角色智能任务分配 |
| | 自动化任务 | 重复性任务自动识别与执行 |

---

## 2. 底层引擎类 (Core Engine)

### 2.1 DocInsight 文档智能引擎 (NEW)

**模块路径**：`com.xiyu.bid.docinsight` | **核心定位**：全系统文档解析与证据链基础设施

- **技术突破**：实现“解析 -> 锚定 -> 核对”三位一体的文档认知闭环。
- **核心能力**：
  - **高保真转换**：自动处理 .doc/.docx/.pdf，输出结构化 Markdown。
  - **结构化切片 (Structural Chunking)**：基于文档大纲（H1-H6）的物理语义切片，保留完整的章节路径上下文。
  - **证据锚定 (Evidence Anchoring)**：提取的每一个字段均携带 `sourceExcerpt`（原文摘录）和 `sectionPath`（章节位置）。
  - **通用 AI 流水线**：支持通过 Schema 驱动不同的解析 Profile（如：标书、合同、案例）。
- **业务价值**：彻底消除 AI 幻觉，将核对成本降低 90%，为全系统提供带“实锤”的结构化提取能力。

---

## 3. 投标准备类（4 项）

### 3.1 AI 分析

**配置 ID**：`ai-analysis` | **类别**：`prepare`

- **角色定位**：资深投标专家（15 年以上投标经验）
- **核心能力**：招标文件智能解析，提取关键信息、需求项、章节快照和风险点
- **输出内容**：
  - 项目背景概要（`projectInfo`）
  - 技术要求与商务条款（`requirements`）
  - 评分标准解析（`scoringCriteria`）
  - 潜在风险点识别（`risks`）
- **评分体系**：
  - 综合评分 0-100 分
  - 中标率权重：技术 50% + 商务 30% + 经验 20%
  - 风险阈值：高 > 0.8、中 > 0.5、低 > 0.2

#### 项目级解析与任务拆解

项目详情页提供独立的“解析招标文件”入口，不依赖 AI 生成初稿先成功完成。

| 能力 | API | 结果 |
|------|-----|------|
| 解析就绪检查 | `GET /api/projects/{projectId}/tender-breakdown/readiness` | 检查项目权限和 DeepSeek provider key |
| 上传解析 | `POST /api/projects/{projectId}/tender-breakdown` | 写入 `bid_tender_document_snapshots` 与 `bid_requirement_items` |
| 任务拆解 | `POST /api/projects/{projectId}/tasks/decompose` | 生成商务、技术、资料、评分复核等项目任务 |

设计口径：
- 标讯导入不强制完整解析所有文件，避免导入阶段变慢。
- 项目级解析结果可同时服务“拆解任务”和“AI 生成初稿”。
- “评分标准拆解”是评分覆盖入口，不再承担任务看板主按钮职责。

### 3.2 评分点覆盖

**配置 ID**：`score-coverage` | **类别**：`prepare`

- **角色定位**：投标评审专家
- **核心能力**：评分标准与标书内容逐项匹配分析与 V3 双阶段打分闭环
- **V3 双阶段评分闭环架构 (NEW 2026-08-15)**：
  - **阶段 1（招标文件解析与规则预判）**：从招标文件自动提取结构化评分要素（商务、技术、实施、资质、团队、加分项等 13 项标准维度），联动企业知识库（资质库、人员库、业绩库）预判满足状态（`✓ 满足` / `✗ 不满足` / `待确认`）并给出预计得分。
  - **阶段 2（投标文件对标打分与标书引用建议）**：针对已上传投标文件自动执行客观项打分判定，精确标注标书原文章节出处（`quote`），标明缺失原因（`missedReason`），并对不满足/待确认项提供具体修改建议（`suggestion`）；主观项清晰隔离标注为待评标专家人工评审。
- **覆盖状态分类**：
  - **已覆盖**（covered）：评分点在标书中有完整响应
  - **部分覆盖**（partialCovered）：评分点有响应但不完整
  - **未覆盖**（uncovered）：评分点在标书中缺失
- **覆盖率阈值**：默认 80%，低于阈值自动高亮提醒
- **后端与前端实现**：
  - 前端抽屉：`src/views/Project/stages/components/ScoreParseDrawer.vue` + `useScoreParseDrawer.js`
  - 后端模块：`com.xiyu.bid.scoreanalysis` 与 `com.xiyu.bid.ai`
  - 5 个标准评分维度：技术能力（30%）、财务实力（25%）、团队经验（20%）、历史业绩（15%）、合规性（10%）
  - 加权评分算法：总分 = 各维度分数 x 维度权重
  - 风险等级自动判定：80-100 低风险、60-79 中等风险、0-59 高风险
  - 支持项目历史趋势跟踪与多项目对比分析，支持一键导入到项目评分草稿库。

#### AI 评分标准解析 V3 抽屉（spec 041）

- 前端：`ScoreParseDrawer.vue` + `useScoreParseDrawer.js` + `scoreParseTask.js`（轮询/枚举映射）
- API 模块：`src/api/modules/scoreParse.js` → spec 041 真接口 `/api/projects/{id}/score-parse/*`
- 阶段 1 招标正文：立项已上传的 `TENDER` 文件（`InitiationTenderTextResolver`），不走 Bid Agent 初稿；读失败回退快照；超过 50MB 不整包载入
- 打开抽屉：从未解析过才自动新建；进行中跟随；失败展示原因后须点「重新解析」
- 阶段 2 实际打分走异步任务：`triggerScoring → pollTask(2s/900次) → getResults`
- 状态映射：OK/DANGER/PENDING → ok/danger/neutral；OBJECTIVE/SUBJECTIVE → 客观项/主观项
- 假后端已清除：`biddraftagent` 的 BidScoreEvaluationPolicy/AppService/score-evaluation 端点（关键词硬编码打分）已删除，统一走 spec 041 真 LLM 打分
- 合计行列对齐布局（2026-08-25）：`ScoreParseTable.vue` tfoot 从 colspan=8 横向 flex 条拆为对齐到表格列的单元格——满足状态三项（满足/不满足/待确认）竖排、客观/主观项权重两行竖排、高亮得分框居得分列；两阶段共用组件天然对齐；抽屉头部留白已压缩（内容上移）

### 3.3 竞争情报

**配置 ID**：`competition-intel` | **类别**：`prepare`

- **角色定位**：市场竞争分析专家
- **核心能力**：从公开数据中提取竞争情报，预测竞对策略
- **分析输出**：
  - 竞争对手列表（`competitors`）
  - 报价策略预测（`probabilityStrategy`）
  - 价格范围估算（`priceRange`）
  - 优劣势分析（`strengthAnalysis`）
- **分析深度**：支持深度分析模式（`deep`），含历史数据回溯
- **后端实现**：竞争情报模块（`com.xiyu.bid.competitionintel`）
  - 竞争对手管理：名称、优势劣势、市场份额、投标范围
  - 竞争分析：按项目维度分析竞争态势
  - 历史表现跟踪：胜率预测（0-100）
  - API 端点 6 个，含竞对管理与分析功能

### 3.4 ROI 核算

**配置 ID**：`roi-analysis` | **类别**：`prepare`

- **角色定位**：财务分析专家
- **核心能力**：投标投入产出比分析，辅助决策
- **输入参数**：项目预算、预计中标金额、投入成本（人力/时间/材料/差旅/其他）、历史中标率
- **输出内容**：
  - ROI 指标（`roi`）
  - 回收周期（`paybackPeriod`）
  - 投标建议（`recommendation`）
  - 风险等级（`riskLevel`）
- **决策阈值**：ROI >= 1.5 时建议投标

---

## 4. 标书编制类（2 项）

### 4.1 智能装配

**配置 ID**：`smart-assembly` | **类别**：`composition`

- **角色定位**：标书编制专家
- **核心能力**：根据招标要求智能组装标书各章节内容
- **7 步装配流程**：

| 步骤 | 说明 |
|------|------|
| 1. 需求分析 | 解析招标文件，提取章节要求 |
| 2. 模板匹配 | 从模板库匹配最佳模板 |
| 3. 内容生成 | AI 生成符合要求的章节内容 |
| 4. 合规检查 | 检查生成内容的合规性 |
| 5. 格式优化 | 统一格式、排版、编号 |
| 6. 交叉验证 | 各章节间一致性校验 |
| 7. 终稿输出 | 生成最终标书文档 |

- **支持章节**：Executive 摘要、技术方案、商务方案、项目计划、团队介绍、案例介绍
- **自动格式化**：默认开启（`autoFormat: true`）

### 4.2 合规雷达

**配置 ID**：`compliance-check` | **类别**：`composition`

- **角色定位**：招投标法规与质量专家（熟悉《招标投标法》、招标文件深度条款及废标点）
- **核心能力**：全方位对真实投标文件执行 25 项标准质量核查，构建完整页码证据链与规避废标风险
- **架构设计 (Feature 047)**：
  - **前置 18 项纯 Java 确定性规则**：基于 FP-Java 纯函数式策略（`DeterministicCheckPolicy`），零框架依赖，实现毫秒级快速熔断与确定性判定（涵盖金额大小写转换折算、废标敏感词正则扫描、签章占位、联系方式等）；
  - **后置 7 项定向章节语义裁决**：按需检索知识库（资质库/人员库/品牌库），定向提取关键章节切片送 LLM 语义裁决；
  - **真实文件流式解析与物理切片**：通过 Sidecar 流式解析保留物理换页符 `\f`，按页切片生成包含物理页码的 `DocumentContent`；
  - **证据链溯源 (QualityEvidence)**：每项判定均携带来源标签（招标/投标/知识库）、物理页码与原文摘录；
  - **单表异步任务与 SHA-256 哈希缓存防抖**：基于 `compliance_check_results` 单表状态机流转与双哈希比对复用。

- **3 大分类 25 项标准检查项清单**：

| 分类 | 检查项 | 严重级别 | 裁决方式 |
|------|--------|----------|----------|
| **基本信息合规 (10)** | 封面项目名称与编号一致性 | CRITICAL | 纯规则确定性匹配 |
| | 投标单位全称与法人印鉴一致性 | CRITICAL | 纯规则确定性匹配 |
| | 投标报价大写金额与小写金额一致性 | CRITICAL | 纯规则大写转换折算匹配 |
| | 投标总价与分项报价汇总一致性 | HIGH | 纯规则数值校验 |
| | 投标有效期满足招标文件要求 | CRITICAL | 纯规则正则与数值匹配 |
| | 交付期/工期承诺满足招标文件要求 | HIGH | 纯规则/语义比对 |
| | 质保期承诺满足招标文件要求 | HIGH | 纯规则/语义比对 |
| | 质量标准承诺满足招标文件要求 | MEDIUM | 纯规则关键词扫描 |
| | 投标保证金金额及缴纳凭证 | CRITICAL | 纯规则/证据检测 |
| | 投标文件签署/盖章完整性 | CRITICAL | 纯规则占位与签章标记 |
| **分项条款合规 (10)** | 技术规格参数逐条响应及偏离表 | CRITICAL | 定向章节 LLM 语义裁决 |
| | 商务条款逐条响应及偏离表 | HIGH | 定向章节 LLM 语义裁决 |
| | 实施方案/施工组织设计完整性 | MEDIUM | 定向章节 LLM 语义裁决 |
| | 项目团队人员配置及资质证明 | HIGH | 知识库人员库定向检索比对 |
| | 类似业绩证明材料及合同/发票 | HIGH | 知识库业绩库定向检索比对 |
| | 售后服务方案及承诺 | MEDIUM | 定向章节 LLM 语义裁决 |
| | 培训方案及承诺 | MEDIUM | 定向章节 LLM 语义裁决 |
| | 应急预案及保障措施 | MEDIUM | 定向章节 LLM 语义裁决 |
| | 知识产权与保密承诺 | HIGH | 纯规则关键词扫描 |
| | 废标风险词/敏感词排查 | CRITICAL | 纯规则敏感词正则字典扫描 |
| **附件与凭证清单 (5)** | 营业执照/法人身份证明 | CRITICAL | 知识库资质库定向比对 |
| | 授权委托书及被授权人身份证明 | CRITICAL | 纯规则/知识库比对 |
| | 财务审计报告/资信证明 | HIGH | 纯规则/附件匹配 |
| | 依法缴纳税收和社会保障资金证明 | HIGH | 纯规则/附件匹配 |
| | 参加政府采购活动前3年无重大违法记录声明 | CRITICAL | 纯规则文本匹配 |

- **UI 交互**：880px 右侧抽屉 (`QualityCheckDrawer.vue`)，支持 4 态统计看板、3 大分类折叠、高亮证据链对比与 2s 轮询进度条。

---

## 5. 团队协作类（3 项）

### 5.1 版本管理

**配置 ID**：`version-control` | **类别**：`collaboration`

- **角色定位**：文档管理专家
- **核心能力**：智能追踪标书版本变更，生成变更摘要
- **功能特性**：
  - 版本对比与差异摘要（新增/修改/删除分类展示）
  - 最多保留 10 个历史版本
  - 自动快照功能（`autoSnapshot: true`）
  - 智能合并策略（`mergeStrategy: 'smart'`）

### 5.2 协作中心

**配置 ID**：`collaboration-center` | **类别**：`collaboration`

- **角色定位**：项目管理专家
- **核心能力**：智能分配任务，追踪协作进度
- **5 角色任务分配**：

| 角色 | 职责 |
|------|------|
| 项目经理（PM） | 统筹协调、进度管控 |
| 技术负责人 | 技术方案编写与审核 |
| 商务经理 | 商务方案编写、报价策略 |
| 文档编写人 | 标书内容撰写与整理 |
| 审核人 | 内容审核与质量把关 |

- **优先级体系**：critical / high / medium / low
- **通知机制**：任务分配自动通知（`notifyEnabled: true`）

### 5.3 自动化任务

**配置 ID**：`auto-tasks` | **类别**：`collaboration`

- **角色定位**：流程自动化专家
- **核心能力**：自动识别和执行重复性标书编制任务
- **可自动化任务类型**：

| 任务类型 | 可自动化 | 说明 |
|----------|----------|------|
| 表格填充 | 是 | 自动填入企业信息、资质数据等 |
| 格式调整 | 是 | 统一字体、页边距、行距等 |
| 章节生成 | 是 | 根据模板自动生成标准章节 |
| 交叉引用 | 是 | 自动更新章节间引用关系 |
| 内容审核 | 否 | 需人工判断，AI 仅辅助 |

---

## 6. 后端 AI 架构

### Provider 抽象

后端 AI 模块采用接口化设计，通过 `AiProvider` + `RoutingAiProvider` 实现运行时路由：

| Provider | 路由来源 | 说明 |
|----------|----------|------|
| MockAiProvider | `ai.provider=mock` 或设置中心 activeProvider=mock | 默认兜底实现，返回模拟数据 |
| OpenAI Compatible | 系统设置 `aiModelConfig.providers` + `activeProvider` | 统一兼容 OpenAI / DeepSeek / 通义千问 / 豆包 等 OpenAI 兼容协议厂商 |

配置入口：
- 系统设置读取/更新：`GET/PUT /api/settings`
- AI 连接测试：`POST /api/settings/ai-models/test`
- 运行时权限：`GET /api/settings/runtime-permissions`

安全口径：
- API Key 仅存储加密值，接口响应只返回 `apiKeyConfigured` 和脱敏 `apiKeyMasked`
- 运行时优先读取设置中心密钥，缺失时才回退到环境变量

### 异步执行

- 所有 AI 分析方法使用 `@Async` 注解，返回 `CompletableFuture`
- 不阻塞主线程，支持并发多任务分析
- 完善的异步异常处理机制

### 分析结果结构

```
AiAnalysisResponse
  - score: 综合评分 (0-100)
  - riskLevel: 风险等级 (LOW / MEDIUM / HIGH)
  - strengths: 优势列表
  - weaknesses: 劣势列表
  - recommendations: 建议列表
  - dimensionScores: 维度评分列表
      - dimensionName: 维度名称
      - score: 维度分数
      - details: 详细说明
```

---

## 7. Prompt 配置结构

前端 AI 功能配置位于 `src/config/ai-prompts.js`，每项 AI 能力包含两部分配置：

### promptTemplate（Prompt 模板）

| 字段 | 说明 | 示例 |
|------|------|------|
| `role` | AI 扮演的角色身份 | "你是一位资深的投标专家，具有15年以上的投标经验" |
| `task` | 任务描述与输入变量模板 | "请分析以下招标文件内容：\n{docContent}\n\n提取关键信息..." |
| `outputFormat` | 输出格式要求 | "JSON格式，包含projectInfo, requirements, scoringCriteria, risks字段" |

### formConfig（表单配置）

每项 AI 能力还配置了对应的表单参数，用于前端交互：

- **AI 分析**：中标率权重、风险阈值
- **评分点覆盖**：评分项列表、覆盖率阈值
- **竞争情报**：竞对列表、分析深度
- **ROI 核算**：成本类别、ROI 阈值、币种
- **智能装配**：章节列表、模板引用、自动格式化开关
- **合规雷达**：检查项列表（含严重等级）、自动修复开关、报告导出
- **版本管理**：最大版本数、自动快照、合并策略
- **协作中心**：团队角色、优先级、通知开关
- **自动化任务**：任务类型列表、已启用任务

---

## 8. 与业务流程的映射

各 AI 能力在 [[business-process]] 各阶段的作用：

| 业务阶段 | AI 能力 | 作用 |
|----------|---------|------|
| 标讯获取 | AI 分析、ROI 核算 | 标讯价值评估、投标决策辅助 |
| 项目立项 | 竞争情报 | 竞争态势分析，辅助立项决策 |
| 任务分解 | 协作中心、自动化任务 | 智能任务拆解与分配 |
| 标书编制 | 智能装配、合规雷达、评分点覆盖、版本管理、案例库智能推荐 | 标书生成、质量保障、过程管理、历史优秀案例复用 |
| 投标提交 | 合规雷达 | 提交前最终合规复核 |
| 结果闭环 | 竞争情报、AI 分析、案例自动沉淀 | 竞对信息更新、结项标书切片自动沉淀案例库 |

---

## 9. 案例库智能推荐与结项自动沉淀 (Knowledge Case Engine)

### 9.1 投标编制主链路推荐
- **入口**：在「投标编制阶段」点击「AI智能推荐案例」，呼出 `AiRecommendDrawer` 抽屉。
- **匹配机制**：
  - 读取当前项目解析出的评分项列表（`ProjectScoreDraft`）。
  - 调用 `/api/cases/recommend` 由 `KnowledgeCaseMatchPolicy` 执行多因子匹配（评分项文本 Jaccard 相似度、行业/客户类型匹配度、中标结果加权）。
  - 支持 300ms 防抖的关键词二次过滤。
- **复用与闭环**：
  - 案例卡片直接复用案例库 `CaseCard`（2 列网格，左下角创建时间；推荐场景经可选 `matchScore` prop 额外渲染「匹配度 N%」，案例库不传零变化）。
  - 详情复用案例库 `CaseDetailDialog`（居中弹窗；点击卡片调 `GET /api/cases/{caseId}` 拉全量含招标主体；推荐抽屉内嵌套使用必须传 `append-to-body`，否则弹窗被抽屉 overlay 压制），三模块：评分项详细要素原文 / 应答片段全文 / 案例元信息（项目名称、招标主体、创建时间、项目状态、客户类型、项目类型）；底部「查看源项目 / 标书原文 / 复用」。原自研 `AiRecommendDualPaneDialog` 已删除（2026-08 对齐案例库 PRD 精简重构）。
  - 点击「复用」将应答内容写入系统剪贴板，同时调用 `POST /api/cases/{id}/reuse` 记录复用（后端累加 `reuse_count`，界面不展示）。
  - 推荐列表额外返回 `bidEntity` / `createdAt` / `projectStatus`（来源项目 + 标讯批量装配，不落案例表）。

### 9.2 结项自动沉淀与幂等防重
- **触发机制**：项目状态流转为 `CLOSED` 时触发 `ProjectClosedEvent`。
- **评分项数据源**：`score_item`（招标文件解析产物，spec 041）。前置判定与构建均读 `score_item`（经 `ScoreItemSnapshot` 承载），`scoringCategory` 由 `CaseAiMatcher.extractCategory(score_item.dim)` 按中文关键词归类为技术/商务/实施服务/资质业绩。
- **处理链路**：`ProjectClosedEventListener` 异步加载归档标书全文与评分项，调用大模型切片匹配提取优秀应答。
  - ⚠️ 历史坑 1（project_score_drafts）：早期依赖 `project_score_drafts`（导入评分草稿链路，入口 2026-08-17 下线后全表为空），导致结项后无法沉淀案例；draft 链路删除后须改读 `score_item`。
  - ⚠️ 历史坑 2（标书正文读取）：归档标书 `filePath` 为 `obs-direct:`（OBS 云存储引用）时，若直接用 `Files.readAllBytes` 当本地路径读会抛 `NoSuchFileException`，回退到 16 字符占位文本，导致 AI 返回 `criteriaId=0` 空片段、入库 0 条。必须经 `ObsShareUrlSigner.trySign` 签名预签名 URL 后下载字节；OBS 读取失败应上报失败通知而非虚假成功。
  - ⚠️ 历史坑 3（预签名 URL 二次编码 403）：经 `ObsShareUrlSigner.trySign` 拿到预签名 URL 后，**必须用 `RestTemplate.getForObject(URI, ...)` 下载而不能传 `String`**。传 String 会走 `UriTemplateHandler`，把预签名 URL 中已 percent 编码的 `%` 二次编码成 `%25`，导致 OBS 验签用的资源路径与签名时不匹配，抛 `SignatureDoesNotMatch` 403。曾用默认 RestTemplate mock 断言"测试全绿"却掩盖了真实网络行为——根因回归测试须用内嵌真实 HttpServer 断言服务器收到的路径保持单次编码（`%E5..` 而非 `%25E5..`）。
- **并发幂等**：
  - DB 层：`knowledge_case` 表建立 `(source_project_id, scoring_point_title)` 复合唯一索引。
  - 应用层：内存比对过滤 + 捕获 `DataIntegrityViolationException` 按幂等成功处理，防止重复结项造成脏数据。

