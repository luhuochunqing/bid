---
title: 模块目录
space: engineering
category: module
tags: [模块, 前端, 后端, 功能清单, 边界]
sources:
  - README.md
  - CLAUDE.md
  - backend/MODULES_QUICK_REFERENCE.md
  - backend/README.md
  - backend/src/main/java/com/xiyu/bid/tenderupload/README.md
  - backend/src/main/java/com/xiyu/bid/settings/README.md
  - src/api/modules/marketInsight.js
  - src/views/Bidding/list/useMarketInsight.js
  - backend/src/main/java/com/xiyu/bid/marketinsight/controller/MarketInsightController.java
  - backend/src/main/java/com/xiyu/bid/marketinsight/service/MarketInsightService.java
  - src/api/modules/dashboard.js
backlinks:
  - _index
  - architecture
  - business-process
  - contract-constraints
  - dashboard-gap-analysis
  - design-system
  - implementation/attachment4-gap-matrix
  - implementation/attachment4-requirement-task-book
  - implementation/attachment6-function-list-trace
  - implementation/delivery-playbook
  - implementation/weekly-status
  - overview
  - requirements
  - team-and-timeline
  - workflow-form-center
created: 2026-04-15
updated: 2026-05-31
health_checked: 2026-06-27
---
# 模块目录

## 1. 模块总览

平台采用前后端分离架构，包含 **前端 7 大功能模块** 和 **后端 40 个技术模块**。

- **前端**：Vue 3 + Vite + Element Plus + Pinia，按功能域划分为工作台、标讯中心、投标项目、知识资产、资源管理、数据分析、AI 智能中心、系统设置共 8 个一级入口（其中 AI 智能中心与系统设置合计为第 7、8 模块）。
- **后端**：Spring Boot 3.2 + Java 21 + MySQL 8 + Redis，按领域驱动设计将业务拆分为标讯域、项目域、文档域、知识域、资源域、AI 域、协作域和基础域共 8 大域。

---

## 2. 前端功能模块详细

### 2.1 工作台（Dashboard）

路由：`/dashboard`

| 功能 | 说明 |
|------|------|
| 指标卡片 | 投标数量、中标率、中标金额等关键统计 |
| 待办清单 | 按优先级分类展示（紧急/评审/上传/审批） |
| 项目日历 | 全局投标日历，自动提取截标日、开标日等关键节点 |
| 进行中项目 | 个人负责的活跃项目列表与进度概览 |
| 客户跟进卡片 | 客户最新动态与跟进提醒 |

角色化视图：销售、投标经理、管理层看到不同内容。

### 2.2 标讯中心（Bidding）

路由：`/bidding`

| 功能 | 说明 |
|------|------|
| 标讯列表 | 外部标讯统一入库，多维度检索筛选（区域、产品线、预算等） |
| AI 评分推荐 | AI 智能评分（0-100）与风险等级判定，优先展示高价值标讯 |
| 标讯详情与分析 | 招标文件解析、关键信息提取、相关案例推荐 |
| 采购方规律分析 | 超前预测与市场洞察，识别高潜力机会 |

`Trend` 功能口径（市场洞察）：
- 为真实后端能力，前端调用 `GET /api/market-insight/insight` 获取 `industryTrends/purchaserPatterns/forecastTips`。
- 后端基于标讯数据计算趋势（当前 3 个月 vs 前 3 个月），输出 `up/down/stable`、增长率、热度等级。
- 前端保留默认示例与失败兜底文案；因此“页面有数据”不等于“本次接口一定成功返回真实数据”。

### 2.3 投标项目（Project）

路由：`/project`

| 功能 | 说明 |
|------|------|
| 项目立项 | 三步表单引导式创建（基本信息、团队组建、任务规划） |
| 项目详情 | 项目全信息管理、进度跟踪、团队成员 |
| 任务看板 | 四阶段看板（待办、进行中、审核中、已完成） |
| 招标文件解析 | 项目级上传并解析招标文件，产出需求项和章节快照，供任务拆解与 AI 初稿复用 |
| 任务拆解 | 根据已解析需求项生成商务、技术、资料、评分复核等协作任务 |
| AI 检查 | AI 合规检查 + AI 质量检查双引擎 |
| 结果录入 | 中标/未中标登记、竞对信息记录 |

### 2.4 知识资产（Knowledge）

路由：`/knowledge/*`

| 子模块 | 路由 | 说明 |
|--------|------|------|
| 资质库 | `/knowledge/qualification` | 企业资质集中管理，到期自动提醒，资质借阅流程 |
| 案例库 | `/knowledge/case` | 历史成功案例归档，按行业/产品/结果标签分类，全文检索 |
| 模板库 | `/knowledge/template` | 6 大分类标书模板，版本控制，使用次数统计，一键应用 |

### 2.5 资源管理（Resource）

路由：`/resource/*`

| 子模块 | 路由 | 说明 |
|--------|------|------|
| 费用管理 | `/resource/expense` | 保证金、标书费、差旅费等费用申请与台账，保证金归还提醒 |
| 平台账户管理（BAR） | `/resource/account` | 招标平台账户集中管理，账户借阅与审计轨迹，BAR 投标资产台账 |

