# 多 Agent 并行开发 SOP — 可移植工程化方案

> 本文档脱胎于"西域数智化投标管理平台"的多 Agent 协作实践，
> 目标是把踩过的坑提炼成**可直接复制到任何项目**的工程化清单。
>
> 同行项目可以直接复制本文件到仓库根目录，按 "[新项目落地清单](#新项目落地清单)" 逐项实施。

---

## 一、设计原则

### 1. 物理上不可能 > 规则上禁止

不要靠"约定俗成"或"团队纪律"保障安全。
要靠 **branch protection + git wrapper + hook 硬防线**让违规操作物理上不可行。

### 2. Ratchet（棘轮）模式

任何护栏的阈值"只能更严，不能更松"。
比如 root commit 数 ratchet = 当前值，发现退化立即 CI 红。给数字一个具体值，而不是写"应该 ≤ 1"。

### 3. 可见性即治理

不能把坏状态藏在某个 worktree 的本地日志里。
**一个命令**能扫描全机器 / 全 worktree / 全分支状态。

### 4. 自愈优先于人为兜底

任何"需要人去清理"的状态都会积累成债务。
定时任务 + 自动 PR + auto-merge = 0 人介入。

### 5. Agent 专属资源隔离

每个 Agent Worktree 拥有**专属端口 + 专用数据库 + 专用 Redis DB**，
物理隔离使并行开发互不干扰。

---

## 二、架构总览

```
┌────────────────────────────────────────────────────────────────┐
│                    5 层防御门禁体系                              │
├──────────┬──────────┬──────────┬──────────┬───────────────────┤
│  L1      │  L2      │  L3      │  L4      │  L5               │
│ PATH包装器 │ Git Alias │ git hooks│ CI/CD    │ 自动愈合           │
│ 系统级拦截 │ 硬绕过防线 │ pre-commit│ GitHub   │ Janitor + Cron    │
│--no-verify│ 过滤参数  │ +pre-push│ Workflows│ 自动清理过期锁     │
└──────────┴──────────┴──────────┴──────────┴───────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                    两级 Worktree 策略                           │
├────────────────────────┬───────────────────────────────────────┤
│ 一级：持久 Worktree     │ 二级：临时 Worktree                    │
│ 串行小任务              │ 并行/破坏性变更                        │
│ 每个 Agent 一个持久目录 │ 按任务创建隔离环境                      │
│ 端口/DB 一次性初始化    │ 用完即删                               │
│ 切分支完成不同任务       │ 适合 E2E 测试、改表结构等              │
└────────────────────────┴───────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                    任务生命周期                                 │
├───────┬────────┬────────┬────────┬────────┬───────────────────┤
│ 早操  │ 开任务  │ 开发    │ 验证    │ 提交    │ PR/完成           │
│ sync  │ 建分支  │ 编码    │ 测试    │ 门禁    │ PR → Merge →      │
│ rebase│ 锁文件  │ 分阶段  │ 架构    │ push    │ 清理分支           │
└───────┴────────┴────────┴────────┴────────┴───────────────────┘
```

---

## 三、核心：Agent 工作区资源隔离

这是整个多 Agent 协作的**物理基础**。没有资源隔离，所有 Agent 共享一个数据库、一个端口，并行开发就是空话。
### 3.1 隔离维度

每个 Agent Worktree 在 4 个维度上物理隔离：

| 维度 | 隔离方式 | 为什么必要 |
|------|---------|-----------|
| **前端端口** | 每个 Agent 独立端口（1314~132N） | 可同时启动多个前端服务互不干扰 |
| **后端端口** | 每个 Agent 独立端口（18080~1808N） | 后端进程可独立运行、独立调试 |
| **Sidecar端口** | 每个 Agent 独立端口（8000~800N） | 文档转换等 sidecar 服务不冲突 |
| **数据库名** | 每个 Agent 独立 MySQL database | 迁移脚本、测试数据完全隔离 |
| **Redis DB** | 每个 Agent 独立 Redis DB index | 缓存/会话数据不串 |

### 3.2 Agent 与 Worktree 分配表

本项目当前有 **1 个主基准区 + 8 个持久 Worktree**，每个对应一个 AI Agent：

| Agent | 目录路径 | 前端端口 | 后端端口 | Sidecar端口 | 数据库名 | Redis DB | 用途 |
|-------|---------|---------|---------|------------|---------|---------|------|
| **main（基准区）** | `/Users/user/xiyu/xiyu-bid-poc/` | 1314 | 18080 | 8000 | `xiyu_bid_main` | 0 | 主仓库，PR 合入目标 |
| **claude** | `/Users/user/xiyu/worktrees/claude/` | 1315 | 18081 | 8001 | `xiyu_bid_claude` | 1 | Claude Agent 专属 |
| **codex** | `/Users/user/xiyu/worktrees/codex/` | 1316 | 18082 | 8002 | `xiyu_bid_codex` | 2 | Codex CLI Agent 专属 |
| **gemini** | `/Users/user/xiyu/worktrees/gemini/` | 1317 | 18083 | 8003 | `xiyu_bid_gemini` | 3 | Gemini Agent 专属 |
| **cursor** | `/Users/user/xiyu/worktrees/cursor/` | 1318 | 18084 | 8004 | `xiyu_bid_cursor` | 4 | Cursor Agent 专属 |
| **integrator** | `/Users/user/xiyu/worktrees/integrator/` | 1319 | 18085 | 8005 | `xiyu_bid_integrator` | 5 | 集成验证 Agent |
| **qoder** | `/Users/user/xiyu/worktrees/qoder/` | 1320 | 18086 | 8006 | `xiyu_bid_qoder` | 6 | Qoder Agent 专属 |
| **trae** | `/Users/user/xiyu/worktrees/trae/` | 1321 | 18087 | 8007 | `xiyu_bid_trae` | 7 | Trae Agent 专属 |
| **assistant** | `/Users/user/xiyu/worktrees/assistant/` | 1322 | 18088 | 8008 | `xiyu_bid_assistant` | 8 | Assistant Agent 专属 |

