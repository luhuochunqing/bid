<!-- OPENSPEC:START -->
> **已禁用**：OpenSpec 指令已从本项目移至 `@/.wiki/pages/open-spec.md`。
> 如需创建/更新规格文档，请使用 `speckit-*` skill（参见 CLAUDE.md §Spec Kit 流程门禁）。
<!-- OPENSPEC:END -->
# CLAUDE.md

每次对话开始，都请打招呼：同志，你好！
本文件提供本仓库的执行入口、常用命令、验证清单和环境坑点。
它的目标不是描述理想状态，而是帮助代理和开发者快速对齐当前仓库的真实情况。

## 仓库路径

- **主工作区（开发环境所在地）**：`/Users/user/xiyu/worktrees/trae`（Trae Agent Worktree）
- 主目录基准区：`/Users/user/xiyu/xiyu-bid-poc`
- 后端目录：`/Users/user/xiyu/worktrees/trae/backend`
- **其他 worktree（claude/codex/cursor/gemini/kimi/mimo/qoder/zcode）**：仅用于代码编辑和 git 操作，不启动开发环境

### 双远程仓库配置（Gitee 主 + GitHub AI 协作入口）

> 自 2026-06-24 起启用双远程仓库。**Gitee 为主仓库**，GitHub 作为 AI Coding 工具（Cursor/Codex/Claude 等）的远端协作入口 + 镜像备份。同步流程详见 skill `github-sync`。

| Remote 名 | URL | 用途 |
|---|---|---|
| `origin` | `git@gitee.com:allinai888/bid.git` | **主仓库**：PR / CI / 协作走这里 |
| `github` | `git@github-bid:yzcynk5vtp-ship-it/bid.git` | **AI 协作入口 + 镜像**：AI 工具改代码，本地同步回 Gitee |

**日常推送命令**：
```bash
git push origin main    # → Gitee（主仓库，PR/CI 走这里）
git push github main    # → GitHub（供 AI 工具拉取 + 镜像备份）
```

**从 GitHub 拉取 AI 改动**：
```bash
git fetch github                    # 拉取 GitHub 远端
git branch -r | grep github/        # 查看 AI 工具创建的分支
git checkout -b review/ai-xxx github/<branch>  # 审查 AI 改动
# 审查 OK 后合入 main，推回 Gitee
git checkout main && git merge review/ai-xxx && git push origin main
```

**SSH 配置**：
- Gitee：`~/.ssh/id_ed25519`（默认 key）
- GitHub：`~/.ssh/github_bid_mirror` + `~/.ssh/config` 中的 `Host github-bid`（`IdentitiesOnly yes`）
- GitHub 账户：`yzcynk5vtp-ship-it`（deploy key 仅绑定 `bid` 仓库，已开启 write access）

**重要约定**：
- **Gitee 是唯一 source of truth**：所有最终代码以 Gitee main 为准
- **GitHub 改动必须审查**：AI 工具的改动不能直接合入，必须本地审查
- 所有 PR / Code Review / CI 仍走 Gitee，GitHub 不走 Gitee CI
- 推送前门禁（`pre-push-gate.sh` 等）对两个 remote 都生效
- 旧 GitHub 账户 `ericforai` 已封禁，新账户为 `yzcynk5vtp-ship-it`
- 完整同步流程详见 skill `github-sync`

## 当前项目口径

- 对外项目名称统一为“西域数智化投标管理平台”。
- 仓库名、包名、构件名中的 `xiyu-bid-poc`、`bid-poc` 属于历史遗留。
- 当前项目按真实 API 交付模式协作，Mock 模式已于 2026-04-30 退役（`mock.js`、`mock-adapters/`、`.env.mock` 均已删除）。
- 如仍在其它文档或评论中看到 `frontendDemo` / `demoPersistence` / `isMockMode` 字样，视为过期表述，不代表仓库真实状态。
- **数据库**：仅支持 MySQL 8.0。迁移脚本统一放在 `migration-mysql/` 目录（B73 基线 + V74~V1081 等活跃版本）；`migration/` 目录为历史遗留，含 V111~V120 等 16 个历史文件，Flyway 配置不再读取。

### 数据库迁移规范

- **迁移脚本位置**：`backend/src/main/resources/db/migration-mysql/`（活跃迁移目录）
- **命名规范**：
  - 基线版本：`B{version}_*.sql`（如 `B73__full_schema_baseline.sql`）
  - 增量版本：`V{version}_*.sql`（如 `V1081__remove_task_executor_role.sql`）
- **版本号**：必须大于已有最大版本号
  - ⚠️ **严禁手动猜测或 `ls | tail` 决定版本号**，必须使用 `bash scripts/new-migration.sh <描述>` 创建迁移（内部自动调用 `next-migration-version.sh`，fetch remote + 本地取 max+1）
  - ⚠️ **创建前先运行 `scripts/next-migration-version.sh --reserve`** 预约版本并打印创建命令
  - ⚠️ `sync-env.sh` 早操和 `pre-push-gate.sh` 推送前会自动检测版本冲突；冲突在 pre-push 阶段会**强制 auto-fix**，禁止以任何方式绕过
  - ⚠️ **并行开发防冲突**：两个 agent 同时开迁移时，`new-migration.sh` 会各自从 remote 最新取版本号，不会撞号；如果因 rebase 时序导致撞号，pre-push gate 会自动重编号（V+1 递增），无需人工介入
