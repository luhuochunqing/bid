# Feature Specification: Account 详情 429 反复修 — 根因分析与根治

**Feature Branch**: `agent/cursor/root-account-429`
**Created**: 2026-07-12
**Status**: Draft
**Supersedes**: spec 034 (`friendly-rate-limit`) 已落地的"症状层"修复（PR !2032 / !2035 / !2036）
**Author**: agent/cursor（根因分析 + spec 沉淀 + pre-push 脚本）

---

## 0. 为什么需要新 spec？前 3 个 PR 反复修不好的真相

| PR | 改了什么 | 真实问题是什么 | 为什么没修好 |
|---|---|---|---|
| **!2032** `feat(rate-limit): 限流提示友好化` | 文案 / 3s 冷却合并 / `silentRateLimit` / `Retry-After` / `RateLimitResponseFactory` | 用户看到 `AxiosError: 429` 的根本原因是**业务层 catch 块覆写了全局提示** | 不动业务层（71 处仍存在） |
| **!2035** `fix(rate-limit): 修复业务层 catch 覆盖` | Account.vue 改 2 处 → `notifyErrorUnlessRateLimit` | 还有 69 处同类问题 + **Account.vue `loadAccountDetail` catch 块被遗漏**（只 `console.error`，不调用 `notifyErrorUnlessRateLimit`） | grep 不全，没用 `scripts/scan-load-on-mount-429.mjs` 自动化审计 |
| **!2036** `fix(rate-limit): Account 详情请求 429 降级 + 串行化` | `DETAIL_CONCURRENCY` 5→2→**1（完全串行）**，加 `.catch(() => row)` 降级 | 列表端点只返回脱敏 SummaryDTO，**强制前端 N+1 拉每行 detail**（`accounts.js` 第 60-67 行 + 第 263 行 `loadDetailsInBatches`） | 追症状不追根因；以为"降并发 = 不撞限流"；不理解 N+1 是架构问题不是参数问题 |

**第 6 次反复修复历史链**（co-数字追溯）：

```
afc11b64e !1997  批次大小 5      ← 局部防御
f3f4ca6f4 !2005  批次大小 2      ← 局部防御
8a32fe8b3 !2036  批次大小 1（串行） ← 局部防御
```

每次都"提高防御等级"，但根因——**`list 端点不返回完整 DTO`**——始终没动。账号数翻倍后这条链还要继续追加 `!2040 / !2050 / !2060 ...`。

**根因（5 个为什么 §3.1）**：

```
W1: 为什么 Account 详情 429？
  → GET /api/platform/accounts/{id} 短时间内集中触发 RateLimitFilter
W2: 为什么集中触发？
  → loadAccounts 加载列表后，对每行 getDetail（N+1）
W3: 为什么有 N+1？
  → getList 端点只返回脱敏 SummaryDTO，缺失完整业务字段
W4: 为什么 list 端点要脱敏？
  → 2026-Q1 L3 安全加固时把 password 字段拆到独立 /password 端点，
    同时为"最小授权"把所有非 password 业务字段也拆了
W5: 为什么业务字段要拆？
  → 当年 PR 没区分"密码敏感字段" vs "普通业务字段"，做了过度设计

根因：list 端点做了过度脱敏，迫使前端 N+1 → 撞限流 → 业务层错误处理被绕过 → 用户看到 AxiosError
```

**为什么之前一直修不好（命中工程纪律 §1 的 4 个根因）**：

1. **根因 1「追症状不追根因」**：3 个 PR 都聚焦"429 之后用户看到什么"，没人问"为什么会发生 429"
2. **根因 4「测试只测修过的函数，不测根因行为」**：`Account.spec.js` 用 `expect(source).toMatch(/DETAIL_CONCURRENCY\s*=\s*1/)` 测文本，**不验证 N+1 真的消失**
3. **根因 5「修 A 破 B」**：每次降并发都假设"60 秒 100 次"够用，但生产 40 账号详情请求 **50 行数据 50 个 detail** 加上页面其他请求，必然撞
4. **根因 7「盲目相信已修复」**：!2036 合入后没在生产 N=100+ 场景下真实验证就 ship