> **注**：上表中 trae 和 assistant 的 worktree 已在磁盘上存在，但 `scripts/dev-env.sh` 尚未配置它们的端口/数据库映射（仅配置了 claude/codex/gemini/cursor/integrator/qoder 六组）。新增 Agent 时需同步更新 `dev-env.sh` 的 elif 分支。

### 3.3 递增规律

资源分配遵循简单的**递增规则**，方便扩展：

| 资源 | 基准值（main） | 递增步长 | 第 N 个 Agent 公式 |
|------|---------------|---------|-------------------|
| 前端端口 | 1314 | +1 | `1314 + N` |
| 后端端口 | 18080 | +1 | `18080 + N` |
| Sidecar端口 | 8000 | +1 | `8000 + N` |
| 数据库名 | `xiyu_bid_main` | 命名替换 | `xiyu_bid_{agent_name}` |
| Redis DB | 0 | +1 | `N` |

### 3.4 自动识别机制（dev-env.sh）

通过在 `scripts/dev-env.sh` 中**根据当前目录路径自动匹配**，无需手工配置：

```bash
CURRENT_DIR="$(pwd)"

if [[ "$CURRENT_DIR" == *"worktrees/claude"* ]]; then
  export FRONTEND_PORT=1315
  export BACKEND_PORT=18081
  export SIDECAR_PORT=8001
  export DB_NAME="xiyu_bid_claude"
  export REDIS_DB=1
elif [[ "$CURRENT_DIR" == *"worktrees/codex"* ]]; then
  export FRONTEND_PORT=1316
  export BACKEND_PORT=18082
  export SIDECAR_PORT=8002
  export DB_NAME="xiyu_bid_codex"
  export REDIS_DB=2
elif [[ "$CURRENT_DIR" == *"worktrees/gemini"* ]]; then
  export FRONTEND_PORT=1317
  export BACKEND_PORT=18083
  export SIDECAR_PORT=8003
  export DB_NAME="xiyu_bid_gemini"
  export REDIS_DB=3
elif [[ "$CURRENT_DIR" == *"worktrees/cursor"* ]]; then
  export FRONTEND_PORT=1318
  export BACKEND_PORT=18084
  export SIDECAR_PORT=8004
  export DB_NAME="xiyu_bid_cursor"
  export REDIS_DB=4
elif [[ "$CURRENT_DIR" == *"worktrees/integrator"* ]]; then
  export FRONTEND_PORT=1319
  export BACKEND_PORT=18085
  export SIDECAR_PORT=8005
  export DB_NAME="xiyu_bid_integrator"
  export REDIS_DB=5
elif [[ "$CURRENT_DIR" == *"worktrees/qoder"* ]]; then
  export FRONTEND_PORT=1320
  export BACKEND_PORT=18086
  export SIDECAR_PORT=8006
  export DB_NAME="xiyu_bid_qoder"
  export REDIS_DB=6
else
  # main 基准区默认值
  export FRONTEND_PORT=1314
  export BACKEND_PORT=18080
  export SIDECAR_PORT=8000
  export DB_NAME="xiyu_bid_main"
  export REDIS_DB=0
fi
```

### 3.5 分支级数据库后缀

在同一 worktree 内切换不同任务分支时，数据库名自动追加分支名后缀，实现细粒度数据隔离：

```bash
# 在 agent worktree 内（非 main/锚点分支）
CURRENT_BRANCH="agent/codex/project-page"
CLEAN_BRANCH=$(echo "$CURRENT_BRANCH" | sed -E 's/^agent\/[a-zA-Z0-9_-]+\///')
CLEAN_BRANCH=$(echo "$CLEAN_BRANCH" | tr '[:upper:]' '[:lower:]' | tr '/-' '_' | tr -cd 'a-z0-9_')
CLEAN_BRANCH="${CLEAN_BRANCH:0:30}"
CLEAN_BRANCH=$(echo "$CLEAN_BRANCH" | sed -e 's/^_*//' -e 's/_*$//')
export DB_NAME="${DB_NAME}_${CLEAN_BRANCH}"
```

最终数据库名示例：
- 在 codex worktree、`agent/codex/project-page` 分支上 → `xiyu_bid_codex_project_page`
- 在 cursor worktree、`agent/cursor/tender-export` 分支上 → `xiyu_bid_cursor_tender_export`

