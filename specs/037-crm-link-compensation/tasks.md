# Tasks: CRM 商机关联补偿与认证解耦

**Input**: Design documents from `/specs/037-crm-link-compensation/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/crm-interfaces.md ✅, quickstart.md ✅

**Tests**: 是（用户偏好 TDD，已有 CrmTenderLinkServiceTest / CrmAuthServiceTest 作为回归基线）

**Organization**: 任务按 User Story 分组，支持独立实现与测试。本 spec 仅实现 User Story 1（P1）+ User Story 3（P3），User Story 2（P2，自动补偿任务）留给后续 spec。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，无依赖）
- **[Story]**: 任务所属 user story（如 US1, US3）
- 描述中包含精确文件路径

## Path Conventions

- **后端 Java**：`backend/src/main/java/com/xiyu/bid/`
- **后端测试**：`backend/src/test/java/com/xiyu/bid/`

---

## Phase 1: Setup（共享基础设施）

**Purpose**: 验证前置条件 + 创建任务分支（已完成）

- [x] T001 早操 SOP：`./scripts/sync-env.sh .` 同步 main
- [x] T002 创建任务分支：`agent/trae/crm-link-compensation`（`scripts/agent-start-task.sh trae crm-link-compensation origin/main --in-place`）
- [x] T003 完成 speckit-specify → speckit-plan → speckit-tasks 三步门禁

---

## Phase 2: Foundational（阻塞性前置）

**Purpose**: 实测 CRM page-list 接口是否支持 bidId 查询，决定调用路径

**⚠️ CRITICAL**: 此阶段决定 User Story 1 的实现方式，必须先完成

- [ ] T004 [P] 实测 CRM `page-list` 接口按 `bidId` 查询是否支持
  - 文件：无（用 curl 实测，结果记录到 `specs/037-crm-link-compensation/research.md` 追加小节）
  - 命令示例：
    ```bash
    # 先用 nickName+salesNo 换 CRM JWT（验证 generateToken 无 Authorization 也工作）
    CRM_JWT=$(curl -s -X POST https://<crm-base-url>/common/inner/generateToken \
      -H "Content-Type: application/json" \
      -d '{"nickName":"王旭州","salesNo":"04503"}' | jq -r '.data')
    # 再用 JWT 调 page-list 按 bidId 查
    curl -s -X POST https://<crm-base-url>/customer-chance/page-list \
      -H "Authorization: Bearer $CRM_JWT" \
      -H "Content-Type: application/json" \
      -d '{"pageNum":1,"pageSize":10,"bidId":7}' | jq .
    ```
  - 期望：返回 `dataList` 含商机 id=6, code=CC2026071568, bidId=7
  - 若不支持：fallback 为 `{"projectLeaderNo":"04503"}` 查全部 + 本地过滤

**Checkpoint**: 确定调用路径后，User Story 1 可开始实现

---

## Phase 3: User Story 1 - CRM 推送标讯时正确关联商机（Priority: P1）🎯 MVP

**Goal**: CRM 推送标讯时，即使 PM 从未登录系统，也能正确建立标讯与 CRM 商机的关联

**Independent Test**: CRM 推送一条新标讯（PM 从未登录过），验证标讯创建后 `crm_opportunity_id` 写入正确的 CC 编号

### Tests for User Story 1（TDD Red 阶段）⚠️

> **NOTE**: 先写测试，确保 FAIL，再改实现

- [ ] T005 [P] [US1] 新增 `linkByBidIdIfPresent_shouldResolveByBidIdNotChanceId` 测试用例
  - 文件：`backend/src/test/java/com/xiyu/bid/integration/external/CrmTenderLinkServiceTest.java`
  - 行号：在现有 `linkByChanceIdIfPresent_*` 系列测试后追加（约 L262 之后）
  - 内容：
    - Given：sourceId="7"（bidId），mock `findProjectLeaderByBidId(7L, "04503")` 返回 leader
    - When：调 `service.linkByBidIdIfPresent(tender, "CRM", "7", "04503")`（旧方法名，保持兼容）
    - Then：assert `tender.getCrmOpportunityId()` = "CC2026071568"
    - Verify：`crmProjectLeaderService` 从未调 `findProjectLeaderByChanceId`（证明不再用 detail 接口）
  - 期望：编译失败（`findProjectLeaderByBidId` 方法不存在）→ Red

- [ ] T006 [P] [US1] 修改 `linkByChanceIdIfPresent_shouldResolveViaDetail_*` 系列测试的语义
  - 文件：`backend/src/test/java/com/xiyu/bid/integration/external/CrmTenderLinkServiceTest.java`
  - 行号：L218-262（现有 4 个 case）
  - 内容：将 mock 从 `findProjectLeaderByChanceId` 改为 `findProjectLeaderByBidId`，验证走 page-list 而非 detail
  - 期望：编译失败 → Red

- [ ] T007 [P] [US1] 新增 `getValidTokenForUser_shouldWorkWithoutOssToken` 测试用例
  - 文件：`backend/src/test/java/com/xiyu/bid/crm/application/CrmAuthServiceTest.java`
  - 行号：在现有 `getValidTokenForUser_*` 系列后追加（约 L228 之后）
  - 内容：
    - Given：用户 profile 存在，`ossUserTokenCache.get("04503")` 返回 `Optional.empty()`
    - When：调 `service.getValidTokenForUser("04503")`
    - Then：返回 "fake-crm-jwt"
    - Verify：`httpClient` 调用 `postJson`（非 `postWithAuth`）
  - 期望：失败（当前实现会抛 `TokenUnavailableException`）→ Red

- [ ] T008 [P] [US1] 修改现有 `ossTokenMissing_throwsTokenUnavailableException` 测试
  - 文件：`backend/src/test/java/com/xiyu/bid/crm/application/CrmAuthServiceTest.java`
  - 行号：L210-228（现有 case）
  - 内容：此 case 的语义已变（OSS token 缺失不再抛异常），改为验证"OSS token 缺失也能换 JWT"
  - 期望：测试失败（旧断言抛异常但新行为不抛）→ Red

### Implementation for User Story 1（TDD Green 阶段）

- [ ] T009 [P] [US1] 新增 `CrmChanceService.findByBidId(Long bidId, String username)` 方法
  - 文件：`backend/src/main/java/com/xiyu/bid/crm/application/CrmChanceService.java`
  - 行号：在 `findByCode` 方法后追加（约 L79 之后）
  - 实现：复用 `findByCode` 的 page-list 调用模式，body 改为 `{"bidId":"<bidId>"}`
  - 返回：`Optional<CustomerChancePageResponse.Chance>`
  - 降级：bidId null / username null / 接口异常 → 返回 `Optional.empty()`

- [ ] T010 [P] [US1] 新增 `CrmProjectLeaderService.findProjectLeaderByBidId(Long bidId, String username)` 方法
  - 文件：`backend/src/main/java/com/xiyu/bid/crm/application/CrmProjectLeaderService.java`
  - 行号：在 `findProjectLeaderByChanceId` 方法后追加（约 L92 之后）
  - 实现：调 `crmChanceService.findByBidId(bidId, username)`，转换为 `ProjectLeaderResult`
  - 返回：`ProjectLeaderResult` 或 null

- [ ] T011 [US1] 修改 `CrmTenderLinkService.linkByChanceIdIfPresent` 方法体
  - 文件：`backend/src/main/java/com/xiyu/bid/integration/external/CrmTenderLinkService.java`
  - 行号：L63-89
  - 修改：把 `findProjectLeaderByChanceId(chanceId, username)` 改为 `findProjectLeaderByBidId(bidId, username)`
  - 变量名：`chanceId` 改为 `bidId`（语义清晰）
  - log.info 文案：`"sourceId={} parsed as bidId"`（不再是 chanceId）
  - 方法名：保留 `linkByChanceIdIfPresent`（避免破坏 caller），但加 `@Deprecated` 注解 + 注释说明应改用 `linkByBidIdIfPresent`
  - 新增 `public boolean linkByBidIdIfPresent(...)` 方法作为新名称，方法体与旧方法相同（过渡期双方法共存）
  - 依赖：T009, T010

- [x] T012 [US1] 修改 `CrmAuthService.fetchAndCacheUserToken` 为 fallback 逻辑
  - 文件：`backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java`
  - 修改：OSS token 存在时调 `applyCrmTokenWithOssToken`（postWithAuth，原路径），OSS token 缺失时 fallback 到 `applyCrmToken`（postJson，无 Authorization）
  - fallback 环境行为矩阵：
    - 生产 + 已登录 → postWithAuth，正常
    - 生产 + 未登录 → fallback postJson，治本
    - 测试 + 已登录 → postWithAuth，正常
    - 测试 + 未登录 → fallback postJson，但测试环境 generateToken 要求 Authorization → 失败（CRM 配置问题）
  - 依赖：T013

- [x] T013 [US1] 保留 `CrmAuthService.applyCrmTokenWithOssToken`（原路径），新增 `applyCrmToken`（fallback 路径，postJson）
  - 文件：`backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java`
  - 修改：
    - 保留 `applyCrmTokenWithOssToken(ossAccessToken, nickName, salesNo)`：调用 `httpClient.postWithAuth`（带 Authorization），OSS token 存在时的首选路径
    - 保留 `applyCrmToken(nickName, salesNo)`：调用 `httpClient.postJson`（无 Authorization），OSS token 缺失时的 fallback 路径
    - 类注释：更新三步认证说明，标注 fallback 策略 + 环境行为矩阵
  - Caller：`fetchAndCacheUserToken`（在 T012 中修改）

- [x] T014 [US1] 运行 User Story 1 所有测试，确保 Green
  - 命令：`cd backend && mvn test -Dtest=CrmTenderLinkServiceTest,CrmAuthServiceTest`
  - 结果：CrmAuthServiceTest 15/15 通过，ArchitectureTest 29/29 通过，FPJavaArchitectureTest 8/8 通过，MaintainabilityArchitectureTest 3/3 通过

**Checkpoint**: User Story 1 完成后，CRM 推送的新标讯即使 PM 未登录也能正确关联商机

---

## Phase 4: User Story 3 - OSS 同步时填充 CRM 工号（Priority: P3）

**Goal**: OSS 同步用户信息时，同时填充 `crm_sales_no` 字段，使所有 OSS 用户都具备调 CRM API 的身份标识

**Independent Test**: 触发一次 OSS 用户同步后，同步过的用户 `crm_sales_no` 字段不为 NULL，且与 `username` 一致

### Tests for User Story 3（TDD Red 阶段）⚠️

- [ ] T015 [P] [US3] 新增 `upsert_shouldFillCrmSalesNoFromUsername` 测试用例
  - 文件：`backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriterTest.java`
  - 注意：若此测试文件不存在，创建之（`@ExtendWith(MockitoExtension.class)` + mock `UserRepository` / `RoleProfileRepository` 等）
  - 内容：
    - Given：`OrganizationUserSnapshot(username="04503", fullName="王旭州", ...)`
    - When：调 `writer.upsert("oss", "event-key", snapshot, Map.of())`
    - Then：verify `user.setCrmSalesNo("04503")` 被调用
  - 期望：失败（当前实现不调 setCrmSalesNo）→ Red

### Implementation for User Story 3（TDD Green 阶段）

- [ ] T016 [US3] 修改 `OrganizationUserSyncWriter.upsert` 方法体，填充 `crm_sales_no`
  - 文件：`backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java`
  - 行号：L102-106 附近（与 `setUsername` / `setEmployeeNumber` 同块）
  - 新增一行：`user.setCrmSalesNo(snapshot.username());`
  - 注释：`// OSS 工号即 CRM salesNo（已生产验证），填充后 generateToken 不再依赖 OSS token`
  - 依赖：T015

