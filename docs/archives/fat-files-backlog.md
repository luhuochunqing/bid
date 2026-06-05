# 存量胖文件治理 Backlog

**最后盘点时间**: 2026-04-30
**盘点范围**: 前端 `src/**/*.{vue,js}` 中 > 500 行的文件
**Owner**: 待认领（前端负责人）；季度重盘人 = 当季 owner
**关联门禁**: `npm run check:line-budgets`（配置见 `scripts/line-budget.config.json`，`maxLines=300`）

> 后端胖文件（`backend/src/main/java/`）虽在门禁覆盖范围内，但本 backlog **暂未盘**。详见 [后端盘点缺口](#后端盘点缺口)。

**盘点命令**:
```bash
find src -name "*.vue" -o -name "*.js" | xargs wc -l 2>/dev/null \
  | awk '$1 > 500 && $2 != "total" {print $1, $2}' | sort -rn
```

> 注：90d commits 与盘点日有 ±1~2 天的自然漂移；以重盘当日 `git log --since='90 days ago' --oneline -- <file> | wc -l` 为准。

## 背景

`npm run check:line-budgets` 是 **ratcheting** 门禁：它只拦 **新增** ≤ 300 行 + **历史胖文件不能再长**，但对存量 > 300 行的文件没有主动告警。

真实案例：`src/views/Analytics/Dashboard.vue` 在 2153 行的规模下存活到 2026-04-30（PR #106 才拆完），期间没触发任何 CI 告警。这份 backlog 就是为了**把存量风险显式化**，让它不再隐身。

治理目标：**不要求一次全拆**，而是按 ROI 分 sprint 消化。本 backlog 每季度重新盘点一次。

---

## 当前清单（16 个）

> "90d commits" = 过去 90 天内该文件被修改的 commit 数，反映**热度**；热度越高，拆分 ROI 越大（每次 CR/merge 都受益）。

### 🔴 P0 — 胖 + 高频改动（最该先拆）

| 行数 | 文件 | 90d commits | 拆分切入点 |
|---:|---|---:|---|
| 526 | `src/components/layout/Sidebar.vue` | **27** | 路由菜单配置 + 权限过滤 + 折叠态管理可独立为 composable |
| 608 | `src/components/layout/Header.vue` | 14 | 用户下拉 / 通知 / 全局搜索 / 角色切换可分别抽组件 |
| 568 | `src/views/Login.vue` | 12 | 登录表单 / 凭据提示 / 社交登录 按块拆 |
| 596 | `src/views/Resource/BAR/SiteList.vue` | 11 | 筛选条 + 表格 + 批量操作 + 弹窗（本次已顺手拆 pagination，但主体仍在） |
| 572 | `src/api/modules/knowledge.js` | 9 | API 层，按 endpoint 组（case / template / qualification / score）拆子文件 |
| 510 | `src/api/modules/collaboration.js` | 9 | 同上（comments / mentions / exports / assignments 拆子文件） |

### 🟡 P1 — 中等胖度 + 中频（中等 ROI）

| 行数 | 文件 | 90d commits | 备注 |
|---:|---|---:|---|
| 711 | `src/views/Resource/BAR/SiteDetail.vue` | 6 | 最胖，但改动不高频；建议与 `SiteList.vue` 一起拆成一个域 |
| 563 | `src/views/Resource/Account.vue` | 7 | 账号管理页 |
| 525 | `src/views/Project/List.vue` | 7 | 本次已顺手拆 pagination |
| 635 | `src/components/ai/VersionControl.vue` | 4 | AI 版本控制组件 |
| 584 | `src/components/ai/CollaborationCenter.vue` | 4 | 协作中心 |
| 503 | `src/components/common/TaskBoard.vue` | 4 | 任务看板 |
| 533 | `src/components/ai/MobileCard.vue` | 5 | 移动端 AI 卡片 |

### 🟢 P2 — 稳定胖文件（低 ROI，暂缓）

| 行数 | 文件 | 90d commits | 备注 |
|---:|---|---:|---|
| 700 | `src/components/ai/ConfigDialog.vue` | 2 | 几乎不改 |
| 515 | `src/components/ai/ComplianceCheck.vue` | 3 | 合规检查 |
| 565 | `src/api/trendradar.js` | 1 | 基本没人碰 |

---

## 拆分目标（分两阶段）

为避免对 700+ 行文件一刀切到 ≤ 300 不现实，本 backlog 采用**两阶段目标**：

| 阶段 | 目标行数 | 适用对象 | 期望节奏 |
|---|---|---|---|
| **Stage 1** | ≤ 500 | 所有 P0 / P1 文件，必须先达成 | 1 文件 1 PR |
| **Stage 2** | ≤ 300（与门禁对齐） | Stage 1 完成后再推进 | 1 文件可拆 2~3 PR |

> Stage 1 的价值：把"门禁不能再长"的天花板从 ~600 下压到 ~500，立刻减小未来增长空间。
> Stage 2 才追平 `maxLines=300` 的门禁配额，作为长期目标。

---

## 交付窗口节奏限制（硬约束）

**当前交付窗**：2026-04-27 启动准备 → 2026-07-10 正式上线 → ~10-10 试运行结束。

| 时段 | 允许的拆分动作 |
|---|---|
| **04-27 ~ 07-10（交付窗内）** | ❌ 不开集中拆分 sprint；✅ 仅允许"顺手拆"（写新功能/改 bug 时碰到就拆，且 PR 主线不是"为了拆而拆"） |
| **07-10 ~ 10-10（试运行期）** | ✅ 集中执行 Sprint 1~3（高频路径优先，避开 launch 关键周） |
| **09-25 之后** | ✅ Sprint 4~5 + 季度重盘 |

> 理由：Sprint 1（Sidebar/Header/Login）正好是 launch 前合 PR 最多的高频路径，集中大拆会与功能 PR 抢 merge 队列、增加 conflict 风险。

---

## 建议 Sprint 节奏

> 排期受 [交付窗口节奏限制](#交付窗口节奏限制硬约束) 约束。每个 Sprint 开一个 tracking issue。
> 下文 "目标 ≤ 300" 指 **Stage 2** 终态；**Stage 1** 先以 ≤ 500 收口，详见 [拆分目标](#拆分目标分两阶段)。

### Sprint 1 — 布局基建层（~1700 行，P0 核心）
**期望执行时段**：试运行期前段（07 月）
- [ ] `Sidebar.vue`（526 → Stage 1 ≤ 500，Stage 2 ≤ 300）
- [ ] `Header.vue`（608 → Stage 1 ≤ 500，Stage 2 ≤ 300）
- [ ] `Login.vue`（568 → Stage 1 ≤ 500，Stage 2 ≤ 300）

**理由**：改动最频繁的三个文件；拆了立刻受益；全站可见。

### Sprint 2 — 资产管理（BAR）模块（~1310 行）
**期望执行时段**：试运行期中段（08 月）
- [ ] `SiteList.vue`（596 → Stage 1 ≤ 500，Stage 2 ≤ 300）
- [ ] `SiteDetail.vue`（711 → **建议拆 2~3 个 PR**：先抽弹窗 + 表格区到 ≤ 500，再下钻 composable 到 ≤ 300）

**理由**：两兄弟同模块，可抽共享子组件 + composable；一次拆完整域。

### Sprint 3 — API 模块层（~1650 行）
**期望执行时段**：试运行期中段（08 月）
- [ ] `api/modules/knowledge.js`（572 → ≤ 300）
- [ ] `api/modules/collaboration.js`（510 → ≤ 300）
- [ ] `api/trendradar.js`（565 → ≤ 300）— 顺手，几乎没人碰

**理由**：API 层拆分模式固定（按 endpoint 分文件），机械工作，CR 快；可一次到 Stage 2。

### Sprint 4 — 视图杂项（~1500 行）
**期望执行时段**：09-25 之后
- [ ] `views/Project/List.vue`（525 → Stage 1 ≤ 500，Stage 2 ≤ 300）
- [ ] `views/Resource/Account.vue`（563 → Stage 1 ≤ 500，Stage 2 ≤ 300）
- [ ] `components/common/TaskBoard.vue`（503 → Stage 1 ≤ 500，Stage 2 ≤ 300）

### Sprint 5（可选）— AI 组件族（~3000 行）
**期望执行时段**：09-25 之后，待 AI 子系统稳定再动
- [ ] `components/ai/VersionControl.vue`
- [ ] `components/ai/CollaborationCenter.vue`
- [ ] `components/ai/MobileCard.vue`
- [ ] `components/ai/ConfigDialog.vue`
- [ ] `components/ai/ComplianceCheck.vue`

**理由**：构成一个 AI UI 子系统，可以统一抽 `ai-core/` composable 层。

---

## 拆分 Playbook（参考 PR #106）

以 Editor/Create/Dashboard 三大拆分为范本：

1. **先读完整个目标文件**，识别 3 类边界：
   - **UI 区块**（Header / Panel / Dialog）→ 抽子组件
   - **业务逻辑块**（数据加载 / 过滤 / 下钻）→ 抽 `composables/`
   - **纯函数**（formatter / option builder）→ 抽 `utils/`
2. **主壳保留路由挂载点**，只做"排布 + 连线"
3. **props/emits 严格契约**，父子之间用 `v-model` 或 `defineModel`，不让子组件直接改 prop
4. **每抽一个，跑一次** `npm run lint && npm run build && npm run test:unit`
5. **按 ≤ 300 行的配额写**，CSS 超 200 行考虑独立 scoped 样式或移给子组件
6. **风险点优先处理**：有 URL 同步 / 跨组件事件 / 第三方库挂载的区块先抽，避免最后留坑

**自动化门禁**：`check:line-budgets` 仍会拦"新增文件 > 300" + "历史胖文件再长"，拆的过程中自动保护。

---

## 后续动作

- [ ] **季度重盘**：每季度第一周对 `src/**/*.{vue,js}` 重跑盘点命令，更新行数与 90d commit；当季 owner 负责
- [ ] **CI warning**：在 `check:line-budgets` 增加"存量文件 > 500 行"的 warning（不 fail），让每个相关 PR 都看到（追踪：待开 issue）
- [ ] **后端盘点**：补 `backend/src/main/java/` 的胖文件清单（详见下节）
- [x] 新增 > 300 行的胖文件触发告警 — 已由 `check:line-budgets` 覆盖
- Sprint 完成后打勾 `[x]`；全部完成 + 季度盘点连续 2 次为空 → 废弃本文档

---

## 后端盘点缺口

`scripts/line-budget.config.json` 的 `includePrefixes` 包含 `backend/src/main/java/`，意味着后端 Java 文件**已经在 ratcheting 门禁覆盖范围内**，但本 backlog 暂未盘点其存量。

**待办**：
- [ ] 跑后端盘点命令：
  ```bash
  find backend/src/main/java -name "*.java" | xargs wc -l 2>/dev/null \
    | awk '$1 > 500 && $2 != "total" {print $1, $2}' | sort -rn
  ```
- [ ] 按"行数 × 90d commits"打 P0/P1/P2 标签，追加到本文档（或拆出 `backend-fat-files-backlog.md`）
- [ ] 后端 owner = 后端负责人；与前端共用本文档的 Sprint 节奏 / 交付窗口约束
