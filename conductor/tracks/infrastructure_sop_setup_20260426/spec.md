# 需求规格说明书: SOP 基础设施初始化

## 1. 概述
本规格说明书详细描述了多 Agent 工程化协作 SOP 的初始化设置。核心目标是当多个 AI Agent (Claude, Codex, Gemini, Cursor) 同时在 `xiyu-bid-poc` 代码库上进行操作时，消除状态冲突、端口冲突以及环境变量污染。

## 2. 需求

### 2.1 Git Worktree 隔离
* **需求:** 各 Agent 严禁共享同一个物理目录。
* **实现:** 使用 `git worktree` 在主项目外部的 `/Users/user/xiyu/worktrees/` 目录下创建隔离的开发环境。主目录是 `/Users/user/xiyu/xiyu-bid-poc/`。
* **目录结构:**
  - `/Users/user/xiyu/worktrees/claude`
  - `/Users/user/xiyu/worktrees/codex`
  - `/Users/user/xiyu/worktrees/gemini`
  - `/Users/user/xiyu/worktrees/cursor`
  - `/Users/user/xiyu/worktrees/integrator`

### 2.2 环境变量同步
* **需求:** 新创建的 worktree 缺少未提交的 `.env` 文件，这会导致本地服务启动失败。
* **实现:** 提供一个 Shell 脚本 `scripts/sync-env.sh`，必须能够将 `.env.api` 复制到指定的 worktree 目录中。

### 2.3 自动分配端口与资源
* **需求:** 必须防止多个 Agent 同时启动服务时发生端口冲突。
* **实现:** 提供一个 Shell 脚本 `scripts/dev-env.sh`，它需要根据当前所在的目录名称，动态分配端口和数据库名。
* **端口映射表:**
  - `main` (默认回退): 前端 5173 / 后端 8080 / DB `my_project_main` / Redis 0
  - `claude`: 前端 5174 / 后端 8081 / DB `my_project_claude` / Redis 1
  - `codex`: 前端 5175 / 后端 8082 / DB `my_project_codex` / Redis 2
  - `gemini`: 前端 5176 / 后端 8083 / DB `my_project_gemini` / Redis 3
  - `cursor`: 前端 5177 / 后端 8084 / DB `my_project_cursor` / Redis 4
  - `integrator`: 前端 5178 / 后端 8085 / DB `my_project_integrator` / Redis 5

### 2.4 服务启动包装器
* **需求:** 各个 Agent 应该使用标准命令来启动服务，而不需要去手动记端口或指定端口。
* **实现:** 
  - `scripts/start-frontend.sh`
  - `scripts/start-backend.sh`
  这些脚本在内部会 `source dev-env.sh`，并将分配好的端口自动传递给 `npm` 和 Maven。

### 2.5 包管理器一致性
* **需求:** 维持当前项目的依赖管理体系。
* **实现:** 继续使用 `npm` (由 `package-lock.json` 决定)，不强行切换到 pnpm。

## 3. 不在范围内 (Out of Scope)
- 不包含将项目从 `npm` 迁移到 `pnpm` 的工作。
- 不包含通过 Docker 容器来实现数据库的绝对隔离 (目前我们依赖动态的数据库命名约定来进行逻辑隔离)。
- 不包含修复与环境初始化无关的应用本身存在的历史 Bug。