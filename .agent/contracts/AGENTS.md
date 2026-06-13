<!-- OPENSPEC:START -->
> **已禁用**：OpenSpec 指令已从本项目移至 `@/.wiki/pages/open-spec.md`。
> 如需创建/更新规格文档，请使用 `speckit-*` skill（参见 CLAUDE.md §Spec Kit 流程门禁）。
<!-- OPENSPEC:END -->

# AGENTS.md - 项目智能体协作口径

本仓库对应"西域数智化投标管理平台"的交付项目。
当前目录名、包名和构件名中仍保留 `xiyu-bid-poc`、`bid-poc` 等历史命名，这些属于遗留标识，不代表项目仍按 POC方式协作或对外表达。

## Agent Contract

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

## 协作口径

- **协作语言**：默认使用中文进行沟通、代码注释、测试说明和变更描述。
- **项目品牌**：对外统一使用"西域数智化投标管理平台"全称；仅在引用仓库路径、包名、脚本名时保留 `xiyu-bid-poc` 等历史标识。
- **暗号协定**：
  - **"早操SOP"** → 自动执行 `git fetch origin && git rebase origin/main && bash scripts/sync-env.sh .`（早操三连）。
  - **"开个任务/开个分支 XX"** → 自动调用 `scripts/agent-start-task.sh <当前agent名> <XX> origin/main --in-place`（早操三连 + 切开发分支一步到位）。
  - **"早操SOP + 开个分支 XX"** → 同上，相当于 `--in-place` 一次完成全部流程。
- **开场约定**：AI 代理开启新任务或接收复杂任务时，先声明当前环境（worktree 名称、当前分支、协作模式），随后按 `RULES.md` 中的四阶段流程（plan → tdd → code-review → refactor-clean）和核心业务逻辑架构约束展开工作。Mock 政策见 §Mock 政策（统一决策）。
- **架构约束**：详细解释见 `RULES.md`；后端纯核心门禁由 `FPJavaArchitectureTest` 执行。
- **架构门禁口径**：纯核心仍禁止显式依赖 `System` 等隐式输入；Java 枚举 `values()` 编译器生成的 `System.arraycopy` 属于合成字节码误报，由门禁排除。
- **可维护性约束**：受保护模块的防上帝类门禁由 `MaintainabilityArchitectureTest` 执行。
- **项目权限门禁口径**：`ProjectAccessGuardCoverageTest` 扫描所有带 `projectId` 或引用项目关联 DTO/实体的 Controller/Service，必须命中 `ProjectAccessScopeService` 等统一守卫证据，或进入 `project-access-guard-baseline.txt` 显式基线并写明原因。
- **标书生成 Agent**：`com.xiyu.bid.biddraftagent.domain` 是纯核心，`application` 只做 run 编排和写入计划，`infrastructure/documenteditor` 负责实际写入章节树。

## Mock 政策（统一决策）

- **唯一支持路径**：前端、后端、E2E、演示环境均以真实后端 API 为唯一事实源。
- **前端 Mock**：已清空（`src/mock`、`src/api/mock-adapters/`、`.env.mock` 均已删除）；`src/api/config.js` 硬编码 `mode: 'api'`。
- **后端 `demo/` 包**：`backend/src/main/java/com/xiyu/bid/demo/` 是 E2E 测试的合法辅助包，用于提供 `E2eDemoDataInitializer` 等测试数据初始化，非 Mock 代码。
- **遗留代码现状**：仓库内仍可见 `frontendDemo` 适配层、`demoPersistence` 等历史遗留；这些内容当前只应被视为清理对象，不允许新增、不允许扩散。
- **执行要求**：任何新功能、Bug 修复、测试回归、截图演示都必须以真实后端联调为前提。

### Final Class Mock 策略

