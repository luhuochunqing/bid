# Implementation Plan: Account 详情 429 反复修 — 根治

**Branch**: `agent/cursor/root-account-429` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)
**Author**: agent/cursor (spec + 工具沉淀)

## Summary

本次任务分两阶段：

**阶段 A（cursor agent 本次执行）**：沉淀 spec + 新增/升级 3 个 pre-push 脚本 + 更新 .wiki 案例库
- 新增 `scripts/check-list-endpoint-n1.mjs` — 防 N+1 list-detail 反模式
- 新增 `scripts/audit-existing-429-exposure.mjs` — 审计存量 71 处业务层 ElMessage.error 风险
- 升级 `scripts/check-429-error-override.mjs` — 增加 `--audit-existing` flag
- 更新 `.wiki/pages/engineering-discipline.md` §5.1 + §6.3 + §6.4
- 更新 `.wiki/pages/frontend-pitfalls.md` §12 末尾新增"根治 vs 防御"

**阶段 B（trae 主工作区后续执行）**：根治 N+1 + 全局业务层 catch 治理
- 后端 `getList` 端点契约改造（FR-A-01~04）
- 前端 Account.vue 删除 N+1（FR-B-01~03）
- 71 处业务层 catch 迁移（FR-B-04，分 3 个 PR）

本次改动**不动限流阈值**，不引入新框架，不破坏现有 429 文案协议。

## Technical Context

- **Language/Version**: Vue 3 + Vite 5 + Element Plus | Java 21 + Spring Boot 3.2
- **Primary Dependencies**: Axios (frontend), Spring Web / Servlet Filter / Redis (backend)
- **Storage**: N/A for tooling; MySQL 8.0 for backend contract
- **Testing**: Jest/Vitest (frontend), JUnit 5 + Mockito (backend)
- **Performance Goals**: pre-push 脚本运行 < 5 秒
- **Constraints**: 单文件硬上限 300 行；新增工具脚本必须无框架依赖

## Constitution Check

| Principle | Status | Notes |
|---|---|---|
| I. FP-Java Architecture | ✅ 合规 | 不改 Java 业务逻辑；本阶段仅沉淀工具与文档 |
| II. Real-API Only | ✅ 合规 | pre-push 脚本扫描真实代码 |
| III. Test-Driven Development | ✅ 合规 | 新增脚本配套测试用例 |
| IV. Split-First & Simplicity | ✅ 合规 | 3 个脚本各司其职，单文件 < 200 行 |
| V. OSS Integration | N/A | 不涉及 |
| VI. Authorization Unification | N/A | 不涉及 |
| VII. Defensive Collection & Graceful Degradation | ✅ 合规 | 扫描脚本对异常文件跳过，不阻断流程 |
| VIII. Boring Proven Patterns | ✅ 合规 | 用 ripgrep + AST 简单正则 + plain Node fs API |

## Project Structure (本阶段新增文件)

```
scripts/
├── check-list-endpoint-n1.mjs        # NEW (FR-C-01)
├── audit-existing-429-exposure.mjs   # NEW (FR-C-03)
├── check-429-error-override.mjs      # MODIFIED (FR-C-02) 加 --audit-existing
└── *.spec.js                         # NEW 测试

specs/035-root-account-429/
├── spec.md                            # ✅ 已完成
├── plan.md                            # ⬅ 本文
├── tasks.md                           # NEXT
└── checklists/requirements.md         # NEXT

.wiki/pages/
├── engineering-discipline.md          # MODIFIED (§5.1 / §6.3 / §6.4)
└── frontend-pitfalls.md               # MODIFIED (§12 末尾新增)
```

## Implementation Design

### 工具 1：`scripts/check-list-endpoint-n1.mjs`（FR-C-01）

**目标**：检测"list 端点 + N+1 getDetail"反模式

**实现要点**：
- 输入：直接传目录（默认 `src/`），不再依赖 git diff（pre-push 时常有未 commit 的修改）
- 扫描三类反模式：
  1. `Promise.all(...map(row => *.getDetail(...)))`
  2. 函数名匹配 `loadDetailsInBatches` / `loadDetailsSequentially` / `loadAllDetails` / `fetchDetailsInLoop`
  3. `await list.forEach` 内调用 `getDetail`
- 输出：violation 文件 + 行号 + 修复建议

### 工具 2：`scripts/audit-existing-429-exposure.mjs`（FR-C-03）

**目标**：把存量 71 处业务层 `ElMessage.error` 风险分成高/中/低三档

**实现要点**：
- 输入：`src/` 全量扫描
- 风险评级规则：
  - **HIGH**: catch 块在 `onMounted`/`setup`/`created` 路径 + 调用 `*.getList()` + 有 `ElMessage.error`
  - **MEDIUM**: catch 块在任意 `async function` + 调用 `*.getList()` + 有 `ElMessage.error(e.message || ...)`
  - **LOW**: 其他 API 调用 + 仅有 `ElMessage.error('fallback')` 字符串
- 输出：JSON 报告（`--json`）或控制台表格（默认）

### 工具 3：升级 `scripts/check-429-error-override.mjs`（FR-C-02）

**目标**：在原"拦截新增 diff"基础上，加 `--audit-existing` 模式跑存量扫描

**实现要点**：
- 不破坏现有行为（默认仍是"拦截新增 diff"）
- 加 `--audit-existing` 参数：跑存量扫描但只 warning 不阻断
- 输出 JSON 报告供 CI 消费

## Testing Strategy

| 脚本 | 测试 | 验证 |
|---|---|---|
| `check-list-endpoint-n1.mjs` | `check-list-endpoint-n1.spec.js` | fixture：含 N+1 反模式 / 不含 / 边缘情况 |
| `audit-existing-429-exposure.mjs` | `audit-existing-429-exposure.spec.js` | fixture：HIGH/MEDIUM/LOW 三档各 1 个样本 |
| `check-429-error-override.mjs` (升级) | 已有 `*.spec.js` 不变 | 新加 `--audit-existing` 分支测试 |

跑测试命令：
```bash
cd /Users/user/xiyu/worktrees/cursor
npm run test:unit -- --run scripts/
```

## Complexity Tracking

无 Constitution 违规。

## Reference

- spec: [spec.md](./spec.md)
- 工程纪律: `.wiki/pages/engineering-discipline.md`
- 前端陷阱: `.wiki/pages/frontend-pitfalls.md`
- spec 034: `specs/034-friendly-rate-limit/`
- 现有 429 检测脚本：`scripts/scan-429-catch.mjs` `scripts/scan-load-on-mount-429.mjs` `scripts/check-429-error-override.mjs`
