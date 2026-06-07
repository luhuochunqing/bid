---
name: blueprint-driven-development
description: >
  按飞书产品蓝图逐章节实现标讯中心功能（小节粒度，如 4.2.1）。
  触发场景：用户提到"做标讯"、"按蓝图"、"产品蓝图"、"做某小节"、"继续做/下一节"、
  "实现蓝图"、"对标蓝图"、"验证小节"、"推进标讯"、"接着上次标讯"、"还差哪部分"。
  即使不确定是否该触发，也先用上——宁可误触发也不要漏触发。
  核心原则：一图一，要到位——必须启动前后端 + Playwright 真实页面验证 + E2E 测试。
---

# 蓝图标讯中心实现工作流

🚨 **硬性要求：每次只做一个小章节（如 4.2.1、4.2.3、4.2.4），不得跨章节实现。一个小节完成并验证通过后，才进入下一个小节。** 🚨

每次处理产品蓝图的一个小节，按以下步骤推进：

```
读蓝图 → 探代码 → 析差距 → 定方案 → 写代码 → 启动服务 → 真实验证 → 修 Bug → 测试 → 提交
```

## 第 1 步：读蓝图

用 `lark-doc` 技能读取飞书产品蓝图文档的指定章节。

```bash
lark-cli docs +fetch --api-version v2 \
  --doc "https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d" \
  --scope outline --max-depth 5  # 先拿目录，找到目标小节的 block-id
lark-cli docs +fetch --api-version v2 \
  --doc "https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d" \
  --scope section --start-block-id <章节的block-id> \
  --detail full --format pretty
```

## 第 2 步：探索代码现状

用 Explore agent 并行查找：
- 后端：entity、controller、service、repository、state machine 等
- 前端：相关 Vue 组件、composable、API 模块
- Wiki：`.wiki/pages/` 下的相关文档
- 迁移脚本：`db/migration-mysql/` 下相关 SQL

关注：已有实现 vs 蓝图的差异点。

## 第 3 步：差距分析

输出清晰的差距表：

| 蓝图要求 | 当前状态 | 差距 |
|---------|---------|------|
| ... | ... | ... |

明确区分"本次做"和"本次不做"：
- **本次做**：功能缺失、行为不对齐、测试未覆盖
- **本次不做**：依赖后续小节、外部系统、产品待确认

## 第 4 步：实现

按差距分析逐个改动。注意：
- 后端改完 `mvn compile -q` 确保编译通过
- 前端改完 `npm run build` 确保构建通过
- 涉及文件权限变更需同步 Wiki 文档
- 数据库变更需写 Flyway 迁移脚本（`db/migration-mysql/V{version}__*.sql`）+ 回滚脚本（`db/rollback/migration-mysql/U{version}__*.sql`）

> **重要：RoleProfileCatalog 中的权限定义变更必须同步 Flyway 迁移脚本。**
> `RoleProfileBootstrap.ensureSystemRoles()` 对已存在的角色只更新 `isSystem` 字段，
> 不改 `dataScope` 和 `menuPermissions`。所以必须通过 Flyway 迁移来更新数据库中已有角色的定义。

### E2E 测试（强制要求）

任何新增的前端页面或功能，**必须**编写对应的 Playwright E2E 测试。

- 测试文件位置：`e2e/<模块>-<功能>-flow.spec.js`
- 使用 `auth-helpers.js` 进行认证：`ensureApiSession()` + `injectSession()`
- 每个蓝图小节至少覆盖：正向流程、权限验证、边界情况

**模板：**

```javascript
import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

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

test.describe('§X.X.X 小节标题', () => {
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

**外部依赖处理：**
- 如果测试依赖外部 API（如 AI Provider Key），使用 `test.skip()` 标记并写明原因
- commit message 中用 `[skip e2e-scope]` 标记纯后端改动（不涉及前端页面变更）

## 第 5 步：启动服务

### 确定端口和数据库

参考多 Agent SOP 专属资源映射表（CLAUDE.md §2），根据当前所在 worktree 确定：

| Agent | 前端端口 | 后端端口 | 数据库名 | Redis DB |
| :--- | :--- | :--- | :--- | :--- |
| **当前 worktree** | 1315 | 18081 | xiyu_bid_claude | 1 |
| **Codex** | 1316 | 18082 | xiyu_bid_codex | 2 |
| **Gemini** | 1317 | 18083 | xiyu_bid_gemini | 3 |
| **Cursor** | 1318 | 18084 | xiyu_bid_cursor | 4 |
| **Integrator** | 1319 | 18085 | xiyu_bid_integrator | 5 |

> 资源映射表来自项目根目录 CLAUDE.md 的「多 Agent 执行手册 §2 专属资源映射表」。
> 不要硬编码端口——启动前先用 `source scripts/dev-env.sh` 自动检测当前 worktree 的端口分配。

### 后端启动

```bash
export XIYU_DEV_CONFIRMED=1
source scripts/dev-env.sh  # 自动设置 FRONTEND_PORT / BACKEND_PORT / DB_NAME
# 或用显式环境变量覆盖：
DB_NAME=xiyu_bid_claude \
JWT_SECRET="xiyu-bid-poc-local-dev-secret-key-please-change-in-prod-32bytes-min" \
DB_PASSWORD="XiyuDB!2026" \
CORS_ALLOWED_ORIGINS="http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT}" \
mvn spring-boot:run -Dspring-boot.run.profiles=dev,mysql \
  -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT}"
