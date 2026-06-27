# 设计评估建议采纳修复实施记录

## 问题口径

- 上一轮已修复标讯/项目列表选人显示工号，但 Review 指出项目列表仍用 `valueField="name"` 做姓名筛选，存在同名误匹配。
- 前端 `firstNonBlank` 逻辑在用户归一化和标签格式化中重复。
- `MentionInput` 直接调用 `usersApi.search()`，仍按旧 `response.data` 结构读取，且缺少请求取消，可能导致 @ 候选为空或旧请求覆盖新请求。
- `useProjectSearch` 中残留空 `userList`，已无业务引用。

## 决策与权衡

- 项目列表筛选改为用户 ID 精确匹配：搜索表单使用 `projectLeaderId` / `biddingLeaderId`，`UserPicker` 回到默认 ID value，不再使用 `value-field="name"`。
- 后端 `ProjectDTO` 补充 `projectLeaderId`、`biddingLeaderId`、`secondaryBiddingLeaderId`；项目负责人 ID 优先取立项 `ownerUserId`，再回退标讯 `projectManagerId`，最后回退项目 `managerId`；投标负责人 ID 优先取 `ProjectLeadAssignment.primaryLeadUserId`，副负责人单独返回用于筛选。
- `/api/projects` 与 `/api/projects/export` 增加 ID 参数，同时保留姓名参数兼容旧调用；本轮不把项目列表改为服务端分页/Repository JOIN 查询，避免扩大改造面。
- 抽出 `src/utils/firstNonBlank.js`，让用户字段归一化和标签格式化共享同一空白判断。
- `MentionInput` 改为读取 `usersApi.search()` 的数组返回，并通过 `AbortController` 取消旧请求。
- 后端用户搜索的 username fallback 保留，但补充注释：这是组织同步历史数据的展示兼容，长期应回填 `employee_number`。

## 未纳入本次范围

- 不做用户表 `employee_number` 历史回填或组织同步链路改造。
- 不做用户搜索 `LIKE '%q%'` 的索引/全文检索优化。
- 不做项目列表服务端分页或 DB JOIN 级负责人过滤重构。
- 不做 UserPicker option 二级部门/角色展示优化。

## 验证

- 前端：`pnpm exec vitest run src/api/modules/users.spec.js src/composables/useUserPicker.spec.js src/components/common/UserPicker.spec.js src/components/common/MentionInput.spec.js src/utils/firstNonBlank.spec.js src/utils/formatUserLabel.spec.js src/views/Project/List.spec.js`：7 files / 56 tests passed；保留既有 `useUserPicker.spec.js` 中 Vue lifecycle warning。
- 后端：`mvn -f backend/pom.xml test -Dtest=UserSearchServiceTest,UserSearchControllerTest,UserRepositorySearchTest,ProjectControllerAuthorizationTest,ProjectControllerIntegrationTest,ProjectListInitiationStateIntegrationTest`：29 tests passed，BUILD SUCCESS；提交前因行数预算拆分 `ProjectListEnrichmentSupport` 后补跑 `mvn -f backend/pom.xml test -Dtest=ProjectListInitiationStateIntegrationTest,ProjectControllerIntegrationTest,ProjectControllerAuthorizationTest`：15 tests passed，BUILD SUCCESS；保留 Maven `systemPath`、git-commit-id 插件参数、ByteBuddy agent 等既有 warning。
- 通用：`git diff --check`：通过，无空白错误。

---

## 2026-06-25 UserPicker filter-method 回归 Bug

### 问题
commit `61dd1fd87` 为 UserPicker 加了 `:filter-method="mode === 'search' ? () => {} : undefined"`，目的是在 `mode='search'` 时禁用前端过滤，让用户只能通过远程搜索选人。

但这在 Element Plus 的 `handleQueryChange` 中产生副作用：
```
if (filterable && isFunction(filterMethod))  → 调用 filterMethod(val) ← 空函数命中
else if (filterable && remote && isFunction(remoteMethod)) → 永不到达
```

结果：输入文字 → 空函数执行 → API 永不触发 → 用户搜索不到执行人。

### 修复
- 删除 `:filter-method` 行（`UserPicker.vue:7`）。
- 在 `scripts/check-userpicker-mode.mjs` 新增 `checkFilterMethodInUserPicker()` 守卫，任何 staged `.vue` 文件中 `<UserPicker>` 带 `filter-method` 属性都会被阻止提交。

### 权衡
- 移除 `filter-method` 后，`mode='search'` 时用户仍然可以前端过滤 — 但这不是问题，因为 `remote` + `remote-method` 下 Element Plus 的行为是先调 `remoteMethod`，结果显示在 option 中，前端过滤只是锦上添花。
- 更彻底的方案可以设 `filterable="mode !== 'search'"`（之前的 commit 这么做过又还原了），但会让 mode 切换行为不一致，且需要改测试和 pre-commit 检查。当前方案最小化改动。

### 防复发
- 新增的 pre-commit 守卫强制阻断任何为 UserPicker 加 `filter-method` 的提交。
- 已有 `SEARCH_MODE_FILES` 列表确保关键文件必须使用 `mode='search'`。

# 标讯/项目列表选人工号未知修复实施记录

## 问题口径

- 标讯列表和项目列表的选人入口能按姓名选到人，但下拉展示里的工号缺失，用户看到“未知”或只看到姓名。
- 其他选人场景能显示工号，说明统一候选人路径已有工号，问题集中在列表筛选使用的用户搜索路径。

## 根因判断

- 标讯列表筛选已经使用统一 `UserPicker`，但走的是 `mode="search"`，依赖 `/api/users/search`；上一轮主要修的是 `mode="candidates"` 的候选人端点。
- 项目列表筛选原来没有用统一 `UserPicker`，而是局部 `el-select + usersApi.search()`，字段与展示逻辑独立。
- `usersApi.search()` 之前直接返回后端原始 DTO，未做 `id/departmentName/employeeId/employeeNumber` 统一标准化。
- 后端搜索 DTO 的 `employeeNumber` 只取 `users.employee_number`；组织同步用户里用户名通常就是工号，但 `employee_number` 可能为空。

## 决策与权衡

- 前端把 `usersApi.search()` 与 `getAssignableCandidates()` 统一走 `normalizeUserOption()`，并让标准化同时兼容 `employeeNumber/employeeId/jobNumber/staffId/username`。
- `UserPicker` 的展示统一改为 `formatUserLabel()` 的 `姓名（工号）` 口径，避免 search/candidates 两种模式展示不一致。
- `UserPicker` 增加 `valueField`，项目列表可复用统一组件但仍保持原有筛选参数为姓名，避免扩大项目列表后端查询契约。
- 后端 `/api/users/search` 在 `employeeNumber` 为空时回退用户名；这是对当前组织同步“用户名即工号”事实的最小修复，不新增 DTO 字段暴露。
- `UserRepository.searchActiveUsers()` 增加 `employee_number` 查询条件，让真实 `employee_number` 已落库的用户也可按工号搜索。
- 暂未做组织同步持久化工号和历史数据回填；这是更长期的正确性改造，范围大于本次列表选人显示修复。

## 验证

- RED：新增/调整前后端回归测试后，确认 `usersApi.search()` 未标准化、`UserPicker.formatLabel()` 未显示工号。
- GREEN：`pnpm exec vitest run src/api/modules/users.spec.js src/composables/useUserPicker.spec.js src/components/common/UserPicker.spec.js src/utils/formatUserLabel.spec.js src/views/Project/List.spec.js`：5 files / 49 tests passed。
- GREEN：`mvn -f backend/pom.xml test -Dtest=UserSearchServiceTest,UserSearchControllerTest,UserRepositorySearchTest`：14 tests passed。
- `git diff --check`：通过。

# 退出登录未回登录页修复实施记录

## 问题口径

- 用户在业务页点击账户“退出登录”后，期望立即进入登录页，但现场仍停留在业务页面。
- 当前认证已切到 HttpOnly cookie，前端只持有 `currentUser` 和 `user` hint；因此退出必须同时完成后端登出、本地状态清理、登录页导航三件事。

## 根因判断

- `userStore.logout()` 会在 `finally` 中执行 `resetSession()` 和 `navigateToLogin()`，但如果路由跳转 reject，整个 `logout()` 仍会 reject。
- `Header.vue` 外层 catch 把所有异常都按“用户取消”静默吞掉，导致后续 `router.replace('/login')` 不执行，用户留在当前业务页且没有错误提示。
- 顶部用户下拉菜单中 `profile` 与 `keyword-subscription` 的 `<el-dropdown-item>` 存在无效嵌套，可能影响 Element Plus 下拉项 command 结构，顺手修正为平级菜单项。

## 决策与权衡

- 保持真实 API 登出，不引入 Mock，不绕过后端 `/api/auth/logout`。
- 将登录页跳转统一收敛到 `userStore.logout()`/`navigateToLogin()`，Header 不再重复 `router.replace('/login')`，避免双重导航和二次异常。
- `navigateToLogin()` 捕获路由跳转失败并兜底 `window.location.assign('/login')`，确保登出后的本地会话清理不会因为前端路由异常而被用户感知为“没退出”。
- Header 不再静默吞掉非预期异常；如果 store 登出链路真的抛错，会记录错误并提示用户刷新重试。

## 验证

- `pnpm exec vitest run src/components/layout/Header.spec.js src/router/sessionNavigation.spec.js`：2 files / 5 tests passed。
- `npm run check:vue-template-balance`：Vue template balance check passed。
- `pnpm exec eslint src/components/layout/Header.vue src/components/layout/Header.spec.js src/router/sessionNavigation.js src/router/sessionNavigation.spec.js src/router/index.js src/stores/user.js`：通过，无 lint 输出。
- `git diff --check`：通过，无空白错误。


## 问题口径

- 用户 `06234` 通过 `/api/auth/me` 返回 `roleCode=admin`，但前端项目评标页不可编辑。
- 评标页编辑态由 `menuPermissions` 中的 `project:evaluate` / `evaluation.update` / `task.review` / `all` 间接决定；OSS 菜单权限缓存只返回菜单级权限，未带回本地角色目录中的业务操作权限。

## 决策与权衡

- 修复放在后端 `DataScopeConfigService.getRoleMenuPermissions` 的 OSS 缓存返回口，保持前端权限判断不变。
- 对 OSS 已解析出的已注册内部角色，合并 `RoleProfileCatalog` 默认业务权限；未知角色不补齐，避免越权。
- `admin` 对齐 `UserDetailsServiceImpl` 后端鉴权语义：保留 OSS 菜单权限，同时补 `all` 和 catalog 全部已知权限，确保 `/api/auth/me` 与 Spring Security authorities 不再错位。
- 不修改 OSS 菜单编码配置，不要求 OSS 侧新增按钮级菜单；本次目标是恢复西域数智化投标管理平台内部角色应有的业务操作权限。

## 验证

- 先新增回归测试 `DataScopeConfigServiceTest#getRoleMenuPermissions_ShouldEnrichOssAdminPermissionsWithCatalogDefaults`，RED 确认为只返回 `project/project-detail`、缺少 `all/evaluation.update/task.review`。
- 修复后执行：`mvn -f /Users/user/xiyu/worktrees/zcode/backend/pom.xml test -Dtest=DataScopeConfigServiceTest`，10 tests passed。

# CO-315 标书审核人进入标书制作阶段修复实施记录

## 问题口径

- 标书制作阶段由投标负责人上传标书并指定标书审核人后，审核人进入 `/project/42` 时需要进入“标书制作”阶段完成查看、通过或驳回。
- 当前 `ProjectStageTimeline` 只按项目真实阶段顺序开放当前及历史阶段；如果项目真实阶段不在 `DRAFTING`，被指定审核人无法通过阶段入口进入标书制作审核界面。

## 决策与权衡

- 最小修复放在前端阶段入口：`ProjectStageTimeline` 增加 `extraUnlockedStages`，保留原有按阶段进度解锁规则，只允许调用方显式追加少量业务解锁阶段。
- `ProjectDetailMainColumn` 复用真实接口 `projectLifecycleApi.getDrafting(projectId)` 获取审核状态；仅当当前用户 ID 等于 `reviewerId` 且 `reviewStatus=REVIEWING` 时追加解锁 `DRAFTING`。
- 不改角色目录、不新增接口、不引入 Mock 数据、不调整 `DraftingStage` 的审核按钮逻辑；审核按钮仍由既有“当前用户是指定审核人且状态 reviewing”判断控制。
- `getDrafting` 失败时保持默认阶段锁定并仅记录 warning，避免阶段时间线整体不可用。
- Review 后按“全部修直”收口设计：阶段入口能力改由后端 `/api/projects/{id}/stage` 快照返回 `accessibleStages/defaultOpenStage`；前端 `ProjectDetailMainColumn` 不再额外调用 `getDrafting()` 判断审核人身份，`ProjectStageTimeline` 只消费后端快照。
- `/stage` 移除 `ADMIN/MANAGER` 方法级白名单，保留类级认证并显式调用 `ProjectAccessScopeService.assertCurrentUserCanAccessProject(projectId)`；被指定且 `REVIEWING` 的审核人默认打开 `DRAFTING`，非审核人保持真实阶段默认打开。
- 这样避免了前端重复实现 `reviewerId + reviewStatus` 权限规则、重复请求完整 drafting view、异步 watcher 乱序和审核后父组件解锁状态残留等设计弯路。

## 验证计划

- RED：新增 `ProjectStageTimeline.spec.js` 用例，确认旧实现即使传入 `extraUnlockedStages: ['DRAFTING']` 也不会 emit `stage-click`。
- RED：新增 `ProjectDetailMainColumn.spec.js` 用例，确认旧实现不会读取 `getDrafting`，也不会给时间线传入 `['DRAFTING']`。
- GREEN：运行 `pnpm exec vitest run src/components/project/stage/ProjectStageTimeline.spec.js src/components/project/detail/ProjectDetailMainColumn.spec.js`。

# 消息接口交付文档整理实施记录

## 问题口径

- 客户需要“所有消息接口”的交付说明，本次按当前代码真实实现整理为 `docs/api/message-interfaces.md`。
- 覆盖范围包括平台站内通知接口、CRM/客户消息中心代理发送接口，以及内部企微消息中心发送能力。

## 决策与权衡

- 不新增或修改接口，只整理现有实现与已沉淀的 wiki/spec 口径。
- 将前端已声明但后端未实现的 `GET /api/notifications/{id}` 标为“前端预留，后端未实现”，避免把未交付能力写成已完成。
- 将 CRM 消息发送路径差异显式列为待确认项：当前运行默认 `/common/sendMessage`，历史 Spec 曾记录 `/message/send`，联调时以客户 YAPI `project/557/api/35649` 真实路径为准。
- 响应结构按当前模块实际情况分别说明；没有强行改写成理想统一格式，避免文档与代码不一致。

## 验证

- 本次为文档整理，无代码逻辑变更；验证方式为基于当前源码路径与配置逐项核对接口、请求体、响应示例和限制说明。

# 添加任务附件查看与交付物保存修复实施记录

## 问题口径

- “任务附件可以上传/保存但不能查看”：已保存附件只有项目文档元数据，任务表单没有给 `el-upload` 生成可访问的下载 URL，后端也缺少 `/api/projects/{projectId}/documents/{documentId}/download` 闭环。
- “交付物上传能选择但保存不成功”：任务保存链路只上传 `attachments`，没有处理 `deliverableFiles`；编辑任务时还会把 `File` 对象塞进任务 DTO。

## 决策与权衡