### 3.6 Docker Compose 变量化映射

为了让 Docker 容器为每个 Agent 创建独立的数据库实例，在 `docker-compose.yml` 中通过环境变量注入：

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: ${DB_NAME:-xiyu_bid_main}
      MYSQL_USER: ${DB_USER:-xiyu}
      MYSQL_PASSWORD: ${DB_PASSWORD:-secret}
    ports:
      - "${DB_PORT:-3306}:3306"

  redis:
    image: redis:7
    command: ["redis-server", "--save", ""]
    # Redis DB index 在应用层通过 spring.data.redis.database 设置
```

后端 `application.yml` 引用环境变量：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:${DB_PORT:-3306}/${DB_NAME}?...
  redis:
    database: ${REDIS_DB:-0}
```

### 3.7 服务启动命令中的隔离体现

脚本自动读取当前目录确定 Agent 身份，无需手动指定端口和数据库：

```bash
# 一键启动
cd /Users/user/xiyu/worktrees/codex
export XIYU_DEV_CONFIRMED=1
npm run dev:all
# 自动使用：前端 1316 + 后端 18082 + sidecar 8002 + DB xiyu_bid_codex + Redis DB 2

# 分步启动后端
cd /Users/user/xiyu/worktrees/codex/backend
XIYU_DEV_CONFIRMED=1 ./start.sh
# start.sh 内部 source dev-env.sh → 自动获取 18082 + xiyu_bid_codex

# 分步启动前端
cd /Users/user/xiyu/worktrees/codex
VITE_API_MODE=api VITE_API_BASE_URL=http://127.0.0.1:18082 npm run dev -- --host 127.0.0.1 --port 1316
```

### 3.8 多 Agent 联调命令

通过 `npm run agent:*` 统一管理本 worktree 的服务生命周期，脚本自动完成资源映射：

```bash
npm run agent:morning  # 早操三连 + 重启服务（git fetch + rebase + sync-env + 重启）
npm run agent:up       # 安装/启动 launchd 服务（自动映射端口和数据库）
npm run agent:restart  # 重启 launchd 服务
npm run agent:status   # 查看当前 worktree 的服务状态
npm run agent:logs     # 查看日志
npm run agent:stop     # 停止服务
```

这些命令内部通过 `scripts/agent-dev.sh` → `scripts/dev-env.sh` 完成资源识别。



## 四、基础设施准备

### 4.1 仓库目录结构

在开始多 Agent 协作前，项目仓库需要以下基础设施：

```
<project-root>/
├── .githooks/
│   ├── pre-commit                # 提交前门禁（15+ 项检查）
│   ├── pre-push                  # 推送前门禁（历史保护 + 凭证检查）
│   ├── git-push-wrapper.sh       # push 别名包装器（过滤 --no-verify）
│   └── git-commit-wrapper.sh     # commit 别名包装器（过滤 --no-verify）
├── scripts/
│   ├── dev-env.sh                # ⭐ Agent 环境识别（目录→端口/数据库/Redis 映射）
│   ├── dev-services.sh           # 本地服务生命周期管理
│   ├── dev-services-launchd.sh   # macOS launchd 包装器
│   ├── agent-dev.sh              # 多 Agent 服务统一入口（agent:morning/up/restart/status/logs）
│   ├── agent-start-task.sh       # ⭐ 核心：任务启动脚本（创建 worktree / in-place 分支）
│   ├── sync-env.sh               # 环境文件同步 + main-forward rebase
│   ├── install-githooks.sh       # Git hooks 启用脚本
│   ├── git                       # 系统级 git 安全包装器（PATH 拦截 --no-verify）
│   ├── pr-create.sh              # 统一 PR 创建脚本
│   ├── pre-push-gate.sh          # 推送前 14 道门禁
│   ├── who-touches.sh            # 文件冲突预检（谁在改这个文件）
│   ├── manage-agent-locks.mjs    # 文件锁管理（获取/释放）
│   ├── check-agent-locks.mjs     # 文件锁检查
│   ├── check-hot-path-locks.mjs  # 热路径锁强制检查
│   ├── check-line-budgets.mjs    # 行预算门禁
│   ├── check-flyway-versions.sh  # Flyway 版本冲突检查
│   ├── check-flyway-rollback.sh  # 回滚脚本完整性检查
│   ├── next-migration-version.sh # 迁移版本号生成
│   ├── sweep-merged-branches.sh  # 已合入分支清理
│   ├── agent-health-check.sh/mjs # 全 worktree 健康扫描
│   ├── hot-paths.yml             # ⭐ 热路径定义（哪些文件必须加锁）
│   ├── line-budget.config.json   # ⭐ 行预算配置
│   └── ci-pre-pr.sh              # PR 前一站式门禁
├── .agent-locks/                 # ⭐ 文件锁注册表目录（per-task .yml 文件）
├── AGENTS.md                     # 项目导航地图（按任务找信息）
├── RULES.md                      # 开发红线与四阶段流程
├── CLAUDE.md                     # 执行入口与常用命令
├── .wiki/                        # 项目知识库
└── docker-compose.yml            # Docker Compose（DB_NAME 变量化）
```

### 4.2 GitHub 保护设置

