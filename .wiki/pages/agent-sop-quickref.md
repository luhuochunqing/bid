---
title: Agent 开发 SOP 快速参考
space: engineering
category: guide
tags: [SOP, 多Agent协作, lease协议, worktree, 开发流程]
sources:
  - CLAUDE.md
  - scripts/who-touches.sh
  - conductor/tracks/refactor_safety_governance_20260426/spec.md
backlinks:
  - _index
  - lessons-learned
created: 2026-04-26
updated: 2026-06-07
health_checked: 2026-06-21
---
# Agent 开发 SOP 快速参考

> 多 Agent 协作开发的「每次操作必备流程」速查表。
> 本文是浓缩骨架；实操细节在 `CLAUDE.md` §1-§6（参考章节都标了对应位置）。

---

## 一、每次进 worktree（早操，每个 session 一次）

```bash
cd /Users/user/xiyu/worktrees/<你-agent-名>
git fetch origin && git rebase origin/main && ./scripts/sync-env.sh .
```

**为什么**：worktree 基线对齐最新 main + 环境变量同步。**不跑早操就动代码 = 在过期 base 上累工作 = merge 假冲突。**

---

> **💡 快捷方式**：如果使用 `--in-place` 模式，`agent-start-task.sh` 会自动执行早操三连：
> ```bash
> scripts/agent-start-task.sh <agent> <task-slug> origin/main --in-place
> ```
> 脚本会在切分支前自动执行 `git fetch origin` → `git rebase origin/main` → `scripts/sync-env.sh .`，
> 相当于早操 + 开新任务一步到位。详见下节 §二。



## 暗号速查（对 Agent 说）

| 你说 | Agent 执行 | 等价于 |
|------|-----------|--------|
| "早操SOP" | `git fetch origin && git rebase origin/main && ./scripts/sync-env.sh .` | 早操三连 |
| "开个分支 XX" | `scripts/agent-start-task.sh <agent> <XX> origin/main --in-place` | 早操 + 切开发分支 |
| "早操SOP + 开个分支 XX" | `scripts/agent-start-task.sh <agent> <XX> origin/main --in-place` | 同上，一次完成 |

注意：Agent 通过 `AGENTS.md` §协作暗号 推导自己的 `<agent>` 名称。

---

## 二、每次开新任务前（每个任务起点都要跑）

### 2.1 同步基线（CLAUDE.md §5.0）
```bash
git fetch origin && git rebase origin/main
```
**为什么**：早操只覆盖 session 开头。任务之间也得 sync — 否则下一步 `who-touches.sh` 看到的是旧 main 的 diff，漏掉别的 agent 中途合的改动。

> 有 uncommitted 工作要 sync？先 `git stash`，rebase 后再 `git stash pop`。

### 2.2 Lease 检查（CLAUDE.md §5.1）
```bash
./scripts/who-touches.sh <你打算改的路径>
```
- **退出 0 + 无输出** → 干净，开工
- **退出 1 + 有输出** → 别的 agent 在动这块。处置：等他 PR 合 / 换任务 / PR 描述 @ 协调
- **撞到 `agent/gemini-init`** → 多查一条看任务上下文（CLAUDE.md §5.2）：
  ```bash
  grep -h "\[~\]" conductor/tracks/*/plan.md | grep gemini
  ```

> Claude / Codex / Cursor **没有等价的任务声明机制**，撞到时直接看 `git log <branch>` commit message + PR 描述协调。

---

## 三、写代码时

- **新文件加项目标头**（项目约定）：
  ```
  // Input: 依赖的输入
  // Output: 提供的功能
  // Pos: 所属位置
  // 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
  ```
- **commit 格式**：`<type>(<scope>): <desc>`，type ∈ feat/fix/refactor/docs/test/chore
- **Flyway 迁移**：新迁移文件统一使用 `V_next__{表名}_{动作}.sql` 命名，pre-commit 自动分配版本号。
  必须同时创建对应的回滚文件 `U_next__{表名}_{动作}.sql`。
  **表名必须写在文件名中**（如 `project_add_status`），pre-push 会自动检测多个分支是否改了同一张表。
  示例：
  ```bash
  # 创建迁移文件（自动编号，不需要自己写版本号）
  cat > backend/src/main/resources/db/migration-mysql/V_next__add_xxx_table.sql << 'SQL'
  -- 版本号在 commit 时自动分配
  ALTER TABLE xxx ADD COLUMN yyy VARCHAR(255);
  SQL
  
  cat > backend/src/main/resources/db/rollback/migration-mysql/U_next__add_xxx_table.sql << 'SQL'
  ALTER TABLE xxx DROP COLUMN IF EXISTS yyy;
  SQL
  ```
  **绝对不要手动编号**，否则 push 时 pre-push 门禁会拒绝。

