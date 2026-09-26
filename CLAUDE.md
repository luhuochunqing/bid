<!-- OPENSPEC:START -->
> **已禁用**：OpenSpec 指令已从本项目移至 `@/.wiki/pages/open-spec.md`。
> 如需创建/更新规格文档，请使用 `speckit-*` skill（参见 CLAUDE.md §Spec Kit 流程门禁）。
<!-- OPENSPEC:END -->
# CLAUDE.md

每次对话开始，都请打招呼：同志，你好！
本文件提供本仓库的执行入口、常用命令、验证清单和环境坑点。
它的目标不是描述理想状态，而是帮助代理和开发者快速对齐当前仓库的真实情况。

## 仓库路径

- **基准仓库（.git 仓库根，临时 worktree 拉取源）**：`/Users/user/xiyu/xiyu-bid-poc`
- **开发环境宿主（唯一常驻 worktree，跑 docker/后端/前端/launchd）**：`/Users/user/xiyu/worktrees/trae`
- 后端目录：`/Users/user/xiyu/worktrees/trae/backend`
- **任务隔离 worktree**：`/Users/user/xiyu/worktrees/xiyu-<agent>-<task>`（临时，用完即删，纯源码）

### 远程仓库配置（Gitee 唯一主仓库）

> **Gitee（`origin`）是唯一主仓库**：所有 PR / 合并 / Code Review 走 Gitee，验证门禁全部在本地跑（无远程 CI runner）。
> git config 中可能残留一个 dormant 的 `github` remote（历史 AI 协作镜像），**不属于 PR / 合并 / 验证流程**——不要向它推送来"触发 CI"，也不要用它走 PR。

| Remote 名 | URL | 用途 |
|---|---|---|
| `origin` | `git@gitee.com:allinai888/bid.git` | **唯一主仓库**：PR / 合并 / 协作走这里 |

**日常推送命令**：
```bash
git push origin main    # → Gitee（唯一主仓库）
```

**重要约定**：
- **Gitee 是唯一 source of truth**：所有最终代码以 Gitee main 为准
- 所有 PR / Code Review 走 Gitee；**验证在本地跑**（`pre-push-gate.sh` + `mvn`/`npm`），本项目无远程 CI
- 推送前门禁（`pre-push-gate.sh` 等）在本地生效

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
# 推荐：一键联调（仅开发宿主 trae）
cd /Users/user/xiyu/worktrees/trae
export XIYU_DEV_CONFIRMED=1
npm run dev:all
```

```bash
# 手动方式：后端（仅开发宿主 trae）
cd /Users/user/xiyu/worktrees/trae/backend
# 推荐：使用 start.sh（已内置默认环境变量 + JVM 内存限制，需 XIYU_DEV_CONFIRMED=1）
XIYU_DEV_CONFIRMED=1 ./start.sh

# 或直接使用 mvn（需手动传入必需环境变量 + 显式 -Xmx）
# ⚠️ 禁止裸 `mvn spring-boot:run`：JVM 默认堆 = 物理内存 1/4（32GB 机器 = 8GB），
#    在 macOS swap 17GB+ 时会触发系统级 OOM Killer（exit code 137）。
#    必须显式传 -Xmx1g（与 start.sh 默认值一致）。
JWT_SECRET="xiyu-bid-poc-local-dev-secret-key-please-change-in-prod-32bytes-min" \
DB_PASSWORD="XiyuDB!2026" \
CORS_ALLOWED_ORIGINS="http://localhost:1323,http://127.0.0.1:1323" \
mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Xmx1g -Xms256m -XX:MaxMetaspaceSize=256m" \
  -Dspring-boot.run.arguments="--server.port=18089"
```

> 后端必需环境变量（未设置会导致启动失败）：
> - `JWT_SECRET`：JWT 签名密钥（至少 32 字节），`backend/start.sh` 已提供本地默认值
> - `DB_PASSWORD`：MySQL 8.0 密码（默认 `XiyuDB!2026`）
> - `CORS_ALLOWED_ORIGINS`：允许的前端源地址，默认包含 `http://localhost:1323` 与 `http://127.0.0.1:1323`
>
> 生产部署必须通过真实环境注入这些值，**不得依赖上述默认值**。

