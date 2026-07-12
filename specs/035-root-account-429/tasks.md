# Tasks: Account 详情 429 反复修 — 根治

**Input**: Design documents from `/specs/035-root-account-429/`
**Prerequisites**: plan.md (required), spec.md (required)
**Strategy**: 本任务分两阶段 — 阶段 A (cursor) 仅沉淀工具与文档；阶段 B (trae) 改业务代码

---

## Phase A: 工具与文档沉淀（cursor agent 本次完成）

### Phase A.1 — 新增 pre-push 脚本（FR-C-01, FR-C-02, FR-C-03）

- [ ] **T001** [P] 创建 `scripts/check-list-endpoint-n1.mjs`
  - 检测 `Promise.all(...map(row => *.getDetail(...)))` 反模式
  - 检测 `loadDetailsInBatches` 函数名
  - 输出 violation 文件 + 行号 + 修复建议
  - 配套测试：`scripts/check-list-endpoint-n1.spec.js`
  - **Checkpoint**: `node scripts/check-list-endpoint-n1.mjs src/` 跑通，无误报

- [ ] **T002** [P] 创建 `scripts/audit-existing-429-exposure.mjs`
  - 三档风险评级（HIGH/MEDIUM/LOW）
  - JSON 报告输出到 stdout
  - 配套测试 fixture
  - **Checkpoint**: 跑出当前 71 处存量风险分布

- [ ] **T003** 升级 `scripts/check-429-error-override.mjs`
  - 加 `--audit-existing` 参数
  - 默认行为不变（仍拦截新增 diff）
  - 加 `--audit-existing` 分支测试
  - **Checkpoint**: pre-push gate 集成通过

### Phase A.2 — 更新 .wiki 案例库（§8 沉淀）

- [ ] **T004** [P] 更新 `.wiki/pages/engineering-discipline.md`
  - §5.1 规范表新增：「`/api/*/list` 端点默认必须返回完整 DTO，敏感字段走单独端点；不允许 N+1」
  - §6.3 案例库新增：「Account 详情 429 反复修（6 次 / 根因 1+4+5+7）」
  - §6.4 pre-push 脚本索引新增：`scripts/check-list-endpoint-n1.mjs` + `scripts/audit-existing-429-exposure.mjs`
  - **Checkpoint**: 链接 + 引用规范

- [ ] **T005** [P] 更新 `.wiki/pages/frontend-pitfalls.md`
  - §12 末尾新增"根治 vs 防御"段落
  - 关键论点：「429 防御是治标；N+1 根治才是治本」
  - **Checkpoint**: 与 §12 原内容连贯

### Phase A.3 — 验证与提交

- [ ] **T006** [P] 跑 lint/build/test 验证
  - `npm run check:line-budgets` — 不超 300 行
  - `npm run check:front-data-boundaries` — 无新增违规
  - `node scripts/check-list-endpoint-n1.mjs src/` — 跑通
  - `node scripts/audit-existing-429-exposure.mjs` — 跑出报告
  - `node scripts/check-429-error-override.mjs --audit-existing src/` — 跑通

- [ ] **T007** 提交 + push + 创建 Gitee PR
  - commit message：`feat(tooling+wiki): spec-035 root-account-429 沉淀 — 3 个 pre-push 脚本 + .wiki 案例库更新`
  - push 到 `agent/cursor/root-account-429`
  - 创建 Gitee PR，目标 `main`

---

## Phase B: 业务代码改造（trae 主工作区后续执行）

> ⚠️ **本阶段不在 cursor 工作区执行**。cursor 仅沉淀工具 + spec。
> trae 主工作区拿到本 spec 后，按以下 Phase 推进。

### Phase B.0 — 范围摸底（trae）

- [ ] **T101** [P] 跑 `grep -rn "loadDetailsInBatches\|getDetail" src/` 列出所有 N+1 反模式
- [ ] **T102** [P] 跑 `node scripts/audit-existing-429-exposure.mjs` 输出 JSON 报告
- [ ] **T103** [P] 跑 `node scripts/check-429-error-override.mjs --audit-existing src/` 输出报告

