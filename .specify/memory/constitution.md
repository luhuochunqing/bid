<!--
  Sync Impact Report
  ==================
  Version change: 1.2.0 → 1.3.0
  Type: MINOR — added Authorization Unification Core Principle (VII) codifying
        single-permission-model (isAuthenticated + hasAuthority), prohibiting
        hasAnyRole/hasRole dual-track, and mandating migration of 177 legacy
        whitelist sites. Strengthens Security & Access Control §API Authorization.

  Root cause context: eb58f2817 (2026-06-16) cut legacy ROLE_STAFF/ROLE_MANAGER
        compatibility for bid-otherDept/bid-administration/bid-Team, but did not
        migrate the 177 @PreAuthorize sites depending on those authorities —
        leaving a dual-track model responsible for 20+ recurring 403 PRs
        (CO-362 → CO-466, CO-394 A/B/C/D, CO-415/416, etc.). This principle
        makes the migration mandatory and prevent recurrence via ArchTest gate.

  Modified principles:
    - VI. Boring Proven Patterns → renumbered to VII.
    - Added new VI. Authorization Unification (NON-NEGOTIABLE).
  Added sections: new Core Principle VI (Authorization Unification);
        Security & Access Control §API Authorization materially expanded
        (single-source, dual-track prohibition, migration mandate).
  Removed sections: none.

  Templates requiring updates:
    - .specify/templates/plan-template.md ✅ aligned (Constitution Check
      section reads constitution file dynamically — no change needed)
    - .specify/templates/spec-template.md ✅ aligned (no structural changes)
    - .specify/templates/tasks-template.md ✅ aligned (no structural changes)

  Deferred TODOs: none
-->

# 西域数智化投标管理平台 Constitution

## Core Principles

### I. FP-Java Architecture (NON-NEGOTIABLE)

后端严格区分 **Pure Core**（纯业务规则、校验、计算）与 **Imperative Shell**
（Controller、Service、Repository）。核心业务逻辑 MUST 保持不可变：Java 使用
`record` 或 `final`，前端避免原地修改对象。包名按业务域划分（如
`com.xiyu.bid.calendar`）。

**Rationale**: 强制关注点分离，使核心逻辑可独立测试、无需基础设施即可验证；
不可变数据消除隐式副作用，降低并发和调试成本。

### II. Real-API Only (NON-NEGOTIABLE)

Mock 模式已于 2026-04-30 退役并彻底删除。前端、后端、E2E 均 MUST 以真实后端
API 为唯一事实源。`VITE_API_MODE` 硬编码为 `api`，禁止重新引入双模式切换。

**Rationale**: 历史双模式导致 mock/prod 不一致引发生产事故；单一路径消除
环境差异风险，确保所有测试结果可直接反映生产行为。

### III. Test-Driven Development (NON-NEGOTIABLE)

开发 MUST 遵循 Red → Green → Refactor 循环：
- 先写测试 → 用户/审查者确认 → 测试 FAIL → 再实现 → 重构
- 架构测试（ArchUnit）验证 FP-Java 分层约束与边界规则，MUST 保持全绿
- 可维护性测试监控 Service 行数、协作者数量与公开方法数
- E2E（Playwright）覆盖关键交互路径
- **Blueprint E2E Mandate**: 每个产品蓝图小节 MUST 有对应的 Playwright E2E 测试，
  覆盖正向流程 + 4 角色权限验证 + 边界情况。纯后端改动可用 `[skip e2e-scope]` 豁免。

**Rationale**: TDD 确保每行代码都有存在的理由；架构测试防止分层腐化；
E2E 捕获真实用户路径的回归；蓝图 E2E 强制确保每个功能点在真实页面中可交互。

### IV. Split-First & Simplicity

禁止上帝类：Application Service、Domain Policy、Mapper、Repository MUST 拆分。
单个 Java 文件软上限 200 行，硬上限 300 行（棘轮门禁强制执行）。
新代码默认 <100 行，单文件实现直到有充分证据需要拆分。

**Rationale**: 小文件强制单一职责；棘轮门禁防止已拆分代码重新膨胀；
YAGNI 原则避免过度工程化。

### V. OSS Integration (NON-NEGOTIABLE)

所有 OSS（组织架构服务）集成 MUST 符合以下规则：
- **真实 API 唯一源**: 禁止 Mock 或伪造 OSS 数据，集成 MUST 调用真实 OSS
  Gateway 接口。
- **批量优先**: 同一批请求中涉及的外部查询 MUST 优先使用批量接口；仅在
  无批量能力时才允许逐条调用，并 MUST 说明原因。
- **角色映射大小写安全**: OSS 侧角色/岗位名称到内部 `role_code` 的映射 MUST
  大小写不敏感，同时保持向后兼容，已有配置不因大小写调整而失效。