- 保持任务附件和交付物两条现有业务语义：任务附件继续走 `TASK_ATTACHMENT` 项目文档；交付物继续先上传 `TASK_DELIVERABLE` 项目文档，再创建任务交付物元数据。
- 不引入 Mock，不改上传接口；复用现有 `projectStore.addDeliverable()`，只在任务新增/编辑成功后补上传 `deliverableFiles`。
- `taskFormDtoToBackend()` 不再透传 `deliverableFiles`，避免把浏览器 `File` 对象提交到 JSON 任务 DTO。
- 任务附件查看采用项目文档下载 URL：`/api/projects/{projectId}/documents/{documentId}/download`，后端通过现有文件存储端口读取真实文件内容。
- Review 后收敛下载边界：Controller 不再为文件名全量查询项目文档列表，改由 service 一次返回 `fileName`、`contentType`、`contentLength` 和 `Resource`；MIME 推断改用 Spring `MediaTypeFactory`。
- 文件存储读取仍兼容既有 byte[] 返回；当真实路径存在时使用 `FileSystemResource`，否则退回 `ByteArrayResource`，避免扩大到 docinsight 存储深改。
- `TaskForm` 只负责发出 `attachment-preview`，打开新窗口的副作用上移到 `ProjectTaskBoardCard`。
- `TaskDtoMapper` 严格只把 `documentCategory=TASK_ATTACHMENT` 的项目文档映射为任务附件；历史空分类 TASK 文档不再混入附件列表，需通过数据清洗补分类。
- 上传一致性采用最小前端恢复策略：任务已保存但附件/交付物上传失败时保留抽屉不关闭、不给成功提示，用户可直接重试；暂不引入“任务+文件”组合事务接口。
- Linear MCP 未读取：当前会话可用工具中没有 Linear MCP，且本轮提示未提供具体 Linear issue ID/URL。
- 2026-06-21 线上复测补充：`POST /api/projects/14/tasks/377/deliverables` 返回 403 的高概率根因是后端交付物元数据接口要求“仅任务执行人本人可上传交付物”。添加任务抽屉允许管理员选择他人为执行人并同时选择交付物，前端保存链路会先创建任务、再用当前登录人上传交付物；若当前登录人不是新任务执行人，第二步必然 403。
- 本次不放宽后端交付物权限，避免破坏蓝图 §2.3.1 的执行人本人提交规则；最小修复为前端在发现 `deliverableFiles` 且当前用户不是任务 `assigneeId` 时，不再调用交付物上传接口，并提示“仅任务执行人本人可上传交付物，请让执行人打开任务后上传”，避免再次产生“项目文档上传成功但交付物元数据 403”的半成功状态。
- 2026-06-21 20:16 后恢复 SSH 直连并读取 `journalctl`，测试服务器日志确认：`2026-06-21 19:30:53` 后端对 `POST /api/projects/14/tasks/377/deliverables` 返回 `HTTP 403 FORBIDDEN`，原因正是 `仅任务执行人本人可提交/上传交付物`；access log 同时记录 `status=403 elapsed=54ms clientIp=172.16.96.136`。

## 验证计划

- 前端：`pnpm exec vitest run src/composables/projectDetail/useProjectDetailTaskActions.spec.js src/components/project/TaskForm.spec.js src/components/project/ProjectTaskBoardCard.spec.js`
- 后端：`mvn -f backend/pom.xml test -Dtest=TaskDtoMapperTest,ProjectDocumentWorkflowServiceTest,ProjectDocumentControllerTest`

# CO-282 实施记录

## 问题口径

- 本次将“标讯修改接口”理解为外部集成接口 `PUT /api/integration/tenders/{sourceSystem}/{sourceId}` 触发的评估数据保存链路。
- “14 个评估字段”按评估表客户信息矩阵的 14 个 `infoKey` 处理：`NAME`、`CONTACT_INFO`、`POSITION`、`XIYU_CONTACT`、`CONTACT_METHOD`、`INFO_TENDENCY_BASIS`、`CONTACTED`、`GUIDED_BID`、`CAN_GET_KEY_INFO`、`CAN_REMOVE_ADVERSE`、`CAN_SYNC_EVAL`、`TENDENCY`、`INFO_CLEAR_WINNER_BID`、`INFO_WIN_RATE_IMPACT`。

## 决策与权衡

- 外部接口仍兼容 EAV 与 flat 两种入参格式，不改变接口结构。
- flat 格式不再统一保存为 `TEXT`，改为按 `infoKey` 推导标准 `valueType`。
- EAV 格式如果传入缺失、非法或与 `infoKey` 不匹配的 `valueType`，本次选择纠正为标准类型，而不是拒绝请求；原因是外部 CRM 推送需要兼容性，避免一次性破坏已有调用方。
- 2026-06-20 Linear 补充接口文档后，选项类字段按数字索引保存：`POSITION` 1-14、`CONTACT_METHOD` 1-7、`TENDENCY` 1-3、`INFO_WIN_RATE_IMPACT` 1-6；前端用索引值显示中文标签。
- 是否/开关类字段兼容 `true`/`false` 与历史 `是`/`否` 输入，落库统一保存为字符串 `true`/`false`；前端表单内转换为 boolean 供下拉/开关显示。
- 后端仍保留 `CONTACT -> CONTACT_INFO`、`EVALUATION_BASIS -> INFO_TENDENCY_BASIS` 的历史字段名兼容。
- 对 CO-266 的显示问题，保留 `clear()` 后 `saveAndFlush()` 的删除顺序，并让 flat/EAV 两种格式在 `roleKey` 缺失时都生成 `EXTERNAL_ROLE_N`，避免客户信息因无角色键被跳过。

## 补充修复

- 外部接口 `dueDate`、`bidOpeningTime`、`registrationDeadline`、`createDate` 仍严格按文档要求接收 `yyyy-MM-ddTHH:mm` 或 `yyyy-MM-ddTHH:mm:ss`，不把 date-only 静默补全为默认时间。
- 日期时间格式错误现在转换为 `IllegalArgumentException`，由全局异常处理返回 HTTP 400，避免对接方收到 500「系统繁忙」。

## 客户信息矩阵展示优化

- 客户信息矩阵不再把 14 个预设角色配置当作展示数据自动合并；页面只渲染接口/数据库实际返回、且至少包含一个客户信息值的行。
- 固定 `roleKey` 仍可用于解析已有数据的中文角色名，但不会补齐其他未传入的预设行。
- 表格隐藏左侧角色列，避免展示外部生成角色名。
- `职位` 和 `触达方式` 两列宽度分别调整为 220、180，减少下拉值显示不全。

## 2026-06-20 彻底清理游客与缓存残留

- “游客”不是后端真实用户：后端 `/api/auth/me` 仍要求认证，问题来自前端 Header 展示层 fallback 和旧 bundle/cache。
- Header 删除 `guest -> 游客` 和所有 `|| '游客'` 兜底；恢复会话期间显示“加载中”，无显示名时显示“用户”。
- `localStorage/sessionStorage` 中的旧 `user` hint 增加结构校验；坏 JSON 或缺少身份字段的旧缓存会被清理，避免污染 `currentUser`。
- nginx 对 `index.html` 和 SPA fallback 增加 `no-cache/no-store/must-revalidate`，避免部署后旧入口 HTML 长时间加载旧前端 bundle；已打开页面仍需要刷新才能切换到新 JS。

## 未纳入本次范围

- 不处理评估审核 `reviewEvaluation` 前端 API 缺失问题。
- 不处理编辑标讯时可能提交 `status: PENDING_ASSIGNMENT` 的问题。
- 不调整评估状态 `DRAFT`/`SUBMITTED` 业务流转。
- 不清理 `TenderEvaluationFormAdaptive.vue` 旧字段名。
- 不做数据库迁移或大范围重构。

# CRM 商机列表 409 修复实施记录

## 问题口径

- `/bidding` 标讯详情页点击“关联CRM商机”时，前端打开选择器会调用 `POST /api/xiyu/crm/chances/page-list`。
- 现场报错是该只读查询接口返回 HTTP 409，导致用户无法进入商机选择流程。

## 根因

- `page-list` 本身没有本地业务校验会主动返回 409；查询失败后的既有契约也是返回空分页结果。
- 实际 409 来自进入 CRM 查询前的 token 获取：`CrmAuthService.getValidToken()` 在 OSS token / CRM generateToken 失败、配置缺失、外部认证失败或 cooldown 时抛 `IllegalStateException`。
- 全局异常处理会把所有 `IllegalStateException` 映射为 HTTP 409，因此只读 CRM 查询把认证链路故障暴露成了 Conflict。

## 决策与权衡

- 对商机列表这类只读查询，按既有服务契约降级为空列表，不把 token 获取失败继续向外抛成 409。
- 保持真实 CRM API 链路，不引入 Mock 数据，也不伪造商机。
- 不修改 `CrmAuthService` 或全局 `IllegalStateException -> 409` 映射，避免影响项目阶段推进、评估提交等状态机语义。
- 同时覆盖 `pageList` 和 `searchByTender` 内部调用，避免未来前端切回 `search-by-tender` 后复现同类 409。

## 后续注意

- 本次修复能避免前端按钮因 409 阻断；如果仍持续显示空商机列表，需要继续排查部署环境的 CRM 认证配置。
- 重点检查：`XIYU_CRM_AUTH_BASE_URL` / `XIYU_CRM_BASE_URL`、OSS 账号密码、`XIYU_CRM_OAUTH_SYSTEM`、`XIYU_CRM_GENERATE_TOKEN_NICK_NAME`、`XIYU_CRM_GENERATE_TOKEN_SALES_NO`。
- 运行时日志关键词：`OSS applyToken failed`、`Cannot acquire CRM token`、`CRM generateToken failed`、`token apply in cooldown`。

## 2026-06-20 补充修复与回归覆盖

- 这是功能 bug：用户点击“关联CRM商机”属于只读查询入口，CRM 认证或上游故障不应该通过全局 `IllegalStateException -> 409` 暴露成业务冲突，导致页面流程被阻断。
- 未全局修改 `IllegalStateException` 的 HTTP 映射；该异常在其他模块可能承载状态机冲突语义。CRM 查询在本服务边界内做精准兜底，降低跨模块回归风险。
- `pageList` / `searchByTender` 在外部 CRM 返回 401 后刷新 token 时，如果刷新失败，现在记录明确日志并返回空分页，避免二次 token 异常继续漏成 409。
- 今天 PR 中发现“查询 CRM 对接人失败”曾被改成阻断商机关联；本次恢复为非阻断：给出 warning，继续关联商机，只是不自动带入客户信息。
- 新增/调整回归测试覆盖：
  - `CrmChanceServiceTest`：初始 token 获取失败、401 后 token 刷新失败时，`pageList` 与 `searchByTender` 都返回空分页。
  - `CrmChanceControllerIntegrationTest`：`/api/xiyu/crm/chances/page-list` 在服务返回空分页时保持 HTTP 200 契约。
  - `useCrmOpportunitySelector.spec.js`：CRM 对接人查询失败时仍 emit `linked`，且 `customerInfos` 为空数组。
  - `src/api/modules/crm.spec.js`：锁定商机查询与对接人查询 API endpoint/body，防止路径和请求体漂移。

## 2026-06-20 服务器日志补充根因

- 在客户测试服务器 `172.16.38.78` 只读排查后，确认 `/etc/xiyu-bid/backend.env` 已配置 `XIYU_CRM_AUTH_BASE_URL`、`XIYU_CRM_BASE_URL`、`XIYU_CRM_CHANCE_BASE_URL`、`XIYU_CRM_OAUTH_USERNAME`、`XIYU_CRM_OAUTH_PASSWORD`、`XIYU_CRM_GENERATE_TOKEN_NICK_NAME`、`XIYU_CRM_GENERATE_TOKEN_SALES_NO` 等变量。
- 但运行日志持续出现 `OSS oauth login: baseUrl=null, path=/oauth/login, username=`，说明应用没有把服务器环境变量绑定进 `app.crm.*`。
- 代码追溯发现：`CrmProperties` 绑定前缀是 `app.crm`；完整 CRM 环境变量映射曾主要存在于 `application-dev.yml`，而 `application-prod.yml` 只保留了精简生产配置，没有覆盖 CRM 映射。测试此前只覆盖服务行为，没有覆盖 prod profile 合并后的 CRM 配置绑定，因此漏过。
- 本次把 `app.crm.*` 环境变量绑定上移到通用 `application.yml`，让 prod/dev 等 profile 共享同一映射；新增 `ProductionSecurityPropertiesTest.productionProfileKeepsCrmBindingsExternalized` 防止以后生产 profile 再漏掉 CRM 绑定。
- 产生原因总结：配置映射与运行环境文件分离维护，且缺少“生产 profile 配置绑定”回归测试；后续新增外部集成变量时，应优先放在通用配置并用 prod profile 合并测试锁定。

## 2026-06-20 工程化防复发补强

- 配置契约测试从“只检查少量 CRM 字段”扩展为生产 profile 合并测试：锁定 CRM base-url、认证字段、token 交换字段、核心路径、匹配策略，以及组织架构外部集成的开关、目录 URL、路径、header、超时、重试和对账配置。
- 新增启动期安全配置摘要日志，只打印外部集成字段是否已配置、开关状态和策略名，不打印 URL 明文、账号、密码、token、client secret 等敏感值；目标是在日志里一眼发现 `baseUrl` 未绑定这类问题。
- 生产 smoke 增加 CRM `page-list` 只读探针：默认 `CRM_SMOKE_MODE=optional` 锁定 HTTP 200/响应结构/禁止 409；客户测试和生产可用真实 CRM 时使用 `required`，空商机也阻断发布。
- 服务器端 post-deploy smoke 增加后端日志扫描，发现 `baseUrl=null`、`Cannot acquire CRM token`、`OSS token acquisition failed` 直接失败。
- 本次没有修改 `application.yml` 补充 CRM `chance.*` / `contact-person.*` path env 映射，因为该文件仍被上一轮 CRM 配置修复分支的有效 agent lock 持有；当前防线先锁定已有共享映射，后续如要补 path 映射需先释放或接管锁。

# CO-287 标讯中心来源平台筛选项修正实施记录

## 问题口径

- 标讯中心筛选区“来源平台”下拉原来复用标讯源配置项，包含 `CRM商机转入`、`第三方标讯平台名称` 等非后端 `source` 字段真实值。
- 前端查询仍通过 `source` 参数过滤，后端按 `sourceNormalized` 精确匹配；筛选项值与数据值不一致会导致筛不到对应标讯。

## 决策与权衡

- 筛选区与标讯源配置弹窗均统一使用真实 `source` 字段值：`人工录入`、`CRM创建`、`第三方平台`，避免同一“来源平台”概念在页面内出现两套口径。
- 用户补充要求 `CRM 创建` 统一为 `CRM创建`，因此同步了标讯来源值链路中的展示/序列化/测试预期；否则筛选项传 `CRM创建` 但后端仍产出 `CRM 创建` 会继续不匹配。
- 没有做数据库迁移；若历史数据 `source` 字段已落过 `CRM 创建`，当前通过后端查询兼容匹配，并在前端展示层归一为 `CRM创建`。
- Review 后补齐标讯源配置弹窗测试连接链路：平台判断、提示文案、测试连接请求平台值均改为 `第三方平台`，避免弹窗选项统一后按钮/请求仍使用旧平台名。

## 验证计划