```

### 前端启动

```bash
# ⚠️ 关键：VITE_API_BASE_URL 必须指向当前 worktree 的后端端口！
# 否则前端登录时会 CORS 报错（指向别的 worktree 的后端）
VITE_API_BASE_URL=http://127.0.0.1:${BACKEND_PORT} ./node_modules/.bin/vite \
  --port ${FRONTEND_PORT} --force
```

> **Vite HMR 坑**：git worktree 中 Vite 文件监听偶尔不触发。
> 如果改了前端代码但页面没更新 → `pkill -f vite` → `rm -rf node_modules/.vite` → 加 `--force` 重启。

## 第 6 步：真实页面验证（最关键的一步）

用 Playwright 的 `browser_run_code_unsafe` 工具登录系统，在每个角色下验证功能是否符合蓝图表。

### 验证模式（我们验证过的可靠方式）

```
# 1. 用 curl 获取各角色的 token
# 2. 用 Playwright 注入 token + 用户信息到 localStorage
# 3. 导航到目标页面
# 4. 检查按钮显隐 / 数据范围
```

**获取 token：**

```bash
curl -s -X POST http://127.0.0.1:${BACKEND_PORT}/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bid_admin","password":"Test@123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])"
```

**Playwright 验证代码模板：**

```javascript
async (page) => {
  // 用 curl 或之前获取 of token（注意：重启后端后 token 会失效，需重新获取）
  const token = '<从 curl 获取的 token>';
  
  await page.goto('http://127.0.0.1:1315');
  await page.waitForTimeout(2000);
  
  // 注入认证信息
  await page.evaluate((token) => {
    localStorage.clear();
    localStorage.setItem('token', token);
  }, token);
  
  // 导航到目标页面
  await page.goto('http://127.0.0.1:1315/bidding');
  await page.waitForTimeout(3000);
  
  // 检查按钮
  const buttons = await page.locator('button').all();
  const btnTexts = [];
  for (const btn of buttons) {
    if (await btn.isVisible()) {
      const text = await btn.textContent();
      if (text && text.trim()) btnTexts.push(text.trim());
    }
  }
  return { visibleButtons: [...new Set(btnTexts)].sort() };
}
```

### 验证清单

- ✅ 每个角色的按钮显隐是否符合蓝图权限矩阵
- ✅ 每个角色的数据范围是否正确（能看到/不能看到什么）
- ✅ 状态流转是否按蓝图定义
- ✅ 弹窗内容是否与蓝图一致
- ✅ 异常边界（空数据、错误状态）是否友好

### 角色验证矩阵

对于权限相关的改动，至少验证这 4 个角色：

| 角色 | 用户名 | 密码 |
|------|--------|------|
| `bid_admin`（投标管理员） | `bid_admin` | `Test@123` |
| `bid_lead`（投标组长） | `bid_lead` | `Test@123` |
| `sales`（项目负责人） | `sales` | `Test@123` |
| `bid_specialist`（投标专员） | `bid_specialist` | `Test@123` |

## 第 7 步：修复差距

验证中发现的任何不符合蓝图的行为必须修复。修复后重复步骤 5-6 重新验证。

## 第 8 步：确认测试通过

```bash
npm run build                        # 前端构建
npm run test:unit                    # 前端单元测试
mvn test -Dtest=ArchitectureTest     # 后端架构测试
npm run check:front-data-boundaries  # 前端数据边界
npm run check:doc-governance         # 文档治理
npm run check:line-budgets           # 行数预算

# E2E 测试（必须运行，除非本次改动完全不涉及前端页面）
npm run test:e2e                     # 全量 E2E 测试
# 或针对新增功能运行特定测试：
# npx playwright test e2e/<新增功能>.spec.js --config playwright.config.js
```

> **E2E 门禁**：如果本次改动了前端页面或新增了功能，但 `e2e/` 目录下没有新增或修改对应的 `.spec.js` 文件 → **不准提交**。这是硬性要求。
> 纯后端改动可用 `[skip e2e-scope]` 标记跳过。

## 第 9 步：提交代码

```bash
git add -A && git commit -m "type(scope): description"
git push origin <branch-name>
```

Commit message 格式：`feat|fix(tender): 描述`，说明改了哪个文件、为什么改。

## 第 10 步：记录实现笔记

更新 `implementation-notes.md`，记录：
- 本次改动概要
- 决策记录（D1, D2, ...）——每个决策的 Why 和 How to apply
- 调试中踩到的坑（R1, R2, ...）——便于后续复用
- 未在本次范围的内容

---

## 关键原则

### 一图一
蓝图上写的每一行、每个表、每个字段都要做到位。不能说"这个不重要"跳过。如果确实不适合当前实现，要在 implementation-notes 中记录原因并征得用户同意。

### 真实验证优先
不要只靠单元测试和构建通过就声称"完成"。必须启动前后端，用 Playwright 登录系统，在真实页面中点击验证每个角色的按钮和数据范围是否符合蓝图表。

### 找不到的要做
如果差距分析发现"蓝图上写着但代码里没有"，那就是要做。不是跳过。

### Worktree 路径
当前在 `/Users/user/xiyu/worktrees/gemini`，命令路径都基于此。
启动时用 `source scripts/dev-env.sh` 自动检测当前 worktree 的端口分配，不要硬编码。
