# Tasks: 消除 @PreAuthorize hasAnyRole 双轨制技术债

**Input**: Design documents from `/specs/024-preauthorize-unification/`（plan.md, spec.md, research.md, quickstart.md）

**Prerequisites**: plan.md (required), spec.md (required), research.md (decisions), quickstart.md (验证清单)

**Tests**: 本特性遵循 Constitution III (TDD)，所有改动配套测试任务。Red→Green 强制。

**Organization**: 按 user story 组织（US1=P1 立即修 / US2=P2 架构守卫 / US3=P3 分批迁移），P3 内部按 research.md 决策 3 的 7 个批次细分。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: US1 / US2 / US3（US3 内部批次用 [US3-B1] 等标注）
- 含精确文件路径

## Path Conventions

- 后端：`backend/src/main/java/com/xiyu/bid/`、`backend/src/test/java/com/xiyu/bid/`
- 迁移脚本：`backend/src/main/resources/db/migration-mysql/`

---

## Phase 1: Setup（前置准备）

**Purpose**: 实现前的任务分支与基线确认

- [ ] T001 在主工作区运行 `scripts/agent-start-task.sh zcode preauthorize-unification origin/main --in-place` 创建正式任务分支（spec/plan/tasks 文档已在 agent/zcode-init 起草，实现阶段切到正式分支）
- [ ] T002 [P] 确认当前 hasAnyRole 使用点基线：运行 `grep -rn "@PreAuthorize.*hasAnyRole\|@PreAuthorize.*hasRole" backend/src/main/java --include="*.java" | grep -v test | wc -l` 记录基线数（预期 177），写入 implementation-notes.md

---

## Phase 2: Foundational（P2 守卫先建 — TDD Red 阶段）

**Purpose**: ArchitectureTest 守卫必须先就位，作为后续所有迁移的验证基础

**⚠️ CRITICAL**: P1/P3 所有迁移任务的"豁免清单同步删减"都依赖此守卫。先建守卫，P1 修复才能被守卫正确豁免 + 验证。

- [ ] T003 [P] 在 `backend/src/test/java/com/xiyu/bid/ArchitectureTest.java` 新增守卫规则 `controllers_must_not_use_role_enumeration_auth`：用 ArchUnit `methods().that().areAnnotatedWith(PreAuthorize.class).should(...)` + 自定义 `DescribedPredicate<JavaMethod>` 扫描注解 SpEL 值，禁止包含 `hasAnyRole` 或 `hasRole`。规则含 `.because("Constitution VI: ...")`
- [ ] T004 [P] 在 ArchitectureTest 中定义豁免清单常量 `HASANYROLE_EXEMPT_CONTROLLERS`（Set<String>，含当前所有 35 个使用 hasAnyRole/hasRole 的 Controller 全限定名，从 T002 基线生成）。守卫规则用 `orShouldBeInExemptionList` 跳过清单内 Controller
- [ ] T005 实现豁免清单数量一致性自验：新增辅助测试方法，运行时 `grep` 实际使用点数，断言 `== HASANYROLE_EXEMPT_CONTROLLERS.size()`。**Red 阶段**：先故意让清单为空，运行 `mvn test -Dtest=ArchitectureTest` 应失败（实际有 177 处但清单为 0）
- [ ] T006 [P] 补全豁免清单至 177 处（35 个 Controller），重跑 `mvn test -Dtest=ArchitectureTest` 应通过（Green）。记录到 implementation-notes.md

**Checkpoint**: 守卫就位，177 处全在豁免清单内，ArchitectureTest 全绿。后续每迁一个 Controller，从清单删一行，数量自验会强制同步。

---

## Phase 3: User Story 1 - 跨部门协同人员能正常使用任务表单 (Priority: P1) 🎯 MVP

**Goal**: 修复 TaskExtendedFieldController 的 hasAnyRole 过度收紧，bid-otherDept/bid-administration 角色访问 GET /api/task-extended-fields 返回 200

**Independent Test**: bid-otherDept 角色调用 GET /api/task-extended-fields → 200

### Tests for User Story 1（TDD Red）

- [ ] T007 [P] [US1] 在 `backend/src/test/java/com/xiyu/bid/task/controller/TaskExtendedFieldSecurityTest.java` 新增测试：`bidOtherDept_canListExtendedFields`（@WithMockUser(roles="BID_OTHERDEPT") 调 GET → expect 200）。**Red 阶段**：当前代码返回 403，测试应失败
- [ ] T008 [P] [US1] 同文件新增 `bidAdministration_canListExtendedFields`（@WithMockUser(roles="BID_ADMINISTRATION") → expect 200，Red 失败）
- [ ] T009 [P] [US1] 同文件新增 `anonymous_cannotListExtendedFields`（无认证 → expect 401，验证仍需登录，应通过）

