# Tasks: 修复平台账号密码查看权限异常类型误用

**Feature**: 修复平台账号密码查看权限异常类型误用
**Plan**: [plan.md](./plan.md)
**Spec**: [spec.md](./spec.md)
**Branch**: `agent/gemini/fix-account-password-403`

## TDD Workflow

遵循 Red → Green → Refactor（Constitution Core Principle III）：
1. **Red**: 先修改/新增测试，断言 `AccessDeniedException`，运行测试 → 期望 FAIL
2. **Green**: 修改 `PlatformAccountService` 3 处异常类型，运行测试 → 期望 PASS
3. **Refactor**: 不引入重构（保持最小化）

## Phase 1: Setup

- [ ] T001 同步基线：在当前任务分支 `agent/gemini/fix-account-password-403` 上运行 `./scripts/sync-env.sh .` 确保 base 是最新 `origin/main`
- [ ] T002 检查文件锁：运行 `npm run agent:lock-check:changed` 确认 `backend/src/main/java/com/xiyu/bid/platform/service/PlatformAccountService.java` 没有其他 agent 的 active lock

## Phase 2: Foundational (TDD - Red Phase)

> 目标：先修改/新增测试，使其在当前代码下 FAIL（Red），证明测试有效。

- [ ] T003 [US1] 修改 `backend/src/test/java/com/xiyu/bid/platform/service/PlatformAccountServiceTest.java` 中 `getPassword_whenBidTeam_throwsIllegalStateException` 测试：
  - 测试方法名改为 `getPassword_whenBidTeamNotContactPerson_throwsAccessDeniedException`
  - 断言从 `isInstanceOf(IllegalStateException.class)` 改为 `isInstanceOf(AccessDeniedException.class)`
  - DisplayName 同步更新
- [ ] T004 [US1] 修改 `backend/src/test/java/com/xiyu/bid/platform/service/PlatformAccountServiceTest.java` 中 `getPassword_nonAdmin_throws` 测试：
  - 测试方法名改为 `getPassword_nonPrivilegedNonBidTeamRole_throwsAccessDeniedException`
  - 断言从 `isInstanceOf(IllegalStateException.class)` 改为 `isInstanceOf(AccessDeniedException.class)`
- [ ] T005 [US2] 在 `backend/src/test/java/com/xiyu/bid/platform/service/PlatformAccountServiceTest.java` 新增测试 `getPassword_whenCurrentUserNull_throwsAccessDeniedException`：
  - 调用 `service.getPassword(1L, null)`
  - 断言抛出 `AccessDeniedException`
  - 必要时导入 `org.springframework.security.access.AccessDeniedException`
- [ ] T006 [US1] 运行测试验证 Red Phase：`cd backend && mvn test -Dtest=PlatformAccountServiceTest#getPassword_*`，期望 3 个测试 FAIL（断言 AccessDeniedException 但实际抛 IllegalStateException 或 NPE）

## Phase 3: User Story 1 - 非授权用户查看密码收到 403 (Priority: P1)

> 目标：替换 `PlatformAccountService.getPassword` 中 2 处业务权限校验异常类型（Green Phase）。

- [ ] T007 [US1] 在 `backend/src/main/java/com/xiyu/bid/platform/service/PlatformAccountService.java` 添加 import：`org.springframework.security.access.AccessDeniedException`（如尚不存在）
- [ ] T008 [US1] 修改 `backend/src/main/java/com/xiyu/bid/platform/service/PlatformAccountService.java` L254-255：将 `throw new IllegalStateException("Only administrators or the account's contact person can view the password")` 改为 `throw new AccessDeniedException("Only administrators or the account's contact person can view the password")`
- [ ] T009 [US1] 修改 `backend/src/main/java/com/xiyu/bid/platform/service/PlatformAccountService.java` L258：将 `throw new IllegalStateException("Only administrators can view account passwords")` 改为 `throw new AccessDeniedException("Only administrators can view account passwords")`
- [ ] T010 [US1] 运行测试验证 Green Phase：`cd backend && mvn test -Dtest=PlatformAccountServiceTest#getPassword_*`，期望所有测试 PASS