| 设置项 | 值 |
|--------|-----|
| Require PR before merging | ✅ |
| Require linear history | ✅ |
| Disallow force pushes | ✅ |
| Disallow deletions | ✅ |
| **Enforce on admins** | ✅ **（别忘这一勾，否则前面全是装样子）** |
| Required status checks | 至少包含 branch-history-guard |

---

## 五、两级 Worktree 策略

### 5.1 一级：持久 Worktree（串行小任务）

每个 Agent **只有一个**持久 worktree，端口/数据库/依赖**一次性初始化**。
后续小改动在持久 worktree 内切分支完成。

创建持久 worktree：

```bash
# 在每个 Agent 自己的环境下初始化
git clone <repo-url> /Users/user/xiyu/worktrees/<agent-name>
cd /Users/user/xiyu/worktrees/<agent-name>
git checkout -b agent/<agent-name>-init
git push -u origin agent/<agent-name>-init
bash scripts/install-githooks.sh
source scripts/dev-env.sh    # 激活端口/数据库映射
```

例 — 创建 codex Agent 的持久 worktree：

```bash
git clone git@github.com:org/project.git /Users/user/xiyu/worktrees/codex
cd /Users/user/xiyu/worktrees/codex
git checkout -b agent/codex-init
git push -u origin agent/codex-init
bash scripts/install-githooks.sh
source scripts/dev-env.sh
# 自动识别为 codex → 端口 1316 + 18082 + 8002 + DB xiyu_bid_codex + Redis DB 2
```

持久 worktree 内切分支**不改变端口/数据库分配**，资源随目录固定而非分支：

```bash
# 串行任务：在同一个 worktree 内切分支
cd /Users/user/xiyu/worktrees/codex
git checkout agent/codex-init          # 回到锚点
git pull origin main                   # 同步最新 main
git checkout -b agent/codex/project-page  # 切新任务分支
# 端口/数据库不变，仍是 codex 的分配
# 数据库会追加分支后缀：xiyu_bid_codex_project_page
```

### 5.2 二级：临时 Worktree（并行/破坏性变更）

适合以下场景时创建隔离 worktree：

- 需要同时运行前后端联调（两个 Agent 各自启动全套服务）
- E2E 测试需要专属环境
- 破坏性变更（改表结构、数据库连接等）
- 两个 task 并行开发需要各自独立验证

```bash
# 创建新 worktree
scripts/agent-start-task.sh <agent> <task> origin/main

# 例：为 codex Agent 创建 project-page 任务 worktree
scripts/agent-start-task.sh codex project-page origin/main
# → 在 /Users/user/xiyu/worktrees/codex-project-page/ 创建
# → 分支：agent/codex/project-page
# → 注意：临时 worktree 不在 dev-env.sh 的路径匹配中，会 fallback 到 main 基准区的端口/DB
#   因此临时 worktree 需要手动指定资源或修改 dev-env.sh 添加匹配规则

# 任务结束后删除 worktree
git worktree remove /Users/user/xiyu/worktrees/codex-project-page --force
git branch -D agent/codex/project-page
git push origin --delete agent/codex/project-page
```

### 5.3 分支命名规范

| 分支类型 | 格式 | 示例 | 说明 |
|---------|------|------|------|
| Worktree 锚点 | `agent/<name>-init` | `agent/codex-init` | 常驻基线，**禁止直接开发**，**禁止删除**（本地和远端均不可删） |
| 任务开发 | `agent/<name>/<task-slug>` | `agent/codex/project-page` | 每个原子任务一个独立分支，PR 合入后可删 |
| 集成分支 | `integrate/<name>` | `integrate/baseline` | 多 Agent 集成测试用 |

### 5.4 一键开任务脚本

```bash
# --in-place 模式：早操三连 + 在当前 worktree 内创建分支（最常用）
scripts/agent-start-task.sh codex project-page origin/main --in-place

# 自动完成：
#   1. git fetch --prune origin
#   2. git rebase origin/main
#   3. bash scripts/sync-env.sh . （复制环境文件 + main-forward rebase）
#   4. 创建分支 agent/codex/project-page
#   5. 安装 git hooks + 配置 git alias（防止 --no-verify 绕过）
#   6. pnpm install --prefer-offline（离线优先装依赖）
#   7. 生成 .agent-task-context 文件（记录 agent/branch/task 元信息）

# 如果涉及高冲突路径，加锁：
scripts/agent-start-task.sh codex project-page origin/main --in-place \
  --lock src/views/Project/Detail.vue \
  --lock-dir backend/src/main/resources/db/migration-mysql/ \
  --lock-reason "项目详情页改造 + 新增迁移" \
  --lock-days 3

# 加锁后首次提交需包含锁文件
git add .agent-locks/
git commit -m "chore: register initial agent locks for project-page"
```

---

## 六、任务生命周期

### Phase 0: 早操（每日开工前）

```bash
# 暗号：早操SOP → 自动执行：
git fetch origin                    # 拉取远端更新
git rebase origin/main              # rebase 到最新 main
bash scripts/sync-env.sh .          # 同步环境文件 + main-forward

# 或一键命令
npm run agent:morning
# 实际执行：git fetch + rebase + sync-env + 清理已合并分支 + 重启 launchd 服务
```

