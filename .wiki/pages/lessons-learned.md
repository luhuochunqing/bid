---
title: 工程经验总结
space: engineering
category: guide
tags: [经验总结, 数据库迁移, PostgreSQL, MySQL, Flyway, 多Agent并行, 工程化护栏, Git历史, ArchUnit, Controller包规范, 错误消息设计, 用户体验]
sources:
  - CLAUDE.md
  - RULES.md
  - conductor/tracks/tender_source_tag_20260509/index.md
backlinks:
  - _index
  - multi-agent-defense-playbook
created: 2026-05-10
updated: 2026-06-22
health_checked: 2026-06-27
---
# 工程经验总结

> 记录本次 `cursor/tender-source-tag-20260509` 分支开发中积累的经验教训。

---

## 一、数据库迁移目录清理（PostgreSQL → MySQL）

### 问题背景

项目早期同时支持 PostgreSQL 和 MySQL，导致存在两套迁移脚本目录：
- `db/migration/` — PostgreSQL（已废弃）
- `db/migration-mysql/` — MySQL（活跃使用）

2026-04-29 官方移除 PostgreSQL 支持后，`migration/` 目录成为历史遗留，未及时清理。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 双数据库目录导致维护成本翻倍 | 决策后立即清理废弃路径，防止技术债累积 | **废弃即删除**，不要保留"仅供参考"的代码 |
| `application-mysql.yml` 配置 `flyway.locations: classpath:db/migration-mysql`，但 `migration/` 仍存在 | 配置文件与实际目录结构必须保持同步 | 每次配置变更后验证对应目录状态 |
| CI 可能在历史某次配置中引用 PostgreSQL 测试 | CI 配置需与当前技术栈严格对齐 | **CI 是技术栈的真实声明**，不是可配置选项 |

### 操作规范（已固化到 CLAUDE.md）

1. **迁移脚本位置**：`backend/src/main/resources/db/migration-mysql/`
2. **禁止使用 `migration/`**：该目录已废弃
3. **命名规范**：
   - 基线版本：`B{version}_*.sql`
   - 增量版本：`V{version}_*.sql`
4. **版本号**：必须大于已有最大版本号
5. **回滚脚本**：`db/rollback/` 目录

### 验证命令

```bash
# 确认迁移目录结构正确
ls backend/src/main/resources/db/
# 期望输出：migration-mysql/  rollback/

# 确认 CI 只测试 MySQL
grep -E "Flyway.*Test" .github/workflows/ci.yml
# 期望输出：FlywayMysqlContainerTest（无 PostgreSQL 相关）
```

---

## 二、CI 配置与实际技术栈对齐

### 发现的问题

CI 配置中曾存在指向 PostgreSQL 的测试：
```yaml
# 错误的配置（已修复）
- name: Run migration and architecture gates
  run: mvn -Dtest=DualDatabaseMigrationParityTest,FlywayPostgresContainerTest,...
```

### 修复方案

```yaml
# 正确的配置
- name: Run migration and architecture gates
  run: mvn -Dtest=FlywayMysqlContainerTest,ArchitectureTest test
```

### 经验

- **CI 配置即技术栈声明**：CI 中出现的每一项都代表项目实际支持的能力
- **定期审计 CI**：使用 `git log` 和 `grep` 检查 CI 配置与代码库的一致性

---

## 三、工作流程建议

### 数据库迁移操作 Checklist

```
[ ] 确认目标数据库类型（MySQL/PostgreSQL）
[ ] 检查 flyway.locations 配置
[ ] 确认迁移脚本所在目录
[ ] 创建迁移脚本（V{version}__{desc}.sql）
[ ] 运行 Flyway 测试验证
[ ] 更新 CLAUDE.md（如有规范变更）
[ ] 提交前验证 CI 配置一致性
```

### 删除废弃代码 Checklist

```
[ ] 确认代码已被配置引用（grep 检查）
[ ] 确认 CI 配置不再使用
[ ] 确认文档不再引用
[ ] 执行删除
[ ] 更新文档（如 CLAUDE.md）
[ ] 提交并推送
[ ] 通知相关 agent（涉及共享模块时）
```

---

## 四、相关文档

- [[agent-sop-quickref]] — Agent 开发 SOP（含多 Agent 协作规范）
- [[architecture]] — 架构说明
- CLAUDE.md — 项目执行入口（含数据库迁移规范）

---

## 五、变更记录