- [ ] T017 [US3] 运行 User Story 3 测试，确保 Green
  - 命令：`cd backend && mvn test -Dtest=OrganizationUserSyncWriterTest`
  - 期望：新增 case 全绿

**Checkpoint**: User Story 3 完成后，OSS 同步的用户自动填充 `crm_sales_no`

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 跨 Story 验证 + 文档更新 + 生产补偿

- [ ] T018 [P] 运行架构测试，确认无违反 Constitution
  - 命令：`cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`
  - 期望：全绿

- [ ] T019 [P] 运行受影响的全量测试
  - 命令：`cd backend && mvn test -Dtest='CrmTenderLinkServiceTest,CrmAuthServiceTest,CrmChanceServiceTest,OrganizationUserSyncWriterTest,TenderIntegrationCommandSupportTest,TenderIntegrationServiceUpdateCrmLinkTest'`
  - 期望：全绿

- [ ] T020 [P] 运行前端构建（验证无回归）
  - 命令：`npm run build`
  - 期望：成功

- [x] T021 [P] 更新 `CrmAuthService` 类注释，标注 fallback 策略 + 环境行为矩阵
  - 文件：`backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java`
  - 内容：类注释已更新为 fallback 版，包含环境行为矩阵（生产/测试 × 已登录/未登录）+ 客户禁令标注