## Phase 4: User Story 2 - 未认证用户调用收到 403 (Priority: P2)

> 目标：替换 `currentUser == null` 防御性校验的异常类型（Green Phase）。

- [ ] T011 [US2] 修改 `backend/src/main/java/com/xiyu/bid/platform/service/PlatformAccountService.java` L247：将 `throw new IllegalStateException("Authentication required")` 改为 `throw new AccessDeniedException("Authentication required")`
- [ ] T012 [US2] 运行测试验证 Green Phase：`cd backend && mvn test -Dtest=PlatformAccountServiceTest#getPassword_whenCurrentUserNull*`，期望测试 PASS

## Phase 5: User Story 3 - Sentry 不再上报 (Priority: P2)

> 目标：通过测试验证 `AccessDeniedException` 走 4xx handler 路径，不触发 Sentry 上报。

- [ ] T013 [US3] 在 `backend/src/test/java/com/xiyu/bid/exception/GlobalExceptionHandlerTest.java` 中确认已有 `handleAccessDeniedException` 测试覆盖（如果已有，跳过；如果没有，补充一个测试断言返回 403 + 不调用 Sentry）
- [ ] T014 [US3] 运行全局异常处理器测试：`cd backend && mvn test -Dtest=GlobalExceptionHandlerTest`，期望所有测试 PASS

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T015 运行架构测试验证无回归：`cd backend && mvn test -Dtest=ArchitectureTest`
- [ ] T016 运行相关集成测试验证无回归：`cd backend && mvn test -Dtest=PlatformAccountControllerSecurityTest`
- [ ] T017 在主工作区 trae 运行后端全量测试：`cd /Users/user/xiyu/worktrees/trae/backend && mvn test`（如有 PlatformAccount 相关集成测试需要数据库，须在主工作区执行）
- [ ] T018 提交代码：原子提交，commit message 说明 Sentry issue XIYU-N 修复
- [ ] T019 推送到远端：`git push origin HEAD:agent/gemini/fix-account-password-403`
- [ ] T020 创建 PR（Gitee），PR 描述包含：Sentry issue 链接、修改前/后对比、影响范围、回滚方案

## Dependencies

```
T001 → T002 → T003, T004, T005 (并行) → T006 (Red 验证)
                                       ↓
                          T007 → T008, T009 (并行) → T010 (US1 Green)
                                       ↓
                                 T011 → T012 (US2 Green)
                                       ↓
                          T013 → T014 (US3 验证)
                                       ↓
                 T015, T016, T017 (并行回归) → T018 → T019 → T020
```

## Parallel Opportunities

- Phase 2 (Red Phase): T003, T004, T005 可并行（不同测试方法，同一文件）
- Phase 3 (US1 Green): T008, T009 可并行（同一文件不同行）
- Polish: T015, T016, T017 可并行（不同测试类）

## Independent Test Criteria

- **US1**: 调用 `GET /api/platform/accounts/{id}/password` 时，非特权非绑定联系人用户收到 HTTP 403
- **US2**: `currentUser == null` 时调用 `getPassword` 抛 `AccessDeniedException`
- **US3**: 触发权限校验失败时，Sentry 不接收到 IllegalStateException 上报

## Implementation Strategy

MVP scope = US1 + US2（核心修复，最小化 PR 范围）。US3 通过现有 `handleAccessDeniedException` handler 隐式满足，无需额外代码改动。

## Notes

- 本次不修改 `GlobalExceptionHandler`（已有 `AccessDeniedException` handler 复用）
- 本次不修改 `PlatformAccountViewerPolicy`（`checkCanReturnAccount` 同类问题作为技术债单独处理）
- 本次不做权限校验下沉到 Policy 的对称化重构（spec 已明确）
- 测试覆盖：5 个 getPassword 测试用例（2 个修改 + 1 个新增 + 2 个成功路径不变）