| 日期 | 分支 | 变更内容 |
|------|------|----------|
| 2026-05-10 | cursor/tender-source-tag-20260509 | 删除废弃 migration/ 目录，更新 CLAUDE.md 数据库迁移规范 |

---

## 二、多 Agent 并行开发的故障模式与防御体系（2026-05-11）

> 一次排障涉及 V117 迁移报错 / 企微登录按钮丢失 / Flyway history 污染 / 8 把过期锁 / Gemini worktree 死循环 5 个看似独立的问题。
> 彻查后发现它们共享同一类根因：**多 Agent 并行改动在没有护栏的情况下持续退化**。本节把这次梳理出的故障模式和护栏落成文字，避免未来重复踩坑。

### 2.1 真实发生过的故障模式

| 模式 | 症状 | 根因 |
|---|---|---|
| **Git 历史撕裂** | `main` 出现 4 个 disconnected root commits；`src/views/Login.vue` 等文件被"覆盖回旧版本"，企微入口消失 | Agent 用 `filter-repo` / `checkout --orphan` / `replace --graft` / 强推 后未验证，且 `main` 没有 branch protection |
| **迁移自身 bug 溜进 main** | V117 包含 `UPDATE status='BIDDING'`，但 ENUM 里没有 `BIDDING` → fresh DB 启动全挂 | CI 只跑 H2 ddl-auto 测试，**从未把 migration 从 baseline 跑一遍**到真 MySQL |
| **失败迁移的 schema_history 污染** | 启动失败后 Flyway 留一条 `success=0`，下次启动直接被卡住；launchd 每 5s 重启一次生成新 failed 行 | watchdog 是无脑循环，没有失败计数/退避，加上多 worktree 共享同一个 `xiyu_bid_main` DB，事态会跨 worktree 传染 |
| **文件锁形同虚设** | `.agent-locks.yml` 里 8 把锁全过期 2 天；新任务改 `db/migration-mysql/` 和 `Login.vue` 没人挂锁；CI 的 `agent-locks` job 只检查冲突，**不检查是否登记** | 锁系统设计完整但 CI 不强制、没续期机制、没清理机制，导致全员默认忽略 |
| **分支长期失败无人知** | Gemini worktree 编译失败 24 小时，launchd 每 30s 重试一次，产出 tens of GB 日志 | watchdog 没有阈值停机；没有跨 worktree 的健康度聚合工具；失败信号只存在于本地日志里 |

### 2.2 一次事故里踩到 5 个坑 —— 事故时间线

```
触发: npm run dev 启动失败
  ↓ backend 报 "Data truncated for column 'status'"
发现 1: V117 有 SQL bug（UPDATE ENUM 未声明的值）
  ↓ 清 flyway_schema_history 后重启
发现 2: launchd 每 5s 又把 backend 拉起来，立即再写 failed 行
  ↓ launchctl bootout 停掉守护进程
发现 3: 另一个 worktree（Gemini）也在连 xiyu_bid_main，每 30s 抢写
  ↓ 对比 worktree 的 Login.vue
发现 4: main 上的 Login.vue 被覆盖回了无企微按钮的旧版本
  ↓ git log --all --oneline 追查
发现 5: main 有 4 个 disconnected root commits，历史被改写过
```

**教训**：单一工程问题在没有护栏的系统里**会互相放大**。每一层小漏洞独立看都可以"下次注意"，叠加起来就是几小时的 production-style 排障。

### 2.3 护栏体系（2026-05-11 已上线）

