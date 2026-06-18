# Implementation Plan: OSS Menu Permission Sync

**Branch**: `agent/kimi/002-oss-menu-permission-sync` | **Date**: 2026-06-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-oss-menu-permission-sync/spec.md`

## Summary

在现有 OSS 组织架构集成基础上，新增对 OSS `GET /sysMenuUrl/getUserMenuTree` 接口的调用能力，将 OSS 用户菜单树映射为平台内部菜单权限码，并支持：
1. 系统管理员在角色管理页面按角色手动同步菜单权限；
2. 组织架构同步流程中可选自动按用户聚合角色菜单权限。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.3

**Primary Dependencies**: Spring Web (`RestTemplate`), Jackson, JPA, Flyway

**Storage**: MySQL 8.0 (`roles.menu_permissions`)

**Testing**: JUnit 5 + Mockito + MockRestServiceServer

**Target Platform**: Linux server / macOS dev

**Project Type**: Web application (backend monolith + Vue frontend)

**Performance Goals**: 手动同步 5 秒内返回（不含 OSS 耗时）；自动同步单用户额外查询 P95 < 1 秒

**Constraints**: 生产 Java 文件硬上限 300 行；domain/policy 层必须可单测、不依赖 Spring；真实 API 唯一源；调用可观测

**Scale/Scope**: 角色数量 < 100；菜单树节点 < 1,000；同步用户批次数百级

## Constitution Check

- **FP-Java Architecture**: 映射策略保持纯函数；持久化在 Application Service 中完成。
- **Real-API Only**: 直接调用 OSS 真实接口，禁止 Mock。
- **OSS Integration**: 调用记录日志、失败隔离、大小写安全映射、批量优先（本接口为单用户查询，自动聚合时按角色合并结果）。
- **Split-First**: 新能力拆分为 Gateway、DTO、Mapper、Domain Policy、App Service、Controller，避免上帝类。
- **Security**: 仅 ADMIN 可调用手动同步接口；配置项通过 `OrganizationIntegrationProperties` 管理。

## Project Structure

### Documentation (this feature)

```text
specs/002-oss-menu-permission-sync/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/integration/organization/
│   ├── application/
│   │   ├── OrganizationDirectoryGateway.java          # add fetchUserMenuTree port
│   │   ├── OrganizationIntegrationProperties.java     # add menu tree config
│   │   ├── OrganizationRoleMenuSyncAppService.java    # manual sync orchestration
│   │   └── OrganizationIntegrationConfig.java         # bean wiring if needed
│   ├── domain/
│   │   └── policy/
│   │       └── OssMenuPermissionMapper.java           # pure mapping policy
│   ├── dto/
│   │   ├── OssMenuTreeNode.java                       # menu node DTO
│   │   └── SyncRoleMenuPermissionRequest.java         # admin request DTO
│   └── infrastructure/client/
│       ├── OrganizationDirectoryHttpGateway.java      # implement port
│       ├── OrganizationDirectoryRestClient.java       # add GET support
│       ├── OrganizationDirectoryJsonMapper.java       # parse menu tree
│       └── NoOpOrganizationDirectoryGateway.java      # no-op fallback
├── src/main/java/com/xiyu/bid/controller/
│   └── AdminRoleOssMenuSyncController.java            # POST /api/admin/roles/{id}/sync-oss-menu-permissions
├── src/main/java/com/xiyu/bid/service/
│   └── RoleProfileService.java                        # add updateMenuPermissions
└── src/test/java/com/xiyu/bid/integration/organization/...
    ├── domain/policy/OssMenuPermissionMapperTest.java
    ├── application/OrganizationRoleMenuSyncAppServiceTest.java
    ├── infrastructure/client/OrganizationDirectoryHttpGatewayTest.java (add cases)
    └── ...
```

## Design Decisions

1. **Gateway Port Extension**: `OrganizationDirectoryGateway` 新增 `Optional<List<OssMenuTreeNode>> fetchUserMenuTree(String userIdentifier, OrganizationDirectoryLookupContext ctx)`。这里 userIdentifier 使用工号（jobNumber），与现有 `OssUserJobAndRoleDto` 一致；HTTP 实现再通过内部映射或 OSS 用户详情接口将其转换为 OSS 用户 ID（若接口需要）。

2. **HTTP GET 调用**: `OrganizationDirectoryRestClient` 增加 `get(String url, Map<String,String> queryParams, OrganizationDirectoryLookupContext context)` 方法，使用 `HttpEntity` 仅携带鉴权头，通过 `RestTemplate.exchange` 发起 GET。

3. **配置项**（`OrganizationIntegrationProperties.Directory`）:
   - `userMenuTreePath`：默认 `/sysMenuUrl/getUserMenuTree`
   - `userMenuTreeSystemName`：默认 `xiyu-bid-poc`（可配置）
   - `userMenuTreeRetrievalType`：默认 `2`
   - `userMenuTreeConnectTimeoutMs` / `userMenuTreeReadTimeoutMs`：独立超时
   - `autoSyncMenuPermissions`：默认 `false`
   - `menuCodeToPermissionKeyMappings`：`Map<String,String>` 大小写不敏感映射规则

4. **映射策略**: `OssMenuPermissionMapper` 纯函数：输入 `List<OssMenuTreeNode>`，递归遍历 children，对每个节点先规范化 menuCode（trim + lowerCase），再查配置映射；未命中时使用可配置的 `defaultUnmappedBehavior`（`IGNORE` 或 `USE_NORMALIZED_CODE`）。输出 `Set<String>` 去重。

5. **手动同步流程**:
   - `AdminRoleOssMenuSyncController` 接收 `POST /api/admin/roles/{id}/sync-oss-menu-permissions` + body `{jobNumber}`
   - `OrganizationRoleMenuSyncAppService` 查找角色、调用 Gateway 拉取菜单树、调用 Mapper、调用 `RoleProfileService.updateMenuPermissions(roleId, permissions)`

6. **自动聚合流程**:
   - 在 `OrganizationUserSyncWriter` 中，当 `autoSyncMenuPermissions=true` 且成功解析出用户角色后，调用 `Gateway.fetchUserMenuTree`。
   - 使用一个聚合器（如 `OssRoleMenuPermissionAggregator` 纯函数）按角色合并所有用户的权限集合。
   - 同步批次结束后统一写入 `RoleProfile`。
   - 为避免单用户失败阻塞，失败用户跳过并记录。

7. **可观测性**: 每次 OSS 调用记录 url、systemName、retrievalType、userIdentifier、status、耗时、返回节点数；失败记录错误和响应摘要。

## Complexity Tracking

> 无 Constitution 违规。
