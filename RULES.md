# RULES.md — 项目强制红线与开发作业流程

本文件定义本项目当前有效的开发红线、执行步骤和已知存量例外。
目标是让参与项目的人类与 AI 代理都能区分：什么是已经落地的硬门禁，什么是当前代码仍未收口但必须继续清理的历史问题。

---

## 1. 标准作业流程（SOP）

所有功能开发、Bug 修复、重构任务都必须按以下四阶段执行，不得直接跳到“改代码”。

### Phase 1: Plan（规划）

- 开始实现前先明确目标、改动范围、验收标准和风险点。
- 涉及多个模块、架构边界或测试基线调整的任务，必须先写清楚影响面，再进入实现。
- 禁止“边写边猜”；不清楚接口、状态流转或验收方式时，先查清现状。
- 涉及后端 domain/policy/core 包变更的，必须在 Plan 中明确标注"纯核心边界在哪，外壳层在哪"，未标注不得进入 TDD 阶段。纯核心包受 preflight-domain-purity.sh 和 FPJavaArchitectureTest 双重门禁。

### Phase 2: TDD（测试驱动）

- 优先遵循 Red → Green → Refactor 循环：先补/写测试，再补实现。
- 前端优先使用 Vitest；后端优先使用 JUnit；跨链路回归使用 Playwright。
- 最低要求不是“有测试文件”，而是能证明本次变更确实被验证过。
- 当前仓库允许按影响面分层验证，不要求每次都无脑跑全仓所有测试；但必须如实报告哪些跑了、哪些没跑、哪些受现有基线问题影响。

### Phase 3: Code Review（代码审查）

- 实现完成后，必须主动做自审。
- 检查重点：分层边界、依赖方向、安全输入、错误处理、回归风险、文档同步。
- 发现问题先修，不得把“已知问题”伪装成“后续再说”，除非它本来就是存量基线问题且本次未扩大影响。

- 提交 PR 前必须运行 `scripts/preflight-self-review.sh`，将输出的自审清单粘贴到 PR description body 中，逐项确认后将 ⬜ 改为 ✅。未附自审清单的 PR 可以被 reviewer 关闭。

### Phase 4: Refactor-Clean（重构与清理）

- 在验证通过后再做清理：消重、提炼命名、删死代码、删无效分支。
- 若发现历史双模式、Mock 回退或无用开关，应优先往“删除”方向收口，而不是继续兼容。
- 清理后重新运行相关验证，确认没有引入新的回归。

---

## 2. 核心业务逻辑架构约束

本节约束的是**业务核心逻辑**，目标是防止 AI 代理把数据读取、状态更新、异常处理和业务计算混成难以测试的面条代码。

执行口径：
- 核心业务逻辑必须优先写成可单元测试的纯函数或近似纯函数。
- Controller、Repository、API adapter、Pinia action、Vue 组件事件、事务编排服务属于命令式外壳，允许副作用，但不得承载复杂业务规则。
- 如因框架约束必须在外壳层更新状态，必须先把核心计算提炼到独立函数、Service 或领域方法中。
- 后端新写纯核心代码时，优先放入 `..core..` 或 `..domain..` 包；这些包下的非 `..entity..` 类受 `FPJavaArchitectureTest` 强制约束。

### 2.0 Split-First Rule（防上帝类默认口径）

任何后端功能实现前，必须先按职责拆分，而不是直接堆进一个 `*Service`：

- **Application Service**：只做编排，负责取数、调用纯核心、事务、保存和边界转换。
- **Domain Policy / Rules**：只做业务规则、校验、状态流转、金额/评分/权限计算，默认保持纯函数或近似纯函数。
- **Mapper / Assembler**：只做 DTO / Entity / ViewModel 转换，不承载业务判断。
- **Repository / Gateway**：只做数据库或外部系统访问，不承载业务规则。
- **Decision / Result Object**：复杂业务分支优先返回显式结果对象，不允许靠超长 `Service` 方法藏状态变化。