| 层 | 工具 | 功能 | 应急开关 |
|---|---|---|---|
| **Git 历史** | `.github/workflows/branch-history-guard.yml` | root commit 数 ratchet；超过 `MAX_ROOTS=4` 直接 CI 红 | 修复后降低 ratchet |
| **Git 历史** | `.githooks/pre-push` | 拒绝 non-fast-forward push 到 main/master/release/* | `FORCE_PUSH_OK=1` 一次性 |
| **Git 历史** | GitHub Branch Protection | 强制 PR / 线性历史 / 禁 force push / 禁删除 | GitHub 设置 |
| **Git 历史** | `RULES.md §10.5` | 7 条历史改写命令的红线清单 + 双人签字流程 | — |
| **迁移** | `.github/workflows/flyway-migrate-dryrun.yml` | 每个改 `migration-mysql/` 的 PR 在 MySQL 8.0 从 baseline 跑一遍 | — |
| **多 Agent** | `scripts/check-agent-locks.mjs` + `scripts/hot-paths.yml` | hot-path 改动必须有 active lock；无锁 CI 红 | 加锁再 push |
| **多 Agent** | `npm run agent:lock-renew` | 当前分支锁批量延期 +2 天 | — |
| **多 Agent** | `.github/workflows/agent-locks-janitor.yml` | 每天 2:17 AM UTC 清理已死分支的过期锁 | — |
| **运行时** | `scripts/dev-services.sh` 退避 | 30s → 2min → 10min → 30min；10 次失败写 `backend.fail-state` | `rm .runtime/dev-services/backend.fail-state` |
| **运行时** | `npm run agent:health-check` | 聚合所有 worktree 的 backend/frontend/sidecar 状态 + 最近 ERROR 行 | — |

### 2.4 工作流铁律

踩过这次坑之后，**以下行为在 `main` 上默认禁止**，违反需要先开 issue 拿签字：

- `git filter-repo` / `git filter-branch` / `git checkout --orphan`
- `git replace --graft` / `git replace -d`
- `git rebase --root`
- `git push --force` / `git push --force-with-lease`
- `git reset --hard` 后接 push
- 通过 GitHub API 直接 PATCH refs

详细清单和豁免流程见 `RULES.md §10.5`。

### 2.5 给未来自己的 checklist

如果接下来再出现 fresh DB 启动失败 / 登录页某功能消失 / worktree 疯转的情况，**先跑这 3 条**再往下查：

```bash
# 1. 有没有某个 worktree 在后台死循环？
npm run agent:health-check

# 2. 当前分支改的文件是不是 hot-path 没挂锁？
npm run agent:lock-check:changed

# 3. main 的历史是不是被改写了？
git rev-list --max-parents=0 origin/main | wc -l   # 应该是 1
```

### 2.6 待办（P1 / P2）

| Issue | 状态 | 优先级 | 真正修复依赖 |
|---|---|---|---|
| [#224](https://github.com/ericforai/bidding/issues/224) | ratchet 锁住不恶化 | P1 | ~30 min 停机窗口做历史缝合 |
| [#220](https://github.com/ericforai/bidding/issues/220) | 数据污染路径已关 | P1 | `dev-env.sh` 改造支持 per-worktree DB |
| [#221](https://github.com/ericforai/bidding/issues/221) | health-check 可见 | P2 | Gemini worktree 手动 rebase |
| [#227](https://github.com/ericforai/bidding/issues/227) | `@Disabled` 绕过 | P2 | 业务 owner 决定 fixture 策略 |

**重点**：防御到位 ≠ 问题消失。这些 P1/P2 仍是真实债务，只是当前不会继续恶化。

---

## 三、路由组件替换与 E2E 数据播种隔离冲突引发的 CI 失败故障模式分析（2026-05-21）

> 在 Phase 2 中，我们为了上线新版 “AI 案例应答网格” 视图，直接将原本指向 `Case.vue`（传统案例库）的路由 `/knowledge/case` 替换成了指向 `CaseGrid.vue`。
> 这一改动看似简单，却在 GitHub CI 的 E2E 检查阶段引发了连锁失败，经历了多次提交调试。以下为故障模式的深度解剖与避坑指南。

### 3.1 真实发生过的故障模式与原因分析

| 冲突模式 | 症状描述 | 根本原因 |
|---|---|---|
| **E2E 播种与 UI 展现脱节** | `commercial-main-flow.spec.js` 执行到案例库校验步骤时发生超时，提示找不到刚才 seeded 的案例标题。 | E2E 在 `/api/knowledge/cases` API 下生成了一个传统 `Case` 实例。但前端 `/knowledge/case` 路由组件已被替换为 `CaseGrid.vue`，它仅渲染通过事件驱动生成的 AI 应答切片（`/api/cases`），完全不请求也不展示传统案例，导致播种的数据在页面中为 **不可见** 状态。 |
| **选择器和按钮文本变化导致挂死** | `case-advanced-flow.spec.js` 在执行到搜索案例步骤时卡住，等待 `getByRole('button', { name: '搜索' })` 时超时失败。 | 新的 `CaseGrid.vue` 筛选表单虽然有 "关键词" 输入框，但其提交按钮被命名为 **“筛选”** 且执行不同的回调；而传统 `Case.vue` 按钮名为 **“搜索”**。E2E 脚本是黑盒测试，仍然使用旧的选择器定位，导致直接挂死。 |
| **本地与 CI 行为差异（为什么本地容易漏测）** | 开发者在本地开发完后，常通过本地 dev 服务进行点击确认，发现 AI 应答网格展示正常，容易产生“功能已就绪”的错觉；而 E2E 跑在 CI 纯净的数据库中，才会暴露播种和断言的深层断裂。 | 1. 局部修改时，容易漏跑全量 E2E 脚本。<br>2. 新增的 AI 案例（`KnowledgeCase`）是事件驱动的（通过 `ProjectClosedEventListener`），手动测试如果没有走完项目归档全流程，就无法自动在 AI 应答网格中生成数据。因此很难通过常规手工点点点发现数据源的断层。 |

### 3.2 本地运行 Playwright 时踩到的“端口占用”陷阱

在排查上述 E2E 失败时，我们发现在本地执行 `npx playwright test` 经常会遇到 `Port 18080 is already in use` 报错，导致测试框架在 Global Setup 阶段就被阻断，增加了本地复现和验证的难度。

- **起因**：Playwright 的全局生命周期脚本 `e2e/api-global-setup.js` 在执行时，会检查 `.rehearsal/playwright-api-stack.started` 等标记文件。如果认为 E2E 服务未启动，它会强行运行 `scripts/test/start-api-e2e-stack.sh` 来启动一个内置的 H2 内存版后端实例（占 18080）和 Preview 前端实例（占 1314）。
- **冲突**：平时 Agent 在 Worktree 联调时，常设的 `npm run agent:up` 常驻服务已经占用了 18080 和 1314 端口，但是因为没有在 `.rehearsal/` 下写入 PID 和 stack 标记文件，Playwright 误判定“无现成服务可用”，尝试拉起新进程并发生了 **端口争抢**。

### 3.3 规避与防护体系

1. **功能保留与页签包装（Wrapper）设计**：
   - 避免无脑“一刀切”替换路由组件。对于旧有功能和已被其它组件引用（如标书编辑器的案例引用）的数据，应当使用选项卡包装器 [CaseWrapper.vue](file:///Users/user/xiyu/xiyu-bid-poc/src/views/Knowledge/views/CaseWrapper.vue) 将 **“AI 案例网格”** 和 **“传统案例库”** 进行平级兼容。
   - 这样既把新视图作为默认 Tab 展示，又保留了历史库和测试播种通道。
2. **E2E 脚本与 UI 变更联动**：
   - 在前端主入口被 Tab 包装后，E2E 脚本在断言 seeded 的传统数据前，必须主动触发点击切换到传统页签的动作：
     ```javascript
     await page.getByText('传统案例库').click()
     ```
3. **本地 E2E 测试正确命令流**：
   - 若要完美复现/跑通本地 Playwright 脚本，必须先关闭常驻 dev 服务释放端口，然后再启动测试：
     ```bash
     # 1. 停止本地开发服务
     export XIYU_DEV_CONFIRMED=1 && npm run agent:stop
     
     # 2. 运行 E2E 测试，Playwright 将自动启动独立的干净 mock-AI/H2 内存后端
     npx playwright test
     
     # 3. 验证通过后，重新拉起开发联调环境
     export XIYU_DEV_CONFIRMED=1 && npm run agent:up
     ```

### 3.4 经验值变更记录

| 日期 | 分支 | 变更内容 |
|------|------|----------|
| 2026-05-21 | 004-knowledge-base-impl | 包装 CaseWrapper 选项卡，解决传统案例 E2E 测试因路由覆盖导致的全部失败 |
| 2026-05-29 | PR #485 #489 | 7 项工程踩坑复盘，见下 §四 |

## 四、2026-05-29 多 PR 踩坑复盘

> 来源：PR #485 (返回按钮 + CA管理) → PR #488 (孤儿锁) → PR #489 (45 存量测试归零)

### 4.1 Agent Worktree 代码泄露

**事故**: 从 agent worktree 拷贝文件时混入了其他分支的预存变更（codex 的 bidding-create 改动、E2E 删除、spec 文件删除），导致 PR #485 包含了不该有的文件。

**根因**: agent worktree 有 `git status` 未显示的预存变更。

**预防**:
- 拷贝前先在 agent worktree 执行 `git status` 确认干净
- 提交前 `git diff origin/main..HEAD --stat` 逐文件审查
- 用精确路径拷贝而非批量拷贝

### 4.2 Controller 直接依赖 Repository / Entity

**事故**: `CaCertificateController` 注入 `UserRepository` 并返回 `CaCertificateEntity`，ArchitectureTest 报 2 个违规。

**修复**: 用户解析移到 Service 层（`resolveUser(UserDetails)`），返回值改用 DTO。

**预防**:
- 新建 Controller 后立刻 `mvn test -Dtest=ArchitectureTest`
- Controller 只持有 Service，不持有 Repository
- 返回值统一用 DTO，不用 Entity

### 4.3 E2E 面包屑文本冲突

**事故**: 返回按钮功能给子页面添加了面包屑，面包屑文本与 `<h2>` 页面标题相同。Playwright `getByText()` 在 strict mode 下匹配到 2 个元素 → 失败。

**修复**: 改用 `getByRole('heading', { name: 'xxx' })` 精确定位页面标题。

**预防**:
- E2E 优先用语义选择器：`getByRole('heading')` > `getByRole('button')` > `getByText()`
- 新增 UI 元素（面包屑等）后，跑关联 E2E 测试确认无冲突

### 4.4 Lock 文件孤儿

**事故**: PR #485 的 `.agent-locks/claude-universal-back-button.yml` 包含非 hot-path 锁（Favorites.vue、ai-analysis），合并 main 后成为孤儿，阻塞其他 agent 的 CI。

**修复**: PR #488 删除孤儿锁。事后发现合并前应执行 `agent:lock-release --all`。

**预防**:
- 合并 PR 前：`npm run agent:lock-release -- --all`
- 只保留 hot-path 必需的锁（router、migration、workflows、githooks）
- 非 hot-path 文件改完就释放锁，不要带进 main

### 4.5 ApiResponse JSON 字段名 `msg` vs `message`

**事故**: `ApiResponse` 用 `@JsonProperty("msg")` 输出 JSON 字段 `msg`，但 40 个测试断言 `$.message`。PR #489 批量修复 15 个测试文件。

**根因**: `ApiResponse` 改了 JSON 输出字段名以兼容客户规范，测试未同步。

**预防**:
- 修改 API 响应格式 → `grep -rn '\$\.message' src/test/` 找所有断言
- 或改 `ApiResponse` 的同时跑全量测试，根据失败列表批量修正

### 4.6 回滚脚本路径 + Source Header

**事故**: U1008 回滚放在 `db/rollback/` 而非 `db/rollback/migration-mysql/`，且缺少 source header。FlywayRollbackScriptCoverageTest 报错。

**预防**:
- 路径：`db/rollback/migration-mysql/U{version}__*.sql`
- Header 模板：
  ```
  -- Input: migration-mysql/V{version}__*.sql
  -- Output: rollback script for mysql environments; ...
  -- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
  ```
- 提交前 `mvn test -Dtest=FlywayRollbackScriptCoverageTest`

### 4.7 ProjectAccessGuard Baseline

**事故**: 新建 `CaCertificateController` / `CaCertificateService` / `CaBorrowService` 未注册到 `project-access-guard-baseline.txt`，测试报 unguarded files。

**预防**:
- 新建 Controller/Service → 添加到 baseline（如不涉及 projectId 访问）
- 或接入 `ProjectAccessScopeService` 守卫链（如涉及 projectId）

### 4.8 提交前门禁清单

```bash
# 后端
mvn test -Dtest=ArchitectureTest        # 架构合规
mvn test -Dtest=FlywayRollbackScriptCoverageTest  # 回滚覆盖

# 前端
npm run build                            # 构建
npm run test:unit                        # 单元测试
npm run check:line-budgets               # 行预算
npm run check:doc-governance             # 文档治理
npm run check:front-data-boundaries      # 数据边界

# 提交前审查
git diff origin/main..HEAD --stat        # 逐文件审查，排除泄露
npm run agent:lock-release -- --all      # 释放非 hot-path 锁（合并前）
```

---

## 五、新增 Controller 放入根包导致 ArchitectureTest 失败（2026-06-18）

> 来源：PR #792 `feat(oss-org): sync role menu permissions from OSS getUserMenuTree (#002)`
> 分支：`agent/kimi/002-oss-menu-permission-sync`

### 5.1 事故

新增 `AdminRoleOssMenuSyncController` 时按直觉放在了 `com.xiyu.bid.controller` 根包下，本地业务测试全绿，但 `mvn test -Dtest=ArchitectureTest` 报错：

```
java.lang.AssertionError: Architecture Violation [Priority: MEDIUM] -
Rule 'root controller package should only contain whitelisted classes' was violated (1 times):
Class <com.xiyu.bid.controller.AdminRoleOssMenuSyncController> does not match any whitelist
```

### 5.2 根因

项目通过 ArchUnit 对 `com.xiyu.bid.controller` 根包做了白名单限制，只允许早期少量通用 Controller（如 `AdminRoleController`）存在。新业务能力 Controller 必须落入对应一级模块包，例如 `com.xiyu.bid.integration.organization.controller`。

### 5.3 修复

1. 将文件物理移动到 `backend/src/main/java/com/xiyu/bid/integration/organization/controller/`
2. 修改 `package` 声明为 `com.xiyu.bid.integration.organization.controller`
3. 同步更新测试文件 `AdminRoleOssMenuSyncControllerTest` 的 import
4. 重新运行 `ArchitectureTest` 通过

### 5.4 预防 Checklist

- [ ] 新建 Controller 前先确认它属于哪个业务模块
- [ ] 业务模块 Controller 统一放在 `com.xiyu.bid.<module>.controller`，不要直接放根包
- [ ] 提交前必跑 `mvn test -Dtest=ArchitectureTest`
- [ ] 若必须放根包，先更新 `ArchitectureTest` 白名单并经过架构评审

### 5.5 经验值变更记录

| 日期 | 分支 | 变更内容 |
|------|------|----------|
| 2026-06-18 | `agent/kimi/002-oss-menu-permission-sync` | 将 `AdminRoleOssMenuSyncController` 从根包迁移到 `integration.organization` 包，修复 ArchUnit 违规 |

## 六、PR 合并验证盲区——编号复用 + rebase 丢 commit + 服务器/main 代码漂移（2026-06-19）

> 来源：CRM bidInfoSync 回传修复（CO-263）
> 涉及分支：`agent/zcode/webhook-crmbidinfosync-fix`（旧，含核心修复）→ 重新发起为 PR !827
> 教训级别：**P0**（险些导致已验证的线上修复被回退）

### 6.1 事故

CRM bidInfoSync 回传有两个核心修复：(A) `mapToCrmStatus` 弃标 status `1→6`（CRM 真实枚举 1=跟进中、6=弃标），(B) `buildPayload` 的 code 字段改用 `crm_opportunity_id`（商机编号）而非 sourceId。修复已在服务器部署并通过 tender 268 实测验证（用户确认 CRM 商机状态变为"弃标"）。

次日继续工作时，为修另一个前端 bug 从 main 开新分支，发现 main 上的 `WebhookEventListener.mapToCrmStatus` 仍是 `ABANDONED -> 1`（错误值），`buildPayload` 仍用 sourceId——**两个核心修复从未进入 main**。而服务器上跑的 jar 是修复版（commit `29bd3bc91`），**服务器代码与 main 不一致**，下次从 main 部署会回退到 status=1，CRM 商机状态又会被错误改成"跟进中"。

### 6.2 根因：三个叠加的验证盲区

**盲区一：PR 编号复用导致"已合并"假象**

Gitee PR 编号是全局递增的，标题/分支可复用。本案例中：
- 旧分支 `agent/zcode/webhook-crmbidinfosync-fix` 含核心修复（commit `b035dea50`/`29bd3bc91`/`fb2c7038d`）
- 但 Gitee `!820` 标题是"@TransactionalEventListener save 静默失效 + status 列截断"，实际合并的是**另一个 commit `ddf4661d3`**（只含 6 个文件：NotificationDeliveryTaskListener、status 列加宽、V1086 迁移），**不含我们的 status 1→6 修复**
- 前一会话总结里写"PR !820 已合并"，照着 `git log origin/main` 看到 `!820` 字样就以为核心修复已合并，**未核对 !820 实际合并的 commit 是否就是本分支的 commit**

**盲区二：rebase 静默丢弃 commit**

从 main 开新分支时执行了 `git rebase origin/main`。由于 main 上已有同号 `!820` 占位（内容不同），rebase 时旧分支的 4 个 commit **被静默丢弃**（`git log origin/main..HEAD` 为空），但没有任何告警。开发者以为"分支已与 main 同步"，实际是"修复被丢了"。

**盲区三：服务器/main 代码漂移未被察觉**

服务器上跑的 jar 是某个临时 commit（`29bd3bc91`）编译的，该 commit 从未进 main。这种"线上跑的代码 ≠ main 代码"的状态在以下情况下会导致回退：
- 下次从 main 重新部署 → 回退到未修复版本
- 他人基于 main 开发 → 看到旧代码，可能重复修或引入冲突

### 6.3 决定性证据

```bash
# origin/main 上 !820 实际合并的 commit
$ git show --stat b3c30f93c   # !820 merge commit
Merge: 963c9c475 ddf4661d3    # 来源是 ddf4661d3，不是我们的 29bd3bc91
 6 files changed              # 只含 NotificationDeliveryTaskListener + status 列加宽 + V1086 迁移

# origin/main 上 mapToCrmStatus 仍是错误值
$ git show origin/main:.../WebhookEventListener.java | grep ABANDONED
    case ABANDONED -> 1;      # 错误！弃标应为 6

# 我们的核心修复 commit 是否在 origin/main 历史中
$ git branch -r --contains 29bd3bc91
  origin/agent/zcode/webhook-crmbidinfosync-fix   # 只在旧分支，不在 origin/main
```

### 6.4 修复

重新发起 PR !827（分支 `agent/zcode/fix-crm-opportunity-code`，基于 latest main 重做两个核心修复）：
- `mapToCrmStatus`: `ABANDONED 1→6`
- `buildPayload`: 注入 `TenderRepository`，code ← `crm_opportunity_id`，删除 `extractSourceId`
- 单测 9/9 绿，文档同步修正

### 6.5 预防 Checklist

**PR 合并验证（不能只看编号）**：
- [ ] 合并后必须用 `git log origin/main --oneline | grep <本分支 commit hash>` 确认**本分支的具体 commit** 进了 main，不能只看 PR 编号或标题
- [ ] 用 `git branch -r --contains <commit-hash>` 确认核心 commit 在 `origin/main` 历史中
- [ ] 若 PR 标题/分支被复用，必须核对 `git show <merge-commit> --stat` 实际合并的文件清单是否覆盖本次修复

**rebase 后验证**：
- [ ] rebase 后立即跑 `git log origin/main..HEAD --oneline`，确认本分支的 commit 仍在（不为空）
- [ ] 若为空但本应有改动，说明 commit 被丢弃，需 `git reflog` 找回或重新应用

**服务器/main 一致性**：
- [ ] 服务器部署的 jar 必须对应一个**已在 main 上的 commit**，不能是临时 commit
- [ ] 部署后在服务器上跑 `git log -1` 或记录 commit hash，与 main 对比
- [ ] 定期（至少每次从 main 部署前）核对 `服务器 jar 对应 commit` ∈ `origin/main 历史`

### 6.6 通用规则

1. **PR 编号不是合并证据**——编号可复用，标题可相似。唯一可靠的合并证据是"本分支的具体 commit hash 出现在目标分支历史中"
2. **rebase 不保证保留 commit**——当 main 已有同号/同标题 PR 占位时，rebase 会静默丢弃"看似已合并"的 commit。rebase 后必须用 `git log origin/main..HEAD` 验证 commit 仍在
3. **线上 jar 必须可追溯到 main**——任何部署到服务器的 jar 都必须对应 main 上的一个 commit，否则就是"未进 main 的修复"，下次部署必回退
4. **"已合并"的状态要在新分支上验证**——开新分支从 main rebase 后，若发现代码仍是旧值，说明前一个 PR 的修复未真正进 main，要立即调查而不是继续闷头干活

### 6.7 经验值变更记录

| 日期 | 分支 | 变更内容 |
|------|------|----------|
| 2026-06-19 | `agent/zcode/fix-crm-opportunity-code`（PR !827） | 重新发起被 PR !820 编号复用误导而丢失的核心修复（status 1→6 + code 改用 crm_opportunity_id）；沉淀本节 PR 合并验证 checklist |
| 2026-06-22 | `agent/cursor/co-301-tender-duplicate-message`（PR !958） | 标讯去重拦截错误消息从"标讯已存在"改为"投标管理系统该标讯已存在"（3 处源码 + 4 个测试类 + E2E 测试）；沉淀"业务异常消息应包含系统上下文"经验；部署时发现 Maven 缓存旧 class、SSH 中文乱码误判，沉淀"服务器部署 jar 验证四原则" |

---

## 七、业务异常消息应包含系统上下文（2026-06-22）

> 来源：CO-301 / PR !958

### 7.1 问题

业务异常消息 `"标讯已存在"` 在代码中三处出现（`TenderDuplicateException`、`TenderIntegrationCommandService` 中的两处 `IllegalArgumentException` 和 `PushResult.message`），服务于手动创建和外部系统推送两个入口。用户/集成方看到后无法判断"哪个系统拦截了操作"，导致额外沟通成本。

### 7.2 根因模式

| 根因 | 说明 |
|------|------|
| message 无系统上下文 | `"标讯已存在"` 没有"谁"的信息 |
| 多入口共用同文案 | 不同入口应使用一致风格的前缀 |
| Code Review 不审查 message | 只检查异常类型和 HTTP 状态，不检查 message 可读性 |
| 测试断言宽松匹配 | `contains("标讯已存在")` 让 message 可默默退化而不红 |

### 7.3 规范

| 场景 | 规范 |
|------|------|
| 新业务异常 | message 必须包含系统/子系统前缀，如 `"投标管理系统XXX"` |
| 多入口消息 | 同一业务概念在不同入口使用一致前缀 + 差异化细节 |
| Code Review | 异常字符串等同于 UI 文案，审查可读性而非只看类型 |
| 测试断言 | 精确匹配完整 message，或至少匹配带系统前缀的片段 |

### 7.4 防复发策略

```bash
# 检查业务异常是否缺少系统上下文
grep -rn 'super(".*已存在")' backend/src/main/java
grep -rn 'IllegalArgumentException("标讯' backend/src/main/java
```

### 7.5 关联的变更

- [docs/lessons/root-cause-analysis-co-301.md](../../docs/lessons/root-cause-analysis-co-301.md) — 完整根因分析
- [docs/lessons/lessons-learned.md](../../docs/lessons/lessons-learned.md) §11 — 通用扩展版本

---

## 八、服务器部署 jar 验证四原则（2026-06-22）

> 来源：CO-301 部署排查

### 8.1 问题

代码已合入 main 并打包 jar 部署到服务器，但运行时仍返回旧文案。排查发现：
1. 打包时 Maven 缓存了旧 class 文件，jar 中实际内容未更新
2. 仅检查 jar 大小无法发现内容差异
3. SSH 终端中文编码导致 javap 输出乱码，误判为旧版本
4. 最终通过 `jar uf` 局部更新 class 文件解决

### 8.2 四原则

| 原则 | 说明 |
|------|------|
| **打包后必须验证 jar 内容** | 不能只检查 jar 大小，用 `javap -v` 检查关键 class 常量池 |
| **`mvn clean` 后重新打包** | 不依赖增量编译，确保使用最新编译结果 |
| **用 `xxd` 字节比较验证** | SSH 终端中文乱码时，用 `xxd \| grep` 或 `diff <(xxd) <(xxd)` 比较 |
| **用 `jar uf` 局部更新** | 只更新修改的 class 文件，无需重新打包整个 jar |

### 8.3 验证命令

```bash
# 打包前先 clean
cd backend && mvn clean compile spring-boot:repackage -DskipTests

# 验证 jar 中关键 class 的常量池
unzip -p target/bid-poc-1.0.3.jar BOOT-INF/classes/.../Foo.class | javap -v - 2>/dev/null | grep -A5 "Constant pool"

# 服务器上用 xxd 验证（避免中文乱码）
ssh server 'unzip -p /opt/.../app.jar BOOT-INF/classes/.../Foo.class | xxd | grep -A2 "e68a 95"'

# 局部更新 jar
jar uf app.jar BOOT-INF/classes/.../Foo.class

# 字节级比较
diff <(unzip -p local.jar BOOT-INF/classes/.../Foo.class | xxd) \
     <(ssh server 'unzip -p remote.jar BOOT-INF/classes/.../Foo.class | xxd')
```

### 8.4 防复发检查清单

- [ ] `mvn clean` 后重新打包，不依赖增量编译
- [ ] 打包后用 `javap -v` 验证关键 class 常量池内容
- [ ] 服务器验证用 `xxd` 字节比较，不依赖 SSH 中文显示
- [ ] 如需快速更新，用 `jar uf` 局部替换 class 文件
- [ ] 部署后通过 API 实测验证功能（而非仅检查 actuator 状态）