### Implementation for User Story 1

- [ ] T010 [US1] 在 `backend/src/main/java/com/xiyu/bid/task/controller/TaskExtendedFieldController.java` 删除 `list()` 方法第 40 行的方法级 `@PreAuthorize("hasAnyRole('ADMIN',...)")` 注解（保留类级 `@PreAuthorize("isAuthenticated()")`）
- [ ] T011 [US1] 从 ArchitectureTest 的 `HASANYROLE_EXEMPT_CONTROLLERS` 清单删除 `com.xiyu.bid.task.controller.TaskExtendedFieldController` 条目（数量自验会强制从 177→176）
- [ ] T012 [US1] 重跑 T007-T009 测试 → Green（200）。重跑 `mvn test -Dtest=ArchitectureTest` → 通过（豁免清单与实际一致）

**Checkpoint**: P1 生产故障修复，bid-otherDept 可访问接口。可独立部署。

---

## Phase 4: User Story 2 - 架构守卫完整生效 (Priority: P2)

**Goal**: 验证守卫的"禁止新增 hasAnyRole"能力，确保债务零增长

### Tests for User Story 2

- [ ] T013 [P] [US2] 在 ArchitectureTest 新增负向验证测试：构造一个临时测试 Controller（或用 ArchUnit 的 `JavaClasses` 自定义导入）标注 `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")` 且不在豁免清单内，运行守卫应失败。验证后删除临时代码
- [ ] T014 [P] [US2] 补 `RoleProfileCatalogTest` 断言：确认权限键常量（如 BIDDING_VIEW 等）已注册，为 P3 迁移做权限键可用性预检

### Implementation for User Story 2

- [ ] T015 [US2] 完善守卫规则的错误信息：失败时输出明确提示 `"禁止 hasAnyRole/hasRole，请改用 hasAuthority('<permissionKey>') 或 isAuthenticated()，参见 Constitution VI 与 specs/024-preauthorize-unification"`
- [ ] T016 [US2] 在 `docs/lessons/lessons-learned.md` 新增 §31（或续编）：沉淀"hasAnyRole 双轨制"根因与 ArchitectureTest 守卫范式，关联本次 PR

**Checkpoint**: 守卫具备"禁止新增 + 豁免清单一致性自验"双重能力，债务零增长防线就位。

---

## Phase 5-11: User Story 3 - 分批迁移 (Priority: P3)

**Goal**: 177 处 → 0，按 research.md 决策 3 的 7 个批次推进

**每批通用模式（适用于 Phase 5-11 所有批次）**：
1. 迁移 Controller 注解（hasAnyRole → hasAuthority + RoleProfileCatalog 常量，或类级 isAuthenticated）
2. 若缺权限键：RoleProfileCatalog 注册 + Flyway V/U 迁移
3. 补 SecurityTest 回归（应有权限角色 200 + 无权限角色 403）
4. 从 ArchitectureTest 豁免清单删除该 Controller 条目（数量自验强制同步）
5. PR 描述附 grep 前后对比

### Phase 5: 批次 1 — task 模块收尾 [US3-B1]

- [ ] T017 [P] [US3-B1] 迁移 `backend/src/main/java/com/xiyu/bid/task/controller/TaskController.java:199` 的 `@PreAuthorize("hasRole('ADMIN')")` → 评估是否需 hasAuthority('task.admin') 或保留类级 isAuthenticated（按语义）
- [ ] T018 [US3-B1] 从豁免清单删除 TaskController，重跑 ArchitectureTest 验证一致性

### Phase 6: 批次 2 — casework 模块（收尾 CO-452/466） [US3-B2]

- [ ] T019 [P] [US3-B2] 迁移 `backend/src/main/java/com/xiyu/bid/casework/controller/ProjectArchiveController.java` 类级（L47）+ 方法级（L160/192/235）共 4 处 `hasAnyRole('ADMIN','MANAGER')` → `hasAuthority('project')` 或对应权限键（对齐 CO-466 已迁移的 export 方法风格）
- [ ] T020 [P] [US3-B2] 迁移 `CaseController.java`（L57/95/103/143）和 `KnowledgeCaseController.java`（L109/115/121/135）共 8 处 → hasAuthority
- [ ] T021 [US3-B2] 补 `CaseworkSecurityTest`（或现有测试类）回归：bid-Team/bid-otherDept 访问列表 200、写接口按权限键 200/403
- [ ] T022 [US3-B2] 从豁免清单删除 3 个 casework Controller，ArchitectureTest 一致性验证