默认文件预算：
- 单个 Java 文件软上限 `200` 行。
- 单个 Java 文件硬上限 `300` 行。
- 超过硬上限前必须先拆分职责，不得继续追加代码把文件做成上帝类。

### 2.1 纯核心与命令式外壳

业务计算、校验、状态流转、金额计算、评分计算、权限判断必须优先放在纯核心中。

纯核心不得直接调用：
- 数据库、Repository、ORM 查询
- HTTP API、外部系统 SDK
- LocalStorage、SessionStorage、文件系统
- 控制台日志
- 路由跳转
- 当前时间、随机数等隐式输入

允许副作用的位置：
- 前端：Vue 组件事件、Pinia action、API 模块、浏览器适配层
- 后端：Controller、Repository、Gateway、Adapter、事务编排 Service

要求：
- 外壳层负责取数、保存、调用外部系统和状态提交。
- 核心层只负责根据显式输入计算显式输出。
- 新增业务规则时，不得直接塞进 API 调用、组件事件或数据库事务代码中。

### 2.2 业务计算函数默认无副作用

领域计算函数必须满足：
- 输入来自显式参数。
- 输出来自返回值。
- 不修改传入对象或数组。
- 不读写全局状态、Store、组件实例字段或静态可变字段。
- 不隐藏调用 API、数据库、文件、浏览器存储。

禁止把复杂业务规则写成只修改外部状态的 `void`/无返回值方法。

例外：
- Vue/Pinia/Spring 等外壳层方法可以更新状态或返回框架要求的类型。
- 这些方法只能做编排，复杂业务规则应提炼到可测试的函数或服务中。

### 2.3 业务错误作为普通值返回

业务可预期失败必须作为普通值返回，例如：
- 校验失败
- 找不到业务对象
- 状态不允许流转
- 权限不足
- 额度不足
- 数据不完整

推荐返回结构：

```js
{
  ok: false,
  code: 'VALIDATION_FAILED',
  message: '预算金额不能为空'
}
```

禁止用异常做正常业务流程控制。

允许异常的场景：
- 编程错误
- 配置缺失
- 数据库不可用
- 网络中断
- 外部系统协议异常
- 不可恢复的系统错误

边界层必须把异常转换为统一错误响应、日志事件或用户可理解的错误提示。

### 2.4 核心逻辑默认不可变

核心逻辑不得原地修改传入对象或数组。

错误示例：

```js
function approveTender(tender) {
  tender.status = 'APPROVED'
  return tender
}
```

正确示例：

```js
function approveTender(tender) {
  return {
    ...tender,
    status: 'APPROVED'
  }
}
```

Vue 组件和 Pinia Store 可以进行受控状态更新，但更新前的业务计算应尽量放到纯函数中完成。

### 2.5 FP-Java Profile 可执行门禁

后端使用 `FPJavaArchitectureTest` 将 FP-Java Profile 变成自动门禁。

适用范围：
- `com.xiyu.bid..core..`
- `com.xiyu.bid..domain..`
- 排除 `..entity..`，因为 JPA Entity 受 ORM 框架约束，允许可变状态和无参构造等例外。

门禁内容：
- 纯核心不得依赖 Controller、Repository、Config、Adapter、Gateway。
- 纯核心不得依赖 Spring Web/Data/JDBC、JPA、日志、文件、网络等命令式外壳或 I/O API。
- 纯核心不得显式依赖 `System`、`Clock`、随机数等隐式输入；Java 枚举 `values()` 编译器生成的 `System.arraycopy` 属于合成字节码误报，不代表业务代码读取系统状态。
- 纯核心不得依赖项目业务异常包；预期业务失败应通过 Result / Optional / ValidationResult 返回。
- 纯核心业务方法不得返回 `void`；状态变化必须通过返回值表达。
- 纯核心业务方法不得声明、构造或捕获异常来表达业务流程。
- 纯核心数据默认使用 record 或 final 字段，不暴露 setter。

