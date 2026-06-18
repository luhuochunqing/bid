# Tasks: OSS Menu Permission Sync

**Input**: Design documents from `specs/002-oss-menu-permission-sync/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Phase 1: Foundational (Blocking)

- [ ] T001 Add menu tree configuration properties to `OrganizationIntegrationProperties.Directory`
- [ ] T002 Add `OssMenuTreeNode` DTO in `backend/src/main/java/com/xiyu/bid/integration/organization/dto/OssMenuTreeNode.java`
- [ ] T003 Extend `OrganizationDirectoryRestClient` with `get(url, queryParams, context)` method
- [ ] T004 Add `fetchUserMenuTree` port to `OrganizationDirectoryGateway` and `NoOpOrganizationDirectoryGateway`

**Checkpoint**: Gateway port and REST client can execute GET requests.

## Phase 2: Domain Policy & HTTP Adapter

- [ ] T005 Implement `OssMenuPermissionMapper` pure policy in `backend/src/main/java/com/xiyu/bid/integration/organization/domain/policy/OssMenuPermissionMapper.java`
- [ ] T006 Implement `OrganizationDirectoryHttpGateway.fetchUserMenuTree` using `OrganizationDirectoryJsonMapper` to parse tree
- [ ] T007 Add unit tests for `OssMenuPermissionMapper`
- [ ] T008 Add gateway tests for menu tree GET parsing and error handling

**Checkpoint**: Domain mapping and HTTP adapter tested independently.

## Phase 3: User Story 1 - Manual Role Sync (P1)

- [ ] T009 Add `updateMenuPermissions(Long, List<String>)` to `RoleProfileService`
- [ ] T010 Implement `OrganizationRoleMenuSyncAppService` with `syncRoleMenuPermissions(Long roleId, String jobNumber)`
- [ ] T011 Create `SyncRoleMenuPermissionRequest` DTO
- [ ] T012 Implement `AdminRoleOssMenuSyncController` endpoint `POST /api/admin/roles/{id}/sync-oss-menu-permissions`
- [ ] T013 Add app service unit tests
- [ ] T014 Add controller integration/MockMvc tests

**Checkpoint**: Admin can manually sync a role's menu permissions from OSS.

## Phase 4: User Story 2 - Auto Aggregation (P2)

- [ ] T015 Implement `OssRoleMenuPermissionAggregator` pure function to merge per-user permissions by role
- [ ] T016 Integrate optional auto-sync into `OrganizationUserSyncWriter`
- [ ] T017 Add sync writer tests covering enabled/disabled/failure-skip behaviors

**Checkpoint**: Organization sync can optionally aggregate OSS menu permissions by role.

## Phase 5: Polish & Validation

- [ ] T018 Update `quickstart.md` if config keys changed during implementation
- [ ] T019 Run backend tests: `cd backend && mvn test -Dtest='com.xiyu.bid.integration.organization.**,OssMenuPermissionMapperTest,OrganizationRoleMenuSyncAppServiceTest'`
- [ ] T020 Run architecture tests: `cd backend && mvn test -Dtest=ResponsibilityArchitectureTest`
- [ ] T021 Commit and push feature branch

## Dependencies & Execution Order

- T001 → T002 → T003 → T004 can be done mostly in parallel except T004 depends on T003.
- T005/T006 parallel; T007/T008 tests follow.
- T009 blocks T010; T010/T011/T012 can be parallel after T009; tests follow.
- T015/T016 parallel after Phase 2; T017 follows.
- Polish after all stories.
