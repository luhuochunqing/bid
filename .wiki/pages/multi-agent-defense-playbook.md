---
title: 多 Agent 并行开发防御工程化手册
space: engineering
category: guide
tags: [工程化护栏, 多Agent并行, Git历史, CI, 文件锁, 自愈, 可移植]
sources:
  - .wiki/pages/lessons-learned.md
  - RULES.md
  - CLAUDE.md
  - .github/workflows/branch-history-guard.yml
  - .github/workflows/flyway-migrate-dryrun.yml
  - .github/workflows/agent-locks-janitor.yml
  - scripts/check-agent-locks.mjs
  - scripts/agent-locks-prune.mjs
  - scripts/agent-health-check.mjs
  - scripts/dev-services.sh
backlinks:
  - _index
created: 2026-05-12
updated: 2026-06-20
health_checked: 2026-06-27
---
# 多 Agent 并行开发防御工程化手册

> 本页是把本项目踩过的坑提炼成**可移植到任何项目**的工程化清单。`lessons-learned.md` 记录本项目时间线和事故复盘，本页着重"换一个项目还能用"的部分。

---

## 一、6 个失败模式（先认得它们）

| # | 模式 | 一句话症状 |
|---|---|---|
| 1 | **Git 历史撕裂** | `git rev-list --max-parents=0` 多于 1 个 root commit；老 PR 的内容"莫名消失" |
| 2 | **迁移 SQL bug 进 main** | 启动失败、Flyway/Liquibase 报错；但 CI 之前是绿的 |
| 3 | **schema_history 污染** | 同一条 migration 在历史表里出现多次 failed 记录 |
| 4 | **文件锁形同虚设** | 锁机制存在，但全部过期 + 没人续，CI 不强制 |
| 5 | **watchdog 死循环** | 服务持续失败、守护进程每 5s 重启一次，日志 GB 级、风扇飞转 |
| 6 | **跨分支静默退化** | 分支编译失败 24 小时，没人知道 |

**最重要的事**：这 6 个**会互相放大**。一个项目踩到 2 个就开始几小时排障，3 个以上就进入 production-style 调查。本项目的根因排查会贯穿 5 个全部命中（见 [[lessons-learned]]）。

---

## 二、4 条设计原则

### 1. 物理上不可能 > 规则上禁止
不要靠"约定俗成"，要靠 **branch protection + admin enforcement**。"团队大了再加"是不开始的借口——单人项目也立。

### 2. Ratchet 模式
任何护栏的阈值"只能更严，不能更松"。比如 root commit 数 ratchet = 当前值，发现退化立即 CI 红。给数字一个具体值，而不是写"应该 ≤ 1"。

### 3. 可见性即治理
不能让坏状态藏在某个 worktree 的本地日志里。**一个命令**能扫描全机器 / 全 worktree / 全分支状态。本项目的 `npm run agent:health-check` 是范例：第一次跑就暴露了 Gemini worktree 24h 编译失败。

### 4. 自愈优先于人为兜底
任何"需要人去清理"的状态都会积累成债务。Janitor 跑 cron + 自动开 PR + auto-merge = 0 人介入。本项目最后 4 个 PR 全在踩"忘了写 janitor 自愈机制"的坑。

---

## 三、新项目第一周该立的 10 个护栏（按依赖顺序）

### 1. GitHub Branch Protection on main

- ✅ Require PR before merging
- ✅ Require linear history
- ✅ Disallow force pushes
- ✅ Disallow deletions
- ✅ **Enforce on admins**（别忘这一勾，否则前面全是装样子）
- ✅ Required status checks（一个 CI workflow 必过的列表，至少包含下面 #2）

### 2. Branch History Guard CI workflow

5 行 YAML，检查 `git rev-list --max-parents=0 HEAD | wc -l` ≤ N。Ratchet 模式：现状是几就锁几，超过即 CI 红。

### 3. Pre-push hook + install-githooks.sh

本地镜像 #2 + 拒绝非 fast-forward push 到保护分支。带 `FORCE_PUSH_OK=1` 一次性逃生口（用于授权过的历史手术）。

### 4. RULES.md 危险命令红线

列出 7 条需要双人签字才能执行的命令：

```
git filter-repo / filter-branch
git checkout --orphan
git replace --graft / replace -d
git rebase --root
git push --force / --force-with-lease
git reset --hard 后接 push
通过 API PATCH /repos/.../git/refs
```