执行命令：

```bash
cd /Users/user/xiyu/worktrees/cursor/backend
mvn test -Dtest=FPJavaArchitectureTest
```

说明：
- 这是面向新增纯核心包的硬门禁，不会把当前存量 DTO、Service、JPA Entity 一次性纳入红灯区。
- 如果某段逻辑需要被 FP-Java Profile 保护，应主动迁入 `core` / `domain` 非 Entity 包，而不是继续留在事务编排 Service 中。
- 当前新增纯核心包包括 `com.xiyu.bid.ai.core` 与 `com.xiyu.bid.biddraftagent.domain`，分别承载 AI 基础规则和标书生成 Agent 规则。



### 2.5.1 纯核心策略接入 Shell 的三种注册方式

纯核心（domain / core 包下的策略类）默认不应依赖 Spring 框架，
但 Spring Shell（Application Service、Controller 等）需要将它们注入为 Bean。
以下三种模式按优先级排列，选用时遵循"越少侵入纯核心越好"的原则。

_背景（2026-05~06）_: `KnowledgeCaseMatchPolicy` 先后 3 次（#26, #34, #38/#69）
因错误的注册方式导致 228 个测试全崩，核心原因是代理不理解纯核心类
"不写 @Component" 和 "Shell 拿不到 Bean" 之间的平衡点。

#### 模式 A: @Configuration + @Bean（推荐，零侵入纯核心）

```java
@Configuration
public class CaseworkPolicyConfig {
    @Bean
    public KnowledgeCaseMatchPolicy knowledgeCaseMatchPolicy() {
        return new KnowledgeCaseMatchPolicy();
    }
}
```

纯核心类本身是普通 POJO，没有 `@Component` 或其他 Spring 注解。
`@Configuration` 类放在 `application` / `config` 包中，负责显式注册。
这是最干净的分离方式，也是 AGENTS.md 中纯核心门禁的默认要求。

#### 模式 B: @Component on 无状态纯核心（可接受，有前提）

```java
@Component
public class BidDocumentSectionChecks {
    // 纯核心检查逻辑，没有任何 Spring 依赖注入
    public CheckResult check(BidDocument document) { ... }
}
```

前提条件：
- 类本身不注入任何 Spring Bean（`@Autowired`、`@Value`、`@Resource` 等）。
- 类没有可变字段或外部状态依赖。
- 类所在的包已被 `FPJavaArchitectureTest` 门禁覆盖（确保不会意外引入框架依赖）。

适用场景：无状态的 Guard、Policy、Validator，且暂不需要单元测试隔离。
这种方法在 PR #65 的 `BidDocumentSectionChecks` 等拆分中已被证明可行。

#### 模式 C: 纯核心 Config + new 实例（灵活，适合参数化）

```java
@Configuration
public class ScoringPolicyConfig {
    @Value("${scoring.pass-threshold:60}")
    private int passThreshold;

    @Bean
    public ScoringPolicy scoringPolicy() {
        return new ScoringPolicy(passThreshold);
    }
}
```

适用于需要在创建时将配置参数（数据库读取、配置文件、外部 API 响应等）
注入到纯核心构造函数中的场景。

#### 禁止模式

❌ **在纯核心类上加 @Component 且同时编写 @Autowired 字段**。
这会立刻违反 FP-Java 门禁，导致测试因 ApplicationContext 加载失败而全崩。

❌ **手动 new 策略类但忘记注册为 Bean**（`NoSuchBeanDefinitionException`）。
228 errors 的根源就在于此。

#### 门禁覆盖

`FPJavaArchitectureTest` 新增 `pure_core_domain_should_prefer_config_over_component()` 测试，
对 `domain/` 包下非 Entity 类检测 `@Component` 注解并输出警告（不阻断），
提醒开发者优先使用 Config 注入模式。

### 2.6 Split-First 可执行门禁

后端使用 `MaintainabilityArchitectureTest` 约束受保护模块中的 `Service` 形状，阻止新功能第一次落地就长成上帝类。