### Phase 1: 开任务

```bash
# 暗号：开个任务 XX  / 开个分支 XX
scripts/agent-start-task.sh codex project-page origin/main --in-place
```

### Phase 2: 开发（四阶段流程）

按 RULES.md 中的四阶段执行：

1. **Plan（规划）** — 先明确目标、改动范围、验收标准
2. **TDD（测试驱动）** — Red → Green → Refactor 循环
3. **Code Review（代码审查）** — 自审：分层边界、依赖方向、安全
4. **Refactor-Clean（重构清理）** — 消重、删死代码、删无效分支

### Phase 3: 提交

```bash
git add <files>
git commit -m "feat: 你的提交信息"
# pre-commit 钩子自动运行 15+ 项检查（行预算、Flyway版本冲突、回滚脚本、热路径锁...）
```

### Phase 4: 推送与 PR

```bash
git push
# 门禁流程（自动）：
#   1. L1 PATH 包装器拦截 --no-verify
#   2. L2 Git Alias 强制走 git-push-wrapper.sh
#   3. wrapper 调用 pre-push-gate.sh（14 道门禁）
#   4. 执行真实 git push（触发 .githooks/pre-push 二次门禁）
#   5. push 成功后 → 自动调用 pr-create.sh 创建 PR
```

PR 创建后等待 GitHub CI 通过，1 个 required review 批准后 auto-merge 自动开启。

### Phase 5: 完成

```bash
# PR merged 后
git checkout agent/codex-init
git pull origin main
git branch -D agent/codex/project-page
```

---

## 七、5 层防御门禁体系

### L1: 系统级 git 安全包装器

`scripts/git` — 通过 `source scripts/dev-env.sh` 注入 PATH，
对所有 `git` 命令生效：

- 拦截 `git commit --no-verify` 和 `git push --no-verify`
- 提示改用正确方式修复门禁
- 逃生口：`XIYU_ALLOW_GIT_NO_VERIFY=1`（仅紧急情况经团队批准后使用，会记录审计日志）

### L2: Git Alias 硬防线

由 `agent-start-task.sh` 自动配置，**不依赖 shell PATH**，即使 `scripts/git` 被绕过仍生效：

```bash
git config alias.push '!bash .githooks/git-push-wrapper.sh'
git config alias.commit '!bash .githooks/git-commit-wrapper.sh'
```

无论用户输入 `git push --no-verify` 还是 `git commit -n`，都强制走包装器脚本。

### L3: Git Hooks 门禁

#### pre-commit（15+ 项检查，提交时自动触发）

| 检查项 | 脚本 | 强制/警告 |
|--------|------|----------|
| Agent worktree 保护 | `agent-worktree-guard.sh` | ⛔ 强制 |
| Java 编码规范（checkstyle/PMD/SpotBugs） | `check-java-coding-standards.sh` | ⛔ 强制 |
| 文件行预算（200行软上限 / 300行硬上限） | `check-line-budgets.mjs` | ⚠ 硬上限强制 |
| Token 治理 | `check:token-governance` | ⛔ 强制 |
| Vue 测试脚手架规范 | `check-vue-test-boilerplate.mjs` | ⛔ 强制 |
| 前端枚举硬编码检测 | `check-vue-enum-mapping.mjs` | ⚠ 警告 |
| 热路径锁检查（修改 hot-path 必须已有锁） | `check-hot-path-locks.mjs` | ⛔ 强制 |
| System-scope jar 检查 | `check-system-scope-jar.sh` | ⛔ 强制 |
| Flyway 版本冲突预检 | `check-flyway-versions.sh` | ⛔ 强制 |
| Flyway 迁移头部质量 | `check-migration-headers.sh` | ⛔ 强制 |
| E2E 选择器质量 | `check-e2e-selectors.mjs` | ⛔ 强制 |
| 回滚脚本完整性（每个 V*.sql 必须对应 U*.sql） | `check-flyway-rollback.sh` | ⛔ 强制 |
| E2E-UI 联动提醒 | `check-e2e-ui-sync.mjs` | ⚠ 警告 |
| API URL 一致性 | `check-api-urls.mjs` | ⚠ 警告 |
| ESLint | eslint on staged files | ⚠ 警告 |
| 分支卫生提醒 | `check-branch-hygiene.sh` | ⚠ 提醒 |

#### pre-push（推送时自动触发）

