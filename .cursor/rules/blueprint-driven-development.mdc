---
name: blueprint-driven-development
description: >
  按飞书产品蓝图逐章节实现标讯中心功能（小节粒度，如 4.2.1）。
  触发场景：用户提到"做标讯"、"按蓝图"、"产品蓝图"、"做某小节"、"继续做/下一节"、
  "实现蓝图"、"对标蓝图"、"验证小节"、"推进标讯"、"接着上次标讯"、"还差哪部分"。
  即使不确定是否该触发，也先用上——宁可误触发也不要漏触发。
  核心原则：一图一，要到位——必须启动前后端 + Playwright 真实页面验证 + E2E 测试。
  含检查点契约和任务追踪，不按流程会被卡住。
---

# 蓝图标讯中心实现工作流

> **工作流执行契约**：本技能使用检查点文件 + 任务追踪双机制确保按序执行。
> 不遵守流程的 Agent 会在第一步就被检查点卡住。不要跳过任何阶段。

## 工作流执行契约（强制约束 ⚖️）

### 检查点文件机制

每次启动时读取 `<worktree-root>/.blueprint-checkpoint.json`。文件不存在则创建：

```bash
WORKTREE=$(git rev-parse --show-toplevel 2>/dev/null || echo "$PWD")
cat "$WORKTREE/.blueprint-checkpoint.json" 2>/dev/null || echo '{"section":null,"phase":1,"phases_done":[]}' > "$WORKTREE/.blueprint-checkpoint.json"
```

检查点格式：
```json
{"section":"4.2.1","phase":3,"phases_done":[1,2],"worktree":"/path/to/worktree"}
```

- `phase`：当前应执行的阶段（1-5）
- `phases_done`：已完成的阶段编号
- 完成一个阶段后更新 `phases_done` 并将 `phase` 加 1

### 任务追踪

进入每个阶段前，**必须先检查**：

```bash
WORKTREE=$(git rev-parse --show-toplevel 2>/dev/null || echo "$PWD")
CHECKPOINT=$(cat "$WORKTREE/.blueprint-checkpoint.json" 2>/dev/null || echo '{"phase":1,"phases_done":[]}')
```

检查当前 `phase` 是否等于你要执行的阶段编号。如果不相等，说明你跑错了段，停下来用 TaskList 查看当前应该做什么。

每个阶段完成时：
1. ✅ 更新检查点文件（`phase` + 1，追加 `phases_done`）
2. ✅ 用 TaskUpdate 将对应任务标为完成
3. ✅ 记录 implementation-notes.md

### 阶段门禁汇总

| 阶段 | 编号 | 前置条件 | 后置产出 |
|------|:----:|---------|---------|
| 蓝图分析 | 1 | 检查点 phase=1 | 蓝图摘要输出 |
| 差距分析 | 2 | phase=2 且 phase-1 已完成 | 差距分析表（写入 `gap-analysis.md`） |
| 实现 | 3 | gap-analysis.md 存在 | 编译/构建通过 |
| 验证 | 4 | 编译/构建通过 | 所有角色验证截图 |
| 验收与提交 | 5 | 验证通过 | git 提交成功 |

---

## 快速参考

```bash
# 获取所有角色的 token（验证阶段使用）
bash .claude/skills/blueprint-driven-development/scripts/get-tokens.sh
# 输出：/tmp/blueprint-tokens.json

# 页面验证辅助函数（注入 browser_run_code_unsafe）
# 路径：.claude/skills/blueprint-driven-development/scripts/verify-page.js

# 角色权限矩阵（参考用）
# 路径：.claude/skills/blueprint-driven-development/references/role-permissions.md

# 自动检测端口和数据库
source scripts/dev-env.sh   # 设置 BACKEND_PORT / FRONTEND_PORT / DB_NAME
```

---

## Phase 1: 蓝图分析

**前置条件**：检查点 phase=1
**门禁**：如果检查点 phase > 1，跳到 Phase 2

### 操作步骤

1. 用 `lark-doc` 读取蓝图文档目录，找到目标小节的 block-id：

```bash
lark-cli docs +fetch --api-version v2 \
  --doc "https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d" \
  --scope outline --max-depth 5
```

2. 读取目标小节正文：