- **纯核心（domain / core / policy）的 `final class`**：禁止 Mockito mock。纯核心类应通过构造真实实例 + 控制输入数据来测试。
- **集成测试（IT）**：使用真实 Spring 上下文和真实 Bean 实例，不得 mock `final class`。
- **当前配置**：`mockito-extensions/org.mockito.plugins.MockMaker` 为 `mock-maker-subclass`（非 inline），不支持 `mockStatic`。如需 mock `final` / `static`，优先重构为可注入的协作对象，而非升级 mockito-inline。
- **例外**：框架适配类、配置类（如 `@ConfigurationProperties`）不受此限。

## 当前项目事实

### 推荐启动方式

**方式一：稳定联调启动（推荐）**
```bash
export XIYU_DEV_CONFIRMED=1
npm run dev:stable:start
npm run dev:stable:status
```

说明：
- `npm run dev:stable:start` / `dev:stable:status` / `dev:stable:logs` 统一走 `scripts/dev-services.sh`
- 默认拉起文档转换 sidecar、后端 `dev,mysql` 和前端 API-only 开发服务
- 主目录默认使用前端 `127.0.0.1:1314`、后端 `127.0.0.1:18080`、sidecar `127.0.0.1:8000`、数据库 `xiyu_bid_main`
- 根目录 `npm run dev:all` / `./start.sh` 仍是兼容入口，会先按当前目录加载 `scripts/dev-env.sh`，再转调同一套稳定启动逻辑
- 启动脚本会显式注入 `VITE_API_MODE=api` 供健康检查使用，但前端配置本身已固定为 API-only
- `backend/start.sh`、`scripts/dev-services.sh`、`scripts/dev-services-launchd.sh`、`scripts/local-docker-stack.sh` 等本地脚本带 dev-only guard；本地启动必须显式设置 `XIYU_DEV_CONFIRMED=1`，生产部署不得使用这些脚本

**方式二：一键兼容入口**
```bash
export XIYU_DEV_CONFIRMED=1
npm run dev:all
```

**方式三：手动启动（主目录基准区）**
```bash
# 终端 1：启动后端
cd /Users/user/xiyu/xiyu-bid-poc/backend
XIYU_DEV_CONFIRMED=1 ./start.sh

# 终端 2：启动前端
cd /Users/user/xiyu/xiyu-bid-poc
VITE_API_MODE=api VITE_API_BASE_URL=http://127.0.0.1:18080 npm run dev -- --host 127.0.0.1 --port 1314
```

> **Worktree 中的路径**：本文件记录的是主目录（`xiyu-bid-poc`）基准区的路径约定。
> Agent 在 worktree（`/Users/user/xiyu/worktrees/<name>/`）中开发时，
> 命令中的路径应替换为对应 worktree 根目录；脚本会自动适配，无需手动改写。

补充：
- 如必须直接运行 `mvn spring-boot:run`，需自行补齐 `JWT_SECRET`、`DB_PASSWORD`、`CORS_ALLOWED_ORIGINS` 等必需环境变量；默认优先使用 `backend/start.sh`
- 多 Agent worktree 会自动使用专属端口和数据库，例如 Codex worktree 使用前端 `1316`、后端 `18082`、sidecar `8002`、数据库 `xiyu_bid_codex`

### 技术栈

- **前端**：Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router 4 + Axios + ECharts + Sass
- **后端**：Java 21 + Spring Boot 3.3 + JPA (Hibernate) + MySQL 8.0 + Redis 7 + Flyway + ArchUnit
- **E2E 测试**：Playwright + Node.js (VITE_API_MODE=api)
- **文档转换**：Python 3.12 + FastAPI + MarkItDown (sidecar 模式)
- **部署**：Docker + Docker Compose + nginx

### 关键路径与文档

- **项目主页**：[[_index]] (WIKI.md)
- **需求追溯**：[[requirements]] (pages/requirements.md)
- **接口文档**：[[api-openapi]] (pages/api-openapi.md)
- **团队排期**：[[team-and-timeline]] (pages/team-and-timeline.md)
- **开发规约**：`RULES.md`
- **任务看板**：`TODO.md` / `CHANGELOG.md`

