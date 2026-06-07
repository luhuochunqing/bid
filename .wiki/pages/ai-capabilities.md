---
title: AI 能力
space: engineering
category: module
tags: [AI, 智能, 大模型, 合规, 评分, 竞争情报, 智能装配]
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
  - business-process
  - contract-constraints
  - implementation/attachment4-gap-matrix
  - implementation/attachment4-requirement-task-book
  - implementation/attachment6-function-list-trace
  - modules
  - requirements
created: 2026-04-15
updated: 2026-05-31
health_checked: 2026-06-05
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
- **核心能力**：评分标准与标书内容逐项匹配分析
- **覆盖状态分类**：
  - **已覆盖**（covered）：评分点在标书中有完整响应
  - **部分覆盖**（partialCovered）：评分点有响应但不完整
  - **未覆盖**（uncovered）：评分点在标书中缺失
- **覆盖率阈值**：默认 80%，低于阈值自动高亮提醒
- **后端实现**：评分分析模块（`com.xiyu.bid.scoreanalysis`）
  - 5 个标准评分维度：技术能力（30%）、财务实力（25%）、团队经验（20%）、历史业绩（15%）、合规性（10%）
  - 加权评分算法：总分 = 各维度分数 x 维度权重
  - 风险等级自动判定：80-100 低风险、60-79 中等风险、0-59 高风险
  - 支持项目历史趋势跟踪与多项目对比分析

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

- **角色定位**：招投标法规专家（熟悉《招标投标法》及相关规定）
- **核心能力**：全方位检测标书合规性，规避废标风险
- **6 维度检测**：

| 维度 | 严重等级 | 检查内容 |
|------|----------|----------|
| 格式检查 | MEDIUM | 页码、排版、装订规范 |
| 完整性检查 | HIGH | 必要章节和材料是否齐全 |
| 资质检查 | HIGH | 营业执照、案例证明、授权书等 |
| 签章检查 | HIGH | 法人签章、授权代表签章、骑缝章 |
| 时效检查 | CRITICAL | 截止时间、有效期、资质到期 |
| 报价合规 | HIGH | 报价格式、计算准确性、大小写一致 |

- **后端实现**：合规检查模块（`com.xiyu.bid.compliance`）
  - 灵活规则引擎：JSON 格式规则定义，5 种规则类型（QUALIFICATION / DOCUMENT / FINANCIAL / EXPERIENCE / DEADLINE）
  - 风险评分算法：CRITICAL 100 分、HIGH 75 分、MEDIUM 50 分、LOW 25 分
  - 整体状态判定：COMPLIANT（全部通过）、WARNING（失败率 < 20%）、PARTIAL_COMPLIANT（20-50%）、NON_COMPLIANT（>= 50% 或有 CRITICAL 失败）
  - 检查结果持久化，支持历史查询与审计
  - 支持导出检查报告

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
| 标书编制 | 智能装配、合规雷达、评分点覆盖、版本管理 | 标书生成、质量保障、过程管理 |
| 投标提交 | 合规雷达 | 提交前最终合规复核 |
| 结果闭环 | 竞争情报、AI 分析 | 竞对信息更新、案例沉淀分析 |
