---
title: 数据模型
space: engineering
category: reference
tags: [数据模型, 实体, JPA, 数据库, 真实API]
sources:
  - .wiki/sources/implementation/西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx
  - backend/src/main/java/com/xiyu/bid/entity/README.md
  - backend/src/main/java/com/xiyu/bid/dto/README.md
  - docs/architecture/后端架构设计.md
backlinks:
  - _index
  - architecture
  - implementation/attachment4-gap-matrix
  - implementation/attachment4-requirement-task-book
created: 2026-04-15
updated: 2026-06-21
health_checked: 2026-06-21
---
# 数据模型

## 1. 核心实体清单

| 实体 | 数据库表 | 关键字段 | 业务含义 |
|------|----------|----------|----------|
| **User** | `users` | id, username, name, role, departmentId, status | 系统用户与权限信息，支持 ADMIN / MANAGER / SALES / STAFF 等角色 |
| **Tender** | `tenders` | id, title, budget, region, industry, aiScore, winProbability, status, deadline | 标讯信息，记录招标公告的核心数据及 AI 评分 |
| **Project** | `projects` | id, code, name, tenderId, customerName, budget, status, progress, managerId | 投标项目，从标讯转化而来，承载投标全流程 |
| **Task** | `tasks` | id, projectId, title, type, priority, status, assigneeId, dueDate, deliverableCount | 项目任务，支持看板式管理（待办 / 进行中 / 待审核 / 已完成），可关联多个交付物 |
| **TaskDeliverable** | `task_deliverables` | id, taskId, name, deliverableType, size, fileType, storagePath, storageKey, version, uploaderId, uploaderName, createdAt | 任务交付物文件/文档，支持版本管理，5 种类型分类 |
| **Qualification** | `qualifications` | id, name, type, number, issuingAuthority, issueDate, expiryDate, status | 企业资质证书，支持到期预警 |
| **Case** | `cases` | id, title, customer, industry, amount, result, tags | 历史投标案例，支持按行业 / 标签检索复用 |
| **Template** | `templates` | id, name, category, description, fileUrl, version | 标书模板，支持一键使用和版本管理 |
| **AuditLog** | `audit_logs` | id, userId, action, entityType, entityId, details, timestamp | 操作审计日志，自动记录关键业务操作 |
| **Expense** | `expenses` | id, projectId, type, amount, status, applicant | 投标费用（保证金、标书费等），支持审批流程 |
| **Account** | `accounts` | id, platform, username, status, managerId | 招标平台账户，统一管理各大招标网站的登录凭据 |
| **TenderAnalysis** | `tender_analyses` | id, tenderId, customerHistory, requirementAnalysis, competitorAnalysis, riskWarning | AI 分析结果，包含客户历史、需求分析、竞争对手、风险预警 |
| **Deliverable** | `deliverables` | id, taskId, title, fileName, fileUrl, version | 任务交付物，关联文档文件 |
| **BidProcess** | `bid_processes` | id, projectId, currentStep, draftCompleted, reviewCompleted, sealCompleted, submitCompleted | 投标流程状态，跟踪从初稿到提交的全过程 |
| **ProjectCompetitor** | `project_competitors` | id, projectId, competitorName, price, strength, weakness, strategy | 项目竞争对手信息 |

---

## 2. 实体关系概述

核心实体之间的关系如下：

- **User -> Project**：用户作为项目经理（managerId）管理投标项目，一个用户可管理多个项目
- **User -> Task**：用户作为任务执行人（assigneeId）或创建人（creatorId）关联任务
- **Tender -> Project**：标讯转化为投标项目，一对一关系（tenderId 唯一）
- **Tender -> TenderAnalysis**：每条标讯对应一份 AI 分析结果，一对一关系
- **Project -> Task**：一个项目包含多个任务，级联删除
- **Project -> BidProcess**：每个项目对应一个投标流程实例
- **Project -> ProjectCompetitor**：一个项目可关联多个竞争对手
- **Task -> TaskDeliverable**：每个任务可产出多个交付物（文档/资质/技术方案/报价单等），级联删除
- **User -> Department**：用户归属部门，部门支持层级结构（parentId 自关联）
- **User -> Expense**：用户发起费用申请
- **User -> Account**：用户管理招标平台账户
- **User -> AuditLog**：用户的操作被自动记录到审计日志

---

## 3. 历史 Mock 数据结构（待清理）

前端 `src/api/mock.js` 中仍可见历史 `mockData` 对象。这些数据只作为遗留清理参考，不作为后续开发、联调、演示、UAT 或验收的数据事实源。正式数据模型、字段口径和迁移策略以真实后端 API、MySQL 8.0 表结构、SOW V1.4、蓝图确认件和接口清单为准。

| 字段 | 数据类型 | 说明 |
|------|----------|------|
| `users` | Array | 用户列表（小王、张经理、李总、李工） |
| `todos` | Array | 待办任务列表 |
| `tenders` | Array | 标讯列表（含 AI 评分和推荐理由） |
| `projects` | Array | 投标项目列表（含内嵌的 tasks 和 documents） |
| `projectScoreDrafts` | Object | 项目评分草稿 |
| `qualifications` | Array | 资质证书库 |
| `cases` | Array | 历史案例库 |
| `templates` | Array | 标书模板库 |
| `aiAnalysis` | Object | AI 分析结果（按项目 ID 索引） |
| `complianceCheck` | Object | 合规检查结果 |
| `competitionIntel` | Object | 竞争情报数据 |