- **回滚脚本**：放在 `backend/src/main/resources/db/rollback/migration-mysql/` 目录，与迁移脚本版本对应（回滚文件实际位于 `migration-mysql/` 子目录下，而非直接放在 `db/rollback/`）

## 推荐命令

### 启动

> **Dev-only guard**：`backend/start.sh`、`scripts/dev-services.sh`、`scripts/dev-services-launchd.sh`、`scripts/local-docker-stack.sh`、`scripts/release/rehearsal-env.sh` 均内置 dev-only 双层守卫。
> 必须显式导出 `XIYU_DEV_CONFIRMED=1` 才能运行，且任一 `SPRING_PROFILES_ACTIVE` / `XIYU_ENV` / `NODE_ENV` / `ENV` / `ENVIRONMENT` 含有 `prod*`、`production`、`staging`、`stg`、`release`、`live`、`uat`、`canary` 等信号时均会拒绝执行。
> 本地跑：`export XIYU_DEV_CONFIRMED=1` 再调用脚本；生产部署不得走这些脚本。

```bash
# 推荐：一键联调（仅主工作区 trae）
cd /Users/user/xiyu/worktrees/trae
export XIYU_DEV_CONFIRMED=1
npm run dev:all
```

```bash
# 手动方式：后端（仅主工作区 trae）
cd /Users/user/xiyu/worktrees/trae/backend
# 推荐：使用 start.sh（已内置默认环境变量，需 XIYU_DEV_CONFIRMED=1）
XIYU_DEV_CONFIRMED=1 ./start.sh

# 或直接使用 mvn（需手动传入必需环境变量）
JWT_SECRET="xiyu-bid-poc-local-dev-secret-key-please-change-in-prod-32bytes-min" \
DB_PASSWORD="XiyuDB!2026" \
CORS_ALLOWED_ORIGINS="http://localhost:1323,http://127.0.0.1:1323" \
mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--server.port=18089"
```

> 后端必需环境变量（未设置会导致启动失败）：
> - `JWT_SECRET`：JWT 签名密钥（至少 32 字节），`backend/start.sh` 已提供本地默认值
> - `DB_PASSWORD`：MySQL 8.0 密码（默认 `XiyuDB!2026`）
> - `CORS_ALLOWED_ORIGINS`：允许的前端源地址，默认包含 `http://localhost:1323` 与 `http://127.0.0.1:1323`
>
> 生产部署必须通过真实环境注入这些值，**不得依赖上述默认值**。

```bash
# 手动方式：前端（真实 API 模式，仅主工作区 trae）
cd /Users/user/xiyu/worktrees/trae
VITE_API_MODE=api VITE_API_BASE_URL=http://127.0.0.1:18089 npm run dev -- --host 127.0.0.1 --port 1323
```

### 前端与文档验证

```bash
cd /Users/user/xiyu/worktrees/trae
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run build
npm run test:unit
npm run test:e2e
```

