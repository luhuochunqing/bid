# 变更日志 (CHANGELOG)

记录项目的重要变更和版本历史。

## [未发布]

### Infrastructure
- **Markitdown Sidecar 首次部署到客户测试服务器**（2026-06-21）：`winbid-01.test`（`172.16.38.78`）首次以 Docker 容器方式部署 `xiyu-sidecar`，端口 `8000`，`--restart=always`。
  - **背景**：服务器此前从未运行过 sidecar，所有 doc-insight 调用都走 fallback 纯文本提取，丢失标题层级、表格结构、OCR 能力，导致 LLM 智能解析质量差。
  - **部署方式**：Docker 容器（服务器有 Docker 26.1.4，无 Python 3.9+，无法直接 venv 部署）。
  - **新增 `Dockerfile.cn`**：国内镜像源加速版（阿里云 debian + 清华 PyPI），解决服务器无法直连 Docker Hub 默认源的问题。构建耗时从"卡死 7 分钟"降到 2 分钟。
  - **修复 markitdown 安装**：`requirements.txt` 注释说"NOT on PyPI"是历史误判，`Dockerfile.cn` 显式 `pip install markitdown[all]`。
  - **验证**：`GET /health` 返回 `{"status":"up"}`，`POST /convert` 成功转换测试文档返回完整 markdown + sections + contentHash，容器内存占用 122MB。
  - **详见**：`docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md §14`

### Added
- **西域组织架构 SDK 接入 Phase 1**（分支 `agent/cursor/organization-sdk-integration`）：实现 SDK 直连接入框架、Bearer token 动态换取、HTTP fallback 路径清理。
  - 新增 `OrganizationTokenService`（Bearer token 换取 + 内存缓存 + 10% 比例自动续期 + 3次失败 cooldown），模式与 `CrmAuthService` 完全一致。
  - 新增 `OrganizationEventSdkConsumerAdapter`（`@ConditionalOnProperty` 控制）、`OrganizationEventSdkResponseMapper` 及测试。
  - 删除 `com.xiyu.bid.organization` 全包（`EventSyncService`、`ClientSdkAdapter`、`FullInitService` 等 HTTP fallback 桩代码），删除 `OrganizationEventWebhookController`、`OrganizationWebhookSignatureVerifier`、`DisabledOrganizationDirectoryGateway`。
  - `SecurityConfig` 移除 `/api/integrations/organization/events` 白名单路由。
  - `application.yml` / `application-dev.yml` 新增 `authClientId` / `authClientSecret` 配置。
  - `pom.xml` 新增 `sdk` Maven profile（按需引入 `com.ehsy.eventlibrary:ClientSDK`）。
  - 98 个 organization 集成测试全绿，架构门禁全绿。
  - **待西域提供/验证**：Maven 私服已给出 `maven.ehsy.com/nexus`，待拉包验证；YAPI base URL、applyToken 凭证仍待提供。