- **映射优先级**: 当多个来源都能决定用户角色时，优先级从高到低为
  人员级规则 > 部门级规则 > 岗位级规则 > 系统角色列表（`sysRoleList`）。
- **可观测性**: 每个 OSS 接口调用 MUST 记录请求/响应摘要、耗时、错误码，失败
  时保留原始响应用于排查。

**Rationale**: 集中式组织服务是权限与岗位信息的权威来源；批量调用降低外部
依赖负载与同步耗时；大小写安全避免生产环境因地名大小写差异导致角色丢失；
明确优先级防止多来源映射冲突。

### VI. Authorization Unification (NON-NEGOTIABLE)

全仓 MUST 维护**单一权限模型**。`@PreAuthorize` 表达式只允许两种形态：

- `isAuthenticated()` — 早过滤层，仅校验"是否登录"。用于 Controller 类级注解，
  以及无业务语义的只读 schema / 公开字典接口。
- `hasAuthority('<permission-key>')` — 细粒度业务权限层。`<permission-key>`
  MUST 在 `RoleProfileCatalog` 的 `SeedDefinition.menuPermissions` 中注册，
  并通过 Flyway 迁移同步到各角色。

**禁止形态（MUST NOT）**：

- `hasAnyRole('ADMIN','MANAGER',...)` — 角色枚举式白名单。与 `RoleProfileCatalog`
  的 `menuPermissions` 形成双轨制，每新增一个受限角色就在某个白名单漏一次。
- `hasRole('ADMIN')` — 同上，例外仅限 `SecurityConfig` 的 `/api/admin/**`
  路径级兜底（已有路径白名单约束），不得出现在业务 Controller 方法级。
- 任何在 Controller 中手抄 `RoleProfileCatalog` 角色列表的行为。

**Controller 职责边界**：Controller `@PreAuthorize` 只做"是否登录 + 是否有模块权限"
的早过滤；"是否对该具体资源有操作权限" MUST 下沉到 Service 层 Policy
（如 `ProjectDocumentWorkflowPolicy`、`PlatformAccountViewerPolicy`）。Controller
不得用 `hasAnyRole` 一刀切收紧（详见 lessons-learned §24、§28）。

**存量治理**：截至本 Constitution v1.3.0，全仓有 177 处 `hasAnyRole`/`hasRole`
使用点（含 105 处 `hasAnyRole('ADMIN','MANAGER')`）作为技术债登记在
`specs/<feature>/`，MUST 分批迁移到 `hasAuthority`。迁移期间 MUST 同步新增
ArchitectureTest 守卫，确保债务不再增长；全部迁移完成后 ArchitectureTest
从警告升级为硬失败门禁。

**Rationale**: 2026-06-16 的 `eb58f2817` 切断了 `bid-otherDept`/`bid-administration`/
`bid-Team` 三个角色的 `ROLE_STAFF`/`ROLE_MANAGER` legacy 兼容（堵越权），但未同步
迁移依赖这些 authority 的 177 处 `@PreAuthorize` 白名单。从此系统分裂为"新模型给角色
正确赋权 / 旧模型拒绝承认"的双轨制，导致 CO-362 → CO-466 等 20+ 个 403 反复返工
PR。CO-394 A/B/C/D 已验证 `hasAnyRole → hasAuthority` 迁移范式可行，本原则将其
从"单点修复"上升为"系统性治理"。

### VII. Boring Proven Patterns

优先使用经过验证的、可预测的技术模式。只在以下条件触发时才引入复杂度：
- 性能数据证明当前方案过慢
- 明确的规模需求（>1000 用户、>100MB 数据）
- 多个已验证的用例需要抽象

避免框架的"魔法"用法；选择最平淡、最可读的实现。

**Rationale**: 可维护性 > 聪明。平淡的代码更容易被团队理解、调试和接手；
生产系统的可靠性不依赖个人技艺。

## Code Quality Gates

- **Checkstyle**: Java 代码 MUST 通过 checkstyle 检查，配置文件位于项目根目录。
  禁止 `@SuppressWarnings("checkstyle:.*")` 绕过规则，除非有明确注释说明原因。
- **PMD**: MUST 通过 PMD 静态分析，检测常见编程缺陷（空指针、资源泄漏等）。
- **SpotBugs**: MUST 通过 SpotBugs 字节码级缺陷检测，尤其是安全漏洞和并发问题。
- **Quality CI**: `mvn -Pjava-quality,java-quality-spotbugs,quality-strict checkstyle:check pmd:check spotbugs:check` MUST 在 PR 门禁中全绿。
- **Frontend Gate**: `npm run check:front-data-boundaries`、`npm run check:doc-governance`、
  `npm run check:line-budgets` MUST 通过。

## Performance Constraints