### 后端验证

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=<相关测试类>
# 架构边界测试（CI 实际运行下列三类）：
mvn test -Dtest=ArchitectureTest
mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest
mvn test
```

## 当前验证清单（按可信度排序）

### 1. 当前可直接信任的前端基线

以下命令截至 2026-04-22 当前可通过：

```bash
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run build
npm run test:unit
npm run test:e2e
```

### 2. 后端验证口径

- `ArchitectureTest` 已恢复为**全绿基线**。
- 2026-04-16 已完成两类历史问题修复：
  - `E2eDemoDataInitializer` 引发的 `config -> service` 违规依赖
  - `RateLimitService` 与 `ExportConfig` 引发的 `config <-> service` 循环依赖
- 因此，后端任务完成时必须：
  - 跑受影响测试
  - 如涉及架构边界，再跑 `mvn test -Dtest=ArchitectureTest`
  - 如出现失败，按新增问题处理并说明影响范围
  - 不得再把当前仓库写成“存在已知存量失败”

## 默认登录凭据

### dev / prod

| 用户名 | 密码 | RoleProfile code | 角色名称 | 来源 |
|--------|------|------|------|------|
| `admin` | `XiyuAdmin2026!` | `admin` | 管理员 | V57 迁移 + DefaultAdminInitializer |

### e2e

> 来源：`E2eDemoDataInitializer`，密码统一为 `123456`。
> 注意：第一列为登录用户名，第三列为角色 code（以 `RoleProfileCatalog` 常量为准）。

| 用户名 | 密码 | RoleProfile code | 角色名称 |
|--------|------|------|------|
| `lizong` | `123456` | `admin` | 管理员 |
| `xiaowang` | `123456` | `bid-Team` | 投标专员 |
| `xiaochen` | `123456` | `/bidAdmin` | 投标管理员 |
| `xiaoliu` | `123456` | `bid-TeamLeader` | 投标组长 |
| `xiaozhang` | `123456` | `bid-projectLeader` | 投标项目负责人 |
| `xiaozhou` | `123456` | `bid-Team` | 投标专员 |
| `xiaozheng` | `123456` | `bid-administration` | 行政人员 |

### dev (本地联调，SPRING_PROFILES_ACTIVE=dev)

> 来源：`LocalDevAccountInitializer`，需设置 `LOCAL_DEV_PASSWORD` 环境变量（默认密码 `Test@123`）。
> 注意：第一列为登录用户名，第三列为角色 code（以 `RoleProfileCatalog` 常量为准）。

| 用户名 | 密码 | RoleProfile code | 角色名称 |
|--------|------|------|------|
| `bid_admin` | `Test@123` | `/bidAdmin` | 投标管理员 |
| `bid_lead` | `Test@123` | `bid-TeamLeader` | 投标组长 |
| `sales` | `Test@123` | `bid-projectLeader` | 投标项目负责人 |
| `bid_specialist` | `Test@123` | `bid-Team` | 投标专员 |
| `admin_staff` | `Test@123` | `bid-administration` | 行政人员 |

### 角色清单（RoleProfileCatalog）

> 当前系统仅存在以下 7 个标准 RoleProfile。`auditor`/`manager`/`bid_senior`/`task_executor`/`staff` 等角色已不存在。
> 角色 code 以 `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java` 中的常量定义为唯一真相来源。

| RoleProfile code | 角色名称 | 配置规则 |
|---|---|---|
| `admin` | 管理员 | 按人员 |
| `/bidAdmin` | 投标管理员 | 按人员 |
| `bid-TeamLeader` | 投标组长 | 按人员 |
| `bid-projectLeader` | 投标项目负责人 | 按岗位 |
| `bid-Team` | 投标专员 | 按部门 |
| `bid-administration` | 行政人员 | 按部门 |
| `bid-otherDept` | 跨部门协同人员 | 按人员 |

生产环境通过 `ADMIN_PASSWORD` 环境变量覆盖默认密码。任何 profile 启动后数据库至少有一个可登录账户。

## 端口约定

> **自 2026-06-21 起，开发环境统一到主工作区 `/Users/user/xiyu/worktrees/trae`。**
> 其他 worktree 不再分配独立端口和数据库，也不允许启动开发环境
> （`start-frontend.sh` / `start-backend.sh` / `dev-services.sh` 均有守卫拒绝执行）。

- **主工作区（trae）**：
  - 前端开发与演示统一使用 `1323`
  - 后端 API 统一使用 `18089`
  - Sidecar 统一使用 `8009`
  - 数据库统一使用 `xiyu_bid_main`
  - Redis 统一使用 DB `0`
- 默认访问地址：`http://127.0.0.1:1323`
- 默认后端健康检查：`http://127.0.0.1:18089/actuator/health`
- **其他 worktree（claude/codex/cursor/gemini/kimi/mimo/qoder/zcode）**：仅用于代码编辑和 git 操作，不启动开发环境。如需联调，请切换到主工作区。

## 环境坑点

1. **`npm run dev` 只会启动前端**
   如果任务需要真实链路联调，优先使用 `npm run dev:all`，不要误以为单独前端已经代表系统启动完成。

2. **根目录 `start.sh` 会强制真实 API 模式**
   当前脚本会给后端注入 `SPRING_PROFILES_ACTIVE=dev,mysql`，给前端注入 `VITE_API_MODE=api` 和 `VITE_API_BASE_URL=http://127.0.0.1:18089`。

3. **后端默认端口不是 8080，而是 18089**
   当前文档、脚本、E2E 和联调路径都以 `18089` 为准（主工作区 trae 统一端口）。

4. **Mock 模式已退役（2026-04-30）**
   `src/api/mock.js`、`src/api/mock-adapters/`、`.env.mock` 均已删除；`src/api/config.js` 硬编码 `mode: 'api'`，不再读取 `VITE_API_MODE`。旧文档里的"双模式切换"/`isMockMode()` 路径均为已退役的历史表述，不要再把它们当作现状。

5. **`check-front-data-boundaries` 不是全能扫描器**
   它拦一部分明显违规导入（如直接 import 已删除的 mock 模块），但不能覆盖所有前端数据边界；代码审查时仍需人工检查。

6. **安全配置当前比目标生产策略更宽松**
   当前 `SecurityConfig` 仍放行 `/api/auth/sessions`、`/actuator/info`、`/h2-console/**`，默认 CORS 也兼容若干历史开发端口。不要继续扩大这些范围；如需调整，必须同步文档与代码。

