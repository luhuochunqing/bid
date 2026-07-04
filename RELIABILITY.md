# RELIABILITY.md — 可靠性与稳定性要求

门禁、回滚、上线、测试、PR、文件锁。

## 审计与质量门禁

- **静态扫描**：代码提交前必须通过 `eslint` (前端) 和 `checkstyle/pmd/spotbugs` (后端)。
- **架构测试**：后端必须通过 ArchUnit 门禁，确保包依赖、异常处理和 FP-Java 约束不被破坏。主要测试类包括 `FPJavaArchitectureTest`、`MaintainabilityArchitectureTest`、`ProjectAccessGuardCoverageTest`。
- **TDD 覆盖率**：核心业务逻辑（后端 domain/core 层、前端业务组件）的单元测试覆盖率目标为 80% 以上。
- **E2E 验证**：涉及 UI 交互的变更必须包含对应的 Playwright 脚本验证。

### 本地门禁（GitHub CI 替代方案）

当前 GitHub CI 不可用，门禁完全依赖本地 git hooks + alias 双防线：

| 层次 | 机制 | 触发时机 | 能否绕过 |
|------|------|---------|---------|
| 1 | `scripts/git` 包装器（需 `source dev-env.sh` 激活） | 每次 `git` 命令 | 系统级拦截 `--no-verify` |
| 2 | `.githooks/pre-push` + `pre-push-gate.sh`（14 道门禁） | `git push` | hook 自动触发，别名拦截 `--no-verify` |
| 3 | `.githooks/pre-commit`（15+ 项检查） | `git commit` | hook 自动触发，别名拦截 `--no-verify` |
| 4 | **git alias**（`.githooks/git-push-wrapper.sh`） | `git push` | 过滤 `--no-verify`，门禁强制跑 |
| 5 | **git alias**（`.githooks/git-commit-wrapper.sh`） | `git commit` | 过滤 `--no-verify`，hook 强制跑 |

第 4/5 层 alias 是**不依赖 shell PATH 的硬防线**——由 `agent-start-task.sh` 在创建 worktree 时自动配置，
每次 `git push` / `git commit` 无论是否带 `--no-verify`，都强制走包装器脚本，门禁不可绕过。

完整本地门禁（手动触发）：
- `npm run ci:local:quick` — 快速预检（编译 + 架构门禁）
- `npm run ci:local` — 完整本地 CI 模拟（需本地 Docker）
- `npm run agent:pre-push-dry-run` — 模拟推送前 14 道门禁
- `npm run ci:pre-pr` / `bash scripts/ci-pre-pr.sh` — 提交 PR 前一站式门禁

### Gitee CI（远端门禁）

仓库根目录 `.gitee-ci.yml` 提供 Gitee Go 远端流水线，覆盖：
- **治理门禁（governance）**：agent-locks、line-budget、front-data-boundaries、doc-governance
- **前端**：变更范围检测 → 条件执行单元测试与构建
- **后端**：变更范围检测 → 条件执行编译、ArchUnit 架构门禁（FPJavaArchitectureTest、MaintainabilityArchitectureTest）、项目权限守卫覆盖（ProjectAccessGuardCoverageTest）、Checkstyle/PMD/SpotBugs 质量门禁
- **E2E**：默认手动触发，接入 Gitee 私有 Runner 后可改为自动执行

执行逻辑：
- quality-scope job 通过 `git diff` 检测变更范围（backend/src/e2e/doc），以 dotenv artifact 传递给后续 job
- 无相关变更时，对应阶段跳过完整流程（如仅改文档时跳过前后端完整构建）
- agent-locks 不依赖 quality-scope，可并行检测锁文件冲突

> 注意：Gitee Go 需要在仓库「服务」→「Gitee Go」中手动开通；若流水线列表为空，请先检查是否已开通。

## 文件锁门禁

- **锁机制**：已改为 per-task 文件模式（`.agent-locks/<task-slug>.yml`），详情见 CLAUDE.md §5.2。
- **严禁绕过**：`git push --no-verify` / `git commit --no-verify` 已被两层防线禁止：① `scripts/git` 包装器（系统级拦截）；② git alias 强制走 `.githooks/git-push-wrapper.sh` / `.githooks/git-commit-wrapper.sh`（过滤 `--no-verify`）。由 `agent-start-task.sh` 自动配置。
- **自动合并**：1 个 required review 批准后，`.github/workflows/auto-enable-merge-on-approved.yml` 会自动为 PR 开启 GitHub auto-merge（--squash）。真正合并仍需所有门禁（agent-locks、line-budget、frontend/backend/e2e + strict）通过。
- **锁管理命令**：`npm run agent:lock-acquire` / `agent:lock-release` / `agent:lock-renew` / `agent:lock-check` / `agent:lock-cleanup` — 各 worktree 的 task lock 操作快捷入口。

## 创建 Pull Request

推荐使用统一脚本 `scripts/pr-create.sh`（自动适配 GitHub / Gitee）：