## Agent 行为规范

### 0. Spec Kit 流程门禁（强制）

**当收到包含 `Phase`、`开发计划`、`需求开发` 或类似复杂任务时，必须按以下顺序执行：**

1. 使用 `speckit-specify` skill 创建/更新规格文档
2. 使用 `speckit-plan` skill 生成/更新实现计划
3. 使用 `speckit-tasks` skill 生成任务清单
4. **在编码前必须完成上述三个步骤**
5. 编码完成后使用 `speckit-analyze` skill 验证一致性

> **违规说明**：跳过流程直接编码的提交将被 CI 门禁拒绝。

### 1. 任务起始
Agent 在每次对话开始或切换任务时，必须声明当前环境（worktree 名称、当前分支、协作模式）。

### 2. 变更原则
- **禁止 Mock**：严禁在 `src/mock` 或任何非 API 路径下编写代码。
- **JPA 优先**：后端存储必须通过 JPA 实体映射到 MySQL，禁止使用内存 Map 模拟。
- **原子提交**：每次提交应包含功能实现、对应的 `Flyway` 迁移脚本（如涉及库表）、以及至少一个验证成功的测试用例证据。

### 3. 多 Agent 协作 (Worktree)

#### 3.1 两级 Worktree 策略

为了平衡物理隔离与开发效率，采用**两级** worktree 管理策略。

**一级：持久 Worktree（串行小任务）**

每个 Agent 只有一个持久 worktree（如 `agent/codex-init`），端口、数据库、依赖**一次性初始化**，后续小改动在持久 worktree 内切分支完成：

```bash
# ── 仅首次初始化（已完成）──

# ── 每个新任务（串行） ──
git checkout agent/<name>-init         # 回到锚点
git pull origin main                   # 同步最新 main
git checkout -b agent/<name>/<task>    # 切新分支
# 开发、提交、推送、PR
# PR merged 后：
git checkout agent/<name>-init
git pull origin main
git branch -D agent/<name>/<task>
```

脚本支持 `--in-place` 模式一键完成上述流程：

```bash
scripts/agent-start-task.sh <name> <task> origin/main --in-place
```

**二级：临时 Worktree（并行/破坏性变更）**

适合以下场景时创建隔离 worktree：
- 需要同时运行前后端联调
- E2E 测试需要专属环境
- 破坏性变更（改表结构、数据库连接等）
- 两个 task 并行开发需要各自独立验证

使用传统模式（不带 `--in-place`）：

```bash
scripts/agent-start-task.sh <name> <task> origin/main [--lock ...]
```

任务结束后删除 worktree：

```bash
git worktree remove /path/to/worktree --force
git branch -D agent/<name>/<task>
git push origin --delete agent/<name>/<task>
```

#### 3.2 通用原则

- **物理隔离**：各 Agent 在 `/Users/user/xiyu/worktrees/` 下的独立持久 Worktree 工作，严禁在 `main` 基准区修改代码。
- **资源分配**：每个 Agent 拥有固定的专属端口（前端 131x / 后端 1808x）和数据库名。持久 worktree 内切分支不改变资源分配。
- **验证责任**：遵循"谁改代码，谁在自己的 Worktree 跑通验证"原则。报告"任务完成"前，必须提供在 Worktree 内部执行 `npm run build` 和 `mvn test` 的成功证据。
- **协作启动命令**：多 Agent 本地联调优先使用 `npm run agent:up` / `agent:restart` / `agent:status` / `agent:logs` / `agent:stop`；`npm run agent:morning` 等价于早操SOP（sync + 重启）；脚本会按当前 worktree 自动映射端口、数据库、Redis DB、sidecar 端口和 launchd label，启动类命令同样需要 `XIYU_DEV_CONFIRMED=1`。
- **健康诊断**：`npm run agent:health-check` — 跨 worktree 聚合展示 sidecar/backend/frontend 健康状态。
- **分支命名**：
  - **Worktree 锚点分支**（agent/<name>-init，如 `agent/codex-init`、`agent/cursor-init`）：各 Agent worktree 的常驻基线分支。**严禁直接在此分支上开发**（CI 门禁会拦截），**严禁删除**（本地或远端均不可删）。仅用于 worktree 锚定和多 Agent 间同步基线。
  - **任务开发分支**（`agent/<name>/<task>` 等前缀）：每个原子任务一个独立分支。PR 合入后由 CI 自动清理删除远端分支，本地分支需手动 `git branch -D`。
  - 分支命名规范详见 `.wiki/pages/branch-naming.md`。

