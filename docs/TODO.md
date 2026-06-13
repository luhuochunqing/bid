# TODO

- [ ] 对接外部标讯聚合 API（替换 `src/views/Bidding/List.vue` 中 `fetchExternalTendersFromApi` 的占位实现，打通“一键获取标讯”真实链路）
- [ ] 补跑 Microsoft Edge 浏览器兼容测试：当前 Chrome、Firefox 29 个页面巡检已通过；Edge 因测试机未安装 `/Applications/Microsoft Edge.app` 且自动安装需要 sudo 密码暂未完成。安装 Edge 后按真实 API 模式补跑同一套页面兼容巡检，并更新验收结论。

## 流程表单中心交付记录

- [x] 已完成：表单历史版本查看与回滚能力（管理员查看历史版本、单击回滚并发布新版本）

## Bid Agent 优化

- [ ] 将 `RequirementProfile` 进一步做成原子化条目拆分，避免长句聚合，便于逐条响应与章节映射
- [ ] 在项目详情抽屉增加“招标拆解预览”，支持人工快速核查结构化结果后再生成初稿
- [ ] 为扫描版 PDF 增加 OCR 流程，并区分文本 PDF / 扫描 PDF 的处理策略与错误提示
- [ ] 优化知识库信号召回，按行业、项目上下文、标签做更精准的资质/模板/案例筛选
- [ ] 继续增强 OpenAI structured output 清洗，对列表字段做更细的语义去噪和分段规整
- [ ] 支持基于解析快照的多版本管理与人工激活，允许选择“哪个招标版本”参与后续生成
- [ ] 补全文档编辑器里的来源追踪展示，让章节可直接查看引用的招标条款与知识库来源
- [ ] 增加生成阶段的异步任务化与进度持久化，支持长文档生成的可靠重试 and 状态恢复

## 系统集成中心

> 2026-04-25 已交付：系统设置「系统集成」Tab + 企业微信配置入口（CorpID/AgentID/Secret + SSO/消息推送启用开关 + 连接测试）。详见 [[integration-wecom]]。以下为后续尚未完成工作。

### 企业微信 — 运行时实现（本次仅做配置）

- [ ] **真实连通性 Probe**：替换 `WeComMockConnectivityProbe` 为调用 `https://qyapi.weixin.qq.com/cgi-bin/gettoken` 的真实实现；缓存 access_token（约 7200s 有效），失败时给出具体错误码与建议处置
- [ ] **SSO OAuth2 回调落地**：
  - 新增 `/api/auth/wecom/callback` 处理 `state + code`
  - 实现 state token 校验（防 CSRF）
  - 通过 OAuth2 接口 `snsapi_base` / `snsapi_privateinfo` 拉用户 openid/userid
  - 与本地 user 表按 userid 或手机号对齐建立联系；首次登录弹绑定窗
  - 登录成功签发 JWT，复用现有 `JwtUtil` + 登录流程
  - 前端在登录页增加"企业微信扫码登录"按钮，未启用时隐藏
- [ ] **应用消息实际推送**：
  - 新增 `WeComMessagePublisher` 服务（`application/` 层）
  - 封装 `https://qyapi.weixin.qq.com/cgi-bin/message/send` 调用
  - 接入三个事件触发点：标讯入库（`TenderCreatedEvent`）、审批结果（`ApprovalDecidedEvent`）、代办提醒（定时任务）
  - 支持按用户开关订阅（在「用户偏好」页增加订阅矩阵）
- [ ] **异常重试与限流**：接 Spring Retry 或自建 backoff，记录最近 N 次失败到独立 `wecom_push_log` 表供排查
- [ ] **配置变更审计**：PUT /api/admin/integrations/wecom 成功后写入 `audit_log`，保留修改前后 diff（Secret 字段脱敏）

### 通讯录同步（二期）

- [ ] 新增 `WeComContactSyncAppService`，周期性或手动触发拉取部门树与成员
- [ ] 落盘到 `department`、`user` 表（保留映射字段 `external_department_id` / `external_user_id`）
- [ ] 前端在「系统集成 → 企业微信」卡增加"立即同步"按钮与最近同步状态
- [ ] 同步冲突策略：企微为准 vs 本地保留，提供开关
- [ ] 离职/调岗增量事件订阅（企微通讯录回调）

### 占位系统落地（等接口规范）

