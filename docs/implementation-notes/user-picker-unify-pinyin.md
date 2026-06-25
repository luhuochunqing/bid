# 选人控件统一 + 拼音搜索恢复 — 实现说明

**分支**: `agent/zcode/user-picker-unify-pinyin`
**日期**: 2026-06-25
**关联 spec**: `.specify/specs/021-user-picker-event-sync/`（mimo 的活跃 spec，本任务为其子集 + 增量）

## 用户需求

1. 6 个选人入口（标讯分配/标讯转派/任务执行人/投标负责人/投标辅助人员/标书审核人）统一为同一控件，不重复造轮子。
2. 选人支持模糊搜索：姓名、**姓名拼音**、工号。
3. **测试 Flyway**（之前启动失败过）。

## 决策记录

### 决策 1：修 V1096 而非新增兜底迁移（用户拍板）

**根因**：`V1096__add_users_employee_number_pinyin.sql` 的 `AFTER full_name_pinyin` 引用了已被删除的列，Flyway 执行报 `Unknown column 'full_name_pinyin'`，应用启动失败。实测复现。主 DB 卡在 V1095，V1096/1097/1098 均未执行。

**为什么直接改 V1096 而不新增 V1099 兜底**：Flyway 顺序执行，V1096 卡死后到不了 V1099，必须修 V1096 本身。主 DB 的 `flyway_schema_history` 无 V1096 记录，改 checksum 不会触发本地 mismatch。

**风险**：若其他环境曾跑过原版 V1096，改后 checksum 不符，需 `flyway repair`。鉴于这是新迁移且全公司主 DB 都未跑过，判断风险低。如遇此情况：`flyway repair` 即可。

### 决策 2：逆转 !1101，恢复拼音搜索（用户拍板）

**背景**：拼音搜索今天（6-25）在 main 上被反复加/删：
- `407587394`（19:15）加了拼音搜索
- `!1101`（20:23）以"V1097 已删 full_name_pinyin 列"为由又删了

**冲突信号**：锁文件显示 `agent/zcode/fix-v1097-remove-code-refs-to-dropped-column` 分支的清理工作还在活跃（锁到 6-27）。

**决策**：用户明确要拼音搜索能力。本任务 B1 重新加回 `full_name_pinyin` 列 + `User.fullNamePinyin` 字段 + `UserNamePinyinBackfillRunner` + 搜索 SQL。这逆转了 !1101 的清理方向。**若 !1101 的清理分支后续提 PR，需协调以避免来回覆盖。** 产品需求优先于代码清理洁癖。

### 决策 3：拼音存量回填用启动 runner（用户拍板）

MySQL 无法在迁移脚本里调用 pinyin4j 做汉字转拼音。三个选项中选了"启动时自动回填"：
- 新建/修改用户由 `User` 的 `@PrePersist/@PreUpdate` 自动算（镜像现有 `employeeNumberPinyin` 模式）
- 存量用户（`full_name_pinyin IS NULL`）由 `UserNamePinyinBackfillRunner`（bootstrap 层 `ApplicationRunner`）在启动时幂等回填

`UserNamePinyinBackfillRunner` 放在 `bootstrap` 包，注入 `UserRepository`，规避 `ArchitectureTest RULE 9`（config 不能依赖 service/repository）。已通过 `FPJavaArchitectureTest` + `MaintainabilityArchitectureTest`。

### 决策 4：ESCAPE 子句最终不加回

