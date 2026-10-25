# 共享 Gitdir 防复发门禁规格

## 背景

西域数智化投标管理平台使用一个主仓库和多个 linked worktree。所有持久 worktree 应共享同一个 Git common directory，同时每个 worktree又有独立的 Git directory 和 HEAD。若受管目录中误放独立 clone，协作脚本看到的引用、hooks 和 worktree 登记会发生分叉。

现有 `check-worktree-protection.sh` 只验证 `git worktree list` 中的路径存在，无法发现受管目录里的独立 clone。本变更只补齐这一项拓扑防复发能力。

## 目标

验证约定范围内的持久 worktree 都解析到当前仓库的同一个 canonical common gitdir。

检查失败时必须 fail closed，并指出检查对象、期望值、实际值和可操作的排查提示。

## 非目标

- 不创建、删除、修复或迁移任何 worktree。
- 不启动前端、后端、Sidecar、数据库或 Redis。
- 不修改 Maven、`git.properties`、Sentry、业务 Java 或发布流程。
- 不扫描任意用户目录；检查范围限于 Git 登记信息和仓库约定的持久 worktree。

## 方案

扩展 `scripts/check-worktree-protection.sh`：从受管持久目录枚举实际目录，而不是从当前 common gitdir 的登记列表反向自证。每个候选目录独立解析 canonical common gitdir，并与当前仓库期望值比较。脚本接受 `--worktree-base` 和 `--expected-common-dir` 参数，仅用于隔离回归测试。

## 错误处理

- Git 命令不可用、目录未登记、common gitdir 无法解析：失败。
- 持久 worktree 目录存在但不属于当前 common gitdir：失败，并报告该目录。
- 临时任务 worktree 不属于持久目录清单：不将其误报为缺失。
- 多项错误一次性汇总，便于一次修复；最终退出码保持非零。

## 门禁接入

`scripts/pre-push-gate.sh` 继续调用扩展后的持久 worktree 完整性脚本，并运行其独立 CLI 回归。不新增 Maven 调用或其他门禁职责。

同步更新：

- `scripts/README.md` 的脚本索引；
- `RELIABILITY.md` 或脚本内涉及门禁数量、职责的说明（仅在实际计数发生变化时）；
- 受影响脚本的头部维护声明。

## 测试与验收

采用 RED→GREEN：

1. 在临时受管目录创建一个主仓库和 linked worktree，证明同一 common gitdir 通过；
2. 在同一受管目录放入独立 clone，证明目录枚举能发现并拒绝不同 common gitdir；
3. 覆盖路径含空格和相对 `.git` 指针；
4. 在真实任务 worktree 执行脚本，确认 common gitdir 指向主仓库；
5. 执行相关 Node/shell 自测及 pre-push 调度契约测试，确认门禁确实调用该脚本。

## 文件范围

预计修改：

- `scripts/check-worktree-protection.sh`
- `NEW: scripts/check-worktree-protection.test.sh`
- `scripts/pre-push-gate.sh`
- `scripts/README.md`
- 本规格后续对应的 `plan.md`、`tasks.md`

不修改业务 Java、Vue、数据库迁移或运行时配置。

## 完成条件

- 所有规定负例稳定失败、正例稳定通过；
- 真实 linked worktree 的 common gitdir 通过；
- pre-push 调度测试证明新门禁无法因入口漏接而失效；
- 工作区只包含本规格允许的文件；
- 不宣称部署或业务 UAT，本任务交付边界为经验证的本地代码与测试。
