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

### 单一 Worktree 策略（--in-place 模式）

每个 Agent 只有一个持久 worktree（如 `agent/codex-init`），**所有任务都在持久 worktree 内切分支完成**，不再创建独立的临时 worktree。

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

> **⚠️ 已废弃二级临时 worktree**：自 2026-06-22 起，不再允许创建独立的临时 worktree。
> 所有任务必须在持久 worktree 内以 `--in-place` 模式完成。这样可以避免 worktree 膨胀、简化资源管理。

### 通用原则

- **物理隔离**：各 Agent 在 `/Users/user/xiyu/worktrees/` 下的独立持久 Worktree 工作，严禁在 `main` 基准区修改代码。
- **资源统一**：**开发环境统一到主工作区（trae）**，前端 1323 / 后端 18089 / Sidecar 8009 / 数据库 xiyu_bid_main / Redis DB 0。其他 worktree 不再分配独立端口和数据库，仅用于代码编辑和 git 操作。
- **验证责任**：遵循"谁改代码，谁在自己的 Worktree 跑通验证"原则。报告"任务完成"前，必须提供在 Worktree 内部执行 `npm run build` 和 `mvn test` 的成功证据。
- **健康诊断**：`npm run agent:health-check` — 跨 worktree 聚合展示 sidecar/backend/frontend 健康状态。
- **分支命名**：
  - **Worktree 锚点分支**（agent/<name>-init）：各 Agent worktree 的常驻基线分支。**严禁直接在此分支上开发**（CI 门禁会拦截），**严禁删除**（本地或远端均不可删）。仅用于 worktree 锚定和多 Agent 间同步基线。
  - **任务开发分支**（`agent/<name>/<task>` 等前缀）：每个原子任务一个独立分支。PR 合入后由 CI 自动清理删除远端分支，本地分支需手动 `git branch -D`。

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