- 前端：运行 `TenderSearchCard.spec.js`、来源展示 helper 相关测试，以及必要的前端门禁。
- 后端：运行 `TenderSourceTypeTest` 和 CRM 标讯来源映射相关测试，确认 `CRM创建` 序列化/反序列化和集成映射一致。

## 2026-06-21 Review 修复补充

- P1：后端来源过滤归一化现在会跳过空值、去重，并在查询 `CRM创建` 时兼容历史 `CRM 创建` 数据；外部集成映射改为复用 `Tender.SourceType` 标签，降低后端标签重复维护风险。
- P2：前端新增共享来源标签常量，标讯中心筛选、标讯源配置、手工创建默认值、列表展示和项目列表展示统一复用同一组来源标签；保留 `CRM 创建` 和 `批量导入` 作为历史数据展示兼容入口，不作为新的配置/筛选项。

# CO-293 跨部门协同任务执行人项目可见性修复实施记录

## 问题口径

- 跨部门协同人员被分配为项目任务执行人后，应该能查看该任务所属项目，否则无法进入项目上下文处理任务。
- 本次将问题定位在统一项目访问范围，而不是前端列表展示或菜单权限。

## 决策与权衡

- 最小改动放在 `ProjectAccessScopeService`：把“当前用户作为任务执行人的项目”纳入项目可访问 ID 集合。
- 不调整角色菜单、数据范围配置或前端路由；`bid_other_dept` 的基础数据权限仍保持受限，只因具体任务分配获得对应项目可见性。
- 不修改任务流转策略；执行人是否能提交/审核仍由既有 `ProjectTaskAuthorizationPolicy` 控制。
- 不新增数据库迁移；直接复用已有 `tasks.assignee_id` 与 `tasks.project_id` 数据。

## 权限边界补充

- 2026-06-21 复核 Linear CO-293 原文后，确认“处理待办”入口是工作台跳转 `/project?tab=todo`，不是通知卡片直达单个任务；因此 P0 需要项目列表包含任务执行人所属项目。
- 本次按 Linear P0 处理为“项目级可见性”：任务执行人可在项目列表看到对应项目，并通过项目详情入口。项目详情内部模块/tab 级隔离属于 Linear 记录的 P1，未在本次最小修复中实现。
- 已复核项目工作流任务与普通任务均落在 `tasks` 表，对应 `com.xiyu.bid.entity.Task` / `TaskRepository`，本次没有接入错误的任务表。
- 既有 `TaskProjectVisibilityPolicy` 中 `allowedProjectIds` 为空时视为全量可见，而 `ProjectAccessScopeService` 的项目列表过滤中空集合代表无可见项目；这是既有语义不一致。本次不顺手修改，避免扩大 CO-293 范围，但后续做 P1 或任务权限收口时应统一该语义。

## 验证

- 已先补 `ProjectAccessScopeServiceTest` 回归测试，RED 阶段确认生产代码缺少任务执行人项目范围。
- GREEN 后运行 `mvn test -Dtest=ProjectAccessScopeServiceTest` 通过。
- 补跑 `mvn test -Dtest=ProjectAccessGuardCoverageTest` 通过。

## 2026-06-22 菜单与项目详情补充修复

- 复核后确认 PR #918 只把任务执行人所属项目加入后端可访问项目范围，没有处理前端菜单权限。
- “任务看板”是独立顶层菜单，前端 Sidebar/Router 依赖 `task-board`；工作台组件权限 `dashboard:view_technical_task` 不能让 `/task-board` 可见或可访问。
- 本次只给 `bid_other_dept` 补充 `task-board`，不补 `project`、`bidding`、`knowledge`、`resource`，避免把跨部门协同人员扩大成普通员工/完整项目角色。
- 由于已有 DB 中的 `roles` 记录不会被 RoleProfileBootstrap 覆盖，新增 V1089 数据迁移对既有 `bid_other_dept` 角色幂等追加 `task-board`。
- PR #918 的 scope 生效仍会被 `GET /api/projects/{id}` 方法级 `hasAnyRole('ADMIN','MANAGER','STAFF')` 提前拦截；本次仅把项目详情接口调整为 `isAuthenticated()`，继续由 `ProjectAccessScopeService.assertCurrentUserCanAccessProject` 做对象级 403。
- 不修改项目列表、项目创建/更新/删除、任务流转或任务看板页面生产代码。

# CO-290 提交投标未完成任务闸门修复实施记录

## 问题口径

- 投标文件审核通过后，用户点击项目详情页“提交投标”，后端 `POST /api/projects/{projectId}/drafting/submit-bid` 返回 409：`仍有 1 个任务未完成，无法提交投标`。
- 本次只处理提交投标被未完成任务阻断的问题；控制台中 `/api/tenders/206`、`/api/projects/*/initiation`、`/api/tenders/206/evaluation` 的 404 视为不同链路，未纳入本次范围。

## 决策与权衡

- `submitBid` 已有投标文件审核通过校验，本次按新文档审核流语义，让审核通过后的提交投标不再复用“全部任务已完成”闸门。
- 保留 `/drafting/advance` 的全部任务完成校验，避免影响仍依赖任务完成推进阶段的旧入口。
- 不修改 `AllTasksCompletedPolicy`，也不把 `REVIEW` 视为完成态；任务终态仍只有 `COMPLETED` / `CANCELLED`。
- 未调整权限、阶段状态机、前端按钮或数据库结构。

## 验证

- TDD RED：新增 `ProjectDraftingServiceTest.submitBid_approvedReview_allowsIncompleteTasks` 后，确认原实现返回 409。
- GREEN：移除 `submitBid` 内未完成任务闸门后，单测 `mvn test -Dtest=ProjectDraftingServiceTest#submitBid_approvedReview_allowsIncompleteTasks` 通过。
- 回归：`mvn test -Dtest=ProjectDraftingServiceTest,AllTasksCompletedPolicyTest,BidReviewPolicyTest` 通过，54 tests。
- 补充权限覆盖：`mvn test -Dtest=ProjectAccessGuardCoverageTest` 通过。

## 2026-06-21 服务器 500 补充修复

- 服务器日志确认：任务闸门移除后，项目 13 已执行到 `DRAFTING→EVALUATING`，随后阶段变更通知插入 `notification` 表时报 `Column 'created_by' cannot be null`，导致接口从原 409 变为 500。
- 根因在 `ProjectNotificationService.notifyStageTransition` 旧三参方法用 `null` 作为通知创建人；`notification.created_by` 为非空列，且通知写入异常会污染当前事务。
- 本次新增四参 `notifyStageTransition(..., userId)`，`submitBid` 传入当前提交人作为通知创建人；旧三参方法保留并使用系统用户 `0L` 兜底，避免其他旧调用继续传空。
- 新增回归测试锁定：阶段变更通知使用真实操作者；旧三参签名也不再传 `null createdBy`。
- 回归：`mvn test -Dtest=ProjectNotificationServiceTest,ProjectDraftingServiceTest,AllTasksCompletedPolicyTest,BidReviewPolicyTest,ProjectAccessGuardCoverageTest` 通过，83 tests。

# 提交投标负责人角色分配修复实施记录

## 问题口径

- 线上“投标负责人也提交不了”的直接原因是授权模型把 `bid_specialist` 只当作辅助人员匹配 `secondaryLeadUserId`。
- 业务更正：投标负责人不应硬限制为 `sales`（投标项目负责人）；实际通常会选择 `bid_specialist`（投标专员）担任投标负责人。
- 因此，`bid_specialist` 被写入 `primaryLeadUserId` 是合法业务数据，不应要求管理员改成主/副字段互换。

## 决策与权衡

- 放宽提交投标授权：`bid_specialist` 匹配 `primaryLeadUserId` 或 `secondaryLeadUserId` 均可提交；`sales` 仍只匹配 `primaryLeadUserId`。
- 移除立项审批和标书制作阶段分配主/副负责人时的角色硬校验，保留“主负责人必填、主/副不能相同”等结构校验。
- 前端“投标负责人”搜索不再过滤为 `sales`，避免再次挡住正常选择投标专员；“投标辅助人员”搜索仍保留 `bid_specialist` 过滤。
- 用户搜索 DTO 保留 `roleCode` 字段，供辅助人员筛选和后续展示使用；不返回邮箱、密码等敏感字段。
- 不需要数据库迁移；既有 `bid_specialist` 作为 `primaryLeadUserId` 的线上项目在新授权下可直接提交。

## 验证

- 后端：`mvn -f backend/pom.xml -Dtest=BidSubmissionAuthorizationPolicyTest,ProjectDraftingServiceTest,ProjectInitiationApprovalServiceTest,UserSearchServiceTest,UserSearchControllerTest test` 通过，57 tests。
- 前端：`pnpm vitest run src/composables/projectDetail/useProjectDraftingPermissions.spec.js src/views/Project/stages/InitiationStage.spec.js src/api/modules/users.spec.js` 通过，70 tests（保留既有 Element Plus stub 警告）。
- 质量检查：`git diff --check && npm run check:line-budgets && npm run agent:lock-check:changed` 通过。

# OSS 二级菜单权限同步修复实施记录

## 问题口径

- 06288 在 OSS 已配置二级菜单权限，但本地角色权限没有出现对应 `bidding-list`、`project-list`、`analytics-dashboard` 等内部权限 key。
- 现场日志说明 OSS 登录成功，但登录链路不会刷新菜单权限；菜单权限依赖组织同步/手动同步链路把 OSS 菜单树映射进本地角色。

## 决策与权衡

- 不把菜单权限同步塞进登录链路，避免每次登录都依赖 OSS 菜单树接口的性能与稳定性；本次先修复组织同步/手动同步链路。
- 补齐默认 OSS 数字菜单编码映射，只映射当前前端已有明确权限 key 的菜单；没有明确权限 key 的 OSS 菜单继续按 `IGNORE` 丢弃，避免误授权。
- `fetchUserMenuTree` 原来只在日志中记录 `jobNumber`，请求没有携带用户工号；本次默认以 `jobNumber` query 参数传给 OSS，并把参数名做成配置 `user-menu-tree-job-number-param-name` 以兼容接口合同变化。
- `Authorization` 等额外鉴权不硬编码；新增 `auth-header-name` / `auth-token` 可选配置，只有配置完整时才发送，且不在日志中输出 token。
- 同步成功后仍需要对目标用户/角色触发一次组织菜单同步，已落库角色权限不会仅因代码发布自动刷新。

## 验证计划

- 后端：`mvn test -Dtest=OssMenuPermissionMapperTest,OrganizationDirectoryHttpGatewayTest,OrganizationRoleMenuSyncAppServiceTest`
- 架构：视测试耗时补跑 `mvn test -Dtest=ArchitectureTest`

# CO-303 本地上传附件下载 403 修复实施记录

## 问题口径

- CRM 通过外部集成接口拿到本地上传附件链接后，浏览器直接点击下载时无法携带 `X-API-Key` Header。
- `/api/doc-insight/download` 是内部登录态下载端点，要求 JWT；CRM 直接点击该链接出现 403 属于权限边界按设计生效。
- 本次复现到的缺口是：当附件链接已经被补全为 `https://.../api/doc-insight/download?...` 完整 URL 时，外部 URL 归一化没有把它改写成 `/api/integration/tenders/attachments/download`，导致 CRM 仍拿到内部端点。

## 决策与权衡

- 不放宽 `DocInsightController` 的 `isAuthenticated()`，避免让内部下载端点同时承担 API Key 认证，扩大权限面。
- 继续复用已有集成下载端点 `/api/integration/tenders/attachments/download` 和 `ApiKeyAuthenticationFilter`。
- 只在 `TenderAttachmentUrlResolver` 中补齐“本站完整内部下载 URL”的识别与改写；外部 `http(s)://` 文件地址仍保持原样，不误改第三方链接。
- `api_key` 追加逻辑保持在外部调用上下文路径中，解决 CRM 浏览器点击无法带 Header 的问题。

## 验证

- TDD RED：`mvn test -Dtest=TenderIntegrationMapperToDownloadUrlTest#toIntegrationFullUrl_absoluteLegacyDocInsightUrlWithApiKey_redirectsToNewEndpoint` 先失败，实际仍返回 `https://winbid-test.ehsy.com/api/doc-insight/download?...`。
- GREEN：补齐完整内部下载 URL 改写后，上述单测通过。
- Review 后补强：`api_key` 只追加到本站集成下载端点；外部 URL 即使路径文本包含 `/api/integration/tenders/attachments/download` 也保持原样，避免泄露 API Key。
- 回归：`mvn test -Dtest=TenderIntegrationMapperToDownloadUrlTest,CallerContextUrlResolverTest,TenderAttachmentDownloadServiceTest` 通过，48 tests。

## 2026-06-22 二次根因修复

- 服务器日志确认第一轮 URL 改写已生效：CRM 点击的地址已经是 `/api/integration/tenders/attachments/download?...&api_key=...`，且 `ApiKeyAuthenticationFilter` 打出 `API Key auth OK`。
- 同一请求随后仍返回 403，日志中的拒绝用户变成普通登录用户 `06234`，说明浏览器同站请求自动带上了西域登录态 `access_token` Cookie。
- 根因是 `JwtAuthenticationFilter` 在 API Key 过滤器之后执行时无条件写入 `SecurityContext`，把 `api-key:3` / `ROLE_EXTERNAL_API` 覆盖成普通 JWT 用户，导致 `TenderAttachmentDownloadController` 的 `hasRole('EXTERNAL_API')` 方法级鉴权失败。
- 本次不调整 filter 顺序、不放宽集成下载 Controller 权限、不把内部 JWT 用户加入 `ROLE_EXTERNAL_API`；只在 `JwtAuthenticationFilter` 中识别已有 `api-key:` 认证上下文并直接放行，避免 JWT Cookie 覆盖更具体的 API Key 身份。
- 新增回归测试覆盖“API Key 上下文 + 同时存在 `access_token` Cookie”场景；RED 阶段断言失败，实际 principal 被覆盖为 `06234`；GREEN 后保持 `api-key:3` 和 `ROLE_EXTERNAL_API`。
- 验证：`mvn test -Dtest=JwtAuthenticationFilterRevocationTest,TenderIntegrationMapperToDownloadUrlTest,CallerContextUrlResolverTest,TenderAttachmentDownloadServiceTest` 通过，52 tests；`git diff --check`、`npm run check:line-budgets`、`npm run agent:lock-check:changed` 均通过。

# 标讯附件空 URL 入库修复实施记录

## 问题口径

- 测试环境 `/bidding/400` 的标讯附件列表存在文件名但 `fileUrl=""`，因此详情页下载按钮拿不到可下载地址。
- 根因不是下载接口本身，而是新建标讯页在文件选择后先把 Element Plus `fileList` 放入 `form.attachments`，但存储/解析异步完成前用户仍可保存，导致空 URL 附件被提交。
- 后端 `TenderCommandService.saveAttachments()` 只跳过“文件名和 URL 都为 null”的条目，允许“文件名存在但 URL 为空字符串”的坏数据入库。

## 决策与权衡

- 新建页文件处理改为“先存储、后解析”：选择文件后先调用 `/api/doc-insight/store` 获得 `fileUrl/storagePath` 并立即回填附件，再用 `/api/doc-insight/parse-existing` 做 AI 识别增强。
- 若存储失败，仍保留原 `/api/doc-insight/parse` 一站式解析作为回退；但保存阶段会校验附件 URL，避免再把空 URL 附件提交到后端。
- 保存按钮在 `parsingDocument` 为 true 时禁用，`handleSave()` 也增加二次防线，避免通过事件或状态竞争绕过按钮禁用。
- `buildManualTenderPayload()` 对“有文件名但没有 fileUrl”的附件直接抛错，不做静默过滤，原因是静默过滤会造成用户以为附件已保存但实际丢失。
- 后端在创建/更新标讯变更实体或删除旧附件前校验附件元数据；发现 `fileName` 有值但 `fileUrl` 为空时返回 `BusinessException(400, "标讯附件未完成上传，请重新上传后再保存")`，避免部分写入或误删旧附件。