```bash
# 手动方式：前端（真实 API 模式，仅开发宿主 trae）
cd /Users/user/xiyu/worktrees/trae
VITE_API_BASE_URL=http://127.0.0.1:18089 npm run dev -- --host 127.0.0.1 --port 1323
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
# 架构边界测试（本地门禁实际运行下列三类）：
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

> 当前系统存在 **8** 个标准 RoleProfile（PR !2020 起含 `bid-SystemAdmin`）。`auditor`/`manager`/`bid_senior`/`task_executor`/`staff` 等角色已不存在。
> 角色 code 以 `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java` 中的常量定义为唯一真相来源。
> **原则**：本地 `admin` 与 OSS 无关；OSS 用户角色/菜单来自 OSS；`bid-SystemAdmin` **不再映射为 admin**。

| RoleProfile code | 角色名称 | 配置规则 |
|---|---|---|
| `admin` | 管理员（本地超级管理员） | 本地 bootstrap，不走 OSS |
| `/bidAdmin` | 投标管理员 | OSS 角色码 |
| `bid-SystemAdmin` | 投标系统管理员 | OSS 角色码（权限基线等同 /bidAdmin，不含 `all`） |
| `bid-TeamLeader` | 投标组长 | OSS 角色码 / 人员 |
| `bid-projectLeader` | 投标项目负责人 | OSS 角色码 / 岗位 |
| `bid-Team` | 投标专员 | OSS 角色码 / 部门 |
| `bid-administration` | 行政人员 | OSS 角色码 / 部门 |
| `bid-otherDept` | 跨部门协同人员 | OSS 角色码 |

生产环境通过 `ADMIN_PASSWORD` 环境变量覆盖默认密码。任何 profile 启动后数据库至少有一个可登录账户。

## 端口约定

> **自 2026-06-21 起，开发环境统一到开发宿主 `/Users/user/xiyu/worktrees/trae`。**
> 临时任务 worktree 纯源码，不启动开发环境
> （`start-frontend.sh` / `start-backend.sh` / `dev-services.sh` 均有守卫拒绝执行）。

- **开发宿主（trae）**：
  - 前端开发与演示统一使用 `1323`
  - 后端 API 统一使用 `18089`
  - Sidecar 统一使用 `8009`
  - 数据库统一使用 `xiyu_bid_main`
  - Redis 统一使用 DB `0`
- 默认访问地址：`http://127.0.0.1:1323`
- 默认后端健康检查：`http://127.0.0.1:18089/actuator/health`
- **临时任务 worktree**（`xiyu-<agent>-<task>`）：纯源码，仅做代码编辑和 git 操作，不启动开发环境。如需联调，请切换到开发宿主。

### 多项目端口分配规范（避免跨项目冲突）

> **自 2026-07-05 起，本地多项目端口分配固定如下，避免 nexus / xiyu 等项目互相抢占 3306/6379 标准端口。**
> 根因：2026-07-05 nexus-db 容器抢占 3306 端口，导致 xiyu 后端连错库、数据迁移失败、服务挂掉。

| 项目 | MySQL | Redis | 后端 | 前端 | 备注 |
|------|-------|-------|------|------|------|
| **xiyu（本项目）** | `3306` | `6379` | `18089` | `1323` | 与 application.yml 默认配置一致，不需要额外环境变量 |
| **nexus** | `3307` | `26380` | - | - | nexus 项目容器已用此端口，不与 xiyu 冲突 |
| **其他新项目** | `3xxxx` 段 | `3xxxx` 段 | - | - | 避免与 xiyu 标准端口冲突 |

**xiyu 项目的数据库栈管理**：
- 容器配置已固化为 `docker-compose.yml`（项目根目录）
- 启动数据库栈：`docker compose up -d`（在项目根目录）
- 停止数据库栈：`docker compose down`（volume 保留，数据不丢）
- 端口冲突预检：`bash scripts/check-port-conflicts.sh`（启动 dev-services 前运行）

**关键配置（docker-compose.yml 中固化）**：
- MySQL `sql_mode` 去掉 `NO_ZERO_DATE,NO_ZERO_IN_DATE`：兼容 V1077 迁移（清理 zero-date 记录）
- MySQL `collation-server=utf8mb4_unicode_ci`：兼容历史数据 collation，避免 JOIN 时 collation 冲突（V1092 教训）
- MySQL volume = `xiyu-bid-mysql-data`（包含历史数据，**不要换成其他 volume**）
- Redis volume = `xiyu-bid-redis-data`

**遇到端口冲突时**：
1. 运行 `bash scripts/check-port-conflicts.sh` 诊断
2. 停掉冲突容器：`docker stop <冲突容器名>`
3. 启动 xiyu 数据库栈：`docker compose up -d`
4. 重启后端：`./scripts/dev-services.sh start`（仅开发宿主）

## 环境坑点

1. **`npm run dev` 只会启动前端**
   如果任务需要真实链路联调，优先使用 `npm run dev:all`，不要误以为单独前端已经代表系统启动完成。

2. **根目录 `start.sh` 会强制真实 API 模式**
   当前脚本会给后端注入 `SPRING_PROFILES_ACTIVE=dev,mysql`，给前端注入 `VITE_API_BASE_URL=http://127.0.0.1:18089`。

3. **后端默认端口不是 8080，而是 18089**
   当前文档、脚本、E2E 和联调路径都以 `18089` 为准（开发宿主 trae 统一端口）。

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