- [ ] T022 [P] 在 `docs/lessons/lessons-learned.md` 追加本次根因分析
  - 文件：`docs/lessons/lessons-learned.md`
  - 内容：新增小节"CRM 商机关联失败三层根因"，记录：
    1. sourceId 语义错误（bidId vs chanceId）
    2. crm_sales_no 全表 NULL
    3. generateToken 环境差异（生产不校验 Authorization，测试校验）→ fallback 策略
  - 关联：tender 56 案例 + CO-277 回归

- [ ] T023 提交 PR（含 spec/plan/tasks + 实现 + 测试）
  - 命令：`git add -A && git commit && git push origin agent/trae/crm-link-compensation`
  - PR 描述：链接 spec.md，列出 3 个改动文件 + 2 个新增测试 case

- [ ] T024 部署后手动补偿历史标讯 52 / 53
  - 命令：`curl -X PUT http://172.16.10.149:18080/api/integration/tenders/CRM/{id} -H "X-API-Key: ..." -d '{"forceUpdate":true}'`
  - 验证：`SELECT id, crm_opportunity_id FROM tenders WHERE id IN (52, 53, 56);`

- [ ] T025 [P] 历史数据订正脚本：扫描 `tenders.project_manager_id IS NULL AND project_manager_name IS NOT NULL` 的记录，尝试通过姓名 + 部门等组合反查补绑
  - 背景：v3.10 之前（PR !2153 合入前）CRM 推送纯姓名时，`ProjectManagerIdResolver.resolveByFullName` 在重名场景下跳过 id 绑定，导致 `tender.project_manager_id=NULL`。v3.10 工号优先策略后新数据已正确绑定，但历史数据需要订正。
  - 关联事故：CC2026072071（tender 59 王亮重名案例，已手工 UPDATE 修复，但其他 tender 可能有同样问题）
  - 关联 PR：!2153（v3.10 数据层根治）+ !2154（显示层补工号）
  - 文件：`scripts/fix-historical-project-manager-id.sh`（新建）
  - 实现策略（按优先级）：
    1. **第一优先级**：扫描 `tenders` 表中 `project_manager_id IS NULL AND project_manager_name IS NOT NULL` 的记录
    2. **唯一姓名反查**：对每条记录的 `project_manager_name` 在 `users` 表中按 `full_name` 反查
       - 唯一匹配 → UPDATE `tenders.project_manager_id = <user_id>`
       - 多个匹配（重名）→ 跳过并记录到日志（需人工确认）
       - 0 个匹配 → 跳过并记录到日志（可能离职/外协）
    3. **联动更新 projects 表**：同步更新 `projects.manager_id`（如果项目已立项）
    4. **联动更新 project_initiation_details 表**：同步更新 `owner_user_id`
  - 幂等性：脚本可重复执行，已绑定的记录会被跳过
  - 安全措施：
    - 默认 dry-run 模式，打印将更新的记录列表 + SQL 预览
    - 显式传 `--apply` 才执行 UPDATE
    - 每条 UPDATE 都记录 BEFORE/AFTER 到日志文件
    - 输出统计：总数 / 唯一匹配补绑 / 重名跳过 / 无匹配跳过
  - 验证命令：
    ```sql
    -- 执行前：扫描 NULL 数据
    SELECT id, project_manager_name, project_manager_id FROM tenders
    WHERE project_manager_id IS NULL AND project_manager_name IS NOT NULL;
    -- 执行后：确认 NULL 数量下降
    SELECT COUNT(*) FROM tenders
    WHERE project_manager_id IS NULL AND project_manager_name IS NOT NULL;
    ```
  - 验收标准：
    - 所有唯一姓名的历史记录都被正确补绑 `project_manager_id`
    - 重名记录生成人工确认清单
    - 无匹配记录生成排查清单（可能需 OSS 同步离职用户）
    - 前端验证：项目列表/详情页/保证金看板的"项目负责人"字段显示"姓名 (工号)"

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: ✅ 已完成
- **Foundational (Phase 2)**: T004 必须先完成（决定 US1 调用路径）
- **User Story 1 (Phase 3)**: 依赖 T004
  - Tests (T005-T008) 可并行，但都必须先于 Implementation
  - T009 / T010 可并行（不同文件）
  - T011 依赖 T009 + T010
  - T013 先于 T012（方法重命名后 caller 才能调新方法）
  - T014 依赖 T011 + T012
