# 共享 Gitdir 防复发门禁 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 防止受管持久 worktree 脱离共享 common gitdir。

**Architecture:** 扩展现有拓扑门禁，使其从受管目录发现真实节点；pre-push 继续执行该检查和一个隔离的真实 Git 回归。

**Tech Stack:** Bash 3.2、Git

## Global Constraints

- 不创建、删除、修复或迁移真实 worktree。
- 不启动共享开发服务。
- 不修改或调用 Maven、后端、Sentry及发布流程。
- 所有行为变更必须先由 shell 回归测试产生预期 RED。

---

### Task 1: 持久 worktree 拓扑门禁

**Files:**
- Modify: `scripts/check-worktree-protection.sh`
- Create: `scripts/check-worktree-protection.test.sh`

**Interfaces:**
- Consumes: `--worktree-base <dir>`、`--expected-common-dir <dir>`
- Produces: 成功退出码 0；任一目录不是同一 common gitdir 时非零退出并列出路径

- [ ] 写临时主仓库、linked worktree 和独立 clone 的失败测试。
- [ ] 运行测试，确认因现有脚本不支持参数且发现不了独立 clone 而 RED。
- [ ] 实现直接子目录枚举、canonical common gitdir 比较和错误汇总。
- [ ] 运行测试，确认正负例 GREEN。

### Task 2: 门禁接入与文档

**Files:**
- Modify: `scripts/pre-push-gate.sh`
- Modify: `scripts/README.md`

**Interfaces:**
- Consumes: Task 1 的拓扑脚本
- Produces: pre-push 早期阻断错误拓扑

- [ ] 写或扩展调度测试，证明 pre-push 调用拓扑门禁且没有额外 Maven 调用。
- [ ] 运行调度测试得到预期 RED。
- [ ] 最小修改 pre-push 和 README。
- [ ] 运行调度测试与 shell 自测得到 GREEN。

### Task 3: 真实 worktree 验收

**Files:**
- Verify only

**Interfaces:**
- Consumes: 当前任务 worktree
- Produces: 当前 common gitdir 的机器证据

- [ ] 运行真实拓扑检查。
- [ ] 执行 `git diff --check`、范围审计和相关全部回归。