```bash
lark-cli docs +fetch --api-version v2 \
  --doc "https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d" \
  --scope section --start-block-id <章节的block-id> \
  --detail full --format pretty
```

3. 输出蓝图摘要到 `implementation-notes.md`，记录本章节要求的功能点、权限矩阵、状态流转。

### 完成后

```bash
WORKTREE=$(git rev-parse --show-toplevel)
echo '{"section":"4.2.1","phase":2,"phases_done":[1],"worktree":"'"$WORKTREE"'"}' > "$WORKTREE/.blueprint-checkpoint.json"
# 更新 TaskUpdate 将 Phase 1 标为完成
```

---

## Phase 2: 差距分析

**前置条件**：检查点 phase=2
**门禁**：验证 Phase 1 已完成（检查点 `phases_done` 含 1）

### 操作步骤

1. 用 Explore agent 并行查找以下路径中的相关代码：
   - 后端：`backend/src/main/java/com/xiyu/bid/` 下 entity / controller / service / repository
   - 前端：`src/` 下 Vue 组件 / composable / API 模块
   - 迁移：`backend/src/main/resources/db/migration-mysql/`
   - Wiki：`.wiki/pages/`

2. 输出差距分析表到 `gap-analysis.md`：

```markdown
# 差距分析 - 小节 [4.2.X]

| 蓝图要求 | 当前状态 | 差距 | 工作量预估 |
|---------|---------|------|-----------|
| ... | ... | ... | ... |
```

3. 明确区分：

| 类别 | 说明 |
|------|------|
| **本次做** | 功能缺失、行为不对齐、UI 不一致、测试未覆盖 |
| **本次不做** | 依赖后续小节、外部系统未就绪、产品待确认 |

### 完成后

```bash
WORKTREE=$(git rev-parse --show-toplevel)
echo '{"section":"4.2.1","phase":3,"phases_done":[1,2],"worktree":"'"$WORKTREE"'"}' > "$WORKTREE/.blueprint-checkpoint.json"
# TaskUpdate: Phase 2 done
```

---

## Phase 3: 实现

**前置条件**：检查点 phase=3 且 `gap-analysis.md` 存在
**门禁**：如果 `gap-analysis.md` 不存在 → **不能进入 Phase 3**，退回 Phase 2

### 操作步骤

1. 按差距分析表逐个改动。优先处理"本次做"项。

2. 每改完一个大块：
   - 后端：`mvn compile -q` 确保编译通过
   - 前端：`npm run build` 确保构建通过

3. **权限变更**：如果改了 `RoleProfileCatalog.java` 中的 `menuPermissions`：
   - 必须写 Flyway 迁移脚本更新已存在角色的数据
   - 文件：`db/migration-mysql/V{next}__*.sql`
   - 回滚：`db/rollback/migration-mysql/U{next}__*.sql`
   - 原理：`RoleProfileBootstrap.ensureSystemRoles()` 对已存在角色只更新 `isSystem`，不改 `dataScope` 和 `menuPermissions`

4. **E2E 测试（强制要求）**：
   任何新增的前端页面或功能，**必须**编写对应的 Playwright E2E 测试。

   - 测试文件位置：`e2e/<模块>-<功能>-flow.spec.js`
   - 使用 `auth-helpers.js` 进行认证：`ensureApiSession()` + `injectSession()`
   - 每个蓝图小节至少覆盖：
     - 正向流程（正常操作是否成功）
     - 权限验证（不同角色是否看到正确内容）
     - 边界情况（空数据、错误状态）

   **E2E 测试模板：**

   ```javascript
   import { test, expect } from '@playwright/test'
   import { ensureApiSession, injectSession } from './auth-helpers.js'

   const E2E_PASSWORD = 'XiyuDemo!2026'

   async function loginAsRole(page, role) {
     const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
     const session = await ensureApiSession({
       username: `e2e_${role}_${suffix}`,
       role,
       fullName: `E2E ${role} 测试`
     })
     await injectSession(page, session)
     return session
   }

   test.describe('小节标题', () => {
     test('正向流程描述', async ({ page }) => {
       await loginAsRole(page, 'bid_admin')
       await page.goto('/目标路径')
       await page.waitForSelector('.el-table, .el-form', { timeout: 10000 })
       // 执行操作并断言
     })

     test('权限验证：bid_specialist 不应看到某按钮', async ({ page }) => {
       await loginAsRole(page, 'bid_specialist')
       await page.goto('/目标路径')
       await page.waitForSelector('.el-table, .el-form', { timeout: 10000 })
       const button = page.getByRole('button', { name: '某管理按钮' })
       await expect(button).toHaveCount(0)
     })
   })
   ```

   **命名规范：**
   - `e2e/tender-manual-create.spec.js` — 标讯人工录入
   - `e2e/bidding-permission-buttons.spec.js` — 按钮权限验证
   - `e2e/project-create-full-flow.spec.js` — 项目创建完整流程

   **外部依赖处理：**
   - 如果测试依赖外部 API（如 AI Provider Key），使用 `test.skip()` 标记并写明原因
   - commit message 中用 `[skip e2e-scope]` 标记纯后端改动（不涉及前端页面变更）

