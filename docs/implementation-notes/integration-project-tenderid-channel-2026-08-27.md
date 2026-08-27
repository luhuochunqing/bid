# 实现笔记：接口六契约修正——定位参数 sourceId → tenderId（2026-08-27）

> 任务：`agent/qoder/integration-project-tender-channel`，对 !2409 的投产前契约修正。
> 本文档按用户要求维护，记录方案之外必须临场拍板的决策、被动调整与权衡取舍。

## 方案外决策（计划未覆盖、实现时补齐的）

### D1. `GlobalExceptionHandler.handleTypeMismatch` 重写（新增改动面）

- **问题**：方案预期"CRM 误传 CC... 编号时类型转换直接 400"。实测发现：Spring WebMVC 6.1.1 的父类 `ResponseEntityExceptionHandler` 已注册 `TypeMismatchException` 处理器，而本类尾部还有 `@ExceptionHandler(Exception.class)` 兜底 → 若不做任何处理，状态码取决于父类接管结果且响应体不是 ApiResponse 格式；若新声明同类型 handler 会触发启动期 Ambiguous 冲突（仓库 CO-529 前科）。
- **决策**：按 CO-529 口径**重写 protected `handleTypeMismatch`**，统一转 400 + ApiResponse 硬编码消息（M-03 不透传参数细节）。已用 MockMvc 用例验证非数字路径参数返回 `{"success":false,"code":400}` 且未发生启动冲突。
- **波及面声明**：该重写是全局生效的（所有路径/查询参数类型不匹配都走此格式），不只是接口六。评估为正向收益（原先这类错误会落入非标准响应体），但评审时请注意这是超出接口六范围的唯一行为变化。

### D2. 跳过响应的 `updatedAt` 也置 null

- 方案只说"applied=false 且 projectId/projectName 为 null"。实现选择 updatedAt 同为 null——没发生的更新不应携带时间戳误导调用方。wiki §1.1 与本次说明均按三字段全 null 口径。

### D3. `resolveProject` 返回 `Optional<Project>`

- 跳过语义产生后，"标讯存在但无关联项目"不再是异常而是正常返回通道，故改为 Optional 承载（empty = 未立项），由主流程统一构造 skip 响应，避免深层抛接。

### D4. 来源键前缀校验只在 tenderId 查找分支生效

- projectId 分支已有更强的等值断言（项目.tenderId == 路径 tenderId），不需要再做 externalId 前缀检查（那需要额外一次 tenderRepository 加载，初版就是这么做的，本次顺手去掉了这次多余查询）。

### D5. 版本号标注策略

- 代码注解/Swagger tag 维持"v3.12 §3.6"不臆造 v3.13（接口未投产，契约修正收敛进同一版本）；wiki 版本演进表沿用仓库已有的"— | 日期"行格式登记代码层变更。若飞书文档侧决定升版本号，需同步改 Tag 注解（一行）。

## 取舍记录

| 取舍点 | 选择 | 放弃的替代 |
|---|---|---|
| 单通道替换 vs 双通道并存 | 单通道（用户决策：接口零调用方） | 双通道保留 sourceId 兼容一段期——多一套键校验、多一类歧义面，不值得 |
| tenderId(Long) 定位 vs crm_opportunity_id fallback 排查链 | 主键直查 | fallback 可行（UNIQUE 背书）但仍以"来源编号当主键用"为前提，语义不如主键干净 |
| 一致性断言口径 | project.tenderId == path.tenderId 等值断言 | 原 externalId 字符串比对——弱且有格式歧义 |
| 手工标讯（externalId=null）处理 | 前缀校验跳过放行 | 一律拒绝会误伤 M 链路数据（本次故障的业务半边） |

## 偏差与备注

- 计划要求的测试用例全部落地并跑绿（服务层 25 + MockMvc 7 = 32 个），另有 4 个原"来源键交叉校验"用例按方案改写为一致性断言用例，`_` 占位符跳过用例随占位符语义一并删除。
- `TenderIntegrationResolver` 从接口六服务中移除依赖（单通道后无引用）；接口三自身的同名方法 `TenderIntegrationCommandService.updateByExternalId` 是另一个类，不受影响。
- 测试基建注意点：gap 附件用例中 mock 参数 `anyString()` 改为 `any(String.class)` 以适配重载解析（行为等价）。

## 验证证据

- `mvn test -Dtest='ProjectIntegrationCommandServiceTest,ProjectIntegrationControllerTest'` → Tests run: 32, Failures: 0, Errors: 0
- 全量 `mvn test` 结果见 PR 描述（提交前必须绿）
- MockMvc 断言：更新成功用例验证 tender 实体 title/externalId 全程不变（tenderEntityNotModified）