## 验证

- RED：新增前端/后端回归测试后，确认旧实现会失败：前端未调用 `storeTenderDocument`、保存按钮未受 `parsingDocument` 控制、payload 仍生成空 `fileUrl`；后端创建/更新未按 400 拒绝。
- GREEN 前端：`pnpm vitest run src/views/Bidding/list/composables/useTenderAiParse.spec.js src/views/Bidding/list/helpers.spec.js src/views/Bidding/TenderCreatePage.spec.js` 通过，45 tests。
- GREEN 后端：`mvn -f backend/pom.xml test -Dtest=TenderCommandServiceTest` 通过，12 tests（2 skipped，保留既有 disabled 用例）。

# 标讯附件下载 403 修复实施记录

## 问题口径

- CRM 附件下载已恢复，但西域数智化投标管理平台自己的标讯详情页点击附件下载时显示 Whitelabel 403。
- 本次聚焦标讯详情页“标讯文件”下载链路，不改变 CRM 集成下载端点。

## 根因

- 标讯详情页将 `doc-insight://...` 转换为 `/api/doc-insight/download?fileUrl=...` 后调用通用 `downloadWithFilename()`。
- `downloadWithFilename()` 使用原生 `fetch(url, { credentials: 'include' })`，不会复用项目 axios/httpClient 的认证与刷新链路。
- 当前系统登录态由项目 `httpClient` 统一处理；下载请求绕开它后访问受保护的 `/api/doc-insight/download`，后端返回 403，随后 fallback `window.open(url)` 打开同一个受保护 URL，于是浏览器显示 Whitelabel 403。
- CRM 能下载是因为走 `/api/integration/tenders/attachments/download` 集成端点，与内部标讯详情页下载不是同一前端链路。

## 决策与权衡

- 最小修复在前端下载工具：凡是 `/api/**` 下载 URL 改走 `httpClient.get(..., { responseType: 'blob' })`，复用现有登录态/刷新/错误处理链路。
- 外部 http(s) 文件仍保持原生 `fetch` 逻辑，避免把第三方下载地址错误送进业务 axios 拦截器。
- 不放开 `/api/doc-insight/download` 后端匿名访问，避免把内部附件下载接口扩大成公开下载面。
- 保留 `Content-Disposition` 文件名解析和 blob 下载行为。

## 验证

- 新增回归测试：`src/utils/download.spec.js` 覆盖 API 下载必须调用 `httpClient.get` 而不是 `fetch`。
- 补齐 `BasicInfoReadOnly.spec.js` 对 `@/api/config.js` 的 mock，使详情页附件 URL 测试继续覆盖。
- 已运行：`pnpm vitest run src/utils/download.spec.js src/views/Bidding/detail/components/BasicInfoReadOnly.spec.js`，19 tests 通过。

# 标讯附件下载跨 Host 403 修复实施记录

## 问题口径

- 用户从 `http://172.16.38.78:8080/bidding/401` 访问系统页面，但点击标讯附件后打开 `https://winbid-test.ehsy.com/api/doc-insight/download?...`，浏览器不会把 IP Host 下的 HttpOnly 登录 Cookie 发送给域名 Host。
- 后端 `/api/doc-insight/download` 要求 `isAuthenticated()`，因此跨 Host 下载请求被判定为未登录并返回 403，用户看到 Whitelabel Forbidden 页面。

## 决策与权衡

- 内部 `/api/...` 下载统一改为同源相对路径请求：不管后端返回 IP、域名还是相对 URL，前端下载工具最终都用 `/api/...` 调用 `httpClient`，让浏览器按当前页面 origin 携带 Cookie。
- 标讯详情页对 `doc-insight://` 和绝对内部 API URL 做同源归一化，避免组件层继续传递跨 Host 内部下载地址。
- API 下载失败时不再 fallback 到 `window.open(originalUrl)`，避免把用户带到 Whitelabel；401/403 改为明确提示“登录已过期或访问入口不一致”。
- 非 API 外部文件下载仍保留原有 `window.open` fallback，避免扩大影响范围。

## 验证计划

- 前端：`pnpm exec vitest run src/utils/download.spec.js src/views/Bidding/detail/components/BasicInfoReadOnly.spec.js`。

# CO-317 行政人员点击资质证书触发 /api/tenders 403

## 问题口径

- `admin_staff` 点击「资质证书」时观测到 `/api/tenders` 返回 403。
- 代码排查确认资质证书页面自身请求 `/api/knowledge/qualifications`，但全局或并行页面逻辑可能触发标讯列表请求。
- Linear 页面因认证/JS 加载限制未能读取 issue 正文和评论；本次依据 issue 标题与代码路径做最小修复。

## 根因

- `admin_staff` 被显式排除在 legacy `ROLE_STAFF` 兼容外。
- `TenderController` 类级授权包含 `ADMIN_STAFF`，但 `GET /api/tenders` 方法级授权仅包含 `ADMIN/MANAGER/STAFF`，方法级鉴权覆盖后导致只读列表 403。

## 决策与权衡

- 按用户确认的方案 A，只放开 `GET /api/tenders` 的只读列表权限，增加 `ADMIN_STAFF`。
- 不修改标讯详情、创建、修改、删除、投标/弃标决策、角色目录或前端资质页面，避免扩大写权限和无关范围。

## 验证

- TDD RED：先在 `TenderControllerBidOtherDeptAccessTest` 中增加 `admin_staff` 访问列表应 200、创建/删除仍 403 的回归用例；修复前列表用例返回 403。
- GREEN：仅修改 `TenderController#getAllTenders` 的 `@PreAuthorize` 后，执行 `mvn -f /Users/user/xiyu/worktrees/zcode/backend/pom.xml test -Dtest=TenderControllerBidOtherDeptAccessTest` 通过，6 tests，0 failures，0 errors。
- 保护性断言确认 `admin_staff` 仍不能创建/删除标讯。

## 2026-06-23 线上 06643 二次根因

- 后端部署后读取测试服务器日志确认，用户 `06643` 登录时 OSS 角色解析为 `admin_staff`，菜单编码 `[1001, 100402]` 映射为 `[knowledge-qualification, dashboard]`。
- 同一测试窗口内 `/api/tenders?size=10000` 已返回 200，未再出现该接口 4xx/5xx，说明原后端 `/api/tenders` 403 已修复。
- 剩余阻断来自前端路由权限：资质证书路由要求同时具备 `knowledge` 与 `knowledge-qualification`，但 OSS 登录链路只返回子权限 `knowledge-qualification`，缺少父级 `knowledge`。
- 本次最小修复放在前端认证 DTO 归一化边界：任一 `knowledge-*` 子权限存在时补齐父级 `knowledge`；不修改 `hasAllPermissions` 全局语义，不补其他子权限，不扩大后端或 OSS 权限。

# OSS 用户 Legacy Role 兼容回归测试补强实施记录

## 问题口径

- 曾有实现把 OSS cache hit 用户统一设置为 `skipLegacyCompat=true`，导致 `/bidAdmin`、`bid-TeamLeader`、`bid-projectLeader` 等管理/负责人角色不再获得 `ROLE_ADMIN` / `ROLE_MANAGER`。
- 系统仍有大量历史接口使用 `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`，上述角色缺少 legacy authority 会造成批量 403。

## 决策与权衡

- 当前生产代码已使用 `RoleProfileCatalog.shouldSkipLegacyRoleCompat(roleCode)` 按角色目录决定是否跳过 legacy 兼容，本次不改生产逻辑。
- 不简单给所有 OSS 用户恢复 `ROLE_MANAGER`：`bid-Team`、`bid-administration`、`bid-otherDept` 是受限角色，若继承 `ROLE_MANAGER` 会误入旧接口白名单。
- 继续保留 OSS cache miss fail-closed，不回退 DB roleProfile，避免 OSS 用户拿到过期或被篡改的本地高权限。
- 本次补强单元测试锁定两类边界：管理/负责人 OSS 角色必须保留 legacy 兼容；受限 OSS 角色必须不继承 legacy 兼容。

## 验证计划

- 后端：`mvn -f backend/pom.xml test -Dtest=UserDetailsServiceImplTest`。

# 分配/指派候选人不可选修复实施记录

## 问题口径

- 分配标讯/指派标讯弹窗进入后，`UserPicker` 候选人加载失败，浏览器控制台显示 `GET /api/users/assignable-candidates?context=tender&deptCode=&roleCode=` 返回 HTTP 500。
- 即使接口成功返回，后端候选人 DTO 字段是 `userId` / `deptName`，而前端 `UserPicker` 合并候选项时按 `id` 过滤，存在候选人被丢弃的隐患。

## 根因判断

- `UserRepository.findByEnabledTrue()` 将 JPA `User` 实体列表直接放入 Redis cache，线上 `spring.cache.type=redis` 时容易因实体序列化/反序列化、懒加载对象或代理对象导致候选人接口 500。
- `AssignmentCandidateController` 通过 `@AuthenticationPrincipal User` 获取当前用户，但实际 `JwtAuthenticationFilter` 放入的是 Spring Security `UserDetails`，导致当前用户在控制器内可能为 `null`，进而影响部门范围过滤。
- 前端 API 层没有把 `AssignmentCandidateDTO.userId` 归一成通用选人组件需要的 `id`。

## 决策与权衡

- 移除 `users:enabled` 实体列表缓存，保持候选人接口直接查询真实启用用户；本次不引入新的 Redis DTO 缓存，避免在紧急修复中扩大缓存一致性设计。
- 控制器改为从 `Authentication#getName()` 获取认证用户名，再通过 `UserRepository.findByUsername()` 解析业务 `User`，与 JWT 过滤器当前 principal 类型对齐。
- 若认证用户暂时查不到业务 `User`，仍按既有服务空用户路径执行，不在本次把行为改成 404/401，避免改变现有权限服务的兜底语义。
- 前端在 `usersApi.getAssignableCandidates()` 边界补齐 `id`、`departmentName` 和 `employeeId`，让 `UserPicker` 继续消费统一字段，不要求后端为了单个组件改变 DTO 契约。
- Review 后收口设计弯路：新增通用 `CurrentUserLookupService` 承担 `UserDetails -> User` 解析，`AssignmentCandidateController` 改为接收 `@AuthenticationPrincipal UserDetails`，不再直接依赖 `UserRepository` / `SecurityContextHolder`。
- 认证用户无法解析为业务用户时改为 fail-fast，由全局认证异常处理返回 401，避免静默返回空候选人掩盖账号同步问题。
- 前端新增 `normalizeUserOption()` 作为统一用户选项归一化 helper，并对非数组响应返回空列表，避免 API 响应异常时组件层崩溃。

## 验证

- RED 前端：`pnpm exec vitest run src/api/modules/users.spec.js`，新增候选人归一化用例按预期失败，缺少 `id` / `departmentName`。
- RED 后端：`mvn -f backend/pom.xml test -Dtest=AssignmentCandidateControllerTest`，新增当前用户解析用例按预期失败，`UserRepository.findByUsername("manager")` 未被调用。
- RED 前端补强：`pnpm exec vitest run src/api/modules/users.spec.js`，新增 `employeeId` 归一化和非数组响应用例按预期失败。
- RED 后端补强：`mvn -f backend/pom.xml test -Dtest=AssignmentCandidateControllerTest`，新增通用当前用户解析服务用例先因 `CurrentUserLookupService` 不存在编译失败。
- GREEN 前端：`pnpm exec vitest run src/api/modules/users.spec.js src/components/common/UserPicker.spec.js src/composables/useUserPicker.spec.js`，3 files / 20 tests passed（保留既有 Vue lifecycle warning）。
- GREEN 后端：`mvn -f backend/pom.xml test -Dtest=AssignmentCandidateControllerTest,AssignmentCandidateAppServiceTest,AssignmentCandidatePolicyTest`，22 tests passed。
- `git diff --check`：通过，无空白错误。
# 选人控件缺失工号后端修复实施记录

## 问题口径

- 上一轮修复后，选人控件不再报"找不到工号"错误，但工号也完全消失了——下拉列表只显示姓名，不再显示"姓名（工号）"格式。
- 用户确认：先后端分析不改代码，发现上一轮合入时其他 agent 的改动没有覆盖/污染我的文件，是个独立的新 bug。

## 根因

三个原因叠加：

1. **数据库 `employee_number` 为 NULL**：V1095 迁移只建列+索引，不做数据 backfill。所有已有用户 `employee_number = NULL`。
2. **候选人路径无 username 降级**：`AssignmentCandidatePolicy.toDTO()` 直接取 `user.getEmployeeNumber()`，没有像 `UserSearchService` 那样 fallback 到 `username`。
3. **后端 DTO 不含 `username` 字段**：`UserSearchResult` 和 `AssignmentCandidateDTO` 都没有 `username` 字段，前端 `firstNonBlank(..., user.username)` 的最终降级链永远拿到 `undefined`。

## 决策与权衡

- **不 backfill 数据库**：应用层降级比数据库回填更安全、可逆。等到组织同步持久化工号后再统一回填。
- **在 `User.java` 实体上集中 `getDisplayEmployeeNumber()`**：遵循业务实体已有的 `getRoleCode()`/`getRoleName()` 回退模式。一个方法覆盖所有调用方，消除 `UserSearchService` 的私有方法重复。
- **同时修复两条后端路径**：搜索结果路径（`/api/users/search`）已有 fallback，委托到实体方法；候选人路径（`/api/users/assignable-candidates`）加上 fallback。

## 改动摘要

4 个文件，最小改动：

| 文件 | 改动 |
|---|---|
| `backend/.../entity/User.java` | 新增 `getDisplayEmployeeNumber()`：`employeeNumber` 非空非空白返回，否则 `username` |
| `backend/.../mention/service/UserSearchService.java` | 删除私有 `employeeNumberOrUsername()`，改委托 `u.getDisplayEmployeeNumber()` |
| `backend/.../user/core/AssignmentCandidatePolicy.java` | `toDTO()` 中 `user.getEmployeeNumber()` → `user.getDisplayEmployeeNumber()` |
| `backend/.../user/core/AssignmentCandidatePolicyTest.java` | 新增 2 个测试：有值保留、null 时降级 username |

## 验证

- `mvn -f backend/pom.xml test -Dtest=UserSearchServiceTest,AssignmentCandidatePolicyTest,AssignmentCandidateControllerTest`：28 tests passed，0 failures，0 errors。
- `git diff --check`：通过，无空白错误。

# 用户选择框标准化 + 拼音搜索实施记录

## 问题口径

- 用户选择框（转派/指派标讯）使用 `mode="candidates"`（固定候选列表），只能选下拉预加载的人。几千人的组织里无法搜索未预加载的用户。
- 需要按 **姓名**、**工号**、**姓名拼音** 进行搜索。
- **投标负责人选择框（`mode="search"`）是正确的标准**，系统中所有选择框应统一为该行为。

## 决策与权衡

