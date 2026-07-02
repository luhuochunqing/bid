# Feature Specification: 消除 @PreAuthorize hasAnyRole 双轨制技术债

**Feature Branch**: `024-preauthorize-unification`

**Created**: 2026-07-02

**Status**: Draft

**Input**: User description: "消除 @PreAuthorize hasAnyRole 双轨制技术债治理 — 177 处白名单迁移到 isAuthenticated/hasAuthority 单一模型，配架构测试守卫防止复发，根除 20+ 个反复返工的 403 PR"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 跨部门协同人员能正常使用任务表单 (Priority: P1) 🎯 MVP

跨部门协同人员（`bid-otherDept` 角色）登录系统后，打开任务处理表单（TaskForm）时，
表单应正常渲染所有扩展字段输入控件，不应因"任务扩展字段 schema"接口返回 403 而导致
扩展字段缺失。当前症状：访问 `GET /api/task-extended-fields` 被
`hasAnyRole('ADMIN','MANAGER',...)` 白名单拦截，返回 403，前端将字段列表降级为空数组，
用户看到的表单缺少扩展字段，且因前端缓存机制会话内不会重试。

**Why this priority**: 这是当前生产环境正在发生的真实故障（2026-07-02 工号 09118 用户
报错），且该接口语义上就是"公开读取的全局字段 schema"（类注释明确"公开读取"，Service
无身份维度），用角色白名单收紧属于明显的过度鉴权。修复成本低、收益直接、风险极小。

**Independent Test**: 用 `bid-otherDept` 角色用户访问
`GET /api/task-extended-fields`，应返回 200 + 字段列表。可用一个 SecurityTest 用例
独立验证，不依赖其他故事。

**Acceptance Scenarios**:

1. **Given** 一个 `bid-otherDept` 角色的已登录用户，**When** 该用户调用
   `GET /api/task-extended-fields`，**Then** 返回 HTTP 200，响应体为启用字段列表
   （非空数组或合理空列表，取决于配置）。
2. **Given** 一个 `bid-administration`（行政人员）角色的已登录用户，**When** 该用户
   调用 `GET /api/task-extended-fields`，**Then** 返回 HTTP 200（同理，公开读取接口）。
3. **Given** 一个未认证的匿名请求，**When** 调用 `GET /api/task-extended-fields`，
   **Then** 返回 HTTP 401（仍需登录，只是不再用角色白名单收紧）。
4. **Given** TaskForm 组件挂载，**When** `bid-otherDept` 用户打开任务处理页面，
   **Then** 扩展字段输入控件正常渲染（非空，与 admin 用户看到的一致）。

---

### User Story 2 - 新增 @PreAuthorize 滥用会被自动拦截 (Priority: P2)

开发者（或 AI Agent）提交 PR 时，如果新增的 Controller `@PreAuthorize` 使用了
`hasAnyRole`/`hasRole` 角色枚举式白名单，CI 的架构测试应立即失败并给出明确提示，
要求改用 `isAuthenticated()` 或 `hasAuthority('<permission-key>')`。这一层守卫确保
技术债不再增长——存量 177 处作为已登记债务逐步迁移，但禁止新增。

**Why this priority**: 第二层"止血"防复发。没有这一层，第三层迁移期间开发者可能
继续用旧模式新增端点，导致债务不降反增。P1 修复后应立即落地此守卫。

**Independent Test**: 新增一个故意使用 `hasAnyRole` 的测试用 Controller，运行
ArchitectureTest 应失败；改用 `hasAuthority` 后应通过。

**Acceptance Scenarios**:

1. **Given** ArchitectureTest 守卫已启用，**When** 开发者新增一个标注
   `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")` 的 Controller 方法，**Then**
   `mvn test -Dtest=ArchitectureTest` 失败，错误信息明确指出"禁止 hasAnyRole，
   请改用 hasAuthority 或 isAuthenticated，参见 Constitution VI"。
2. **Given** ArchitectureTest 守卫已启用且含 177 处豁免清单，**When** 运行测试，
   **Then** 测试通过（豁免清单覆盖所有现存使用点），且豁免清单数量 == 实际
   `hasAnyRole`/`hasRole` 使用点数量（一致性自验）。
