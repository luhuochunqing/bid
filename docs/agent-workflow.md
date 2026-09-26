<!-- 一旦我所属的文件夹有所变化，请更新我。 -->

# 多 Agent 协作流程 (agent-workflow)

> 本文件由根 `CLAUDE.md` 的「多 Agent 执行手册」章节迁移而来（2026-09-26），按需查阅，不再常驻每个 session 的上下文。
> 本地 Claude Code 可通过 skill `agent-workflow`（`.claude/skills/agent-workflow/`）自动路由到本文件。
>
> **常驻约束不在此处**：Git 安全规则（禁止 `--no-verify`）、任务完成门禁、早操核心指令仍保留在根 `CLAUDE.md`，始终生效。

## 1. 任务启动协议 (Lease + Auto-Detect)

不画静态目录所有权表 — 任务推进就过时。改用 **git 事实** 当主信号，文件锁仅用于 hot-paths 前置预订。

### 1.0 同步基线（**每次开新任务都要跑**，不只是 session 开头）

"早操"只覆盖 session 开头。**任务之间也必须重新同步** — 否则 1.1 的 `who-touches.sh` 看到的是旧 main 的 diff，可能漏掉别的 agent 中途合的改动，你照样会在过期 base 上累工作。

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

### 1.1 主信号：git 事实（**所有 agent 通用，必跑**）

开新任务前对你打算改的路径跑：
```bash
./scripts/who-touches.sh <path-or-glob>
```
列出有未合 commit 的 `agent/*` 分支（origin + local 去重，跳过自己）。
- 退出码 `0` + 无输出 → 干净，可以开工
- 退出码 `1` + 有输出 → 别的 agent 在动这块，看清楚再决定

### 1.2 文件锁（hot-paths 前置预订）— 已改为 per-task 文件

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

### 1.3 Gemini 任务声明（**仅当撞到 `agent/gemini-init` 时有用**）

Gemini 在 `conductor/tracks/` 系统里执行任务，会把 in-progress 任务标 `[~]` 并附 `(@gemini, scope: ...)`。如果 1.1 显示 `agent/gemini-init` 在你的目标路径有未合改动，可以查一眼具体任务上下文：
```bash
grep -h "\[~\]" conductor/tracks/*/plan.md | grep gemini
```

> **重要**：Claude / Codex / Cursor **没有等价的任务声明机制**。撞到这几个 agent 的分支时，**不要假设可以从 plan.md 查到他们的意图** — 直接看 `git log <branch>` 的 commit message，或在 PR 描述里 @ 对方协调。其他 agent 的"意图声明"靠 §2 的 commit message + push 频率自然沉淀。

### 1.4 撞了的处置

- 等对方 push 完一个原子 commit / PR merge / Gemini 任务标 `[x]`
- 换一个不撞的任务先做
- 在 PR 描述里 @ 对方说明协调结果（"我接着你的 X 改 Y，rebase 你的 PR 后再合"）

### 1.5 没撞 → 开工

- 你是 Gemini：在 plan.md 把任务从 `[ ]` 改成 `[~] (@gemini, scope: ...)` 让别人看见你的意图
- 你是其他 agent：**push commit 时就是你的意图声明** — commit message 写清楚 scope

> 验证脚本：`./scripts/who-touches.sh --self-test` 应该看到 4 个 `[PASS]`。

## 2. 纪律：每日 push WIP 分支

`who-touches.sh` 是所有 agent 共用的协调机制，准确性靠每天至少 push 一次自己 `agent/*` 分支：
- **本地 commit 不算数** — 别的 agent / session 看不到，lease 检测会漏。
- 每个工作 session 结束前推一次（即使没开 PR、即使是半成品）：
  ```bash
  git push origin HEAD:$(git rev-parse --abbrev-ref HEAD)
  ```
- push 是给 lease 检测看的，**不是为了 merge** — 半成品 WIP 分支允许、欢迎、必须存在。

> 对非 Gemini 的 agent 而言，**commit message + 改动文件就是你的"我在做这事"声明**。所以 commit message 要明确，例如 `wip: add CONTRACT profile (scope: docinsight/contract*)`，即使没 PR 别人也能从 `who-touches.sh` 输出 + `git log <branch> --oneline -5` 推断你在干什么。

## 3. 分支策略：默认分支 + 任务分支

### 分支分类

| 分支类型 | 示例 | 用途 | 可否直接 commit |
|---|---|---|---|
| 基线分支 | `main` | 唯一真值，所有任务分支的最终合入点 | 否（受 Gitee 分支保护） |
| Bootstrap 种子分支 | `agent/codex-init`, `agent/cursor-init` 等 | 仅承载初始化配置（环境文件、钩子安装） | 否（`agent-worktree-guard.sh` 禁止） |
| 任务分支 | `agent/codex/project-task-breakdown-from-tender` | 每个原子任务一个独立分支 | 是 |

### Bootstrap 分支（`*-init`）的定位

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

### 任务分支的生命周期

默认（不加 `--in-place`）从主仓库拉一个**独立临时 worktree**（纯源码，不装依赖），收尾自动删除：

```bash
# 默认：从主仓库拉独立临时 worktree（纯源码，收尾自动删）
./scripts/agent-start-task.sh codex project-task-breakdown-from-tender origin/main
# -> worktree: /Users/user/xiyu/worktrees/xiyu-codex-project-task-breakdown-from-tender（临时）
# → branch:   agent/codex/project-task-breakdown-from-tender
```

旧持久 worktree 内联模式仍可用，只需显式加 `--in-place`：

```bash
./scripts/agent-start-task.sh codex quick-fix origin/main --in-place
```

**生命周期规则：**
1. 任务分支默认在独立临时 worktree 中开发，完成后执行收尾（`scripts/agent-finish-task.sh`）自动删除临时 worktree + 分支
2. 任务分支 PR 合入 `main` 后，该分支**视为终结**
3. 下次新任务 → 再拉起一个新的独立临时 worktree
4. **不要在 `*-init` bootstrap 分支上做任务开发**

这样做的好处：
- 每个任务默认隔离，互不污染，用完即删，避免 worktree 长期膨胀
- 简化资源管理（临时 worktree 纯源码不装依赖）
- 每个分支天然是 `origin/main` 的最新衍伸，不会积累陈旧

### 早操 main-forward 的工作原理

`sync-env.sh` 在 `agent/*/*` 任务分支上自动执行：

```
sync-env.sh .
  1. env 文件同步（.env.example 等）
  2. main-forward rebase：
     git stash (如有未提交变更)
     git fetch origin main --prune
     git rebase origin/main
     git stash pop (rebase 成功后)
```

保护分支（`main`、`agent/*-init` 等）跳过 rebase。