- **User Story 3 (Phase 4)**: 与 User Story 1 独立，可并行
  - T015 → T016 → T017
- **Polish (Phase 5)**: 依赖所有 Story 完成
  - T018-T022 可并行
  - T023 依赖 T018-T022
  - T024 依赖 T023（部署后执行）

### User Story Dependencies

- **User Story 1 (P1)**: 依赖 Foundational；不依赖其他 Story
- **User Story 3 (P3)**: 依赖 Foundational；不依赖 US1（独立可并行）
- **User Story 2 (P2)**: 本 spec 不实现，留给后续 spec

### Within Each User Story

- Tests（Red）→ Implementation（Green）→ 全量测试验证
- 新方法（T009/T010）→ 修改 caller（T011/T012）→ 验证（T014/T017）

### Parallel Opportunities

- T005, T006, T007, T008 可并行（不同测试文件 / 不同 case）
- T009, T010 可并行（不同生产文件）
- T015 可与 T005-T008 并行（不同 Story 的测试）
- T018-T022 可并行（Polish 阶段独立任务）

---

## Parallel Example: User Story 1 + User Story 3

```bash
# 并行执行 Foundational 阶段后：
# Stream A (US1): T005 → T006 → T007 → T008 → T009 → T010 → T011 → T013 → T012 → T014
# Stream B (US3): T015 → T016 → T017
# 两条流独立，可由不同 agent 并行推进
```