1. **拼音方案选择**：选择在数据库增加 `full_name_pinyin` 列，配合 `@PrePersist`/`@PreUpdate` 生命周期钩子在应用层自动维护。不选择全运行时计算（性能差），不选纯 SQL 函数（MySQL 8.0 无内置拼音函数）。
2. **Pinyin4j 库**：使用 `com.belerweb:pinyin4j:2.5.1` — 成熟、轻量、无外部依赖。Maven 代理问题通过 `pom.xml` 直接引用，仓库中心已有。
3. **拼音存储格式**：**全拼连续无空格**（`"张三" → "zhangsan"`，不是 `"zhang san"`）。这样 `LIKE '%zhangsan%'` 匹配全拼搜索，`LIKE '%ang%'` 也匹配部分拼音。
4. **`mode="candidates" → "search"` 转换**：3 处 Bidding 相关的转派/指派对话框改为 `mode="search"`，移除 `context="tender"`（search mode 不使用 context）。`@select` 事件和 `v-model` 完全兼容。
5. **`fullNamePinyin` 的 `@PreUpdate` 策略**：每次更新时重新计算拼音（不设条件），确保 fullName 改变时拼音列始终同步。`@PrePersist` 只在 null 时设置，兼容初始未见 persister 或直接 JDBC 写入的情况。
6. **不修改 `UserPicker.vue` 或 `useUserPicker.js`**：两个 mode 已支持完善，无需改 composable。
7. **不修改已为 `mode="search"` 的其他组件**（ProjectSearchCard.vue, InitiationStage.vue 等）：它们本来就是正确的。

## 测试覆盖

- 新增 `PinyinUtilsTest`：12 个用例覆盖 null/空/空白/汉字/英文/数字混合/多音字
- 新增 `UserSearchServiceTest.searchByPinyin`：验证拼音搜索的 service 层管道
- 回归 `UserSearchServiceTest` + `UserSearchControllerTest`：总计 25 tests passed, BUILD SUCCESS

## 改动摘要

| 文件 | 改动 |
|---|---|
| `backend/pom.xml` | 添加 `com.belerweb:pinyin4j:2.5.1` 依赖 |
| `backend/.../common/util/PinyinUtils.java` | **新建**：汉字→全拼无空格转换工具类 |
| `backend/.../db/migration-mysql/V1096__add_users_full_name_pinyin.sql` | **新建**：users 表增加 `full_name_pinyin` 列（idempotent 模式） |
| `backend/.../entity/User.java` | 新增 `fullNamePinyin` 字段 + `@PrePersist`/`@PreUpdate` 自动维护 |
| `backend/.../repository/UserRepository.java` | `searchActiveUsers` 查询增加 `full_name_pinyin` 匹配 |
| `backend/.../common/util/PinyinUtilsTest.java` | **新建**：12 个拼音转换测试 |
| `backend/.../mention/service/UserSearchServiceTest.java` | 新增拼音搜索 service 层测试 |
| `src/views/Bidding/List.vue` | `mode="candidates"` → `mode="search"` |
| `src/views/Bidding/detail/DetailPage.vue` | `mode="candidates"` → `mode="search"` |
| `src/views/Bidding/list/components/AssignDialog.vue` | `mode="candidates"` → `mode="search"` |
| `.agent-locks/user-picker-pinyin-search.yml` | **新建**：文件锁 (entity + migration)

## P1 修复（思维链 Review 驱动）

| 问题 | 位置 | 修复内容 | 状态 |
|------|------|---------|------|
| mergedOptions 去重 | `UserPicker.vue:58-67` | 已有 Map 去重逻辑，无需修改 | ✅ 验证通过 |
| 多选编辑回显标签显示为数字ID | `ReminderSettingsDialog.vue:94-104` | 新增 `userPickerInitialOptions`，编辑时从 `reminderTargets` 构造 `{id, name}` 格式传入 `:initial-options`，确保 UserPicker 已选项显示用户姓名而非纯 ID 数字 | ✅ 已修复 |
| 用户对象→ID 转换代码分散5个组件 | `DraftingStage.vue:202` / `InitiationStage.vue:214-215` / `ProjectGroupSettingsPanel.vue:91` / `ReminderSettingsDialog.vue:100-107` | 新增 `src/utils/userPicker.js` 提供：`toUserIds()` / `toUserName()` / `toReminderTargets()` / `fromReminderTargets()` 四个通用函数，消除重复 | ✅ 已修复 |


# CO-338 任务看板详情抽屉实施记录

## 范围确认
- CO-338 核心：卡片点击弹出任务详情抽屉（复用 TaskForm.vue，mode="view"）
- 评论1：CSS 三列宽度修复（minmax + word-break）
- 排除：评论2（弹窗统一）、评论3（项目详情页权限过滤）、CO-339（按状态禁用按钮）

## 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 抽屉位置 | TaskBoardPage.vue 模板内 | 参考已有模式，drawer 状态是页面级，不抽 composable |
| 提交审核 API | 放在 TaskBoardPage.handleSubmitForReview | TaskForm.submitForReview 只做校验+返回数据，保持通用性 |
| canDeliver 读取 | taskFormRef.value?.canDeliver computed | TaskForm expose 了 canDeliver，父组件通过 ref 访问 |
| @click.stop | card-actions 和 dialog 外层加 stop | 防止按钮点击触发卡片根绑定的事件冒泡 |
| 字段映射 | title→name, dueDate→deadline, content→content, completionNotes→completionNote | TaskForm model 字段名不同，handleTaskClick 做映射 |

## API 交互流程

handleSubmitForReview:
1. 调用 form.submitForReview() 获取校验数据和交付物
2. 遍历 data.deliverableFiles，通过 FormData 逐个上传到 createTaskDeliverable
3. 调用 updateTask(taskId, {completionNotes}) 保存完成说明
4. 调用 updateTaskStatus(projectId, taskId, 'REVIEW') 提交审核
5. 刷新卡片列表 loadTasks()，关闭 drawer

## 测试覆盖

| 文件 | 测试数量 | 新增覆盖 |
|------|---------|---------|
| TaskBoardCard.spec.js | 7 | card-click emit, 按钮阻止冒泡 |
| TaskBoardPage.spec.js | 5 | 3列渲染, 卡片展示, drawer打开, 提审按钮显示, 提审 API 交互 |
| useTaskBoard.spec.js | 2 | 回归（未改动） |

## 验证结果

| 检查项 | 结果 |
|--------|------|
| npx vitest run src/views/TaskBoard | ✅ 14 tests passed (3 files) |
| npm run check:line-budgets | ✅ passed (guarded_changes=2) |
| npm run build | ✅ success (no new warnings) |

## 设计思维链 Review 修复（第二轮）

2026-06-25 根据设计评估报告实施 10 项修复：

### 修复清单

| # | 严重度 | 问题 | 修复内容 | 变更文件 |
|---|--------|------|---------|---------|
| 1 | P0 | 提审逻辑重复（confirmSubmit vs handleSubmitForReview） | 抽取 `useTaskSubmitReview` composable，双方统一调用 `submitForReview()` | 新建 composable + 修改两个 vue 文件 |
| 2 | P0 | status 固定 'TODO' 导致业务偏差 | 改为保留 API 原值 `item.status`，删除 `\|\| 'TODO'` | TaskBoardPage.vue |
| 3 | P1 | 字段映射硬编码 + 快照转存 | 展开 `...item` 只覆盖映射字段，保留 API 原始响应式数据 | TaskBoardPage.vue |
| 4 | P1 | nextTick 延迟打开 drawer 造成提审按钮闪烁 | 删除 nextTick，selectedTask 赋值后直接设 drawerVisible | TaskBoardPage.vue |
| 5 | P1 | matchesCurrentUser 缺少类型注释 | 添加 jsdoc `@param`/`@returns` | TaskBoardCard.vue |
| 6 | P1 | 模拟的 TaskFormStub 精度不足 | 改进断言：`toHaveBeenCalledWith` 参数级检查 | TaskBoardPage.spec.js |
| 7 | P2 | PRIORITY_TYPE_MAP 在 setup 内重复创建 | 提升到模块级常量 | TaskBoardCard.vue |
| 8 | P2 | v-loading 未 mock 导致测试 warn | 添加 `directives: { loading: vi.fn() }` | TaskBoardPage.spec.js |
| 9 | P2 | submit-review 测试只断言 `toHaveBeenCalled` | 改为参数级断言 + 注释未调用路径 | TaskBoardPage.spec.js |
| 10 | P2 | 注意到 `projectsApi` 在 TaskBoardCard 中不再直接引用 | 移除 `projectsApi` import，改为走 composable（确认 BID_REVIEW 操作仍保留 projectLifecycleApi） | TaskBoardCard.vue |

### 新增 composable

`src/views/TaskBoard/composables/useTaskSubmitReview.js` — 封装三条 API 调用：

1. `createTaskDeliverable`（遍历 `deliverableFiles` 逐个上传）
2. `updateTask`（保存 completionNote）
3. `updateTaskStatus(projectId, taskId, 'REVIEW')`

### 验证结果

| 检查项 | 结果 |
|--------|------|
| npx vitest run src/views/TaskBoard | ✅ 14 tests passed (3 files, 0 warn) |
| npm run check:line-budgets | ✅ passed (guarded_changes=3) |
| npm run build | ✅ success (no new errors) |

## 存量用户拼音回填

### 问题口径

- 部署验证发现：按中文姓名（`q=张`）和按工号（`q=03595`）搜索正常，但按拼音（`q=zhang`）搜索返回空。
- V1096 迁移只建 `full_name_pinyin` 列不填充数据；`@PrePersist/@PreUpdate` 仅在新建/更新用户时才赋值。
- 服务器上线前的所有**存量用户**的 `full_name_pinyin = NULL`，导致拼音搜索完全无效。

### 决策与权衡

1. **Java 启动期回填 vs SQL 函数回填**：选择 Java 层 `ApplicationRunner` + `PinyinUtils.toPinyin()`，因为拼音计算已在 Java 层有成熟逻辑（包含多音字、非汉字保留），SQL 中没有现成拼音函数。Java 层还能复用已有单元测试。
2. **全 profile 运行 vs 仅 dev/prod**：选择全 profile 运行（不限制 profile），因为 test/e2e 环境也可能需要拼音搜索覆盖。幂等性保证了后续部署不会重复回填。
3. **批量提交 vs 单条更新**：选择每 500 条一批 `saveAll()` 提交事务，避免超大事务锁表。存量用户几千到几万，批次合理。
4. **`saveAll` vs `@Query UPDATE` 批量更新**：选择 `saveAll`（实体更新），这比 native UPDATE 更安全——JPA 生命周期钩子都会触发。`@PreUpdate` 也在改 fullName 时重新计算拼音，双重安全。

### 改动摘要

| 文件 | 改动 |
|------|------|
| `backend/.../repository/UserRepository.java` | 新增 `findEnabledWithNullPinyin()` native 查询 |
| `backend/.../bootstrap/PinyinBackfillRunner.java` | **新建**：启动时回填存量用户拼音，500 条一批幂等执行 |
| `backend/.../bootstrap/README.md` | 新增 PinyinBackfillRunner 边界条目 |

### 验证

- `mvn compile`：通过，无编译错误
- `mvn test -Dtest=ArchitectureTest`：42 tests passed, 0 failures（架构规则全部满足）
- `mvn test -Dtest=PinyinUtilsTest,RoleProfileBootstrapArchitectureTest`：13 tests passed（拼音工具 + bootstrap 架构规则均通过）

# CO-338 评论2 & 评论3 实施记录

## 评论2：独立任务看板交付物上传弹窗统一

### 问题口径

- 独立任务看板（`TaskBoardCard.vue`）与项目详情页（`TaskBoard.vue`）的交付物上传弹窗不一致
- 评论要求统一为项目详情页样式：文案/字段/拖拽/按钮

### 决策与权衡

1. **不合并弹窗，而是拆分**：原 TaskBoardCard 只有一个"提交审核"弹窗（含交付物上传+完成说明+提审）。评论2要求的是"交付物上传弹窗"统一，但"提交审核"弹窗有独立功能（完成说明+REVIEW状态）。因此拆分成两个独立弹窗：
   - 新建"上传交付物"弹窗（与项目详情页统一：名称+类型+拖拽+保存）
   - 保留原有"提交任务"弹窗（完成说明+提审到REVIEW）
2. **"上传交付物"按钮已变为独立弹窗入口**，不再混在"提交审核"流程中
3. **按钮文案**：`交付物上传` → `上传交付物`，与项目详情页一致
4. **交付物类型映射**：前端用 document/qualification/technical/quotation/other，提交时映射为后端 DOCUMENT/QUALIFICATION/TECHNICAL/QUOTATION/OTHER

## 评论3：项目详情页任务看板权限过滤

### 问题口径

- 项目详情页任务看板（`TaskBoard.vue`）的操作入口缺少权限过滤
- 仅任务执行人本人可见/可点"上传交付物"
- 状态改变按角色禁用
- 交付物删除入口仅执行人可见

### 决策与权衡

1. **`isTaskAssignee` helper**：比对 `userStore.currentUser.id` 与 `task.assigneeId`（均转为 String 比较，兼容 number/string 类型差异）
2. **"上传交付物"**：`v-if="isTaskAssignee(task)"`，非执行人直接不渲染该 dropdown 项
3. **状态变更**：`:disabled="normalizeStatus(task.status) === s.code || !canChangeStatus(task)"`，使用 `canChangeStatus` 允许执行人和管理员/组长变更状态
4. **交付物删除**：`:closable="isTaskAssignee(task)"`，非执行人不可删除交付物（el-tag 不显示关闭图标）
5. **不拆分弹窗**：common TaskBoard.vue 本身的弹窗逻辑不改变，只做操作入口门控
6. **canChangeStatus 借用了已有 userStore 的 isBidManager getter**，不引入新的角色判断逻辑

### 改动摘要

| 文件 | 改动 |
|------|------|
| `src/components/common/TaskBoard.vue` | 新增 `isTaskAssignee` / `canChangeStatus` 函数；upload 项加 v-if；状态变更加 disabled 条件；del 关闭改 :closable |
| `src/components/common/TaskBoard.spec.js` | 新增 3 个权限测试（非执行人看不到上传、执行人能看到上传、非执行人状态变更全禁用）+ 调整 dropdown 项数量断言（5→5） |
| `src/views/TaskBoard/components/TaskBoardCard.vue` | 评论2 改造：拆分弹窗，新建独立上传交付物弹窗 |
| `src/views/TaskBoard/components/TaskBoardCard.spec.js` | 评论2：更新按钮文案断言，新增 4 个上传弹窗测试 |

### 验证结果

| 检查项 | 结果 |
|--------|------|
| npx vitest run src/views/TaskBoard src/components/common/TaskBoard.spec.js | ✅ 25 tests passed (2 files) |
| npm run check:line-budgets | ✅ passed (guarded_changes=2) |
| vite build | ✅ success (no new errors) |

---

## CO-338 设计审查 7 条修复实施 (2026-06-25)

### 修复清单

| # | 严重度 | 内容 | 状态 |
|---|--------|------|------|
| 2 | P1 | 提取 `isTaskAssignee`/`matchesCurrentUser`/`isBidReviewer` 到 `permission.js` | ✅ |
| 3 | P1 | 提取 `hexToSoftBackground` 到 `color.js` | ✅ |
| 4 | P1 | 统一 priority 大小写 (`HIGH` vs `high`) | ✅ |
| 5 | P2 | 移除 TaskBoardCard 提审弹窗中重复的 el-upload | ✅ |
| 6 | P2 | `canChangeStatus` 补充项目负责人 (primaryLeadUserId/secondaryLeadUserId) | ✅ |
| 1 | P1 | 提取 `useDeliverableUpload` composable | ✅ |

