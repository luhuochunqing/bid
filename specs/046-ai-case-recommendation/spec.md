# Feature Specification: AI 智能推荐案例（案例库与语义推荐引擎）

**Feature Branch**: `046-ai-case-recommendation`

**Created**: 2026-08-18

**Status**: Draft

**Input**: User description: "基于《AI 智能推荐案例 — 抽屉详情（PRD）》，构建真实可生产的案例库数据模型与评分项级语义推荐引擎，分阶段推进推荐与标书复用闭环。"

## 背景与目标

在投标文件编制过程中，编制人员需要根据招标文件的各项评分标准（如“技术方案完整性”、“应急响应方案”等）编写针对性的应答文本。目前现状：
1. 投标人员常需在海量历史投标文档中手工翻找类似案例，效率低、重复劳动重；
2. PRD 原型虽然定义了交互流程，但前端仅使用硬编码 mock 数据与内存计算，缺乏后端真实数据底座与语义向量推荐链路；
3. 原 PRD 设计中存在“项目级向量整体匹配”导致的语义稀释问题，需要纠偏为**“评分项详情（`score_item.detail`）级精准语义匹配”**；
4. 需要分阶段推进：**Phase 1 案例库底座与数据层** -> **Phase 2 评分项级语义推荐引擎** -> **Phase 3 标书编辑器复用与撤销闭环**。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 案例库数据模型与基础管理（Priority: P1）

系统建立持久化的标书应答案例库（`case_library`），沉淀历史优质投标应答内容（评分项标题、评分点、项目类型、客户类型、中标状态、应答原文、摘要及复用计数），为推荐引擎提供数据源。

**Why this priority**: 数据底座是整个 AI 推荐功能的核心前提，没有结构化的案例库数据，任何算法和前端交互都无法落地。

**Independent Test**:
- 通过 Flyway 迁移建立 `case_library` 表，支持案例的入库、查询详情、状态流转（`ACTIVE` / `DISABLED`）及 `reuse_count` 原子自增。

**Acceptance Scenarios**:
1. **Given** 数据库已执行最新迁移，**When** 创建/录入历史案例，**Then** 案例成功持久化，包含评分项标题、评分点、应答原文、摘要、项目类型等必要字段。
2. **Given** 存在一条已启用的案例，**When** 通过 ID 查询案例详情，**Then** 正确返回案例全部元数据与应答原文。

---

### User Story 2 - 基于评分项详情的语义推荐 API（Priority: P1）

在指定投标项目中，用户针对当前招标文件的具体评分项（如“A1 总体架构设计”），系统调用推荐接口，基于评分项的描述文本（`score_item.detail`）与案例库中的应答/要求向量或关键词进行语义计算，按真实匹配度降序返回推荐案例列表。

**Why this priority**: 核心算法引擎。确保推荐结果来自真实的语义匹配与算法打分，而非前端硬编码或静态随机数。

**Independent Test**:
- 调用 `POST /api/projects/{projectId}/cases/recommend` 传入 `scoreItemId` 或关键词，能够返回按相似度评分排序的推荐案例列表，并在无匹配时优雅降级。

**Acceptance Scenarios**:
1. **Given** 项目已完成 AI 评分标准解析（存在 `ScoreItem` 记录），**When** 用户选择某一评分项请求推荐，**Then** 系统提取该评分项 `detail` 计算与案例库的语义相似度，过滤 `status = 'ACTIVE'` 的案例，按综合匹配度从高到低返回推荐列表（含高亮匹配点）。
2. **Given** 用户在推荐抽屉中输入自定义搜索关键词，**When** 发起推荐请求，**Then** 结合关键词与评分项上下文进行混合检索召回。

---

### User Story 3 - 案例详情查看与复用计数（Priority: P2）

用户在推荐列表中点击卡片可查看案例完整应答原文、项目背景与匹配点分布；当用户确认复用该案例时，系统记录复用行为并原子递增 `reuse_count`。

**Why this priority**: 建立案例价值沉淀与度量闭环，为后续复用热度排序与模型优化提供统计依据。

**Independent Test**:
- 调用 `GET /api/cases/{caseId}` 返回完整详情；调用 `POST /api/cases/{caseId}/reuse` 记录复用日志并原子更新 `reuse_count`。

**Acceptance Scenarios**:
1. **Given** 用户请求查看 ID 为 1001 的案例，**When** 案例存在且状态为 ACTIVE，**Then** 返回案例完整结构化详情。
2. **Given** 用户复用 ID 为 1001 的案例，**When** 触发复用，**Then** `case_library.reuse_count` 原子 +1 并持久化。