## 飞书文档同步：被权限阻塞，附可直接粘贴的替换文案（D6）

**状态**：定位到正典《西域对接接口文档》wiki 节点 `TNJywraUziKuRukBQxhcyZO9nSb`（版本 v3.16，底层 docx `CUjIdrWrBodPBmxxppzcWQfVnRb`）。当前用户对其**可读不可编辑**；`docs +update` 双身份（user/bot）均返回 4030004；`drive +apply-permission` 因本机 lark-cli 缺 `docs:permission.member:apply` scope 无法发起申请。两步都需要人工参与：① 用户执行 `lark-cli auth login --scope "docs:permission.member:apply" --no-wait --json` 走扫码授权后由我重新提交权限申请；或 ② 文档所有者在飞书里直接给卢文融开编辑权。

**重要发现（坐实根因）**：该文档 §3.6 路径参数表现有文案为——sourceId："来源系统内业务编号（即标讯接口使用的 sourceId，如 CRM 商机编号）"，示例 `"OPP-2026-00918"`。**文档自己就在引导调用方拿商机编号当定位键**，与 spec 037 的 bidId 口径冲突。CRM 完全是照文档行事的。

**拿到编辑权后需要落地的改动清单**（均已按原文档红色 `rgb(216,57,49)` 样式拟好）：

1. 导语段（block `doxcneiyNov4iNfIMsG9IggGNoh`）整体替换：
   > 按 (sourceSystem, tenderId) 定位项目：tenderId 为平台标讯主键（CRM 从"推送标讯"等接口响应中获取并保存），平台按主键直接定位标讯及其一一关联的项目，与来源侧业务编号/商机编号彻底解耦；非数字值（如误传商机编号 CC...）在路径绑定阶段即被拒绝并返回 400。……跳过语义：标讯存在但尚未立项时返回 HTTP 200 且 data 仅含 applied=false，立项完成后重试同一请求即可。
2. 路径行：`/api/integration/projects/{sourceSystem}/{sourceId}` → `/api/integration/projects/{sourceSystem}/{tenderId}`
3. 路径参数表 sourceId 行 → tenderId / Long / "平台标讯主键（取自推送标讯接口响应）" / 示例 `928`
4. 请求示例 URL `CRM/OPP-2026-00918` → `CRM/928`
5. 成功响应 data 增加 `"applied": true`
6. 尾部说明块错误码改为"404 标讯不存在（路径 tenderId 未命中）"并补跳过语义句
7. 文档版本记录追加 v3.17 行
8. body 表 projectId 说明补"传入时需与路径 tenderId 一致，不一致返回 400"

## 收尾重构（ai-slop-cleaner）：抽取 ApiKeyUserResolver 消除 3 处重复

**背景**：本次接口六改造新增了第三个 controller 内嵌的 `resolveApiKeyUserId()` 私有方法，
与 `TenderSyncController` / `TenderIntegrationController` 各自的同名私有方法**逐字重复**
（前两个 hash 相同，第三个仅声明方式用全限定名）。3 份 copy-paste 是典型的 AI-slop 产物。

**决策**：抽取为共享组件 `ApiKeyUserResolver`（`@Component` + 构造注入 ApiKeyService/AuthService），
三个 controller 的 import/字段/调用点全部收敛到 `apiKeyUserResolver.resolveUserId()`，私有方法删除。
行为逐行保持：`api-key:` principal 解析 → ApiKeyService 反查属主 → AuthService 解析 userId，
任一步失败一律返回 null 不阻断主流程；日志 warn 文案原样保留。

**连带改动**：`ProjectIntegrationControllerTest` 用 Mockito `@InjectMocks` 构造 controller，
改造前 controller 只有一个构造参数，现在变成 2 个，Mockito 找不到 `ApiKeyUserResolver` 的 mock
会把该参数注入 null → 5 个用例全部 500（NPE）。修复：把测试里已无用的 `@Mock ApiKeyService`/`@Mock AuthService`
换成 `@Mock ApiKeyUserResolver`，`verifyNoMoreInteractions(apiKeyService, authService)` 改为
`verify(apiKeyUserResolver).resolveUserId()`（语义等价：断言 controller 不直接触碰 API Key 服务）。

**验证**：对齐 CI 口径子集 56 绿（ArchUnit 11 + CommandService 25 + ControllerTest 7 + Resolver 13）；
质量门禁 checkstyle/pmd/spotbugs 全过；净减 42 行（+73/−115）。commit `492f80734`。