每条要求 issue + 两人签字 + 备份 tag。

### 5. Migration dry-run CI

起一个真 MySQL/Postgres container，从 baseline 跑 `flyway migrate` 到 HEAD。任何迁移 PR 都跑。本项目的 V117 ENUM bug 在 PR 阶段就会被这一项拦截。

### 6. 文件锁系统（多 agent / 多人改 hot-path）

- `hot-paths.yml`：白名单，宁少勿多（migration 目录、entity 模型、router、config、CI workflows、git hooks）
- CI 强制：改 hot-path 必须有 active lock
- 续期命令（`npm run agent:lock-renew`）
- **Janitor 自动清孤儿锁**（cron 每天跑，且 prune 脚本要能识别"分支已死"= 自动 stale）

### 7. Service 失败退避

watchdog/守护进程一定要带：
- exponential backoff（30s → 2min → 10min → 30min cap）
- max-failure 停止（如 10 次后写 fail-state 文件）
- `start` 命令在 fail-state 存在时拒绝启动，强制操作者先 review 原因

### 8. 跨工作区健康度

一个命令扫所有 worktree / 所有服务，输出 ALIVE/DEAD + 最近 ERROR 行。本项目 `npm run agent:health-check` 范例。

### 9. .wiki/lessons-learned.md

每次大事故后，把"失败模式 / 时间线 / 防御 / checklist"写下来。下一个新人入职第一周有得读。

### 10. 每天定时演练

任何防御都至少手动 dispatch 一次验证，cron 启动后再观察一周。本项目证明 janitor "看起来配好了" 但 YAML 解析错误 → 5 次伪跑 0s 失败没人察觉。

---

## 四、最值钱的几个具体陷阱

这些是 GitHub 文档里查不到、踩过才知道的：

### 1. `enforce_admins: false` 是默认的
GitHub UI 上 "Do not allow bypassing" 那个 checkbox 不勾，你 admin 能力随时绕过所有防御。

### 2. Required status checks 列表为空 = 形同虚设
勾了 "Require status checks" 但 `contexts: []`，所有 CI 红的 PR 仍然能合。要手动一个个搜出来加进去。

### 3. Required approvals: 1 + 单人开发 = 死锁
因为 PR 作者不能 approve 自己。要么找另一个账号，要么 approval count 设为 0（保留 require PR 即可）。

### 4. GitHub Workflow file `name:` 解析失败时不会报错
它会用文件路径作为 `name` 显示，然后 `workflow_dispatch` 等 trigger 也悄悄失效。

检测方法：

```
gh api repos/<owner>/<repo>/actions/workflows/<id>
```

看 `name` 字段是不是文件路径。

### 5. HEREDOC 多行字符串在 YAML 里要严格对齐缩进

```yaml
run: |
  git commit -m "title

body line not indented"     # ← 这一行顶格会破坏整个 YAML 结构
```

改用 `-m "title" -m "body"` 多次传 -m，避免续行缩进陷阱。

### 6. Workflow 直推 main 会被 branch protection 拒
自动化 workflow（如 janitor）必须改成开 PR + auto-merge，不能直推。错误信号：

```
remote: error: GH006: Protected branch update failed for refs/heads/main.
```

### 7. Squash merge 会保留 self-lock 在 main（三层防御）
PR 用 self-lock 通过 hot-path 检查，合并后那个锁会跟随到 main，挡死下一个 PR。除非 janitor 检测"分支已死 = 自动 stale"，否则会形成无限 PR 链。本项目踩过两次：

- 第一波：#239 / #242 / #244 / #245（janitor 不会自愈 + 直推被 branch protection 拒）
- 第二波（2026-05-14）：lock 系统改成 **per-task 文件**后，prune 脚本仍只扫旧的单文件 `.agent-locks.yml`，新格式 `.agent-locks/<task>.yml` 完全不在它视野内。一个 PR self-lock 又躺尸 2 天才被发现。

**根因不是单一 bug，是结构性 gap**。每一层都能漏，需要三层独立兜底：