> 注意：#7 (null guard) 已折叠进 #2 — `matchesCurrentUser` 自带 null/empty 保护

### 关键决策与权衡

#### #2 — permission.js 提取

- `matchesCurrentUser(id)` 使用 `String` 比较规避类型不一致（后端 userId 可能为 number 或 string）
- `isTaskAssignee(task)` 读取 `task?.assigneeId` — TaskBoard.vue 用 `task.assigneeId`、TaskBoardCard.vue 用 `task.assigneeId`（已对齐）
- `isBidReviewer(item)` 读取 `item?.reviewerId` — 仅 TaskBoardCard.vue 的 BID_REVIEW 流程使用
- `userStore` 在 `permission.js` 内通过 `useUserStore()` 懒加载，而非 props 传入 — 因为 Vue composable 内调用 pinia store 是常规做法

#### #3 — color.js 提取

- 正则 `/^#?([\da-f]{3}|[\da-f]{6})$/i` 允许可选的 `#` 前缀和后端传来的无 `#` 色值
- 匹配失败时返回 `var(--bg-subtle)` 而不是抛异常 — 让 header 仍然有底色兜底
- 透明度固定 `0.12` — 这是 Element Plus el-tag 的 soft 背景惯例；如需不同透明度应在调用方处理

#### #4 — Priority 大小写统一

- `getPriorityType` 和 `getPriorityLabel` 都放在 `workbench-formatters.js` 中，通过 `toLowerCase()` 归一化
- TaskBoard.vue 通过别名 `getPriorityLabel as getPriorityText` 导入（模板中叫 `getPriorityText`），避免改模板
- 空值返回空字符串而非 fallback，不改变既有 UI 行为

#### #5 — TaskBoardCard 提审弹窗 el-upload 移除

- 提审弹窗（submit dialog）不再包含 el-upload，交付物上传已在独立弹窗中完成
- 独立上传弹窗保留 el-upload — 这是 #1 中 composable 管理的 UI 模板部分，composable 只管理行为逻辑
- `deliverableFileList`/`deliverableUploadRef` 等提审弹窗内的上传相关变量全部移除

#### #6 — canChangeStatus 补充项目负责人

- **选择从 projectStore.currentProject 读取 lead IDs** 而非 props 透传 — 因为 TaskBoard.vue 已经使用 projectStore，且 currentProject 在路由切换时已由 ProjectTaskBoardCard 所在上层加载。避免改 3 层中间组件(props 透传链: ProjectTaskBoardCard → ProjectDetailMainContent → MainColumn)
- `String(uid)` 比较避免类型不匹配
- line-budget 约束：TaskBoard.vue 已超 300 行历史限制，通过精简已有代码(压缩 computed/箭头函数单行、合并 return 语句)净减 4 行

#### #1 — useDeliverableUpload composable

- **为什么提取**：TaskBoard.vue 和 TaskBoardCard.vue 有完全相同的上传弹窗状态管理（openDialog 重置、handleFileChange、save 校验/提示/重置）+ 仅 API 调用方式不同（store vs api module + FormData）。提取后消除 ~65 行重复代码
- **API 差异适配**：通过 `onSave(task, payload)` 回调桥接。TaskBoard.vue 传递 `projectStore.addDeliverable(projectId, taskId, object)`，TaskBoardCard.vue 传递 `projectsApi.createTaskDeliverable(projectId, taskId, FormData)`。composable 不关心具体实现
- **ElMessage 处理**：composable 内统一处理成功/错误提示。调用方 onSave 中不再需要 ElMessage
- **为什么不提取模板**：el-dialog/el-upload 模板存在差异（TaskBoardCard 额外 `append-to-body`/`close-on-click-modal`），提取模板反而增加复杂度。composable 模式符合 Vue 3 Composition API 的最佳实践（分离逻辑与表现）
- **已删除的变量**：TaskBoard.vue 中的 `currentTask`/`fileList`/`deliverableForm`/`showUploadDialog`、TaskBoardCard.vue 中的 `uploadingTask`/`uploadingLoading`/`uploadFileList`/`deliverableForm( reactive)` — 全部替换为 composable 返回的 ref
- **保留的 `ref` import**：TaskBoard.vue 中 `ref` 仍在使用(其他地方)，`uploadSaving` 解构但未在模板中使用 — 这些是 eslint 的 warn，非错误

### 测试策略

| # | 测试文件 | 新增测试 | 策略 |
|---|----------|----------|------|
| 2 | `permission.test.js` (新建) | 24 | 3 describe (matchesCurrentUser / isTaskAssignee / isBidReviewer)，各 7-9 条，覆盖 null/empty/匹配/不匹配 |
| 3 | `color.spec.js` (新建) | 8 | 6 位色/3 位色/带#/无#/无效色/空值 |
| 4 | 在既有 spec 中 | 0 | 改数据测试用例 — 原有用例因大小写不匹配已覆盖 |
| 5 | `TaskBoardCard.spec.js` | 改动 | 更新按钮/弹窗存在的断言，删除原提审弹窗上传相关测试 |
| 6 | `TaskBoard.spec.js` | 3 | project lead (primary) / secondary lead / non-lead |
| 1 | `useDeliverableUpload.spec.js` (新建) | 8 | 初始状态 / openDialog 重置 / handleFileChange / save 空校验 / save 成功 / save 失败 / saving 状态切换 |

### 验证结果

| 检查项 | 结果 |
|--------|------|
| `npx vitest run src/composables/useDeliverableUpload.spec.js` | ✅ 8 passed |
| `npx vitest run src/utils/permission.test.js` | ✅ 24 passed |
| `npx vitest run src/utils/color.spec.js` | ✅ 8 passed |
| `npx vitest run src/components/common/TaskBoard.spec.js` | ✅ 18 passed |
| `npx vitest run src/views/TaskBoard/components/TaskBoardCard.spec.js` | ✅ 9 passed |
| `npx vitest run` (full suite) | ✅ 1526 passed, 2 skipped, 5 pre-existing failures (TaskForm/line-budgets/sidecar/env-detection — 与本次改动无关) |
| `npm run check:line-budgets` | ✅ passed |
| Commit hooks (TDD coverage / token-governance / wiki) | ✅ 全部通过 |

---

## 3dcc30097 回退恢复 (2026-06-26)

### 根因
`3dcc30097`「同步 GitHub main 重构」（2026-06-25 16:15）声称"同步"，实际 GitHub main 当时滞后于 Gitee，把当天早些时候的 9 个 Gitee 修复 commit 整体覆盖（净删 1236 行 / 74 文件改 / 18 文件删）。

### 系统排查结论

- ❌ **完全未恢复（本次修复）**：CO-338 前端（抽屉 + CSS + 冒泡隔离，9 项）+ 投标专员标讯可见性后端（1 项）
- 🔄 **已被后续 commit 自愈**：CO-329（1272fb095）、CO-333（ba415fe1c）、User 拼音字段链（2b14e0eb5 等）、CO-324 审计（45a5a0144）
- ⚠️ **设计性重构（非回退）**：tender permission 类删除改 @PreAuthorize、CO-266/310 客户信息段内联化

### PR-A: CO-338 前端恢复

**手法**：从 `90563f6a1` (CO-338 原修复) 反向取 diff，手工合并到当前重构后的代码结构（后续 commit 把卡片操作按钮拆到了 `TaskBoardTaskActions.vue` / `TaskBoardBidReviewActions.vue` 子组件，所以无法直接 cherry-pick，必须手工合并）。

| 文件 | 恢复内容 |
|------|----------|
| `TaskBoardPage.vue` | `el-drawer` 任务详情抽屉 + `handleTaskClick` 字段映射 + `handleSubmitForReview`（上传交付物→保存说明→更新状态）+ `canSubmitForReview` computed + CSS `grid-template-columns: minmax(0, 1fr)` 三列等宽 |
| `TaskBoardCard.vue` | 根元素 `@click="emit('task-click', item)"` + `defineEmits` 补 `task-click` + BID_REVIEW 表格恢复 `bid-review-documents` 外层 + `overflow-x: auto` + 操作子组件加 `@click.stop` 隔离冒泡 + `.task-card` 加 `cursor:pointer`+`min-width:0` + `task-name`/`task-project` 加 `word-break:break-all` |
| `TaskBoardPage.spec.js` | 从 90563f6a1 恢复（5 条：抽屉渲染/打开/提审按钮显隐/API 交互） |
| `TaskBoardCard.spec.js` | 新增 3 条测试（点击根元素 emit / 按钮冒泡隔离 / bid-review-documents wrapper 存在） |

**验证**：18 tests passed (3 files) / build OK / line-budgets OK

### PR-B: 投标专员标讯可见性后端恢复

**手法**：基于 `a048a0cfd` 的 diff 应用到当前 `TenderProjectAccessGuard`（Repository 还在，方法签名兼容）。

| 文件 | 恢复内容 |
|------|----------|
| `TenderProjectAccessGuard.java` | 注入 `TenderAssignmentRecordRepository`；`isSelfOwnedTender` 扩展为 `isSelfVisibleTender`（单条路径 `findFirstByTenderIdOrderByAssignedAtDesc`）；批量路径用 `findLatestByTenderIds` 预加载（无 N+1），`canAccessViaProjects` 加 `latestAssignment` 参数 |
| `TenderProjectAccessGuardVisibilityTest.java` | 从 a048a0cfd 恢复 4 场景（分配给自己可见 / 分配给他人不可见 / 自己创建可见 / 完全无关不可见） |
| `TenderCommandServiceTest.java` | 构造 accessGuard 时补传 `tenderAssignmentRecordRepository`（新增依赖） |

**适配当前代码的偏离**：
- `DataScopeAccessProfile` 已改为 `@Builder`（非 public 构造）→ 测试用 `builder().dataScope("self").build()`
- Mockito strict 模式下 `findByTenderIdIn(List)` 实参是 `Set` → 改用 `any()` 匹配

**业务影响**：投标专员（BID_TEAM）被分配标讯后，即使不是 owner，在 `dataScope=self` 下也能看到该标讯。3dcc30097 把这个判断压回了只认 owner，导致投标专员看不到分配给自己的标讯。

**验证**：`mvn -Dtest=TenderProjectAccessGuardVisibilityTest,TenderCommandServiceTest,ArchitectureTest,MaintainabilityArchitectureTest` → 45 tests, 0 failures, 2 skipped

### 关键决策与权衡

#### 为什么不直接 cherry-pick 90563f6a1？
后续 commit（`e06af5c06` "refactor(CO-339): 修复待审核任务显示提交按钮 + 架构优化"）把 TaskBoardCard 的内联操作按钮拆到了 `TaskBoardTaskActions.vue` / `TaskBoardBidReviewActions.vue` 子组件。90563f6a1 时的 `@click.stop` 加在 `<div class="card-actions">` 上，现在那些 div 已经是独立组件了——cherry-pick 会冲突且语义错误。所以手工把 `@click.stop` 加到子组件的引用上（`<TaskBoardTaskActions @click.stop>`），效果等价。

#### 为什么冒泡隔离是必须的？
如果不加 `@click.stop`，点"上传交付物""提交""驳回""通过审核"按钮会冒泡到卡片根，触发 `task-click` → 弹出详情抽屉——这是 90563f6a1 commit message 里写明的回归点。

#### PR-B 的 a048a0cfd 与产品需求确认
排查阶段已建议"先确认投标专员按 assignee 看标讯这条路径是否仍在产品需求里"。本次实施恢复 = 默认该需求仍然有效，因为：a048a0cfd 标题就是"投标专员标讯列表可见性 - 增加被分配标讯的判断"，是显式产品需求；没有任何后续 commit 反转这个决策（只是被回退掉，不是被否定）。

### 未纳入本次范围

- 不重构 `3dcc30097` 的"tender permission 类删除改 @PreAuthorize"（设计性变更，已与原方案功能等价）
- 不恢复 `TenderEvaluationCustomerInfoDeleteService`（CO-266/310 已被内联 `clear()+saveAndFlush()+addAll()` 替代）
- 不改 `ProjectStageService` 审计日志（CO-324 已通过事件解耦重构实现，功能达成）

### 验证结果

| 检查项 | 结果 |
|--------|------|
| `npx vitest run src/views/TaskBoard/` | ✅ 18 passed (3 files) |
| `npx vite build` | ✅ 8.37s |
| `npm run check:line-budgets` | ✅ passed |
| `mvn -Dtest=TenderProjectAccessGuardVisibilityTest,...` | ✅ 45 passed, 2 skipped |
| Commit hooks | ✅ 全部通过 |

---

## 2026-06-26 第 3 次生产部署（CO-346，验证 validate 预检重复部署鲁棒性）

本次为独立部署任务，非 CO-338 范围。完整报告见 `docs/release/deploy-report-2026-06-26-3rd.md`，CHANGELOG 已追加。

### 关键决策
- **SSH 密钥**：`/tmp/xiyu-prod-deploy-crm-test` 缺失，经用户确认复用 `~/.ssh/xiyu_cursor_deploy`（连通性 + sudo 免密验证通过）。
- **mysqldump 兼容**：服务器是 MariaDB 5.5.68 客户端连 MySQL 8.0.43 服务端，不认 `--set-gtid-purged=OFF`，去掉后备份成功。
- **前端 baseURL**：`VITE_API_BASE_URL=http://172.16.38.78:8080` 传入但被 `src/api/config.js:47` 生产构建强制 `baseURL=""`（同源，bug 复发 3 次后的代码层强制）。

### 核心验证结论
validate 预检在重复部署（`e51a673bc` 相对服务器 `b77fc9170` 代码零 diff、schema 无变化）下**仍无条件执行并通过**（165 migrations），证明其为不可短路的门禁，不依赖"是否有变化"启发式判断。CO-346 systemName 字段经 webhook_delivery_tasks 表对比（id=129 无 vs id=130/131 有）精确验证生效。

---

## CO-355 任务执行人搜索结果被固定人员淹没（根因修复）

### 问题口径
- 任务执行人选择框（`TaskForm.vue` 的 UserPicker）按姓名/拼音/工号搜索时，结果"混在固定人员里"，搜 `zrr` 命中郑蓉蓉但被预加载的 37 个候选人淹没。
- 用户反复反馈："其他搜索框都没问题，就这个有问题；后台数据库能搜到不代表前台能展现。"
- 此前多个 PR（!1130 enabled 过滤、#1139 ORDER BY 相关度、#1148 拼音首字母缩写）都是正确的后端增强，但**都没解决用户实际看到的问题**——因为根因不在后端，而在前端。

### 真正根因（剥洋葱结论）
1. `TaskForm.vue:50` 是唯一在 `mode="search"` 下传 `:initial-options="assigneeOptions"` 的任务执行人入口。
2. `useTaskAssigneePicker` 在 `onMounted` 调 `loadAssignees()` → `/api/users/assignable-candidates?context=task` 返回 37 个启用用户 → 注入 `assigneeOptions`。
3. `UserPicker.vue` 的 `mergedOptions` 把 `initialOptions` 与搜索结果按 id 合并进一个 Map → 37 个固定人员**始终在场**。
4. el-select-v2 在 `remote` 模式下（`useSelect.mjs` `isRemoteMethodValid = filterable && remote && isFunction(remoteMethod)`），`isValidOption` 对**所有**选项返回 `true` → 预加载的 37 人不会被前端过滤掉，永远展示。
5. 结果：搜索命中郑蓉蓉后，下拉里 37 个固定人员 + 郑蓉蓉 一起出现，用户感觉"搜出来的人混在固定人员里"。