---

### User Story 4 - 标书章节树对接与内容写入闭环（Priority: P3）

在前端投标文档编辑区，用户选择复用后弹出定位选择弹窗，选择目标章节（默认推荐当前评分项关联章节），支持「追加到末尾」或「覆盖当前章节」，内容写入标书文档编辑器并支持撤销操作。

**Why this priority**: 最终的业务交付价值，依赖前端文档编辑器已有章节树与事务机制。

**Independent Test**:
- 在标书编辑器中选择案例复用，目标章节成功追加或替换对应段落文本，点击撤销可恢复先前文档状态。

**Acceptance Scenarios**:
1. **Given** 用户在推荐抽屉点击「复用」，**When** 弹出章节选择并确认追加，**Then** 案例 `responseText` 追加到当前文档选中章节，编辑器内容即时刷新。

---

## 边界与异常场景 (Edge Cases)

1. **项目未做评分解析**：若当前项目尚未提取任何 `ScoreItem`，推荐抽屉应友好提示“请先完成 AI 评分标准解析”，或允许纯关键词检索。
2. **评分项描述极短或为空**：当 `score_item.detail` 仅有几个字时，结合 `dim`（维度）和 `scoring_point_title` 补充语义上下文。
3. **案例库冷启动与无匹配结果**：当库中案例较少或相似度均低于阈值（如 < 0.3）时，返回空列表并提示“暂无高匹配度参考案例，建议调整关键词”，不硬塞无关案例。
4. **并发复用计数一致性**：多用户同时复用同一案例时，`reuse_count` 采用数据库行锁或 `update case_library set reuse_count = reuse_count + 1 where id = ?` 原子更新，避免并发丢失。

---

## 需求清单 (Requirements)

### 数据模型需求 (Data Model)

- **FR-001**: 系统必须通过 Flyway 迁移创建 `case_library` 表，包含主键 `id`、评分项分类 `scoring_title`、评分点 `scoring_point_title`、项目类型 `project_type`、客户类型 `customer_type`、中标结果 `bid_result`（WON/LOST/UNKNOWN）、应答原文 `response_text`、应答摘要 `response_text_summary`、原始需求 `requirement_raw`、来源项目信息 `source_project_id`/`source_project_name`、状态 `status`（ACTIVE/DISABLED）、复用次数 `reuse_count`、匹配标签 `matched_items`（JSON）、时间戳与审计字段。
- **FR-002**: 系统必须支持存储或即时计算案例的语义表征，便于向量或文本相关度检索。

### 接口与算法需求 (API & Recommendation)

- **FR-003**: 系统必须提供 `GET /api/projects/{projectId}/score-parse/items`（或沿用已有接口）供前端加载当前项目的评分项下拉列表。
- **FR-004**: 系统必须提供 `POST /api/projects/{projectId}/cases/recommend` 接口，入参包含 `scoreItemId`（可选）、`keyword`（可选）、`page`、`size`，返回按匹配度排序的推荐案例列表。
- **FR-005**: 推荐算法**必须**以当前评分项的详细要求（`ScoreItem.detail`）为主要语义比对源，而非粗粒度使用整个项目级向量。
- **FR-006**: 系统必须提供 `GET /api/cases/{caseId}` 查询单个案例的完整详情。
- **FR-007**: 系统必须提供 `POST /api/cases/{caseId}/reuse` 记录复用行为、更新 `reuse_count`。

### 交互与编辑器闭环需求 (UI & Integration - Phase 3)

- **FR-008**: 前端在投标文件编制区域提供「AI智能推荐案例」抽屉入口，支持评分项联动切换与关键词过滤。
- **FR-009**: 点击案例支持弹窗查看详细比对，支持高亮匹配条款。
- **FR-010**: 复用弹窗必须接入标书章节树，支持「追加到末尾」与「覆盖」两种写入模式。

---

## 阶段规划与交付路径 (Phased Plan)

- **Phase 1（本次核心）**：`case_library` 数据模型、Flyway 迁移、Entity/Repository/DTO、单元测试与 CRUD 管理基础。
- **Phase 2（本次核心）**：基于 `ScoreItem.detail` 的语义推荐 Service、Controller 接口及算法集成测试（含相似度排序与过滤）。
- **Phase 3（后续扩展）**：前端推荐抽屉组件、详情弹窗、标书章节树联动与写入/撤销闭环。