7. **仓库命名仍带 `POC`**
   `package.json`、`pom.xml` 中仍使用 `poc` 命名，这是历史遗留。对外表达、汇报和文档正文不要继续强化 POC 口径。

8. **后端启动必须提供必需环境变量**
   直接运行 `mvn spring-boot:run` 会因为缺少 `JWT_SECRET`、`DB_PASSWORD` 而启动失败。
   本地开发推荐使用 `backend/start.sh`（已内置默认值），或参考“推荐命令 / 手动方式：后端”传入完整环境变量。
   生产部署必须通过真实环境注入，**不得使用 `start.sh` 中的本地默认值**。

9. **launchd 守护进程会在后台自动重启 dev-services（仅主工作区）**
   自 2026-06-21 起，只有主工作区（trae）注册了 launchd 守护进程：`~/Library/LaunchAgents/com.xiyu.bid.dev-services.main.plist`。它会在登录时自动启动 `scripts/dev-services.sh watch-run`。`pkill` 杀不掉它，launchd 会立即重启。要彻底停掉，用 `launchctl bootout gui/$(id -u)/com.xiyu.bid.dev-services.main`。其他 worktree 不再注册 launchd 守护进程。

10. **watchdog 后端失败 10 次会进入 STOPPED 状态**
    新版 `scripts/dev-services.sh` 给 backend 重启加了指数退避（30s → 2min → 10min → 30min cap）。连续失败 10 次后写入 `.runtime/dev-services/backend.fail-state` 并停止重试。`scripts/dev-services.sh start` 在 fail-state 存在时会拒绝启动并打印最后的错误行。修复后用 `rm .runtime/dev-services/backend.fail-state && ./scripts/dev-services.sh start` 恢复。
    可调整：`WATCHDOG_BACKEND_MAX_FAILURES` 环境变量（默认 10）。
    **全局聚合**：`npm run agent:health-check` 会扫描所有 worktree 的 `.runtime/dev-services/` 并打印总体状况（每个 worktree 的 backend/frontend/sidecar 是否 ALIVE、最近一条 ERROR 行、fail-state 详情）。怀疑某个 worktree 在闷头重启时先跑一下这个。

11. **直接 `mvn checkstyle:check` 不会走项目的 checkstyle 配置**
    `backend/pom.xml` 中 `<configLocation>`、`<suppressionsLocation>` 等都放在 `<profile><id>java-quality</id>` 内部。不带 `-P` 直接跑 `mvn checkstyle:check` 会落到 plugin 默认值（`sun_checks.xml`、无 suppressions），扫全仓报 39k+ 错误，让人误以为项目配置 bug。
    正确做法：永远带 `-Pjava-quality`，并按需附 `,quality-audit`（不阻断）或 `,quality-strict`（违规失败）：
    ```bash
    mvn -Pjava-quality -Dquality.skip=false \
        -Dquality.includes="<pattern>" \
        -Dquality.failOnViolation=false \
        checkstyle:check
    ```
    调试实际加载的 config 用 `mvn -e -X -Pjava-quality checkstyle:check 2>&1 | grep configLocation`，应输出项目路径而不是 `sun_checks.xml`。
    完整说明见 `backend/QUALITY_GATE_GUIDE.md` "踩坑提示" 小节。

## 路径提示

- 前端业务代码：`src/`
- 后端业务代码：`backend/src/main/java/com/xiyu/bid/`
- 后端启动初始化：`backend/src/main/java/com/xiyu/bid/bootstrap/`（独立于 config 包，避免 ArchitectureTest RULE 9）
- 后端测试：`backend/src/test/java/com/xiyu/bid/`
- E2E：`e2e/`
- 标书生成 Agent：`backend/src/main/java/com/xiyu/bid/biddraftagent/`
- 文档编辑器草稿树写入：`backend/src/main/java/com/xiyu/bid/documenteditor/`
- 治理脚本：`scripts/`
- 交付与规范文档：`docs/`
- 项目知识库（Wiki）：`.wiki/pages/`（含标书需求追溯、架构、模块、缺口分析等，导航见 `.wiki/pages/_index.md`）

## 执行原则

- 真实 API 是唯一支持路径。
- 核心业务逻辑遵守 `RULES.md`：纯核心与命令式外壳分离，业务错误优先作为值返回，核心计算默认不原地修改输入。
- 文档要反映“当前事实 + 待清理事项”，不要再把目标状态写成现状。
- 发现架构测试、Mock 遗留或安全配置与文档不一致时，应优先修正文档口径，或在同次任务中同步收口代码，而不是继续掩盖。 

### 角色码解析强约束（CO-373 治理）

- **服务层禁止直调 `User.getRoleCode()`**，统一走 `EffectiveRoleResolver.resolveRoleCode(user)` 或 `DataScopeConfigService.getRoleCode(user)`。
- `User.getRoleCode()` 实体方法已标 `@Deprecated`（OSS 用户 `role_id=NULL` 时 fallback 返回 `"manager"`，是 CO-361 / CO-373 五次反复修复的根因）。
- pre-push gate 已加 `check-rolecode-direct-calls` 拦截（`scripts/check-rolecode-direct-calls.mjs`）；新增直调必须先迁到统一入口，或在调用点上方加 `// SAFE: <具体豁免理由>` 注释（仅限已记录豁免场景）。
- 完整教训：`[[lessons-learned/CO-361-five-rounds-no-fix]]`。