当前受保护模块：
- `calendar`
- `collaboration`
- `competitionintel`
- `marketinsight`
- `scoreanalysis`
- `roi`
- `versionhistory`
- `documenteditor`
- `documents`
- `analytics`
- `approval`
- `batch`
- `compliance`
- `export`
- `projectworkflow`

门禁内容：
- 受保护模块的 `Service` 文件默认不得超过 `300` 行。
- 受保护模块的 `Service` 默认不得依赖超过 `5` 个实例协作者。
- 受保护模块的 `Service` 默认不得暴露超过 `8` 个公开方法。

执行命令：

```bash
cd /Users/user/xiyu/worktrees/cursor/backend
mvn test -Dtest=MaintainabilityArchitectureTest
```

说明：
- 这是棘轮式门禁，不是假装全仓已经收口。当前少量超标的历史 `Service` 先保留小范围白名单，后续重构时逐个摘除。
- 新增代码不允许以”历史上也很大”为理由继续扩散；一旦进入受保护模块，就必须按 Split-First Rule 拆分。

### 2.6.1 棘轮口径（白名单的使用与退出）

- 白名单集合（`SERVICE_LINE_BUDGET_EXEMPTIONS` / `SERVICE_DEPENDENCY_BUDGET_EXEMPTIONS` / `SERVICE_PUBLIC_METHOD_BUDGET_EXEMPTIONS`）只收录**当前已超标的历史 Service**，不是通行证。
- 下次触碰白名单内 Service 时必须朝门禁方向减少：
  - 行数只能减不能加。
  - 协作者数量只能减不能加。
  - 公开方法数量只能减不能加。
- 一旦某条目对应的类回落到门禁阈值以下，必须**从白名单中移除**；不允许以”也在白名单里”为由继续堆。
- 新增 Service 不允许直接进入白名单；必须按 Split-First Rule 在落地时就符合 300/5/8 预算。

### 2.6.2 核心源码 300 行棘轮门禁

仓库级 `check-line-budgets.mjs` 将 300 行预算扩展到核心源码目录，避免前端页面、组件、composable、API 模块与后端主源码继续无约束膨胀。

当前纳入范围：
- `src/views/**`
- `src/components/**`
- `src/composables/**`
- `src/api/modules/**`
- `backend/src/main/java/**`

当前排除范围：
- `**/*.spec.*`
- `**/*.test.*`
- `backend/src/test/**`
- `scripts/**`
- `.github/workflows/**`
- `backend/src/main/resources/db/**`
- `package.json`
- `backend/pom.xml`

门禁口径：
- 新文件超过 `300` 行，直接失败。
- 原本 `<=300` 行的文件，本次改动后超过 `300` 行，直接失败。
- 原本已经 `>300` 行的历史文件，本次改动行数只能减不能增；继续增长直接失败。

执行命令：

```bash
cd /Users/user/xiyu/worktrees/cursor
npm run check:line-budgets
```

说明：
- 该门禁按 diff 范围执行，是棘轮，不是要求一次性清空所有历史超线文件。
- 本地手动执行 `npm run check:line-budgets` 默认检查当前工作区；`.githooks/pre-commit` 执行暂存区检查；CI 按 PR / push diff 执行相同规则。
- 现有局部门禁（如 `MaintainabilityArchitectureTest` 与 Bidding 页面预算测试）继续保留，作为更细粒度的专项约束。

### 2.7 设置持久化契约（Defaults / Migration / User Config）

系统设置、角色权限、数据范围等管理员可保存配置必须区分三种所有权：

- **Defaults**：只用于首次创建缺失配置，不得在读取、列表刷新、bootstrap、定时刷新等普通路径里反复应用。
- **Migration**：已上线环境需要补齐新默认权限或新字段时，必须写一次性迁移脚本，并说明影响面。
- **User Config**：管理员保存后的配置是事实源，后续默认值、bootstrap 和初始化器不得覆盖用户保存过的字段。