- **Pagination**: 列表页分页选项固定为 20/50/100 条，禁止超过 100 条/页。
- **Export Limit**: 单次导出最多 500 条记录，超出 MUST 阻断并提示缩小筛选范围。
- **Async Export**: 导出任务超过 30 秒 MUST 改为异步处理，完成后通过消息中心通知下载。
- **File Upload**: 附件大小上限 20MB，支持 PDF/JPG/PNG 格式。

## Security & Access Control

- **RoleProfileCatalog Canonical Source**: 角色权限 MUST 在
  `RoleProfileCatalog.java` 中统一定义。新增功能模块 MUST 在此文件中注册对应的
  `permissionKey`，并通过 Flyway 迁移脚本更新已存在角色的权限数据。
- **Project Access Guard**: 涉及 `projectId` 的接口 MUST 通过统一的
  `ProjectAccessScopeService` 进行项目权限校验；不得在 Controller 中裸写权限逻辑。
- **Secrets Management**: `JWT_SECRET`、`DB_PASSWORD`、`ADMIN_PASSWORD` 等敏感值
  MUST 通过环境变量注入，禁止硬编码或提交到仓库。`start.sh` 中的默认值仅限本地
  开发，生产部署 MUST 使用真实环境注入。
- **Security Config Scope**: `SecurityConfig` 当前放行范围（`/api/auth/sessions`、
  `/actuator/info`、`/h2-console/**`）MUST NOT 继续扩大；如需调整 MUST 同步更新
  文档与代码。
- **CORS**: 允许的源 MUST 通过 `CORS_ALLOWED_ORIGINS` 环境变量配置，
  不得在代码中硬编码生产源。
- **API Authorization**: Controller 方法 MUST 使用 `@PreAuthorize` 注解控制访问，
  且只允许 `isAuthenticated()` 或 `hasAuthority('<permissionKey>')` 两种形态
  （详见 Core Principle VII）。禁止 `hasAnyRole`/`hasRole` 角色枚举式白名单。
  权限键 MUST 与 `RoleProfileCatalog.SeedDefinition.menuPermissions` 中注册的
  `permissionKey` 严格一致。新增模块 MUST 先在 `RoleProfileCatalog` 注册权限键 +
  Flyway 迁移同步角色权限，再写 Controller `@PreAuthorize`。
- **Authorization ArchTest Gate**: ArchitectureTest MUST 包含守卫规则，禁止
  `@PreAuthorize` 表达式中出现 `hasAnyRole`/`hasRole`。迁移过渡期内已有的 177 处
  使用点作为白名单豁免（逐 Controller 移除后从豁免清单删除）；新增使用点 MUST NOT
  进入豁免清单。全部迁移完成后豁免清单清空，守卫升级为硬失败门禁。

## Development Workflow & Multi-Agent SOP

- **Sync-First**: 每个 session 和每次新任务开始前 MUST 执行
  `git fetch origin && git rebase origin/main`，确保基于最新 `origin/main` 工作。
- **Lease Protocol**: 修改文件前 MUST 运行 `./scripts/who-touches.sh <path>`
  检测其他 agent 的未合改动。退出码 0 方可开工；退出码 1 需协调或换任务。
- **WIP Visibility**: 每个工作 session 结束前 MUST push 当前 `agent/*` 分支到
  remote（即使未完成、未开 PR），确保 `who-touches.sh` 能检测到。
- **Completion Gate**: 报告完成前 MUST 在当前 worktree 运行：
  `npm run build` + `cd backend && mvn test` + `git status` 确认无未授权修改。
- **DB Migrations**: MUST 使用 Flyway 管理；迁移脚本放在
  `backend/src/main/resources/db/migration-mysql/`；禁止手动修改数据库 schema。
  每个 V 脚本 MUST 有对应的 U 回滚脚本。
- **Blueprint Workflow**: 蓝图功能开发 MUST 遵循 5 阶段流程：
  蓝图分析 → 差距分析 → 实现（含 E2E）→ 真实验证 → 验收提交。

## Governance

本 Constitution 是项目开发的最高准则，所有代码审查、架构决策和 PR 门禁 MUST 以
此处列出的原则为基准。任何与 Constitution 冲突的本地实践或习惯 MUST 向
Constitution 对齐，而非反之。

**Amendment Procedure**:
- 原则新增/删除/重定义 → MAJOR 版本升级
- 新增章节或实质性扩展指导 → MINOR 版本升级
- 措辞澄清、拼写修正、非语义性调整 → PATCH 版本升级
- 所有修订 MUST 通过 PR 审查并更新本文件、传播至关联模板

**Compliance Review**:
- 每个 PR MUST 在描述中说明是否违反 Constitution 原则
- 违反 MUST 在 `plan.md` 的 Complexity Tracking 表中记录并给出正当理由
- 架构测试（`mvn test -Dtest=ArchitectureTest`）作为自动化合规门禁

**Version**: 1.3.0 | **Ratified**: 2026-05-15 | **Last Amended**: 2026-07-02