5. **异常处理**：
   - 编译错误：阅读错误日志，修复后重试
   - 构建失败：检查 `node_modules` 完整性，`npm install` 后重试
   - 迁移 checksum 失败：`rm -rf backend/target` 后重试

### 完成后

```bash
WORKTREE=$(git rev-parse --show-toplevel)
echo '{"section":"4.2.1","phase":4,"phases_done":[1,2,3],"worktree":"'"$WORKTREE"'"}' > "$WORKTREE/.blueprint-checkpoint.json"
# TaskUpdate: Phase 3 done
```

---

## Phase 4: 验证（最关键的阶段）

**前置条件**：检查点 phase=4 且编译/构建通过
**门禁**：如果 `mvn compile -q` 或 `npm run build` 失败 → **不准进入验证**，退回 Phase 3 修编译错误

### 4.1 启动服务

```bash
export XIYU_DEV_CONFIRMED=1
source scripts/dev-env.sh  # 自动检测端口

# 完整启动脚本见 references/service-startup.md
DB_NAME=${DB_NAME} JWT_SECRET="..." DB_PASSWORD="..." \
mvn spring-boot:run -Dspring-boot.run.profiles=dev,mysql \
  -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT}" > /tmp/backend.log 2>&1 &

VITE_API_BASE_URL=http://127.0.0.1:${BACKEND_PORT} ./node_modules/.bin/vite \
  --port ${FRONTEND_PORT} --force > /tmp/frontend.log 2>&1 &
```

> 完整启动脚本和故障恢复见 `references/service-startup.md`

### 4.2 获取认证 Token

```bash
bash .claude/skills/blueprint-driven-development/scripts/get-tokens.sh
# 检查输出：4 个角色都应显示 ✅
# ❌ 说明后端未就绪或登录接口有问题
TOKENS=$(cat /tmp/blueprint-tokens.json)
```

### 4.3 Playwright 验证

使用 `scripts/verify-page.js` 辅助函数注入 token、导航页面、批量验证权限。

```javascript
await v.loginWithToken(page, url, token);
await page.goto(url + '/bidding');
const buttons = await v.getVisibleButtons(page);
const results = await v.batchVerify(page, checks);
return { buttons, results };
```

> 完整验证代码和辅助函数用法见 `references/playwright-verification.md`

### 4.4 角色验证矩阵

每个改动至少验证以下 4 个角色（用 Phase 2 的差距分析表逐项对照）：

| 角色 | 验证重点 |
|------|---------|
| `bid_admin`（投标管理员） | 全部权限是否到位 |
| `bid_lead`（投标组长） | 管理权限是否正确 |
| `sales`（项目负责人） | 业务权限是否正确 |
| `bid_specialist`（投标专员） | 仅审核权限，无管理权限 |

验证清单：
- ✅ 每个角色的按钮显隐是否符合蓝图权限矩阵
- ✅ 每个角色的数据范围是否正确（能看到/不能看到什么）
- ✅ 状态流转是否按蓝图定义
- ✅ 弹窗/表单内容是否与蓝图一致
- ✅ 空数据状态、异常状态是否友好
- ✅ 截图保存到 `verification-screenshots/`

### 4.5 修复差距

验证中发现的任何不符合蓝图的行为必须修复。修复后回到 4.1 重启服务重新验证。

每次修复后更新 `gap-analysis.md`，在差距旁标注已修复。

### 完成后