### 4. 文件锁门禁
- **锁机制**：已改为 per-task 文件模式（`.agent-locks/<task-slug>.yml`），详情见 CLAUDE.md §5.2。
- **严禁绕过**：`git push --no-verify` / `git commit --no-verify` 已被两层防线禁止：① `scripts/git` 包装器（系统级拦截）；② git alias 强制走 `.githooks/git-push-wrapper.sh` / `.githooks/git-commit-wrapper.sh`（过滤 `--no-verify`）。由 `agent-start-task.sh` 自动配置。
- **自动合并**：1 个 required review 批准后，`.github/workflows/auto-enable-merge-on-approved.yml` 会自动为 PR 开启 GitHub auto-merge（--squash）。真正合并仍需所有门禁（agent-locks、line-budget、frontend/backend/e2e + strict）通过。详见 CLAUDE.md §6。
- **锁管理命令**：`npm run agent:lock-acquire` / `agent:lock-release` / `agent:lock-renew` / `agent:lock-check` / `agent:lock-cleanup` — 各 worktree 的 task lock 操作快捷入口。

## 审计与质量门禁

- **静态扫描**：代码提交前必须通过 `eslint` (前端) 和 `checkstyle/pmd/spotbugs` (后端)。
- **架构测试**：后端必须通过 ArchUnit 门禁，确保包依赖、异常处理和 FP-Java 约束不被破坏。主要测试类包括 `FPJavaArchitectureTest`、`MaintainabilityArchitectureTest`、`ProjectAccessGuardCoverageTest`。
- **TDD 覆盖率**：核心业务逻辑（后端 domain/core 层、前端业务组件）的单元测试覆盖率目标为 80% 以上。
- **E2E 验证**：涉及 UI 交互的变更必须包含对应的 Playwright 脚本验证。

### 本地门禁（GitHub CI 替代方案）

当前 GitHub CI 不可用，门禁完全依赖本地 git hooks + alias 双防线：

| 层次 | 机制 | 触发时机 | 能否绕过 |
|------|------|---------|---------|
| 1 | `scripts/git` 包装器（需 `source dev-env.sh` 激活） | 每次 `git` 命令 | 系统级拦截 `--no-verify` |
| 2 | `.githooks/pre-push` + `pre-push-gate.sh`（14 道门禁） | `git push` | hook 自动触发，别名拦截 `--no-verify` |
| 3 | `.githooks/pre-commit`（15+ 项检查） | `git commit` | hook 自动触发，别名拦截 `--no-verify` |
| 4 | **git alias**（`.githooks/git-push-wrapper.sh`） | `git push` | 过滤 `--no-verify`，门禁强制跑 |
| 5 | **git alias**（`.githooks/git-commit-wrapper.sh`） | `git commit` | 过滤 `--no-verify`，hook 强制跑 |

第 4/5 层 alias 是**不依赖 shell PATH 的硬防线**——由 `agent-start-task.sh` 在创建 worktree 时自动配置，
每次 `git push` / `git commit` 无论是否带 `--no-verify`，都强制走包装器脚本，门禁不可绕过。

完整本地门禁（手动触发）：
- `npm run ci:local:quick` — 快速预检（编译 + 架构门禁）
- `npm run ci:local` — 完整本地 CI 模拟（需本地 Docker）
- `npm run agent:pre-push-dry-run` — 模拟推送前 14 道门禁
- `npm run ci:pre-pr` / `bash scripts/ci-pre-pr.sh` — 提交 PR 前一站式门禁

