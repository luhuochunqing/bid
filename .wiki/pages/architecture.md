---
title: 架构合成
space: engineering
category: architecture
tags: [架构, 前端, 后端, 数据库, Vue, SpringBoot, 技术栈]
sources:
  - .wiki/sources/implementation/西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx
  - docs/architecture/技术架构方案.md
  - docs/architecture/后端架构设计-SpringBoot.md
  - docs/research/API_INTEGRATION.md
  - docs/specs/MYSQL_8_DEPLOYMENT.md
  - backend/src/main/resources/application.yml
  - backend/src/main/java/com/xiyu/bid/tenderupload/README.md
  - backend/src/main/java/com/xiyu/bid/settings/README.md
  - CLAUDE.md
  - backend/README.md
backlinks:
  - _index
  - api-openapi
  - contract-constraints
  - implementation/attachment4-gap-matrix
  - implementation/attachment4-requirement-task-book
  - implementation/delivery-playbook
  - implementation/milestones
  - integration-oa-crm
  - lessons-learned
  - overview
  - requirements
  - roles-and-permissions
  - team-and-timeline
created: 2026-04-15
updated: 2026-05-28
health_checked: 2026-06-05
---
# 架构合成

## 1. 总体架构分层

平台采用五层分层架构，自顶向下依次为：

| 层次 | 名称 | 核心职责 |
|------|------|----------|
| L1 | **前端展示层** | Web 界面 + 响应式设计，支持 PC / 平板 / 移动端 |
| L2 | **业务应用层** | 标讯中心、投标项目、智能编制、知识库、AI 智能中心、数据分析 |
| L3 | **AI 能力层** | AI 分析、评分覆盖、合规雷达、竞争情报、智能装配、ROI 核算 |
| L4 | **数据服务层** | 项目数据、文档数据、知识数据、竞品数据、历史数据 |
| L5 | **基础设施层** | Spring Boot / Nginx、MySQL 8 + Redis、共享文件存储（NFS/NAS） |

各层通过 RESTful API 解耦通信。前端展示层与业务应用层之间通过统一 API 客户端交互；AI 能力层作为核心差异化竞争力，为业务应用层提供 10 大智能功能；数据服务层统一管理五类业务数据；基础设施层提供运行时环境、持久化存储和共享文件服务（不依赖对象存储）。

---

## 2. 前端架构

### 2.1 技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4 | 前端框架，采用 Composition API |
| Vite | 5.0 | 构建工具，极速 HMR 热更新 |
| Element Plus | 2.5 | 企业级 UI 组件库 |
| Pinia | 2.1 | 官方推荐轻量级状态管理 |
| Vue Router | 4.2 | 前端路由，支持懒加载 |
| ECharts | 5.4 | 数据可视化（雷达图、柱状图、折线图等） |
| Sass/SCSS | - | 样式预处理，支持变量、嵌套、混合 |

### 2.2 目录结构

```
src/
├── api/                  # API 层
│   ├── config.js         # API 配置（模式切换、基础 URL）
│   ├── client.js         # Axios 客户端封装
│   ├── mock.js           # 历史遗留 Mock 数据源，待清理，不作为交付路径
│   ├── modules/          # 按模块分组的 API 调用
│   │   ├── auth.js       # 认证模块
│   │   ├── tenders.js    # 标讯模块
│   │   ├── projects.js   # 项目模块
│   │   ├── knowledge.js  # 知识库模块
│   │   ├── fees.js       # 费用模块
│   │   ├── ai.js         # AI 分析模块
│   │   └── ...
│   └── index.js          # 统一导出
├── components/
│   ├── layout/           # 布局组件（MainLayout, Header, Sidebar）
│   ├── charts/           # ECharts 图表组件
│   ├── ai/               # AI 相关组件（11 个）
│   └── common/           # 通用组件（TaskBoard, AnimatedNumber 等）
├── config/
│   └── ai-prompts.js     # AI 功能配置（三大类别）
├── stores/               # Pinia 状态管理
│   ├── user.js           # 用户状态（登录、角色切换）
│   ├── project.js        # 项目状态
│   ├── bidding.js        # 标讯状态
│   └── bar.js            # BAR 投标资产台账状态
├── router/
│   └── index.js          # 路由配置（含权限守卫）
├── styles/               # 设计系统 CSS 变量
└── views/                # 页面组件（按功能模块组织）
    ├── Login.vue
    ├── Dashboard/
    ├── Bidding/
    ├── Project/
    ├── Knowledge/
    ├── Resource/
    ├── Analytics/
    └── System/
```

### 2.3 路径别名

`@` 别名指向 `src/` 目录，所有模块导入统一使用：

```js
import Something from '@/components/...'
```

### 2.4 状态管理模式

- 使用 Pinia + Composition API 风格的 `defineStore`
- 数据初始化来源：正式交付、联调、UAT、演示和验收均来自后端真实 API
- 用户状态持久化到 `localStorage`（token、用户信息）
- 路由守卫在 401 时自动调用 `userStore.resetSession()` 清除状态并重定向至登录页

---

## 3. 后端架构

### 3.1 技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.0 | 核心框架 |
| Java | 21 (LTS) | 开发语言 |
| Spring Security | - | 安全认证与授权 |
| JWT | - | 无状态令牌认证 |
| Spring Data JPA | - | 持久层框架 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 7 | 缓存与会话 |
| Flyway | - | 数据库迁移管理 |
| Lombok | - | 代码简化 |
| Actuator + Prometheus | - | 可观测性与监控 |

### 3.2 分层架构