```bash
WORKTREE=$(git rev-parse --show-toplevel)
echo '{"section":"4.2.1","phase":5,"phases_done":[1,2,3,4],"worktree":"'"$WORKTREE"'"}' > "$WORKTREE/.blueprint-checkpoint.json"
# TaskUpdate: Phase 4 done
```

---

## Phase 5: 验收与提交

**前置条件**：检查点 phase=5 且 Phase 4 验证通过
**门禁**：如果 `verification-screenshots/` 目录为空或缺少某个角色的截图 → **不准提交**

### 操作步骤

1. 运行测试套件（必须跑 CI 同等的检查，确保 PR 提交后 CI 能通过）：

```bash
# 前端
npm run build                        # 前端构建
npm run build:api                    # API 模式构建（CI 同时跑 build + build:api）
npm run test:unit                    # 前端单元测试
npm run check:front-data-boundaries  # 前端数据边界
npm run check:doc-governance         # 文档治理
npm run check:line-budgets           # 行数预算

# E2E 测试（必须运行，除非本次改动完全不涉及前端页面）
npm run test:e2e                     # 全量 E2E 测试
# 或针对新增功能运行特定测试：
# npx playwright test e2e/<新增功能>.spec.js --config playwright.config.js

# 后端 CI 质量门禁（checkstyle + PMD + SpotBugs）
mvn -Pjava-quality,java-quality-spotbugs,quality-strict \
  -DskipTests -Djacoco.skip=true checkstyle:check pmd:check spotbugs:check

# 后端 CI 架构 + Flyway 容器测试
mvn -Dtest=ArchitectureTest,FlywayMysqlContainerTest test
```

> **E2E 门禁**：如果本次改动了前端页面或新增了功能，但 `e2e/` 目录下没有新增或修改对应的 `.spec.js` 文件 → **不准提交**。这是硬性要求。
> 纯后端改动（如 API 响应格式调整、Service 层重构）可用 `[skip e2e-scope]` 标记跳过。

2. 更新 `implementation-notes.md`，追加记录：
   - 本次改动概要（改了什么文件、为什么）
   - 决策记录（D1, D2, ...）——每个决策的 Why
   - 调试中踩到的坑（R1, R2, ...）
   - 未在本小节范围的内容

3. 提交代码：

   ```bash
   git add -A
   git commit -m "feat(tender): 小节 4.2.X — 一句话描述核心改动"
   git push origin <current-branch>
   ```

4. （如创建堆叠 PR）创建 PR 分支后，检查 agent-locks：

   创建 `pr/tender-4-2-X` 分支后，如果改动涉及 hot-path 文件（`application*.yml`、entity、db/migration），必须确保 `.agent-locks/` 中对应 lock 的 `branch` 字段指向 PR 分支名：

   ```bash
   # 检查 lock 的 branch 是否匹配 PR 分支
   cat .agent-locks/<task-slug>.yml
   # 如不匹配，手动更新 branch 字段后 cherry-pick 到 PR 分支
   ```

   否则 CI `agent-locks` 门禁会因 branch 不匹配而失败。

### 完成后

```bash
WORKTREE=$(git rev-parse --show-toplevel)
echo '{"section":"4.2.1","phase":0,"phases_done":[1,2,3,4,5],"worktree":"'"$WORKTREE"'"}' > "$WORKTREE/.blueprint-checkpoint.json"
# TaskUpdate: Phase 5 done
```

---

## 错误恢复指引

各阶段常见问题及修复方案见 `references/troubleshooting.md`。

---

## 关键原则

### 一图一
蓝图上写的每一行、每个表、每个字段都要做到位。不能说"这个不重要"跳过。如果确实不适合当前实现，在 `implementation-notes.md` 记录原因并征得用户同意。

### 真实验证优先
不要只靠单元测试和构建通过就声称"完成"。必须启动前后端，用 Playwright 在真实页面中点击验证每个角色的按钮和数据范围。

### 找不到的要做
如果差距分析发现"蓝图上写着但代码里没有"，那就是要做。不是跳过。

### 动态路径
不要硬编码 worktree 路径或端口。始终使用：
- `git rev-parse --show-toplevel` 获取当前 worktree 根目录
- `source scripts/dev-env.sh` 检测端口分配
- `.claude/skills/blueprint-driven-development/scripts/` 引用脚本