---

## 1. User Scenarios & Testing

### User Story 1 - 根治 N+1（治本）

**As a** 投标专员 / 项目负责人 / 管理员
**I want to** 进入「平台账户管理」页面时**只发一次**列表请求
**So that** 不再触发 429，看到完整业务数据

**Acceptance Scenarios**:
1. **Given** 系统中存在 50 个平台账户，**When** 打开 `/resource/account` 页面，**Then** 网络面板显示 1 个 `GET /api/platform/accounts` 请求 + 0 个 `getDetail`，**总请求数 = 1**（当前 = 51）
2. **Given** 系统中存在 200 个平台账户，**When** 打开页面，**Then** 仍只 1 个列表请求，列表端点分页参数正常工作，**总请求数 ≤ 2**（列表 + 当前页 detail 缓存）
3. **Given** 用户在列表中点击某行查看详情，**When** 已打开过该行，**Then** 复用列表中已有的完整 DTO，不再发 `getDetail`（降级路径仍保留为可选优化）

### User Story 2 - 全局业务层 429 治理（治标兜底）

**As a** 任何页面的业务开发
**I want to** 业务层 catch 块统一走 `notifyErrorUnlessRateLimit` 而不是裸 `ElMessage.error`
**So that** 即使发生 429，用户也只会看到一条友好提示，不会看到 `AxiosError` 堆栈

**Acceptance Scenarios**:
1. **Given** `src/` 下任何 `.vue`/`.js` 文件中存在 `try { await ...Api.x() } catch (e) { ElMessage.error(e.message || ...) }` 模式，**When** pre-push gate 跑起来，**Then** 拦截并提示迁移到 `notifyErrorUnlessRateLimit`
2. **Given** 存量 71 处业务层 `ElMessage.error` 中至少 30 处可能在 429 路径上（基于 `scan-429-catch.mjs` 结果），**When** 跑 `scripts/audit-existing-429-exposure.mjs`，**Then** 输出"高风险"清单（按 API 调用频次 + 是否在 onMounted 路径）

### User Story 3 - 防复发拦截（工程纪律 §6.4）

**As a** 任何 agent / 开发者
**I want to** 提交代码时自动检测"list 端点 + N+1 getDetail"反模式
**So that** 未来不会再出现"为了脱敏把 list 端点做残，迫使前端 N+1"的架构错误

**Acceptance Scenarios**:
1. **Given** 有人在 `src/views/<x>/<list>.vue` 里写了 `await Promise.all(list.map(row => <api>.getDetail(row.id)))` 或 `loadDetailsInBatches`，**When** pre-push gate 跑，**Then** 报错：`list 端点应返回完整 DTO；如果必须分页加载，每页 < 5 个 detail 即可，禁用 N+1 全量加载`
2. **Given** 后端新增一个 `getList` 端点**只返回 ID 数组**（典型 N+1 反模式），**When** 跑 `scripts/check-list-endpoint-n1.mjs`，**Then** 警告：`/api/<x>/list 只返回 N 个 ID，前端必然 N+1`

---

## 2. Functional Requirements

### FR-A: 后端契约改造（治本）
- **FR-A-01** `GET /api/platform/accounts` 返回完整业务 DTO（含 `contactPerson` / `registrant` / `registerPhone` / `registerEmail` / `hasCa` / `remarks` / `borrower` / `dueAt` / `lastUsed` 等所有非敏感字段）
- **FR-A-02** `password` 字段在 list 端点**绝不出现**（保持独立 `/api/platform/accounts/{id}/password` 端点）
- **FR-A-03** 后端单元测试：`PlatformAccountControllerTest#list_doesNotIncludePasswordField` 断言响应 JSON 不含 `password` 字段
- **FR-A-04** 后端契约文档：`backend/docs/api-contract-platform-account.md` 列出 list 端点返回字段白名单