**为什么只有这个入口有问题**：其他搜索框（TaskKanban、Bidding/List、AssignDialog 等）都用 `mode="search"` 但**不传 `initialOptions`**，`mergedOptions` 只含搜索结果，所以表现正常。零号病人就是 `:initial-options="assigneeOptions"`。

### 修复
`UserPicker.vue` 的 `selectOptions` computed：搜索态（`mode==='search'` 且 `options.value.length>0`）时只用搜索结果，无搜索结果时回落到 `mergedOptions`（含 `initialOptions`），保证未搜索/关闭态下已选值标签仍能渲染。

```js
const searching = props.mode === 'search' && options.value.length > 0
const source = searching ? options.value : mergedOptions.value
```

### 关键决策与权衡
- **不动 `mergedOptions`，只动 `selectOptions`**：`mergedOptions` 仍被 `handleChange` 用于回查已选用户对象，保留它避免改 `handleChange`。展示和回查分离更清晰。
- **不额外保留"已选值"在搜索态列表里**：考虑过搜索时把已选 id 从 `initialOptions` 里补回列表以防标签消失，但分析了 el-select-v2 行为——搜索时输入框显示的是查询词（非已选标签），关闭态/清空查询时 `options.value=[]` 自动回落到 `mergedOptions`（含已选）。且"已选值不在候选人里"的标签消失问题在**修复前就已存在**（选一个不在 37 人里的搜索结果后清空查询，同样会消失），本次不引入新回归，不过度设计。
- **受益面**：所有 4 个传 `initialOptions` 的入口（TaskForm / ReminderSettingsDialog / DraftingStage / InitiationStage）都获得正确行为——搜索时只看搜索结果，不搜索时看预加载列表。无 `initialOptions` 的入口行为不变（`mergedOptions` 退化为只有 `options.value`，`searching` 分支和回落分支结果一致）。
- **防复发测试**：新增 2 条 `UserPicker.spec.js` 用例——"搜索态不混入 initialOptions"和"无搜索时回落到 initialOptions"。已用 `git checkout -- UserPicker.vue` 还原组件验证：新测试在未修复组件上失败（固定人员甲/乙混入），修复后通过。

### 验证
- `npx vitest run src/components/common/UserPicker.spec.js`：12 passed（含 2 条新增防复发用例）。
- `npx vitest run src/composables/useUserPicker.spec.js src/composables/projectDetail/taskAssigneePayload.spec.js`：21 passed。
- RED 证明：还原 `UserPicker.vue` 后新测试失败，输出显示 `固定人员乙（00002）` 与 `郑蓉蓉（06234）` 同时出现——正是用户描述的"混在固定人员里"。

---

## 2026-06-27 CO-361 项目详情页任务看板缺失任务（两入口展示逻辑一致）

### 问题
Linear CO-361（多次修复未根治：PR#1153/#1156/#1197）。任务看板有两个入口，IN_PROGRESS（已废弃）状态处理不一致，导致项目详情页看板任务消失：
- **独立看板** `GET /api/task-board/items`（TaskBoardService → `TaskBoardItemMapper.mapTaskStatus`，第 92 行 `case IN_PROGRESS -> STATUS_TODO`）—— 已归一化。
- **项目详情看板** `GET /api/tasks/project/{projectId}`（TaskService.getTasksByProjectId → `TaskDtoMapper.toDTO`，第 69 行 `.status(task.getStatus())`）—— 原样返回 IN_PROGRESS。
- 前端 `TaskBoard.vue` 第 210 行 `availableStatuses = statuses.filter(s => s.code !== 'IN_PROGRESS')` 把 IN_PROGRESS 列过滤掉 → 项目详情看板中状态仍为 IN_PROGRESS 的任务直接消失。

PR#1153 只在写入侧废弃 IN_PROGRESS，读出/展示侧（项目详情看板）从未对齐。PR#1197 的 `TaskVisibilityPolicy`（执行人可见性）逻辑正确，不是本 bug 根因。

### 决策与权衡
- **方向选择**：在展示层 `TaskDtoMapper.toDTO` 做兜底归一（IN_PROGRESS→TODO），与 `TaskBoardItemMapper` 语义对齐。备选是改 `TaskBoard.vue` 不过滤 IN_PROGRESS，但那样会让前端出现一个已废弃的列，且独立看板早已走归一逻辑——保持两入口同构更安全。
- **最小改动**：单行三元 `task.getStatus() == Task.Status.IN_PROGRESS ? Task.Status.TODO : task.getStatus()`，不抽公共方法。`TaskBoardItemMapper` 返回 String、本处返回 `Task.Status` 枚举（`TaskDTO.status` 字段类型），类型不同不宜强行共用常量，避免为复用而引入跨层耦合。
- **未扩展到 CANCELLED**：`TaskBoardItemMapper` 把 CANCELLED→COMPLETED，但 CO-361 范围仅 IN_PROGRESS 消失问题；CANCELLED 不在本次 issue 描述内，不顺手扩大改造面。
- **未触碰数据迁移**：`V1101__normalize_in_progress_tasks.sql`（`UPDATE tasks SET status='TODO' WHERE status='IN_PROGRESS'`）已存在，但只清理历史存量，运行后仍可能有新的 IN_PROGRESS（写入侧废弃是软约束、或迁移未跑的环境）。展示层兜底是防御性最后一道，确保任何路径下都不消失。
- **null 安全**：`Task.Status` 是枚举，`==` 对 null 返回 false 走 else 分支返回 null，与原 `.status(task.getStatus())` 行为一致，不引入 NPE。

### 未纳入本次范围
- 不改 `TaskBoard.vue` / `useProjectDetailInit.js` / `project-utils.js`（前端展示链路保持原样）。
- 不改 `TaskVisibilityPolicy` / `TaskService.getTasksByProjectId`（PR#1197 可见性逻辑正确）。
- 不改 `TaskBoardItemMapper`（参考实现，已正确）。
- 不改 `ProjectTaskWorkflowService`（项目工作流视图，`mapStatus` 走 doing，不同链路，不在 CO-361 范围）。
- 不做 CANCELLED 归一、不做写入侧强制约束加强。

### 修改文件范围
- `backend/src/main/java/com/xiyu/bid/task/service/TaskDtoMapper.java`（第 69 行 + 2 行注释，共 +3/-1）
- `backend/src/test/java/com/xiyu/bid/task/service/TaskDtoMapperTest.java`（+56 行：2 个新测试 + 注释）

### 验证（TDD Red→Green + 回归）
- **Red**：先加 `toDTO_normalizesInProgressToTodo`，跑 `mvn test -Dtest=TaskDtoMapperTest` → Tests run: 11, Failures: 1，失败信息 `expected: TODO but was: IN_PROGRESS`，证明 bug 存在于 `TaskDtoMapper.toDTO`。
- **Green**：改第 69 行归一后 → Tests run: 11, Failures: 0, BUILD SUCCESS。`toDTO_keepsCanonicalStatusesUntouched`（TODO/REVIEW/COMPLETED 原样保留）同时通过。
- **回归**：
  - `TaskVisibilityPolicyTest`：16 passed。
  - `TaskServiceProjectAccessTest`：5 passed。
  - `ArchitectureTest`：25 passed（架构守卫未违反）。
  - `ProjectWorkflowIntegrationTest`：3 passed（`@SpringBootTest` + H2 内存库，含 `/api/projects/{id}/tasks` 端到端）。
- 日志中 `notification_delivery_task` 唯一约束报错与 `/tasks/decompose` 500 是该集成测试既有非确定性噪声（H2 并发下通知去重约束偶发冲突），与本次改动无关，最终测试全绿。

---

## 2026-06-27 CO-361 Review 发现 A+B 修复（CANCELLED 同形缺口 + 状态归一收口）

### 问题（思维链 Review 识别）
前序提交 `f7fee2e76` 修了 IN_PROGRESS 缺口后，启动 Review 发现两个相邻设计问题：

**发现A（中风险）CANCELLED 同形缺口**：
- `TaskTransitionPolicy.java:23-24` 允许 TODO→CANCELLED 合法转换（有真实写入路径）
- `task_status_dict`（V101）种子只有 TODO/IN_PROGRESS/REVIEW/COMPLETED 四条，无 CANCELLED 列
- 前端 `TaskBoard.vue:210` `availableStatuses` 按字典生成列，无 CANCELLED 列 → CANCELLED 任务在项目详情看板消失（与 IN_PROGRESS 同形 bug）
- `TaskService.getTasksByProjectId`（line 173/175）两个分支都不过滤 CANCELLED
- 关键事实核实：独立看板 `TaskBoardService` 在 `fromTask` 之前用 `isVisibleTask` 前置过滤 CANCELLED（line 72/112/120），所以 `mapTaskStatus` 里 `case CANCELLED->COMPLETED` 是死代码——独立看板对 CANCELLED 真实语义 = **不展示**

**发现B（低风险）状态归一逻辑重复**：
- IN_PROGRESS→TODO 归一在 `TaskDtoMapper.toDTO`（三元）和 `TaskBoardItemMapper.mapTaskStatus`（switch）两处独立实现
- 是 CO-361 三次复发（PR#1153/#1156/#1197）的结构性根因——状态模型变更需同步多处，易遗漏

### 决策与权衡
- **发现A方向**：选择在 `TaskService.getTasksByProjectId` 过滤 CANCELLED，对齐独立看板 `isVisibleTask` 语义。**不**在 Mapper 归一 CANCELLED→COMPLETED——那样与独立看板"不展示"语义冲突，反而制造新不一致。最终直接复用 `TaskBoardItemMapper.isVisibleTask` 静态方法（`TaskService` 同包可访问），既对齐语义又消除重复。最初抽了私有 `visibleNonCancelled`，但把 `TaskService` 推到 310 行触发 `ResponsibilityArchitectureTest` 行预算门禁（max 300），遂重构为三元选 list + 统一 `.filter(TaskBoardItemMapper::isVisibleTask)`，行数回到 300。
- **发现B方向**：在 `Task.Status` 枚举加 `normalizedForDisplay()` 纯方法（仅 IN_PROGRESS→TODO），两处 Mapper 复用，单一真相源。CANCELLED 不纳入归一（它走过滤路径）。
- **TaskBoardItemMapper.mapTaskStatus 的 CANCELLED→COMPLETED 分支保留**：虽是 isVisibleTask 前置过滤后的死代码，但保留避免语义争议，行为不变（12 个测试印证）。
- **前端 filter 未清理**：`TaskBoard.vue:210` 的 `filter(s => s.code !== 'IN_PROGRESS')` 与 `project-utils.js` 的 `TASK_STATUS_FROM_API[IN_PROGRESS]` 在后端归一+过滤后成为冗余防御，但删它触达 e2e 联动门禁且非本次核心，保留并在本 notes 记录为后续清理项。

### 修改文件范围（4 源 + 1 测试 + notes）
- `backend/.../entity/Task.java`：枚举加 `normalizedForDisplay()` 方法（+11 行）
- `backend/.../task/service/TaskDtoMapper.java`：第 71 行改用枚举方法（保持 null 安全）
- `backend/.../task/service/TaskBoardItemMapper.java`：`mapTaskStatus` 复用枚举方法，保留 CANCELLED 分支
- `backend/.../task/service/TaskService.java`：`getTasksByProjectId` 重构为三元选 list + 统一 filter CANCELLED（复用 `TaskBoardItemMapper.isVisibleTask`），行数持平
- `backend/.../task/service/TaskServiceProjectAccessTest.java`：+2 个 CANCELLED 过滤测试（覆盖全量可见/执行人可见两分支）+ task helper 重载 status

### 未纳入本次范围
- 前端 `TaskBoard.vue` filter / `project-utils.js` 双向映射表的冗余清理（待后续，需 e2e 联动评估）。
- `TaskBoardItemMapper.mapTaskStatus` CANCELLED 死代码分支的清理（保留避免争议）。
- `FlywayRollbackScriptCoverageTest` 既有债务（main 上即红，与本次无关）。

### 验证（74 tests 全绿）
- `TaskBoardItemMapperTest`：12 passed（含 5 个 mapTaskStatus，验证枚举复用未破坏归一行为）
- `TaskDtoMapperTest`：11 passed（含前序 2 个 CO-361 归一测试）
- `TaskVisibilityPolicyTest`：16 passed
- `TaskServiceProjectAccessTest`：7 passed（含 2 个新增 CANCELLED 过滤测试）
- `ArchitectureTest`：25 passed（FP-Java 架构守卫未违反）
- `ProjectWorkflowIntegrationTest`：3 passed（`@SpringBootTest` 端到端，含 `/api/tasks/project/{id}`）
- 日志噪声同前序（notification_delivery_task H2 去重约束 + decompose 500），非本次引入。


# CO-361 任务状态三态模型收口实施记录

## 问题口径

- 用户要求彻底下线 `IN_PROGRESS` 与 `CANCELLED`，回归三态模型 `TODO → REVIEW → COMPLETED`（审核驳回回 TODO）。
- 用户明确指示「先不用加删除按钮」，因此 `deleteTask` API 保持原样，不在本次范围。
- 本次为全栈收口：DB ENUM、后端枚举/策略/Service/Mapper/Policy、前端常量/组件/composables、以及对应测试。

## 决策与权衡

### task_status_dict 采取「禁用」而非「删除」
- V101 种子数据在 `task_status_dict` 插入了 `IN_PROGRESS` 字典行，前端状态选择器通过 `TaskStatusDictController.listEnabled` 读取启用行。
- 若直接 `DELETE`，丢失历史数据完整性且无法审计「曾经存在过哪些状态」。
- 选择 `UPDATE task_status_dict SET enabled = FALSE WHERE code = 'IN_PROGRESS'`：前端选择器立即不再展示，历史行保留，管理员可在后台重新启用（向后兼容逃生通道）；U1105 回滚对应 `SET enabled = TRUE`。

### TaskStatusCategory 枚举未动
- `TaskStatusCategory`（`OPEN / IN_PROGRESS / REVIEW / CLOSED`）是**字典元数据分类**，不是业务状态枚举，服务于 `task_status_dict` 字典行的分类标签。
- 保留 `TaskStatusCategory.IN_PROGRESS` 不删——它不承载业务流转语义，删除会破坏字典分类体系。

### V1105 单脚本承担三步操作（顺序严格）
1. `UPDATE tasks SET status='TODO' WHERE status='CANCELLED'` — 存量 CANCELLED 归一（IN_PROGRESS 已由 V1101 提前归一，不重复）。
2. `ALTER TABLE tasks MODIFY COLUMN status ENUM('TODO','REVIEW','COMPLETED') NOT NULL DEFAULT 'TODO'` — ENUM 收口。
3. `UPDATE task_status_dict SET enabled = FALSE WHERE code = 'IN_PROGRESS'` — 字典禁用。

**权衡**：理想应拆成多个脚本（数据归一 / DDL / 字典）。但三步强耦合——只做 DDL 不先归一数据，存量 CANCELLED 行会因 ENUM 收口报错；只禁用字典不收 ENUM，后端枚举与 DB 类型不一致。合并为一个脚本保证原子性，U1105 严格逆序还原。

### normalizedForDisplay() 未引入
- 早期方案（PR #1218，位于另一分支 `agent/zcode/co361-project-leader-task-board-missing`）曾在 `Task.Status` 上加 `normalizedForDisplay()` 做显示态归一。
- 本分支基线无此方法，`TaskDtoMapper` 直接透传 `.status(task.getStatus())`。DB 层已用 V1105 把存量数据归一到三态，运行时不可能再出现 IN_PROGRESS/CANCELLED，无需引入兼容层。

