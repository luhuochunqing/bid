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
