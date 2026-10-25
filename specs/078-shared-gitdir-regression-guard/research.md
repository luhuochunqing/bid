# 研究结论

## 决策 1：从受管目录枚举，而不是从 Git 登记反向自证

**Decision**：拓扑门禁扫描 `--worktree-base` 的直接子目录，并逐个执行 Git 查询。

**Rationale**：`git worktree list` 只能看到当前 common gitdir 已登记的成员，无法发现同一受管目录下的独立 clone。

**Alternatives considered**：继续枚举 `git worktree list`；该方案不能覆盖目标故障，拒绝。

## 决策 2：不纳入构建元数据

**Decision**：本任务不修改或校验 `git.properties`。

**Rationale**：构建溯源是独立历史问题，不是共享 gitdir 拓扑的必要组成；纳入会使所有后端 push 被无关缺陷阻断。

**Alternatives considered**：替换 Maven 插件、自制构建清单或添加强制校验；均超出最小防复发范围，拒绝。