- **知识库 Phase 2 — AI 案例应答网格** (#335)：新增 `CaseGrid.vue` 组件，从事件驱动的 `KnowledgeCase` 实体生成 AI 切片案例卡，支持按关键词/标签筛选与一键复用。
  - 后端新增 `KnowledgeCaseController`、`KnowledgeCaseService`、`KnowledgeCaseRepository`，以及 `ProjectClosedEventListener` 在项目归档时自动生成案例切片。
  - 新增 Flyway 迁移脚本 V999（`knowledge_case` 表）。
  - 前端 `/knowledge/case` 路由迁移为 `CaseWrapper.vue` Tab 包装器，AI 案例网格作为默认 Tab，传统案例库作为第二 Tab，实现向下兼容。
- **工程化 E2E 门禁** (#337)：新增三层自动拦截机制防止 CI 反复失败。
  - `scripts/check-route-e2e-compat.mjs`：静态检查路由组件替换后 E2E 播种的 API 是否仍可见，在 pre-commit 和 CI 双重拦截。
  - `scripts/check-hot-path-locks.mjs`：pre-commit 检查 staged 文件是否命中 hot-paths 且有对应 agent lock，防止忘记注册锁导致 CI 被拒。
  - CI `e2e-scope` job：改 `src/views/**` 或 `src/router/**` 时，若 `e2e/` 无对应变更则 CI 失败，强制"改 UI 必须改 E2E"。
  - E2E 全量覆盖扩展至 `case-advanced-flow`。
  - `start-api-e2e-stack.sh` 端口冲突时给出 `agent:stop` 明确引导，消除本地调试障碍。
- **组织架构集成文档切片**: 新增 YAPI 契约映射与部署验收 Runbook，冻结 `BaseOssDept` / `BaseOssUser` 事件、部门/员工详情和时间窗同步的 Blocking Inputs，并明确 SDK JAR 缺失时不伪造 SDK、先走统一应用服务链路。
- **DocInsight 文档智能引擎**: 实现了全系统通用的底层文档解析基础设施 (`com.xiyu.bid.docinsight`)。
  - 支持 .doc/.docx/.pdf 到高保真 Markdown 的高保真转换（物理层）。
  - 实现了基于标题层级的“结构化切片”逻辑，保留完整的章节上下文路径（结构层）。
  - 建立了通用 AI 提取流水线，强制要求字段携带原文摘录证据与章节定位（认知层）。
  - 新增 `/api/doc-insight/parse` 通用 API，支持多业务 Profile 驱动的结构化提取。
- **证据驱动立项工作台**: 新增通用的 `DocVerificationWorkbench.vue` 前端组件。
  - 支持 Schema 驱动的表单动态渲染，实现“点击数据项，自动跳转原文高亮证据”的交互闭环。
  - 深度集成至项目创建流程（Project Create）与投标助手抽屉（Bid Agent Drawer）。

### Changed
- **解析逻辑去业务化**: 将原有的标书解析逻辑解耦，迁移为 DocInsight 引擎的首个 TENDER Profile。
- **性能与鲁棒性优化**: 前后端接口超时时间分别延长至 120s/90s，支持 80+ 页超长文档的稳健解析。
- **立项流程重构**: 优化立项业务逻辑，实现基于已有标讯附件的自动驱动解析，消除了用户的重复上传操作。

### Fixed
- **E2E 播种与 UI 展现脱节**（#335）：`/knowledge/case` 路由替换后传统案例在 E2E 环境不可见，导致 CI 多次失败。通过 `CaseWrapper.vue` Tab 包装器解决兼容性问题，并更新两个 E2E spec 脚本（增加切换到"传统案例库"Tab 的步骤）。
- 修复了前端模板标签未闭合导致的编译错误及后端非 ASCII 字符导致的 Maven 编译失败。
- 修正了 `BidTenderDocumentImportAppService` 对物理文件存储路径的识别逻辑。

### Key PRs
- [#337 工程化 E2E 门禁 + Wiki 总结](https://github.com/ericforai/bidding/pull/337)
- [#335 知识库 Phase 2 实现](https://github.com/ericforai/bidding/pull/335)
- [#55 MySQL 8 main sync](https://github.com/ericforai/bidding/pull/55)
- [#52 Non-integration gap closure](https://github.com/ericforai/bidding/pull/52)
- [#51 Full-green stabilization](https://github.com/ericforai/bidding/pull/51)
- [#50 Score draft policy rescue](https://github.com/ericforai/bidding/pull/50)
- [#49 Compliance check split](https://github.com/ericforai/bidding/pull/49)
- [#48 Expense ledger multidimensional stats](https://github.com/ericforai/bidding/pull/48)
- [#47 Template library 3D classification](https://github.com/ericforai/bidding/pull/47)
- [#45 Deposit return auto follow-up](https://github.com/ericforai/bidding/pull/45)

## [1.0.3] - 2026-04-24

### Added
- 新增可配置的投标匹配评分模型，支持按维度、权重、证据键和规则类型维护评分规则。
- 新增标讯匹配评分后端模块、数据库迁移、评分历史与最新结果查询接口。
- 新增项目详情和 AI 分析页的匹配评分面板，并在系统设置中提供评分模型配置入口。
- 新增前后端单元测试，覆盖评分纯核心、API 映射、设置表单校验、详情页和 AI 分析页集成。

### Changed
- 项目详情和 AI 分析链路接入真实 `/api/bid-match/models` 与 `/api/tenders/{id}/match-score` 接口。
- 拆分匹配证据采集快照，保持应用服务只负责编排和持久化，评分计算留在纯核心。
- 同步 API、Bidding 和 bidmatch 模块 README，明确真实 API 交付边界。
- 修正纯核心架构门禁，排除 Java 枚举 `values()` 合成字节码对 `System.arraycopy` 的误报。

### Fixed
- 修复评分 API 前端路径与后端控制器不一致的问题。
- 修复匹配评分历史查询重复组装证据导致的额外开销。

## [1.0.2] - 2026-04-23

### Added
- 系统设置新增 AI 模型供应商运行时配置能力，支持按供应商维护模型、基地址、密钥与可用状态。
- 新增 AI 模型连通性测试接口与前端配置面板，支持在保存前进行连接验证与错误提示。
- 新增 `AiProviderCatalog` 与 `RoutingAiProvider` 相关后端单元测试，补齐多供应商路由与配置边界场景覆盖。
- 可以在项目详情直接发起 AI 标书初稿生成，查看运行产物、审查摘要，并把草稿写入文档编辑器。
- 标书生成链路现在使用 OpenAI Responses structured outputs 作为生产路径，生成内容会保留来源线索、置信度和人工确认项。
- 文档编辑器支持批量写入 AI 草稿章节树，保留来源 metadata，并自动跳过已锁定章节。
- 新增后端架构/领域/应用/控制器/文档写入测试、前端 bid-agent 单测和 E2E 回归覆盖。

### Changed
- `SettingsController` 与 `SettingsService` 扩展 AI 配置读写契约，返回脱敏后的密钥状态并保持治理字段一致性。
- 工作台指标卡与系统设置页面样式/组件结构同步更新，适配新增 AI 设置交互与信息展示。
- 启动脚本与 Dashboard 相关说明文档同步更新，保持开发启动与页面边界文档一致。
- AI 深度能力服务完成拆分，评分、风险、gap、任务等规则进入可单测的纯核心，应用服务只负责编排。
- OpenAI Provider 改为配置注入，减少环境变量和手工 JSON 截取造成的运行风险。
- 项目详情、Dashboard、Bidding 相关前端测试拆分和稳定化，保持 Split-First 单文件 300 行约束。

### Fixed
- 修复 AI 模型配置明文密钥回传风险，接口响应统一返回掩码与是否已配置标识。
- 修复供应商路由在异常配置下的回退与错误分支处理，避免请求落到错误 provider。
- 修复 bid-agent 项目级访问校验缺口，STAFF/MANAGER 跨项目访问会返回 403。
- 修复 Dashboard demo 项目链接误跳 `/project/P001`，现在使用 demo query 路由。
- 修复项目详情 projectId prop 类型 warning、`/project/create` QA 路由、Bidding/Dashboard 既有单测和相关 E2E 稳定性问题。
- 修复 AI 分析失败提示缺少“加载失败”前缀的问题。
- 修复 API 交付模式下客户商机中心仍可直连的问题，隐藏入口和路由都会回到标讯中心。
- 修复 Playwright API E2E 会复用其它 worktree 旧服务的问题，现在只复用自身管理的测试栈。
- 修复合并主线后的 Flyway 迁移版本冲突，将文档章节 metadata 扩容脚本顺延到 v84。
- 修复 OpenAI 招标要求结构化输出落库前缺少分类 allowlist 和置信度范围校验的问题。
- 修复 tender upload 队列迁移在 H2/MySQL schema validation 下的兼容问题，上传状态和任务状态保持 `VARCHAR` 持久化口径。

## [1.0.1] - 2026-04-22

### Changed
- 拆分 Dashboard Workbench 巨型页面为页面编排、纯核心规则、应用服务 composable、展示组件和独立样式模块。
- 将投标日历、指标卡、项目列表、待办、审批、流程、团队表现、快速发起等区块迁移为职责单一的组件。
- 补齐 Dashboard 空状态、错误状态和重试入口，避免 API 空数据或失败时出现沉默空白。
- 为可点击卡片和日历节点补充键盘访问与可见 focus 状态。

### Added
- 新增 Workbench characterization、纯核心、composable 和展示组件测试，覆盖角色视图、API 映射、路由、支持申请、待办完成和日历 store 接线。
- 新增 Dashboard README，记录 Workbench 拆分边界、空态/a11y 约束和 300 行拆分规则。

### Fixed
- 移除 Workbench 样式入口中的 CSS `@import` 串联，改由 JS 入口交给 Vite 打包。
- 移除日历样式覆盖中的 `!important`，降低样式特异性债务。

### 2026-04-20

#### 新增 (Features)
- **标准模板库三维分类闭环**: 以真实 `/api/knowledge/templates` API 为入口，补齐按产品类型、行业、文档类型分类的最小客户验收闭环
  - 后端新增 `templatecatalog` 增量模块，落实模板三维分类、受控字典校验、版本规则与组合筛选
  - 数据模型补齐 `productType`、`industry`、`documentType` 字段，并保持历史 `category` 兼容
  - 前端模板库页面完成拆分，新增三维筛选、受控下拉表单与真实 API 联动
  - 新增真实 API 场景下的后端集成测试与 E2E 覆盖

#### 重构 (Refactor)
- **FP-Java / Split-First 收口**: 收敛模板域、资质库与相关页面职责边界
  - 模板域核心规则下沉到纯核心，应用服务只负责用例编排
  - 拆分超大前端页面与 API 模块，减少职责混杂与单文件膨胀
  - 强化架构门禁，补齐 Architecture / FP-Java 相关测试约束
- **全绿稳定化收口**: 收敛告警调度、项目详情启动链路与 Java 质量门禁
  - 新增 `alertdispatch` 协调层，解除 `alerts -> businessqualification/resources -> alerts` 架构循环
  - 收敛项目详情页与费用页的页面壳/组合式函数拆分，降低页面对渲染时序的偶然依赖
  - 恢复 `quality-strict`、Flyway 容器校验与 CI 门禁的一致口径

#### 修复 (Fixes)
- **Ship 收口兼容修复**: 修复工作台与标讯页在现有单元测试约定下的兼容问题
  - 恢复 `alerts` / `fees` API 兼容方法，消除历史调用断裂
  - 为工作台补齐数据归一化工具，稳定真实 API 数据映射
  - 调整 `Bidding/List` 视图与测试桩，消除 slot props 缺失导致的测试失败
- **版本治理补齐**: 新增仓库根目录 `VERSION`，并将前后端版本号收敛到同一来源
  - 新增版本同步与一致性检查脚本
  - 构建与发布预检接入版本一致性门禁
- **稳定化修复**: 补齐认证恢复、项目详情、文档归档摘要与资源费用闭环
  - 修复 Flyway 重复迁移版本冲突，并顺延保证金跟踪、历史项目快照与案例资产扩展脚本版本号
  - 修复 `/login` stale user hint 会话恢复与 `/api/auth/logout` 的 `Authorization`/refresh 策略
  - 补齐费用支付登记、支付流水查询、保证金退还自动跟踪与手工提醒链路
  - 修复告警规则、费用页和项目详情相关真实 API 回归问题

### 2026-03-19

#### 安全修复 (Security Fixes)
- **PasswordEncryptionUtil.java**: 修复硬编码加密密钥的 P0 安全漏洞
  - 使用 `PLATFORM_ENCRYPTION_KEY` 环境变量替代硬编码密钥
  - 添加开发/测试环境的 fallback 机制
  - 添加生产环境启动验证（非开发环境必须有环境变量）
  - **TDD 实现**: 20 个单元测试 + 3 个集成测试，覆盖率 > 80%

#### 修复 (Fixes)
- **client.js**: 修复导航跳转绕过 Vue Router 守卫的问题
  - 使用 `router.push()` 替代 `window.location.href`
  - 确保路由守卫正确触发
  - 添加 NavigationDuplicated 错误处理
  - 添加 E2E 测试覆盖路由跳转场景

#### 文档 (Docs)
- 更新 `TECHNICAL_DEBT.md`: 记录 P0 修复完成状态
- 添加密码加密安全修复的 TDD 实施报告
- 添加路由导航修复摘要

---

### 2026-03-11

#### 修复 (Fixes)
- **project.js**: 添加 API 失败时的 mock 数据回退逻辑，解决白屏问题
- **List.vue**: 修复"查看详情"按钮路由跳转失败问题
  - 添加 async/await 错误处理
  - 添加调试日志
  - 实现编辑按钮跳转功能
- **mock.js**: 修复语法错误（删除多余的 `}`）

#### 文档 (Docs)
- 新增 `src/stores/README.md`
- 新增 `src/api/README.md`
- 新增 `src/views/Project/README.md`
- 为修改的源码文件添加标准头部注释

---