9. **launchd 守护进程会在后台自动重启 dev-services（仅开发宿主）**
   自 2026-06-21 起，只有开发宿主（trae）注册了 launchd 守护进程：`~/Library/LaunchAgents/com.xiyu.bid.dev-services.main.plist`。它会在登录时自动启动 `scripts/dev-services.sh watch-run`。`pkill` 杀不掉它，launchd 会立即重启。要彻底停掉，用 `launchctl bootout gui/$(id -u)/com.xiyu.bid.dev-services.main`。临时任务 worktree 不注册 launchd 守护进程。

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

12. **`start-backend.sh` 的 Redis 16379 fallback 已过期**
    脚本里 `FALLBACK_REDIS_PORT="16379"` 是历史遗留（多 Agent worktree 时期分配的独立 Redis 端口）。自 2026-06-21 起开发环境统一到开发宿主，Redis 固定为 `6379`（见 `docker-compose.yml`）。
    如果 6379 不可用，**不要依赖 16379 fallback**（16380 已被 nexus-redis 占用，16379 fallback 会连到错误的 Redis 实例）。正确做法：`docker compose up -d` 启动 xiyu 专用 Redis 容器。

## 路径提示

- 后端启动初始化：`backend/src/main/java/com/xiyu/bid/bootstrap/`（独立于 config 包，避免 ArchitectureTest RULE 9）
- 标书生成 Agent：`backend/src/main/java/com/xiyu/bid/biddraftagent/`
- 文档编辑器草稿树写入：`backend/src/main/java/com/xiyu/bid/documenteditor/`
- 项目知识库（Wiki）：`.wiki/pages/`（含标书需求追溯、架构、模块、缺口分析等，导航见 `.wiki/pages/_index.md`）

其余目录（`src/`、`backend/src/`、`e2e/`、`scripts/`、`docs/`）按惯例布局，直接 `ls` 即可。

## 执行原则

- 真实 API 是唯一支持路径。
- 核心业务逻辑遵守 `RULES.md`：纯核心与命令式外壳分离，业务错误优先作为值返回，核心计算默认不原地修改输入。
- 文档要反映“当前事实 + 待清理事项”，不要再把目标状态写成现状。
- 发现架构测试、Mock 遗留或安全配置与文档不一致时，应优先修正文档口径，或在同次任务中同步收口代码，而不是继续掩盖。
- **Wiki 维护纪律**：回答涉及 ≥3 个文件或跨模块的复杂问题后，必须判断是否回填新 wiki 页面（复合查询回填，详见 `.wiki/WIKI.md §2 触发点 3`）。任务收尾时必须过 `agent-finish-task.sh` 的 wiki checkpoint。

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
# 1. 同步环境（在开发宿主或任意持久 worktree 中执行）
./scripts/sync-env.sh .
# 2. 开新任务时，自动从基准仓库拉独立临时 worktree（纯源码，收尾自动删）
./scripts/agent-start-task.sh <agent名> <task-slug> origin/main
# -> 创建 /Users/user/xiyu/worktrees/xiyu-<agent>-<task-slug> 临时 worktree
# -> 创建分支 agent/<agent>/<task-slug>
# 3. 进入临时 worktree 开发
cd /Users/user/xiyu/worktrees/xiyu-<agent>-<task-slug>
```
`sync-env.sh` 会自动完成：
1. 同步 `.env.example` 等环境模板文件
2. **main-forward 同步**：将当前分支同步到最新的 `origin/main`
   - 任务分支（`agent/*/*`）：自动 rebase
   - init 分支（`agent/*-init`）：自动 ff-only 同步（锚点分支应与 main 保持一致）
   - 保护分支（`main` 等）不做同步
   - 有未提交变更时自动 stash → 同步 → pop
   - 同步失败时给出明确的手动解决指引
   - 如遇 Flyway checksum 校验失败，可在 rebase 前手动 `rm -rf backend/target` 清理缓存
  - **创建新迁移文件前必须先运行 `scripts/next-migration-version.sh --reserve` 预约版本号**，防止并行开发时多人取了同一版本

### 1. 任务完成门禁
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

### 6. 任务启动协议、每日 push 纪律、分支策略

> 详细流程已迁移到 **`docs/agent-workflow.md`**（已入库，团队共享），不再常驻每个 session 的上下文。
> 本地 Claude Code 可用 skill **`agent-workflow`** 自动路由到该文件。
> 开新任务、判断路径是否被别的 agent 改动中、申请/释放文件锁、决定分支归属时，请先读 `docs/agent-workflow.md`。

一句话速览：开新任务前跑 `./scripts/sync-env.sh .` 同步基线，再对目标路径跑 `./scripts/who-touches.sh <path>` 做租约检测；hot-paths 改动必须先 `npm run agent:lock-acquire`；每个 session 结束前 push 一次 WIP 分支（本地 commit 不算数）；任务分支（`agent/*/*`）在临时 worktree 中开发，`*-init` 分支只做 bootstrap。

当前活跃 feature 规格以 `specs/` 目录为准（每目录含 spec/plan/tasks.md，按 feature 编号递增）。