历史 Mock 数据中的项目实体内嵌了 `tasks`（任务列表）和 `documents`（文档列表），而后端实体模型中这些是独立的关联表；后续以真实后端实体模型为准。

---

## 4. 状态枚举

### 4.1 标讯状态（TenderStatus）

| 枚举值 | 含义 | 历史 Mock 对应值 |
|--------|------|-------------|
| `NEW` | 新发现 | `new` |
| `CONTACTED` | 已联系 | - |
| `FOLLOWING` | 跟进中 | `following` |
| `QUOTING` | 报价中 | - |
| `BIDDING` | 投标中 | `bidding` |
| `ABANDONED` | 已放弃 | - |
| `CONVERTED` | 已转项目 | - |

### 4.2 项目状态（ProjectStatus）

| 枚举值 | 含义 | 历史 Mock 对应值 |
|--------|------|-------------|
| `DRAFT` | 草稿 | - |
| `PENDING` | 待审批 | - |
| `APPROVED` | 已审批 | - |
| `IN_PROGRESS` | 进行中 | `bidding` |
| `REVIEW` | 审核中 | `reviewing` |
| `SUBMITTED` | 已提交 | - |
| `WON` | 中标 | `won` |
| `LOST` | 未中标 | - |
| `CANCELLED` | 已取消 | - |

### 4.3 任务状态（TaskStatus）

| 枚举值 | 含义 | 前端映射 |
|--------|------|----------|
| `TODO` | 待办 | `todo` |
| `IN_PROGRESS` | 进行中 | `doing` |
| `REVIEW` | 待审核 | `review` |
| `COMPLETED` | 已完成 | `done` |
| `CANCELLED` | 已取消 | `cancelled` |

### 3.6 交付物类型（DeliverableType）

| 枚举值 | 含义 |
|--------|------|
| `DOCUMENT` | 文档 |
| `QUALIFICATION` | 资质文件 |
| `TECHNICAL` | 技术方案 |
| `QUOTATION` | 报价单 |
| `OTHER` | 其他 |

### 4.4 任务优先级（TaskPriority）

| 枚举值 | 含义 |
|--------|------|
| `URGENT` | 紧急 |
| `HIGH` | 高 |
| `MEDIUM` | 中 |
| `LOW` | 低 |

### 4.5 用户角色（UserRole）

| 枚举值 | 含义 |
|--------|------|
| `ADMIN` | 系统管理员 |
| `MANAGER` | 部门经理 |
| `SALES` | 销售 |
| `BIDDING` | 投标经理 |
| `TECHNICAL` | 技术人员 |
| `FINANCE` | 财务 |
| `STAFF` | 普通员工 |

更多术语定义参见 [[glossary]]。

---

## 5. 数据库迁移

项目使用 **Flyway** 进行数据库迁移管理：

- 迁移脚本存放于 `backend/src/main/resources/db/migration/`
- 命名规范：`V{版本号}__{描述}.sql`（例如 `V47__add_customer_manager.sql`）
- 当前已有 V1 ~ V56 的迁移版本，涵盖：
  - 基础表结构创建（用户、标讯、项目、任务等）
  - 知识库表（资质、案例、模板）
  - 资源管理表（费用、账户）
  - 审计日志表
  - 客户经理字段扩展（V47）
  - 历史来源兼容字段扩展（V48）
  - 历史数据导入（V49）
  - 投标结果表（V50）
  - **任务交付物表 + REVIEW 状态扩展（V56）** — 新增 `task_deliverables` 表，Task.Status 枚举新增 REVIEW
- 应用启动时 Flyway 自动检测并执行未应用的迁移
- E2E 测试环境使用 Testcontainers 进行 baseline 验证

---

## 6. 前后端数据映射

### 6.1 DTO 层的作用

后端通过 DTO（Data Transfer Object）层实现前后端数据契约的稳定性：

| DTO | 对应实体 | 用途 |
|-----|----------|------|
| `LoginRequest` | - | 登录请求参数 |
| `RegisterRequest` | - | 注册请求参数 |
| `AuthResponse` | User | 认证响应（用户信息 + Token） |
| `TenderDTO` | Tender | 标讯数据传输 |
| `ProjectDTO` | Project | 项目数据传输 |
| `TaskDTO` | Task | 任务数据传输（含 deliverableCount 字段） |
| `TaskDeliverableDTO` | TaskDeliverable | 交付物数据传输（id/name/type/version/uploaderName/createdAt） |
| `TaskDeliverableCreateRequest` | - | 交付物创建请求（@NotBlank name, @NotNull type） |
| `DeliverableCoverageDTO` | - | 交付物覆盖度（required/covered/percentage + TypeCoverage 列表） |
| `BidSubmissionResponse` | - | 标书提交响应（accepted/message/submittedAt/totalTasks/completedTasks/tasksWithDeliverables/gaps） |
| `QualificationDTO` | Qualification | 资质数据传输 |
| `CaseDTO` | Case | 案例数据传输 |
| `TemplateDTO` | Template | 模板数据传输 |
| `ApiResponse<T>` | - | 统一 API 响应包装 |
| `DataScopeConfigPayload` | - | 数据范围配置请求 |
| `DataScopeConfigResponse` | - | 数据范围配置响应 |

### 6.2 映射原则

- Entity 只描述数据库持久化模型，不承载控制器流程
- DTO 只做字段承载与映射，不承载业务规则
- Controller 层接收 DTO、调用 Service、返回 DTO
- Service 层在 Entity 和 DTO 之间转换
- 前端只应通过真实 API DTO 与后端交互；历史 Mock 字段差异不得作为新增开发或验收依据
