# 第 3 章 透传范式：让真实信号流过所有层

> 本章素材全部来自本项目的真实演进记录：8 段演进 PR（涉及 !153 / !155 / !158 / !972 / !1023 / !1350 / !1458 / !1460 / !1659 / !1819）、10 个生产踩坑、10 条沉淀经验。所有文件路径均为仓库内真实路径，可直接打开对照源码。
>
> 项目 owner 把「透传」列为本项目最重要的沉淀，原因很直接：它是唯一一条同时压制了 AI 生成本能、撑起了安全审计、又能在下个项目 Day-1 原样复用的工程纪律。

## 3.1 TL;DR

**透传 = 让真实信号（业务错误消息 / TraceID / 字段）从产生点原样流过所有中间层，到达消费点。中间层只允许做两件事：安全判定与格式转换。禁止吞掉、禁止改写、禁止编造、禁止兜底。**

反面就是 AI 生成代码的默认本能：防御性 try-catch 把真实错误吞成「系统繁忙」、Mock 兜底让失败流程「看起来成功」、一刀切硬编码把 409/429/503 全压成 400、顺手 `ex.getMessage()` 把 SQL 和 API Key 直接返回给前端。这四种反模式在本项目全部引发生产事故（证据见 §3.3），而透传范式恰好是它们的对偶解。

透传不是一句口号，它在本项目落地为四条具体链路，每条链路都有明确的组件、文件和可测试的规则：

1. **业务错误消息链**（核心链路）：领域校验失败的消息一路透传到前端弹窗。
2. **blob 下载错误链**：下载失败时，blob 错误体先转 JSON，再透传后端消息。
3. **TraceID 链**：一个追踪 ID 贯穿前端请求、后端日志、出站外部调用、异步线程。
4. **normalizer 字段透传**：前端数据规范化不裁剪后端字段，新增字段自动到达 UI。

## 3.2 四条透传链路详解

### 3.2.1 业务错误消息链（核心链路）

这是最重要的一条链。完整流向：

```
领域/纯核心校验失败
  → 抛 BusinessException(code, message) 或 AppFailureException 子类
  → Application Service 不 try-catch 吞掉、不 Mock 兜底
  → GlobalExceptionHandler 只做异常路由分发
  → ExceptionMessageSanitizer（安全判定）+ ExceptionResponseStrategy（构建响应）
  → 统一响应体 ApiResponse{success:false, code, msg}
  → 前端 axios 响应拦截器优先取 response.data?.msg 弹 ElMessage
  → 无 msg 才按 HTTP 状态码 fallback
```

关键组件与核心规则：

| 组件 | 文件 | 职责 | 核心规则 |
|---|---|---|---|
| BusinessException / AppFailureException | `backend/src/main/java/com/xiyu/bid/exception/BusinessException.java`、`AppFailureException.java` | 业务失败的统一异常基类，携带 code / httpStatus / userMessage / retryable | 业务校验失败必须抛它，**禁止用 IllegalStateException / RuntimeException**——后者会被全局 handler 当系统缺陷吞成通用文案（PR !1659 根因）；code→httpStatus 映射收敛在 `resolveHttpStatus` |
| GlobalExceptionHandler | `backend/src/main/java/com/xiyu/bid/exception/GlobalExceptionHandler.java` | `@RestControllerAdvice`，只做「异常类型 → handler」的路由编排 | 不再直接透传 `ex.getMessage()`（M-03）；消息走 Sanitizer/Strategy，日志走 ExceptionLogger；HTTP 状态码必须用 `ex.getHttpStatus()`，不得硬编码（PR !1458）。拆分后从 691 行降到 487 行 |
| ExceptionMessageSanitizer | `backend/src/main/java/com/xiyu/bid/exception/ExceptionMessageSanitizer.java` | 「异常 → 可安全返回前端的消息」的纯函数映射，无 Spring/IO/日志（PR !1819 拆出，233 行纯核心） | 四类治理 + `isSafeForPassthrough` 五道检查 + `stripCrlf` 防 CRLF 注入，详见 §3.4 |
| ExceptionResponseStrategy + ExceptionLogger | `backend/src/main/java/com/xiyu/bid/exception/ExceptionResponseStrategy.java`、`ExceptionLogger.java` | 前者纯核心构建 ApiResponse（`buildResponse` / `buildFixedResponse` / `buildWithPrefix`）；后者统一日志 + Sentry + 敏感字段脱敏 | 「哪些异常透传、哪些硬编码」的规则集中在这两个类，不散落在各 handler |
| ApiResponse | `backend/src/main/java/com/xiyu/bid/dto/ApiResponse.java` | 统一响应体 `{success, code, msg, data}`，`@JsonProperty("msg")` 输出消息字段（兼容客户规范） | 前后端只认 `msg` 这一个字段名；前端 `response.data?.msg` 是唯一读取点 |
| axios 响应拦截器 | `src/api/client.js` | 统一把后端 msg 弹到 ElMessage；401 自动 refresh；429 合并限流提示 | `serverMsg = response.data?.msg` 优先于按状态码硬编码的 fallback 文案；提供 `silentError` / `skipGlobalErrorMessage` / `silentRateLimit` 三个逃生口 |