---

## 多 Agent 执行手册 (SOP 落地)

### 🚨 核心指令：进入工作区后的"早操" (必做)
**在进行任何代码修改前，你必须确保你的代码库是最新的：**
```bash
./scripts/sync-env.sh .
```
`sync-env.sh` 会自动完成：
1. 同步 `.env.api` 等环境模板文件
2. **main-forward 同步**：将当前分支同步到最新的 `origin/main`
   - 任务分支（`agent/*/*`）：自动 rebase
   - init 分支（`agent/*-init`）：自动 ff-only 同步（锚点分支应与 main 保持一致）
   - 保护分支（`main` 等）不做同步
   - 有未提交变更时自动 stash → 同步 → pop
   - 同步失败时给出明确的手动解决指引
   - 如遇 Flyway checksum 校验失败，可在 rebase 前手动 `rm -rf backend/target` 清理缓存
  - **创建新迁移文件前必须先运行 `scripts/next-migration-version.sh --reserve` 预约版本号**，防止并行开发时多人取了同一版本

### 1. 快速进入开发状态
1. **确认路径**：确保你位于 `/Users/user/xiyu/worktrees/[Agent名称]` 或 `/Users/user/xiyu/worktrees/[Agent名称]-[任务名]`。
2. **同步环境**：在 Worktree 根目录下执行 `./scripts/sync-env.sh .`（包含 main-forward rebase）。
3. **环境检测**：执行 `source scripts/dev-env.sh`。

### 2. 专属资源映射表（已废弃，统一到主工作区）

> **⚠️ 自 2026-06-21 起，此映射表已废弃。**
> 开发环境（前端/后端/sidecar/数据库/Redis）统一在主工作区 `/Users/user/xiyu/worktrees/trae` 启动。
> 其他 worktree 不再分配独立端口和数据库，也不允许启动开发环境。
>
> **主工作区（trae）资源配置**：
> | 资源 | 值 |
> | :--- | :--- |
> | 前端端口 | 1323 |
> | 后端端口 | 18089 |
> | Sidecar 端口 | 8009 |
> | 数据库名 | xiyu_bid_main |
> | Redis DB | 0 |
>
> **历史映射表（仅作参考，不再生效）**：
> | Agent | 前端端口 | 后端端口 | 数据库名 | Redis DB |
> | :--- | :--- | :--- | :--- | :--- |
> | Claude | 1315 | 18081 | xiyu_bid_claude | 1 |
> | Codex | 1316 | 18082 | xiyu_bid_codex | 2 |
> | Gemini | 1317 | 18083 | xiyu_bid_gemini | 3 |
> | Cursor | 1318 | 18084 | xiyu_bid_cursor | 4 |
> | Integrator | 1319 | 18085 | xiyu_bid_integrator | 5 |
> | Qoder | 1320 | 18086 | xiyu_bid_qoder | 6 |
> | Kimi | 1321 | 18087 | xiyu_bid_kimi | 7 |
> | Mimo | 1322 | 18088 | xiyu_bid_mimo | 8 |
> | Trae（主工作区） | 1323 | 18089 | xiyu_bid_main | 0 |

### 3. 协作启动命令（仅主工作区可用）

> **只有主工作区（trae）允许启动开发环境。** 其他 worktree 的 `start-frontend.sh` / `start-backend.sh` / `dev-services.sh` 有守卫拒绝执行。

Agent 必须使用包装脚本启动（仅限主工作区）：
- **启动前端**：`cd /Users/user/xiyu/worktrees/trae && ./scripts/start-frontend.sh`
- **启动后端**：`cd /Users/user/xiyu/worktrees/trae && ./scripts/start-backend.sh`
- **启动全部服务（launchd 守护）**：`cd /Users/user/xiyu/worktrees/trae && XIYU_DEV_CONFIRMED=1 ./scripts/dev-services-launchd.sh install`

其他 worktree 如需联调，请切换到主工作区：
```bash
cd /Users/user/xiyu/worktrees/trae
source scripts/dev-env.sh
./scripts/start-frontend.sh  # 或 start-backend.sh
```

### 4. 任务完成门禁
在报告完成前，必须在 **当前 Worktree** 运行：
1. `npm run build` (前端构建验证)
2. `cd backend && mvn test` (后端全量/受影响测试验证)
3. `git status` 确认只修改了授权文件。

### 5. Git 安全规则（系统级禁止绕过门禁）
**严禁** 使用 `git push --no-verify` 或 `git commit --no-verify`。