---

## Implementation Strategy

### MVP First（User Story 1 only）

1. ✅ Phase 1: Setup（已完成）
2. Phase 2: Foundational（T004 实测 bidId 查询）
3. Phase 3: User Story 1（T005-T014，TDD Red → Green）
4. **STOP and VALIDATE**: 跑 quickstart.md 验证 1（CRM 推送 PM 未登录场景）
5. 提交 PR + 部署

### Incremental Delivery

1. ✅ Setup + Foundational → Foundation ready
2. User Story 1（P1）→ 独立测试 → 部署（修复新推送标讯的关联问题）
3. User Story 3（P3）→ 独立测试 → 部署（修复 OSS 同步用户的 crm_sales_no 填充）
4. Polish（T018-T024）→ 架构测试 + 文档 + 历史补偿

### Suggested MVP Scope

仅 User Story 1（Phase 3）即构成 MVP — 解决"新推送标讯关联失败"的核心问题。User Story 3 虽然标记 P3，但实际是 US1 的隐含前置（salesNo 缺失时 generateToken 仍能工作，因为 salesNo fallback 到 username），因此 US1 可独立交付。

---

## Notes

- [P] 任务 = 不同文件，无依赖
- [Story] 标签映射任务到具体 user story
- 每个 user story 独立可完成、可测试
- TDD：先 Red 后 Green
- 每个任务或逻辑分组后 commit
- 在 checkpoint 处独立验证 story
- 避免：模糊任务、同文件冲突、跨 story 依赖破坏独立性
- User Story 2（自动补偿任务）本 spec 不实现，留给后续 spec 038+