### Gitee CI（远端门禁）

仓库根目录 `.gitee-ci.yml` 提供 Gitee Go 远端流水线，覆盖：
- 治理门禁：agent-locks、line-budget、quality-scope
- 前端：单元测试、`npm run build` / `build:api`、ESLint（无 UI 变更时走快速编译检查）
- 后端：编译、ArchUnit 架构测试（`FPJavaArchitectureTest` / `MaintainabilityArchitectureTest`）、集成测试、Flyway 容器测试、Checkstyle / PMD / SpotBugs（无后端变更时走快速编译检查）
- E2E：Playwright 核心流程回归（无 UI 变更时跳过）
- 后端质量审计（非阻断）

触发条件：`push` 到 `main` 以及所有 Pull Request。

> 注意：Gitee Go 需要在仓库「服务」→「Gitee Go」中手动开通；若流水线列表为空，请先检查是否已开通。




## 数据库迁移规范

### 迁移文件命名

- 正向迁移: `V{版本号}__{描述}.sql`
- 回滚脚本: `U{版本号}__{描述}.sql`（必须与正向迁移同名同描述）

### 回滚脚本要求

- **必写回滚**：所有新的 Flyway 迁移文件必须附带对应的 U 脚本
- **安全性**：回滚脚本中必须包含前置检查（`DROP COLUMN IF EXISTS`、`DROP TABLE IF EXISTS` 等）
- **PR 备注**：U 脚本在创建时需要在注释中注明 PR 编号
- **历史补全**：历史迁移（V1047 之前）的回滚脚本可选，但推荐逐步补全

### 数据库运维命令

- `npm run db:dev-repair` — 修复本地 Flyway 状态（rebase/sync 后版本冲突或 checksum 漂移）
- `npm run db:generate-rollback` — 为所有正向迁移自动生成回滚脚本骨架

## 创建 Pull Request

推荐使用统一脚本 `scripts/pr-create.sh`（自动适配 GitHub / Gitee）：

```bash
# 方式一：title + body 文件
./scripts/pr-create.sh "feat: 你的标题" /path/to/body.md

# 方式二：title + stdin（用 heredoc 写多行）
./scripts/pr-create.sh "feat: 你的标题" <<'BODY'
## 改动
...
BODY
```

需要环境变量：GitHub 需要 `gh` 已登录，Gitee 需要 `GITEE_TOKEN`。

### Gitee 工作流

```bash
GITEE_TOKEN=xxx npm run gitee:pr-create    # 创建 PR（当前分支→main）
GITEE_TOKEN=xxx npm run gitee:pr-list      # 列出当前分支 PR
GITEE_TOKEN=xxx npm run gitee:pr-merge     # 合并 PR（squash）
npm run gitee:auto-merge                   # 自动合并已批准 PR
```

### Gitee CI（远端门禁）

仓库根目录 `.gitee-ci.yml` 提供 Gitee 社区版可用的远端流水线，覆盖：
- **治理门禁（governance）**：agent-locks、line-budget、front-data-boundaries、doc-governance
- **前端**：变更范围检测 → 条件执行单元测试与构建
- **后端**：变更范围检测 → 条件执行编译、ArchUnit 架构门禁（FPJavaArchitectureTest、MaintainabilityArchitectureTest）、项目权限守卫覆盖（ProjectAccessGuardCoverageTest）、Checkstyle/PMD/SpotBugs 质量门禁
- **E2E**：默认手动触发，接入 Gitee 私有 Runner 后可改为自动执行

执行逻辑：
- quality-scope job 通过 `git diff` 检测变更范围（backend/src/e2e/doc），以 dotenv artifact 传递给后续 job
- 无相关变更时，对应阶段跳过完整流程（如仅改文档时跳过前后端完整构建）
- agent-locks 不依赖 quality-scope，可并行检测锁文件冲突

## 数据库迁移规范