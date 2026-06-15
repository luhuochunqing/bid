# PLANS.md — 计划管理指引

复杂任务的执行计划是代码库的"一等公民"，不要只在聊天窗口里管理。

## Spec Kit 流程门禁（强制）

**当收到包含 `Phase`、`开发计划`、`需求开发` 或类似复杂任务时，必须按以下顺序执行：**

1. 使用 `speckit-specify` skill 创建/更新规格文档
2. 使用 `speckit-plan` skill 生成/更新实现计划
3. 使用 `speckit-tasks` skill 生成任务清单
4. **在编码前必须完成上述三个步骤**
5. 编码完成后使用 `speckit-analyze` skill 验证一致性

> **违规说明**：跳过流程直接编码的提交将被 CI 门禁拒绝。

## 变更原则

- **禁止 Mock**：严禁在 `src/mock` 或任何非 API 路径下编写代码。详见 `SECURITY.md §Mock 政策`。
- **JPA 优先**：后端存储必须通过 JPA 实体映射到 MySQL，禁止使用内存 Map 模拟。
- **原子提交**：每次提交应包含功能实现、对应的 `Flyway` 迁移脚本（如涉及库表）、以及至少一个验证成功的测试用例证据。

## 多 Agent 协作（Worktree）

### 两级 Worktree 策略

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

### 通用原则

- **物理隔离**：各 Agent 在 `/Users/user/xiyu/worktrees/` 下的独立持久 Worktree 工作，严禁在 `main` 基准区修改代码。
- **资源分配**：每个 Agent 拥有固定的专属端口（前端 131x / 后端 1808x）和数据库名。持久 worktree 内切分支不改变资源分配。
- **验证责任**：遵循"谁改代码，谁在自己的 Worktree 跑通验证"原则。报告"任务完成"前，必须提供在 Worktree 内部执行 `npm run build` 和 `mvn test` 的成功证据。
- **协作启动命令**：多 Agent 本地联调优先使用 `npm run agent:up` / `agent:restart` / `agent:status` / `agent:logs` / `agent:stop`；`npm run agent:morning` 等价于早操SOP（sync + 重启）；脚本会按当前 worktree 自动映射端口、数据库、Redis DB、sidecar 端口和 launchd label，启动类命令同样需要 `XIYU_DEV_CONFIRMED=1`。
- **健康诊断**：`npm run agent:health-check` — 跨 worktree 聚合展示 sidecar/backend/frontend 健康状态。
- **分支命名**：
  - **Worktree 锚点分支**（agent/<name>-init）：各 Agent worktree 的常驻基线分支。**严禁直接在此分支上开发**（CI 门禁会拦截），**严禁删除**（本地或远端均不可删）。仅用于 worktree 锚定和多 Agent 间同步基线。
  - **任务开发分支**（`agent/<name>/<task>` 等前缀）：每个原子任务一个独立分支。PR 合入后由 CI 自动清理删除远端分支，本地分支需手动 `git branch -D`。
  - 分支命名规范详见 `.wiki/pages/branch-naming.md`。

## 落计划约定

- **小型修改**：轻量临时计划，不必建文件。
- **复杂任务**：在 `docs/exec-plans/active/` 建 `<task-slug>-plan.md`（目标 + 进度 + 决策日志），完成后移入 `completed/` 或归并到 `docs/archives/`。
- **技术债**：登记到 `docs/exec-plans/tech-debt-tracker.md`。

## 参考文档索引

| 概念 | 位置 |
|---|---|
| **执行计划（active/completed/技术债）** | `docs/exec-plans/`（新增落点） |
| 活跃开发计划（既有） | `docs/plans/` |
| 历史计划归档（既有） | `docs/archives/plans-2026-{03,04,05}/` |
| 任务编排 tracks | `conductor/tracks/`、`conductor/tracks.md` |
| 任务看板 | `docs/TODO.md`、`docs/release/CHANGELOG.md` |
| 实施计划书 | `docs/specs/西域数智化投标管理平台-实施计划书-7月10日上线版.md` |