### FR-B: 前端消费方改造
- **FR-B-01** `Account.vue` 删除 `loadDetailsInBatches` 函数、`DETAIL_CONCURRENCY` 常量、`loadAccountDetail` 的 catch 块
- **FR-B-02** `accountsApi.getList` 响应中的 `data` 直接用作列表数据（已经是完整 DTO，无需再次 normalize）
- **FR-B-03** `Account.spec.js` 新增**根因行为测试**：mock list 返回 50 条记录时断言 `getDetail` 调用次数 = 0；mock list 返回完整字段时断言列表能直接渲染所有字段
- **FR-B-04** 业务层 catch 块（71 处存量）按以下策略迁移：
  - `ElMessage.error(e.message || 'fallback')` → `notifyErrorUnlessRateLimit(e, 'fallback')`
  - `ElMessage.error('fallback')` （无 e.message）→ 保留（不会触发 429 路径）
  - `ElMessage.error(serverMsg)`（已取 `res?.msg`）→ 保留（业务层响应码处理）

### FR-C: 工程基础设施
- **FR-C-01** 新增 `scripts/check-list-endpoint-n1.mjs`：扫描 `src/views/**/*.{vue,js}` 检测 `Promise.all(...map(row => *.getDetail(...)))` 反模式；扫描 `src/api/modules/**/*.js` 检测 `getList` 端点缺少 `data` 完整性的契约
- **FR-C-02** 升级 `scripts/check-429-error-override.mjs`：从"只拦截新增 diff"升级为"同时审计存量 71 处"，输出高风险清单 + 修复指引
- **FR-C-03** `scripts/audit-existing-429-exposure.mjs`：在 CI 跑一次，输出 `high-risk / medium-risk / low-risk` 三档清单（基于"是否在 onMounted 路径 + API 频次"）

---

## 3. Success Criteria

| 编号 | 指标 | 测量方法 |
|---|---|---|
| SC-01 | Account 页面网络请求数从 51（50 detail + 1 list）降到 ≤ 2 | `DevTools Network` 录制 N=50 / N=200 场景 |
| SC-02 | Account 页面打开时**完全不出现** 429 响应 | 同上录制 |
| SC-03 | 后端 list 端点响应体**100%** 不含 `password` 字段 | `PlatformAccountControllerTest` + 契约测试 |
| SC-04 | 存量业务层 `ElMessage.error` 调用从 71 处降到 ≤ 10 处（仅保留"非 API 错误"或"已取 res.msg"的合理用法） | `scan-429-catch.mjs` 跑分 |
| SC-05 | `scripts/check-list-endpoint-n1.mjs` 在 CI 跑通过 | pre-push gate 集成 |
| SC-06 | pre-push gate 集成 `audit-existing-429-exposure` 高风险项 **= 0** | pre-push gate 集成 |

---

## 4. Non-Goals

- **不**修改 RateLimit 阈值（60 秒 100 次保持）
- **不**修改 RateLimitFilter 现有 429 文案逻辑（spec 034 已落地）
- **不**改动 `accounts.js` 的 password 独立端点
- **不**重构 Account.vue 的 tabs/弹窗 UI（只动数据加载层）

---

## 5. Dependencies / Risks

| 依赖 / 风险 | 缓解 |
|---|---|
| 后端 list 端点改造可能影响其他 list 消费方（CA 管理、BAR 站点） | 灰度：先在 Account 模块验证，再推广到 CA/BAR |
| Account.vue `loadDetailsInBatches` 删除后，其他页签（"我的申请"、"我的审批"）若间接依赖 detail 接口 | 测试覆盖三个 tab，确认无回归 |
| 后端 list 端点字段增加可能放大响应体（每行多 ~500 字节） | 50 行 = 25KB，可接受；如未来需要可加 `?fields=` 参数 |
| 71 处业务层 catch 改造 PR 太大 | 拆为 3 个 PR：PR-1 后端契约 + Account.vue / PR-2 业务层迁移前 30 处 / PR-3 业务层迁移后 41 处 |
| 其他 agent 已经在改 `accounts.js` / `Account.vue` | 文件锁已 acquire（`.agent-locks/cursor-init.yml`），其他 agent 看到会等 |