执行要求：
- 设置类保存链路必须补契约测试，至少覆盖“保存 → 再读取/列表刷新/触发初始化 → 用户配置保持不变”。
- 恢复系统默认值必须通过显式重置入口完成，例如 `RoleProfileService.resetRole`，不得藏在普通刷新路径中。
- bootstrap / initializer 如需写已有数据，只能写系统身份、版本戳等非用户配置字段；写用户配置字段必须改为迁移脚本或显式重置。
- 当前角色权限护栏由 `RoleProfileServicePersistenceTest` 和 `RoleProfileBootstrapArchitectureTest` 覆盖。

执行命令：

```bash
cd /Users/user/xiyu/worktrees/cursor/backend
mvn test -Dtest=RoleProfileServicePersistenceTest,RoleProfileBootstrapArchitectureTest
```

---


### 2.6 Composable 内联规则

#### 2.6.1 规则

当组合式函数（composable）满足以下全部条件时，**必须**直接写在组件的 `<script setup>` 内，不得提取到独立文件：

1. **唯一引用**：当前只被一个组件使用。
2. **小型逻辑**：预估行数 ≤ 80 行（含空行和注释）。
3. **无复用预期**：未来 2 个迭代内没有多组件复用的计划。

#### 2.6.2 为什么

Vite/Rollup 在构建时会对异步 chunk 中的跨文件依赖做内联（inline）。实测发现当 composable 被静态导入并在异步 chunk 中被内联时，tree-shaking 可能错误截断函数体——只保留部分代码（如某个 ref 初始化），丢失其余 ref 创建、方法定义和 return 语句。调用该函数返回 `undefined`，导致解构调用方崩溃。

详见 PR #447 修复记录。

#### 2.6.3 例外

- 确实需要多组件复用且行数 > 80 行的 composable，可以提取到独立文件，但构建后**必须**通过 `npm run build:check` 检查产物，确保内联后的函数体完整。
- 框架适配类（如 router hooks、store bridge、薄层编排型 composable）不受此限。薄层编排型 composable 指仅做子 composable 聚合编排、自身不含大量 `ref`/`reactive` 初始化逻辑的入口层 composable。
- 构建管道已集成 `npm run build:check`（构建后产物检测）和 `npm run check:composable-placement`（源码级预检），新写 composable 前可运行 `npm run check:composable-placement --staged` 检查暂存区是否符合内联规则。


## 3. Mock 政策（统一决策）

### 3.1 唯一事实源

- 项目从当前时点起，统一按**真实后端 API 单一路径**执行。
- 前端页面、组件、Store、路由、E2E、演示脚本均不得把 Mock 作为可选正常路径。
- 新功能、新页面、新接口联调一律不允许增加双模式判断。

### 3.2 对存量遗留的处理原则

- 仓库内仍保留 `frontendDemo` 适配层、`demoPersistence` 等历史遗留。
- 这些内容的身份是：**待删除技术债**，不是允许继续沿用的架构设计。
- 遇到相关代码时，允许在本次任务范围内继续清理；不允许新增依赖、不允许扩散使用面、不允许恢复成默认链路。

### 3.3 禁止事项

- 禁止新增任何以 `mock`/`demo` 为条件的数据分支。
- 禁止新增从本地静态假数据读取业务数据的逻辑。
- 禁止以“联调未完成”为由添加本地假数据兜底。
- 禁止把历史 `mock` 命令写回 README、演示流程或测试基线。

---

## 4. 当前硬门禁与执行口径

### 4.1 当前可直接执行的前端门禁

以下命令当前在仓库中可执行，并应作为前端与文档的硬门禁：

```bash
npm run check:line-budgets
npm run check:front-data-boundaries
npm run check:doc-governance
npm run build
```