最初想给 `searchActiveUsers` 的 LIKE 加显式 `ESCAPE '\\'` 子句（让 `escapeLike` 的反斜杠转义更规范）。但测试发现 H2（单测用）的 ESCAPE 语法和 MySQL 不同，加了反而破坏 H2 测试兼容性。MySQL 默认认 `\` 为转义符，原 SQL 靠默认行为工作正常。**结论：不加 ESCAPE，保持 H2/MySQL 双兼容。**

## 6 个入口统一情况

| # | 入口 | 文件 | 改动前 | 改动后 |
|---|---|---|---|---|
| 1 | 标讯分配 | `AssignDialog.vue` | 已用 UserPicker | 不动 |
| 2 | 标讯转派 | `List.vue` / `DetailPage.vue` | 已用 UserPicker | 不动 |
| 3 | 任务执行人 | `TaskKanban.vue` 等 | 已用 UserPicker | 不动 |
| 4 | 投标负责人 | `InitiationStage.vue:136` | 已用 UserPicker | 不动 |
| 5 | **投标辅助人员** | `InitiationStage.vue:138` | 裸 el-select + searchAssistant | **改用 UserPicker + roleFilter="bid-Team"** |
| 6 | **标书审核人** | `DraftingStage.vue:47` | 裸 el-select + searchReviewer | **改用 UserPicker + excludeIds（排除项目负责人/团队）** |

### UserPicker 新增能力

为收口偏离实现，给统一组件补了两个 prop（避免"统一后丢功能"）：
- `excludeIds: Array` — 从下拉排除指定 id（审核人场景：排除项目经理/负责人/辅助/团队成员，避免自审）
- `roleFilter: String` — 按 roleCode 前端过滤候选（辅助人员场景：仅展示 bid-Team 角色）

过滤在 `selectOptions` computed 做（合并 initialOptions + 搜索结果之后），因为 `usersApi.search` 不支持 roleCode 过滤。

### 遗留：InitiationApprovalCard.vue 手敲 ID

`InitiationApprovalCard.vue:7-10`（立项审批）的投标负责人/辅助人员仍是 `<el-input>` 让用户手敲数字 ID。这是最糟的偏离，但属于另一个审批流程的卡片，改动面外溢。**本次未动，记录为遗留**。

## 验证证据

### Flyway（用户最关心）
- V1096（修复后）+ V1097 + V1098 + V1099 逐条在主 DB 上手动执行，全部成功
- 主 DB users 表最终有 `full_name_pinyin` + `employee_number_pinyin` 两列 + `idx_users_full_name_pinyin` 索引
- 端到端拼音搜索实测：搜 `zhangjingli`/`zhang`/`张经理` 三种方式都命中"张经理" ✅

⚠️ **未做的框架级验证**：主 DB 的 `flyway_schema_history` 表里 V1096-V1099 还没记录（我是手动 mysql 执行的，不是 Flyway 框架执行）。trae 主工作区后端跑的是今早 9 点的旧 jar（不含本次改动）。要让 Flyway 框架正式补登 history + runner 回填存量数据，需重新构建 jar 并重启 trae 后端。**这一步影响主工作区，留给用户决定何时执行。** 迁移脚本都是幂等的，重启时 Flyway 会安全补登。

### 后端测试
- `UserRepositorySearchTest`: 4 绿（含新增 `MatchesFullNamePinyin` + `MatchesPinyinPrefix`）
- `UserSearchServiceTest`: 9 绿
- `FPJavaArchitectureTest` + `MaintainabilityArchitectureTest`: 全绿
- `mvn compile`: 通过

### 前端测试 + 构建
- `UserPicker.spec.js`: 10 绿（含新增 `excludeIds` + `roleFilter` 用例）
- `npm run build`: 全绿（含 front-data-boundaries / doc-governance / line-budgets / vite build）

## Pre-existing 问题（非本次引入）

1. **`ArchitectureTest` RULE 17 失败**：`ProjectStageService` 类级 `@Transactional` + `requestTransition` 方法 `@Auditable`。在干净的 origin/main 上本就失败（已 stash 验证）。CLAUDE.md 声称的"全绿基线"已漂移。**与本次改动无关**，未触碰该类。
2. **`check-userpicker-mode.mjs` 缺维护声明**：导致 doc-governance 门禁失败。顺手补了一行维护声明（治理性修复）。
3. **InitiationStage.vue 的 `searchAssistant`/`searchLeader`/`roleOptions` 现为死代码**：template 改用 UserPicker 后不再调用，但被 `defineExpose` 暴露可能有外部引用。本次保留未删，标记为可清理。

## 与 mimo spec 021 的边界

- 本任务交付 spec 021 的 T044（辅助人员）+ T045（审核人）— 无冲突，是补位
- spec 021 的 FR-013 只写"姓名/用户名匹配"，**没覆盖拼音** — 本任务 B1 是增量增强
- 本任务**不动**：事件库 SDK（spec US2）、候选人 API 合并（spec US3）、其余 17 处选人迁移

## 涉及文件

**后端**: V1096（改）、V1099 + U1099（新）、User.java、UserRepository.java、UserNamePinyinBackfillRunner.java（新）、UserRepositorySearchTest.java
**前端**: UserPicker.vue、UserPicker.spec.js、DraftingStage.vue、InitiationStage.vue
**治理**: check-userpicker-mode.mjs（补维护声明）、.agent-locks/zcode-user-picker-unify-pinyin.yml（新）