**Checkpoint**: 拿到完整"高风险清单 + N+1 出现点列表"，确定 PR 拆分方案

### Phase B.1 — 后端契约改造（FR-A-01~04）

- [ ] **T201** 后端 `PlatformAccountController.list()` 修改 DTO 输出（含完整业务字段）
- [ ] **T202** 后端 `PlatformAccountControllerTest#list_doesNotIncludePasswordField` 新增
- [ ] **T203** 后端契约文档 `backend/docs/api-contract-platform-account.md` 新增
- [ ] **T204** 数据库 migration（如有 schema 变更）：`bash scripts/new-migration.sh extend_account_list_dto`

**Checkpoint**: `mvn test -Dtest=PlatformAccountControllerTest` 全绿；契约文档发布

### Phase B.2 — 前端 Account.vue 简化（FR-B-01~03）

- [ ] **T301** `src/api/modules/resources/accounts.js` 简化 `getList` 响应处理
- [ ] **T302** `src/views/Resource/Account.vue` 删除 `loadDetailsInBatches` + `DETAIL_CONCURRENCY` + `loadAccountDetail` catch 块
- [ ] **T303** `src/views/Resource/__tests__/Account.spec.js` 重写为根因行为测试
- [ ] **T304** 跑前端测试 `npm run test:unit -- --run src/views/Resource/__tests__/Account.spec.js`
- [ ] **T305** 跑 pre-push gate 全部门禁

**Checkpoint**: Account 页面网络请求数从 51 → 1，DevTools Network 验证

### Phase B.3 — 业务层 catch 迁移（FR-B-04，分 3 个 PR）

- [ ] **T401** PR-A: HIGH 风险 30 处迁移到 `notifyErrorUnlessRateLimit`
- [ ] **T402** PR-B: MEDIUM 风险 30 处迁移
- [ ] **T403** PR-C: LOW 风险 11 处迁移（或加 `// SAFE: 非 API 错误` 豁免注释）
- [ ] **T404** 每个 PR 跑 `node scripts/scan-429-catch.mjs src/` 验证数量下降

**Checkpoint**: `scan-429-catch.mjs` 输出 ≤ 10 处

### Phase B.4 — 灰度与监控（trae）

- [ ] **T501** 后端 list 端点灰度：先在 Account 模块启用完整 DTO
- [ ] **T502** 监控：429 响应计数 / 用户反馈
- [ ] **T503** 7 天观察期：DR 群 / Sentry / Prometheus 数据
- [ ] **T504** 全量推 Account 模块成功后，推广到 CA 管理 / BAR 站点

**Checkpoint**: 7 天观察期内 Account 页面 429 响应计数 = 0

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase A** (cursor): 独立，可立即开始
- **Phase B.0** (trae): 依赖 Phase A 的工具（audit-existing-429-exposure.mjs）
- **Phase B.1** (trae): 依赖 Phase B.0 的范围摸底
- **Phase B.2** (trae): 依赖 Phase B.1 的后端契约
- **Phase B.3** (trae): 可与 Phase B.2 并行
- **Phase B.4** (trae): 依赖 Phase B.1~3 全完成

### Parallel Opportunities

- Phase A.1 三个脚本（T001/T002/T003）可并行
- Phase A.2 两个 wiki 更新（T004/T005）可并行
- Phase B.3 三个 PR（T401/T402/T403）可由不同 agent 并行

---

## Notes

- 本任务遵循 RULES.md 四阶段流程（plan → tdd → code-review → refactor-clean）
- 阶段 A 不改业务代码，仅沉淀工具与文档
- 阶段 B 必须有真实环境验证（DevTools Network 录制 + Sentry 429 计数对比）
- 所有 pre-push 脚本必须跑通：`bash scripts/pre-push-gate.sh`
- 严禁 `--no-verify` 绕过门禁（CLAUDE.md §5）
