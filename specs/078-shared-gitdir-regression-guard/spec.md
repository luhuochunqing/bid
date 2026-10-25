# 共享 Gitdir 防复发门禁规格

## 背景

西域数智化投标管理平台使用一个主仓库和多个 linked worktree。所有持久 worktree 应共享同一个 Git common directory，同时每个 worktree 又有独立的 Git directory 和 HEAD。若 worktree 意外来自独立 clone，协作脚本看到的远端、引用和 hooks 会发生分叉；若构建工具把 common directory 的 HEAD 当成当前 worktree HEAD，生成的 `git.properties` 会错误标识构建来源。

现有 `check-worktree-protection.sh` 只验证 `git worktree list` 中的路径存在，不能证明持久 worktree 共享同一 common directory，也不能证明构建元数据属于当前 worktree。本变更补齐这两个防复发面。

## 目标

新增一个可独立执行、可由推送门禁复用的检查脚本，同时验证：

1. 约定范围内的持久 worktree 都解析到当前仓库的同一个 canonical common gitdir；
2. Maven 生成的 `git.properties` 中完整 commit ID 等于当前 worktree 的 `git rev-parse HEAD`。

检查失败时必须 fail closed，并指出检查对象、期望值、实际值和可操作的排查提示。

## 非目标

- 不创建、删除、修复或迁移任何 worktree。
- 不启动前端、后端、Sidecar、数据库或 Redis。
- 不替换 `git-commit-id-maven-plugin`，除非真实 RED 证明确需最小配置修复。
- 不以 `git.properties` 替代发布时对 jar 内实际代码内容的验收。
- 不扫描任意用户目录；检查范围限于 Git 登记信息和仓库约定的持久 worktree。

## 方案

### 单一入口、两个检查阶段

新增 `scripts/check-shared-gitdir.sh`：

1. **拓扑阶段**
   - 以当前仓库 `git rev-parse --path-format=absolute --git-common-dir` 的 canonical path 为期望值；
   - 从 `git worktree list --porcelain` 获取登记的 worktree；
   - 仅对主工作区和 `/Users/user/xiyu/worktrees/<持久名称>` 约定范围执行检查，临时 `xiyu-<agent>-<task>` worktree 不要求成为持久节点，但其自身必须能解析到同一 common gitdir；
   - 在每个目标目录内重新执行 Git 查询，不从 `.git` 文件文本猜测；
   - 使用物理 canonical path 比较，避免相对路径和符号链接造成误报。

2. **构建元数据阶段**
   - 获取当前 worktree 完整 HEAD；
   - 通过 Maven 的最小生命周期生成 `backend/target/classes/git.properties`；
   - 读取插件生成的完整 commit ID 属性并与 HEAD 严格比较；
   - 属性缺失、值为空、生成失败或不一致均判失败；
   - 若当前插件配置真实复现错误，才修改 `backend/pom.xml`，让插件显式读取当前 worktree 的 Git directory。配置必须保持跨机器可移植，不写入绝对路径。

脚本提供仅供自动化测试使用的环境注入点，使测试可以传入临时仓库、持久目录根和预生成属性文件，避免篡改真实 worktree 或依赖完整 Maven 构建。默认执行路径不接受降低强度的静默跳过。

## 错误处理

- Git 命令不可用、目录未登记、common gitdir 无法解析：失败。
- 持久 worktree 目录存在但不属于当前 common gitdir：失败，并报告该目录。
- 临时任务 worktree 未位于固定持久目录命名范围：不将其误报为缺失。
- Maven 未安装或元数据生成失败：在完整检查模式下失败，不把环境缺失写成通过。
- 多项错误一次性汇总，便于一次修复；最终退出码保持非零。

## 门禁接入

`scripts/pre-push-gate.sh` 在现有持久 worktree 完整性检查附近调用新脚本。门禁输出分别标识“共享 gitdir 拓扑”和“构建 commit 元数据”，避免只看到笼统失败。

同步更新：

- `scripts/README.md` 的脚本索引；
- `RELIABILITY.md` 或脚本内涉及门禁数量、职责的说明（仅在实际计数发生变化时）；
- 受影响脚本的头部维护声明。

## 测试与验收

采用 RED→GREEN：

1. 临时创建一个主仓库和 linked worktree，证明同一 common gitdir 通过；
2. 混入独立 clone 或伪造不同 common gitdir，证明拓扑检查失败；
3. 提供与 worktree HEAD 相同的 `git.properties`，证明元数据检查通过；
4. 提供 common/main HEAD 或任意其他 SHA，证明元数据检查失败；
5. 覆盖路径含空格、相对 `.git` 指针、缺失属性和空值；
6. 在真实任务 worktree 执行脚本，确认 common gitdir 指向主仓库且生成属性等于当前任务分支 HEAD；
7. 执行相关 Node/shell 自测及 pre-push 调度契约测试，确认门禁确实调用该脚本。

## 文件范围

预计修改：

- `NEW: scripts/check-shared-gitdir.sh`
- `NEW: scripts/test/check-shared-gitdir.test.sh`（若现有测试约定更适合 Node，则使用同名 `.test.mjs`）
- `scripts/pre-push-gate.sh`
- `scripts/check-pre-push-dispatch.test.mjs` 或新增的等价调度契约测试
- `scripts/README.md`
- `backend/pom.xml`（仅在 RED 证明插件配置有误时）
- 本规格后续对应的 `plan.md`、`tasks.md`

不修改业务 Java、Vue、数据库迁移或运行时配置。

## 完成条件

- 所有规定负例稳定失败、正例稳定通过；
- 真实 linked worktree 的 common gitdir 与当前 HEAD 元数据均通过；
- pre-push 调度测试证明新门禁无法因入口漏接而失效；
- 工作区只包含本规格允许的文件；
- 不宣称部署或业务 UAT，本任务交付边界为经验证的本地代码与测试。