---

## 6. Open Questions（执行前必须澄清）

1. **list 端点返回完整 DTO 是否会泄漏其他敏感字段**（不只是 password）？
   - 当前 L3 安全加固时一并拆出的字段是 `password / contactPerson.userId / registerPhone / registerEmail`
   - 前两者仍有脱敏需求，list 端点应返回**脱敏后的 phone/email**（如 `138****1234`）
   - **决策点**：FR-A-01 应明确 list 端点返回 `phoneMasked` / `emailMasked` 字段，而 `phone` / `email` 仅 detail 端点返回

2. **`loadDetailsInBatches` 删除是否影响其他页面**（CA 管理 / BAR 站点 / 我的审批详情）？
   - 需 grep：`grep -rn "loadDetailsInBatches\|getDetail" src/`
   - **决策点**：执行 agent 先跑这个 grep 再决定删/留

3. **71 处业务层 catch 迁移是否真有必要**？
   - 71 处中可能有 30+ 处是"非 API 错误"或"已取 res.msg"的合理用法
   - **决策点**：`audit-existing-429-exposure.mjs` 必须先跑出**真实高风险清单**，再决定迁移范围（不要一刀切）

---

## 7. Execution Plan（autopilot 可执行）

| Phase | Task | Owner | 验证 |
|---|---|---|---|
| Phase 0 | 跑 `grep -rn "loadDetailsInBatches\|getDetail" src/` + `audit-existing-429-exposure.mjs` 给出真实范围 | cursor | 输出"高风险清单" |
| Phase 1 | 新增 `scripts/check-list-endpoint-n1.mjs` + `scripts/audit-existing-429-exposure.mjs` + 升级 `check-429-error-override.mjs`（FR-C-01~03） | cursor | pre-push gate 集成通过 |
| Phase 2 | 更新 `.wiki/pages/engineering-discipline.md` §5.1 + §6.3 + §6.4 + `frontend-pitfalls.md` §12 末尾 | cursor | 案例库 + 规范沉淀完成 |
| Phase 3 | 创建 `specs/035-root-account-429/tasks.md`（autopilot 完整任务清单） | cursor | 与其他 agent 同步入口 |

**Phase 1~3 全部由 cursor agent 完成**（spec + 工具 + 文档沉淀，不动业务代码）。

**Phase 4~7 由 trae 主工作区执行**（后端契约改造 + 前端消费方改造 + 业务层迁移 + 灰度验证），按 RULES.md 四阶段流程（plan → tdd → code-review → refactor-clean）。

---

## 8. 案例沉淀（本次同步更新）

- `.wiki/pages/engineering-discipline.md` §6.3 案例库新增「Account 详情 429」条目标记「6 次反复修 / 根因 1+4+5+7」
- `.wiki/pages/engineering-discipline.md` §5.1 规范表新增：「`/api/*/list` 端点默认必须返回业务所需完整 DTO，敏感字段走单独端点；不允许 N+1」
- `.wiki/pages/frontend-pitfalls.md` §12 末尾新增「根治 vs 防御」段落：「429 防御是治标；N+1 根治才是治本」
- `.wiki/pages/lessons-learned.md` §X 新增 CO-XXX 条目

---

## 9. References

- 工程纪律手册：`/Users/user/xiyu/worktrees/cursor/.wiki/pages/engineering-discipline.md` §1（7 大根因）§3（5 个为什么）§4（SOP）§6（沉淀）
- 前端陷阱：`/Users/user/xiyu/worktrees/cursor/.wiki/pages/frontend-pitfalls.md` §12（业务层 catch 覆盖 429）
- spec 034（症状层）：`/Users/user/xiyu/worktrees/cursor/specs/034-friendly-rate-limit/`
- 关联 commit：`afc11b64e` `f3f4ca6f4` `8a32fe8b3` `c15e1e4e7` `8e9838e17` `30d12aeab`
- pre-push 脚本：`scripts/check-429-error-override.mjs` `scripts/scan-429-catch.mjs` `scripts/scan-load-on-mount-429.mjs`