说明：
- `npm run check:line-budgets` 默认检查当前工作区；如需与 pre-commit 一致的暂存区口径，执行 `npm run check:line-budgets:staged`。它在 pre-commit 与 CI 中独立执行，不并入 `build`。
- `npm run build` 当前会串行执行前两条门禁后再进行 Vite 构建。
- 这些门禁当前是可信的，但覆盖范围主要在前端边界与文档治理，不代表后端架构已完全收口。

### 4.2 当前后端验证口径

建议命令：

```bash
cd backend
mvn test -Dtest=<相关测试类>
mvn test -Dtest=ArchitectureTest
mvn test
```

当前现状：
- `ArchitectureTest` 已存在并能运行，当前基线已恢复为**全绿**。
- 截至 2026-04-16，历史上的两类失败已完成修复：
  - `config -> service` 违规依赖：`E2eDemoDataInitializer` 不再直接依赖 `RoleProfileService`
  - `config <-> service` 循环依赖：`RateLimitService` 不再依赖 `ExportConfig`
- 因此，当前后端验证要求是：
  - 至少运行与本次变更相关的测试
  - 若触及架构边界，运行 `ArchitectureTest`
  - 若新增或修改 `..core..` / `..domain..` 非 Entity 纯核心代码，运行 `FPJavaArchitectureTest`
  - 若新增或扩展受保护模块的 `Service`，运行 `MaintainabilityArchitectureTest`
  - 若新增或修改带 `projectId` 的 Controller、Service、DTO、命令或实体，运行 `ProjectAccessGuardCoverageTest`
  - 如出现失败，按新引入问题处理
  - 不得再把当前仓库写成“存在已知存量失败”

### 4.3 项目权限覆盖门禁

后端使用 `ProjectAccessGuardCoverageTest` 建立项目关联接口的棘轮式权限门禁。

门禁口径：

- 扫描真实后端 `Controller`、`Service`、`Guard` 源文件。
- 文件中直接出现 `projectId`，或引用了带 `projectId` 字段的 DTO、命令、实体、模型、视图类型，即视为项目关联入口。
- 项目关联入口必须出现统一项目权限守卫证据，例如 `ProjectAccessScopeService`、`ProjectLinkedRecordVisibilityPolicy`、`assertCurrentUserCanAccessProject`、`requireProjectAccess`、`ExpenseAccessGuard` 等。
- 未命中守卫证据的存量文件必须进入 `src/test/resources/project-access-guard-baseline.txt`，并写明“委托到受保护服务”“非用户请求路径”“仍是权限债”等具体理由。
- 基线条目会做陈旧校验：文件不再是扫描候选时，必须从基线移除。

执行命令：

```bash
cd backend
mvn test -Dtest=ProjectAccessGuardCoverageTest
```

该门禁是棘轮，不声称全仓所有存量项目关联接口都已完成收口；它防止新增或改动的项目关联入口在没有统一守卫或显式豁免说明的情况下进入代码库。

---

## 5. 架构边界

### 5.1 后端分层原则

目标分层仍然是：

```text
Controller → Service → Repository → Entity
```

当前执行要求：
- 新代码不得让 Controller 直接访问 Repository。
- 新代码不得继续制造 `config -> service` 或 `service -> config` 循环。
- 新业务优先放入按业务域分包的结构中，不再把新能力塞回根层平铺包。
- 如果为了兼容存量代码临时触碰例外，必须在结论中明确指出，不得伪装成规范实现。

### 5.2 前端边界

- 页面、组件、Store 不得直接 import `@/api/mock`。
- 页面、组件、Store 不得直接 import `@/utils/demoPersistence`。
- 页面、组件、Store 不得新增本地 `mockData` 作为失败兜底。
- 路由、Store、API 模块中现存的 Mock 遗留判断属于待清理项，本次之后不允许继续扩散。

### 5.3 关于自动检查脚本的真实能力

- `scripts/check-front-data-boundaries.mjs` 当前会扫描 `views`、`components`、`stores` 下的部分直接违规导入与本地 `mockData` 定义。
- 该脚本**当前还不能完整识别**所有 `isMockMode()` 或 API 模块内部的双模式遗留。
- 因此，代码审查仍需人工补位，不能把脚本通过等同于“Mock 已彻底清理完毕”。