```bash
# 方式一：title + body 文件
./scripts/pr-create.sh "feat: 你的标题" /path/to/body.md

# 方式二：title + stdin（用 heredoc 写多行）
./scripts/pr-create.sh "feat: 你的标题" <<'BODY'
## 改动
...
BODY
```

需要环境变量：GitHub 需要 `gh` 已登录，Gitee 需要 `GITEE_TOKEN`。

### Gitee 工作流

```bash
GITEE_TOKEN=xxx npm run gitee:pr-create    # 创建 PR（当前分支→main）
GITEE_TOKEN=xxx npm run gitee:pr-list      # 列出当前分支 PR
GITEE_TOKEN=xxx npm run gitee:pr-merge     # 合并 PR（squash）
npm run gitee:auto-merge                   # 自动合并已批准 PR
```

## 关键硬约束（一句话）

- 推送前必过本地门禁：`npm run ci:pre-pr`。
- 所有新 Flyway 迁移必附 U 回滚脚本。
- 涉及 UI 变更必带 Playwright 证据。
- 原子提交：每次提交应包括功能实现、对应的 Flyway 迁移脚本（如涉及库表）、以及至少一个验证成功的测试用例证据。
- JPA 优先：后端存储必须通过 JPA 实体映射到 MySQL，禁止使用内存 Map 模拟。
- 事务传播自检：涉及 `@Transactional` 的 PR 必须确认事务边界（见 ARCHITECTURE.md §事务边界三原则）。
- **恢复被回退的代码，默认走 `git cherry-pick`，禁止手工重写**（详见下方「回退恢复纪律」）。
- **P0 事故优先止血**：核心模块宕机时走「紧急修复通道（P0 Hotfix）」，30 分钟 SLA（详见下方章节）。

## 紧急修复通道（P0 Hotfix）

> 背景：2026-07-03 标讯中心宕机事故（lessons-learned §36），完整 14 道门禁 + Spec Kit 流程在 P0 场景下耗时过长。本章节定义 P0 事故的快速止血通道，**不替代正常流程**，只在 P0 判定通过时启用。

### P0 判定标准

| 级别 | 判定 | 示例 |
|---|---|---|
| **P0** | 核心模块不可用 / 数据丢失风险 / 安全漏洞 | 标讯中心列表打不开、审批流异常导致项目卡死 |
| P1 | 单一功能不可用 / 性能严重退化 | 某个审批按钮报错、列表查询慢 |
| P2 | 体验问题 / 非核心功能 | UI 错位、文案错误 |

只有 **P0** 才走本通道。P1/P2 必须走正常 14 道门禁流程。

### 30 分钟 SLA 流程

```
T0 ~ T+5min   止血决策
  ├─ 能回滚？→ 走 ROLLBACK.md（dist/jar symlink，2-5 min）
  └─ 不能回滚（新 bug 非回归）→ 启动 hotfix

T+5 ~ T+15min  hotfix 分支
  1. git checkout main && git pull
  2. git checkout -b hotfix/<YYYYMMDD>-<issue-slug>
  3. 改最小代码（只改触发 bug 的那行/那个方法）
  4. 跑 npm run ci:local:quick（编译 + ArchUnit，~2min）
  5. 写一个回归单测证明修复有效

T+15 ~ T+20min 紧急合入
  1. PRE_PUSH_GATE=0 git push -u origin hotfix/...
  2. 创建 PR，标题前缀 [HOTFIX P0]
  3. @reviewer 紧急 review（1 人即可）
  4. 直接 merge to main（不等 auto-merge）

T+20 ~ T+30min 紧急发布
  1. 触发 .github/workflows/main-release.yml
     或 ssh 到生产机跑 scripts/release/remote-deploy.sh
  2. 跑 scripts/release/run-prod-smoke.mjs 验活
  3. 确认 /actuator/health UP + 受影响模块可用
```

### 紧急通道合规边界

`PRE_PUSH_GATE=0` 是**绕过门禁的唯一合规方式**，但有硬性边界：