### Phase 7: 批次 3 — knowledge 知识库验证 [US3-B3]

- [ ] T023 [P] [US3-B3] 验证 CO-394 A/B/C/D 已迁移的 4 个 Controller（BrandAuthorization/Personnel/Performance/Qualification）当前是否仍有 hasAnyRole 残留（预期 0，若非 0 补迁移）
- [ ] T024 [US3-B3] 确认这 4 个 Controller 不在豁免清单（CO-394 时未建清单，现在补登记为已迁移状态）

### Phase 8: 批次 4 — resources 模块（含 CO-409 混合补丁） [US3-B4]

- [ ] T025 [P] [US3-B4] 迁移 `CaCertificateController.java` 7 处混合表达式 `hasAnyRole('ADMIN','MANAGER') or hasAuthority('ROLE_BID_TEAM')` → 统一 `hasAuthority('resource-ca')`（CO-409 已为 bid-Team 注册该权限，语义等价）
- [ ] T026 [P] [US3-B4] 迁移 `BarSiteSubresourceController.java`（6 处）、`BarCertificateController.java`（3 处）、`BarAssetController.java`（2 处）、`ExpenseController.java`（5 处）、`AccountController.java`（2 处）→ hasAuthority
- [ ] T027 [US3-B4] 若发现 resources 模块缺权限键（如 resource-bar、resource-expense），在 RoleProfileCatalog 注册 + Flyway V113x/U113x 迁移
- [ ] T028 [US3-B4] 补 ResourcesSecurityTest 回归，从豁免清单删除 6 个 resources Controller

### Phase 9: 批次 5 — project 模块 [US3-B5]

- [ ] T029 [P] [US3-B5] 迁移 `ProjectController.java`（12 处 hasAnyRole）、`ProjectClosureController.java`（6 处含 BID_TEAMLEADER/BIDADMIN 手抄列表）、`ProjectDraftingController.java`（3 处）、`ProjectEvaluationController.java`（5 处）、`ProjectInitiationController.java`（5 处）、`ProjectResultController.java`（1 处）→ hasAuthority
- [ ] T030 [US3-B5] 审查 project 模块的 Service 层 Policy（ProjectAccessScopeService、ProjectDocumentWorkflowPolicy）是否已覆盖资源级权限，Controller 放宽后有兜底
- [ ] T031 [US3-B5] 补 ProjectSecurityTest 回归（项目级 owner check 场景），从豁免清单删除 6 个 project Controller

### Phase 10: 批次 6 — tender 模块（含 EXTERNAL_API） [US3-B6]

- [ ] T032 [P] [US3-B6] 迁移 `TenderIntegrationController.java:52` 的 `hasRole('EXTERNAL_API')` → `hasAuthority('integration.external')`。在 RoleProfileCatalog 为外部 API Key 主体注册该权限键（不归业务角色），确认 ApiKeyAuthService 授予权限方式同步改造
- [ ] T033 [P] [US3-B6] 迁移 `TenderController.java`（13 处含 SALES 历史包袱）、`TenderEvaluationController.java`（5 处）、`TenderTransferController.java`（1 处）→ hasAuthority
- [ ] T034 [US3-B6] 补 TenderSecurityTest（含外部 API Key 场景），从豁免清单删除 tender Controller

### Phase 11: 批次 7 — platform + analytics + 分散模块 [US3-B7]

- [ ] T035 [P] [US3-B7] 迁移 platform（PlatformAccountController 8 处）、analytics（DashboardController 6 处）、calendar（3 处）、collaboration（2 处）、competitionintel（3 处）、contractborrow（2 处）、documenteditor（2 处）、documents（1 处）、fees（6 处）、marketinsight（3 处）、marketprediction（1 处）、roi（2 处）、scoreanalysis（1 处）、security（ProjectMemberController 2 处）、versionhistory（1 处）、approval（1 处）、alerts（4 处）、audit（1 处）、bidmatch（3 处）、batch（1 处复合表达式，按 research 决策 4.3 拆解到 Service）、apikey（1 处）、formengine（1 处）、settings（4 处）、notification（2 处）、integration（OrganizationQueryController 1 处 + AdminRoleOssMenuSyncController）、controller（AdminRoleController/AdminUserController/TestController 3 处）、crm（1 处）、projectworkflow（1 处）→ hasAuthority
- [ ] T036 [US3-B7] 逐个从豁免清单删除，ArchitectureTest 一致性验证（177→0）