链路末端还有一个重要变体：当业务需要自行处理错误（比如失败后回滚 UI 状态）时，调用方显式传 `skipGlobalErrorMessage` 跳过全局弹窗，由业务 catch 块透传 msg 并做状态回滚。实例见 `src/api/modules/tenders.js:382-384`，模式成型于 PR !972（`crmLinkFailedSignal` prop 驱动子组件重置关联状态）。

这条链路上任何一个环节断裂，后果都是静默的——不报错、不告警，只是用户再也看不到真实原因：

| 断点位置 | 典型断法 | 用户看到什么 |
|---|---|---|
| 产生点 | 用 IllegalStateException 代替 BusinessException | 「系统状态冲突，请刷新后重试」（PR !1659） |
| Application Service | 防御性 try-catch + Mock 兜底 | 假成功，假数据进生产库（PR !1350） |
| GlobalExceptionHandler | 硬编码状态码 / 直接透传 ex.getMessage() | 前端无法分流（PR !1458），或泄露内部细节（PR !1819） |
| axios 拦截器 | 不读 msg，按状态码硬编码文案 | 后端精心准备的消息白写（PR !1460） |
| 业务 catch | 不做 UI 状态回滚 | 界面停留在错误状态，用户以为操作生效（PR !972 修复前） |

### 3.2.2 blob 下载错误链

下载场景的错误响应体是 blob，不是 JSON——如果按普通错误处理，后端精心准备的消息根本到不了用户。

- 组件：`showApiDownloadError`（`src/utils/download.js`）。
- 规则：blob 错误响应先 `response.data.text()` 转 JSON，再优先读 `error.response.data.msg`（例如「投标文件已进入『XX』阶段，文件只读不可下载」），取不到才 fallback（PR !1460）。
- 配套：`notifyErrorUnlessRateLimit`（`src/api/error-utils.js`）——业务 catch 块透传 msg，但跳过已被全局拦截器处理过的 429，避免重复提示。
- 防御：非字符串 msg 要做类型防御。
- 测试：PR !1460 带 +7 个测试用例，覆盖各状态码 × msg 组合。

### 3.2.3 TraceID 链

一个追踪 ID 串起「前端发起 → 后端处理 → 出站外部调用 → 异步线程」全链路，是生产排障的命脉。PR !1458 的修复就是由生产日志 traceId 证据链（用户反复点击 9 次）驱动的。

四段式结构：

1. **前端生成**：请求拦截器生成 `X-Trace-Id`（`src/api/client.js:138-156`）。
2. **入站接管**：`TraceFilter`（`backend/src/main/java/com/xiyu/bid/config/TraceFilter.java`）是最高优先级 Filter——取请求头 `X-Trace-Id` 或生成新 ID 写 MDC，并在响应头回写；header 键名集中在 `TraceConstants.java` 管理。规则：traceId 同时进 MDC（结构化日志）和响应头（前后端串联）；MDC 在 finally 清理，防线程复用泄漏。
3. **出站注入**：`TraceHeaderInjector`（`backend/src/main/java/com/xiyu/bid/config/TraceHeaderInjector.java`）从 MDC 读 traceId 注入 `EHSY-TraceID` 出站头。调用方构造完 headers 加一行 `TraceHeaderInjector.inject(headers)` 即可。已接入 5 个 HTTP Client：CrmHttpClient、OpenAiCompatibleClient(Embedding)、WeComApiClient、2 个 MarkItDownSidecarExtractor、OrganizationDirectoryAuthHeaders（PR !153 / !155 / !158）。该类无 Spring 依赖，可直接单测。
4. **异步复制**：`MdcTaskDecorator` + `AsyncConfig`（`backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java`、`MdcTaskDecorator.java`）——所有 `@Async` 线程池挂载装饰器，把主线程 traceId / userId / roleCode 复制到异步线程。MDC 是 ThreadLocal，跨线程必须显式复制，否则异步日志断链（spec 031 / CO-373）。

