# ARCHITECTURE.md — 顶层架构地图

定义业务领域、包分层规则、纯核心/副作用边界、数据库迁移规范。

## 技术栈

- **前端**：Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router 4 + Axios + ECharts + Sass
- **后端**：Java 21 + Spring Boot 3.2 + JPA (Hibernate) + MySQL 8.0 + Redis 7 + Flyway + ArchUnit（以 `backend/pom.xml` 为唯一源）
- **E2E 测试**：Playwright + Node.js (VITE_API_MODE=api)
- **文档转换**：Python 3.12 + FastAPI + MarkItDown (sidecar 模式)
- **部署**：Docker + Docker Compose + nginx

## Agent Contract（FP-Java Profile）

本项目默认采用 **FP-Java Profile**：

1. 先分清 Pure Core 和 Imperative Shell。
2. 业务规则、校验、金额/状态/权限计算放入可单测的纯核心。
3. Controller / Application Service / Repository 只做取数、事务、保存、消息和边界转换。
4. 纯核心不得修改入参，不得读写数据库、API、文件、时间、随机数或日志。
5. 预期内业务失败用 Result / Optional / ValidationResult 返回，不用异常做业务分支。
6. DTO、VO、命令对象、领域值对象优先用 record 或 final 不可变对象。
7. 纯核心业务方法必须返回值，不得用 `void` 方法隐藏状态变化。
8. JPA Entity、框架适配类可按框架约束例外处理，但不得承载复杂业务规则。
9. 默认遵守 Split-First Rule：先拆 Application Service、Domain Policy、Mapper、Repository/Gateway，再实现。
10. 单个 Java 文件软上限 200 行、硬上限 300 行；超过上限前必须先拆分职责。
11. 完成前必须说明：纯核心在哪里，副作用在哪里，跑了哪些验证。

## 架构门禁口径

- **纯核心**仍禁止显式依赖 `System` 等隐式输入；Java 枚举 `values()` 编译器生成的 `System.arraycopy` 属于合成字节码误报，由门禁排除。
- **可维护性约束**：受保护模块的防上帝类门禁由 `MaintainabilityArchitectureTest` 执行。
- **标书生成 Agent**：`com.xiyu.bid.biddraftagent.domain` 是纯核心，`application` 只做 run 编排和写入计划，`infrastructure/documenteditor` 负责实际写入章节树。

## 事务边界三原则（CO-325）

> 2026-06-25 新增。起因：`PATCH /api/tenders/430/crm-opportunity` 500 错误，
> 根因是 `@Transactional` 事务传播陷阱：子方法抛 RuntimeException → Spring 标记主事务 rollback-only →
> 调用方 try-catch 吞异常 → 事务提交时抛 `UnexpectedRollbackException` → 500。

### 强制规则

1. **禁止"类级 `@Transactional` + `@Auditable` 方法"组合**
   - `@Auditable` 切面的 finally 块在事务中执行审计写入，如果主事务被标记 rollback-only，审计写入也会失败
   - ArchUnit RULE 17 自动拦截（白名单：现有 12 个违规类需逐步收敛）
   - 新代码必须用方法级 `@Transactional` 或将子调用改为 `REQUIRES_NEW`

2. **"附加操作"必须 `REQUIRES_NEW`**
   - 审计日志、回填数据、发通知等"附加操作"必须使用 `Propagation.REQUIRES_NEW`
   - 独立事务执行，失败不影响主流程
   - 参考：`TenderEvaluationBackfillService.backfillFromCrmLink`、`TenderEvaluationCustomerInfoDeleteService`

3. **禁止在 `@Transactional` 方法中 try-catch RuntimeException 后继续执行**
   - Spring 事务拦截器在 RuntimeException 抛出时标记 rollback-only，即使调用方 catch 了异常，事务状态也无法恢复
   - 必须 catch 时，必须配合 `REQUIRES_NEW` 隔离子调用事务
   - 否则事务提交时抛 `UnexpectedRollbackException` → 500

### 测试要求

- 涉及 `@Transactional` 的 Service 方法，应继承 `AbstractTransactionBoundaryTest` 编写事务边界测试
- 测试方法用 `@Transactional(propagation = Propagation.NEVER)`，强制被测方法自管事务
- 如果被测方法有事务泄漏（未正确提交或回滚），测试会立即失败

## 数据库迁移规范

### 迁移文件命名

- 正向迁移: `V{版本号}__{描述}.sql`
- 回滚脚本: `U{版本号}__{描述}.sql`（必须与正向迁移同名同描述）

### 回滚脚本要求

- **必写回滚**：所有新的 Flyway 迁移文件必须附带对应的 U 脚本
- **安全性**：回滚脚本中必须包含前置检查（`DROP COLUMN IF EXISTS`、`DROP TABLE IF EXISTS` 等）
- **PR 备注**：U 脚本在创建时需要在注释中注明 PR 编号
- **历史补全**：历史迁移的回滚脚本可选，但推荐逐步补全（当前活跃迁移已推进至 V1081+）

### 数据库运维命令

- `npm run db:dev-repair` — 修复本地 Flyway 状态（rebase/sync 后版本冲突或 checksum 漂移）
- `npm run db:generate-rollback` — 为所有正向迁移自动生成回滚脚本骨架
- `npm run db:generate-schema` — 从迁移生成 `docs/generated/db-schema.md` 机器真相

## 参考文档索引

| 概念 | 位置 |
|---|---|
| 后端架构设计 | `docs/architecture/后端架构设计.md` |
| 后端架构设计（SpringBoot 篇） | `docs/architecture/后端架构设计-SpringBoot.md` |
| 技术架构方案 | `docs/architecture/技术架构方案.md` |
| 产品蓝图 V1.0 | `docs/architecture/西域数智化投标管理平台 - 产品蓝图-V1.0.md` |
| 8 模块实现计划/完成报告 | `docs/architecture/8-modules-*.md` |
| 合成知识页（架构） | `.wiki/pages/architecture.md`、`.wiki/pages/modules.md` |
| 数据模型（合成视图） | `.wiki/pages/data-model.md` |
| **数据库结构（机器生成真相）** | `docs/generated/db-schema.md` |

## 新增架构设计请放

- 设计稿：`docs/architecture/`
- 执行计划：`docs/exec-plans/active/`
- 涉及库表：同步刷新 `docs/generated/db-schema.md`（`npm run db:generate-schema`）