### 2.6 数据分析（Analytics）

路由：`/analytics/dashboard`（需 admin/manager 角色）

| 功能 | 说明 |
|------|------|
| 管理看板 | 投标数量趋势、中标率趋势、中标金额趋势 |
| 多维度分析图表 | 按区域、产品线、销售团队、客户类型、竞争对手等维度分析 |
| 数据穿透下钻 | 点击数据查看项目详情、过程文件、团队信息 |

`Trend` 功能口径（经营看板）：
- 趋势图接口为 `GET /api/analytics/trends`，用于管理看板趋势可视化与下钻。

### 2.7 AI 智能中心

路由：`/ai-center`

提供 3 大类 9 项 AI 能力，详细说明参见 [[ai-capabilities]]。

### 2.8 系统设置（System）

路由：`/system/settings`

| 功能 | 说明 |
|------|------|
| 用户管理 | 用户创建、编辑、禁用 |
| 角色权限配置 | 按组织架构配置系统权限与数据权限（admin/manager/staff） |
| 系统参数配置 | 预警时间、分发规则、集成参数等系统级配置 |
| 操作日志 | 全部关键操作的审计轨迹 |
| 流程表单配置 | 管理员配置表单模板、字段 schema（OA 绑定已取消）；详见 [[workflow-form-center]] |

---

## 3. 后端模块按领域分组

### 3.1 标讯域

| 模块 | 包路径 | 说明 |
|------|--------|------|
| tender | `com.xiyu.bid.tender` | 标讯管理（CRUD、状态流转、AI 分析触发） |
| bidresult | `com.xiyu.bid.bidresult` | 投标结果管理（中标登记、结果分析） |
| tenderupload | `com.xiyu.bid.tenderupload` | 大标书异步上传受理队列（上传会话、任务入队、状态追踪） |

**Tender 状态枚举**（产品蓝图 §4.2.2）：PENDING_ASSIGNMENT（待分配）→ TRACKING（跟踪中）→ EVALUATED（已评估）→ BIDDING（投标中）→ WON（已中标）/ LOST（未中标），各阶段均可弃标进入 ABANDONED（已放弃）。

**API 端点**：9 个（列表、详情、创建、更新、删除、AI 分析、按状态/来源查询、统计）

异步上传端点：

| Method | Path | 用途 |
|--------|------|------|
| POST | `/api/tenders/upload-init` | 创建上传会话，返回上传标识与目标路径 |
| POST | `/api/tenders/upload-complete` | 完成上传并写入处理队列，立即返回 `taskId` |
| GET | `/api/tenders/tasks/{taskId}` | 查询状态、排队深度与预计开始时间 |

### 3.2 项目域

| 模块 | 包路径 | 说明 |
|------|--------|------|
| project | `com.xiyu.bid.project` | 项目管理（CRUD、状态流转、团队管理） |
| projectworkflow | `com.xiyu.bid.projectworkflow` | 项目工作流（审批流程、阶段推进） |
| projecttenderbreakdown | `com.xiyu.bid.projecttenderbreakdown` | 项目级招标文件解析入口，提供 readiness 检查和上传解析 API |
| task (core) | `com.xiyu.bid.task.core` | 任务核心策略（状态流转守卫、交付物关联规则、标书提交校验） |
| task (entity) | `com.xiyu.bid.task.entity` | 任务交付物实体（TaskDeliverable + V56 迁移） |
| task (repository) | `com.xiyu.bid.task.repository` | 交付物数据访问层 |
| task (dto) | `com.xiyu.bid.task.dto` | 交付物 DTO / Assembler |
| task (service) | `com.xiyu.bid.task.service` | 交付物服务编排 + 标书提交流程 |

**Project 状态枚举**：INITIATED、PREPARING、REVIEWING、SEALING、BIDDING、ARCHIVED

**Task.Status 枚举（扩展后）**：TODO、IN_PROGRESS、REVIEW、COMPLETED、CANCELLED

**API 端点**：19 个（原 13 个 + 新增 6 个）

新增端点：

| Method | Path | 用途 |
|--------|------|------|
| GET | `/api/projects/{id}/tender-breakdown/readiness` | 检查项目级招标文件解析配置是否就绪 |
| POST | `/api/projects/{id}/tender-breakdown` | 上传并解析项目招标文件，写入需求项和章节快照 |
| POST | `/api/projects/{id}/tasks/decompose` | 根据已解析招标文件结果生成项目任务 |
| GET | `/projects/{id}/tasks/{taskId}/deliverables` | 获取任务交付物列表 |
| POST | `/projects/{id}/tasks/{taskId}/deliverables` | 上传交付物 |
| DELETE | `/projects/{id}/tasks/{taskId}/deliverables/{delId}` | 删除交付物 |
| GET | `/projects/{id}/tasks/{taskId}/deliverables/coverage` | 交付物覆盖度 |
| POST | `/projects/{id}/submit-to-bid-document` | 提交至标书编写 |
| GET | `/projects/{id}/bid-process-status` | 标书流程状态 |

