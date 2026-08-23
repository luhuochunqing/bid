---
title: 后端测试基线修复与 mock 用户种子机制
space: engineering
category: reference
tags: [测试, 集成测试, SpringSecurityTest, Testcontainers, projectScoped, 回归基线]
created: 2026-08-22
updated: 2026-08-22
health_checked: 2026-08-22
sources:
  - docs/lessons/lessons-learned.md
  - backend/src/test/java/com/xiyu/bid/testsupport/integration/IntegrationMockUserSeeder.java
  - backend/src/test/java/com/xiyu/bid/testsupport/integration/MockAuthSeedTestExecutionListener.java
  - backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java
backlinks:
  - _index
  - lessons-learned
  - data-permission-hardening
---
# 后端测试基线修复与 mock 用户种子机制

## 1. 结论

2026-08-22 全量回归发现 main 后端基线失守（8386 用例 49F+38E，E2E 栈无法启动），PR !2382 修复后恢复全绿（0F/0E）。根因不是单个 bug，而是三类系统性测试债：projectScoped 审计迁移缺测试联动、公共契约变更后测试侧漂移积压、CI 后端测试面窄导致债务静默累积。

## 2. mock 用户种子机制（IntegrationMockUserSeeder）

projectScoped 审计链路经 `ProjectAccessScopeService.resolveCurrentUser` 查库解析当前用户，`@WithMockUser` 默认 username=`user` 不在 H2 时统一 403。兜底机制：

| 设计点 | 口径 |
|---|---|
| 注册方式 | `src/test/resources/META-INF/spring.factories`，key 为**单数**接口名 `org.springframework.test.context.TestExecutionListener` |
| 触发相位 | `beforeTestExecution`（JUnit @BeforeEach 之后、@Test 之前）——晚于测试类自身的 deleteAll 清理 |
| 写入域守卫 | 仅 JDBC URL 前缀 `jdbc:h2:mem:` 才落库；绝不触碰 Testcontainers/共享 MySQL 容器库 |
| 种子角色 | 一律 MANAGER + 非 OSS + enabled；ROLE_ADMIN authority 由注解提供（hasAdminAccess 只要求库内用户存在且非 OSS），负向权限测试不受影响 |
| 自定义 username | 不进全局清单（避免撞 users.username 唯一键）；由测试类显式 `ensureSeededFor(repo, "xxx")` 或自建同名真实用户 |
| 特例 | @DirtiesContext(AFTER_EACH) 且在 @BeforeEach 内即调受守卫接口的类（ExpensePayment），全局钩子相位不够，需在清理后显式补种 |

## 3. 已知陷阱

- **spring.factories key**：TestExecutionListener 注册用单数接口名；用注解类复数名静默不生效。
- **共享持久容器污染**：本地（非 CI）AbstractMysqlIntegrationTest 家族共用跨 JVM 持久的 `xiyu_bid_verify` 库；任何额外 INSERT 都可能让 Flyway 迁移断言"首轮绿二轮红"。
- **e2e profile Bean 缺口**：给基础设施加 `@Profile("!e2e")` 时必须同步检查消费方注入链，否则 e2e 启动崩溃（范式：@Primary+@Profile("e2e") 子类桩，参考 E2eNoopScoreAnalyzer / E2eTenderDocumentAnalyzer）。

## 4. 新写集成测试规范

优先在测试内自建同名真实用户（参考 `AiDeepCapabilityIntegrationTest#aiEndpoints_ForStaff_ShouldDenyHiddenAndUnlinkedTargets`）；种子器仅是存量 `@WithMockUser` 测试的兜底，不是新测试的捷径。

## 5. E2E 基建补充（2026-08-23）

- **隔离栈端口**：BACKEND_PORT/FRONTEND_PORT 非 18089/1323 时，spec 内 cookie/fetch/goto 必须用 env 驱动 baseURL；每次 playwright 运行必须带同一组 env（global-teardown 会拆栈）
- **详情页阶段切换**：项目详情的阶段步进器是 el-steps（`.el-step`，无 tab role），点击 `.project-stage-timeline .el-step` 按 title 过滤；POST /api/projects 支持 stage 初始值
- **角色码**：register 仅认 RoleProfileCatalog 注册码（STAFF/MANAGER 等遗留名会 400）；auth-helpers.ensureApiSession 已内置归一化映射
- **勘误（2026-08-23）**：此前判断「AGENT 品牌授权无法经 UI 创建（后端 @NotNull 缺陷）」经复核**不成立**——前端 handleSubmit 本就会派生 authStartDate/authEndDate=auth2 区间，原校验对真实流程可过；当时的 400 null 证据来自手工构造的不完整请求。生产代码已回滚恢复原校验；E2E 改走真实生命周期 fixture（PL 上传招标文件→提交立项→lizong 审批）后 spec 全绿
- 剩余 spec 漂移按簇对齐中（performance/initiation/warehouse-export/list-filter 等），模式同上：对照 error-context 快照 + 当前组件源码修正定位器与断言

## 6. 动态表单 schema 种子（2026-08-23 补充）

FormDefinitionE2eSeeder 的 tender.entry schema 必须与前端 fallback 表单字段集保持同步。
V1166 对齐后字段为：项目名称/招标主体/总部所在地(cascader)/报名截止时间/开标时间/客户类型/优先级/项目类型。
pastedText（粘贴识别 textarea）由 schema 驱动渲染——schema 缺此字段则粘贴识别不可用。