---

## 6. 安全要求

### 6.1 当前必须遵守的要求

- 除白名单外的 API 默认需要认证。
- 管理端点 `/api/admin/**` 必须要求管理员角色。
- 密码必须使用 BCrypt 存储。
- 用户输入必须经过清洗与校验后再入库或参与业务处理。
- 禁止硬编码凭据、密钥、Token。
- 禁止在日志中输出密码、Token 原文和完整敏感身份信息。

### 6.2 当前代码与目标策略的差异

当前 `SecurityConfig` 中还存在以下现状：
- 白名单实际额外包含 `/api/auth/sessions`、`/actuator/info`、`/h2-console/**`
- 默认 CORS 仍允许 `5173`、`5174`、`3000` 等历史开发端口

这些属于当前代码事实，不代表目标生产策略。
在安全配置尚未进一步收口前：
- 不得继续扩大白名单
- 不得继续扩大允许的默认跨域来源
- 如需调整，必须同时更新文档与配置，并说明影响面

---

## 7. 端口与环境约定

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 1314 | 默认开发与演示端口 |
| 后端 | 18080 | Spring Boot API 服务 |

补充说明：
- 根目录 `npm run dev:all` 会以真实 API 模式启动前后端。
- 根目录 `start.sh` 当前会给后端注入 `SPRING_PROFILES_ACTIVE=dev,mysql`，并给前端注入 `VITE_API_MODE=api`。
- `14173` 等临时端口只能用于短时排查，不作为项目口径写回文档、截图、测试或演示说明。

---

## 8. 文档同步要求

- 代码变更必须同步更新受影响的 README、规则说明和使用入口文档。
- 文档中必须区分“当前已实现事实”和“目标策略/待清理事项”，禁止继续写成混淆状态。
- 文档治理检查命令 `npm run check:doc-governance` 必须通过。

---

## 9. 多 Agent 工程化红线

### 9.1 强制隔离规则
- **严禁目录跨越**：禁止 Agent 在基准区 `/Users/user/xiyu/xiyu-bid-poc/` (main 分支) 进行任何代码修改或任务分支开发。
- **任务分支隔离**：一个任务对应一个独立分支，命名格式为 `agent/[agent-name]-[task-description]`。严禁多个 Agent 共享同一个分支。
- **配置隔离**：禁止 Agent 修改公共 `.env` 模板文件，除非任务本身要求调整全局环境变量。

### 9.2 强制 SOP 阶段执行
所有任务必须严格闭环：
1. **Plan**：在 Track 中明确允许修改的文件范围，禁止超纲修改。
2. **TDD**：代码变更必须伴随测试用例（或更新现有测试）。
3. **Review**：检查是否引入了跨模块依赖。
4. **Clean**：完成任务后必须执行 `git fetch origin` + `git rebase origin/main`。

### 9.3 并行开发禁忌
以下场景严禁多个 Agent 同时并行执行，必须串行处理：
- 数据库表结构变更 (Flyway Migration)。
- 公共组件重构、路由大改、权限模型调整。
- `package.json` 或 lock 文件的大规模更新。

### 9.4 文件锁门禁（per-task 模式）
- **锁机制**：自 2026-05-12 起改为 per-task 单文件模式，不再使用单文件 `.agent-locks.yml`。
  - `.agent-locks/<task-slug>.yml` — 每个任务一个锁文件，新任务 = 新文件 = 零冲突。
  - `.agent-locks.yml` — **DEPRECATED**；仅做 read-only 兼容，`npm run agent:lock-check` 仍会读取并合并。