系统级实现（2026-06 加固）：
- `scripts/git` 是可执行的 git 包装器，拦截任何带 `--no-verify` 的调用并立即拒绝（列出必须通过的全部门禁）。
- 激活方式：**任何**进入 worktree 后的操作都应先 `source scripts/dev-env.sh`（会把 `scripts/` 提前塞入 PATH，使裸 `git` 命中包装器）。
- 关键入口已自激活：`scripts/sync-env.sh`（早操）、`who-touches.sh`、`agent-start-task.sh` 内部会自动 source dev-env，因此即使直接 `./scripts/sync-env.sh .` 也能获得保护。
- 验证命令：`bash scripts/check-git-wrapper.sh`
- `npm run agent:health-check` 末尾会自动包含 wrapper 安全状态。
- 下游 `.githooks/pre-push` + `pre-push-gate.sh`（agent:lock-check:changed、架构、Flyway 回滚、line-budget、E2E 选择器等）仍保留内容门禁；wrapper 负责在 hook 运行前就挡住 flag。

如果确实遇到极端情况需要紧急推送，必须：
1. 先和团队（或 maintainer）显式沟通并获得批准
2. 使用 `XIYU_ALLOW_GIT_NO_VERIFY=1 git push --no-verify ...`（会打印警告并写入 `.runtime/git-bypass/` 审计日志）
3. 在 commit message 和 PR 描述中写明原因 + 批准人
4. 事后补跑所有被跳过的门禁

永远优先修复导致门禁失败的根本问题，而不是绕过。使用绝对路径 `/usr/bin/git` 绕过同样属于违规。

### 6. 自动合并 + 解除 CI/人工 merge 堵塞（⚠️ 已过期，仅作历史参考）

> **⚠️ 已过期（2026-06 迁移到 Gitee 后失效）**
> 本节描述的 auto-merge workflow (`.github/workflows/auto-enable-merge-on-approved.yml`) 依赖 GitHub CLI (`gh`) 和 GitHub Token。
> 项目主远程仓库为 **Gitee**（`gitee.com/allinai888/bid`，remote 名 `origin`）；另设有 GitHub 远程（`github` remote，作为 AI 协作入口 + 镜像备份）。详见上方"双远程仓库配置"小节。Gitee 不支持 GitHub Actions 的 auto-merge 功能。
> 该 workflow 文件首行已标注 `[STALE for Gitee]`，**不会在 Gitee CI 中执行**。
> 本节内容仅保留作为历史设计参考；当前 PR 合并请走 Gitee 原生流程（人工点击合并或通过 Gitee API）。

**历史背景**（曾用于 GitHub 远程时期）：

**问题**：agent 任务多 → PR 多 → CI 队列 + 人工最后 "点一下合并" 成为瓶颈，严重影响并行开发效率。同时必须 100% 保留所有门禁（agent-locks、line-budget、架构、frontend/backend/e2e + strict up-to-date）。

**历史解法**（不弱化任何门禁，仅在 GitHub 远程时期生效）：

1. **审核通过后自动 enable auto-merge**（核心）
   - 新 workflow: `.github/workflows/auto-enable-merge-on-approved.yml`
   - 触发：`pull_request_review` + state=approved（即"新人审核通过"）。
   - 动作：对该 PR 执行 `gh pr merge --auto --squash --delete-branch`（如果尚未开启）。
   - GitHub **只会**在所有 branch protection 条件满足时才真正合并：
     - 1 个 required review（已满足）
     - required_status_checks 全绿（agent-locks, line-budget, frontend, backend, e2e）
     - strict: true（分支基于最新 main）
   - 效果：审核者批准后，**不需要再手动点合并按钮**。PR 会自动进入"待合并"状态，条件满足即合。
   - 失败/阻塞时仍需人工介入（与之前一致）。

2. **CI 速度优化（减少队列等待）**
   - `ci.yml` 中 backend / frontend job 现在依赖 quality-scope 的变更检测。
   - 无 backend 变更时："backend" required check 秒级 fast-succeed（保持名称不变，保护规则不破）。
   - 无 UI 变更时：frontend 同理 fast-succeed。
   - E2E（P0 核心 7 个 flow）仍正常运行（作为集成烟雾很有价值），但整体无关 PR 不再拖慢 runner 池。
   - 加上已有的 `concurrency: cancel-in-progress: true` + git wrapper 禁止 --no-verify，确保只有高质量推送进入远程 CI。
   - **强烈建议**：每次 push 前必须本地 `npm run ci:local:quick` + `bash scripts/check-git-wrapper.sh` + `npm run agent:lock-check:changed` + `npm run build`。只有本地全绿才 push，远程只做"确认"而非"发现问题"。