**Checkpoint**: 全部 177 处迁移完成，豁免清单清空。

---

## Phase 12: Polish & Cross-Cutting Concerns

**Purpose**: 守卫升级 + 最终验证 + 文档沉淀

- [ ] T037 将 ArchitectureTest 守卫从"白名单豁免模式"升级为"硬失败门禁"：删除 `HASANYROLE_EXEMPT_CONTROLLERS` 常量与豁免逻辑（清单已空），任何 hasAnyRole/hasRole 都直接失败
- [ ] T038 [P] 运行全量验证：`cd backend && mvn test -Dtest=ArchitectureTest`（全绿）+ `grep -rn "hasAnyRole\|hasRole" src/main/java | grep -v test | grep -v SecurityConfig`（空输出）
- [ ] T039 [P] 运行 Constitution 合规验证：`grep -rn "hasAnyRole\|hasRole" src/main/java | grep -v test | wc -l` == 0（SecurityConfig 路径级兜底除外）
- [ ] T040 在 `docs/lessons/lessons-learned.md` 续编本特性完成记录：177→0 迁移经验、ArchTest 守卫范式、双轨制根因彻底消除
- [ ] T041 在 `specs/024-preauthorize-unification/implementation-notes.md` 汇总迁移过程中的决策、tradeoff、遇到的问题（按用户全局指令要求）
- [ ] T042 更新 `QUALITY_SCORE.md`：登记本技术债已清零，关联 Constitution v1.3.0

---

## Dependencies & Execution Order

### Phase 依赖

- **Phase 1 (Setup)**: 无依赖，可立即开始
- **Phase 2 (Foundational 守卫)**: 依赖 Phase 1；**阻塞 P1/P3 所有迁移**（守卫未建则迁移无法验证豁免清单一致性）
- **Phase 3 (US1 P1)**: 依赖 Phase 2 守卫就位。可独立部署为 MVP
- **Phase 4 (US2 P2)**: 依赖 Phase 2；与 Phase 3 可并行（不同文件）
- **Phase 5-11 (US3 P3 批次)**: 每批依赖 Phase 2；批次间相互独立，可并行或按序
- **Phase 12 (Polish)**: 依赖 Phase 5-11 全部完成

### 并行机会

- Phase 2 内 T003/T004/T006 可并行（同一文件不同规则，注意合并冲突）
- Phase 3 (US1) 与 Phase 4 (US2) 可并行
- **Phase 5-11 七个批次可多 Agent 并行**（不同模块、不同文件）—— 适合团队多 worktree 协作。开工前各 Agent 跑 `who-touches.sh` 确认模块无冲突
- 同批次内不同 Controller 的迁移可并行（如 Phase 8 的 6 个 resources Controller）

---

## Implementation Strategy

### MVP First（推荐先交付）

1. 完成 Phase 1-3（Setup + 守卫 + US1 P1 修复）
2. **STOP and VALIDATE**: 部署 P1 修复，验证 bid-otherDept 用户接口恢复 200
3. 此时生产故障已解除，守卫已防复发，可选择性继续 P3

### 增量交付（P3 分批）

4. 按业务优先级推进 Phase 5-11（建议先收尾 casework，再 resources/project/tender）
5. 每批独立 PR + 部署 + 验证
6. 全部完成后执行 Phase 12 守卫升级

### 多 Agent 并行策略（适合团队协作）

- 守卫（Phase 2）+ P1（Phase 3）由一个 Agent 串行完成（强依赖）
- P2（Phase 4）单独一个 Agent
- P3 批次 2-7 分配给不同 Agent（每个 Agent 一个模块 worktree）
- Polish（Phase 12）由最后合并的 Agent 执行

---

## Notes

- 每个迁移任务必须遵循 CO-394-A 范式（commit b64592304）：引用 RoleProfileCatalog 常量，不硬编码权限键字符串
- 豁免清单数量一致性自验是防漂移的关键——任何"迁移了 Controller 但忘删清单"或"清单多写"都会被 ArchitectureTest 捕获
- 复合表达式（如 BatchOperationController）需拆解到 Service Policy，不要试图用 SpEL 表达业务逻辑
- EXTERNAL_API（T032）需同步改造 ApiKeyAuthService，单独写 SecurityTest
- 本特性是 Constitution v1.3.0 首个落地项目，所有 PR 描述应关联 Constitution VI
