# 实施计划：多 Agent 工程化 SOP 基础设施初始化

## 目标
实施多 Agent 工程化协作 SOP，通过设置 Git worktrees 和必要的自动化脚本，确保不同的 AI Agent (Claude, Codex, Gemini, Cursor) 和集成区拥有物理隔离、互不冲突的开发环境。

## 关键上下文与约束
* **Worktree 位置:** 项目外部的同级目录 `/Users/user/xiyu/worktrees/`。主目录是 `/Users/user/xiyu/xiyu-bid-poc/`。
* **包管理器:** 项目将继续使用 `npm`，以最小化破坏。
* **环境变量:** 需要在各 worktree 之间同步的主要环境文件是 `.env.api`。
* **Git 状态:** 在创建 worktree 之前，确保主目录在 `dev` 分支上且工作区是干净的。

## 实施步骤

### 阶段 1: 物理隔离 (创建 Worktrees)
1. 确保当前主目录 (`/Users/user/xiyu/xiyu-bid-poc/`) 在 `dev` 分支上且已更新到最新。
2. 基于 `dev` 分支，在外部目录 `/Users/user/xiyu/worktrees/` 下创建各个 Agent 的 Git worktrees：
   - `git worktree add ../worktrees/claude -b agent/claude-init origin/dev`
   - `git worktree add ../worktrees/codex -b agent/codex-init origin/dev`
   - `git worktree add ../worktrees/gemini -b agent/gemini-init origin/dev`
   - `git worktree add ../worktrees/cursor -b agent/cursor-init origin/dev`
   - `git worktree add ../worktrees/integrator dev`
3. 验证主项目的 `.gitignore` 是否需要处理此外部路径 (由于是在外部，通常不需要，除非该路径在项目子目录下)。

### 阶段 2: 自动化脚本设置
在 `scripts/` 目录下创建所需的自动化脚本，用于管理跨 worktree 的环境变量和端口。
1. **`scripts/sync-env.sh`**:
   - 创建一个脚本，将 `.env.api` 从项目根目录复制到目标 worktree。
2. **`scripts/dev-env.sh`**:
   - 创建一个脚本，自动检测当前工作目录名称，并动态导出不同的端口号 (`FRONTEND_PORT`, `BACKEND_PORT`)、数据库名 (`DB_NAME`) 和 `REDIS_DB`，确保资源隔离。
3. **`scripts/start-frontend.sh` & `scripts/start-backend.sh`**:
   - 创建包装脚本，在内部 source `dev-env.sh` 并使用分配好的端口和配置来启动各自的服务。
   - *注意 npm:* 因为项目使用 `npm`，前端启动脚本应使用 `npm run dev -- --port "$FRONTEND_PORT"` 而不是 `pnpm`。

### 阶段 3: 应用配置感知 (Configuration Update)
更新应用的本地配置文件，让它们能够读取并使用动态分配的环境变量 (`FRONTEND_PORT`, `BACKEND_PORT`, `DB_NAME`, `REDIS_DB`)。
1. **前端:** 确保 Vite (`vite.config.js`) 能够读取 CLI 传入的端口或环境变量。
2. **后端:** 更新本地的 Spring Boot 配置 (如 `backend/src/main/resources/application-local.yml` 等)，让其读取 `${SERVER_PORT}`, `${DB_NAME}`, 和 `${REDIS_DB}`。

### 阶段 4: 验证与集成
1. 为 `scripts/` 目录下的所有新脚本赋予执行权限 (`chmod +x`)。
2. 将这些新脚本和配置更改提交到主目录的 `dev` 分支中 (或者通过集成 PR，如果直接 push 被限制)，以便所有的 worktree 都能继承这些脚本。
3. 测试这套流程：进入某个 worktree (例如 `../worktrees/gemini`)，运行同步脚本，并使用包装脚本启动服务，确认端口和资源已被正确隔离。

## 验证与测试
- 运行 `git worktree list`，显示所有 5 个新创建的 worktree，并且它们关联到了正确的分支。
- 执行 `scripts/sync-env.sh ../worktrees/gemini` 成功复制 `.env.api`。
- 在某个 worktree 目录内部执行 `source scripts/dev-env.sh`，能够正确输出为该 Agent 分配的独立端口和 DB 配置。

## 回滚策略
如果 worktree 创建失败或导致了无法管理的状态：
1. 针对所有创建的 worktree 运行 `git worktree remove`。
2. 删除 `/Users/user/xiyu/worktrees/` 目录。
3. 删除 `scripts/` 目录下新添加的自动化脚本。