| 层 | 触发 | 作用 | 文件 |
|---|---|---|---|
| L1：cron + auto-release | 每天 02:17 UTC + main push 命中 `.agent-locks/**` 即触发 | 兜底 + 主动闭环；prune 同时扫 legacy 单文件和 per-task 目录，per-task 文件清空时直接 unlink | `.github/workflows/agent-locks-janitor.yml`、`scripts/agent-locks-prune.mjs` |
| L2：CI 阻断（`findSelfMergeOrphans`） | PR base=main 且 diff 新增的 lock 的 `branch:` == PR head ref | merge 前就报错，强制作者 `npm run agent:lock-release --all` | `scripts/check-agent-locks.mjs` |
| L3：白名单 | `chore/janitor-*` / `chore/clean-orphan-lock-*` / `chore/auto-release-*` | janitor / 清理 PR 自己豁免 L2，避免循环 | 同上 |

**关键设计决策**：
- L1 复用一段 prune 逻辑、两个触发点（cron + push: main）。一段代码，两个时机。
- L2 把窗口从"24h 滞后"压到"merge 前 0 分钟"，但允许的"逃跑路径"必须留——janitor 自己的 PR 必然有 lock 文件改动，要白名单。
- per-task 文件意味着两个 agent 同时改自己的 task 锁不再撞 yaml 行号冲突——这是 #243 的初衷，但当时漏了 prune 配套。任何"按需重构存储格式"的动作都要同步更新所有读这个格式的工具，**否则就是 R1 这种"改了一半"的 trap**。

### 8. 多个 worktree 默认共享同一个开发数据库
如果不显式配置，每个 worktree 都连 `DB_NAME=foo_main`，一个失败的 worktree 会跨 worktree 污染。dev 环境也要做 per-worktree DB 隔离。

---

## 五、本项目可直接移植的资产清单

| 文件 | 用途 | 适配工作量 |
|---|---|---|
| `.github/workflows/branch-history-guard.yml` | root-commit ratchet | 改名字即可 |
| `.githooks/pre-push` + `scripts/install-githooks.sh` | 本地 force-push 守卫 | 改保护分支正则 |
| `.github/workflows/flyway-migrate-dryrun.yml` | 迁移 CI dry-run | 改数据库镜像 / migration 路径 |
| `scripts/check-agent-locks.mjs` + `scripts/hot-paths.yml` | 文件锁强制 | 重写 hot-paths 清单 |
| `scripts/agent-lock-renew.mjs` + `scripts/agent-locks-prune.mjs` | 锁续期/清理 | 直接抄 |
| `.github/workflows/agent-locks-janitor.yml` | janitor cron（PR-mode） | 直接抄（注意是最终 PR-mode 版本） |
| `scripts/dev-services.sh` 退避 + fail-state 段 | watchdog 退避 | 提炼模式即可 |
| `scripts/agent-health-check.mjs` | 跨 worktree 聚合 | 改 worktree 根路径 |
| `RULES.md §10.5` | 危险命令红线 | 直接抄 + 改链接 |

---

## 六、移植到新项目的推荐顺序

把上面 10 项按"先后能见效"排：

1. **Day 1**：1（branch protection）+ 2（root guard）+ 4（RULES）+ 3（pre-push hook）
2. **Day 2**：5（migration dry-run）
3. **Week 1**：6（文件锁系统）
4. **按需**：7（watchdog 退避）+ 8（health check）—— 有多 worktree / 守护进程才需要
5. **持续**：9（lessons-learned）+ 10（演练）

Day 1 那批一天能搞定 + 立刻产生效果。后面那些可以按真实需求触发。

---

## 七、首日 checklist

新项目（任意语言、任意框架）从零起步时，按这个 checklist 跑：

```
[ ] git rev-list --max-parents=0 HEAD | wc -l   # 应该是 1
[ ] gh api repos/.../branches/main/protection  # 应该返回 6 项保护全启用 + admin enforce
[ ] .github/workflows/branch-history-guard.yml 存在并跑过
[ ] .githooks/pre-push 存在并可执行
[ ] core.hooksPath = .githooks（每个 dev 跑过 install）
[ ] RULES.md 包含危险命令清单
[ ] 至少一个 CI workflow 在 required status checks 必过列表里
```

跑完这 7 项，新项目的护栏地基就立起来了。后面发现的所有 incident，都应该能映射回上面 10 条护栏里的某一条（要么哪条没立，要么哪条阈值太松）。

---

参见：[[lessons-learned]] 的"二、多 Agent 并行开发的故障模式与防御体系"章节，那是本页内容的具体事件溯源。
