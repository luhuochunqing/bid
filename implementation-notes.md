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