### 3.3 文档域

| 模块 | 包路径 | 说明 |
|------|--------|------|
| documents | `com.xiyu.bid.documents` | 文档装配服务（智能装配、章节生成） |
| documenteditor | `com.xiyu.bid.documenteditor` | 文档编辑器（协同编辑、在线审批） |
| documentexport | `com.xiyu.bid.documentexport` | 文档导出（PDF/Word 格式输出） |
| versionhistory | `com.xiyu.bid.versionhistory` | 版本历史（变更追踪、差异对比） |

### 3.4 知识域

| 模块 | 包路径 | 说明 |
|------|--------|------|
| qualification | `com.xiyu.bid.qualification` | 资质库管理（证照、到期提醒、借阅） |
| casework | `com.xiyu.bid.casework` | 案例库管理（归档、标签、检索） |
| template | `com.xiyu.bid.template` | 模板库管理（分类、版本、应用） |
| workflowform | `com.xiyu.bid.workflowform` | 流程表单中心（模板配置、版本快照、OA 触发、回调结果、审批通过后应用业务） |
| formengine | `com.xiyu.bid.formengine` | **动态表单自定义引擎**（Schema 驱动渲染、角色可见性、跨字段验证、多租户覆盖、提交审计；V140-V143） |

### 3.5 资源域

| 模块 | 包路径 | 说明 |
|------|--------|------|
| resources | `com.xiyu.bid.resources` | 资源管理基础模块 |
| fees | `com.xiyu.bid.fees` | 费用管理（申请、台账、保证金提醒） |
| platform | `com.xiyu.bid.platform` | 平台账户管理（账户登记、借阅、BAR 台账） |

### 3.6 AI 域

| 模块 | 包路径 | 说明 |
|------|--------|------|
| docinsight | `com.xiyu.bid.docinsight` | **文档智能引擎**（通用解析、结构化切片、证据锚定） |
| ai | `com.xiyu.bid.ai` | AI 核心服务（Provider 抽象、异步分析） |
| compliance | `com.xiyu.bid.compliance` | 合规检查（6 维度检测、风险评估） |
| competitionintel | `com.xiyu.bid.competitionintel` | 竞争情报（竞对管理、竞争分析） |
| scoreanalysis | `com.xiyu.bid.scoreanalysis` | 评分分析（多维度评分、趋势、对比） |
| roi | `com.xiyu.bid.roi` | ROI 核算（投入产出分析） |

### 3.7 协作域

| 模块 | 包路径 | 说明 |
|------|--------|------|
| collaboration | `com.xiyu.bid.collaboration` | 协作服务（任务分配、进度追踪） |
| calendar | `com.xiyu.bid.calendar` | 日历服务（日程管理、节点提醒） |
| alerts | `com.xiyu.bid.alerts` | 智能预警（多级预警、到期提醒） |

### 3.8 基础域

| 模块 | 包路径 | 说明 |
|------|--------|------|
| auth | `com.xiyu.bid.auth` | 认证授权（JWT 过滤器、用户详情服务） |
| config | `com.xiyu.bid.config` | 配置类（Security、JWT、CORS、Async、RateLimit） |
| entity | `com.xiyu.bid.entity` | 公共实体（Tender、Project 等核心实体） |
| dto | `com.xiyu.bid.dto` | 公共数据传输对象 |
| repository | `com.xiyu.bid.repository` | 公共数据访问层 |
| service | `com.xiyu.bid.service` | 公共业务逻辑层 |
| controller | `com.xiyu.bid.controller` | 公共 REST 控制器 |
| exception | `com.xiyu.bid.exception` | 统一异常处理 |
| util | `com.xiyu.bid.util` | 工具类 |
| annotation | `com.xiyu.bid.annotation` | 自定义注解（@Auditable 等） |
| aspect | `com.xiyu.bid.aspect` | AOP 切面（审计日志切面） |
| audit | `com.xiyu.bid.audit` | 审计日志服务 |
| batch | `com.xiyu.bid.batch` | 批量操作与定时任务 |
| settings | `com.xiyu.bid.settings` | 系统设置管理（含 AI Provider 配置与运行时权限） |
| export | `com.xiyu.bid.export` | 通用导出服务（Excel 等） |

---

## 4. 模块与业务流程映射

| 业务阶段 | 前端模块 | 后端核心模块 |
|----------|----------|-------------|
| 标讯获取 | 标讯中心 | tender, bidresult, alerts |
| 项目立项 | 投标项目 | project, projectworkflow, auth |
| 任务分解 | 投标项目（看板） | task, collaboration, ai |
| 标书编制 | 知识资产 + AI 中心 | documents, documenteditor, template, compliance, scoreanalysis |
| 投标提交 | 投标项目（提交） | project, documentexport, audit |
| 结果闭环 | 资源管理 + 数据分析 | bidresult, competitionintel, fees, export |
| 全局支撑 | 工作台 + 系统设置 | auth, config, settings, calendar, alerts, batch |

各阶段的业务流程详情参见 [[business-process]]。