3. **"自动跑下一个线"（持续循环）**
   - PR 自动合入 main 后：
     - `agent-branch-cleanup.yml` 自动删除任务分支（agent/*-init 保护分支除外）。
     - `main-release.yml` 触发（post-merge gate + staging rehearsal + 打包）。
     - 其他 agent 在下次**早操**时：
       ```bash
       source scripts/dev-env.sh
       ./scripts/sync-env.sh .
       ```
       自动 rebase 到新 main，who-touches.sh 能看到更新，自己的待办 PR 可继续 review / 自动合并流程。
   - 形成：开发（本地全门禁）→ push → CI → 人工 review 批准 → 自动 enable auto-merge → GitHub 守卫满足后自动合并 → main 触发 release 线 + 其他 agent 自动感知下一轮。
   - 这就是"依次循环不断的自动执行"，同时所有门禁都在关键路径上。

**如何手动干预 / 观察**
- `gh pr view 599 --json autoMergeRequest,reviewDecision,statusCheckRollup`
- 某个 PR 想立即合（已批准且绿）：`gh pr merge 599 --auto --squash`
- 想暂时关闭自动：PR 页面点 "Disable auto-merge"。
- 合并冲突或 gate 漂移：按正常人工处理 + 补说明。

**与 Merge Queue 的关系**（进阶选项）
- 当前使用 "auto-merge + required checks strict" 实现 80% 目标，实施成本低。
- 如未来 PR 量更大、冲突更频繁，可在 branch protection 里开启 **Require merge queue**（GitHub 会为队列中的 PR 创建临时 merge commit 跑 CI，按顺序合并）。那时 "下一个线" 会更显式（有队列 UI）。目前先用 auto-merge 方案，已能显著解除手动点击瓶颈。

**实施证据**
- 所有变更仍通过本地 `npm run build`、agent checks、以及将来的 CI。
- 新 workflow 只做 "enable"，不做实际合并决策（决策权仍在 GitHub protection + 门禁脚本）。

### 5. 任务启动协议 (Lease + Auto-Detect)
不画静态目录所有权表 — 任务推进就过时。改用 **git 事实** 当主信号，文件锁仅用于 hot-paths 前置预订。

#### 5.0 同步基线（**每次开新任务都要跑**，不只是 session 开头）
"早操"只覆盖 session 开头。**任务之间也必须重新同步** — 否则 5.1 的 `who-touches.sh` 看到的是旧 main 的 diff，可能漏掉别的 agent 中途合的改动，你照样会在过期 base 上累工作。

开新任务前必跑（**一行搞定**）：
```bash
./scripts/sync-env.sh .
```
- 没改动 → no-op（2-3 秒）
- 有改动 → 早暴露 conflict，不留给 merge 时再处理
- `sync-env.sh` 内部已处理 `git fetch origin main` + `git rebase origin/main`，无需单独再跑

> **注意**：`sync-env.sh` 对不同分支有不同的同步策略：
> - 任务分支（`agent/*/*`）：自动 rebase
> - init 分支（`agent/*-init`）：自动 ff-only 同步
> - 保护分支（`main` 等）：跳过

#### 5.1 主信号：git 事实（**所有 agent 通用，必跑**）
开新任务前对你打算改的路径跑：
```bash
./scripts/who-touches.sh <path-or-glob>
```
列出有未合 commit 的 `agent/*` 分支（origin + local 去重，跳过自己）。
- 退出码 `0` + 无输出 → 干净，可以开工
- 退出码 `1` + 有输出 → 别的 agent 在动这块，看清楚再决定

#### 5.2 文件锁（hot-paths 前置预订）— 已改为 per-task 文件
`scripts/hot-paths.yml` 列出的高危路径（DB 迁移、entity、application.yml、SecurityConfig 等）改动时**必须**有 active lock。锁文件**自 2026-05-12 起改为 per-task 单文件**：

```
.agent-locks/<task-slug>.yml      ← 每个任务一个文件，新任务 = 新文件 = 零冲突
.agent-locks.yml                  ← DEPRECATED；仅做 read-only 兼容层
```

acquire/release 仍走相同 CLI（自动写到 per-task 文件）：
```bash
npm run agent:lock-acquire -- --path <path> --scope file|directory --reason "<reason>"
npm run agent:lock-release -- --path <path>
npm run agent:lock-check                # 列所有锁
npm run agent:lock-check:changed        # 仅检查当前改动是否撞锁
```

> **为什么换 per-task 文件**：原 `.agent-locks.yml` 单文件被所有 agent 同时写，每次 rebase 都撞冲突，conditioned everyone to ignore lock warnings。Per-task 文件意味着新任务 → 新文件 → 不打架；janitor 也清得更干净（删文件 vs 删行）。

#### 5.3 Gemini 任务声明（**仅当撞到 `agent/gemini-init` 时有用**）
Gemini 在 `conductor/tracks/` 系统里执行任务，会把 in-progress 任务标 `[~]` 并附 `(@gemini, scope: ...)`。如果 5.1 显示 `agent/gemini-init` 在你的目标路径有未合改动，可以查一眼具体任务上下文：
```bash
grep -h "\[~\]" conductor/tracks/*/plan.md | grep gemini
```

> **重要**：Claude / Codex / Cursor **没有等价的任务声明机制**。撞到这几个 agent 的分支时，**不要假设可以从 plan.md 查到他们的意图** — 直接看 `git log <branch>` 的 commit message，或在 PR 描述里 @ 对方协调。其他 agent 的"意图声明"靠 §6 的 commit message + push 频率自然沉淀。

#### 5.4 撞了的处置
- 等对方 push 完一个原子 commit / PR merge / Gemini 任务标 `[x]`
- 换一个不撞的任务先做
- 在 PR 描述里 @ 对方说明协调结果（"我接着你的 X 改 Y，rebase 你的 PR 后再合"）

#### 5.5 没撞 → 开工
- 你是 Gemini：在 plan.md 把任务从 `[ ]` 改成 `[~] (@gemini, scope: ...)` 让别人看见你的意图
- 你是其他 agent：**push commit 时就是你的意图声明** — commit message 写清楚 scope，下个 §6

> 验证脚本：`./scripts/who-touches.sh --self-test` 应该看到 4 个 `[PASS]`。

### 6. 纪律：每日 push WIP 分支
`who-touches.sh` 是所有 agent 共用的协调机制，准确性靠每天至少 push 一次自己 `agent/*` 分支：
- **本地 commit 不算数** — 别的 agent / session 看不到，lease 检测会漏。
- 每个工作 session 结束前推一次（即使没开 PR、即使是半成品）：
  ```bash
  git push origin HEAD:$(git rev-parse --abbrev-ref HEAD)
  ```
- push 是给 lease 检测看的，**不是为了 merge** — 半成品 WIP 分支允许、欢迎、必须存在。

> 对非 Gemini 的 agent 而言，**commit message + 改动文件就是你的"我在做这事"声明**。所以 commit message 要明确，例如 `wip: add CONTRACT profile (scope: docinsight/contract*)`，即使没 PR 别人也能从 `who-touches.sh` 输出 + `git log <branch> --oneline -5` 推断你在干什么。

### 7. 分支策略：默认分支 + 任务分支

#### 分支分类

| 分支类型 | 示例 | 用途 | 可否直接 commit |
|---|---|---|---|
| 基线分支 | `main` | 唯一真值，所有任务分支的最终合入点 | 否（受 GitHub 保护） |
| Bootstrap 种子分支 | `agent/codex-init`, `agent/cursor-init` 等 | 仅承载初始化配置（环境文件、钩子安装） | 否（`agent-worktree-guard.sh` 禁止） |
| 任务分支 | `agent/codex/project-task-breakdown-from-tender` | 每个原子任务一个独立分支 | 是 |

#### Bootstrap 分支（`*-init`）的定位

```
仓库远端（origin）：
  main
  └── agent/codex-init      ← 仅 bootstrap，不承载业务代码
  └── agent/cursor-init    ← 仅 bootstrap，不承载业务代码
  └── agent/claude-init    ← 仅 bootstrap，不承载业务代码
  └── agent/gemini-init    ← 仅 bootstrap，不承载业务代码
  └── agent/integrator-init ← 仅 bootstrap，不承载业务代码
```

Bootstrap worktree（`/Users/user/xiyu/worktrees/codex` 等）的用途：
- 通过 `agent-worktree-guard.sh` 做身份识别（`worktree_name` 匹配）
- 安装全局钩子、初始环境配置
- **不允许直接 commit 业务代码**

#### 任务分支的生命周期

每个任务用 `scripts/agent-start-task.sh --in-place` 在持久 worktree 内创建分支：

```bash
# 在持久 worktree 内创建任务分支（必须使用 --in-place）
./scripts/agent-start-task.sh codex project-task-breakdown-from-tender origin/main --in-place
# → worktree: /Users/user/xiyu/worktrees/codex（持久 worktree）
# → branch:   agent/codex/project-task-breakdown-from-tender
```

> **⚠️ 自 2026-06-22 起，不再允许创建独立的临时 worktree。**
> 所有任务必须在持久 worktree 内以 `--in-place` 模式完成。

**生命周期规则：**
1. 任务分支 PR 合入 `main` 后，该分支**视为终结**
2. 下次新任务 → 回到锚点分支，再次执行 `agent-start-task.sh --in-place`
3. **不要在 `*-init` bootstrap 分支上做任务开发**

这样做的好处：
- 避免临时 worktree 膨胀
- 简化资源管理（开发环境统一到主工作区）
- 每个分支天然是 `origin/main` 的最新衍伸，不会积累陈旧

#### 早操 main-forward 的工作原理

`sync-env.sh` 在 `agent/*/*` 任务分支上自动执行：

```
sync-env.sh .
  1. env 文件同步（.env.api 等）
  2. main-forward rebase：
     git stash (如有未提交变更)
     git fetch origin main --prune
     git rebase origin/main
     git stash pop (rebase 成功后)
```

保护分支（`main`、`agent/*-init` 等）跳过 rebase。

<!-- SPECKIT START -->
当前活跃 feature：`specs/005-mysql-integration-test-rollout/plan.md`
<!-- SPECKIT END -->