- [ ] **CRM 系统**：客户提供 CRM 接口规范后，按本次企微同样模式实现（domain/application/controller/infrastructure 分层），占位卡替换为真实配置卡
  - **待确认项**（2026-05-27）：
    1. 7 个 CRM 接口是否全部需要做成系统配置项？**客户答复：不需要**
    2. 配置项范围是否包括 Token 缓存 TTL、重试策略等运行时参数？**客户答复：包括**
    3. 配置持久化方式：存数据库（Settings 表）还是纯环境变量？**客户未确定，待跟进**
- [ ] **OA / 审批流**：等待客户选定 OA 厂商后对接（用印、合同评审、付款申请三类流程模板映射 + 回调状态同步）
- [ ] **流程表单历史版本管理**：在流程表单配置页增加历史版本查看、版本差异对比和回滚发布能力。当前表单实例已保存 schema 快照，不会被后续修改影响；该项用于管理员误发布后的运营恢复。
- [ ] **流程表单附件字段落地**：将附件字段从配置与展示占位升级为真实上传、回显、表单实例快照保存和 OA 字段映射提交能力，支持客户在审批表单中提交材料附件。
- [ ] **流程表单配置审计日志**：记录管理员创建、修改、发布、绑定 OA、试提交等操作，并保存关键配置变更前后差异，便于误配置追踪和上线排查。
- [ ] **OA 字段映射发布前校验**：从泛微测试环境拉取流程字段元数据，校验本地字段映射是否存在、类型是否匹配、必填 OA 字段是否遗漏，把映射错误前移到发布前暴露。
- [ ] **流程表单模板权限与适用范围**：支持按角色、部门、业务类型或项目范围控制表单模板的可见、可提交、可维护权限，避免不同业务线误用全局模板。
- [ ] **流程表单实例运维重试台**：提供表单提交实例列表，展示 OA 状态、业务应用状态和失败原因，并允许管理员对业务应用失败的实例执行补偿重试。
- [ ] **流程表单端到端回归用例**：覆盖管理员建模板、配字段、绑定 OA、预览、试提交、发布、业务用户提交、OA 回调、业务生效，守住跨前端、后端、数据库、OA Gateway 和资质借阅业务的完整链路。
- [ ] **组织架构系统**：进行中，已补齐 YAPI 契约映射和部署验收 Runbook；实现完成状态必须等待真实 `ClientSDK`、YAPI 鉴权/字段/禁用语义冻结、组织架构测试通过和客户 UAT 留证后再更新，且不包含 CRM 或 OA 流程创建。

### 开放 API 接口补齐（跟 [[api-openapi]] 配套）

- [ ] 增加 `/api/v1/...` 路径前缀与版本化策略
- [ ] 机器身份认证（API Key / Client Credentials）：新增 `api_client` 表 + `ApiKeyAuthenticationFilter`
- [ ] Webhook 出站事件回调框架：`webhook_subscription` 表 + 事件总线 + 重试机制
- [ ] 在每个 Controller 上补齐 `@Operation` / `@Tag` / `@Schema` 注解，让 Swagger UI 文档更友好

## 架构与性能优化

### 后端数据权限范围查询下推
- [ ] **Goal**: 将统计与导出的可见项目范围计算从 `projectRepository.findAll()` 后内存过滤，下推为数据库层的当前用户可见项目 ID 查询。
- [ ] **Impact**: 降低非管理员统计和导出的查询成本，避免大租户/大项目量下出现慢接口。
- [ ] **Next Step**: 把 `filterAccessibleProjects(projectRepository.findAll())` 替换为 `currentUserAccessibleProjectIds()`。

## QA 遗留事项 (QA Deferred)

- [ ] **非管理员演示账号不可登录**: 登录页展示 `lizong` 等演示账号但密码 401。需明确移除提示或在本地 profile 中种子化。 (Found: 2026-04-25)
- [ ] **导出响应 recordCount 始终为 0**: 已在分支修复，需确认 `ExportController` 已使用 `ExcelExportService` 返回的结构化元数据。 (Found: 2026-04-25)

## 工程质量

- [ ] `ManualTenderDialog.spec.js` 中临时 `.skip` 的 `emits file changes to the parent workflow` 用例需要原维护者重写
- [ ] 评估把 `integration/domain/ValidationResult.java` 与 `bidmatch/domain/ValidationResult.java` 抽取到 `common/domain/` 共享
- [ ] 补一个 `@SpringBootTest` 级别的 integration 测试，验证 `WeComIntegrationController` 真实路径 + Flyway 迁移端到端