- **锁定时机**：Plan 阶段发现将修改高冲突文件、共享入口、公共目录或已有 Agent 正在开发的区域时，必须先登记锁再编码。
- **命令化管理**：
  - 批量初始锁：`scripts/agent-start-task.sh <agent> <task> --lock <path> --lock-dir <dir> --lock-reason "<reason>"`
  - 追加锁：`npm run agent:lock-acquire -- --path <path> --scope file --reason "<reason>"`
  - 释放锁：`npm run agent:lock-release -- --path <path>` 或 `npm run agent:lock-release -- --all`
  - 查询锁：`npm run agent:lock-check`（列所有锁）、`npm run agent:lock-check:changed`（仅检查当前改动是否撞锁）
- **CI 检查**：PR 会用当前 diff 执行 `scripts/check-agent-locks.mjs --base <base> --head HEAD`，锁冲突会阻断合并。
- **职责边界**：文件锁只用于提前暴露并行修改冲突；高频锁冲突文件必须按 Split-First Rule 拆分职责，不能长期靠锁排队。

### 9.5 仓库历史危险操作清单（强红线）

以下命令任何一条单独执行都会**改写 git 历史**，是导致 `main` 出现 disconnected root commits 类故障的直接成因（参见 [ericforai/bidding#224](https://github.com/ericforai/bidding/issues/224)，曾在一次操作中静默丢失企微登录入口）。本节列出的所有命令在 `main`、`master`、`release/*` 上**默认禁止**：

- `git filter-repo`（及其前身 `git filter-branch`）
- `git checkout --orphan`
- `git replace --graft` / `git replace -d`
- `git rebase --root`
- `git push --force` / `git push --force-with-lease`
- `git reset --hard` 后接 `git push`（任何形式，包括 `--force-if-includes`）
- 通过 GitHub API 直接覆盖 ref（`PATCH /repos/{owner}/{repo}/git/refs/...`）

执行前必须同时满足全部 4 条门槛：

1. **issue 立项**：在 GitHub 上开 issue 描述动机、影响范围、回滚预案；用此 issue 编号关联后续 PR / 工单。
2. **两人签字**：至少 1 名仓库 maintainer + 1 名当事人之外的开发者在 issue 上明确 `LGTM` 后方可执行；签字时间戳必须早于操作。
3. **现状备份**：执行前打 tag `pre-rewrite/<date>-<issue>`，并 push 到远端（这本身不算危险操作，因为只是新增 ref）。
4. **CI / 本地护栏检测的现状**：执行后 `.github/workflows/branch-history-guard.yml` 和 `.githooks/pre-push` 的 ratchet 阈值必须同步调整；否则后续合并会因 ratchet 触发而全员阻塞。

护栏自身（CI workflow 和 git hook）**不绕过**：

- 如需在本地测试 force push，使用一次性环境变量 `FORCE_PUSH_OK=1 git push ...`，仅对当次有效，并且 commit message 或 PR body 必须解释原因。
- 严禁修改 `.githooks/pre-push` 或 `.github/workflows/branch-history-guard.yml` 中的 `MAX_ROOTS` 仅为绕过当前提示——降低阈值只能用于"真的削减了 root 数"的后置收紧场景。

新加入仓库的成员务必执行一次 `./scripts/install-githooks.sh` 启用本地 hook（默认 git 不会主动启用，详见脚本注释）。

---

## 10. 强同步规则 (Sync First Rule)

为确保所有 Agent 都在最新的代码基准上工作，避免“在旧代码上修 Bug”或“制造已修复的冲突”：

### 10.1 开启任务前
任何 Agent 在开始编写代码前，**必须**执行以下同步操作：
1. `git fetch origin`：更新本地仓库对远端分支的感知。
2. `git checkout -b agent/task-name origin/main`：确保新任务是从最新的 `main` 检出的。
3. `scripts/sync-env.sh .`：同步最新的环境变量模板。

### 10.2 开发过程中
若主目录的 `main` 分支发生了合并更新，正在开发中的 Agent **必须**择机执行：
- `git rebase origin/main`：将当前任务分支移至最新基准之上，并立即运行验证脚本（TDD）。