### 3.2.4 normalizer 字段透传

前端 normalizer（数据规范化层）最容易犯的错是「显式枚举字段」：只挑自己认识的字段重新组装对象，后端新增字段被静默丢弃。本项目付出过代价——`isOssUser` 字段被裁掉导致越权判断失效（`docs/lessons/lessons-learned.md` §1196 / §3160，spec 032）。

规则一句话：**用 `...` 展开保留后端全量字段，normalizer 只做改名/兜底，不做白名单裁剪。**

## 3.3 为什么 AI Coding 尤其需要透传范式

透传范式不是普通的「好的错误处理实践」。它的特殊价值在于：**AI 生成本能恰好是透传的反面**。以下四个反模式在本项目反复出现、且每一个都有生产事故背书。下一个 AI Coding 项目里，它们会以同样的频率再次出现。

### 反模式一：防御性 try-catch 吞掉真实错误

AI 爱写「防御性」代码：catch 住一切异常，返回一条友好的通用文案。结果是真实错误被吞成用户看不懂的话术。

- **事故证据（PR !1659）**：Expense 实体用 `IllegalStateException` 做业务状态校验，被全局 handler 归类为系统缺陷，统一吞成「系统状态冲突，请刷新后重试」。用户反复看到这句话，完全不知道该怎么操作。
- **修复方式**：5 处状态校验改用 `BusinessException(409, ...)`，HTTP 状态不变，但业务消息可以到达前端。这个 PR 同时留下一条范围纪律：「无失败证据的同类模式不扩张改」——修 bug 不顺手重构。

### 反模式二：Mock 兜底让流程「不报错」

AI 极度厌恶流程报错，倾向于在外部依赖失败时返回写死的假数据，让流程「顺利走完」。

- **事故证据（PR !1350）**：资质 AI 解析在未配置 / 调用失败 / 文档提取失败时，返回写死的假数据「软件著作权证书（AI Fallback)」。这些假资质直接写进生产资质库，且用户以为解析成功了。
- **修复方式**：删除 `getMockFallback()`，三种失败路径全部改抛 `BusinessException` 透传真实原因，让用户选择重试或人工填写。此修复与 SECURITY.md 的禁止 Mock 政策合流——**禁止 Mock 兜底必须同时是安全政策和透传纪律**。

### 反模式三：一刀切硬编码状态码

AI 生成的错误处理喜欢「收敛」：不管什么错误，统一返回一个状态码、一句文案。

- **事故证据（PR !1458，CO-442）**：GlobalExceptionHandler 把 `BusinessException`(409) / `RetryableOperationException`(429) 全部压成 HTTP 400。前端无法按状态分流，生产用户反复点击 9 次——这个证据是从 traceId 串联的生产日志里挖出来的。修复时还发现「同类遗漏」：修了 `handleBusinessException`，漏了紧邻的 `handleAppFailureException`（commit a63a958db）。
- **同类前端版（PR !1460）**：下载只处理 401/403，其余状态码统一「文件下载失败，请稍后重试」，后端精心准备的消息全部白写。
- **修复方式**：handler 一律用 `ex.getHttpStatus()`；前端所有错误出口「msg 优先、状态码 fallback」。

### 反模式四：顺手 ex.getMessage() 泄露内部信息

AI 生成异常处理时最顺手的写法就是 `return ex.getMessage()`——这是透传的另一面风险：不受控的透传。

- **事故证据（M-03 安全审计，PR !1819）**：GlobalExceptionHandler 里 11 处直接透传 `ex.getMessage()`，可能泄露 SQL 片段、API Key、storagePath 内部路径、企微 access_token、JWT 细节。
- **修复方式**：按 FP-Java Split-First 拆出 ExceptionMessageSanitizer / ExceptionResponseStrategy / ExceptionLogger，137 个测试全绿。详见 §3.4。

### 小结：透传范式为什么是 AI 的对偶解

这四个反模式的共同点是：**AI 在用自己的「判断」替代信号的原始内容**——吞掉它、编一个、压平它、或者不加判断地倒出来。透传范式把问题反过来了：消息从产生点原样流到 UI，每层只做安全判定，不做内容决策。这个表述可机械执行、可写进门禁、可用集成测试锁死，恰好抵消 AI 的生成噪声。而且纯核心拆分（Sanitizer / Strategy 无 Spring 依赖的纯函数）让 AI 能写出可单测的安全判定，而不是把逻辑糊在 handler 里。

