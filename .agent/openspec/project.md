# Project Context

## Purpose
西域数智化投标管理平台 (XiYu Digitalized Bid Management Platform) 旨在实现招投标过程的全链路数智化管理。核心目标包括：
- 提升标书编制效率（通过 `biddraftagent` 智能生成章节）。
- 实现标书文档的标准化转换与治理（通过 sidecar 处理 MarkDown 到各种格式）。
- 提供项目全周期的看板监控、风险预警与评分分析。
- 建立统一的招投标知识库与协作基准。

## Tech Stack
- **Frontend**: Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router 4 + Axios + ECharts + Sass
- **Backend**: Java 21 + Spring Boot 3.3 + JPA (Hibernate) + MySQL 8.0 + Redis 7 + Flyway + ArchUnit
- **Document Sidecar**: Python 3.12 + FastAPI + MarkItDown (Sidecar mode)
- **E2E Testing**: Playwright + Node.js
- **Deployment**: Docker + Docker Compose + Nginx

## Project Conventions

### Code Style
- **FP-Java Profile**: 后端严格区分 Pure Core (业务规则、校验、计算) 与 Imperative Shell (Controller, Service, Repository)。
- **Immutability**: 核心业务逻辑默认不可变，Java 使用 `record` 或 `final`，前端避免原地修改对象。
- **Naming**: 遵循 RESTful API 设计规范；后端包名按业务域划分（如 `com.xiyu.bid.calendar`）。
- **File Size**: 单个 Java 文件软上限 200 行，硬上限 300 行（棘轮门禁强制执行）。

### Architecture Patterns
- **Split-First Rule**: 先拆分 Application Service、Domain Policy、Mapper、Repository，禁止上帝类。
- **Real-API Only**: 彻底删除双模式，严禁使用 Mock。前端、后端、E2E 均以真实后端 API 为唯一事实源。
- **Project Access Guard**: 涉及 `projectId` 的接口必须通过统一的项目权限守卫（如 `ProjectAccessScopeService`）。

### Testing Strategy
- **TDD**: 遵循 Red -> Green -> Refactor 循环。
- **Architecture Tests**: 使用 ArchUnit 验证 FP-Java 约束与分层边界。
- **Maintainability Tests**: 监控 Service 的行数、协作者数量与公开方法数。
- **E2E**: 关键交互路径必须由 Playwright 覆盖。

### Git Workflow
- **Branching**: 主干分支为 `main`。Agent 开发使用 `agent/[name]-[task]` 分支。
- **Sync First**: 开启任务前必须 `fetch` 并基于 `origin/main` 检出；开发中及时 `rebase`。
- **Agent Locks**: 修改高冲突文件前需在 `.agent-locks.yml` 登记文件锁。

## Domain Context
- **Bidding Domain**: 涉及标书章节树、评分维度、预算金额、资质校验、项目状态流转。
- **Document Editing**: 核心在于章节内容的智能生成与异步转换。

## Important Constraints
- **XIYU_DEV_CONFIRMED**: 本地启动脚本带保护，必须显式设置环境变量 `XIYU_DEV_CONFIRMED=1`。
- **VITE_API_MODE**: 前端固定为 `api` 模式。
- **Database**: 使用 Flyway 管理 Schema，严禁手动修改数据库。

## External Dependencies
- **Document Conversion Sidecar**: 运行在 8000/8002 端口，负责文档解析。
- **MySQL/Redis**: 后端核心数据存储与缓存。