3. **Given** 开发者迁移了某个 Controller 后从豁免清单删除对应条目，**When** 该 Controller
   仍残留 `hasAnyRole`，**Then** ArchitectureTest 失败（豁免清单与实际不一致）。

---

### User Story 3 - 全部业务模块迁移到单一权限模型 (Priority: P3)

后端所有业务 Controller 的 `@PreAuthorize` 注解从 `hasAnyRole`/`hasRole` 角色枚举式
白名单，迁移到 `isAuthenticated()`（早过滤）或 `hasAuthority('<permission-key>')`
（细粒度业务权限）两种合法形态。迁移完成后，ArchitectureTest 守卫从"白名单豁免模式"
升级为"硬失败门禁"，`hasAnyRole` 在仓库中彻底消失。系统从此只维护一套权限模型，
不再有"新模型给角色赋权 / 旧模型拒绝承认"的双轨制矛盾。

**Why this priority**: 第三层"治本"。这是工作量最大的故事，需要按模块分批推进
（已按 Controller 文件统计：resources/project/tender 等约 35 个文件、177 处使用点）。
已有 CO-394 A/B/C/D 验证过的迁移范式可复用。优先级最低不是不重要，而是因为它可以
分批交付、不阻塞 P1/P2。

**Independent Test**: 每个模块（如 knowledge、resources、project）迁移完成后，该模块
的所有 `@PreAuthorize` 应为 `isAuthenticated()` 或 `hasAuthority` 形态，对应回归测试
覆盖各角色 200/403 场景。全量迁移完成后，`grep -r "hasAnyRole" backend/src/main`
输出为空。

**Acceptance Scenarios**:

1. **Given** 某业务模块（如 resources）迁移完成，**When** 用 `bid-otherDept` 角色
   访问该模块下原本 403 的接口，**Then** 返回 200（或按业务语义应有的正确状态码，
   不再因角色白名单误伤）。
2. **Given** 某业务模块迁移完成，**When** 用无对应业务权限的角色访问写接口，
   **Then** 返回 403（由 `hasAuthority` 正确拦截，而非 `hasAnyRole` 误伤）。
3. **Given** 全量迁移完成，**When** 运行 `grep -rn "hasAnyRole\|hasRole"
   backend/src/main/java --include="*.java" | grep -v test`，**Then** 输出为空
   （或仅剩 SecurityConfig 的 `/api/admin/**` 路径级兜底，已在宪法中标注例外）。
4. **Given** 全量迁移完成，**When** ArchitectureTest 的豁免清单清空且守卫升级为
   硬失败，**Then** `mvn test -Dtest=ArchitectureTest` 通过，且任何新增
   `hasAnyRole` 都会触发失败。

---

### Edge Cases