## 3.4 安全边界：透传 ≠ 什么都透

透传最容易被误读成「把所有错误都扔给前端」。恰恰相反，本项目的透传范式是在 M-03 安全审计之后才真正成熟的——**透传的是『受控业务消息』，不是『异常文本』**。

### 异常四类治理

| 类别 | 代表 | 处理 | 说明 |
|---|---|---|---|
| 受控异常 | `AppFailureException` / `ExternalServiceException` 等 | **透传 userMessage** | 消息在产生点就是为终端用户写的，原样流过 |
| 半受控异常 | `IllegalArgumentException` | **过 `isSafeForPassthrough` 五道检查后透传** | 消息不是为用户写的，但多数情况下内容安全 |
| 认证 / 资源缺失类 | 认证失败、资源不存在等 | **硬编码友好文案** | 避免泄露资源是否存在等敏感信息 |
| 未受控异常 | NPE、SQL 异常等一切未知异常 | **永不透传**，硬编码默认消息 | 内部细节只进日志和 Sentry |

### isSafeForPassthrough 五道检查

半受控异常的消息必须依次通过五道安全判定才允许透传（全部在 `ExceptionMessageSanitizer` 纯核心里，无副作用、可单测）：

1. 长度 ≤ 200 字符；
2. 不含 SQL 关键字；
3. 不含 `com.xiyu` / `org.springframework` 等包路径；
4. 不含堆栈痕迹；
5. 不含 `.java` / `.class` 后缀。

另加一道全局防护：**所有对外消息过 `stripCrlf`**——异常 message 写入日志/响应时若含 `\r\n`，可伪造日志行或响应头（CRLF 注入）。该防护收编在 Sanitizer 纯核心里统一执行，不散落各处。

### 与 FP-Java Split-First 的协同

透传范式能在本项目立住，靠的是 FP-Java 的拆分纪律兜底：

- GlobalExceptionHandler 只做路由编排（691 行 → 487 行）；
- 消息安全判定（Sanitizer）、响应构建（Strategy）、日志上报（Logger）全部拆成**无 Spring 依赖、无副作用的纯核心**；
- 安全规则因此可以被单测锁死（PR !1819 带 137 个测试），并被 FP-Java 的 ArchUnit 门禁保护；
- 关键收益：**AI 改 handler 时碰不到安全逻辑**。安全判定收编在纯核心之后，AI 在路由层写不出「顺手透传 ex.getMessage()」的代码——路由层根本没有消息构建的职责。

这也是给下个项目的核心启示：不要指望 AI 每次都记起安全规则，要把规则放进它碰不到的地方。

## 3.5 演进时间线：范式是怎么长出来的

透传范式不是顶层设计出来的，而是被事故和审计一步步推着长出来的。把 8 个演进 PR 按时间串起来，能看出三个清晰的阶段——这对下个项目很有参考价值：**§3.7 的 Day-1 清单，本质上是把这一整段演进压缩到第一天完成。**

**阶段一：先修可观测性（TraceID 奠基）**

- **PR !153 / !155 / !158**——统一 TraceID 透传奠基：新增 TraceConstants + TraceHeaderInjector（无 Spring 依赖可单测），5 个出站 HTTP Client（CRM / OpenAI / 企微 / MarkItDown×2 / 组织目录）各加一行 `inject` 接入（commit 0f80ed8e0，merge a96e34c1f）。

注意起点不是错误消息，而是 TraceID。这个顺序很关键：先有全链路可观测性，后面的事故才都能被证据驱动修复——PR !1458 里「用户反复点击 9 次」的证据链，就是从 traceId 串联的生产日志里挖出来的。没有这一步，后面每个 bug 都只能靠猜。

**阶段二：前端透传纪律成型**

- **PR !972（CO-308）**——前端错误透传 + UI 状态回滚模式成型：catch 块拆分 404 / 其他路径，其他错误 `ElMessage.error` 透传后端真实 msg，移除「409 → 标讯已删除」的错误映射；引入 `skipGlobalErrorMessage` 跳过全局弹窗；`crmLinkFailedSignal` prop 驱动子组件重置关联状态（commit 707e7ffa0）。
- **PR !1023**——修复关联 CRM 商机失败时「全局拦截器 + 业务 catch」双重弹窗，确立 `skipGlobalErrorMessage` 出口纪律。

**阶段三：回潮补课与安全收编**