| 允许 | 禁止 |
|---|---|
| P0 判定通过的 hotfix/* 分支 | 非 P0 场景使用 |
| 跳过 E2E / 前端 build / line-budget | 跳过 ArchUnit 架构守卫 |
| 跳过 agent-locks 文件锁 | 跳过 Flyway 版本号检查 |
| 1 人 review 即可合入 | 跳过 review 直接 push main |
| 直接 merge to main | 跳过 prod-smoke 验活 |

**ArchUnit 和 Flyway 检查必须在 ci:local:quick 阶段本地通过**——架构守卫和数据迁移安全不可妥协。

### 事后补作业（7 工作日内）

紧急通道不是"修完就走"，必须补完：

- [ ] 补 RCA 文档（`docs/lessons/root-cause-analysis-<issue>.md`）
- [ ] 补完整回归测试（不止 1 个紧急单测）
- [ ] 补 ArchUnit 守卫（防止同类问题）
- [ ] 更新 `docs/lessons/lessons-learned.md`
- [ ] 复盘门禁为何漏过这个 bug（是测试覆盖盲区 / 架构守卫盲区 / 还是数据特征未预见）

### 反模式（禁止）

- ❌ "我赶时间，先 `PRE_PUSH_GATE=0` 推了再说" → 必须先做 P0 判定
- ❌ "hotfix 分支跳过 ArchUnit 也行吧" → 架构守卫永远不可跳
- ❌ "修完就完事" → 7 工作日内必须补作业
- ❌ "P1 也走紧急通道吧" → 只有 P0 才能走

## 回退恢复纪律（cherry-pick 优先）

> 背景：2026-06-26 CO-338 恢复时，agent 跳过 `git cherry-pick` 直接手工重写，导致原 commit 的 author / 原始改动意图在 `git blame` / `git log` 中丢失。半年后无人能追溯"这段代码最初是谁写的、为什么"。本节是防再犯的硬约束。详细 playbook 见 `docs/references/rollback-recovery-playbook.md`。

### 硬规则

1. **默认 cherry-pick**：发现某 commit `X` 被后续 commit 误回退，恢复时**必须先尝试 `git cherry-pick X`**。
2. **冲突不豁免**：cherry-pick 冲突 → `git status` 看冲突文件 → 手工解决（保留双方语义）→ `git cherry-pick --continue`。**冲突不是绕过 cherry-pick 的理由**。
3. **只有三种情况允许手工重写**，且必须在 commit message + implementation-notes.md 双重记录原因：
   - 原 commit 改动的文件已**完全不存在**（如组件被删除）
   - 原 commit 的改动**语义已被重组**（如内联代码被拆到子组件，cherry-pick 后代码位置无意义）
   - 原 commit 跨越**多个不相关改动**（违反原子提交，cherry-pick 会带回无关变更）
4. **即便手工重写，也必须在**：
   - commit message 引用原 commit hash（`Restore <hash> ...`）
   - implementation-notes.md 记录原 commit hash + 手工重写原因
   - 让未来 `git log --grep` 能从 hash 反查到原作者

### 恢复工作流（强制顺序）

```bash
# 1. 识别被回退的 commit（用 git log -S / git blame 找）
git log -S "isSelfVisibleTender" --oneline   # 例：找符号消失点

# 2. 先试 cherry-pick（默认路径）
git cherry-pick <原 commit>

# 3. 冲突 → 解决（不是放弃）
git status
# 编辑冲突文件，保留双方语义
git add <文件>
git cherry-pick --continue

# 4. 仅当命中"三种允许手工重写的情况" → 放弃 cherry-pick，手工写
git cherry-pick --abort
# 但 commit message 必须写: "Restore <hash>: ..." + 在 notes 记原因
```

### 为什么不能手工重写

| 价值 | cherry-pick | 手工重写 |
|------|------------|---------|
| `git blame` 追溯原作者 | ✅ 保留 | ❌ 丢失 |
| `git log` 原始 commit 上下文 | ✅ 保留 | ❌ 丢失 |
| 回滚单元（git revert） | ✅ 单 commit | ⚠️ 揉进新 commit |
| review 上下文（一改一逻辑） | ✅ 隔离 | ⚠️ 易混杂 |

### 反模式（禁止）

- ❌ "冲突看起来麻烦，我直接重写吧" → 冲突是正常工程成本，不是绕过理由
- ❌ "反正功能等价就行" → 功能等价 ≠ 历史等价，git 历史是团队资产
- ❌ 静默手工重写不记录原 commit → 半年后无人能考古

### PR 事务传播自检 Checklist

涉及 `@Transactional` 改动时，PR 描述中必须回答以下问题：

- [ ] 本次新增/修改的 `@Transactional` 方法，传播策略已明确（REQUIRED / REQUIRES_NEW / NESTED）
- [ ] 如果有 try-catch RuntimeException，确认子方法是否 `REQUIRES_NEW`（否则事务已 rollback-only，catch 无效）
- [ ] 如果调用了其他 `@Transactional` 方法，确认是否复用主事务（默认 REQUIRED = 复用）
- [ ] `@Auditable` 方法的子调用，是否在独立事务中执行（`REQUIRES_NEW`）
- [ ] ArchUnit RULE 17 已通过（无新增"类级 `@Transactional` + `@Auditable`"组合）

## 参考文档索引

| 概念 | 位置 |
|---|---|
| 发布检查清单 | `docs/release/GO_LIVE_CHECKLIST.md` |
| 生产发布流水线 | `docs/release/PRODUCTION_RELEASE_PIPELINE.md` |
| 回滚手册 | `docs/release/ROLLBACK.md`、`ROLLBACK_RUNBOOK.md` |
| 上线部署 Runbook | `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` |
| 性能/安全/备份/监控交付 | `docs/release/PERFORMANCE_SECURITY_BACKUP_MONITORING_DELIVERY.md` |
| Docker 编排 | `docs/deployment/docker-compose.yml` |
| 测试与 UAT | `docs/testing/` |
| 验收记录 | `docs/release/ACCEPTANCE-2026-05-05.md` |
| 技术债追踪 | `docs/exec-plans/tech-debt-tracker.md` |
| Gitee CI 配置 | `.gitee-ci.yml` |