- **路径级兜底例外**：`SecurityConfig` 中 `/api/admin/**` 的
  `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 是路径级兜底，不是业务
  Controller 的方法级 `@PreAuthorize`，宪法已标注为例外，不在迁移范围。
- **EXTERNAL_API 角色**：`TenderIntegrationController` 的
  `@PreAuthorize("hasRole('EXTERNAL_API')")` 是外部 API Key 鉴权，不是业务用户角色，
  迁移时需单独评估（可能保留 hasAuthority 形式但用专属权限键）。
- **复合表达式**：如 `BatchOperationController` 的
  `hasRole('ADMIN') or (hasRole('MANAGER') and !#type.equalsIgnoreCase('tender'))`
  包含业务逻辑，迁移时需拆解为 `hasAuthority` + Service 层 Policy 决策。
- **前端缓存影响**：`loadTaskExtendedFields` 的 store 有 `taskExtendedFieldsLoaded`
  缓存，P1 修复部署后，已出错的会话需刷新页面才会重新请求（不影响新会话）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `GET /api/task-extended-fields` 接口 MUST 对所有已认证用户放行（不再
  用角色白名单收紧），因为该接口语义为"公开读取全局字段 schema"。
- **FR-002**: ArchitectureTest MUST 新增守卫规则，扫描所有 `@PreAuthorize` 注解，
  禁止表达式包含 `hasAnyRole` 或 `hasRole`（Spring Security SpEL 关键字）。
- **FR-003**: ArchitectureTest 守卫 MUST 维护一份豁免清单，覆盖当前所有 177 处使用点；
  豁免清单 MUST 与实际使用点数量保持一致（数量不一致即测试失败）。
- **FR-004**: 每个业务模块迁移到 `hasAuthority` 时，MUST 先确认对应权限键已在
  `RoleProfileCatalog.SeedDefinition.menuPermissions` 中注册；未注册的 MUST 先补注册
  + Flyway 迁移同步角色权限。
- **FR-005**: 迁移后的 Controller 方法 MUST 保留对应的 SecurityTest 回归用例，覆盖
  至少"应有权限角色 200"和"无权限角色 403"两个场景。
- **FR-006**: 全量迁移完成后，ArchitectureTest 守卫 MUST 从白名单豁免模式升级为
  硬失败门禁（豁免清单清空，任何 `hasAnyRole`/`hasRole` 都触发构建失败）。
- **FR-007**: 迁移过程中 MUST 保留 `eb58f2817` 引入的 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT`
  机制不动（它是堵越权的正确防线），只迁移"依赖 legacy role 的白名单"。

### Key Entities *(include if feature involves data)*

- **权限键（permissionKey）**: 细粒度业务权限的标识符（如 `task.handle.own`、
  `project.view`、`brand-auth.view`），定义在 `RoleProfileCatalog`，是 `hasAuthority`
  表达式的参数。一个权限键可被多个角色拥有（在各自 `menuPermissions` 中列出）。
- **角色（roleCode）**: 7 个固定角色（admin / bid-projectLeader / bid-TeamLeader /
  /bidAdmin / bid-Team / bid-administration / bid-otherDept），每个角色在
  `RoleProfileCatalog` 中映射到一组权限键。本特性不新增角色，只调整 Controller
  鉴权方式。
- **ArchitectureTest 豁免清单**: 守卫规则中显式列出的"允许暂时保留 hasAnyRole"的
  Controller 全限定名清单，随迁移进度逐项删除，最终清空。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `bid-otherDept` 与 `bid-administration` 角色用户访问
  `GET /api/task-extended-fields` 返回 200（P1 验收，立即可验证）。
- **SC-002**: 新增任何 `@PreAuthorize` 使用 `hasAnyRole`/`hasRole` 时，
  ArchitectureTest 失败（P2 验收，债务零增长）。
- **SC-003**: `grep -rn "hasAnyRole\|hasRole" backend/src/main/java --include="*.java"
  | grep -v test` 的输出行数从 177 逐步降至 0（P3 进度指标，按模块分批下降）。
- **SC-004**: 全量迁移完成后，连续 3 个月内不再出现因"hasAnyRole 白名单漏角色"导致
  的 403 PR（对比基线：2026-06-27 至 2026-07-02 已有 20+ 个此类 PR）。
- **SC-005**: 迁移期间每个模块的 PR 都包含对应 SecurityTest 回归用例，且全绿
  （100% 模块迁移 PR 含测试证据）。

## Assumptions

- **角色集合稳定**：7 个系统角色在迁移期内不新增（用户已确认）。若期间新增角色，
  按宪法 VI 应通过 `RoleProfileCatalog.menuPermissions` 注册，自动获得权限键，
  无需修改任何 Controller。
- **CO-394 范式可复用**：CO-394 A/B/C/D 已成功将 4 个知识库 Controller 从
  `hasAnyRole('ADMIN','MANAGER')` 迁移到 `hasAuthority('<perm>')`，commit `b64592304`
  等的 diff 可作为标准迁移范式参考。
- **Service 层 Policy 已就位**：部分需要"资源级权限"的接口（如项目级 owner check）
  已有 `ProjectAccessScopeService` / `ProjectDocumentWorkflowPolicy` 等 Service 层
  守卫，Controller 放宽到 `isAuthenticated`/`hasAuthority` 后，细粒度权限由 Service
  层兜底，不会产生越权。
- **前端无需改动**：本特性是纯后端鉴权方式调整，API 契约不变（仍是 200/403），
  前端无需配套修改。
- **分批交付不影响业务**：每个模块迁移独立成 PR，可独立部署，模块间无强依赖
  （某些 Controller 可能跨模块引用权限键，但权限键在 RoleProfileCatalog 是全局
  共享的，不存在迁移先后冲突）。