- **PR !1350**——透传取代 Mock 兜底：删除 `getMockFallback()` 假数据「软件著作权证书（AI Fallback)」，AI 未配置 / 调用失败 / 文档提取失败全部改抛 BusinessException 透传真实原因，与 SECURITY.md 禁止 Mock 政策合流（commit 001488eef）。
- **PR !1458（CO-442）**——后端 HTTP 状态码透传修复：`handleBusinessException` / `handleAppFailureException` 从硬编码 BAD_REQUEST 改用 `ex.getHttpStatus()`，409 / 429 / 503 业务语义不再被压成 400（commit ca4368262 + a63a958db）。
- **PR !1460**——前端下载错误透传：`showApiDownloadError` 优先读 `error.response.data.msg`，blob 错误体先转 JSON；+7 测试用例（commit d62c80e5b）。
- **PR !1659**——「IllegalStateException → BusinessException」模板确立：Expense 实体 5 处状态校验改用 `BusinessException(409, ...)`；并留下「无失败证据的同类模式不扩张改」的范围纪律（commit c3bd34a47）。
- **PR !1819（M-03）**——透传范式安全化收编：GlobalExceptionHandler 11 处 `ex.getMessage()` 直接透传被修复，按 FP-Java Split-First 拆出 ExceptionMessageSanitizer（233 行纯核心，五道判定 + CRLF 防护）/ ExceptionResponseStrategy / ExceptionLogger，137 个测试全绿（commit 7d76a1854）。

三个观察：

1. **阶段三的四个 PR（!1350 / !1458 / !1460 / !1659）全是回潮补课**——纪律确立之后，AI 生成仍然一次次重新引入反模式。透传范式的敌人不是一次性 bug，而是生成噪声的持续回潮，这正是必须用测试和门禁锁死的原因。
2. **演进终点是安全收编（!1819）而非安全让步**——透传和安全不是对立面，成熟的形态是「受控消息原样透传 + 安全判定收编纯核心」。
3. **每个修复 PR 都带着测试**（+7 用例、137 测试、集成测试 7/7）——这不是巧合，是防止下一次回潮的唯一手段。

## 3.6 踩坑清单

以下 10 个坑全部来自本项目真实事故或复盘，按链路归类。证据列里的 PR 号 / commit / 文档锚点均可回查。

| # | 踩坑 | 证据 | 规则 |
|---|---|---|---|
| 1 | `ex.getMessage()` 直接透传泄露内部细节：GlobalExceptionHandler 11 处直接透传，可能泄露 SQL 片段、API Key、storagePath、企微 access_token、JWT 细节 | PR !1819（M-03） | 异常四类治理；判定逻辑必须是无副作用纯核心，可单测锁死 |
| 2 | 业务校验误用 `IllegalStateException`，被全局 handler 当系统缺陷吞成「系统状态冲突，请刷新后重试」 | PR !1659 | 预期内业务失败一律 `BusinessException(code, message)` / AppFailureException 子类；`IllegalStateException` 只留给真正的系统缺陷 |
| 3 | 全局 handler 硬编码 HTTP 状态码，409 业务冲突 / 429 限流 / 503 降级全被压成 400，前端无法分流，生产用户反复点击 9 次 | PR !1458 | handler 必须用 `ex.getHttpStatus()`；修复时警惕同类遗漏——修了 `handleBusinessException` 漏了 `handleAppFailureException`（a63a958db） |
| 4 | AI 解析失败返回写死假数据兜底，假资质「软件著作权证书（AI Fallback)」污染生产资质库，用户以为解析成功 | PR !1350 | 禁止 Mock / 假数据兜底；失败必须抛业务异常透传真实原因，让用户选择重试或人工填写 |
| 5 | 前端错误处理一刀切：下载只处理 401/403，其他统一「文件下载失败，请稍后重试」 | PR !1460 | 所有前端错误出口先读 `response.data?.msg` 再 fallback；blob 错误先转 JSON；补测试覆盖状态码 × msg 组合 |
| 6 | 全局拦截器弹窗与业务 catch 弹窗重复（双重弹窗） | PR !1023 | 业务需自行处理错误时显式 `skipGlobalErrorMessage` / `silentError`，由调用方统一透传 msg 并做 UI 状态回滚（!972 的 crmLinkFailedSignal 模式） |
| 7 | 自定义透传 header 未进 CORS 白名单，浏览器预检直接失败（X-Trace-Id 登录预检 400） | commit 68b8dad20 | 新增任何透传 header（X-Trace-Id / EHSY-TraceID 等）必须同步 SecurityConfig 的 CORS allowedHeaders |
| 8 | 异常 message 写入日志/响应含 `\r\n`，可伪造日志行或响应头（CRLF 注入） | PR !1819 | 所有对外消息过 `stripCrlf`；防护收编在 Sanitizer 纯核心统一执行 |
| 9 | MDC 是 ThreadLocal，`@Async` 异步线程拿不到 traceId / userId，异步任务日志断链 | spec 031 / CO-373 | 所有自定义线程池必须挂 `MdcTaskDecorator` 复制 MDC 上下文 |
| 10 | normalizer 显式枚举字段，后端新增字段被静默丢弃（isOssUser 越权案例） | lessons-learned §1196 / §3160（spec 032） | 字段透传用 `...` 展开保留全量字段；normalizer 只做改名/兜底，不做白名单裁剪 |