| 检查项 | 说明 |
|--------|------|
| 远端凭证检查 | Gitee 必须设 `GITEE_TOKEN` |
| GitHub CLI 登录检查 | `gh auth status` |
| agent/* 分支 PR 提醒 | 提示使用 `pr-create.sh` |
| 保护分支 force push 拦截 | 禁止 force push 到 main/master/release/* |
| 历史根提交数检查 | `git rev-list --max-parents=0` ratchet |

### L4: CI/CD 工作流（GitHub Actions）

| 工作流 | 说明 |
|--------|------|
| `branch-history-guard.yml` | 检查 root commit 数 ratchet，防止历史撕裂 |
| `flyway-migrate-dryrun.yml` | 起真 MySQL container 从 baseline 跑迁移到 HEAD |
| `agent-locks.yml` | 检查 PR 修改 hot-path 时是否覆盖锁 |
| `agent-locks-janitor.yml` | 定时任务自动清理过期锁 |
| `auto-enable-merge-on-approved.yml` | review 批准后自动开启 auto-merge（squash） |
| `ci.yml` | 前端构建 + 后端测试 + E2E |

### L5: 自动愈合（Janitor）

定时任务自动处理：
- 清理过期文件锁
- 清理 stale worktree
- 自动开 PR 修复可自动修复的问题
- auto-merge 减少人为介入

### 门禁绕过难度评估

| 层次 | 机制 | 能否绕过 | 绕过成本 |
|------|------|---------|---------|
| L1 | PATH 包装器 | 需手动 `unset PATH` 或使用绝对路径 `/usr/bin/git` | 低 |
| L2 | Git Alias | `git config --unset alias.push` | 中（需知道别名存在） |
| L3 | Git Hooks | `git config core.hooksPath /dev/null` | 中 |
| L4 | CI/CD | 无绕过路径 | **不可绕过** |
| L5 | Janitor | 无绕过路径 | **不可绕过** |

> **要点**：绕过 L1/L2/L3 只能在本地绕过，推到远端后 L4 门禁仍然生效，依然会拦截 PR。

---

## 八、文件锁系统

### 8.1 为什么需要文件锁

多 Agent 并行修改同文件时，git merge 只能检测文本冲突，
但无法检测**语义冲突**（如两个迁移脚本同时新增不同版本号、同时改同一实体类的不同字段）。

### 8.2 锁机制设计

- **存储**：`.agent-locks/<task-slug>.yml`，per-task 文件，提交到 git
- **内容**：path、scope（file/directory）、owner、branch、expiresAt、reason
- **操作**：`acquire` / `release` / `check`（通过 `manage-agent-locks.mjs`）
- **强制**：`hot-paths.yml` 定义必须加锁的路径模式，pre-commit 和 CI 双重检查

### 8.3 热路径配置（hot-paths.yml）

```yaml
version: 1
hot_paths:
  - pattern: "backend/src/main/resources/db/migration-mysql/**"
    reason: "Flyway migration collisions corrupt schema history"
  - pattern: "backend/src/main/java/com/xiyu/bid/entity/**"
    reason: "Entity model changes have broad ripple effects"
  - pattern: "backend/src/main/resources/application*.yml"
    reason: "Config changes affect all environments"
  - pattern: "src/router/index.js"
    reason: "Single router file, high collision risk"
  - pattern: "src/views/Login.vue"
    reason: "Critical user entry point, historically overwritten"
  - pattern: ".github/workflows/**"
    reason: "CI changes affect entire team merge flow"
  - pattern: ".githooks/**"
    reason: "Hook changes affect local developer workflow"
  - pattern: "backend/src/main/resources/db/rollback/migration-mysql/**"
    reason: "Flyway rollback scripts collide same as forward migrations"
```

### 8.4 锁操作命令

```bash
# 获取锁
node scripts/manage-agent-locks.mjs acquire \
  --path src/views/Project/Detail.vue \
  --scope file \
  --reason "项目详情页改造 - 预计2天" \
  --days 3

# 释放锁
node scripts/manage-agent-locks.mjs release \
  --path src/views/Project/Detail.vue

# 检查锁状态
node scripts/check-agent-locks.mjs

# 冲突预检（开发前检查谁在改这个文件）
scripts/who-touches.sh src/views/Project/Detail.vue
```

---

## 九、关键脚本清单（需要移植的部分）

以下是实现多 Agent 协作所需的最小脚本集合，
按"必须移植"、"强烈推荐"、"可选增强"三级分类：

### 🔴 必须移植（无这些无法工作）

| 脚本 | 说明 | 依赖 |
|------|------|------|
| `scripts/agent-start-task.sh` | ⭐ 核心脚本 — 创建 worktree / in-place 分支 | git |
| `scripts/dev-env.sh` | **⭐ Agent 目录→端口/数据库/Redis 映射**（全 SOP 的物理基础） | 无 |
| `scripts/install-githooks.sh` | 启用 .githooks/ 目录 | git |
| `scripts/git` | 系统级 git 安全包装器（PATH 拦截 --no-verify） | 无 |
| `.githooks/pre-commit` | 提交前 15+ 项门禁 | git |
| `.githooks/pre-push` | 推送前保护分支 + 凭证检查 | git |
| `.githooks/git-push-wrapper.sh` | push 别名硬防线（过滤 `--no-verify`） | bash |
| `.githooks/git-commit-wrapper.sh` | commit 别名硬防线（过滤 `--no-verify`） | bash |
| `.github/workflows/branch-history-guard.yml` | 保护分支历史（5 行 YAML） | GitHub Actions |
| `AGENTS.md` | 项目导航地图（底线 + 按任务找信息） | 无 |
| `RULES.md` | 开发红线与四阶段流程 | 无 |
| `docker-compose.yml` | 数据库/Redis 通过 `${DB_NAME}` `${REDIS_DB}` 变量化 | Docker |

### 🟡 强烈推荐（大幅提升效率）

| 脚本 | 说明 | 依赖 |
|------|------|------|
| `scripts/pr-create.sh` | 统一 PR 创建（自动适配 GitHub/Gitee） | gh CLI / Gitee Token |
| `scripts/pre-push-gate.sh` | 推送前 14 道门禁 | 各子脚本 |
| `scripts/sync-env.sh` | 环境同步 + main-forward rebase | git + dev-env.sh |
| `scripts/agent-dev.sh` | 多 Agent 服务统一入口（`agent:morning/up/restart/status`） | dev-env.sh + launchd |
| `scripts/dev-services.sh` | 服务生命周期管理（start/stop/status/logs/healthcheck） | docker |
| `scripts/hot-paths.yml` | ⭐ 热路径定义（必须加锁的文件模式） | 无 |
| `scripts/check-hot-path-locks.mjs` | 热路径锁检查（pre-commit 和 CI） | Node.js |
| `scripts/manage-agent-locks.mjs` | 文件锁获取/释放 | Node.js |
| `scripts/check-agent-locks.mjs` | 文件锁检查 | Node.js |
| `scripts/who-touches.sh` | 冲突预检（谁在改这个文件） | git |
| `.agent-locks/` | 文件锁注册表目录 | 无 |

### 🟢 可选增强（锦上添花）

| 脚本 | 说明 | 依赖 |
|------|------|------|
| `scripts/check-line-budgets.mjs` | 行预算门禁（文件硬上限检查） | Node.js |
| `scripts/check-flyway-versions.sh` | 迁移版本号冲突 | git |
| `scripts/check-flyway-rollback.sh` | 回滚脚本完整性检查 | 无 |
| `scripts/next-migration-version.sh` | 迁移版本号自动生成 | 无 |
| `scripts/sweep-merged-branches.sh` | 已合入分支清理 | git |
| `scripts/agent-health-check.sh/mjs` | 全 worktree/全分支健康扫描 | Node.js |
| `scripts/ci-pre-pr.sh` | PR 前一站式门禁 | 所有子脚本 |
| `scripts/check-e2e-selectors.mjs` | E2E 选择器质量（防 fragile locator） | Node.js |
| `scripts/check-api-urls.mjs` | API URL 一致性检查 | Node.js |
| `scripts/line-budget.config.json` | 行预算配置 | 无 |
| `.github/workflows/flyway-migrate-dryrun.yml` | 迁移预演（真 MySQL container） | Docker |
| `.github/workflows/agent-locks.yml` | CI 锁检查 | GitHub Actions |
| `.github/workflows/agent-locks-janitor.yml` | 自动清理过期锁 | GitHub Actions |
| `.github/workflows/auto-enable-merge-on-approved.yml` | 自动 squash-merge | GitHub Actions |

---

## 十、新项目落地清单

### 第 1 天：基础设施

- [ ] 创建 Git 仓库，设为 private
- [ ] 设置 GitHub Branch Protection（**务必勾选 Enforce on admins**）
- [ ] **设计并固化 Agent 资源分配表**（端口/数据库/Redis DB，每个 Agent 一行）
- [ ] 编写 `scripts/dev-env.sh`（目录路径 → 端口/数据库/Redis 映射）
- [ ] 在 `docker-compose.yml` 中通过 `${DB_NAME}` `${REDIS_DB}` 变量化数据库和 Redis
- [ ] 复制 AGENTS.md 模板（导航地图 + 底线），按项目修改
- [ ] 复制 RULES.md 模板，按项目技术栈裁剪
- [ ] 配置 `.githooks/` + `scripts/install-githooks.sh`
- [ ] 配置 `scripts/git` 安全包装器

### 第 1 周：核心门禁

- [ ] 部署 `branch-history-guard.yml` CI workflow（5 行 YAML）
- [ ] 部署 `pre-push-gate.sh`（至少包含架构检查 + migration 检查）
- [ ] 配置 `hot-paths.yml`（先列 3-5 个真高冲突路径）
- [ ] 部署文件锁系统（`manage-agent-locks.mjs` + `check-agent-locks.mjs`）
- [ ] 创建首个 Agent 持久 worktree 做 dogfooding
- [ ] 验证 `npm run agent:up` 能按资源分配表正确拉起服务

### 第 2 周：流程完善

- [ ] 部署 `agent-start-task.sh` + 走通完整任务生命周期
- [ ] 配置 `pr-create.sh`（统一 PR 入口）
- [ ] 部署 migration dry-run CI（容器化）
- [ ] 编写 `lessons-learned.md` 开始记录事故
- [ ] 团队培训全部走一遍流程（早操 → 开任务 → 开发 → PR → 完成）

### 持续改进

- [ ] 每次事故后新增门禁或加固现有门禁
- [ ] `hot-paths.yml` 按实际冲突事故扩展
- [ ] line-budget 根据代码现状设定 ratchet 值
- [ ] 维护多 Agent 防御手册

---

## 十一、6 个失败模式（防坑指南）

| # | 模式 | 一句话症状 | 预防 |
|---|------|-----------|------|
| 1 | **Git 历史撕裂** | `git rev-list --max-parents=0` > 1 | Branch History Guard + Admin Enforcement |
| 2 | **迁移 SQL bug 进 main** | 启动失败、Flyway 报错 | Migration dry-run CI + pre-push 回滚覆盖检查 |
| 3 | **schema_history 污染** | 同一条 migration 多次 failed | 锁系统 + 版本号冲突预检 |
| 4 | **文件锁形同虚设** | 锁全部过期 + 没人续 | Janitor 清理 + CI 强制 check |
| 5 | **watchdog 死循环** | 服务持续重启、日志 GB 级 | 健康检查 bounded retry + 人工介入 |
| 6 | **跨分支静默退化** | 分支编译失败 24h | `agent:health-check` 全分支扫描 |

> **这些会互相放大**。项目踩到 2 个就开始几小时排障，3 个以上就进入 production-style 调查。

---

## 十二、危险命令红线

以下命令需要 **Issue + 两人签字 + 备份 tag** 才能执行：

```
git filter-repo / filter-branch
git checkout --orphan
git replace --graft / replace -d
git rebase --root
git push --force / --force-with-lease
git reset --hard 后接 push
通过 API PATCH /repos/.../git/refs
```

---

## 十三、技术栈无关的核心原则

本文档的脚本示例适用于 **Java + Spring Boot + Vue + MySQL** 项目。
如果你的项目使用其他技术栈，以下核心原则依然适用：

| 原项目实现 | 抽象原则 | 其他技术栈等价物 |
|-----------|---------|----------------|
| Maven + JPA + Flyway | 数据库迁移 + ORM | Alembic + SQLAlchemy / Prisma + Postgres |
| Spring Boot + Actuator | 后端健康检查 | `/health` endpoint in any framework |
| launchd + shell | 本地服务管理 | Docker Compose / systemd / PM2 |
| GitHub Actions | CI 门禁 | GitLab CI / Jenkins / CircleCI |
| ArchUnit | 架构测试 | 语言对应的架构测试框架 |
| JUnit + Vitest + Playwright | 分层测试 | 对应语言的测试框架 |
| .githooks | Git Hooks（通用） | 所有 git 仓库都支持 |
| scripts/git PATH 包装器 | 系统级拦截 | 所有 Unix shell 都支持 |
| .agent-locks/ | 文件锁（通用） | 纯文本 YAML，与语言无关 |
| dev-env.sh 路径匹配 | Agent 资源识别 | 任何基于目录路径的自动检测机制 |

---

## 附录 A：AGENTS.md 模板

```markdown
# AGENTS.md - 项目导航地图

> 本文件是 AI Agent 的入口地图。按当前任务去对应文件找详情。

## 不可妥协的底线

1. **真实 API 唯一源，禁止 Mock** → 详见 `SECURITY.md`
2. **复杂任务必走 Spec Kit 流程门禁** → 详见 `PLANS.md`
3. **严禁在 main 基准区修改代码** → 详见 `PLANS.md`
4. **FP-Java：纯核心可单测、不依赖框架** → 详见 `ARCHITECTURE.md`
5. **原子提交 + 测试证据** → 详见 `RELIABILITY.md`

## 按任务找信息

| 你在做什么 | 先读 |
|---|---|
| 写后端 | `ARCHITECTURE.md` |
| 写前端 | `FRONTEND.md` |
| 安全/权限 | `SECURITY.md` |
| 发起任务 | `PLANS.md` |
| 启动服务 | `CLAUDE.md` |
| 提交 PR | `RELIABILITY.md` |

## 协作暗号

- "早操SOP" → `git fetch origin && git rebase origin/main && bash scripts/sync-env.sh .`
- "开个任务 XX" → `scripts/agent-start-task.sh <agent> <XX> origin/main --in-place`

## Agent 与 Worktree 分配

| Agent | Worktree 路径 | 前端端口 | 后端端口 | 数据库名 | Redis DB |
|-------|-------------|---------|---------|---------|---------|
| main | `<project-root>/` | 1314 | 18080 | `project_main` | 0 |
| claude | `worktrees/claude/` | 1315 | 18081 | `project_claude` | 1 |
| codex | `worktrees/codex/` | 1316 | 18082 | `project_codex` | 2 |
| ... | ... | ... | ... | ... | ... |

## 协作语言与品牌

- **协作语言**：{语言}
- **项目品牌**：{品牌全称}
```

## 附录 B：RULES.md 模板

```markdown
# RULES.md — 项目强制红线与开发作业流程

## 1. 标准作业流程（SOP）

所有功能开发、Bug 修复、重构任务都必须按以下四阶段执行：

### Phase 1: Plan（规划）
### Phase 2: TDD（测试驱动）
### Phase 3: Code Review（代码审查）
### Phase 4: Refactor-Clean（重构与清理）

## 2. 核心业务逻辑架构约束

### 2.0 Split-First Rule
- **Application Service**：只做编排
- **Domain Policy / Rules**：只做业务规则
- **Mapper / Assembler**：只做转换
- **Repository / Gateway**：只做数据访问
- 文件软上限 {N} 行、硬上限 {M} 行

### 2.1 纯核心与命令式外壳
### 2.2 业务计算函数默认无副作用
### 2.3 业务错误作为普通值返回
### 2.4 核心逻辑默认不可变

## 3. 数据库迁移规范
## 4. 测试要求
```