```
Controller（REST 端点）
    ↓
Service（业务逻辑）
    ↓
Repository（数据访问 / JPA）
    ↓
Entity（JPA 实体 / 数据库映射）
```

- **Controller 层**：接收 HTTP 请求，参数校验，调用 Service 层，返回统一响应格式
- **Service 层**：承载核心业务逻辑，事务管理，跨模块协调
- **Repository 层**：基于 Spring Data JPA 的数据访问接口
- **Entity 层**：JPA 实体与数据库表结构的映射边界

### 3.3 安全架构

- 基于 Spring Security + JWT 的认证授权体系
- `JwtAuthenticationFilter` 拦截请求并验证令牌
- 基于角色的访问控制（RBAC）：admin、manager、sales、staff
- AOP 切面实现审计日志自动记录（`@Auditable` 注解）
- 速率限制（RateLimit）保护 API 端点

### 3.4 模块划分

后端按业务领域划分为 40+ 模块包，主要包括：

| 模块 | 包路径 | 功能 |
|------|--------|------|
| 认证授权 | `auth/` | JWT 过滤器、用户详情服务 |
| 标讯管理 | `tender/` | 标讯 CRUD、AI 评分 |
| 项目管理 | `project/` | 项目生命周期管理 |
| 任务协作 | `task/` | 任务分配与跟踪 |
| 知识库 | `qualification/`, `template/` | 资质、案例、模板管理 |
| 资源管理 | `resources/`, `fees/` | 费用、账户、BAR 资产 |
| AI 服务 | `ai/`, `scoreanalysis/` | AI 分析、评分覆盖 |
| 数据分析 | `analytics/` | 运营分析、审计分析 |
| 智能预警 | `alerts/` | 资质到期、项目风险预警 |
| 文档编辑 | `documenteditor/`, `documents/` | 在线编辑、智能装配 |
| 协作 | `collaboration/` | 实时协作、版本历史 |
| 批量操作 | `batch/` | 批量导入导出 |
| 导出 | `export/` | Excel 导出服务 |
| 竞争情报 | `competitionintel/` | 竞争对手分析 |

详细模块清单参见 [[modules]]。

---

## 4. 数据层

### 4.1 MySQL 8（主数据库）

- 存储所有业务实体数据（用户、标讯、项目、任务、资质、案例、模板等）
- 使用 Flyway 管理数据库迁移脚本（`backend/src/main/resources/db/migration-mysql/`）
- MySQL 主线 baseline：`B73__full_schema_baseline.sql`
- 默认连接：`jdbc:mysql://<host>:3306/xiyu_bid`

### 4.2 Redis（缓存与会话）

- JWT Token 黑名单管理
- 热点数据缓存（标讯列表、项目统计等）
- 速率限制计数器
- 会话辅助存储

### 4.3 共享文件存储（标书大文件）

- 标书文件落盘到共享路径（`app.tender-processing.storage-root`，默认 `/data/shared/tenders`）
- 应用节点只写共享目录，不将大文件作为长期数据写入数据库
- 元数据与处理状态写入 MySQL（`tender_file` / `tender_task` / `tender_task_dlq`）
- 通过 SHA-256 + 用户维度唯一约束实现幂等去重，降低重复处理成本

详细数据模型参见 [[data-model]]。

---

## 5. API 集成层

### 5.1 真实 API 单一路径

SOW V1.4 与项目协作口径要求后续开发、联调、演示、UAT 和验收均以真实后端 API 为唯一事实源。历史 Mock/demo 适配只作为待清理遗留，不作为正常开发或验收路径。

| 维度 | API 模式 |
|------|----------|
| 数据来源 | 后端 REST API |
| 后端依赖 | Spring Boot + MySQL 8 + Redis |
| 适用场景 | 开发联调、演示、UAT、生产 |
| 切换方式 | `cp .env.api .env` |

### 5.2 环境变量控制

通过 `VITE_API_MODE` 环境变量确认当前运行模式：

```bash
# API 模式
VITE_API_MODE=api
VITE_API_BASE_URL=http://127.0.0.1:18080
```

修改 `.env` 文件后需重启开发服务器。

### 5.3 统一 API 客户端

所有 API 请求通过 `src/api/client.js` 的 Axios 实例发出：

- **请求拦截器**：自动携带 `Authorization: Bearer <token>` 头
- **响应拦截器**：统一处理 401（跳转登录）、403（权限错误）、404（资源不存在）、500（服务器错误）
- **CORS 配置**：后端允许 `http://localhost:1314` 跨域访问

### 5.4 统一响应格式

```json
{
  "success": true,
  "data": { ... },
  "message": "操作成功"
}
```

### 5.5 OpenAPI/Swagger 接口规范

后端集成 `springdoc-openapi-starter-webmvc-ui` 2.3.0，自动从 `@RestController` 注解生成 OpenAPI 3.0 规范，对外提供机器可读的接口文档与 Swagger UI 调试门户：

| 入口 | 用途 |
|---|---|
| `/swagger-ui.html` | 可视化门户，支持 JWT Bearer 在线调试 |
| `/v3/api-docs` | OpenAPI JSON，给集成方代码生成 / Postman 导入 |
| `/v3/api-docs.yaml` | OpenAPI YAML 同上 |

主要满足客户「提供标准 API 接口、具备与 CRM/企业微信集成能力」要求（OA 集成已取消）。详情见 [[api-openapi]]。

---

## 6. 部署架构

平台采用前后端分离部署，支持容器化和弹性伸缩。详细部署流程、发布检查清单和回滚策略参见 [[deployment]]。