## 3.7 下个项目 Day-1 落地清单

透传范式不依赖本项目的任何业务上下文，可以原样搬进下一个项目。以下是 Day-1 就该建好的组件和检查表。

### 组件清单

**后端（8 件）：**

- [ ] `BusinessException` / `AppFailureException` 异常基类：携带 code / httpStatus / userMessage / retryable；code→httpStatus 映射收敛在一处（参考 `resolveHttpStatus`）。
- [ ] `GlobalExceptionHandler`：只做异常类型 → handler 的路由编排，HTTP 状态码一律 `ex.getHttpStatus()`，禁止硬编码。
- [ ] `ExceptionMessageSanitizer`（纯核心）：四类治理 + `isSafeForPassthrough` 五道检查 + `stripCrlf`。
- [ ] `ExceptionResponseStrategy`（纯核心）：统一构建响应体。
- [ ] `ExceptionLogger`：统一日志 + Sentry + 敏感字段脱敏。
- [ ] `ApiResponse`：`{success, code, msg, data}`，`@JsonProperty` 钉死 `msg` 字段名。
- [ ] `TraceFilter` + `TraceConstants`：入站接管/生成 traceId，写 MDC + 响应头回写，finally 清理 MDC。
- [ ] `TraceHeaderInjector` + `MdcTaskDecorator`：出站一行式注入；所有线程池挂装饰器复制 MDC。

**前端（3 件）：**

- [ ] axios 拦截器：`response.data?.msg` 优先、状态码 fallback；401 refresh、429 合并提示；请求拦截器生成 `X-Trace-Id`。
- [ ] 逃生口：`skipGlobalErrorMessage` / `silentError` / `silentRateLimit`，业务 catch 接管透传 + UI 状态回滚。
- [ ] `showApiDownloadError` 类 helper：blob 错误先 `text()` → JSON.parse 再取 msg；非字符串 msg 防御。

**配置（1 件）：**

- [ ] CORS `allowedHeaders` 同步放行所有自定义透传 header（X-Trace-Id / EHSY-TraceID）。

### 检查表（合入前自查）

- [ ] 业务校验失败抛的是 BusinessException 系，不是 IllegalStateException / RuntimeException。
- [ ] handler 里没有任何 `ex.getMessage()` 直接进响应；没有任何硬编码状态码。
- [ ] 外部依赖失败抛业务异常透传真实原因，没有任何 Mock / 假数据兜底。
- [ ] 前端每个错误出口都是「msg 优先」，包括 blob/下载场景。
- [ ] 需要业务自行处理错误的调用都显式带了 `skipGlobalErrorMessage`，且有 UI 状态回滚。
- [ ] 新增透传 header 已同步 CORS 白名单。
- [ ] 新增线程池已挂 MdcTaskDecorator。
- [ ] normalizer 用 `...` 展开，没有显式枚举裁剪。
- [ ] 透传链路有集成测试锁死（参考：ExpenseControllerIntegrationTest 7/7、showApiDownloadError +7 用例、Sanitizer 137 测试）。

### 一句话纪律

把这句话写进项目的 AGENTS.md / CLAUDE.md，并配 ArchUnit / 单测门禁：

> **消息从产生点原样流到 UI，中间层只做安全判定，不做改写、不做兜底、不做编造。**

最后一条经验关于回归：透传的退化几乎都是「静默吞消息」——不报错、不告警，只是用户再也看不到真实原因。没有测试锁死的透传链路一定会回潮（本项目四次修复 !1350 / !1458 / !1460 / !1659 都是回潮后的补课）。所以检查表最后一项不是可选项，是透传范式能否活过第二个迭代的前提。