- **Schema 语义冲突检测**（pre-push 自动运行）：
  检测多个分支是否同时改动同一张表的迁移文件。
  如果检测到冲突，会输出警告并列出相关分支和文件名。
  此时需在 PR 描述中注明冲突的表，并与其他分支作者协调合并顺序。

- **半成品**：用 `wip:` 前缀允许提交

---

## 四、每次 session 结束前必做（CLAUDE.md §6）

```bash
git push origin HEAD:$(git rev-parse --abbrev-ref HEAD)
```

**为什么**：本地 commit 不算数，别的 agent 看不到 = lease 协议失效。即使 WIP、即使没 PR 也要推。

> 对 Claude / Codex / Cursor：commit message 写明白 scope，例如 `wip: add CONTRACT profile (scope: docinsight/contract*)` — 别人从 `who-touches.sh` 输出 + `git log <branch> --oneline -5` 就能推断你在干什么。

---

## 五、报告任务完成前（CLAUDE.md §4）

```bash
npm run build               # 前端构建验证
cd backend && mvn test      # 后端测试验证
cd ..
git status                  # 确认只动了授权文件
```

---

## 六、合并到 main

```bash
gh pr create --base main --head agent/<你> --fill
```
浏览器打开链接 → 看到绿条 "Able to merge" → 点 **Rebase and merge** → confirm。

GitHub 会自动做冲突检查；有冲突时 merge 按钮置灰、显示 "This branch has conflicts that must be resolved"。

---

## 七、合完后同步本地分支

```bash
git fetch origin && git reset --hard origin/main && git push --force-with-lease origin agent/<你>
```

让本地 + 远端 agent 分支 SHA 跟 main 对齐，避免下次 rebase 残影。`--force-with-lease` 比 `--force` 安全（如果别人在你不知情时往 agent 分支推过东西，命令拒绝执行而不是覆盖）。

---

## 三条铁律（最容易翻车的地方）

1. **没跑早操不动代码** — 在过期 base 上累工作，merge 时一堆假冲突
2. **没跑任务边界 sync + `who-touches.sh` 不开新任务** — 跟别人改同一文件，merge 时一堆真冲突
3. **session 结束前必 push** — 你的活在别人眼里不存在，整个 lease 协议失效

---

## 阶段速查表

| 时机 | 命令 | 目的 |
|------|------|------|
| 进 worktree | 早操三连 | 基线对齐 + 环境同步 |
| **开新任务** | `git fetch && git rebase origin/main` | **任务边界再 sync** |
| 任务起点 lease check | `./scripts/who-touches.sh <path>` | 看撞没撞 |
| Session 结束 | `git push origin HEAD:$(git rev-parse --abbrev-ref HEAD)` | 让别人看见你 |
| 报完成 | `npm build + mvn test + git status` | 自证质量 |
| 合 main | `gh pr create --fill` → Rebase merge | 落地 |
| 合完 | `git reset --hard + git push --force-with-lease` | 同步残影 |

---

## 同步频率 mental model

> Sync **不是"每天一次的事"**，是 **"开始做新工作之前的最后一道清醒动作"**。

任务粒度切得越细、push 越频繁，sync 就越廉价、conflicts 就越早暴露。多 commit、多 push、任务之间多 sync — 比"一天写到晚最后 merge"安全得多。

---

## 参考链接

- 实操细则：`CLAUDE.md` §1（快速进入）/ §4（完成门禁）/ §5（任务启动协议）/ §6（push 纪律）
- 协议设计动机 / 历史轨迹：`conductor/tracks/refactor_safety_governance_20260426/spec.md`
- 工具自检：`./scripts/who-touches.sh --self-test` 应见 4 个 `[PASS]`