### TaskTransitionPolicy 转换表简化
| from \ to | TODO | REVIEW | COMPLETED |
|-----------|------|--------|-----------|
| TODO      | -    | ✓      | ✗（拒绝，必须先 REVIEW） |
| REVIEW    | ✓（驳回）| -  | ✓（通过） |
| COMPLETED | ✗    | ✗      | -         |

新增显式拒绝 `TODO → COMPLETED` 跨态跃迁，`TaskTransitionPolicyTest` 覆盖；`REVIEW → TODO` 保留为「审核驳回」路径。

### TaskBoardItemMapper.isVisibleTask 简化为恒真
- 原逻辑过滤 `null` 任务和 `CANCELLED` 任务。CANCELLED 已不存在，简化为 `return task != null`。
- 对应测试 `TaskBoardServiceTest.getBoardItemsFiltersCompletedReviews` 改为验证空任务列表场景，加 `findByAssigneeId` 返回空列表 mock 防止 NPE。

### 前端 availableStatuses 退化为透传
- `TaskBoard.vue` / `TaskForm.vue` 原先按状态过滤可选目标状态。三态后所有非终态状态可选目标集相同，`availableStatuses = computed(() => statuses.value)` 直接透传，保留 computed 包装维持响应式契约。

### 前端 doing 归一映射移除
- 后端 `ProjectTaskWorkflowService.mapStatus` 原先输出 `doing`（对应 IN_PROGRESS）。收口后只剩 `todo / review / done`，前端 `TASK_STATUS_TO_API / TASK_STATUS_FROM_API` 同步删除 `doing / IN_PROGRESS` 映射，测试数据 `doing` 替换为 `review`。

## 未纳入本次范围

- 不加任务删除按钮（用户明确指示）；`deleteTask` API 保持原样。
- 不动 `TaskStatusCategory` 枚举（字典元数据，非业务状态）。
- 不做 `task_status_dict` 行物理删除（采取软禁用，保留审计与逃生通道）。
- 不重构 `TaskService` 结构（已在 300 行预算内，ResponsibilityArchitectureTest 通过）。

## 验证证据

| 验证项 | 结果 |
|---|---|
| 后端编译 | ✅ 通过 |
| ArchitectureTest | ✅ 25 项 |
| FlywayRollbackScriptCoverageTest | ✅ V1105/U1105 覆盖 |
| ResponsibilityArchitectureTest | ✅ 4 项（TaskService 在 300 行预算内） |
| Task 相关单元测试（10 类） | ✅ 107 项通过 |
| ProjectDraftingServiceTest | ✅ 32 项通过 |
| pre-push-gate.sh | ✅ 9 通过 / 0 失败 / 8 跳过（agent/* 分支自动跳过前端测试） |

## 文件变更清单

### DB 迁移（新增）
- `V1105__drop_in_progress_cancelled_status.sql` — 存量归一 + ENUM 收口 + 字典禁用
- `U1105__drop_in_progress_cancelled_status.sql` — 逆序回滚

### 后端主代码（12 文件）
- `entity/Task.java` — Status 枚举删两值
- `task/core/TaskTransitionPolicy.java` — 转换表三态
- `task/service/TaskBoardItemMapper.java` — mapTaskStatus 三 case + isVisibleTask 简化
- `task/service/TaskService.java` — 删 normalizedForDisplay 调用
- `task/service/TaskWorkloadAssembler.java` — todoCount 过滤收口
- `task/core/DeliverableAssociationPolicy.java` — 仅判 COMPLETED
- `project/core/AllTasksCompletedPolicy.java` — TaskState 三值
- `analytics/service/DashboardAnalyticsTeamComputationService.java` — 删 CANCELLED 逾期判断
- `projectworkflow/dto/ProjectTaskStatusUpdateRequest.java` — Status 三值
- `projectworkflow/service/ProjectTaskWorkflowService.java` — mapStatus 三 case
- `projectworkflow/core/ProjectTaskAuthorizationPolicy.java` — 路由简化
- `config/E2eDemoDataInitializer.java` — 种子 3 条

### 前端（8 文件）
- `constants/taskStatus.js` — 3 值常量
- `views/Project/project-utils.js` — 删 doing/cancelled 映射
- `components/common/TaskBoard.vue` — 透传
- `components/project/TaskForm.vue` — 透传
- `composables/projectDetail/useProjectDetailInit.js` — 注释更新
- `composables/projectDetail/useProjectDetailFormatting.js` — review: warning
- `composables/projectDetail/useProjectDetailCore.js` — review: warning
- `composables/projectDetail/useProjectDetailTaskActions.js` — 删 doing label

### 后端测试（12 文件）
- `TaskTransitionPolicyTest.java` — 重写 15 测试
- `TaskTransitionPolicyFuzzTest.java` — isAllowedBySpec 三态
- `TaskBoardItemMapperTest.java` — 删 inProgress/cancelled
- `TaskBoardServiceTest.java` — IN_PROGRESS→TODO，重写 cancelled 过滤测试
- `DeliverableAssociationPolicyTest.java` — 重命名 + 删 cancelled
- `AllTasksCompletedPolicyTest.java` — 删 cancelled
- `BidReadinessPolicyTest.java` — CANCELLED→COMPLETED
- `ProjectTaskAuthorizationPolicyTest.java` — 删 2 个 IN_PROGRESS 测试
- `ProjectDraftingServiceTest.java` — 多处替换
- `TaskHistoryRecorderTest.java` — IN_PROGRESS→REVIEW
- `AbstractAuditOperationalAnalyticsIntegrationTest.java` — IN_PROGRESS→TODO
- `E2eDemoDataInitializerTest.java` — times(3) + containsExactly

### 前端测试（10 文件）
- `useProjectDetailTaskActions.spec.js` — 删 CANCELLED 测试块
- `useProjectDetailInit.spec.js` — mock 三态
- `TaskBoard.spec.js` — 删 IN_PROGRESS mock
- `TaskForm.spec.js` — mock + 状态
- `useProjectDetailDocumentActions.spec.js` / `useProjectDetailActions.spec.js` — IN_PROGRESS→TODO
- `ProjectDetailTaskStatusEvents.spec.js` — doing→review
- `TaskBoardCard.spec.js` — 删 IN_PROGRESS
- `useProjectDetailCore.spec.js` — **新建**，10 测试锁定三态映射（防 doing 回退）
- `useProjectDetailFormatting.spec.js` — **新建**，10 测试锁定三态映射

> 补测试原因：pre-commit TDD 门禁要求 `src/composables/*.js` 改动必须有对应 spec。这两个 composable 在 HEAD 上本就无专属测试，本次借机补齐三态映射防回归。

### 其他
- 删除 `.agent-locks/co-363-personnel-schema-drift-fix.yml`（CO-363 分支已删但锁文件残留，stale lock 清理）
- 新增 `.agent-locks/co361-three-state-model.yml`（本任务三目录锁）

## 设计评审修复（思维链 Review 驱动）

首次提交后启动系统性设计评审，识别 1 个 P0、2 个 P1、3 个 P2 问题，逐项修复：

### P0 — V1105/U1105 列类型前提错误（回滚失真）
- **问题**：首次 V1105 声称 baseline 是 ENUM 五值，但真相是 V102 已把 `tasks.status` 从 ENUM 改成 `VARCHAR(32) NOT NULL`。V1105 的 `ALTER ... enum('TODO','REVIEW','COMPLETED')` 把 VARCHAR(32) 收紧成 ENUM，与项目演进趋势（V102/V1102 都是 ENUM→VARCHAR）相反；U1105 回滚成 ENUM 五值而非真实 baseline VARCHAR(32)，造成回滚失真。
- **修复**：
  - V1105 去掉 `ALTER TABLE ... MODIFY COLUMN status enum(...)`，列类型保持 VARCHAR(32)，仅做数据归一 + 字典禁用。合法值由 `Task.Status` 枚举 + `@Enumerated(EnumType.STRING)` 在应用层约束。
  - U1105 去掉恢复 ENUM 的 ALTER，仅反向重新启用字典行。回滚完全可逆（CANCELLED 数据归一的不可逆性已在注释说明）。
- **理由**：跟随项目既有趋势，避免回滚失真，保留后续新增状态的灵活性。

### P1-1 — TaskStatusCategory 文档与字典面板废弃提示
- **问题**：保留 `TaskStatusCategory.IN_PROGRESS` 决策正确，但留三处不一致：
  - `TaskStatusCategory.java:19` 注释仍提"已取消"（三态后已下线）。
  - `TaskStatusDictPanel.vue` `CATEGORY_OPTIONS` 仍显式提供 `进行中（IN_PROGRESS）` 选项，与 V1105 禁用 IN_PROGRESS 字典行形成"分类可配但具体行被禁用"认知割裂。
- **修复**：注释更新为"包含已完成等终态"；字典面板选项标注 `（已废弃）`。

### P1-2 — availableStatuses 透传冗余
- **问题**：三态后 `availableStatuses = computed(() => statuses.value)` 是纯透传别名，`TaskBoard.vue`/`TaskForm.vue` 多一层间接，意图模糊。
- **修复**：删除 `availableStatuses` computed，模板与 `columns` 直接用 `statuses`。TaskBoard.vue 净减 2 行（343→341，缓解超限）；TaskForm.vue 净减 3 行（298→295）。

### P2 — 残留清理
- `TaskBoard.spec.js:306-307` 拖拽测试用 `'IN_PROGRESS'` 作目标列名 → 改为 `'REVIEW'`（三态后无 IN_PROGRESS 列，避免误导未来读者）。
- `useProjectDetailInit.js:14` 注释矛盾：原文说"保留 normalize 层以兼容历史小写返回值"，但映射表已删 `doing`。修正为说明 doing 走 fallback 原样返回的真实行为。

### 评审未改动的部分（设计正确，记录在案）
- `task_status_dict` 禁用而非删除（保留审计 + 逃生通道）——决策正确。
- `TaskTransitionPolicy` 新增 `TODO→COMPLETED` 显式拒绝——防止跨态跃迁。
- `TaskBoardItemMapper.isVisibleTask` 简化为 `task != null`——CANCELLED 不存在后过滤条件无意义。
- 新增 2 个 composable spec 锁定三态映射——有正向防回归价值。

---

## CO-361 任务可见性 bug 修复 — OSS 用户 roleCode 解析（2026-06-27）

### 背景
Linear CO-361 评论反馈（dom.chuye, 2026-06-27）：在标书制作阶段添加任务并分配任务执行人以后，当任务执行人本身是项目参与人员（且是项目投标负责人），登录系统进入项目详情页任务看板看不到分配给自己/项目的任务；但跨部门执行人在独立任务看板页能看到待办。

### 根因（通过服务器日志 + DB 查询 + 代码链路确认）
OSS 同步用户（陈梦瑶 id=7246、王占俊 id=7220）的 `users.role_id = NULL`。`User.getRoleCode()` 实体方法在 `roleProfile == null` 时 fallback 返回 `"manager"`，而 OSS 缓存里他们的真实角色是 `"bid-Team"`。

`TaskService.getTasksByProjectId` 和 `TaskBoardService.getBoardItems` **直调 `user.getRoleCode()`**（拿到 `"manager"`）而非 **OSS-cache-aware 的 `DataScopeConfigService.getRoleCode(user)`**（拿到 `"bid-Team"`），导致：
- 王占俊（project 101 primary_lead, OSS bid-Team）→ `canViewAllProjectTasks("manager", 7220, 7220, 8556)` 返回 false → 走 `findByProjectIdAndAssigneeId(101, 7220)` → 他不是 101 任务的 assignee → 空看板。
- 独立看板 `shouldQueryByProjectScope("manager")=false` → 走 `findByAssigneeId`，恰好绕开 roleCode 判断，所以独立看板能看到待办——这解释了用户反馈的"独立看板能看到、项目详情页看不到"。

### 修复决策
在 `TaskService` 和 `TaskBoardService` 注入 `DataScopeConfigService`，把 `currentUser.getRoleCode()` 改为 `dataScopeConfigService.getRoleCode(currentUser)`（OSS-cache-aware）。

### 关键权衡与决策
1. **只改 2 处直调点，不收口到 ProjectAccessScopeService/DataScopeConfigService 内部**
   - `ProjectAccessScopeService.getAllowedProjectIds` line 57/120 的 admin 短路基于 `user.getRoleCode()`，但 admin 另有 `hasAdminAccess(authority)` 双保险，非 bug，不动。
   - `DataScopeConfigService.resolveRoleProfile` line 231 若改用 `getRoleCode(user)`，cache miss 返回 null → `definitionForCode(null)` 返回 admin def → **越权风险**，不动。
   - 最小侵入原则：只改实际出 bug 的两个调用点。

2. **fail-closed 安全边界**
   - OSS cache miss → `getRoleCode(user)` 返回 null → `TaskVisibilityPolicy` 返回 false → 走 `findByProjectIdAndAssigneeId` 看到自己作为 assignee 的任务，不越权放大。需重新登录刷新缓存。
   - admin（本地系统账户）→ `isLocalSystemAccount` fallback → "admin" → `GLOBAL_ACCESS_ROLES` 命中 → 看所有任务，不回归。
   - 非 OSS legacy MANAGER → cache miss 返回 null → false，与之前 "manager"→false 一致，不回归。

3. **独立看板行为变化（符合 CO-361 设计意图，不算回归）**
   - 修复前：bid-Team 用户 `shouldQueryByProjectScope("manager")=false` → 走 `findByAssigneeId`（只看自己的）。
   - 修复后：bid-Team 用户 `shouldQueryByProjectScope("bid-Team")=true` → 走 `collectTasksByProjectScope`（看负责项目的所有任务）。这是权限矩阵的预期行为。

### 涉及文件
**修改**（2 文件）：
- `backend/src/main/java/com/xiyu/bid/task/service/TaskService.java` — 注入 DataScopeConfigService，`getTasksByProjectId` 改用 `getRoleCode(currentUser)`
- `backend/src/main/java/com/xiyu/bid/task/service/TaskBoardService.java` — 注入 DataScopeConfigService，`getBoardItems` 改用 `getRoleCode(currentUser)`

**测试**（4 文件）：
- `TaskServiceProjectAccessTest.java` — 构造更新 + 新增 `getTasksByProjectIdUsesOssRoleCodeForBidTeamLead` + `getTasksByProjectIdFailClosedWhenOssCacheMiss`
- `TaskBoardServiceTest.java` — 构造更新 + 新增 `getBoardItemsQueriesByProjectScopeForOssBidTeam`
- `TaskContentPersistenceTest.java`、`TaskExtendedFieldsPersistenceTest.java` — 构造签名同步更新

### 验证证据
- `mvn test -Dtest='com.xiyu.bid.task.**'` → 232 测试全绿（含新增 3 个）
- `mvn compile` → 编译通过

### 不在本次修复范围（记录在案，需后续评估）
- 陈梦瑶（sys_project_member of 101，非 lead）case：`canViewAllProjectTasks` 对她返回 false 是符合策略的（非 lead 只看自己的任务）。如果她被分配了 101 的任务但仍看不到，需进一步确认 `findByProjectIdAndAssigneeId` 的查询结果——可能需 DB 核实她是否真的是 101 任务的 assignee。
- `User.getRoleCode()` 实体方法对 role_id=NULL 返回 "manager" 是历史遗留，全量收口需评估其他调用点。

