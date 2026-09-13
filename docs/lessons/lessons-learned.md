# 通用工程教训与复盘

> 本文件记录跨模块、可复用的核心工程教训与 SOP，按 session 追加章节。
> 仅保留高频引用的通用教训；历史事故详细时间线、一次性 bug 复盘、已被 spec 覆盖的单点修复见 [lessons-archive.md](./lessons-archive.md)。

---

## 2. 后端接口契约变更必须同步前端所有入口

### 问题背景

CO-274 中，V130 评估表重构后 `/api/tenders/{id}/bid` 被设计为「评估-审核后创建项目」，要求请求时标讯已存在 `TenderEvaluation`。但前端标讯详情页的「投标」按钮仍走快速投标流程（`participate -> bid`），该流程不会提交评估表，导致 `/bid` 返回 404 并被静默吞掉，项目未创建。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 后端 `/bid` 契约变更后，前端仍按旧流程调用 | 任何后端接口新增前置条件时，必须梳理前端所有调用方 | 变更接口契约时，在 PR 描述中列出所有前端调用点并逐一验证 |
| 前端 `catch {}` 吞掉关键错误 | 核心业务错误不应静默处理 | 对「创建项目」等关键操作，必须向用户反馈失败或降级处理 |
| 同一功能存在两条差异路径 | 列表页和详情页「投标」入口行为不一致，导致测试覆盖遗漏 | 同一业务动作尽量统一入口；无法统一时，两套路径都要覆盖 |

### 操作规范

1. 后端接口新增 `orElseThrow` / 前置校验时，必须在 PR 中标注「前端调用点影响范围」。
2. 前端对关键写操作禁止空 `catch`；至少记录日志、上报埋点或弹出错误提示。
3. 一个业务动作存在多个前端入口时，每条入口都应有对应的集成测试或 E2E。

### 相关文档

- `docs/lessons/root-cause-analysis-co-274.md` - 完整根因分析

---

## 4. 回滚 PR 前必须确认根因，避免回滚正确修复

### 问题背景

CO-280 排查中，PR !884 修改 `TenderIntegrationMapper.toDownloadUrl()` 添加 `publicBaseUrl` 配置（方向正确），但误判根因为"下载端点不支持外部 URL"。PR !886 实现代理下载后**错误回滚**了 PR !884。部署后 CRM 实测仍失败，才重新识别真正根因是**相对路径跨域**问题。最终 PR !890 重新实现 PR !884 的方向 + 保留代理下载，问题才彻底修复。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 误判根因后回滚了正确修复 | 回滚 PR 前必须确认根因，不能因为"看起来修了另一个问题"就回滚 | 回滚前用"五个为什么"追问根因，确认被回滚的修复与根因无关 |
| 只测同源场景就认为修复生效 | 同源场景下相对路径正常，掩盖了跨域问题 | 跨系统 bug 必须用真实外部系统场景验证 |
| 多个根因可能同时存在 | 修复一个不代表另一个不存在 | 排查时列出所有可能的根因，逐一验证，不能"修了一个就收工" |

### 操作规范

1. **回滚 PR 前必须确认根因**：用"五个为什么"追问，确认被回滚的修复与根因无关。如果不确定，保留修复并观察。
2. **跨系统 bug 必须用真实外部系统场景验证**：不能只测同源访问，必须模拟外部系统的调用场景。
3. **排查时列出所有可能的根因**：逐一验证，不能"修了一个就收工"。
4. **回滚操作需要显式记录理由**：commit message 或 PR 描述中必须写明"为什么回滚"、"确认了什么根因"。

### 相关文档

- `docs/lessons/root-cause-analysis-co-280.md` - CO-280 完整根因分析

---

## 17. Bug 修复前必须先验证实际行为，避免"推测式修复"

### 问题背景

CO-285 附件下载文件名显示为 "download"，一个看似简单的问题花了 3 轮 PR 才真正修复：
- PR #926：修复 Content-Disposition 头编码（无效）
- PR #929：修复 CORS 配置暴露响应头（无效）
- PR #931：修改前端下载方式为 fetch+blob（有效）

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 只看代码推测问题，不验证实际行为 | 修复前必须用浏览器开发者工具验证实际的请求/响应 | Bug 修复前先复现，用 F12 Network 查看实际响应头 |
| 第一次修复无效后继续在同一方向深入 | 修复无效时立即调整方向，而不是继续修复 | 修复无效时回到"问题是什么"重新分析 |
| 忽略浏览器行为差异 | `<a>` 标签导航和 fetch 请求的下载行为不同 | 涉及文件下载时明确下载方式并验证其行为 |
| 重复造轮子 | 项目已有 4 处类似下载函数，又新增 1 处 | 新增工具函数前先 grep 搜索项目中是否已有 |

### 操作规范

1. **Bug 修复前必须先复现**：用浏览器开发者工具（F12 -> Network）查看实际的请求和响应，而不是只看代码推测。
2. **修复无效时立即调整方向**：如果第一次修复无效，不要继续在同一个方向上深入，而是回到问题本身重新分析。
3. **明确下载方式**：涉及文件下载时，必须明确是 `<a>` 标签导航、`window.open`、还是 `fetch+blob`，并验证其行为。
4. **新增工具函数前先搜索**：使用 `grep` 搜索项目中是否已有类似实现，避免重复造轮子。

### 相关文档

- `docs/lessons/root-cause-analysis-co-285.md` - 完整根因分析

---

## 19. 简单 bug 多轮修不对：先定位"空值从哪来"，再改格式化逻辑

### 问题背景

PR #1162 修复"EVALUATED webhook 回调缺少操作人姓名（工号）"，但用户反馈修复后格式不对--只有姓名没有工号。随后又反馈只有工号没有姓名。一个看似简单的字符串格式化 bug，改了 3 轮。

**真正的根因**：`OperatorDisplayName.format()` 当 `user.getFullName()` 为空时直接返回工号，没有 fallback 到 `username` 作为姓名。API Key 对应的用户可能没有设置 `fullName` 字段。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 用户说"格式不对"，没问清是哪种不对就直接改 | 先确认具体现象：是"有姓名无工号"还是"有工号无姓名"？方向反了白改 | 收到 bug 反馈时，先确认**实际输出**和**期望输出**的具体差异 |
| 第 1 轮修了调用方而不是格式化器本身 | 修了调用方没修格式化器本身 | 格式化 bug 先看**格式化函数本身**的分支逻辑，不要先看调用方 |
| `OperatorDisplayName.format()` 有 4 个分支但只测了正常路径 | 边界分支（fullName 为空、employeeNumber 为空）缺少测试 | 格式化函数必须有**全分支测试**，特别是空值 fallback 分支 |

### 操作规范

1. **收到"格式不对"反馈时，先确认具体现象**：问用户实际输出是什么？期望输出是什么？不要凭"格式不对"三个字就推测方向。
2. **格式化 bug 先看格式化函数本身**：格式化函数是所有调用方的公共逻辑，bug 大概率在这里。
3. **格式化函数必须有全分支测试**：正常路径、fullName 为空、employeeNumber 为空、两者都空，每个分支都要有测试用例。
4. **字符串格式化 bug 的标准排查路径**：确认实际 vs 期望差异 -> 找到格式化函数 -> 逐分支检查 -> 修复 + 补全分支测试。

### 相关文档

- `backend/src/main/java/com/xiyu/bid/webhook/domain/OperatorDisplayName.java` - 格式化函数
- 本节 §17 - 同类教训："Bug 修复前必须先验证实际行为"

---

## 23. 全链路日志排查 SOP（Agent 必读）

### 三层诊断体系

```
┌─────────────────────────────────────────────────┐
│  Layer 1: Sentry 自动诊断（P0，首选）              │
│  自动聚合异常 -> 直接定位根因代码行 -> 显示触发用户   │
│  适用：NPE、SQL异常、外部服务失败等系统缺陷         │
├─────────────────────────────────────────────────┤
│  Layer 2: 结构化日志 + TraceId 手动溯源（P1）      │
│  grep traceId -> 全链路还原 -> 请求参数/Body 回放    │
│  适用：业务逻辑错误、性能问题、Sentry 未覆盖场景     │
├─────────────────────────────────────────────────┤
│  Layer 3: git log + cherry-pick 追溯（P2）        │
│  代码变更历史 -> 哪次 commit 引入的回归             │
│  适用：回归问题、merge 冲突导致的功能丢失            │
└─────────────────────────────────────────────────┘
```

### Layer 1：Sentry 自动诊断（首选，5 秒定位根因）

**Sentry 上报哪些异常**：
- 上报：NPE、SQL 异常、`ExternalServiceException`、`IllegalStateException`、`HttpMessageNotReadableException`、`OptimisticLockingFailureException`、`ConstraintViolationException` 等系统缺陷
- 不上报（`SentryConfig.NON_CRITICAL_EXCEPTIONS` 过滤）：`AccessDeniedException`（403 正常权限控制）、`AuthenticationException`（401 正常认证）、`BusinessException`（400 正常业务校验）、`ResourceNotFoundException`（404 正常查询结果）

**Agent 动作**：打开 Sentry Dashboard -> 查看 Issues -> 点击具体 Issue 直接看到异常堆栈 + 触发代码文件路径行号 + 触发用户上下文 + Release 版本（git commit hash）。

### Layer 2：结构化日志 + TraceId 手动溯源（Sentry 未覆盖时使用）

当你被要求调查 Bug 时，请按以下 4 步查找线索：

1. **抓取 X-Trace-Id 溯源**：前端异常找 `FrontendLogController` 打印的 ERROR 日志（含路由、报错栈、`X-Trace-Id`）；提取 traceId 后 grep 所有后端日志还原全链路。
2. **定位崩溃现场（GlobalExceptionHandler）**：借助 `ContentCachingRequestWrapper`，崩溃时 handler 会把 HTTP 请求头、URL、Body、Query 全部输出在 ERROR 日志中。遇到后端报错首先看这个。
3. **排除第三方依赖问题（LoggingClientHttpRequestInterceptor）**：涉及外部系统调用失败时，`RestTemplate` 会把原始请求、头部和第三方返回的原始 JSON 全部打印。
4. **禁止乱猜**：在做出推断前，**必须先用上述方法提取真实的请求体负载数据进行佐证**。没有日志证据前，不要盲目改代码。

### 相关文档

- `SentryConfig.java`：beforeSend 过滤 + 用户上下文注入 + release 自动读取
- `GlobalExceptionHandler.java`：崩溃现场请求体输出
- `LoggingClientHttpRequestInterceptor.java`：外部调用出入参日志

---

## 25. 前端禁止 `catch { /* silent */ }` 吞掉 API 错误（CO-390 root cause）

### 问题背景

CO-390 修复绑定联系人字段升级 userId 后，投标组长/专员新增账户时无法搜索人员。根因是 `AccountFormDialog.vue` 调用 `/api/admin/users`（`@PreAuthorize("hasRole('ADMIN')")`）返回 403，但前端 `catch { /* silent */ }` 静默吞掉错误，`biddingUsers` 静默为空，用户看到的是"无法搜索"而非"权限不足"，严重误导排查方向。

```javascript
// 错误模式
const loadBiddingUsers = async () => {
  try {
    const res = await httpClient.get('/api/admin/users')
  } catch { /* silent */ }  // ← 吞掉 403，用户看到"无法搜索"而非"权限不足"
}
```

### 教训

1. **`catch { /* silent */ }` 是权限问题的隐形放大器**：后端返回 403 时前端吞错，用户看到的是"功能不可用"而非"权限不足"，导致用户提错工单、排查者从错误方向入手。
2. **静默吞错违反"快速失败"原则**：错误应该尽早暴露，而不是静默处理后继续执行导致后续逻辑在错误状态下运行。
3. **`try/catch` 的 catch 块必须有明确处理**：至少记录日志、上报埋点或弹出错误提示，禁止空 catch 块。

### 操作规范

1. **禁止 `catch { /* silent */ }` 或 `catch {}` 空块**：catch 块必须有至少一项处理（`console.error` / `ElMessage.error` / 降级处理 + 明确注释说明原因）。
2. **关键业务写操作（创建/更新/删除）禁止吞错**：必须向用户反馈失败。
3. **数据加载类 catch 必须区分错误类型**：403/401 提示"权限不足"或降级 + 注释；404 提示"资源不存在"；500 提示"服务异常"；网络错误提示"网络异常"。
4. **Code Review 时必须检查 catch 块**：reviewer 看到 `catch {}` 必须质疑。

### 相关文档

- `docs/lessons/root-cause-analysis-co-390-unified-picker.md` - 完整根因分析

---

## 27. 迁移脚本之间不能互相覆盖（V1098 vs V1105 迁移漂移）

### 问题背景

CO-349 修复后遗留的"【待立项】"占位任务问题：V1098 把占位任务 TODO -> CANCELLED（清理了 62 个），但 V1105（三态收口）执行 `UPDATE tasks SET status = 'TODO' WHERE status = 'CANCELLED'` 时未排除占位任务，又改回 TODO。两个迁移脚本互相抵消，导致 62 个占位任务复活，前端看不见但 `AllTasksCompletedPolicy` 计入，`submit-bid` 报"仍有 N 个任务未完成"。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 新迁移覆盖了旧迁移的处理结果 | 迁移脚本之间必须有依赖关系分析 | 执行迁移前检查是否有其他迁移涉及相同数据 |
| 占位任务用 CANCELLED 作终态，与三态模型冲突 | 废弃状态值不应与新引入的三态冲突 | 迁移脚本使用新状态值前，确认下游迁移不会改回旧值 |
| 前端过滤 vs 后端计数不一致 | 前端不展示的数据，后端也不应计入业务校验 | 业务校验层不应计入前端主动过滤的数据 |

### 操作规范

1. **新增迁移脚本涉及状态值变更时，必须检查历史迁移是否有冲突场景**。
2. **废弃状态值迁移前，确认下游迁移是否会覆盖**：如果下游可能归一，需要在 WHERE 条件中排除，或改用直接删除。
3. **前端主动过滤的数据，后端业务校验层不应计入**。

### 相关文档

- `V1112__cleanup_legacy_pending_initiation_tasks.sql` - 根治迁移（直接删除占位任务）

---

## 28. OkHttp3 传递依赖导致 RestTemplate GET 请求全面失败

### 问题背景

dev-services.sh restart 后 backend `/actuator/health` 返回 DOWN，日志报 `IllegalArgumentException: method GET must not have a request body`。三处 sidecar 调用全部受影响。

**根因**：`com.openai:openai-java-client-okhttp:4.32.0` 传递依赖引入 okhttp3，`RestTemplateBuilder` 自动检测后用 `OkHttp3ClientHttpRequestFactory`，OkHttp3 对 GET 严格要求 body 为 null。修复方式：显式指定 `SimpleClientHttpRequestFactory`。修复一处后 stash 中又发现 `OrganizationDirectoryHttpGateway` 也中招。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 错误消息被误判为 sidecar 拒绝 | 错误消息要看完整调用栈，不能只看消息文本 | 排查时先看完整 stacktrace，确认抛异常的类属于哪一层 |
| 改用 JDK HttpClient 是 workaround | workaround 治标不治本，根因未消除，其他使用点仍会中招 | 修复后必须做 5 维度 Review，识别 workaround 并追问根因 |
| OkHttp3 通过传递依赖引入 | 传递依赖会改变框架行为，RestTemplateBuilder 自动检测不可靠 | 显式指定 `requestFactory`，不要依赖自动检测 |
| 修复一处后以为完事，stash 中才发现另一个使用点 | 同一根因可能影响多个使用点，必须全局排查 | 修复后用 `grep -rn "RestTemplateBuilder" backend/src/main` 列出所有使用点 |

### 操作规范

1. 所有 `RestTemplateBuilder` 使用点必须显式指定 `requestFactory`，不依赖自动检测。
2. 新增 HealthIndicator 必须配单元测试，覆盖 UP/DOWN/超时/5xx 至少 4 个场景。
3. 修复 bug 后做 5 维度 Review，识别 workaround 并追问根因。
4. 同一根因修复后全局排查，用 `grep` 列出所有同类使用点。

### 相关文档

- `docs/lessons/root-cause-analysis-okhttp3-get-body-resttemplate.md` - 完整根因分析

---

## 35. Spring Data JPA 派生查询方法传 null 不会变成"无过滤条件"（PR !1563）

### 问题背景

批量导出 Excel 文件内容为空，只有表头无数据行。列表查询正常有数据，仅导出空。按 §23 SOP 定位：此 bug 不触发任何异常（`WHERE status = NULL` 是合法 SQL，MySQL 不报错，HTTP 200），属 Layer 2 适用场景。

### 根因

`BrandAuthExportService.exportAll()` 调用 `repository.findByStatus(null)`，期望"传 null 查全部"。但这是 Spring Data JPA **派生查询方法**，传 null 会生成 `WHERE status = ?` 绑 null 参数，**不会**自动跳过过滤条件。MySQL 中 `status = NULL` **永远返回 false**（NULL 三值逻辑）-> 结果集为空。

### 教训

1. **Spring Data JPA 派生查询方法传 null 不会变成"无过滤条件"**：派生方法（`findByXxx`）对 null 参数生成 `WHERE xxx = ?` 绑 null，**不会**像 MyBatis 的 `<if test=` 那样自动跳过条件。MySQL 中 `= NULL` 永远返回 false。
2. **"查全部"必须用 `findAll()`，不能用 `findByXxx(null)`**。可选条件查询用 `Specification` 动态拼接，或用 `@Query("... where (:status is null or status = :status)")`。
3. **Code Review 时看到 `findByXxx(null)` 必须质疑**：这是反模式信号。
4. **对照判别法**：当出现"列表有数据，导出/报表空"时，优先检查导出查询是否走了与列表不同的查询路径。

### 相关文档

- `backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/ListManufacturerAuthAppService.java` - Specification 动态拼接的正确范例

---

## 37. 筛选语义必须与展示列对齐 + 「null 永真 fallback」是隐形 bug 放大器（PR !1642）

### 问题背景

用户在投标项目列表按投标负责人筛选"陈梦瑶"，张莉娜的项目也显示出来。排查后发现 PR1574 实际上已部署生效，"没生效"是错觉。真正的根因是 PR1574 修复后筛选真正工作，反而暴露了之前被掩盖的 OR 匹配语义设计问题。

### 根因链路（双层 bug 叠加）

**第一层（PR1574 修复前 - 筛选根本不工作，但伪装成正常）**：UserPicker 的 `value-key` 配置错误导致选中值为 `undefined`，`matchId` 函数中 `undefined == null` 为 true -> 永远返回 true -> 等于不筛选 -> 所有项目都显示。

**第二层（PR1574 修复后 - 筛选生效，暴露 OR 语义问题）**：`matchId` 用 `some()` 匹配主负责人 OR 副负责人，但展示列 `biddingLeaderName` 只显示主负责人姓名。筛"陈梦瑶"时，主=张莉娜/副=陈梦瑶的项目被命中（副匹配），但列表显示"张莉娜"，用户误以为筛错了。

### 教训

1. **「筛选值==null 时永真」是隐形的 bug 放大器**：`if (filterVal == null) return true` 是合理的"空值不过滤"设计，但当上游组件因配置错误返回 `undefined` 时，`undefined == null` 为 true -> 把"筛选不工作"伪装成"筛选正常但所有项目都显示"。设计 fallback 时要考虑上游传入 `undefined` 的场景。

2. **修复一个 bug 可能暴露另一个隐藏 bug**：PR1574 修复后筛选真正生效，反而暴露了 OR 匹配语义问题。排查时**不能因为"刚修过"就跳过验证**。

3. **筛选语义必须与展示列对齐**：筛选用「主 OR 副」匹配，但展示列只显示主负责人姓名 -> 用户看到"筛 A 命中 B"的错觉。检查清单：筛选匹配的字段范围 vs 展示列显示的字段范围是否对齐？

4. **展示用姓名、筛选用 ID 的双数据源设计需要强同步**：两表无外键约束、无强同步机制，转派时如果只改 ID 不回写姓名，就会出现"筛 A 命中但显示 B"。

### 操作规范

1. **设计筛选 fallback 时考虑 `undefined` 场景**：用 `filterVal === null || filterVal === ''` 严格判断，`undefined` 时 `console.warn` 报警。
2. **修复 bug 后必须验证「修复是否暴露新问题」**。
3. **筛选匹配范围必须与展示列对齐**：在 PR review 时用表格列出「筛选匹配字段 vs 展示字段」对照。

### 相关文档

- `docs/lessons/root-cause-analysis-bidding-leader-filter-or-semantics.md` - 完整根因分析

---

## 38. Collectors.toMap 无 merge function 三层失效 + 35 处全仓治理（PR !1640 + Spec Kit 027）

### 事故经过

测试系统 `tenderId=937` 关联 2 个 Project（managerId=585 和 7246，业务允许的二次招标场景）。`TenderQueryService.fetchManagerNames` 使用 `Collectors.toMap(Project::getTenderId, Project::getManagerId)` **无 merge function**，遇到重复 key 抛 `IllegalStateException: Duplicate key 937`。异常传播链：`toMap` -> `enrichAssignmentInfoBatch`（无降级）-> `searchTendersPaged`（无 try-catch）-> `GlobalExceptionHandler`（只 `log.warn` 一行，不打印堆栈/不上报 Sentry）-> 标讯中心整个模块不可用。

**三层失效**：
1. **数据层**: `toMap` 无 merge function，fail-fast 抛异常
2. **服务层**: enrichment 是装饰性操作（补充 manager name 显示），但失败时未降级，导致主列表功能崩溃
3. **异常层**: handler 只 `log.warn` 一行，不打印堆栈、不上报 Sentry，定位困难

### 修复方案（三层防御体系）

- **L1 数据层**: 修复全仓 35 处 `toMap` 2 参数版本，添加 `(a, b) -> a` merge function
- **L2 服务层**: 装饰性 enrichment 加 try-catch 降级（降级后 dtos 保持原样，不影响主列表返回）
- **L3 异常层**: 5xx handler `log.warn` -> `log.error`（打印堆栈）+ Payload + Sentry

### 防复发机制

1. **ArchUnit 守卫**: `ArchitectureTest.RULE 18 toMapMustHaveMergeFunction` - 扫描 2 参数 `toMap` 调用，命中即失败
2. **pre-push gate**: `scripts/check-tomap-no-merge-function.mjs`
3. **Constitution v2.0.0 Principle VII**: Defensive Collection & Graceful Degradation（NON-NEGOTIABLE）

### 教训归纳

1. **`Collectors.toMap` 无 merge function 是定时炸弹**：任何 key 非唯一约束的 toMap 调用都可能触发。新代码 MUST 用 3 参数版本。
2. **装饰性操作不得影响主功能**：enrichment 失败时 MUST 降级。判断标准：方法名含 `enrich`/`fetchXxxNames` 且返回值用于补充显示字段（非业务决策）。
3. **异常 handler 必须满足诊断标准**：5xx handler 只 `log.warn` 一行是灾难--MUST `log.error`（堆栈）+ Payload + Sentry。
4. **fail-safe 优于 fail-fast**：运行期面对边界数据应 fail-safe（返回部分数据优于整个模块崩溃）。
5. **ArchUnit 守卫是技术债治理的终极武器**。

### 关键文件

- `ArchitectureTest.java` - RULE 18 toMap 守卫
- `scripts/check-tomap-no-merge-function.mjs` - pre-push gate 脚本
- `specs/027-tomap-defensive-collection/` - Spec Kit 完整文档

---

## 39. Flyway 迁移目录混淆：db/migration/ vs db/migration-mysql/ 双轨制守卫缺失（CO-483/484 P0 事故）

### 事故经过

CO-483/484 PR !1637 在 kimi worktree 开发，建表迁移 `V123__add_bid_review_assignment.sql` **误放在 `db/migration/`**（历史目录，Flyway 9.22.3 不读取此目录）。部署后生产环境 `bid_review_assignment` 表从未被创建，`/api/projects/{id}/stage` 接口 500，P0 故障。

**根因三层**：
1. **目录双轨制**: `db/migration-mysql/` 是活跃目录，`db/migration/` 是历史目录（Flyway 不读取），名字相似容易混淆。
2. **pre-commit hook 仅在主工作区生效**: 其他 worktree 的 `.git/hooks/pre-commit` 都是 MISSING，没有机会拦截。
3. **版本号撞历史基线**: `V123` 已被 `db/migration/V123__tender_reminder_settings.sql` 占用。

### 防复发机制（三层防御纵深）

- **L1 push 时拦截**: `pre-push-gate.sh §3.7` 扫描 commit 范围内被新增的 V*.sql 是否误放在 `db/migration/`，通过 `scripts/git` 包装器在所有 worktree 生效。
- **L2 CI 时拦截**: `EntityTableMigrationCoverageTest` 扫描所有 `@Table(name = "xxx")` 实体，验证 `migration-mysql/` 中存在 `CREATE TABLE xxx` 迁移。
- **L3 手动审计**: `scripts/check-flyway-migration-dir.sh`（主工作区 pre-commit）。

### 教训归纳

1. **pre-commit hook 不是万能的**：仅在主工作区生效，其他 worktree 默认 MISSING。任何依赖 pre-commit 的守卫都必须有 pre-push 或 CI 层的备份。
2. **目录双轨制是隐形陷阱**：Flyway 静默跳过不读取的目录，没有 warning。
3. **ArchUnit 守卫应覆盖"实体-迁移"对应关系**：`@Table(name = "xxx")` 是 JPA 实体的强契约，应该有对应的 `CREATE TABLE xxx` 迁移。
4. **正则 bug 会让守卫形同虚设**：新守卫必须用真实数据验证正则。
5. **多 worktree 协作必须假设其他 worktree 没装 hook**：`scripts/git` 包装器是唯一在所有 worktree 都生效的拦截点。

### 关键文件

- `scripts/pre-push-gate.sh` - §3.7 Flyway 迁移目录守卫
- `backend/src/test/java/com/xiyu/bid/support/EntityTableMigrationCoverageTest.java` - @Table 实体迁移覆盖守卫

---

## 40. 修 bug 时删除代码必须审视隐式前后端字段契约（CO-498 修 CO-443 引入导航断层）

### 事故经过

CO-498：项目复盘阶段提交后，导航时间线上的"结项"tab 显示「待进入」且不可点击，项目负责人无法进入结项阶段提交结项申请，整个结项审核流程死锁。

**根因三层**：
1. **修 CO-443 时删除了"复盘直达 CLOSED"**：删除本身正确，但未审视 `ProjectStageController.get()` 的隐式契约。
2. **未审视 `accessibleStages` 字段的隐式依赖**：该字段计算逻辑一直依赖"复盘提交后 stage=CLOSED"，删除二次推进后 CLOSED 永远不进 `accessibleStages`。
3. **前端 `isUnlocked()` 完全信任后端字段**：后端字段断层直接导致前端 tab 锁死。

### 关键认知

**Service 行为变更 ≠ 视图层契约自动同步**。三层链条是隐式契约：

```
Service 行为（stage 是否推进）
  ↓ 隐式契约
Controller 字段计算（accessibleStages 是否含 CLOSED）
  ↓ 隐式契约
前端视图判定（isUnlocked(stage) 是否返回 true）
```

任何一层行为变更，必须审视另外两层是否依赖此行为。

### 防复发机制

**L1 排查清单（修改 service 行为时必跑）**：
- [ ] 此 service 方法的所有调用方在哪里？grep 出全部 caller
- [ ] 此 service 方法的行为变更会影响哪些 controller 返回字段？
- [ ] 这些字段在前端有哪些视图层判定依赖？
- [ ] 改完之后，受影响字段的"前后端契约测试"是否仍然通过？
- [ ] 是否有"依赖此 service 副作用"的其他代码路径？

**L2 测试守卫**：新增测试必须覆盖"前后端字段契约"的边界，不只是 service 自身行为。

**L3 顽固 bug 全链路推演（Code Review 必跑）**：修复"修 A 引入 B"类顽固 bug 时，必须推演用户全链路（GET /api -> 后端字段计算 -> 前端字段消费 -> 用户交互 -> 业务终态）。

### 教训归纳

1. **删除代码比新增代码风险更高**：新增代码的副作用通常在调用方可控范围内，删除代码则会"静默切断"所有依赖此代码的隐式契约。
2. **前后端字段是隐式契约**：后端 controller 返回的每个字段（特别是 `accessibleStages`、`currentStage`、`canXxx` 等布尔/列表字段）都是前端视图判定的依据。
3. **"看代码注释理解历史决策"是关键排查手段**：两段矛盾的注释是定位"修 A 引入 B"的关键信号。

### 相关文档

- `docs/lessons/root-cause-analysis-co-498.md` - 完整根因分析（含 8 步全链路推演）

---

## 44. 通知派发接收人必须按资源可见性过滤：广播范围 × 资源权限 × targetUrl 三者联动（spec 030 / 06131 案例）

### 问题背景

用户 06131（bid-Team 投标专员）收到大量任务审核通知，点击通知跳转报'没有权限'。这是一个影响所有 `bid-Team`/`bid-projectLeader` 角色的系统性 Bug。

### 三层根因表

| 层级 | 表象 | 真相 |
|------|------|------|
| L1（表层） | 点击通知跳转 403 | `ProjectAccessScopeService` 抛 `AccessDeniedException` |
| L2（中层） | 06131 不该收到这些项目通知 | `TaskReviewNotificationService` 用 `findEnabledByRoleProfileCodes` 全球广播给所有投标专员，未过滤接收人对项目的访问权 |
| L3（根因） | 接收人策略与资源访问权脱节 + targetUrl 硬编码 | 通知派发的'接收人范围'、'资源访问权'、'跳转 URL'三者各自独立设计，没有约束关系 |

### 关键设计教训：广播范围 × 资源权限 × targetUrl 三者必须联动

通知派发的三个维度：
1. **接收人范围**：通过 `findEnabledByRoleProfileCodes(roleCodes)` 按角色反查。如果含 `bid-Team` 等 `dataScope=self` 的受限角色，反查结果包含全球所有该角色用户。
2. **资源访问权**：通过 `ProjectAccessScopeService.getAllowedProjectIds(user)` 计算可访问集。`dataScope=self` 角色的可访问集远小于广播范围。
3. **跳转 URL**：常被硬编码为 `/project/{id}/...`。

**三者必须联动**：如果'接收人范围'含受限角色，且'targetUrl'跳转到的资源有访问权校验，**派发前必须对接收人做资源可见性过滤**。

### 操作规范（新增通知派发器时必跑）

- [ ] **接收人范围审查**：`roleCodes` 是否含 `dataScope=self` 受限角色？
- [ ] **如含 self 角色，必须做项目可见性过滤**：用 `NotificationRecipientFilter.filterRecipients(candidates, uid -> projectAccessScopeService.canAccessProject(uid, projectId))`。
- [ ] **targetUrl 审查**：跳转目标是否有访问权校验？如有，必须确保接收人能通过校验。
- [ ] **降级策略**：过滤逻辑异常时降级为原广播（通知送达优先于精准）。
- [ ] **前端兜底**：`src/api/client.js` 全局 403 拦截器对'项目详情类 403'友好化（黄色 warning + 跳 `/inbox`）。

### 设计 Review 教训（通用化）

**教训 A：改动全局拦截器/中间件时，必须在 plan 阶段做"影响面分析"**
- 正向命中验证：本次场景的所有请求路径能否被精准命中？
- 反向误伤分析：用 `grep` 搜索所有命中点，逐一判定"是否真的属于本次场景"。
- 精准标记优于全局模式匹配。

**教训 B：新增"权限判定"类方法时，必须先 grep 既有同类方法的判定源**
- 判定源对齐：新增方法的判定源（authority vs role_code vs DB 字段）必须与既有同类方法一致。
- 对齐测试：写一个"同一用户对同一资源，两个方法判定结果必须一致"的测试用例。

### 相关文件

- `backend/src/main/java/com/xiyu/bid/notification/core/NotificationRecipientFilter.java`（Pure Core 纯函数）
- `backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java`（`canAccessProject` 方法）
- `specs/030-fix-task-review-notify-403/`

---

## 45. Java 枚举与数据库 ENUM 不同步导致静默失败：alert_rules.type 事件（CO-523）

### 问题背景

四个模块（资质证书/业绩管理/品牌授权/CA信息管理）的到期提醒均未生效。代码逻辑完整、定时任务配置正确、单元测试通过。

### 根因分析

**代码先行，数据库未同步**：Java 枚举 `AlertRule.AlertType` 包含 9 个值，数据库 `alert_rules.type` 列定义为 ENUM 只有 6 个值。后 3 个枚举值在代码新增时**没有同步更新数据库 ENUM 定义**。MySQL 报错 `Data truncated for column 'type'`，异常被 `@Transactional` 回滚，告警未生成，定时任务静默失败。

**为什么没有更早发现**：单元测试使用 H2 内存数据库，H2 对 ENUM 类型的校验比 MySQL 宽松；定时任务异常被吞掉（只 log.error，不抛出）。

### 经验教训

1. **Java 枚举与数据库 ENUM 必须同步**：新增 Java 枚举值时，必须同步创建 Flyway 迁移脚本更新数据库 ENUM 定义。
2. **H2 测试不等于 MySQL 行为**：H2 对 ENUM 校验宽松，MySQL 严格；涉及 ENUM 类型的变更必须在 MySQL 环境验证。
3. **定时任务不能吞异常**：定时任务中的异常必须至少 log.error + 记录到监控（Sentry），不能只 log 后丢弃。
4. **"不发通知"型故障需要主动检测**：到期提醒类功能失灵不会产生错误日志（因为失败的是"生成告警"本身），需要独立的"告警生成计数"监控。

### 操作规范

1. **新增 Java 枚举值时检查清单**：该枚举是否映射到数据库列？如果是 ENUM 列，是否已创建 Flyway 迁移脚本？
2. **定时任务异常处理标准**：必须 log.error + 上报 Sentry，不能只 log 后丢弃。
3. **建议新增 ArchUnit 测试**：断言 Java 枚举值数量 ≤ 数据库 ENUM 值数量。

---

## 48. 止血补丁与技术债清偿必须分 PR，避免一次性还清导致长时间阻塞

### 问题背景

spec 032 的 OSS 菜单权限修复核心代码本可在 1 小时内合入，但后续被设计评审发现的 5 类技术债拖成了 3.5 小时的长时间重构，线上 bug 被重构阻塞。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 设计评审后发现债务，用户要求"本次全部修好" | 止血补丁和债务清偿应明确拆分，避免线上 bug 被重构阻塞 | P0/P1 线上问题先合最小修复；技术债单独开 PR 治理 |
| 5 类债务同时清偿，测试失败串行暴露 | 重构范围越大，返工链越长 | 单笔 PR 聚焦一个债务类型；大范围重构先跑受影响测试清单 |
| agent 分支早操 rebase 后 push 失败 | rebase 后分支历史改写 | rebase 后若此前已 push，直接使用 `--force-with-lease` |

### 操作规范

1. **线上问题必须拆为止血 PR + 债务清偿 PR**：止血 PR 只修复症状，保证最小改动当天可上线；债务清偿 PR 承载重构、测试补齐、架构治理。
2. **开始大范围重构前，先一次性跑受影响测试**。
3. **agent 分支 rebase 后若已 push 过，默认使用 `--force-with-lease`**：无需每次询问，但要在 PR 描述中注明。
4. **PR 描述中必须列出"既有无关失败"**：避免 reviewer 在无关红点上浪费时间。

---

## 53. OSS 与本地用户共用权限代码路径是 10+ 轮反复踩坑的根因（2026-07-10 根因猎手分析）

### 问题背景

系统存在两套人员权限体系（登录鉴权体系 vs 选人业务体系），设计上各自独立，但代码实现层共用 `UserDetailsServiceImpl` / `DataScopeConfigService` / `User.getRoleCode()`，导致 OSS 用户走到为本地用户写的代码路径时反复踩坑。跨 CO-361 -> CO-373 -> spec 032 -> CO-551 等 10+ 轮修复未根治。

### 历史踩坑时间线

| 时间 | Issue/Spec | 现象 | 是否根治 |
|---|---|---|---|
| 06-27 | CO-361 | 项目负责人 403 / 投标负责人只看自己 | 局部 |
| 06-28 | CO-373 | 27 处直调 `User.getRoleCode()` 引爆同类问题 | 系统性但未根治 |
| 07-04 | bid-Team 菜单泄漏 | bid-Team 看到 ai-center/operation-logs | 局部 |
| 07-08 | spec 032 / CO-551 | OSS 用户看到所有菜单 | 三层防御但根因仍在 |
| 07-09 | 标讯 403 | OSS 用户 audit-logs 接口 403 | 单点修补 |

**5 个 PR、跨度 13 天、每次"修一次好一阵子"**--典型"补交叉感染点不治根因"模式。

### 零号病人

零号病人不是某一行代码，是一个**架构决策**：决定让 OSS 同步用户与本地用户共用同一套 `UserDetailsService` / `DataScopeConfigService` / `User` 实体代码路径，靠"分支判断 + 字段标识 + 后续修补"来区分两种身份。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 声明分离 vs 代码分离 | 设计声明必须有 ArchUnit 硬约束兜底，不能只靠注释 |  |
| 共用代码路径靠分支判断区分身份 | OSS 用户走本地用户的 admin 扩散逻辑 -> 越权 | 两套身份体系必须物理隔离代码路径，不能靠 `if (isOssUser)` 守卫 |
| fallback "manager" 是症状放大器 | OSS 用户 role_id=NULL 时 fallback 返回 "manager" -> CO-361 五次反复 | `User.getRoleCode()` 的 fallback 应抛异常（fail-closed），禁止返回任意值 |
| 补交叉感染点不治根因 | 10+ 轮修复每次"修一次好一阵子" | 根因是架构决策，必须走 Spec Kit 流程门禁做根治方案 |

### 操作规范

1. **新增"按角色判断"业务分支时的检查清单**：该分支是否会同时影响 OSS 用户和本地用户？是否需要前置 `isOssUser` 守卫？是否需要新增"权限不扩散"测试用例？
2. **Spec Kit 门禁**：新增角色或权限相关改动必须走 `specs/` 下的 Spec Kit 流程，禁止单点 PR 修补。
3. **`User.getRoleCode()` fallback 禁止返回任意值**：强制调用方走 `EffectiveRoleResolver` 或 `DbRoleSnapshotResolver`。

### 修复方向（三选一，详见 specs/033）

- **方案 A**：真正的代码路径分离（推荐根治）- OSS 用户走独立的 `OssUserDetailsService` + ArchUnit 强制隔离
- **方案 B**：强约束门禁（最小代价）- 扩展 `check-rolecode-direct-calls.mjs` + ArchUnit 守卫
- **方案 C**：消除 "all" 短路 + admin 扩散（中间态）

### 相关文件

- `docs/lessons/root-cause-analysis-oss-local-permission-dual-track.md` - 完整根因分析
- `specs/033-oss-local-permission-path-separation/spec.md` - 根治 Spec Kit
- `.wiki/pages/lessons-learned/CO-361-five-rounds-no-fix.md` - CO-361 五次反复修复的完整教训

---

## 62. 错误消息不应包含不存在的功能引导；前端透传后端 msg 时文案修改只需后端做（2026-07-12 / PR !2043）

### 问题背景

用户在标讯详情页尝试关联一个已被他标占用的 CRM 商机时，系统返回 409 并显示"该 CRM 商机已被标讯 ID=xxx 关联，**请先解除原关联**"。但系统当前**并不存在**"解除关联"功能。该文案引导用户去执行一个无法完成的操作。

### 根因

文案编写者**预设了未来会实现"解除关联"功能**，但该功能从未落地。错误消息成为了一张"空头支票"。

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 错误消息引导用户执行不存在的功能 | 错误消息是用户与系统交互的"最后一公里"，引导必须可执行 | 错误消息中提到的任何操作必须对应实际存在的功能 |
| 文案编写者预设未来功能 | "先写文案，后补功能"是反向耦合 | 文案只能描述当前能力；功能上线后再补对应引导文案 |
| 前端透传后端 msg 时改文案需前后端同步 | 实际上只需后端改 | 前端透传后端 `msg` 时，文案修改只需后端做，前端无需改动 |

### 操作规范

1. **错误消息审计**：编写错误消息时，必须验证其中提到的功能/操作是否实际存在。搜索"请先"、"请前往"、"请使用"等引导词逐条验证。
2. **文案与功能上线顺序**：功能先上线，文案后补。禁止"文案先行，功能后补"。
3. **保留并发冲突等真实引导**：并发冲突的"请刷新后重试"对应浏览器原生能力，属于真实引导，不应一刀切删除。

---

## 66. 删除 bootstrap 锚点分支会导致 worktree 跟踪失败（agent/*-init 不可删）（2026-07-17）

### 问题背景

用户清理远端 stale 分支时删除了 `agent/trae-init`（bootstrap 锚点分支），导致本地 trae worktree 的 tracking 分支失效，`sync-env.sh` 的 ff-only 同步失败。

### 根因

`agent/*-init` 分支是各 worktree 的 bootstrap 锚点分支：通过 `agent-worktree-guard.sh` 做身份识别，`sync-env.sh` 对 init 分支自动执行 ff-only 同步。删除会让 worktree 失去锚点，所有自动化脚本失效。

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 误删 bootstrap 锚点分支 | `agent/*-init` 是 worktree 的身份标识和同步锚点 | 禁止删除 `agent/*-init` 远端分支 |
| stale 分支清理未区分类型 | 清理脚本未区分任务分支（可删）和 bootstrap 分支（不可删） | 清理前必须区分分支类型 |

### 操作规范

1. **`agent/*-init` 分支禁止删除**：这是 bootstrap 锚点分支，删除会导致 worktree 失效。
2. **stale 分支清理前必须区分类型**：任务分支（`agent/<agent>/<task>`）PR 合入后可删；bootstrap 分支（`agent/<agent>-init`）禁止删除。
3. **worktree 失效后恢复**：重建锚点分支 `git branch agent/<agent>-init origin/main` -> 推送远端 -> 在 worktree 内重新跟踪。

---

## 67. Excel 导出 sentinel 反模式 + 枚举 `displayName()` 陷阱（PR !2084 / 2026-07-15）

### 问题背景

业绩管理 Excel 导出存在两个用户可见 bug：

| Bug | 表现 | 根因 |
|---|---|---|
| 状态列显示英文 | `ONGOING` / `EXPIRED` 而非"履约中"/"已到期" | `ContractStatus.name()` 返回枚举名（英文），应使用 `displayName()`（中文） |
| 到期天数列显示 9223372036854780000 | Excel 显示为 `9.22337E+18` | `ContractStatusPolicy` 用 `Long.MAX_VALUE` 作为"无到期日"的 sentinel 值 |

### 根因分析

**Bug 1**: `PerformanceExcelExporter` 直接调用 `ContractStatus.name()`（Java 枚举内置方法，返回枚举常量名），混淆了"枚举常量名"和"业务展示名"两个语义层。

**Bug 2**: `Long.MAX_VALUE` sentinel 反模式--类型语义污染（`Long` 本应表示天数，`MAX_VALUE` 是魔法值）、下游处理陷阱（Excel 显示科学计数法）、计算溢出风险。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| `name()` vs `displayName()` 混淆 | Java 枚举的 `name()` 是技术标识，不应直接用于业务展示 | 枚举展示一律走 `displayName()`，禁止在导出/UI 层调用 `name()` |
| `Long.MAX_VALUE` sentinel | sentinel 值是隐式契约，下游容易误用 | 用 `Optional<Long>` 或 `null` 表达"无值"，禁止用 sentinel |
| 枚举 if-else 分支多 | 每加一个枚举常量就要改 if-else | 枚举字段统一用构造注入，禁止 switch/if-else 返回常量值 |

### 操作规范

1. **新增枚举**：必须用构造注入 `displayName` 字段，禁止 if-else / switch 返回常量值。
2. **Excel 导出枚举值**：一律调用 `displayName()`，禁止 `name()` 或 `toString()`。
3. **sentinel 值禁令**：禁止用 `Long.MAX_VALUE` / `Long.MIN_VALUE` / `-1` 等魔法值表达"无值"语义，改用 `null` 或 `Optional`。
4. **Excel 单元格 null 处理**：导出层必须显式处理 `null`，写入空字符串或留空。

---

## 72. 分支基线过期导致 PR diff 静默回退/删除他人文件（PR !2138 / !2141 / 2026-07-19）

### 问题背景

同一 agent 连续两个 PR 的 diff 中混入与本任务无关的变更：

- **PR !2138**：9 个 `.wiki/pages/*.md` 的 `health_checked` 日期被静默回退。
- **PR !2141**：diff 显示整文件删除他人已合入的部署报告（275 行）。

### 根因

1. **分支从旧 main 切出后未 rebase**：Gitee PR 展示的是 `base...head` 双边 diff，基线之后 main 上新增/修改的文件在 diff 中表现为"删除/回退"。
2. **Agent 提交了 stale 工作区文件**：旧版本文件被一并 commit。
3. **合并前无人核对完整文件清单**。

### 防御规范

| 问题 | 教训 | 规范 |
|---|---|---|
| 分支基线过期 | 双边 diff 会把 main 的新增文件显示成"被删除" | **推送/合并前必做** `git fetch origin && git rebase origin/main`，并核对 `git diff origin/main --stat` 只含本任务文件 |
| stale 工作区文件被提交 | 旧版本文件 commit 进分支后，merge 也会回退 main | commit 前 `git status` + 抽查 diff，发现无关文件不 stage |
| Review 漏看 | 两个 PR 都是人工审查才发现 | PR 审查第一步必看完整 `--stat`，出现无关文件直接打回 |

---

## 73. Review PR 必须看 commit vs parent 的实际 diff，Gitee 显示的 PR diff 会因 base 过期产生"虚假 diff"（PR !2146 / 2026-07-20）

### 问题背景

审查 PR !2146 时，Gitee API 返回的 diff 中除了标题声明的 `List.vue` 外，还包含 `agent-start-task.sh` 改动。据此发布了 CRITICAL review，判断为"夹带改动 + 违反底线"。本地 git 验证后发现原始判断完全错误--commit vs parent 的 diff **只有 List.vue 一个文件**。

### 根因

Gitee PR diff 用 `merge-base` 计算，把"PR 没跟上 main 的修复"显示成"PR 撤销了 main 的修复"--这是 PR base 过期问题，不是夹带改动。

### 正确的识别方法

判断 PR 是否夹带改动，应看 **commit vs parent** 的 diff，而不是 Gitee 显示的 PR diff：

```bash
# 看 commit vs parent 的实际改动（作者真正做的改动）
git diff ${PR_HEAD_SHA}~1..${PR_HEAD_SHA} --stat

# 看 PR 分支 vs 最新 main 的 diff（Gitee 显示的，可能含虚假 diff）
git diff origin/main..${PR_HEAD_SHA} --stat

# 两者对比：如果前者干净、后者有无关文件，说明是 base 过期；如果前者也有无关文件，才是夹带改动
```

### 正确的修复方法

修复 PR base 过期应优先 `git rebase`，不要手动改文件：

```bash
git rebase origin/main   # rebase 会自动让 PR 分支与最新 main 对齐
git push --force-with-lease origin <branch>
```

### 防御规范

| 问题 | 教训 | 规范 |
|---|---|---|
| Gitee PR diff 显示无关文件改动 | merge-base 计算会把"PR 没跟上 main"显示成"PR 撤销了 main" | **review 时必须看 `git diff <commit>~1 <commit> --stat`** |
| 误判为夹带改动 | 没有验证 commit vs parent 就下结论 | 判断夹带改动前必须本地 fetch PR 分支并跑 `git diff <commit>~1 <commit>` |
| 修复方法错误 | 用 `git checkout + amend` 把 main 修复塞进 PR commit | 修复 PR base 过期应优先 `git rebase origin/main` |

---

## 78. PR !2178 基于错误根因分析的修复方案必败--投标系统管理员 403 bug 的真正 3 条故障链（2026-07-21）

### 事故背景

覃超颖（OSS username=09118，角色=投标系统管理员 bid-SystemAdmin）访问标讯详情页报 403。PR !2178 基于错误根因分析，修复方案无效，被驳回。

### 关键认知修正

**OSS 端没有属于我们投标系统的 admin 角色**--admin 是我们系统独有的本地超级管理员，与 OSS 无关。OSS 是多系统共用的角色管理平台，返回的 `sysRoleList` 中混合了多系统角色。属于我们投标系统的只有 7 个角色码（`/bidAdmin`、`bid-TeamLeader`、`bid-SystemAdmin`、`bid-Team`、`bid-projectLeader`、`bid-administration`、`bid-otherDept`）。

### 真正的根因

1. **`RoleProfileCatalog` 未区分本地角色与 OSS 角色**（核心根因）：`canonicalCode("admin")` 返回 "admin"，导致 OSS 返回的其他系统 admin 被错误识别为我们系统的 admin。
2. **TenderController @PreAuthorize 列表缺 BID_SYSTEMADMIN**：所有 `hasAnyRole` 列表不含 `BID_SYSTEMADMIN`，导致 bid-SystemAdmin 角色用户在 Controller 入口直接被拒绝。
3. **UserDetailsServiceImpl 不走 EffectiveRoleResolver**：直接读 OSS 缓存颁发 `ROLE_ADMIN`，绕过 fail-closed 拦截。
4. **hasAdminAccess 不检查 user.isOssUser()**：OSS 缓存 admin 的用户绕过 dataScope 检查越权访问。

### PR !2178 的错误

PR !2178 假设根因是 "DataScopeConfigService 读 DB roleProfile"，实际根因是 "RoleProfileCatalog 未区分本地角色与 OSS 角色"。修复前后 `dataScope` 都是 `self`，403 未消灭。修复方案未覆盖所有故障链。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 基于错误根因分析的修复方案必败 | PR !2178 假设根因是 DataScopeConfigService，实际根因是 RoleProfileCatalog | 修复前必须画完整调用链，定位真正的拦截点；不能凭日志猜测 |
| OSS 角色识别未排除本地独有角色 | `canonicalCode("admin")` 返回 "admin" | OSS 角色解析路径必须排除本地独有角色（admin） |
| OSS 是多系统共用平台被忽视 | sysRoleList 混合多系统角色 | OSS 角色解析必须过滤出属于本系统的 7 个 bid-* 角色码 |
| 修复方案未覆盖所有故障链 | 只覆盖故障链 3（且无效） | 修复前必须推演所有可能的故障链，每条给出代码证据 |
| `@PreAuthorize` 列表与 `RoleProfileCatalog` 不同步 | hasAnyRole 列表不含 BID_SYSTEMADMIN | 新增 RoleProfile 时必须同步检查所有 `@PreAuthorize hasAnyRole` 列表 |
| `hasAdminAccess` 短路不检查 isOssUser | OSS 缓存 admin 用户越权 | `hasAdminAccess` 必须增加 `!user.isOssUser()` 条件 |

### 操作规范

1. **PR 修复 bug 前必须先画完整调用链**：从 HTTP 入口 -> Filter -> Controller -> Service -> Guard -> Repository，每一步标注可能的拦截点。
2. **必须推演所有故障链**：覆盖 OSS 配置正确/错误、tender 关联/未关联 project、admin/非 admin 角色等多场景。
3. **必须补端到端测试**：不能只测纯核心，要覆盖完整业务路径。
4. **`@PreAuthorize` 列表必须与 `RoleProfileCatalog` 同步**。
5. **`hasAdminAccess` 必须检查 isOssUser**：OSS 用户永远不走 admin 短路。
6. **OSS 角色解析必须排除本地独有角色**：OSS 返回的 sysRoleList 中如果包含 admin，那是其他系统的 admin，应该跳过。

### 相关文档

- `docs/reviews/pr-2178-production-risk-review-2026-07-21.md` - PR !2178 完整审计报告
- spec 033 `specs/033-oss-local-permission-path-separation/spec.md` - 根治方案

---

## 81. Agent Wiki 维护纪律建立：从"有了不维护"到"4 触发器门禁"（2026-07-23）

### 事故背景

.wiki/ 体系架构完整（60+ 页面），但维护停在 2026-06-20。CO-361 反复修复 5 轮、OSS 角色问题 10+ 轮--Wiki 一条都没记。根因不是"没有架构"而是"纪律未建立"--触发器没钉进门禁，靠 Agent 自律等于没纪律。

### 关键教训与规范

| 问题 | 教训 | 规范 |
|------|------|------|
| Wiki 有了但不维护 | 根因不是架构问题，是纪律问题 | 建立 4 个硬触发点：任务收尾 / PR 创建 / 复杂查询回填 / pre-push |
| pre-push 拦截但无过渡期 | 直接 hard fail 会让存量违规阻塞所有 PR | 2 周过渡期 warning 模式，2026-08-06 转 error |
| 历史档案 updated >30 天误报 | SOW/合同内容不会变 | `archive: true` 字段豁免，仍需 health_checked |
| 机械批量改日期 ≠ 维护 | 应付门禁等于没维护 | 诚实声明未深度 review 的文件，列出高优先级清单 |

### 核心纪律

1. **4 触发器必须钉进门禁**：agent-finish-task.sh Wiki Checkpoint / pr-create.sh body 勾选项 / CLAUDE.md 执行原则 / pre-push-gate.sh §16
2. **2 周过渡期**：warning 模式让存量违规有时间清理，2026-08-06 转 error
3. **历史档案豁免**：`archive: true` 字段标记，仍需 health_checked
4. **诚实声明**：批量回填时必须诚实声明未深度 review 的文件

### 相关文档

- [.wiki/WIKI.md](../../.wiki/WIKI.md) - Agent Wiki 行为宪法
- `scripts/wiki-check.mjs` - wiki 健康检查脚本

---

## 82. agent-locks 门禁检查分支全量 diff 而非 push 增量--每次 push 前锁必须在位（2026-07-23）

### 事故背景

PR !2189 第三个 commit（javadoc 修复）push 时，pre-push gate 的 agent-locks 检查挂了，报"high-risk path changed without active lock"，指向 `RoleProfileCatalog.java`（前序 commit 改的）。第三个 commit 只改了测试文件 javadoc。

### 根因

`scripts/check-agent-locks.mjs --base origin/main` 检查的是**当前分支相对 origin/main 的全部累积差异**，不是本次 push 的增量。分支上前序 commit 碰过的 hot-path 文件，每次 push 前都需要 active lock 在位。锁文件 `.agent-locks/<task-slug>.yml` 是本地工作区状态（被 `.gitignore` 排除），session 切换或工作区清理后可能丢失。

### 教训

1. **agent-locks 检查的是分支全量 diff**：不是本次 push 增量。分支前序 commit 碰过的 hot-path，每次 push 都要锁在位。
2. **锁文件是本地状态，会丢**：`.agent-locks/*.yml` 被 gitignore，session 切换/工作区清理后可能丢失，push 前如果门禁挂了先查锁。
3. **不要用 `--no-verify` 绕过**：agent-locks 是协调机制，绕过会让其他 agent 撞同一 hot-path。

### 相关文档

- [AGENTS.md](../../AGENTS.md) §5.2 文件锁（hot-paths 前置预订）

---

## 85. `@Auditable(projectScoped=true)` 方法禁止 void 返回值--AuditableAspect 无法提取 projectId 导致项目动态丢失记录（2026-07-29）

### 事故背景

PR !2212 修复了 `audit_logs.project_id` 污染问题，但遗漏了一类边界场景：**5 个 `@Auditable(projectScoped=true)` 方法是 void 返回值**，既无返回值可反射，入参也无 `getProjectId()` 方法，导致 `project_id=NULL`，项目动态丢失 5 类操作记录（提交标书审核、审核通过、审核驳回、审核通过项目立项、驳回项目立项）。

### 根因分析

1. **修复 bug 时未全量审视所有同类方法**：修复了 Long 入参误识别问题，但没有审视"void 返回值方法"这一类边界场景。
2. **`projectScoped=true` 的语义未被强制约束**：标注意味着 projectId 应该被记录，但方法是 void，没有返回值可供提取。
3. **测试覆盖遗漏**：只覆盖了"有返回值"的场景，没覆盖"void 返回值"的场景。

### 修复方案

改为返回 View DTO，让切面从返回值反射提取 projectId。DTO 中仅供切面使用的字段用 `@JsonIgnore` 标注，不参与 API 序列化，但 Lombok `@Data` 自动生成的 getter 仍可被反射调用。

### 经验教训

1. **`@Auditable(projectScoped=true)` 方法禁止 void 返回值**：必须返回包含 `getProjectId()` 方法的 DTO。
2. **修复 bug 时必须全量审视同类方法**：不能只修复当前发现的 bug 路径，要搜索所有使用相同注解/模式的方法。
3. **`@JsonIgnore` 字段用于切面反射提取**：DTO 需要携带仅供切面使用的字段时，用 `@JsonIgnore` 标注。

### 操作规范

1. 新增或修改 `@Auditable(projectScoped=true)` 方法时，必须确保返回类型包含 `getProjectId()` 方法。
2. 如果方法原本是 void，必须改为返回包含 projectId 的 View DTO。
3. 修复 AuditableAspect 相关 bug 时，必须搜索所有 `@Auditable(projectScoped=true)` 方法，全量审视。
4. 新增切面测试时，必须覆盖 void 返回值、null 返回值等边界场景。

---

## 96. E2E 测试失败三类根因模式（测试代码问题 vs 产品代码缺陷）（T034 / CO-601 / 2026-08-01）

**背景**：T034 E2E 测试 9 个全部失败，但 CO-601 产品代码经手动 API 验证完全正常。E2E 失败不等于产品代码有 bug，必须区分测试代码问题和产品代码缺陷。

**三类根因模式（均为测试代码/环境问题）**：

1. **测试数据污染（最常见）**：测试运行前未重置表单定义，累积残留字段导致后续 PUT 校验报 "字段 key 重复"。检查信号：连续两次运行同一测试，第二次开始报 "key 重复"。

2. **角色权限不匹配**：测试用 `bid-Team` 角色创建项目，但 `POST /api/projects` 要求 `ADMIN/MANAGER`，被 `Access Denied`。检查信号：API 返回 403，但手动用 admin 调用同接口 200。

3. **后端 OOM 崩溃（环境问题）**：E2E 大量请求触发后端内存增长，`exit code: 137`（SIGKILL），后续测试全部 `ECONNREFUSED`。检查信号：后端进程消失；日志末尾有 `exit code: 137`。

**判别流程**：E2E 失败时，先用 admin 手动跑通同一 API 链路（curl 即可），若手动通过则属测试代码问题，不阻塞产品代码合入。手动 API 验证证据：创建->落库->回显一致。

## 97. 业绩附件导出空图：application-prod.yml 配置缺失导致路径漂移（XIYU-1R / 2026-08-03）

**背景**：生产环境导出业绩 ZIP 时，图片格式附件（jpg/png）全部导出空图，docx/pdf 正常。Sentry XIYU-1R 报 19 次 `附件文件不存在` 异常。

**根因**：`application-prod.yml` 中缺少 `performance.attachment.root` 和 `app.upload.performance-dir` 的显式配置。代码 `@Value` 默认值为相对路径（`data/performance-attachments`），systemd WorkingDirectory 拼出 `/opt/xiyu-bid/shared/backend/data/performance-attachments/`，但批量导入的文件实际存放在 `/data/attachments/performance/<perfId>/`，路径不匹配。

**为什么图片全失败而 docx 成功**：
- 批量导入（3865 条，100% IMAGE）存的是相对路径 `/<perfId>/PF_*.jpg` → 读取时拼错路径 → 全部失败
- 页面上传（21 条，含 4 DOCX + 2 PDF + 15 IMAGE）存的是绝对路径 `/opt/xiyu-bid/...` → 直接命中 → 全部成功
- docx 成功不是因为格式特殊，而是刚好全是页面上传的

**5 Whys**：
1. 图片导出空图 → 批量导入的 file_url 是相对路径，读取时拼出的路径不存在
2. 路径不存在 → `performance.attachment.root` 未显式配置，回退到代码默认值 `data/performance-attachments`（相对 WD）
3. 为什么没配 → application-prod.yml 中 brand-auth-dir / warehouse.attachment.root 都显式配了，唯独 performance 两项遗漏
4. 为什么遗漏没人发现 → 批量导入（7/10）后到首次导出（>3 周后）期间无人触发下载/导出
5. 工程根因 → 违反「生产配置必须显式声明，不依赖代码默认值」纪律，缺少启动时目录存在性检查

**修复**：application-prod.yml 补齐显式配置（PR !2248），生产 backend.env 热修复 + 重启验证通过。

**教训**：
1. **生产 yml 中所有 `@Value` 带路径默认值的字段必须显式声明**，不能依赖代码默认值。brand-auth / warehouse 做到了，performance 漏了。
2. **批量导入后必须做闭环验证**：导入 → 重启 → 导出 ZIP → 校验图片非空。
3. **路径不对称是隐蔽 bug 源**：写路径（导入时）和读路径（导出时）如果用不同的配置项，配置漂移后会导致"写入成功但读取失败"的静默故障。
4. Sentry 告警（XIYU-1R 19 次）应设置升级阈值，≥3 次同类异常自动通知值班人。

---

## 101. Surefire 静默跳过不存在的测试类导致"测试通过"假象 + PathUtils bug（CO-602 / 2026-08-04）

**背景**：PR #2250 设计弯路修复阶段，运行 `mvn -o test -Dtest='PathUtilsTest,StringUtilsTest,ExportTaskResponseTest,BundleExportRequestTest'` 报 BUILD SUCCESS，4 个测试类"全部通过"。实际收尾时发现这 4 个测试文件根本不存在 — surefire 静默跳过了不存在的测试类。

**根因（surefire 静默跳过）**：
1. Maven Surefire 插件默认 `failIfNoTests=false`，`-Dtest=XXX` 指定的测试类不存在时不报错，只在输出中显示 `Tests run: 0`
2. 当同时指定多个测试类（部分存在、部分不存在），输出中只显示存在的测试类结果，不存在的被静默忽略
3. 本次场景：命令指定 9 个测试类，4 个不存在（PathUtilsTest/StringUtilsTest/ExportTaskResponseTest/BundleExportRequestTest），5 个存在且通过 → 看到 "BUILD SUCCESS" 误以为全部通过

**根因（PathUtils bug）**：
```java
// BUG：绝对路径未 normalize
public static Path resolveAbsolute(String path) {
    Path p = Paths.get(path);
    if (!p.isAbsolute()) {
        p = Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
    }
    return p; // ← 绝对路径直接返回，未 normalize
}
```
`/data/exports/../exports/./file.docx` 应归一化为 `/data/exports/file.docx`，但原实现跳过了 normalize。

**修复**：
1. 创建 4 个缺失的测试文件（共 26 个测试用例）
2. PathUtils.resolveAbsolute 修复：将 `normalize()` 提到 if 块外，无论绝对/相对路径都 normalize
3. 收尾流程增加检查：`Glob **/XxxTest.java` 确认测试文件存在

**教训**：
1. **Maven Surefire 默认不报错不存在的测试类** — 运行 `-Dtest=` 后必须检查输出中 `Tests run:` 数量是否匹配预期，或加 `-Dsurefire.failIfNoSpecifiedTests=true`
2. **新增工具类必须立即创建对应测试** — 不能"先跑通再补测试"，因为 surefire 静默跳过会造成假象
3. **normalize() 应在路径归一化函数的所有分支生效** — 不能只在相对路径分支做 normalize，绝对路径同样需要
4. **收尾流程的 Glob 检查是最后防线** — commit 前用 `Glob **/XxxTest.java` 确认测试文件实际存在

---

## 102. 已修复的陷阱在模块复制时复发——NotificationCreatedEvent(null id) 通知死路（CO-602 / PR !2250 / 2026-08-04）

**背景**：业绩合订本导出（performance 模块）对标 warehouse 导出模式复制实现。设计评审发现其 `PerformanceBundleExportNotificationPublisher.publish()` 使用 `eventPublisher.publishEvent(new NotificationCreatedEvent(null, ...))`，而全仓库唯一监听器 `NotificationDeliveryTaskListener` 对 `notificationId == null` 直接 return——完成通知从未发出，且日志打印"通知已发布"谎称成功。

**根因**：warehouse 侧早已踩过同一坑并改为直推 `WeComPushService#pushForRecipient`（根因记录在其类注释中），但 performance 模块复制模式时只复制了"代码结构"，没有复制"已修复的陷阱"。类注释里的教训不进入复制者的视野。

**修复**：`PerformanceBundleExportNotificationPublisher` 改为注入 `WeComPushService` 直推，与 warehouse 两个 publisher 同构（commit dd78bd8f2）。

**教训**：
1. **复制模块模式时，陷阱会随模式一起复制** — 对标既有实现开发新模块时，必须先读目标模块类注释/教训库中记录的坑，而不是只抄当前代码形态。
2. **"事件已发布"不等于"事件被消费"** — publishEvent 是 fire-and-forget，没有监听器消费的断言就是假成功。通知类功能验收必须看到投递侧的真实日志/记录。
3. **修复应辐射所有同构实现** — 一个模式有 N 份拷贝时，任何一份上发现的 bug 都要检查其余 N-1 份（本次反向：旧模块修了，新模块又引入）。

---

## 104. 部门树节点指数级膨胀——递归去重 Set 作用域错误导致 812→22795 节点（PR !2265 / 2026-08-04）

**背景**：组织管理页面 `https://winbid-test.ehsy.com/settings/organization` 打开极慢（10s+ 卡死），800+ 部门数据量下浏览器 JS 堆 768MB+、DOM 元素 18万+。

**根因**：
1. `organization_departments` 表存在重复 `department_code` 记录（OSS 同步历史遗留）。旧版 `buildSubTree` 每层递归**新建独立 visited Set**（`const nextVisited = new Set(visited)`），而非共享同一全局 Set。同一个 `departmentCode` 在不同父分支下可以**重复挂载 N 次**，每次重复又递归构建其子树——典型的指数级膨胀：812 条记录 → 22795 个树节点。
2. `el-tree default-expand-all` 导致 22795 个节点一次性全部展开渲染，产生 183104 个 DOM 元素和 768MB JS 堆占用。

**修复**：
1. `buildSubTree` 改为 `deptTree` computed 内创建**单个共享 `visited = new Set()`**，函数直接 mutate 该 Set，每个 `departmentCode` 在整棵树中只出现一次。
2. 移除 `default-expand-all`，新增 `defaultExpandedKeys` computed，默认只展开 `rootorg` 根节点。

**教训**：
1. **递归去重的 Set 作用域必须在最外层创建** — 每层递归新建 Set 等于没有去重，因为不同分支各自维护独立 Set 无法感知彼此已访问的节点。正确做法是外层创建单个 Set 传入递归函数共享。
2. **树组件 `default-expand-all` 在大数据量下是性能杀手** — 800+ 节点的树必须用 `default-expanded-keys` 只展开根节点或第一层，用户按需点击展开。
3. **DB 存在脏数据（重复 code）时，前端必须做防御性去重** — 不能假设 `department_code` 唯一，全局 visited 是兜底防线。

---

## 103. ImageIO 编码 JPEG 前必须转 RGB——ARGB 图抛 Bogus input colorspace 且被异常吞没放大（CO-602 / PR !2250 / 2026-08-04）

**背景**：业绩合订本 Word 导出中，含 alpha 通道的 PNG 附件（`ImageIO.read` 产出 `TYPE_INT_ARGB`/`TYPE_4BYTE_ABGR`）在合订本中静默丢失，文件本身完好。

**根因**：`ImageWriter.write` 对 ARGB/ABGR 图像做 JPEG 编码抛 `javax.imageio.IIOException: Bogus input colorspace`；异常被上层的 `catch (IOException)` 吞掉，降级为"（图片读取失败）"占位文本——异常分类错误（编码问题被当成读取问题），日志只有一条不指名的泛化 warn。

**修复**：编码前非 `TYPE_INT_RGB` 图像先转为白底 RGB（新建 `TYPE_INT_RGB` 图、`fillRect` 白底、`drawImage` 原图），并补 ARGB→JPEG 防回归测试（`AbstractWordBundleBuilderTest.insertImage_argbImage_shouldEncodeAsJpegSuccessfully`）。

**教训**：
1. **JPEG 无 alpha 通道，ImageIO 不做隐式转换** — 任何 `BufferedImage` → JPEG 的路径都要先确认 `getType() == TYPE_INT_RGB`，否则显式转换。
2. **宽泛 catch + 泛化降级文案会掩盖真实故障分类** — "读取失败"的兜底文案把编码 bug 伪装成数据问题，排查方向完全被带偏。catch 处的降级文案应按异常发生阶段区分。
3. **图片管线的测试必须用真实格式变体** — 只用 RGB 测试图永远发现不了 ARGB 路径问题；测试素材应覆盖 alpha/灰度/CMYK 等真实输入变体。

---

## 104. 设计评审三类"防线失效"模式——死代码钉死、防线滞后、绕过路径（CO-602 / PR !2250 / 2026-08-04）

**背景**：PR !2250 系统性设计评审（三路线并行审查）在功能正确的代码中发现三类反复出现的防线失效模式，均有实例佐证：

**模式一：死代码 + 钉死它的测试**。`ExportTaskResponse.from()`、`BundleExportRequest.isIdMode()/safeCriteria()/safeAttachmentTypes()` 为通过前一轮评审而新建，但 Controller 未接线（仍用手写 `toTaskMap`），同时配了 167 行测试让死代码看起来"有主人"。

**模式二：防线滞后于伤害**。`maxExportRecords=2000` 是 OOM 防线，但实现在 `findAll` 全量加载 + DTO 映射**之后**才判 `records.size() > maxRecords`——超限场景下查询和映射开销已全部发生，防线形同虚设。修复：先 `count(criteria)` 判定再加载。

**模式三：绕过路径无防线**。filter 模式有上限校验，ids 模式（前端勾选导出）完全没有，`ids` 字段也无 `@Size` 约束——勾选 5000 条即可绕过防 OOM 设计。

**教训**：
1. **为评审而写的代码必须检查接线** — 新建 DTO/工具方法后，`grep` 调用方数量是零成本检查；零调用方的"修复"是给评审看的，不是给系统的。
2. **资源上限防线必须先于资源消耗** — 判上限用 `count`/预检，不要用加载后的 `size()`；所有入口路径（filter/ids/后续新增路径）共享同一防线。
3. **多入口功能要逐入口核对约束** — 有一个入口做了校验不代表所有入口都有；评审时把入口列表画出来逐个打勾。

---

## 105. rebase 冲突解决取单侧前必须核对 diff 规模——"1 insertion, 127 deletions" 是危险信号（2026-08-04）

**背景**：推送 PR !2250 时 pre-push 门禁自动 rebase 到最新 origin/main，`docs/lessons/lessons-learned.md` 连续两个冲突。第二个冲突的 HEAD 侧包含 main 新增的 #98/#99/#100 三个条目 + 本分支条目，incoming 侧只有一行章节标题。解决时取 incoming 侧整段替换，导致 main 侧 127 行（三个完整教训条目）被删除，rebase 继续并显示 `[detached HEAD] ... 1 insertion(+), 127 deletions(-)`。

**根因**：conflict 标记内 HEAD 侧的内容不全是"本分支新增"，可能包含 rebase 目标（main）上比旧 base 更新的内容——这是 rebase 冲突与 merge 冲突语义的关键差异（rebase 中 HEAD=已重放的新 base + 已应用提交，incoming=正在重放的本分支提交）。把 HEAD 侧当作"旧内容"整体丢弃，就会删掉 main 的新增。

**修复**：从 `git show c8cec7a16:docs/lessons/lessons-learned.md` 提取被删条目插回，验证 `diff <(git show <base>:<file>) <(git show HEAD:<file>)` 对本分支未触碰的内容零删除，再 `commit --fixup` + `rebase --autosquash` 归并。

**教训**：
1. **rebase 冲突中 HEAD 侧 ≠ 旧版本** — HEAD 是"新 base + 已重放提交"，含有 main 的最新内容；取 incoming 侧前必须确认 HEAD 侧没有需要保留的第三方内容。
2. **提交统计是免费校验** — 文档类 rebase 提交出现大量 deletions（如 127 行）且与你的预期改动不符时，停下来 diff，不要继续。
3. **解决文档冲突后的终态校验** — `diff` 目标文件与 rebase base 的版本，本分支未改的区域必须零差异；只能新增、不能误删。

---

## 106. `@Auditable` action 命名必须对齐 AuditActionPolicy 白名单——不命中即静默丢弃，注解形同虚设（CO-602 / PR !2256 踩坑 + PR !2258 修复 / 2026-08-04）

**背景**：PR !2256 为业绩合订本导出四个端点补 `@Auditable` 审计注解，action 命名为 `PERFORMANCE_BUNDLE_EXPORT_TRIGGER/LIST/STATUS/DOWNLOAD`。合并后真实导出 + 下载，查 `audit_logs` 表**一条记录都没有**——注解全部形同虚设。

**根因**：`AuditableAspect` 写日志前过 `AuditActionPolicy.shouldRecord()` 白名单（`backend/src/main/java/com/xiyu/bid/aspect/AuditableAspect.java:75`）：查询类前缀（READ/QUERY/VIEW/SEARCH/LIST/GET）直接丢弃；其余 action 必须 equals/前缀/后缀命中 KEY_ACTIONS（CREATE/UPDATE/DELETE/SUBMIT 等约 30 个词）。四个新 action 名一个词都不命中 → 全部静默丢弃。而 `AuditActionPolicy.java:51-54` 的注释本就记录过 CO-324 踩过完全相同的坑（`PROJECT_CLOSURE_APPROVED` 等命名不命中被丢弃），本次是第二次复发。

**修复**（PR !2258）：`AuditActionPolicy` KEY_ACTIONS 增加 `DOWNLOAD`（敏感数据批量下载需留痕）；四个注解对齐全项目 219 处惯例（短动词 + entityType）：trigger=`CREATE`、list/status=`READ`（查询类按设计不落审计，注解仅作标记）、download=`DOWNLOAD`，均补 `entityType="PerformanceExportTask"`；`AuditActionPolicyTest` 补 DOWNLOAD 三种形式用例。修复后真实验证：任务 3 导出 + 下载，`audit_logs` 落库 CREATE（id 1069）、DOWNLOAD（id 1070）两条，status 轮询无 READ 记录（符合设计）。

**教训**：
1. **加 `@Auditable` 前先看 AuditActionPolicy 白名单** — action 命名不是自由文本，不命中 KEY_ACTIONS 的注解是死注解；零成本检查：心算 `shouldRecord("你的action")` 或直接跑 `AuditActionPolicyTest` 加一个断言。
2. **审计类修复的验收标准是 audit_logs 落库记录** — 注解加上 ≠ 审计生效；必须真实触发操作后查表确认，本次正是"合并后查表"这一步揭穿了假修复。
3. **记录在代码注释里的坑挡不住复发** — CO-324 的教训就写在 `AuditActionPolicy` 类注释里，评审者和作者都没看到。评审涉及审计/通知等"间接生效"机制时，必须把消费端校验逻辑（白名单/监听器）列入检查清单，而不是只看生产端代码形态。

---

## 107. tender.department 历史快照覆盖实时反查导致调岗后项目页部门显示错误（PR !2257 / 2026-08-04）

**背景**：生产环境工号 06442（刘向博）在三个项目页面显示三个不同的部门（能源电力四组 / 河南战区 / 客户开发部），但组织架构树显示当前部门是"豫皖项目组"。工号 10323（周子靖）在 `/project/29` 显示"客户开发部"，但组织架构显示"央企BD部"。

**根因**：`ProjectQueryService.java:276-284` 的反查逻辑有 `isBlank` 前置条件：
```java
// BUG：只有 leaderDepartment 为空才反查，导致历史快照覆盖实时数据
if (StringUtils.isBlank(dto.getLeaderDepartment()) && dto.getManagerId() != null) {
    String dept = managerDepartmentMap.get(dto.getManagerId());
    if (!StringUtils.isBlank(dept)) {
        dto.setLeaderDepartment(dept);
    }
}
```

而 `ProjectListEnrichmentSupport.populateFromTender` L82-84 在 `leaderDepartment` 为空时用 `tender.department`（创建标讯时的历史快照）兜底填充：
```java
if (isBlank(dto.getLeaderDepartment()) && !isBlank(t.getDepartment())) {
    dto.setLeaderDepartment(t.getDepartment());
}
```

数据流：`pid.leaderDepartment=""` → `tender.department="能源电力四组"` 覆盖 → `isBlank("能源电力四组")=false` → 跳过实时反查 → 项目页显示历史快照部门。

**5 Whys**：
1. 项目页部门错误 → 显示的是 `tender.department` 历史快照，不是当前部门
2. 为什么显示快照 → 反查有 `isBlank` 前置条件，快照非空就跳过实时反查
3. 为什么有 `isBlank` 条件 → 原设计意图是"已有值就不覆盖"，但忽略了 `tender.department` 兜底填充会把空值变成历史快照值
4. 为什么快照兜底在前 → `populateFromTender` 在 `enrichWithManagerDepartment` 之前执行，填充顺序导致反查被短路
5. 工程根因 → **快照数据与实时反查的优先级倒置**：调岗后实时数据才是真值，历史快照应作为兜底而非覆盖

**修复**（PR !2257）：去掉 `isBlank` 前置条件，让实时反查总覆盖历史快照：
```java
// FIX：只要有 managerId 就反查，实时数据覆盖历史快照
if (dto.getManagerId() != null) {
    String dept = managerDepartmentMap.get(dto.getManagerId());
    if (!StringUtils.isBlank(dept)) {
        dto.setLeaderDepartment(dept);
    }
}
```

**教训**：
1. **快照字段（tender.department / project_leader_name 等）在调岗/转派场景下是过期数据** — 查询时必须优先用 ID 实时反查当前值，快照只能作为反查失败的兜底，不能作为"已有值就不覆盖"的依据。
2. **`isBlank` 前置条件是隐蔽的优先级倒置** — 当上游有兜底填充逻辑时，"已有值就不覆盖"实际上等于"快照覆盖实时数据"。改这类反查逻辑时必须问：这个"已有值"是用户填的、还是上游兜底填的？如果是上游兜底填的，反查必须无条件覆盖。
3. **同一员工在多个项目显示不同部门是快照问题的特征信号** — 如果是实时反查 bug，所有项目应该显示同一个错误部门；如果是快照 bug，不同项目会显示不同部门（对应不同时间点的快照）。排查时先看"是一致错误还是不一致错误"。
4. **单元测试必须覆盖调岗场景** — 新增 `shouldAlwaysUseRealtimeDepartmentOverTenderSnapshot_whenEmployeeTransferred` 测试，模拟生产 06442 事故（3 个项目、3 个不同 tender.department 快照），断言全部显示当前实时部门。

---

## Sentry XIYU-F：通知接收人误含 admin 超管导致企微推送 skip（1024 次告警）

> 2026-08-04 修复 · Sentry Issue: 7591309142

**现象**：Sentry XIYU-F 持续产生 `WeCom notification skipped: user has no employee_number` WARNING 告警，累计 1024 次（生产 932 + 测试 92）。

**根因**：

`RoleProfileCatalog.GLOBAL_ACCESS_ROLES` 集合包含 `admin`（本地超级管理员），该集合同时用于：
1. 权限判断 / 数据范围 / 任务可见性（admin 确实需要全局权限）
2. **通知接收人解析**（admin 不参与业务通知，无 employee_number）

通知接收人解析调用 `getAdminUserIds()` → `findEnabledByRoleProfileCodes(GLOBAL_ACCESS_ROLES)` → 选中 admin 用户 → 企微推送时 `WeComPushService.push()` 发现 `employee_number` 为空 → skip + Sentry WARNING。

**关键认知**：admin 是本地超级管理员账号，不参与任何业务流程，不需要收到业务通知。`GLOBAL_ACCESS_ROLES` 的"全局权限"语义 ≠ "全局通知接收人"语义。

**修复**：

1. 在 `RoleProfileCatalog` 新增 `NOTIFICATION_RECIPIENT_ROLES` 常量（`GLOBAL_ACCESS_ROLES` 排除 `ADMIN_CODE`）：
```java
public static final Set<String> NOTIFICATION_RECIPIENT_ROLES =
    Set.of(BID_ADMIN_CODE, BID_LEAD_CODE, BID_SYSTEM_ADMIN_CODE);
```

2. 4 个通知接收人解析点从 `GLOBAL_ACCESS_ROLES` 切换到 `NOTIFICATION_RECIPIENT_ROLES`：
   - `NotificationRecipientResolver.getAdminUserIds()`
   - `TenderEvaluationNotificationService.REVIEWER_ROLES`
   - `TenderPendingAssignmentNotifier.ASSIGNER_ROLES`
   - `WarehouseExpiryScanTask` 通知接收人查询

3. 权限/数据范围/任务可见性等 7 处仍使用 `GLOBAL_ACCESS_ROLES`（不变）。

**教训**：

1. **"全局权限"和"全局通知接收人"是不同语义** — 同一个角色集合不能同时承载两种语义。admin 需要全局权限但不参与业务通知，必须按用途拆分常量。
2. **Sentry 告警的根因可能不在上报点** — 上报点在 `WeComPushService.push()`，但根因在上游的接收人解析逻辑把不该选中的人选了进来。修复应从源头（接收人解析）入手，而非在下游（推送服务）打补丁。
3. **静态常量集合的复用需要标注用途边界** — `GLOBAL_ACCESS_ROLES` 被 10 处引用，其中 3 处通知 + 7 处权限。新增 `NOTIFICATION_RECIPIENT_ROLES` 时必须明确 JavaDoc 标注"仅用于通知接收人解析，权限判断仍用 GLOBAL_ACCESS_ROLES"。
4. **Sentry skip 告警本身是有效的观测手段** — 此前根因分析文档提出的"方案 A：skip 路径增加 Sentry 上报"已实施，才使得这个隐藏问题可见。但需要在修复后同步降噪，避免 1024 次重复告警淹没真实异常。

---

## 108. `@RestControllerAdvice(basePackages=...)` 作用域陷阱：跨包 Controller 业务异常被 GlobalExceptionHandler 吞成 500（PR !2272 / 2026-08-05）

**背景**：新增 CA 证书对外查询接口 `CaIntegrationController`（位于 `com.xiyu.bid.integration.external` 包），复用 `CaCertificateService.getById()` 时抛出 `CaBusinessException("CA证书不存在: " + id)`。期望返回 404，实际返回 500 "系统繁忙"。

**根因**：

项目已有 `CaExceptionHandler`（位于 `com.xiyu.bid.resources.controller` 包），声明为：
```java
@RestControllerAdvice(basePackages = "com.xiyu.bid.resources")
public class CaExceptionHandler {
    @ExceptionHandler(CaBusinessException.class)
    public ResponseEntity<Map<String, Object>> handleCaBusiness(CaBusinessException ex) { ... }
}
```

`basePackages = "com.xiyu.bid.resources"` 限定了该 handler 仅扫描 `resources` 包及其子包下的 Controller。而对外接口 Controller 在 `integration.external` 包，**不在扫描范围内**，`CaBusinessException` 找不到匹配的 `@ExceptionHandler`，向上冒泡到 `GlobalExceptionHandler.handleGlobalException(Exception ex)` 兜底返回 500。

**5 Whys**：
1. ID 不存在返回 500 而非 404 → `CaBusinessException` 没有被 `CaExceptionHandler` 捕获
2. 为什么没捕获 → `CaExceptionHandler` 的 `@RestControllerAdvice(basePackages=...)` 限定了仅扫描 `resources` 包
3. 为什么有 basePackages 限制 → 原设计意图是"CA 业务异常 handler 只对 resources 包内的 Controller 生效"，避免误捕获其他模块的同类异常
4. 为什么跨包复用 Service 时没考虑到 → 新增对外 Controller 时只关注了接口契约，没审查异常处理链路
5. 工程根因 → **`@RestControllerAdvice(basePackages=...)` 的作用域是"包级"而非"异常类型级"**，跨包复用 Service 时，业务异常的处理链路不会自动跟随

**修复**（PR !2272）：在 `CaIntegrationController` 内部添加本地 `@ExceptionHandler`，不依赖远端的 `CaExceptionHandler`：
```java
@RestController
@RequestMapping("/api/integration/ca-certificates")
public class CaIntegrationController {

    @ExceptionHandler(CaBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleCaBusiness(CaBusinessException ex) {
        HttpStatus status = switch (ex.getErrorCode() == null ? "" : ex.getErrorCode()) {
            case "AUTH_REQUIRED" -> HttpStatus.UNAUTHORIZED;
            case "PERMISSION_DENIED" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // /{id} 传非数字 'abc' 时返回 400 而非 500
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "参数「" + ex.getName() + "」格式错误"));
    }
}
```

**教训**：

1. **`@RestControllerAdvice(basePackages=...)` 是"包级作用域"而非"异常类型级"** — 同一种业务异常（如 `CaBusinessException`）在 resources 包内有 handler 兜底，跨到 integration 包就失效。新增跨包 Controller 时必须检查它依赖的 Service 抛出的业务异常是否在当前包有对应 handler。

2. **跨包复用 Service 时要追踪异常处理链路** — 不能只看 Service 的方法签名和返回值，还要看它抛出的业务异常在哪些包内有 `@ExceptionHandler`。如果原 handler 有 `basePackages` 限制，跨包 Controller 必须自建本地 handler，否则异常会被 `GlobalExceptionHandler.handleGlobalException` 吞成 500 "系统繁忙"。

3. **`MethodArgumentTypeMismatchException` 也是高频被吞的异常** — `/{id}` 路径变量传非数字（如 `abc`）时，Spring 抛此异常，如果没有本地 handler，同样被 GlobalExceptionHandler 吞成 500。对外接口必须在每个有 `@PathVariable` 的 Controller 内加本地 handler 返回 400。

4. **对外接口的错误格式必须可预期** — 第三方系统依赖 HTTP 状态码和结构化错误体做处理。404 表示资源不存在、400 表示参数错误、401 表示未认证、403 表示无权限，这些都必须准确返回，不能统一变成 500。500 在对外接口契约中意味着"服务端 bug"，会触发对方系统的告警和重试逻辑。

5. **排查信号：日志中出现 "系统异常" + 业务异常类名** — 当 `GlobalExceptionHandler.handleGlobalException` 捕获了一个本应有专用 handler 的业务异常时，日志会打印 "系统异常" + 完整堆栈（含 `CaBusinessException` 等）。看到业务异常类名出现在 global handler 日志里，就是作用域失效的信号。

**相关代码**：
- `backend/src/main/java/com/xiyu/bid/resources/controller/CaExceptionHandler.java` — 原 handler（basePackages 限定 resources）
- `backend/src/main/java/com/xiyu/bid/integration/external/CaIntegrationController.java` — 新增本地 handler
- `backend/src/main/java/com/xiyu/bid/exception/GlobalExceptionHandler.java` — 兜底 handler（L418-426）

---

## 109. 多数据源聚合层必须防御性去重：生产日历重复显示事故（2026-08-07）

**背景**：生产环境（winbid.ehsy.com/dashboard）日历和截止时间模块出现重复项目，同一标讯在日历中显示2次。

**根因**：

1. **直接原因**：`WorkbenchScheduleQueryService` 合并两个数据源时无去重：
   - `calendarService.getEventsByDateRange()` — 用户手动创建的日历事件
   - `buildTenderDerivedEvents()` — 从 Tender 表派生的开标/报名截止事件
   - 两路数据直接 `addAll` 后排序返回，无任何去重逻辑

2. **数据层原因**：Tender 表存在真实重复记录。生产环境"截止时间"模块（只查 Tender 表，不合并 CalendarService）同样出现重复，证实数据库中已有重复标讯。

3. **去重策略漏洞**：`TenderDeduplicationPolicy.isDuplicate()` 在 `purchaserName`/`registrationDeadline`/`bidOpeningTime` 任一为 null 时**直接返回 false 不判重**。外部推送路径（`TenderIntegrationCommandService.rejectDuplicateBusinessTender`）也跳过空时间字段的标讯，后续 update 补充时间字段时不会再次触发去重检查，导致重复记录进入数据库。

**修复**（前后端双层防御）：

- **后端**：
  - `WorkbenchScheduleQueryService`：合并后按 `(eventType + eventDate + title)` 业务键去重，LinkedHashMap 保留首次出现的事件
  - `WorkbenchDeadlineQueryService`：报名截止/开标列表分别按 `(date + name)` 去重
  - 检测到重复时打印 warn 日志便于监控和后续数据清理
- **前端**（双重保险）：
  - `useWorkbenchSchedule.js`：API 返回数据 normalize 前先按 `(type + date + title)` 去重
  - `workbench-deadline-core.js`：`normalizeItemList` 中按 `(date + name)` 去重

**教训**：

1. **多数据源聚合层是天然的去重点** — 任何 `listA.addAll(listB)` 操作都必须考虑去重，不能假设上游数据一定唯一。即使上游有去重策略（如 TenderDeduplicationService），历史数据、去重策略漏洞、并发写入都可能导致重复。

2. **展示层防御性编程是最后一道防线** — 即使后端有去重，前端数据处理层也应加双重保险去重。前端去重成本极低（一次 Map 遍历），却能兜底后端未覆盖的边界情况、缓存异常、接口返回脏数据等问题。

3. **去重策略不能因字段为 null 就跳过** — `if (field == null) return false` 的去重策略会留下漏洞：先插入字段不全的记录，后续更新补全字段时不再触发去重，就产生了重复。更安全的做法是：字段不全时按已有字段做宽松匹配，或在补全字段时重新触发去重检查。

4. **LinkedHashMap 是去重的首选结构** — 用 `Map<String, T> + putIfAbsent` 去重，既保证 O(n) 时间复杂度，又通过 LinkedHashMap 保留原始插入顺序，避免去重后列表乱序。

5. **生产问题排查技巧：检查"只查一个数据源"的模块** — 如果日历重复是前端渲染问题，那"截止时间"模块（数据源不同、渲染逻辑不同）不应也重复。两个独立模块同时重复 → 根因在共享的数据源（Tender表）而非展示层。

**相关代码**：
- `backend/src/main/java/com/xiyu/bid/workbench/service/WorkbenchScheduleQueryService.java` — 日历聚合去重
- `backend/src/main/java/com/xiyu/bid/workbench/service/WorkbenchDeadlineQueryService.java` — 截止时间列表去重
- `backend/src/main/java/com/xiyu/bid/tender/core/TenderDeduplicationPolicy.java` — Tender去重策略（null字段漏洞）
- `src/views/Dashboard/useWorkbenchSchedule.js` — 前端日历数据去重
- `src/views/Dashboard/workbench-deadline-core.js` — 前端截止时间去重

**Follow-up 任务（已提升为正式任务，2026-08-07 思维链 Review H3）**：

> 本次修复是**展示层（前后端）防御性兜底**，不消除数据源的真实重复。以下根治任务已从 follow-up 备注**提升为正式任务**，登记在 `docs/exec-plans/tech-debt-tracker.md` §「标讯去重：数据层清理 + 推送层策略加固」供重构 agent 扫描处理。前端薄防御层已移除（思维链 H2），去重单一事实源收敛到后端。
>
> 参考：`docs/exec-plans/tech-debt-tracker.md`「标讯去重：数据层清理 + 推送层策略加固」

1. **Tender 数据清理脚本**：开发脚本扫描 Tender 表，按业务键（`purchaserName + registrationDeadline + bidOpeningTime`）找出重复记录，合并/清理历史脏数据。清理前需先备份，并确认不会误删有效记录（保留 `id` 最小的一条）。
2. **去重策略加固（null 漏洞根治）**：`TenderDeduplicationPolicy.isDuplicate()` 当前在任一关键字段为 null 时直接返回 false 不判重。需改为：字段不全时按已有字段做宽松匹配，或在 Tender 更新补全时间字段时重新触发去重检查（`TenderIntegrationCommandService` 外部推送与 update 路径都要覆盖），堵住"先插不全区字段、后续补全不重判"的重复产生路径。
3. **持续观察**：依赖本次新增的 warn 日志（日历聚合与截止时间去重命中时打印）持续监控，确认去重是否仍在触发、是否还有新重复进入，作为数据清理与策略加固的触发依据。

## 110. 新增角色必须同步所有角色白名单——ProjectDocumentWorkflowPolicy 上传/删除遗漏 bid-SystemAdmin（2026-08-09）

### 问题背景

测试环境 06234 账号（OSS 投标系统管理员，角色 `bid-SystemAdmin`）在项目立项阶段上传招标文件、标书制作阶段上传投标文件时均报"权限不足"。

### 根因

`ProjectDocumentWorkflowPolicy.canUploadProjectDocument` 和 `canDeleteProjectDocument` 的角色白名单硬编码了 `admin` / `/bidAdmin` / `bid-TeamLeader` 等角色，但遗漏了 `bid-SystemAdmin`。该角色于 2026-06 作为"权限基线等同 /bidAdmin"的新角色引入（`GLOBAL_ACCESS_ROLES` / `data_scope=all`），但文档上传/删除策略白名单未同步更新。

### 教训

1. **新增角色时必须全局搜索角色白名单**：所有 `RoleProfileCatalog.xxx_CODE` 出现的角色枚举列表都需检查，包括 `@PreAuthorize hasAnyRole`、`ProjectDocumentWorkflowPolicy`、`ProjectAccessScopeService` 等。
2. **纯核心策略的白名单是隐性权限闸门**：与 `@PreAuthorize` 注解不同，纯核心 Policy 类的角色判断不会出现在 Controller 层面，容易被忽略。新增角色时需 grep 所有 `RoleProfileCatalog` 引用点。
3. **bid-SystemAdmin 权限基线等同 /bidAdmin**：两者都属 `GLOBAL_ACCESS_ROLES`，`data_scope=all`，在所有角色白名单中应保持一致。

### 修复

PR !2280：`canUploadProjectDocument` 和 `canDeleteProjectDocument` 白名单补入 `BID_SYSTEM_ADMIN_CODE`，测试覆盖对齐。

---

## 111. 新增被注入类漏加 `@Service` 导致 Spring 启动 crash-loop——纯 Mockito 单测 + `-DskipTests` 打包让它逃过所有门禁（2026-08-12）

### 问题背景

标讯创建事件推送西域 CRM 事件总线（`feat-tender-event-push`）部署到测试环境后，后端连续两次 crash-loop：

- 第一次：`No qualifying bean of type 'TenderEventLogPort' available`（`TenderEventLogWriter` 漏加 `@Service`）
- 第二次：`No qualifying bean of type 'TenderEventPayloadMapper' available`（`TenderEventPayloadMapper` 漏加 `@Service`）

两次都是 `TenderEventPublishService` 构造函数注入失败，`APPLICATION FAILED TO START`，systemd 反复重启。

### 根因链接（为什么升级很久没出问题，这次却逃过所有门禁）

这**不是**"漏加一个注解"的偶然失误，而是一条门禁全空的门禁链：

1. **单测用纯 Mockito，绕过 Spring 容器**：`TenderEventPublishServiceTest` 用 `@Mock private TenderEventPayloadMapper`，直接 `new TenderEventPublishService(...)` 手工注入 mock。测的是编排逻辑，**从不验证 Spring 装配**——漏加 `@Service` 在单测里永远体现不出来。
2. **任务分支本地打包用 `-DskipTests`**：`package-release.sh` 强制 `mvn clean -DskipTests package`，跳过了全部测试。即使本地有全量上下文测试，这一步也完全不跑。
3. **pre-push 门禁不启动 Spring 上下文**：`ArchitectureTest` / `FPJavaArchitectureTest` 是静态/字节码级架构检查，**不启动 Spring 容器**，测不出 DI 装配错误。
4. **CI 的"能拦住"的测试需要 Docker**：`FlywayMysqlContainerTest`（`@SpringBootTest` + Testcontainers 全量上下文）本应能捕获 Bean 缺失，但它是**真正能孵化 Spring 容器的测试**，需要 Docker。本地开发环境 Docker 栈主要跑 MySQL/Redis，测试容器未必常开；且该测试只在 CI 的 `backend_changed=true` 且 PR 合入 main 时才跑。

**为什么"升级很久没出问题"**：以往大多数功能是**修改已有 `@Service` 类**（不新增被注入类），或新增类是入口 Controller/Service 且顺手加了注解。这次是**新增一个独立模块的多个类**，其中两个被 `@RequiredArgsConstructor` 注入的类漏了注解，而它们恰好没有能触发 Spring 装配的测试路径。

### 教训

1. **新增被注入类时，凡用 `@RequiredArgsConstructor` 且作为依赖被别的 `@Service` 注入，必须加 `@Service`/`@Component` 注解**——这是 Spring 装配的硬约束，编译期和纯单测都测不出来。
2. **纯 Mockito 单测算"逻辑正确"，不算"能启动"**：它为被测类手工注入 mock，把 Spring 装配这一步完全架空。被注入类的注解缺失、构造参数不匹配等问题，单测 100% 发现不了。
3. **`-DskipTests` 打包是门禁盲区**：上线产物走 `package-release.sh` 时测试全跳过。**打包前应单独跑一次能孵化 Spring 容器的测试**（如 `mvn test -Dtest=FlywayMysqlContainerTest` 或至少 `mvn spring-boot:run` 启动冒烟），确认 Bean 装配无误，而不是只依赖单测绿。
4. **"能拦截"的测试要真正跑起来才算防线**：`FlywayMysqlContainerTest` 存在但依赖 Docker，本地/特定分支不跑就形同虚设。**新增模块后，应在本地跑一次该全量上下文测试**，确认容器能起来再部署。

### 操作规范

1. 新增被 Spring 注入的类时，三连检查：`@Service` 注解 + `mvn compile` 通过 + 本地跑一次能孵化容器的测试（`mvn test -Dtest=FlywayMysqlContainerTest`）。
2. 涉及新增模块/多类时，部署打包前**必跑**一次全量 Spring 上下文测试，不能只依赖纯 Mockito 单测。
3. 若本地 Docker 不可用导致 `FlywayMysqlContainerTest` Skipped，视为**未验证**而非"通过"，部署前必须补跑或明确说明。

### 相关文件

- `backend/src/main/java/com/xiyu/bid/integration/tenderevent/application/TenderEventPayloadMapper.java`（补 `@Service`）
- `backend/src/main/java/com/xiyu/bid/integration/tenderevent/infrastructure/persistence/TenderEventLogWriter.java`（补 `@Service`）
- `.github/workflows/ci.yml` L193（`FlywayMysqlContainerTest` 全量上下文门禁）
- `docs/release/deploy-report-2026-08-12-122nd-test.md`（第 122 次测试部署报告）
---

## 112. 业绩批量导入按合同名 upsert 去重——重复合同名被合并覆盖，180 行只入库 141 条（2026-08-12）

### 问题背景

向测试环境 `winbid-test.ehsy.com/knowledge/performance` 批量导入"央企业绩导入文件包"（180 行 Excel + 708 附件）。后端返回 `successCount=180, failureCount=0, attachedCount=708, unmatchedFiles=[]`，但数据库 `performance_record` 实际只有 141 条记录。

### 根因

`PerformanceRowImporter.saveParsedRow` 按**合同名称唯一 upsert**：

```java
var existing = repository.findByContractName(parsed.contractName());
if (existing.isPresent()) {
    performanceId = updateService.update(existing.get().id(), parsed.command()).id();
} else {
    performanceId = createService.create(parsed.command()).id();
}
```

源 Excel 180 行里合同名有 22 组重复、共 39 行（如"年度框架协议"出现 6 次签约单位各不相同、"易派客平台推广服务协议"4 次）。后端逐行处理时，重复合同名的后续行会 `update` 覆盖先前行，导致**只保留每组最后一个签约单位的数据，其余被覆盖丢失**。`successCount` 统计的是解析行数而非实际新建记录数，因此返回成功但不代表数据完整。

### 教训

1. **批量导入的"成功数"不等于"入库记录数"**：当落库逻辑含按业务键 upsert 时，`successCount` 可能远大于实际新建记录数。校验导入完整性必须直接查库核对记录数，不能只看接口返回。
2. **合同名是业绩记录的唯一键**：同一合同名只允许一条记录，不同签约单位/项目类型的独立业绩若共用泛化合同名（如"买卖协议""年度框架协议"），导入时会被合并。多记录共用合同名属于数据质量问题。
3. **被合并行是"后写覆盖先写"**：upsert 对重复键取最后出现行，先出现的独立数据（不同签约单位）会被静默丢弃，无任何报错，属于数据丢失隐患。
4. **前端附件数量上限（100 个）与后端无限制不一致**：本次 708 附件需走后端直连 API 批量 multipart 上传，绕过前端组件限制；同时大请求（~265MB）需绕开网关层（APISIX 返回 413），直连后端端口（Spring multipart 上限 3GB）。

### 处理

将重复合同名唯一化（追加签约单位/客户类型等区分后缀，如 `年度框架协议（安泰科技股份有限公司-4）`），使 180 行全部成为独立记录；清空测试库业绩数据（`performance_record`、`performance_attachment` + 物理目录 `/data/attachments/performance/`）后重导，最终 180 条记录 + 708 附件全部入库、合同名无重复。

## 113. 业绩合订本导出 OOM——300 DPI PDF 渲染 + XWPFDocument 全量内存累积（2026-08-12）

### 问题背景

测试环境（`winbid-test.ehsy.com`，JVM `-Xmx2g`）勾选 30 条业绩导出合订本 Word，12 分钟后报 `OutOfMemoryError: Java heap space`。

### 根因

`PerformanceWordStyleConfig` 配置 `PDF_RENDER_DPI=300` + `MAX_PDF_PAGES_PER_FILE=30`，PDFBox `PDFRenderer.renderImageWithDPI` 将每页 A4 渲染为 `2481×3508` 像素图片（约 26MB/页）。30 条业绩 × 30 页 = 900 页 × 26MB = 23.5GB，远超 2GB 堆。POI `XWPFDocument` 全量在内存中构建，所有页面的 JPEG 编码字节累积在 doc 对象中直到 `doc.write(out)`，`img.flush()` 只释放 BufferedImage native 资源，不释放已嵌入 XWPFDocument 的字节数据。

### 教训

1. **PDF 渲染为图片的内存放大公式**：`页数 × DPI² × 4 bytes`（A4@300DPI=26MB/页，@150DPI=6.5MB/页）。Word 合订本导出的 DPI 应 ≤ 150，maxPdfPages 应 ≤ 10。
2. **POI XWPFDocument 不支持流式写入**：大批量场景必须分批构建临时 docx 再合并，或改用 docx4j 流式 API。
3. **前端修复暴露后端存量问题**：修复"跨页勾选丢失 ids"后（PR !2282），后端处理量从 10 条 → 30 条，暴露了内存管理缺陷。修复数据丢失类 bug 后，要验证后端能承载"全量数据"的处理压力。
4. **§104 maxExportRecords 防线无法防"数量少但单条重"**：`maxExportRecords=2000` 防的是 records 数量，不是渲染内存总量。需要"渲染内存预估"防线：`records × avgAttachments × avgPages × dpi²` 超阈值时拒绝。

### 处理

短期修复：`PDF_RENDER_DPI` 300→150（内存降至 1/4），`MAX_PDF_PAGES_PER_FILE` 30→10（页数降至 1/3）。内存预估：30 条 × 10 页 × 6.5MB = 1.95GB（2GB 堆可承载）。生产建议 JVM `-Xmx` 提升至 4g。

中期方案（根治）：分批构建 docx，每 N 条业绩生成临时 docx，最后合并；或改用 docx4j 流式写入。

## 114. 前端 Composable API 异常处理 429 提示覆盖与 CSS Token 硬编码回退陷阱（2026-08-15）

### 问题背景

在重构 AI 评分标准解析抽屉（V3 双阶段架构）过程中，门禁检查触发了 2 处潜在工程规范拦截：
1. `pre-push-gate.sh` 拦截了 Composable 中直接使用 `ElMessage.error(e?.message)` 的 catch 块；
2. Token 治理门禁拦截了使用 `var(--bg-white, #fff)` 的 CSS 声明。

### 根因与教训

1. **429 限流提示覆盖**：Axios 响应拦截器统一处理了 429 限流并弹出友好防抖提示。如果业务层 catch 块直接调用 `ElMessage.error`，会二次弹出通用错误覆盖全局友好提示。统一规则：业务 catch 必须使用 `@/api/error-utils.js` 中的 `notifyErrorUnlessRateLimit(error, fallbackMsg)`。
2. **CSS 变量的 Hex 降级值会被 Token 扫描器作为硬编码检测**：编写样式时随手写的 `var(--border-color, #e2e8f0)` 中的 `#e2e8f0` 会被静态分析器抓取并判定为硬编码颜色。应严格使用纯变量 `var(--border-color)` 或全局令牌变量 `var(--border-color, var(--color-gray-200))`。

---

## 115. Hibernate `week()` 函数仅接受 1 参数——MySQL WEEK() 默认 mode 0 兼容（2026-08-16）

### 问题背景

数据分析模块多维趋势分析选择"周"时间维度时，后端返回 400 错误。`function('week', p.createdAt, 1)` 传入了 2 个参数，Hibernate 底层调用 MySQL `WEEK()` 函数时抛出 `FunctionArgumentException`，由 `GlobalExceptionHandler` 转换为 400 返回。

### 根因

```java
// BUG：week() 传了 2 个参数，Hibernate 无法映射到 MySQL WEEK(date, mode)
function('week', p.createdAt, 1)
```

MySQL `WEEK(date[, mode])` 语法确实支持可选 mode 参数（0-7），但 Hibernate 的 `function()` 注册器只支持单参数版本的 `WEEK()`。多参数调用时 Hibernate 找不到匹配的 `SQLFunction` 实现，抛出 `FunctionArgumentException`。

### 修复

改为单参数调用，使用 MySQL 默认 mode 0（返回值范围 0-53，周日为一周第一天）：

```java
function('week', p.createdAt)
```

### 教训

| 问题 | 教训 | 规范 |
|------|------|------|
| SQL 函数多参数调用 | Hibernate `function()` 注册的 SQL 函数可能不支持多参数 | 使用 `function('func', col)` 前必须先确认 Hibernate 注册的签名，不要假设与原生 SQL 语法一致 |
| 400 错误被误读 | `FunctionArgumentException` 被 GlobalExceptionHandler 转成 400（前端参数校验失败），排查方向被带偏 | 后端 400 错误不一定是前端参数问题，也可能是后端 SQL 函数调用异常，需从日志中找 `FunctionArgumentException` |
| 多时间维度查询无测试覆盖 | 日/周/月/年四个维度的 SQL 查询只在联调时手动验证 | 时间维度查询必须为每个维度编写单元测试，且测试应覆盖不同的 SQL 函数签名 |

### 操作规范

1. 使用 Hibernate `function()` 调用数据库函数前，先在 Hibernate 注册表中确认函数签名（单参数/多参数）。
2. 后端 400 错误应先查日志中的 `FunctionArgumentException` 或 `JdbcSQLSyntaxErrorException`，排除 SQL 函数问题后再排查前端参数。
3. 时间维度/分组聚合类查询，每个维度（日/周/月/年）应有独立测试用例，确保 SQL 函数在不同数据库兼容。

---

## 116. Spring @Component 存在多构造函数时，必须用 @Autowired 明确标注注入入口（PR !2301 / 2026-08-17）

### 问题背景

同步 PR !2300（feat: 得分 LLM 消费防护）到本地后，后端启动直接 crash：

```
BeanInstantiationException: Failed to instantiate [InitiationTenderTextResolver]: No default constructor found
Caused by: java.lang.NoSuchMethodException: InitiationTenderTextResolver.<init>()
```

该类在之前的单构造函数版本可正常启动；PR !2300 新增了一个 package-private 的 7 参数测试构造函数后（第 2 个构造函数），Spring 在启动时无法决定使用哪个构造函数，回退到"找无参构造函数"的逻辑，因该类所有字段都是 `final` 没有无参构造，启动失败。

### 根因

```java
@Component
public class InitiationTenderTextResolver {
    // 构造函数 1：public 6 参数（面向 Spring 生产注入）
    public InitiationTenderTextResolver(ProjectDocumentRepository repo, ..., ObsShareUrlSigner signer) { ... }

    // 构造函数 2：package-private 7 参数（面向测试，注入 UrlContentFetcher stub）
    InitiationTenderTextResolver(ProjectDocumentRepository repo, ..., UrlContentFetcher fetcher) { ... }
}
```

Spring 构造函数注入规则：
- **单构造函数** → 即使无 `@Autowired` 也自动使用（Spring 4.3+ 约定）。
- **多构造函数** → 必须在其中一个上显式加 `@Autowired`；否则 Spring 视为"类希望使用 setter/字段注入"，进而尝试寻找无参构造函数，找不到就抛 `No default constructor found`。

本次 PR !2300 新增了"测试专用构造函数"（构造函数数量从 1 → 2），但没有在生产构造函数上补 `@Autowired`，导致触发"多构造函数无明确入口 → 找无参 → 失败"的路径。**单测不会发现**——因为测试代码直接 `new InitiationTenderTextResolver(...)` 手工调用 package-private 构造，完全绕过 Spring 容器。

### 修复

在生产用的 public 构造函数上添加 `@Autowired`：

```java
@Autowired  // ← 新增：多构造函数场景必须明确标记注入入口
public InitiationTenderTextResolver(
        ProjectDocumentRepository projectDocumentRepository,
        BidTenderDocumentSnapshotRepository snapshotRepository,
        ProjectDocumentFileStorage fileStorage,
        TenderDocumentStorage tenderDocumentStorage,
        TenderDocumentTextExtractor textExtractor,
        ObsShareUrlSigner obsShareUrlSigner
) {
    this(projectDocumentRepository, snapshotRepository, fileStorage, tenderDocumentStorage,
            textExtractor, obsShareUrlSigner, new BoundedHttpDownloader()::get);
}
```

测试用的 package-private 构造函数保留，不加 `@Autowired`，由单测直接调用。

### 教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 新增构造函数破坏了"单构造函数自动注入"的隐式约定 | "能工作"不代表"有显式契约"。Spring 4.3+ 的单构造免 `@Autowired` 是语法糖，一旦有第 2 个构造函数立即失效 | 被 Spring 注入的 `@Component/@Service` 类，如果存在多个构造函数（含测试专用构造），**必须在生产构造函数上加 `@Autowired`**，不能依赖"单构造时自动匹配"的隐式行为 |
| 测试代码绕过 Spring 容器，新增构造函数的破坏性无法在单测中发现 | `new ClassA(arg1, ..., argN)` 的纯 Mockito/手工注入模式，不会验证 Spring 装配路径 | 凡是 `@Component` 类存在"测试专用构造函数"场景，必须至少有一次集成测试启动 Spring 上下文 |
| 类的"构造函数数量"是脆弱的隐式假设 | 重构/新增测试构造时不会提醒"之前依赖了单构造免注解" | Code Review 时，凡是 `@Component/@Service` 类新增了任何构造函数，第一反应检查：生产构造函数上有 `@Autowired` 吗？ |

### 操作规范

1. **`@Component/@Service` 类新增构造函数 = 必查清单**：
   - 单构造函数 → 保持现状（可无 `@Autowired`），但建议显式加以防后续再加第 2 个。
   - 多构造函数 → 立即在生产入口构造上加 `@Autowired`，CR 时不通过视为 blocker。
2. **测试专用构造函数**（package-private / 参数更多用于 stub 注入）：不加 `@Autowired`，但在类头注释中说明用途。
3. **Spring 启动 smoke 测试**：有新增类/构造函数变更的 PR，本地必须至少跑一次 `mvn spring-boot:run` 观察 `Started XiyuBidApplication` 成功日志后再 push。

### 相关文件

- `backend/src/main/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolver.java`（补 `@Autowired` 的修复位置）
- PR !2300（得分 LLM 消费防护）——引入第 2 个构造函数的变更
- PR #2301（本修复）：追加 `@Autowired` 注解
- Lesson 111：同类"Spring 装配问题门禁全空"模式的另一个案例（漏 `@Service`）。

## 117. text block + formatted()：模板里的字面 % 必须写成 %%，否则 100% 崩溃（2026-08-17）

### 问题背景

测试环境（winbid-test，release `9c9b9d91d`）项目 225 的 AI 评分标准解析失败，页面提示：

```
⚠ 解析失败: Conversion = '"'
```

服务器日志（journalctl + application.json.log）：

```
java.util.UnknownFormatConversionException: Conversion = '"'
  at java.base/java.lang.String.formatted(String.java:4441)
  at ScoreParsePrompts.buildCandidateExtractionPrompt(ScoreParsePrompts.java:51)
  at OpenAiScoreAnalyzer.recallLane4LlmFullText(OpenAiScoreAnalyzer.java:133)
  at ScoreParseAppService.doExecuteParse(ScoreParseAppService.java:154)
```

### 根因

`ScoreParsePrompts` 的 prompt 模板是 text block，末尾 `.formatted(index, total, chunkText)` —— **模板本质是 `java.util.Formatter` 格式串**。模板第 26 行示例文案：

```java
- 明确的分值（如"10分"、"占30%"、"得X分"）
                      ↑↑
          % 后紧跟英文双引号 " (0x25 0x22) → 非法转换符 %"
```

`Formatter` 解析格式串时把 `%"` 当成转换符，抛 `UnknownFormatConversionException: Conversion = '"'`。

两个关键认知：
1. **与招标正文无关**。`chunkText` 经 `%s` 参数传入，不参与格式解析；非法格式符在**模板自身的示例文案**里。任何项目、任何输入都必炸——这就是"改了 3 天第一步还是跑不通"的原因：第一阶段候选提取每次调用必失败。
2. **该类此前零测试**。`buildCandidateExtractionPrompt` 等方法从未被单测调用过，一次真实调用即可暴露。

### 修复

```java
// 修复前：占30%" → % 后紧跟引号，非法
- 明确的分值（如"10分"、"占30%"、"得X分"）
// 修复后：%% 是 Formatter 的字面百分号转义，渲染后输出单 %
- 明确的分值（如"10分"、"占30%%"、"得X分"）
```

回归测试 `ScoreParsePromptsTest`：覆盖全部 4 个 prompt 构建方法，参数含 `%`/引号/中文引号，断言透传不参与格式解析。注意 `%%` 渲染后是单 `%`，断言要写 `"占30%\""` 而不是 `"占30%%"`。

### 教训

| 问题 | 教训 | 规范 |
|------|------|------|
| text block 模板里的字面 `%` 没转义 | text block + `.formatted()` 组合中，模板就是格式串，字面 `%` 必须写 `%%` | 凡是 text block 配 `.formatted(`/`String.format(`，CR 时必扫模板文本里的字面 `%`（重点：示例文案里的百分比） |
| 静态模板类零测试 | 这类 bug 与输入无关、100% 必现，一次冒烟调用就能抓住，但没有任何调用就永远不炸 | 静态工具类的每个公开方法必须有"至少调用一次"的冒烟单测；新功能的第一条测试应是最小真实调用 |
| 异常消息被直接透传到前端 | `Conversion = '"'` 是 `java.util.Formatter` 特有错误格式，可直接反查格式串问题 | 看到未知异常先看异常类名与消息格式，`UnknownFormatConversionException` 直接指向格式串，不要往输入数据方向猜 |

### 操作规范

1. **prompt/模板类新增或修改时**：本地必须有一次真实调用（单测或 main 方法）观察渲染结果，再 push。
2. **排查此类问题**：`UnknownFormatConversionException` = 格式串自身有非法 `%`，与参数值无关，直接检查格式串模板。
3. **全仓扫描方法**（本次已扫，无同类活 bug）：`rg '%[^sdfn%0-9,$.#+\- )(tTbBhHcCeEgGaAoOxX\s]'` 过滤后人工排除 JPQL `LIKE %:x%`、SQL `DATE_FORMAT '%Y-%m-%d'`、正则、SLF4J `{}` 占位符。

### 相关文件

- `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreParsePrompts.java`（修复位置：第 26 行）
- `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreParsePromptsTest.java`（回归测试，新建）
- 测试环境 release `9c9b9d91d`（第 125 次部署）首次引入该功能并暴露问题

## 118. 私有转换方法对 null 参数不设防：上游传 null 即全线崩溃（2026-08-17）

### 问题背景

§117 修复上线（`78facc5b2`）6 分钟后，测试环境项目 226 再现解析崩溃：

```
java.lang.NullPointerException: Cannot invoke "...ScoreSection.sectionTitle()" because "section" is null
  at OpenAiScoreAnalyzer.toCandidate(OpenAiScoreAnalyzer.java:247)
  at OpenAiScoreAnalyzer.recallLane3ScoreSemanticPatterns(OpenAiScoreAnalyzer.java:113)
```

### 根因

`toCandidate(ScoringCriterion, ScoreSection)` 直接访问 `section.sectionTitle()/content()/location()`，而**召回一（line 88）与召回三（line 113）两个调用点都传 `null`**（这两路召回没有章节定位）。只要召回一或召回三产出候选即 NPE，整个任务异常终止，后续召回（LLM 兜底）被剥夺执行机会。

之前未暴露的原因链：第 125 次部署前 `%` 格式符 bug 挡在最前面（任何文件必炸）→ 修复后第一批测试文件召回一/三恰好 0 候选 → 项目 226 的文件首次让召回三产出候选 → NPE 首爆。

### 修复

`section == null` 时章节相关字段（contextNote/sourceText/location）降级为空串，召回流程继续。回归测试 `OpenAiScoreAnalyzerTest`：构造同时命中召回一提取与召回三语义过滤的单行文本（`"1 技术方案完整性 提供完整的实施方案得10分"`），修复前精确复现线上 NPE。

### 教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 私有转换方法假设参数非空 | 同一方法有传 null 的调用点时，"参数不可能为 null" 的假设就是 bug | 转换/装配方法若允许语义上的"无此数据"（如无章节定位），必须显式处理 null 分支，不允许裸访问 |
| 连环 bug 逐个挡路 | 修一个崩一个是"层层挡路"模式：前面的必炸 bug 掩盖后面的条件触发 bug | 新功能首发后连续崩溃，先全链路静态审查同类模式（裸解引用、格式串、越界），不要逐次上线逐次发现 |
| 测试盲区：分支未被真实数据走过 | 召回一/三有候选的路径从未在测试中执行过 | 对每个召回/提取分支，构造能触发该分支的最小输入做冒烟，而非只测空输入的 happy path |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiScoreAnalyzer.java`（修复位置：toCandidate 双参版本）
- `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiScoreAnalyzerTest.java`（回归测试，新建）

## 119. 业务大文件上传与下游 AI 解析限制解耦：禁止在核心上传入口误加硬编码拦截（2026-08-17）

### 问题背景

在引入 AI 评分标准解析及实际打分功能时，为了限制 LLM 解析单次消耗，前端在标书编制阶段的核心上传入口（`DraftingStage.vue`）硬编码了 `file.size > 50 * 1024 * 1024`（50MB 限制），后端也增加了带 50MB 校验的 Multipart 上传接口。导致用户原本支持 GB 级直传华为云 OBS 的投标文件无法正常上传。

### 根因与修复

1. **根因**：混淆了“标书制作业务大文件存储”与“下游 AI 解析 Token/文本处理范围”，误在平台通用上传前端入口加锁。同时下游打分读取服务 `ScoreBidDocumentLookup` 早期未接入 OBS 直传签名链路。
2. **修复**：
   - 前端回退 `DraftingStage.vue` 的 50MB 限制与提示文案，恢复直传华为云 OBS 全格式与大文件支持。
   - 后端 `ScoreBidDocumentLookup` 增加 `ObsShareUrlSigner` / `ProjectDocumentFileStorage` / `TenderDocumentStorage` 全链路兼容，支持透明读取 OBS 直传文件（`obs-direct:xxx`）与本地文件。
   - 补齐单元测试 `ScoreBidDocumentLookupTest` 与 `DraftingStage.spec.js` 大文件校验用例。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| AI 能力限制反向污染通用业务入口 | AI 解析吞吐或额度限制应在 AI 触发/解析层做流式切片或降级，严禁反向约束用户核心资产上传 | 核心业务资产（投标文件、工程图纸）存储必须保持无上限直传 OBS，AI 处理层按需分块 |
| 下游读取未对齐存储真实形态 | 存储层早已演进为 `obs-direct:xxx` 直传，下游新服务却按本地临时文件假设编写 | 读取 `project_document` 必须统一通过通用 Resolver/Signer 适配 OBS 真实协议 |

### 相关文件

- `src/views/Project/stages/DraftingStage.vue` / `DraftingStage.spec.js`
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreBidDocumentLookup.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringAppService.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreBidDocumentLookupTest.java`

## 120. 解除上传限制必须成对审视下游读取链路：OBS 直传 1.62GB 投标文件打分 OOM（2026-08-17，教训 119 的连环坑）

### 问题背景

测试环境页面报"投标文件加载失败：obs-direct:f2a75682-..."，服务器日志伴随 `java.lang.OutOfMemoryError: Java heap space`。该文件 `bid_file.file_size = 1743831704`（1.62GB），当天早些时候刚完成教训 119 的修复（解除上传 50MB 限制 + lookup 接入 OBS 直传）。

### 根因（5 Whys）

1. 为什么页面报"投标文件加载失败"？→ `ScoreBidDocumentLookup.loadBytes()` 三条链路全失败，抛兜底 `IllegalStateException`
2. 为什么 OBS 直传链路失败？→ 服务器日志 `Java heap space`：`defaultFetchUrl` 用 `HttpResponse.BodyHandlers.ofByteArray()` 把 1.62GB 全量读进 JVM 堆
3. 为什么没有大小预检？→ 同包 `BoundedHttpDownloader`（50MB Content-Length 预检 + 流式累计中止）已存在，且招标文件链路 `InitiationTenderTextResolver` 已正确接入，但投标文件链路 `ScoreBidDocumentLookup` 默认 fetcher 落到无限制的 `defaultFetchUrl`
4. 为什么漏接？→ 教训 119 修复（commit c0387daee）新增 OBS 直传分支时，没有 grep 全局收敛 `ofByteArray`/`defaultFetchUrl` 的同类风险点——**同类防护只修了一半**
5. 为什么会形成这个缺口？→ "解除上传限制"与"下游读取防护"没有成对设计：上传放开到 GB 级后，下游全量读取的内存模型必然击穿，这是同一对矛盾的两面

### 修复（一次性根治）

- `ScoreBidDocumentLookup` 默认 fetcher 从 `defaultFetchUrl` 换为 `new BoundedHttpDownloader()::get`，删除无限制实现；OBS/本地存储/doc-insight 三条链路统一 `capSize`（50MB）
- OBS 分支 catch 中识别 `TOO_LARGE_MESSAGE` 后立即抛 `OversizedBidFileException` 中止，**不允许吞掉后走 fallback**（fallback 链路同样会 OOM）
- `ScoreScoringAppService.triggerScoring` 同步段（bidHash 计算处）catch 超限转 `IllegalArgumentException("OVERSIZED_BID_FILE")`；Controller 转 400 + "投标文件超过 50MB，无法完成打分，请压缩后重新上传"
- `doExecuteScoring` 异步段保留 catch 兜底：`failTask` + `fallbackPending` 带同一文案
- 根因行为测试 4 个：OBS 超限中止 / 本地存储超限 / 同步段语义码拒绝 / 异步段兜底文案

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 解除上传限制后下游 OOM | 上传策略与读取策略必须成对设计：放开上限的那一刻，就要审计所有全量读取该文件的位置 | 修"限制类" bug 时，必须 grep 该资源的全部消费方，确认大小/内存假设仍然成立 |
| 防护组件存在但没接上 | `BoundedHttpDownloader` 与 `InitiationTenderTextResolver` 模式都已存在，新增同类链路时未对齐 | 新增文件读取链路时，先找包内/模块内已有的 bounded 下载与 capSize 模式并复用；禁止新写裸 `ofByteArray()` |
| catch 块吞掉超限异常继续 fallback | 超限是确定性失败，换链路重试只会重复 OOM | 大小超限类异常必须立即中止并转换为用户可操作提示，与"网络抖动可重试"严格区分 |
| 同一文件同一天第二轮回踩 | 教训 119 修复时只验证了"能传"，未验证"传完之后下游读得动" | 大文件链路修复的验证清单必须包含端到端：上传 → 触发下游（打分/解析）→ 观察内存与结果 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreBidDocumentLookup.java`（修复核心：三链路 capSize + BoundedHttpDownloader 默认化）
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/OversizedBidFileException.java`（新建语义异常）
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringAppService.java`（同步段拒绝 + 异步段兜底）
- `backend/src/main/java/com/xiyu/bid/scoreparse/controller/ScoreParseController.java`（OVERSIZED_BID_FILE → 400 文案）
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/BoundedHttpDownloader.java`（既有防护组件，本次复用）

> **演进说明（2026-08-17 spec 045）**：上述 50MB 止血方案已在 spec 045 中被彻底根治方案替代（流式自适应落盘 + sidecar 流式提取 + 500 万字文本量设限 + 4GB 并发总量配额），打分侧 50MB 字节硬限完全废止，`OversizedBidFileException` 删除。详见 `specs/045-bid-file-streaming-scoring/`。

---

## 121. AI 案例库与投标编制全链路打通：组件错挂、内存扫描防御与沉淀幂等

### 问题背景

PRD 要求投标编制阶段接入案例库智能推荐，结项时自动将标书切片沉淀为历史案例。排查发现四个关键断层与风险：
1. **组件错挂与孤立**：`DraftingStage.vue` 模板挂载了受 `system:admin` 权限限制的切片抽屉 `CaseSliceRecommendDrawer`，已开发完成的 `AiRecommendDrawer` 沦为死代码。
2. **DTO 字段缺失**：后端 `/api/cases/recommend` 仅返回算法分值，缺失评分项原文、应答摘要、来源项目等 9 个核心元数据，导致前端展示空白。
3. **全表扫描与 OOM 隐患**：`KnowledgeCaseRecommendAppService` 使用无分页的 `findAll(spec)` 将全量 ACTIVE 案例拉入 JVM 堆打分。
4. **结项沉淀非幂等**：`knowledge_case` 表无复合唯一索引，重复结项或并发事件可能导致重复插入或报错。

### 修复（全链路闭环与防御）

- 前端：在 `DraftingStage.vue` 正式挂载 `AiRecommendDrawer.vue`，清理孤立的 `CaseSliceRecommendDrawer.vue`；抽离 `AiRecommendDualPaneDialog.vue` 子组件以满足单文件 300 行预算；为 `keywordFilter` 实现真实的 300ms 输入防抖并优化 `handleOpen` 避免并发冗余请求。
- 后端：扩充 `KnowledgeCaseMatchScore` 9 个元数据字段并在 Policy 中装配；`KnowledgeCaseRecommendAppService.fetchCandidates` 增加 `PageRequest.of(0, 200)` 粗排截断（按复用次数倒序），防御全表内存打满。
- 数据库与并发：新增 V1192 迁移脚本创建 `(source_project_id, scoring_point_title)` 复合唯一索引；`ProjectClosedEventListener` 在应用层比对去重，并在持久化时捕获 `DataIntegrityViolationException` 按幂等成功处理。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 组件错挂导致开发成果孤立 | 开发新抽屉后未检查入口页面模板中的挂载点 | 新增/重构前端业务组件时，必须端到端核验调用入口、路由/抽屉挂载与用户权限矩阵 |
| 推荐打分在应用层全表内存扫描 | 算法在应用层计算容易忽视底层数据量增长 | 无论领域打分多高效，DB 查询必须有 `PageRequest` 粗排截断，杜绝无限制 `findAll` |
| 异步事件沉淀缺少并发唯一保护 | 内存 Set 过滤无法抵御高并发下的同时入库 | 数据唯一性必须由 DB 唯一索引兜底，且应用层捕获唯一键冲突时应视作幂等成功，不抛虚假告警 |

### 相关文件

- `src/views/Project/stages/DraftingStage.vue` / `AiRecommendDrawer.vue` / `AiRecommendDualPaneDialog.vue`
- `backend/src/main/java/com/xiyu/bid/casework/application/service/KnowledgeCaseRecommendAppService.java`
- `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`
- `backend/src/main/java/com/xiyu/bid/casework/domain/model/KnowledgeCaseMatchScore.java`
- `backend/src/main/resources/db/migration-mysql/V1192__add_knowledge_case_dedup_unique_index.sql`

---

## 122. AI 智能推荐案例：细粒度评分项语义匹配、富文本高亮 XSS 防御与 Controller DTO 隔离 (PR !2324 / 2026-08-18)

### 问题背景

《AI 智能推荐案例 — 抽屉详情（PRD）》要求在标书编制阶段，支持根据当前项目的评分项（`ScoreItem.detail`）条款进行细粒度语义匹配并推荐历史案例，且支持查看详情高亮对比与一键复用到标书指定章节。

在开发与门禁自检中发现三类关键设计注意点：
1. **粗粒度 vs 细粒度匹配**：项目级大文本向量会稀释单项条款语义，必须以评分项 `detail` 条款为核心做滑动分词重叠度加权计算。
2. **富文本高亮 XSS 风险**：前端使用 `v-html` 渲染高亮匹配词时，若直接在原文上执行正则替换 `<span>`，一旦原文包含 HTML 标签会导致 XSS 隐患，必须先做 HTML 实体转义。
3. **Controller 实体穿透拦截**：Controller 层直接返回 `ScoreItem` 实体会触发架构门禁（ArchUnit 规则），必须返回标准 `ScoreItemDTO`，实现领域模型与传输协议彻底解耦。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 粗粒度语义稀释 | 用整份标书匹配案例会导致单条款特征被稀释 | 推荐策略以评分项 `detail` 细粒度条款为第一特征，结合滑动 n-gram 分词 + 维度一致性加权 |
| `v-html` 注入风险 | 正则高亮文本未转义 HTML 实体存在 XSS 漏洞 | 在 `v-html` 渲染前，先执行 `escapeHtml` 实体转义与 `escapeRegExp` 正则转义 |
| Controller 实体穿透 | Controller 直接暴露 JPA Entity 破坏分层边界 | Controller 接口出入参必须统一使用 DTO / Record，严禁 Entity 穿透到控制器 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicy.java`
- `backend/src/main/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendAppService.java`
- `backend/src/main/java/com/xiyu/bid/casework/controller/ScoreItemCaseRecommendController.java`
- `src/views/Bidding/components/AICaseRecommendDrawer.vue` / `AICaseRecommendCard.vue` / `AICaseDetailModal.vue`
- `backend/src/main/resources/db/migration-mysql/V1193__add_score_item_to_case_reference.sql`

## 123. Flyway 迁移建 VARCHAR 列但 JPA 实体是 @Enumerated(STRING)：Hibernate schema-validation 启动即崩（2026-08-19，第 133 次部署容器测试门禁拦截）

### 背景

第 133 次测试环境部署打包时，容器测试门禁（`FlywayMysqlContainerTest`，2026-08-12 §111 事故后引入）失败：Hibernate schema-validation 报 `compliance_check_results.status` 列 `found [varchar], expecting [enum]`。若绕过门禁直接部署，后端启动即 crash-loop。

### 根因

V1196 迁移新增 `status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED'`，但对应 JPA 实体字段是 `@Enumerated(EnumType.STRING)` 枚举。Hibernate 6 对 STRING 枚举列期望 MySQL `ENUM` 类型，schema-validation（ddl-auto=validate）启动时校验列类型不匹配直接拒绝启动。与 V1040→V1041（check_type 列）历史事故完全同款。

### 修复

新增 V1197 `MODIFY COLUMN status ENUM('INIT','PARSING','CHECKING','COMPLETED','FAILED') NOT NULL DEFAULT 'COMPLETED'`（参照 V1041 先例，大写枚举值可过 validate，MySQL ENUM 比较大小写不敏感），配套 U1197 回滚脚本，PR !2335 合入后继续部署成功。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 迁移列类型与 JPA 枚举不匹配 | 给 `@Enumerated(EnumType.STRING)` 实体字段建列必须用 `ENUM(...)` 而非 `VARCHAR` | 迁移评审检查清单：新增列若映射枚举实体字段，列类型必须为 ENUM |
| 门禁价值验证 | 容器测试门禁拦截了一起必然后端 crash 的缺陷，纯单测无法发现 | 部署打包必过 `FlywayMysqlContainerTest`，逃生阀仅限明确无需验证的场景 |
| 回滚脚本位置与头部 | U 脚本必须放 `db/rollback/migration-mysql/`（非迁移目录）且含 `-- Input: migration-mysql/Vn__xxx.sql` 头 | `FlywayRollbackScriptCoverageTest` 强制校验，pre-push 门禁拦截 |

### 相关文件

- `backend/src/main/resources/db/migration-mysql/V1196__extend_compliance_check_results_for_quality.sql`（缺陷来源）
- `backend/src/main/resources/db/migration-mysql/V1197__fix_compliance_check_status_enum.sql`（修复）
- `backend/src/main/resources/db/rollback/migration-mysql/U1197__fix_compliance_check_status_enum.sql`（回滚）
- `backend/src/main/java/com/xiyu/bid/compliance/entity/ComplianceCheckResult.java`（TaskStatus 枚举）



## 124. 数据分析统计与详情页字段双源分裂 + 模式入口空检查拦截新请求（2026-08-19，PR !2333 验收四连漏）

### 背景

PR !2333 修复数据分析 7 项缺陷后用户复测仍报 4 个问题：M1 区域图表值全 0、M2 客户类型 5 类统计全错、M4 项目名称模式"暂无数据"、M1 部门下拉丢失搜索框/全选清空按钮。

### 根因

1. **字段双源分裂**：项目详情页立项表单编辑客户类型只写 `project_initiation_details.customer_type`（`ProjectInitiationMapper.applyInput`），从不同步 `projects.customer_type`；而 M1/M2 统计、筛选、下钻全部读 `projects.customer_type`。用户改了详情页，统计纹丝不动——上一轮只改了归一化函数，数据源根本不对。
2. **统计口径过窄**：`TrendAnalysisComputationService` 投标数仅计 `WON+LOST`；测试环境有总部所在地的项目几乎全是 `BIDDING` 进行中状态 → 区域维度行存在但值全 0，X 轴省份全靠前端常量补位。
3. **入口防御检查挡住新模式请求**：`CompetitorAnalysisAssemblerService.analyze()` 入口对 `competitors` 为空的请求直接短路返回空响应；项目名称模式前端只传 `projectName` 不传竞品列表，永远进不了项目分支。叠加 `fetchProjectCompetitorRows` 带 createdAt 时间过滤而项目名下拉 `fetchProjectNames` 不带——"能搜到、查不到"。
4. **组件替换丢失交互**：部门树形改造直接换成裸 `el-tree-select`，丢失了 `FilterSelect` 的搜索框/全选/清空/"已选 N 项" trigger 风格。

### 修复

- 客户类型取值统一 `coalesce(nullif(pid.customerType,''), p.customerType)`（优先立项详情、回退主表快照），覆盖 M2 查询/M1 维度与筛选/下钻/筛选项。
- 投标数口径改为全部项目（对齐 M0 KPI「投标总数」与状态维度），下钻"投标数"系列去掉状态过滤。
- `analyze()` 入口改为 `(competitors 为空) && (非项目模式)` 才短路；项目模式查询移除时间过滤。
- 部门下拉重构为 `TreeSelectDropdown`（FilterSelect 风格 trigger/搜索/全选/清空）+ 递归 `TreeSelectNode`（provide/inject 共享状态）。

### 经验教训

| 模式 | 教训 | 预防 |
| --- | --- | --- |
| 统计类 bug 先对数据源 | "统计数字不对"第一优先级是核对统计读的字段与详情页展示的字段是否同一列，而不是先改归一化/计算逻辑 | 新增统计口径前先跑 SQL 对比两表字段值分布 |
| 入口空检查 vs 新模式 | 门面方法入口的参数空检查会在新增"不需要该参数"的模式时静默拦截请求，返回空数据而非报错 | 新增模式分支时检查入口防御逻辑是否兼容空参数模式 |
| 下拉选项与查询过滤条件对称 | 选项接口不过滤的条件（时间/权限），查询接口也不应过滤，否则出现"能选中、查不到" | 选项接口与查询接口的过滤条件清单 diff |
| 组件替换保交互清单 | 换选择器组件时先枚举旧组件的交互资产（搜索/全选/清空/计数 trigger），逐项迁移 | UI 验收清单包含"下拉框交互完整性" |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/analytics/service/CustomerTypeAnalyticsQueryService.java`（coalesce 数据源）
- `backend/src/main/java/com/xiyu/bid/analytics/service/TrendAnalysisComputationService.java`（投标数口径）
- `backend/src/main/java/com/xiyu/bid/analytics/service/CompetitorAnalysisAssemblerService.java`（入口放行）
- `src/views/data-analysis/components/shared/TreeSelectDropdown.vue` + `TreeSelectNode.vue`（部门下拉交互）

---

## 125. 案例沉淀读已下线数据源 `project_score_drafts` 导致结项后无法生成案例（2026-08-19，PR !2342）

### 背景

测试环境项目 1350 已结项（`stage=CLOSED`），但「AI 自动生成案例」按钮不可用且未生成案例。反复联调无果，前端 `precipitateReady=false`、`precipitateTooltip` 为空。

### 根因

案例沉淀前置判定与 AI 切片构建读 `project_score_drafts`（评分草稿表），该表数据来源——「导入评分草稿」入口已于 2026-08-17 前端下线，导致全表为空；真实评分项已存在 `score_item`（spec 041 招标文件解析产物），但沉淀链路从未读取它。两个独立数据源，评分草稿链路删除后沉淀功能成了无源之水。

### 修复

前置判定（`CasePrecipitationAppService.getReadiness`）与 `ProjectClosedEventListener` 门禁 + `buildCases` 全部改读 `score_item`：
- 引入轻量 DTO `ScoreItemSnapshot(id, title, ruleText, category)`，避免 `casework` 直接依赖 `scoreparse` 实体，保持跨模块边界清晰（ArchUnit 通过）。
- `CaseAiMatcher` 输入从 `ProjectScoreDraft` 改为 `ScoreItemSnapshot`。
- `scoringCategory` 映射口径：`CaseAiMatcher.extractCategory(score_item.dim)`——`dim` 是中文业务词（技术/商务/实施服务/资质业绩），复用既有中文关键词归类，不再依赖已删除 draft 的 category 字段。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 数据源随功能下线空转 | 某条链路的数据源入口被下线后，读它的消费方不会报错、只会静默返回空，功能"看起来没坏其实已死" | 删除功能入口 / 清理废弃表时，全仓 grep 该表的消费方（Repository 方法），一并迁移或显式标注废弃 |
| 前置条件判定与真实链路数据源分裂 | 前端校验用的 readiness 与后端实际沉淀读的不是同一张表，会出现"校验通过但沉淀无数据"或"校验不过但数据在别处" | 前置条件判定与业务执行必须读同一数据源，用例/TODO 里显式断言数据源表名 |
| 跨模块实体依赖 | 直接让 application 依赖另一模块的 JPA Entity 会破坏分层，但跨模块取数又是刚需 | 用契约化的轻量 DTO / Record 承载所需字段，实体只在来源模块内部流转 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/casework/application/CasePrecipitationAppService.java`
- `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`
- `backend/src/main/java/com/xiyu/bid/casework/application/CaseAiMatcher.java`
- `backend/src/main/java/com/xiyu/bid/casework/application/ScoreItemSnapshot.java`（新增）
- `backend/src/test/java/com/xiyu/bid/casework/application/CasePrecipitationAppServiceTest.java`（新增）

## 126. 案例沉淀入库 0 条：loadBidFileMarkdown 用本地文件读 OBS `obs-direct:` 引用（2026-08-19）

### 背景

修复第 125 节后，项目 1350（ai测试01）结项后「AI 生成案例完成」按钮可点、能收到消息，但消息为「入库 0 条案例。处理时长 6 秒」。链路已通但 0 入库，需要查后端日志定位。

### 根因

日志给出完整闭环：

```
ERROR Failed to extract markdown from bid file: obs-direct:195e258c-84d8-4987-87d6-4a83f48d98d9
      java.nio.file.NoSuchFileException: obs-direct:195e258c-...
      at ...loadBidFileMarkdown(ProjectClosedEventListener.java:151)
AI 案例匹配开始：7 个评分项，标书文本长度 16 字符
AI 案例匹配完成：解析到 1 个匹配片段
  - draftId=0, confidence=0.8, snippet= (长度 0)
No new cases to save for project: 1350 (total candidates: 0, existing: 0)
```

1. **标书正文读取失败**：`loadBidFileMarkdown` 用 `Files.readAllBytes(Paths.get(filePath))` 读归档标书，但归档的 `filePath` 是 `obs-direct:{uploadId}`（OBS 云存储引用，不是本地路径）→ `NoSuchFileException` → 回退到 16 字符占位文本 `"# ai测试01\n暂无标书正文。"`。
2. **AI 拿到空文本** → 无从匹配，返回 `criteriaId=0` + 空 snippet。
3. **draftId=0 匹配不到评分项** → `buildCases` 里 `scoreItem == null` 直接 `continue` → `candidates=0` → 入库 0 条仍报「成功」。

### 修复

`loadBidFileMarkdown` 识别 `obs-direct:` 前缀走 OBS 签名下载：
- 注入 `ObsShareUrlSigner`（file 公共模块）+ `RestTemplate`。
- `obs-direct:` → `obsShareUrlSigner.trySign(filePath)` 取预签名 URL → `restTemplate.getForObject(url, byte[].class)` 下载字节 → 喂 `docTextExtractor`。
- OBS 读取/签名失败抛异常上报 `notifyFailure`（禁止让 AI 对着占位文本虚假成功）；本地路径保留原容错（文件可能已迁移）。
- 新增 2 个单测：OBS 下载成功入库、OBS 签名失败上报。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 云存储引用被当本地路径读 | `obs-direct:`/`oss://` 等是伪协议引用，不是文件系统路径，`Files.readAllBytes` 必然 `NoSuchFileException` | 读取文件前先判前缀：OBS 引用走签名下载，本地路径才 `Files.readAllBytes` |
| 读不到正文却回退占位继续 AI | 回退占位文本后 AI 只能返回空匹配，最终发「入库 0 条」的成功通知，掩盖真实故障 | 真实业务数据源（OBS 标书）读取失败必须上报失败通知，禁止用占位文本「假成功」 |
| 排障盲区：只盯匹配不盯输入 | 盯着 `draftId` 匹配找不到根因，其实病根在输入侧——AI 收到的标书文本只有 16 字符 | 先确认 AI 输入（标书文本长度、评分项清单）真实可靠，再查 AI 输出与匹配逻辑 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`
- `backend/src/test/java/com/xiyu/bid/casework/application/ProjectClosedEventListenerTest.java`

## 127. 竞品「是否中标」用项目级状态推导：语义层级错配 + notes 字段语义漂移陷阱（2026-08-19，PR !2339 验收遗留）

### 背景

PR !2339 修复竞品分析项目模式"暂无数据"后，用户复测发现数据能显示了，但明细表格「是否中标」列全部误显「已中标」。项目「测试竞品公司」状态为 WON（我司中标），竞品行各自的真实中标状态在项目详情「竞争对手情况」表格中录入。

### 根因

1. **语义层级错配**：`buildProjectResponse` 用 `isWonStatus(row.resultType(), row.projectStatus())` 推导竞品行 isWon——`resultType`/`projectStatus` 均为**项目级**字段（我司的中标结果），所有竞品行共用同一个值：项目 WON → 整列「已中标」。竞品的中标状态与项目（我司）的中标状态是两个独立事实，不能互相推导。
2. **真数据源是语义漂移字段**：项目详情「竞争对手情况」表格的「是否中标」列（`ResultConfirmStage.vue` 下拉枚举 `['已中标','未中标']`）实际写入 `project_result_competitor.notes`——该列原为「其他说明」自由文本，前端改造后复用存枚举（源码有明确注释）。竞品分析查询链路从未带出 `prc.notes`，实体上该字段名也完全暗示不了中标语义，导致排查者想不到真源在这里。
3. **历史修复方向反噬**：早前一轮修复为解决"项目未流转结果确认阶段时竞品全显未中标"，把项目级状态兜底进 isWon 判定（`coalesce(resultType, bidResultStatus, status)` + `isWonStatus`）——短期看似更准，实际把层级错配固化成正式口径。

### 修复

- `CompetitorAnalysisQueryService` 两条 JPQL（项目模式/趋势模式）select 均补 `prc.notes` 列；`CompetitorAnalysisRow` record 同步加字段。
- 新增 `CompetitorAnalysisComputationService.parseCompetitorWon(String notes)`：仅 `"已中标"`（trim 后精确匹配）为 true；「未中标」/null/历史自由文本均为 false。
- `buildProjectResponse` 改用 `parseCompetitorWon(row.notes())`；删除 `isWonStatus`。
- H2 实测回归 `CompetitorAnalysisJpqlRegressionTest`：复现"项目 WON + 竞品 notes 各异"场景，验证 JPQL 构造器 10 参匹配与 notes 按行独立带出。

### 经验教训

| 模式 | 教训 | 预防 |
| --- | --- | --- |
| 语义层级错配 | "项目的事实"不能推导"项目中每个参与者的事实"——竞品中标 ≠ 我司中标，聚合口径混入会整列传染 | 表格行级字段先问：值归属主体是行（竞品）还是父对象（项目）？ |
| 语义漂移字段 | UI 把枚举塞进"其他说明/备注"类自由文本列时，下游无从从字段名推断真源——必须查前端写入代码 | 排查"某列数据从哪来"时直接搜前端组件绑定的 prop，而不是按字段名猜表 |
| JPQL 构造器改动必须实测 | record 加字段后 JPQL `select new` 参数不匹配是运行时错误，纯 Mockito 单测完全不覆盖 | 查询层改动配 @DataJpaTest H2 实测（本项目已有先例模式） |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/analytics/service/CompetitorAnalysisQueryService.java`（JPQL 补 prc.notes）
- `backend/src/main/java/com/xiyu/bid/analytics/service/CompetitorAnalysisComputationService.java`（parseCompetitorWon）
- `backend/src/main/java/com/xiyu/bid/analytics/service/CompetitorAnalysisAssemblerService.java`（isWon 来源替换）
- `src/views/Project/stages/ResultConfirmStage.vue`（notes 语义漂移源头，L208-210 注释）
- `src/constants/competitors.js`（COMPETITOR_WIN_STATUS_OPTIONS 枚举）

---

## 128. MySQL 跨表字段 Collation 不一致导致 COALESCE 比较抛 Error 1267 (HY000)（2026-08-19）

### 背景与服务器崩溃现场

多维度趋势模块在测试服务器部署后，前端筛选区「部门」和「人员」下拉框数据完全为空。由于前端 `useFilterSearch.js` 中使用了 `catch { /* silent */ }` 静默吞错，前端表面上无任何异常报错。
通过全链路日志排查 SOP 查验 `/var/log/xiyu-bid/application.json.log`，抓取到真实异常为后端接口 `/api/analytics/filter-options` 与 `/api/analytics/trends/enhanced` 抛出 HTTP 500：
```
JDBC exception executing SQL [select distinct coalesce(nullif(pid1_0.customer_type,''),p1_0.customer_type) from projects p1_0 left join project_initiation_details pid1_0 on pid1_0.project_id=p1_0.id where ... and coalesce(...)!=''] [Illegal mix of collations (utf8mb4_bin,NONE) and (utf8mb4_0900_ai_ci,COERCIBLE) for operation '<>']
```

### 根因深度剖析 (5 Whys)

1. **MySQL 跨表 Collation 混合规则**：`projects` 表与 `project_initiation_details` / `tenders` 表在历史迁移中创建的字符串列 Collation 不同（`utf8mb4_bin` vs `utf8mb4_0900_ai_ci`）。
2. **COALESCE Collation 降级**：当 MySQL 执行 `COALESCE(colA, colB)` 且两列 Collation 不一致时，返回表达式的 Collation 被隐式设为 **`NONE`**。
3. **比较操作失败**：当对 Collation 为 `NONE` 的表达式进行 `<> ''`、`!= ''` 或 `LIKE '北京市%'` 比较时，MySQL 无法完成跨字符排序规则的强制转换，直接抛出 `Error 1267: Illegal mix of collations for operation '<>' / 'like'`。
4. **前端静默吞错陷阱**：前端 `useFilterSearch.js` 的 `catch` 块中没有任何 `console.error` 或错误提示，导致 500 接口报错直接表现为选项数组为空。

### 彻底根治方案

1. **消除 SQL 跨表 COALESCE / CASE 比较**：
   - 不在 SQL `WHERE` 条件中对 `COALESCE(pid.col, p.col)` 进行比较，拆解为独立列的布尔表达式：`((pid.col IS NOT NULL AND pid.col <> '') OR (pid.col IS NULL AND p.col IS NOT NULL AND p.col <> ''))`。
   - 区域省份解析从 SQL 20 行 `CASE WHEN coalesce(...) LIKE ...` 剥离，改为 Java 层纯函数 `RegionDimensionExtractor.extractProvince()`，100% 纯单测覆盖，彻底免疫数据库底层 Collation 差异。
2. **前端移除静默吞错**：
   - 改造 `useFilterSearch.js`，严格按照规范增加 `console.error` 日志输出。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 跨表 COALESCE 的 Collation 陷阱 | MySQL 对不同 Collation 字段进行 COALESCE 会使结果 Collation 变为 NONE，在此基础上的 LIKE / <> 比较会必杀崩盘 | 避免在 SQL WHERE / CASE 中对跨表不同 Collation 的字符串列做 COALESCE 比较，优先在各表字段独立做 WHERE 条件，或在 Java 内存做合并与解析 |
| 前端 silent catch 掩盖后端 500 | 接口 500 被 catch 吞掉，排查者容易误以为是"后端查出来数据为空"而盲目修改业务取数逻辑 | 严禁前端 catch 静默吞掉核心接口报错（`lessons-learned.md §25`），必须打出 error log |
| 未查真实日志凭空打补丁 | 缺乏真实服务器日志证据前，多次打补丁越修越错 | 第 2 次修复同一 bug 时必须停下来走全链路日志排查 SOP，抓取完整 SQL 堆栈 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/analytics/service/RegionDimensionExtractor.java`（新增纯函数）
- `backend/src/main/java/com/xiyu/bid/analytics/service/RegionDimensionRawRow.java`（新增）
- `backend/src/main/java/com/xiyu/bid/analytics/service/FilterOptionsQueryService.java`
- `backend/src/main/java/com/xiyu/bid/analytics/service/TrendAnalysisDimensionQueryService.java`
- `backend/src/main/java/com/xiyu/bid/analytics/service/TrendDrillDownService.java`
- `backend/src/test/java/com/xiyu/bid/analytics/service/RegionDimensionExtractorTest.java`（新增单测）
- `src/views/data-analysis/components/m1-trend-analysis/composables/useFilterSearch.js`

---

## 129. §128 的数据层根治补记：V1198 统一全库 collation（2026-08-19）

### 问题背景

§128 采用代码层修法（消除区域维度跨表 COALESCE + Java 纯函数），但同模块 `customerType` 维度仍残留两处跨表 coalesce（`TrendAnalysisDimensionQueryService` L138、`FilterOptionsQueryService` L217）——只要库内 collation 漂移还在，同款 1267 就是打地鼠。

### 根治方案（V1198）

Flyway 迁移用存储过程遍历 `information_schema.TABLES`，把所有 `TABLE_COLLATION <> 'utf8mb4_0900_ai_ci'` 的基础表 `CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`，对齐库默认。幂等：已对齐的表不进游标。

### 测试环境演练实测

- 漂移规模：141 张 `utf8mb4_unicode_ci` 历史表，总量约 51MB，最大表 `organization_sync_items` 10.8 万行/36.6MB。
- 执行耗时：**10.8 秒**全量完成，残留非对齐表 0。CONVERT 需重建表（写锁），生产执行建议放部署窗口。
- 边界验证（执行前必查）：所有列均为 utf8mb4、无列级 collation 覆盖、无 latin1 遗留列，同字符集转 collation 无损。
- 演练前 DB 全量备份：`/opt/xiyu-bid/db-backups/winbid-pre-collation-unify-*.sql.gz`。
- 复验：原崩溃 SQL（coalesce LIKE '北京市%'）执行正常，接口不再 500。

### 经验教训

| 模式 | 教训 | 预防 |
| --- | --- | --- |
| 代码层 workaround vs 数据层根治 | 代码层逐个表达式绕开，数据层一次清零所有同模式隐患（本例 customerType 残留即漏网） | 修环境配置漂移类 bug 时优先评估"把环境拉齐"的可行性与成本 |
| 动态迁移适配多环境 | 测试/生产表集合可能不同，存储过程遍历 information_schema 比静态 141 条 ALTER 更稳 | 环境差异型迁移用 information_schema 驱动 + 幂等谓词 |
| ALTER 前边界检查 | CONVERT 会转换所有字符串列，若有 latin1/binary 列会连带变更 | 执行前查 `CHARACTER_SET_NAME <> 'utf8mb4'` 与列级 collation 覆盖 |

### 相关文件

- `backend/src/main/resources/db/migration-mysql/V1198__unify_legacy_tables_collation_utf8mb4_0900_ai_ci.sql`
- `backend/src/main/resources/db/rollback/migration-mysql/U1198__unify_legacy_tables_collation_utf8mb4_0900_ai_ci.sql`

---

## 130. OBS 预签名 URL 二次编码导致 SignatureDoesNotMatch (403) — RestTemplate 传 String 走 UriTemplateHandler（2026-08-19，§125/§126 的连环坑）

### 问题背景

测试环境项目 1350「AI 生成案例」在 §126 修复（改经 `ObsShareUrlSigner.trySign` 签名 `obs-direct:`）后，结项仍报"AI 生成案例失败，原因 403 Forbidden"。走全链路日志排查 SOP 抓 `/var/log/xiyu-bid/application.json.log` 拿到完整 OBS 错误体（`LoggingClientHttpRequestInterceptor` 输出）：

```xml
<Code>SignatureDoesNotMatch</Code>
<Message>The request signature we calculated does not match the signature you provided.</Message>
<StringToSign>GET.../widbid-obs.ehsy.com/bids/2026/08/195e258c-84d8.../%25E5%25BC%25A0%25E5...docx</StringToSign>
```

**StringToSign 中文件名是双重编码 `%25E5%25BC%25A0`**（张家口银行投标6.16.docx），而非预期的单次编码 `%E5%BC%A0`。

### 根因深度剖析（5 Whys）

1. **RestTemplate.getForObject(String, ...) 的 UriTemplateHandler**：传 String 的 `getForObject(String url, ...)` 会把注册的 `UriTemplateHandler`（默认 `DefaultUriBuilderFactory`）把 URL 当 URI 模板处理，对已百分号编码的预签名 URL 再次编码，`%` → `%25`。
2. **OBS 预签名 URL 的本质**：签名时对 `objectKey`（含中文文件名）做一次 percent 编码得到 `%E5%BC%A0...`，并把该串与 `Expires/Signature` 一起计算签名；下载时必须**原样**发这个串。
3. **二次编码导致验签失败**：RestTemplate 把 `%E5` 变成 `%25E5` 后发往 OBS，OBS 收到的是 `%25E5`，用它重建的 `StringToSign` 与签名时用的 `%E5` 不一致 → `SignatureDoesNotMatch` 403。
4. **测试 mock 掩盖真实行为**：§126 的 `obsBidFile_downloadAndSave` 测试把 `restTemplate.getForObject(...)` mock 成直接返回 byte[]，断言字节回写成功，**从不走真实网络**，因此二次编码从未被测试暴露，测试全绿却埋雷。
5. **运行态 vs 测试态偏差**：本地/CI 全绿，测试环境真实下载才触发。

### 根本修复

`loadFileBytes` 下载预签名 URL 改传 **URI 对象**而非 String：

```java
byte[] bytes = restTemplate.getForObject(URI.create(signedUrl.get()), byte[].class);
```

`getForObject(URI, ...)` 重载**不经过 `UriTemplateHandler`**，预签名 URL 保持单次编码原样发送。

### 教训（七大根因判定）

- **主根因：测试不测根因**。上轮 mock 网络层 → 测试全绿但 bug 在，真实网络行为被 mock 掩盖。符合工程纪律"测试全绿，bug 还在 / 只测修过的函数"。
- 本次修复的根因回归测试**用内嵌真实 HttpServer**（`com.sun.net.httpserver.HttpServer`）记录服务器实际收到的 raw path，断言保持 `%E5..` 不含 `%25E5`，不再 mock 网络层。

### 预防措施

1. **凡是「第三方签名 URL / 外部 HTTP 下载」类功能，禁止只靠 mock RestTemplate 断言**；必须用内嵌真实 HTTP 服务器走真实网络（参考 `StreamingBidFileFetcherTest`、`ProjectClosedEventListenerTest#obsPresignedUrl_notDoubleEncoded_rootCauseRegression`）。
2. **拿到外部返回的完整错误体先看 `StringToSign` / 请求路径**，二次编码一眼可辨（`%25` 双百分号）。
3. 认准 `getForObject(URI, ...)` 与 `getForObject(String, ...)` 幂等：预签名/已编码 URL 一律传 URI。

### 相关文件

- `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`（`loadFileBytes`）
- `backend/src/test/java/com/xiyu/bid/casework/application/ProjectClosedEventListenerTest.java`（`obsPresignedUrl_notDoubleEncoded_rootCauseRegression`）

---

## 131. 规避 application→infrastructure 架构违规：使用 Port/Adapter 与 ReadModel 实现依赖反转（2026-08-20）

### 问题背景

在 `casework` 模块架构检查中，`ArchiveFileResponseFactory` 和 `ScoreItemCaseRecommendAppService` 直接依赖了基础设施层的 JPA Entity （`ArchiveFile`, `KnowledgeCase`）和 JPA Repository（`KnowledgeCaseRepository`），触发了 `BidCaseSliceArchitectureTest.applicationDoesNotDependOnInfrastructure` 架构测试拦截（25 处违规）。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 编排层/应用服务直接依赖 JPA 实体和仓库 | 应用服务应该依赖领域层的 读模型（ReadModel）接口 或 端口（Port），而非具体的持久化对象，以实现真正的依赖反转（DIP）。 | 1. 在 domain/model 层引入 `ReadModel` 接口（例如 `ArchiveFileReadModel`）隔离 Entity。<br>2. 在 application 层引入 Port 接口（例如 `KnowledgeCaseQueryPort`），让 infrastructure 层的 Adapter 进行实现以对外提供数据，消除 JPA Repo 直接依赖。 |
| JPA 实体（Entity）作为参数污染应用服务/工厂 | 实体直接传入响应构建器，导致应用层组件与底层的物理表映射关系高度耦合。 | 响应工厂或应用服务应只接收 ReadModel 接口或数据传输对象（DTO），禁止在不被豁免的 application 类中直接引入 Entity 作为方法参数。 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/casework/domain/model/ArchiveFileReadModel.java`
- `backend/src/main/java/com/xiyu/bid/casework/application/port/KnowledgeCaseQueryPort.java`
- `backend/src/main/java/com/xiyu/bid/casework/infrastructure/KnowledgeCaseQueryAdapter.java`
- `backend/src/main/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendAppService.java`

---

## 132. 案例沉淀 OOM：超大标书整文件读堆 + fallback plain text 二次分配（2026-08-20，§126/§130 的连环坑）

### 问题背景

§130 修好 OBS 预签名 403 后，项目 1350「AI 生成案例」下载终于成功，但结项仍报"AI 生成案例已完成，入库 0 条案例，处理时长 1 分 11 秒"。全链路日志排查 SOP 抓 `/var/log/xiyu-bid/application.json.log` 拿到决定性证据：

```
Document extraction started: fileName=张家口银行——投标6.16.docx, size=401229775 bytes
Caused by: java.lang.OutOfMemoryError: Java heap space
  at String.<init> / MarkItDownSidecarExtractor.fallbackExtract(line 221)   ← 把整文件 new String
  at ByteArrayHttpMessageConverter.readInternal                              ← RestTemplate 把整文件读堆
  at ProjectClosedEventListener.loadFileBytes(ProjectClosedEventListener.java:197)
```

标书 `401229775 bytes ≈ 383MB`，backend 堆 `-Xmx2g`。sidecar 端 `SIDECAR_MAX_UPLOAD_MB` 默认 **30MB**，上传必被 413 → health/网络异常 → 走 fallback → `fallbackExtract` 末尾对二进制 `.docx` 直接 `new String(content, UTF_8)` 二次分配约 800MB → OOM → extract 失败返回占位文本 → AI 对着 `暂无标书正文` 匹配 → 入库 0 条。

### 5 个为什么

1. 为什么入库 0 条？→ AI 拿到 16 字符占位文本，匹配不出有效切片。
2. 为什么是占位文本？→ `docTextExtractor.extract()` 抛异常被 `loadBidFileMarkdown` catch 后回退占位。
3. 为什么抛异常？→ `OutOfMemoryError: Java heap space`（`fallbackExtract` 的 `new String(content)`）。
4. 为什么 OOM？→ 401MB 标书被 `RestTemplate.getForObject(byte[].class)` 整读进堆，fallback 又 `new String` 二次分配，`-Xmx2g` 堆爆。
5. 为什么没早发现？→ §126 之前 403 让下载在验签就失败，**根本进不到文本转换**，此 OOM 被 403 掩盖；§130 修好 403 后，被掩盖的"大文件整载入 + 无限流防护"问题暴露（**修 A 破 B**）。

### 修复方案

**1. `MarkItDownSidecarExtractor.extract()` 加大小防护（根因层）**：任何 content 超过 `app.doc-insight.sidecar-max-upload-mb`（默认 30MB，与 sidecar `SIDECAR_MAX_UPLOAD_MB` 同步）直接抛 `DocInsightException`（领域异常，不用通用 `IllegalArgumentException`），**在进入任何大内存 POST / fallback 之前兜住**。`DocInsightException` 不在 `@Retryable(retryFor={RestClientException,IllegalStateException})` 内，不会触发无意义重试。

**2. `ProjectClosedEventListener.loadBidFileMarkdown()` 对 OBS 提取失败 rethrow（防虚假成功）**：与 `loadFileBytes` 的 OBS 分支同口径——OBS 是真实标书数据源，`docTextExtractor.extract()` 抛异常（含超限）必须 rethrow 走 `notifyFailure`，**禁止回退占位文本让 AI 对着"暂无标书正文"假成功"入库 0 条"**。

### 根因行为测试（不 mock 网络/大内存）

- `MarkItDownSidecarExtractorTest#oversizedContent_throwsControlledExceptionInsteadOfOom`：31MB content 超限 → 断言抛 `DocInsightException`（含"超过转换上限"），并 `verify(never)` sidecar POST——证明在进入任何网络请求前兜住。
- `ProjectClosedEventListenerTest#obsBidFile_extractFailure_notifiesFailureInsteadOfFalseSuccess`：OBS 标书 extract 抛超限 → `notifyFailure`(原因含"超过转换上限") + `saveAll(never)`——证明失败上报而非占位假成功。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 修 A 破 B | §130 修好 403 后，被 403 掩盖的"大文件整载入 + fallback OOM"暴露 | **403/500 等"前置失败"修复后，必须重跑下游整链路**；被前置错误掩盖的同类风险点（大文件读堆/OOM）要在同次任务 grep 全局收敛 |
| 框架理解偏差 | 以为 `RestTemplate.getForObject(byte[].class)` 能承载任意大小下载，实际 `ByteArrayHttpMessageConverter` 整文件进堆无防护 | **外部大文件下载/转换前必须先判大小上限**；超过目标系统上限（sidecar 30MB）不得继续上传/fallback |
| 回退占位 = 假成功 | extract 失败回退占位文本，AI 空匹配 → 发"成功但 0 条"通知掩盖真实故障 | 真实业务数据源读取/转换失败必须**上报失败通知**，禁止 fallback 占位让上层误判成功 |
| `new String(任意字节)` 兜底危险 | `fallbackExtract` 末尾对二进制 `.docx` 直接 `new String(content, UTF_8)` 二次分配内存必 OOM | 兜底 plain text 解析只对**已知文本型** input 有意义；未知二进制必须先判 `content.length` 上限或并发给 sidecar，禁止整读转 String |
| `@Recover` 匹配不到就包装 | 超限 `DocInsightException` 抛到 Retry 拦截器后，`RecoverAnnotationRecoveryHandler` 遍历现有 `@Recover(RestClientException/IllegalStateException)` 均不匹配 → 抛 `ExhaustedRetryException: Cannot locate recovery method` 把真实根因掩埋 | **有 domain 异常参与 `@Retryable` 方法时必须补对应 `@Recover(该异常)` 原样 rethrow**；否则真实业务失败会被包装成通用 `ExhaustedRetryException` 掩盖 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/docinsight/infrastructure/extractor/MarkItDownSidecarExtractor.java`（大小防护）
- `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`（OBS 提取失败 rethrow）
- `backend/src/test/java/com/xiyu/bid/docinsight/infrastructure/extractor/MarkItDownSidecarExtractorTest.java`
- `backend/src/test/java/com/xiyu/bid/casework/application/ProjectClosedEventListenerTest.java`


---

## 133. LLM 输出"原文复述"不可靠：prompt 软约束 + 合并"首次优先"叠加导致展示文字与原文不一致（2026-08-20）

### 问题背景

客户验收反馈"识别出的评分项和详细要素文字与招标文件评分表原文不一致"（评分标准解析模块）。排查发现三层根因叠加：LLM 输出 dim/detail/sourceText 是模型复述，prompt 里"禁止摘要、禁止改写"只是软约束，空白/断词漂移（全角↔半角空格、中文断词加空格、跨行合并）无任何校验拦截；正则召回按单行拆解，跨行单元格天然拆碎成短语；`ScoreItemMergePolicy.enrich` detail"首次出现优先"+正则召回先入池，残缺短语版挤掉 LLM 完整版。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| prompt 约束 LLM"原样输出"无确定性保障 | 涉及"逐字保真"的展示需求，必须在代码层做确定性校验/回填，prompt 只是第一道软约束 | LLM 自由文本字段落库前，用空白弹性子串匹配（`Pattern.quote` 拆段 + 段间空白正则）在原文中定位并回填原文精确片段；未命中降级保留 + WARN 可观测 |
| 合并去重"首次出现优先"隐含挤掉更完整版本的风险 | "首次优先"防的是幻觉覆盖，但会保留先入池的残缺版本；需要区分"更完整"与"幻觉" | 用数据驱动的背书判据：later.detail 是 later.sourceText 的子串（对齐回填后成立）且更长才允许覆盖；无背书即使更长也不覆盖 |
| 同编号不同名称直接丢弃 later，挡住补强路径 | 两路召回对同一评分项的 dim 表述常不一致（"技术部分" vs "技术方案"），sameDim 全等判据会挡掉唯一完整版本 | 差异场景用"内容包含关系"二次判定：base.detail 是 later.detail 的归一化子串 → 同一项补强；互不为子串 → 真不同项撞编号，维持丢弃（防误并） |
| 背书判据依赖自引用字段（伪背书） | sourceText 与 detail 同源于 LLM 输出时 `contains` 恒成立，幻觉文本可自证"有背书"绕过防幻觉保护 | 背书判定所依赖的字段必须来自独立真源（原文对齐结果）；未通过验证的内容应置空而非降级携带，切断自证链路 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreOriginalTextAligner.java`（空白弹性对齐纯函数）
- `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreCandidateMapper.java`（fromLlmCandidate 对齐回填 + 失败 WARN + sourceText 置空防伪背书）
- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemMergePolicy.java`（背书覆盖 + 同编号不同名称子串判据）
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreItemMergePolicyTest.java`（背书覆盖/防误并/空背书防御回归）

---

## 134. M1 趋势客户类型 X 轴显示英文枚举 + 筛选漏统计：双源字段"写入形态不一致"而查询链路各自直匹配（2026-08-20）

### 问题背景

数据分析页 `/analytics/dashboard` M1 多维度趋势分析：客户类型维度只勾 X 轴不选值时，图表 X 轴出现 `CENTRAL_SOE/GOVERNMENT` 等英文枚举名；且筛选器选中文客户类型时，已立项项目被静默漏统计。

### 根因链

1. **写入形态不一致**：立项详情 `project_initiation_details.customer_type` 存英文枚举名（`ProjectInitiationMapper.applyInput` 写 `d.getCustomerType().name()`），项目主表快照存中文文本（标讯导入/人工录入）。
2. **分组透传原始值**：M1 趋势维度查询 `coalesce(nullif(pid.customer_type,''), p.customer_type)` 原值直接作为分组标签返回，无归一化 → X 轴显示英文。
3. **筛选 SQL 直匹配双源原始串**：筛选器传中文值，`coalesce(...) IN (中文值)` 对英文枚举行永远不命中 → 已立项项目漏统计。
4. **为什么 M2 饼图正常？** `CustomerTypeAnalyticsComputationService.normalizeCustomerType` 在计算层做了英文枚举/中文变体 → 5 标准分类的归一化；M1 趋势链路是新链路，**漏掉了同一归一化**（同一口径逻辑只存在于一处时，新消费方必然漏）。

### 修复方案

1. **抽取纯函数工具 `CustomerTypeNormalizer`**（FP-Java 纯核心，无注入无 IO）：归一化逻辑从 M2 计算服务迁入，M2 委托复用，M1 分组/筛选/下钻/时间维度全部经同一入口。
2. **分组归一化**：`fetchCustomerTypeRows` 后处理把 `DimensionRow.category` 归一到 5 标准中文分类，归一化失败的脏数据行丢弃（对齐 M2"未分类不计数"既有决策）。
3. **筛选改"归一化后 ID 集"过滤**：新建 `AnalyticsCustomerTypeFilterSupport`（仿既有 `AnalyticsRegionFilterSupport` 模式）——原生 SQL 取回双源原始值，Java 端归一化匹配后收集 projectId，下游查询用 `p.id IN :ids` 过滤；维度查询/时间维度/下钻（含 axisValue 轴点击）全部接入，保证"筛选后数字 = 柱子之和"。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 同字段双源写入形态不一致 | 立项详情存枚举名、主表存中文文本，任何直匹配查询都会在其中一个源上静默失效 | **双源 coalesce 字段的分组/筛选/下钻必须先归一化再比对**；归一化函数抽纯工具类，所有消费方走同一入口 |
| 口径逻辑只存在于一处私有方法 | M2 有归一化、M1 新链路漏掉，bug 在验收后才发现 | **统计口径类逻辑（归一化/去重/边界）必须抽公共纯函数**；新增消费方时 grep 既有同字段的口径处理 |
| 筛选与分组口径分叉 | 分组的 Java 端归一化 vs 筛选的 SQL `IN` 直匹配，两套口径导致筛选计数 ≠ 柱子之和 | **筛选必须与分组共用同一归一化函数**；无法下推到 SQL 的归一化改用"预计算命中 ID 集"模式（见 AnalyticsRegionFilterSupport/AnalyticsCustomerTypeFilterSupport） |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/analytics/service/CustomerTypeNormalizer.java`（归一化纯函数）
- `backend/src/main/java/com/xiyu/bid/analytics/service/AnalyticsCustomerTypeFilterSupport.java`（筛选 ID 集口径）
- `backend/src/main/java/com/xiyu/bid/analytics/service/TrendAnalysisDimensionQueryService.java` / `TrendAnalysisQueryService.java` / `TrendDrillDownService.java`（接入点）
- `backend/src/test/java/com/xiyu/bid/analytics/service/AnalyticsCustomerTypeJpqlRegressionTest.java`（英文枚举回归：分组/筛选/下钻）

---

## 135. 健康检查不要 grep 已经没人读的环境变量（2026-08-20）

### 问题背景

`config.js` 早已硬编码 `mode: 'api'`，不再读取 `VITE_API_MODE`。但 `scripts/dev-frontend-health.sh` 仍在拉取到的 `config.js` 源码里搜 `"VITE_API_MODE": "api"`——源码里从来没有这个字符串。活脚本、CI、`.env.example` 还在注入这个死变量，新 agent 跟着抄。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 健康检查断言 env 名，代码已不读 | 检查过的是构建注入痕迹，不是运行时行为 | 断言真实信号（此处是 `mode: 'api'`），不要断言已退役的 env |
| 死 env 留在启动脚本 | 文档/CI/健康检查互相抄，僵尸变量活得比代码久 | 删读取方时同步删注入方；留下门禁防回潮（如 `demoPersistence` 的 import 拦截） |

### 相关文件

- `src/api/config.js`
- `scripts/dev-frontend-health.sh`
- `docs/implementation-notes/ponytail-safe-cuts.md`

## 136. LLM 网关返回 OpenAI choices 包装：自研解析器必须复用正规 client 的解包链路（2026-08-20）

### 问题背景

案例沉淀链路 `CaseAiMatcher` 自己写 `extractJson`（取首个 `{` 到末个 `}`）解析 LLM 返回。但网关返回标准 OpenAI 格式 `{"choices":[{"message":{"content":"```json\n{...}\n```"}}]}`，`extractJson` 把整个 choices 包装当目标 JSON，`matchedSnippet` 必然取不到 → 摘录质量很好却 8 个评分项全部"诚实降级为无证据"，knowledge_case 恒 0 条且发"成功通知"（spec 049）。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 后写代码绕过正规 client 直接裸调网关 | 项目已有 `OpenAiCompatibleClient` → `AiResponseParser.extractContentFromResponse` 的正确解包链路（先 `choices[0].message.content` 再解析），自研新解析器没复用就重新踩了旧坑 | 调 LLM 网关一律走统一 client；新写解析必须先解 OpenAI choices 包装（`unpackContent`），失败回退裸 JSON |
| 解析出的字段为空时直接判"无证据" | 字段空可能是**解析失败**而非 LLM 真判无证据；诊断日志要打印解包后的原始返回而不是 choices 包装 | 无证据分支打印 `node.toString()` 前 300 字符辅助定位；先确认字段名/结构再信降级结论 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/casework/application/CaseAiMatcher.java`
- `backend/src/main/java/com/xiyu/bid/ai/client/AiResponseParser.java`

## 137. 知识沉淀唯一键粒度：同标题多评分项是合法领域形态，不能按标题去重（2026-08-20）

### 问题背景

项目 1350 的评分项 61/62 标题同为"投标报价"（真实数据）。`knowledge_case` 唯一键 `(source_project_id, scoring_point_title)` 覆盖标题，`saveAll` 整批插入两条同标题记录撞唯一键 → 整个事务回滚 → 0 条入库；`DataIntegrityViolationException` 被 catch 成"幂等成功"发通知，用户看到成功但实际无数据（spec 049 SC-001）。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 唯一键粒度=标题，领域里标题不唯一 | 唯一键必须建在**领域唯一标识**（评分项 ID）上，不能建在展示字段（标题）上；同标题多评分项是合法形态 | 去重/唯一约束用业务主键（scoring_point_id），不要用标题 |
| 入库异常被吞成"成功通知" | `DataIntegrityViolationException` 当成并发幂等兜底，结果吞掉了**非并发**的真实冲突（同批自撞唯一键），误导验收 | 幂等兜底要有区分：同批自撞（前查后插的竞态）与整批回滚（数据语义冲突）不能都当幂等成功 |

### 相关文件

- `backend/src/main/resources/db/migration-mysql/V1199__knowledge_case_scoring_point_id_dedup.sql`
- `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`
- `backend/src/main/java/com/xiyu/bid/casework/infrastructure/KnowledgeCase.java`

## 138. 部署脚本"文件上传完成"不等于"进程重启完成"：验收触发要等新进程就位（2026-08-20）

### 问题背景

`deploy-test.sh` 输出 "Remote deployment completed" 仅表示文件已上传/脚本完成，进程重启在随后才发生。紧接着触发验收，请求落在**旧进程**上跑旧代码，得到"修复无效"的错误结论（spec 049：误判新代码未生效）。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 部署完成通知 ≠ 进程重启完成 | 重启用 systemd/脚本异步执行，文件上传完和进程就绪有时间差 | 部署后先 `ps -eo pid,lstart` 确认新进程启动时间，再触发验收 |
| 旧进程日志污染判断 | 新触发的请求可能由旧进程处理，日志时间戳在重启前 | 以**进程启动时间**为界，只信启动后产生的日志/结果 |

### 相关文件

- `scripts/release/deploy-test.sh`
- 测试服务器 `/opt/xiyu-bid/shared/backend/app.jar`

---

## 139. 通用异步任务表在却没人写：后来者会按模块再抄一张表（2026-08-20）

### 问题背景

基线 `export_tasks`（`ExportTask.java`，含 `data_type` / `export_type` / PENDING→FAILED）早就存在。后来仓库、业绩、人员、CA、平台账号、标讯各自新建 `*_export_task` / `*_import_task`，列几乎同构。V1184 注释写「独立表避免与仓库导出任务混杂」，等于给复制发许可证。

**不是忘了这张表，是这张表没接上异步链路。** `ExportTaskRepository` 生产代码零注入；`/api/export/excel` 写临时文件后直接返回，不落库、不轮询、无 TTL。后来者要「异步 + 进度 + 下载 URL」时，搜到的能跑样本是 `warehouse_export_task`，于是复制。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 通用表存在但主路径不写它 | 基础设施只建表不够，没有写入/轮询/下载，后来者会当死代码 | 通用能力必须有一条活路径；README 不许写成「已提供编排」如果 Controller 没用 Repository |
| 模块隔离被理解成「每模块一张任务表」 | 状态机相同，差异只是 type 列；「避免混杂」不是建表理由 | 新增异步导入/导出：写入 `export_tasks`（或扩一列 type），禁止 `CREATE TABLE xxx_export_task` |
| 半截抽象挡不住复制 | `AbstractExportTaskStateService` 抽了 4 个状态方法，子类 `apply*` 仍逐字相同，新模块继续抄实体 | 新模块接抽象类 + 通用表，不要再抄 Entity/Flyway |

### 防复发

- 门禁：`scripts/check-async-task-table.sh`（pre-commit + pre-push）。新建 `*_export_task` / `*_import_task` 硬失败。
- 逃生阀：`ASYNC_TASK_TABLE_ALLOW_NEW=1`，PR 必须说明为何不能用 `export_tasks`。
- 存量 6～7 张专表不迁（独立任务）；本门禁只拦第 N+1 张。

### 相关文件

- `backend/src/main/java/com/xiyu/bid/export/entity/ExportTask.java`
- `backend/src/main/java/com/xiyu/bid/common/application/AbstractExportTaskStateService.java`
- `scripts/check-async-task-table.sh`
- `docs/exec-plans/tech-debt-tracker.md`（异步任务表复制条目）

## 140. 标书质量核查 047 验收失真根因：Mock 证据 + 固定文案 + page=1 硬编码让"全绿"无实质（2026-08-20，spec 049 实质化复盘）

### 问题背景

标书质量核查（compliance 模块）曾长期"验收看起来全绿"但无实质价值：检查证据是固定文案（如"已对照企业专家与项目经理库""已检索品牌制造商授权"），页码恒为 1，检查项结论与真实投标/招标文件、知识库无任何关联。同时存在新旧双链路（同步旧链 + 异步新链）并行，行为分叉且旧链无真实数据源。spec 049 五步实质化（真实投标文件 → 真实招标要求 LLM 提取 → 四知识库真实查询 → 真实页码/原文定位 → LLM 混合裁决）完成后的复盘。

### 根因链

1. **证据无真源**：早期实现为快速出界面，证据 quote 用模板文案、pageNumber 写死 1，前端"看起来有证据"但不可回查。
2. **无机器门禁拦截失真**：没有任何测试断言"证据必须来自真实定位"，Mock/固定文案在 code review 中也不显眼。
3. **双链路漂移**：重构引入异步链路后旧同步链路未删，两条链路各自演化，验收时走的链路与代码主链路不一致。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 证据类功能用固定文案/硬编码页码凑数 | "看起来有证据"比"没有证据"更危险——验收会误判功能已完成 | 证据字段必须有真实定位器（命中真页码+原文摘录，未命中 page=null+空 quote 显式暴露）；用 grep 门禁验收（固定文案/`pageNumber, 1`/isDemoMode 零命中，见 specs/049 quickstart §5） |
| 失败路径静默吞掉 | 知识库单库异常/AI 未启用若直接吞掉，用户拿到"全绿"假象 | 失败必须传导为可见状态：单库失败记 failedSources → 依赖项 MANUAL+reasonCode（PRD §3.5 五值）；AI 未启用 Noop → MANUAL 注明原因，任务仍 COMPLETED |
| 重构留旧链路"以防万一" | 双链路并存必然漂移，验收走哪条链不可控 | 新链路就绪即删旧链（spec 049 第零步先做双链路合并，解除验收阻塞项后再推进 US） |
| LLM 全量直出不可控 | 让 LLM 直接产出全部 25 项结论，幻觉无确定性拦截 | 混合裁决：确定性规则 lane 毫秒级先跑，LLM 只做语义项裁决层；通道一切失败（未启用/异常/响应非法）在边界适配器吸收为 sealed 值类型（ChatOutcome 四值），纯核心无异常流、单项降级不阻断整体 |

### 相关文件

- `specs/049-bid-quality-check-realization/`（spec/plan/tasks/quickstart/research，commits b492c41ca..cd4f9855c）
- `backend/src/main/java/com/xiyu/bid/compliance/domain/policy/`（BidDocumentQualityEngine/SemanticRulesLane/LlmAdjudicator/EvidenceAssembler/EvidenceLocator/ManualReasons/LlmVerdict）
- `backend/src/main/java/com/xiyu/bid/compliance/service/KnowledgeBaseOnDemandLoader.java`（四库真实查询 + failedSources）

---

## 141. Java 21 switch 模式匹配编译产物含 MatchException 构造，触发 FP 门禁"纯核心禁止构造 Throwable"（2026-08-20）

### 问题背景

spec 049 US5 中 `LlmAdjudicator#adjudicateItem` 源码无任何 try/catch、无 throws、无异常构造，但 `FPJavaArchitectureTest.pure_core_must_not_use_exceptions_for_business_flow` 仍报该方法。javap 反汇编发现：对 sealed 接口做 `switch` 模式匹配时，javac 为穷尽性守卫生成 `new java.lang.MatchException` + `athrow` 字节码，被门禁的"方法内构造 Throwable 子类"判据命中。

### 修复方案

改用 `instanceof` 模式匹配序贯判定（if-instanceof-return 链）：编译产物不生成 MatchException，语义等价且保留 record 解构绑定（`instanceof Success<LlmVerdict> success`）。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 门禁基于字节码而非源码 | 源码"看起来纯"不等于字节码纯；编译器为语言特性注入的隐藏构造（MatchException/合成类）会被 ArchUnit 判据命中 | 纯核心类报异常流违规但源码无异常时，先 `javap -c` 看字节码再改代码；纯核心对 sealed 类型分派优先 instanceof 链而非 switch 模式匹配 |
| void 业务方法触发"业务方法必须返回值"门禁 | 私有静态 helper 也在扫描范围 | 纯核心的 append/add 类 helper 改为返回合并后的集合（调用方可忽略返回值，门禁只查签名） |
| JSON 解析放纯核心 | Jackson checked 异常在纯核心必然 try/catch，必违规 | 解析器置 service 边界层（TenderRequirementsParser/LlmVerdictParser 先例），纯核心只消费解析后的值类型 |

## 142. 质量核查 contains 直出的否定语义盲区 + 精确匹配脆弱性（2026-08-21）

### 问题背景

PR !2365（spec 049 质量核查实质化）评审 + 修复中发现两处确定性 lane 的 `String.contains` 直出误判：

1. **否定语义盲区**：`SemanticRulesLane.declaredDeviation` 用 `contains("负偏离")||contains("不满足")` 判定"标书自述负偏离 → FAIL"。投标文件**标准表述"偏离表：无负偏离"**（含"负偏离"子串）被误判 FAIL；测试 fixture 里就写了"偏离表：无负偏离"但未断言 RESPONSIVENESS 状态，误报被掩盖。
2. **精确匹配脆弱性**：`vetoFirstStage` 用 `bidDoc.fullText().contains(qualification)` 对 LLM 提取的**完整资格表述**做精确子串匹配。LLM 提取 "ISO27001 信息安全认证" vs 标书写 "ISO 27001"，空白/标点差异即误判"缺失一票否决项 → FAIL"。

### 修复方案

1. `declaredDeviation` 改三分支：含关键词且**未被否定修饰** → FAIL；提及但全为否定表述（无/未/零/没有/不存在/不含/无任何 + 关键词）→ 确定性 PASS（自述无偏离）；未提及 → 交 LLM lane。否定判断：检查关键词紧邻前缀（前 4 字符）是否以否定词结尾，避免"付款方式存在负偏离"（前缀"存在"）被误豁免。
2. `vetoFirstStage` 匹配前对投标全文与资格表述统一归一化（去空白含全角空格 + 全部 Unicode 标点 `\p{P}`），保留"缺失即 FAIL"的 spec 语义，仅消除表述差异误报。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| contains 直出 FAIL 遇否定表述误报 | 中文投标文件"无负偏离/无不满足"是标准合规表述，contains 命中即误报 | 确定性规则 lane 用关键词直出硬信号前，先枚举否定语义（紧邻前缀否定词豁免）；宁可 MANUAL 也不武断 FAIL |
| LLM 提取物与文档原文精确匹配 | LLM 提取的资格表述带空白/标点，与文档写法不一致，contains 必误判 | 跨源文本匹配先归一化（去空白/全半角/标点）再 contains；一票否决项（FAIL=废标级）尤其不能靠裸 contains |
| 测试 fixture 未断言受影响项状态 | 测试写"偏离表：无负偏离"却只断言 VETO，RESPONSIVENESS 误报未暴露 | 涉及确定性 lane 的测试 fixture，对每个受影响检查项断言具体状态（PASS/FAIL/MANUAL），不留"未断言"盲区 |

---

## 143. Docker 容器默认无内存限制：sidecar 泄漏挤占宿主内存致后端 JVM native OOM（2026-08-21）

### 问题背景

第 139 次测试环境部署（172.16.38.78，7.5GB 内存无 swap）后 20 分钟，sidecar 容器内存膨胀至 4.7GB，挤占同机后端 JVM 的 native 内存，后端崩溃：`mmap failed to map 1332740096 bytes, errno=12`（native OOM，非堆 OOM，`-Xmx` 限制管不到）。systemd 自动拉起后第二实例仍受挤占风险。

排查确认两台环境（测试 + 生产 172.16.10.149）的 sidecar 容器均为 `docker inspect Memory=0`（无限制），且 sidecar 是**全机唯一 Docker 容器**（后端=systemd 裸机、前端=宿主 Nginx、MySQL/Redis=宿主安装）。根因：`document-converter-sidecar/README.md` 标准启动命令缺 `--memory` 参数——标准命令文档是所有环境复制部署的唯一来源，缺一个参数 = 所有环境裸奔。

### 处置与验证

1. **实证容器化归属**：用户质疑"测试环境用 Docker 了吗"，凭记忆不可靠——`/proc/<pid>/cgroup` 显示全部控制器归属 `/docker/<container-id>` 是铁证；`docker ps` + `systemctl` 排除法补全拓扑。
2. **在线加限零停机**：`docker update --memory=1g --memory-swap=1g xiyu-sidecar`（测试）/ `--memory=2g`（生产，容纳真实大文件转换峰值），`docker inspect` 确认生效 + health 探测通过。
3. **长效固化**：README 启动命令补 `--memory=2g --memory-swap=2g` 并标注 REQUIRED + 事故引用。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 容器默认 Memory=0 无限制 | 宿主无 swap 时，容器泄漏会挤占同机其他进程的 native 内存，表现为"无关进程"（后端 JVM）native OOM，与堆内 OOM（§113/§132）模式不同 | 常驻容器启动命令必须带 `--memory`/`--memory-swap`；标准命令文档（README）改动按安全参数对待，缺失即全环境复制裸奔 |
| 凭记忆断言环境拓扑 | "测试环境用 Docker 了吗"这类问题必须上机器实证，不能凭上次会话记忆辩解 | 容器归属判定用 `/proc/<pid>/cgroup` 的 `/docker/<id>` 前缀作铁证；`docker ps` + `systemctl list-units` 排除法补全全机拓扑 |
| `docker update` 限制会丢 | 在线加限零停机，但容器删除重建后限制归零，隐患静默回归 | 容器重建/迁移后必须复查 `docker inspect Memory`；期望失效模式=内核 OOM 杀容器 + `RestartPolicy=always` 拉起 + 后端优雅降级（WARN），宁可死 sidecar 不死后端 JVM |

### 相关文件

- `document-converter-sidecar/README.md`（启动命令已固化 --memory）
- `docs/release/deploy-report-2026-08-21-139th-test.md`（事故时间线与两环境处置记录）
- ~~遗留：sidecar 泄漏根治（`app.py`/`converter.py`，20 分钟膨胀至 4.7GB 的真实根因）另开任务，容器限制只是止血~~ → 已闭环，见 §144 与 `root-cause-analysis-sidecar-mammoth-oom.md`

---

## 144. 全局猴补丁副作用 + markitdown keep_data_uris 分层断裂：sidecar OOM 根治中的两个陷阱（2026-08-21）

### 问题背景

sidecar OOM 根治任务（PR !2373，完整根因见 `root-cause-analysis-sidecar-mammoth-oom.md`）中踩到两个 Python 侧陷阱：

1. **全局猴补丁破坏同进程其他调用方**：为抑制 docx→HTML 转换的 base64 图片内联，最初把 mammoth patch 打在模块级全局——结果 `/convert-images` 路径的 image_extractor 依赖 mammoth 原生 data URI 语义提取原图，被 patch 后返回空 src，2 个测试失败（images 空）。
2. **markitdown `keep_data_uris=False` 表面可解实际无效**：该选项只在 `_markdownify.py:107` 的 markdownify 层生效；docx 路径的 `_docx_converter.py:80-82` 调用 mammoth 时只传 style_map 不透传该选项——图片在 mammoth 层就已 base64 内联成巨型 HTML，内存峰值已发生，markdownify 层再剥离为时已晚。

最终方案：patch 移入 `ProcessPoolExecutor` worker 进程内执行（`_convert_worker` 首行），父进程 mammoth 保持干净——进程隔离天然分流两条路径的相反语义需求。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 全局猴补丁破坏依赖原语义的其他调用方 | patch 模块级共享符号 = 改变全进程行为；同进程中任何依赖原语义的代码路径都会静默中招 | patch 前 grep 枚举所有调用方对原语义的依赖；无法共存的语义用进程/子进程隔离作用域，不用 flag 分支切换 |
| 外部库"表面开关"不透传到底层 | markitdown 文档有 `keep_data_uris` ≠ docx 路径会消费它；分层架构中上层选项可能根本到不了出问题的那一层 | 用库的配置项前 grep 库源码确认参数流向（谁读它、在哪一层生效）；验证方式=小样本实测内存/输出差异 |
| 测试直调绕过 patch 生效点 | suppression 测试最初直调 `md.convert`（父进程），patch 在 worker 内根本不生效，测试假绿 | 测试必须走与生产一致的调用链（直调 `_convert_worker`）；finally 还原父进程共享状态，避免污染后续测试 |

### 相关文件

- `document-converter-sidecar/app.py`（`_suppress_docx_image_inlining` 幂等版 + `_convert_worker`）
- `document-converter-sidecar/tests/test_memory_guard.py`（suppression 直调 worker + finally 还原）
- `docs/lessons/root-cause-analysis-sidecar-mammoth-oom.md` — 完整根因分析

---

## 145. Sidecar /convert 内存全量拼接致 1GB 容器内大标书解析 OOM 崩溃（2026-08-21）

### 问题背景

在 §143 容器加固为 `--memory=1g` 后，测试环境对项目 1350 的 383MB 标书（`张家口银行——投标6.16.docx`）发起 AI 标书质量核查时报错："无法执行核查：投标文件解析失败，请确认文件可正常打开后重新上传"（HTTP 422）。

全链路日志排查 SOP 证实：
1. `document-converter-sidecar/app.py` 的 `/convert` 接口使用 `chunks: list[bytes]` + `b"".join(chunks)` 将上传文件全量读入内存，383MB 文件瞬间消耗超过 1GB 内存，触发 Python `MemoryError` 并被 Docker OOM-Killer（`Killed`）；
2. Sidecar 进程崩溃导致 TCP 中断，Java 端抛出 `HTTP/1.1 header parser received no bytes`；
3. Java 端 `SidecarStreamDocumentAdapter` 降级读取二进制文件触发 `Input length = 1`，最终返回空文本导致 422 业务异常。

### 修复方案

1. 改造 `document-converter-sidecar/app.py` 的 `/convert` 接口，移除 `chunks` 列表与 `b"".join(chunks)`，统一采用 64KB 流式直接写入 `tempfile.NamedTemporaryFile`。
2. 在流式写入循环中调用 `hasher.update(chunk)` 增量计算 `contentHash`，保持对外哈希结果一致。
3. 新增单测 `test_convert_streaming_content_hash` 验证流式 SHA-256 计算一致性。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| HTTP 大文件接口在内存中拼接字节 | 容器内存加固后，内存中做 `b"".join` 会让内存峰值暴涨至文件大小的 2~3 倍，直接触发 OOM | 处理文件上传的 Web 端点（无论是 FastAPI 还是 Spring）必须边读边写磁盘临时文件，禁止将全量文件内容加载到内存中 |
| 相同能力在不同接口实现不一致 | `/convert-stream` 已实现流式落盘，但 `/convert` 遗留了全量内存拼接 | 同一模块内处理文件流的接口必须对齐同一套流式落盘最佳实践，避免历史遗留接口成为隐藏炸弹 |

### 相关文件

- `specs/050-sidecar-convert-streaming-to-disk/`
- `document-converter-sidecar/app.py`
- `document-converter-sidecar/tests/test_app.py`

---

## 146. MarkItDown Docx pre_process_docx 内存解包爆炸致 1GB 容器 OOM（2026-08-21）

### 问题背景

在流式落盘与 mammoth 猴补丁合入后，测试环境对 383MB 标书（`张家口银行——投标6.16.docx`）发起 AI 标书质量核查时依然报错 422。全链路排查内核 `dmesg` 发现 Sidecar Worker 进程仍消耗了 893MB RSS 并被 cgroup OOM-Killer 杀死。

根因追踪：MarkItDown 的 `_docx_converter.py` 在把 docx 传给 mammoth 前，默认调用了 `pre_process_docx` 对 OMML 数学公式做预处理。该函数通过 `files = {name: zip_input.read(name) for name in zip_input.namelist()}` 把 docx 压缩包内的所有条目（包含 1893 张图片、380MB 媒体）全量解压进内存字典，并重新打包输出到 `BytesIO`，一次性产生 >760MB 的内存分配，直接击穿 1GB 容器限制。

### 修复方案

1. 在 Sidecar 转换 Worker 子进程中将 `markitdown.converters._docx_converter.pre_process_docx` 替换为直通函数（`lambda stream: stream`），避免媒体文件解包与内存重打包。
2. `mammoth.convert_to_html` 直接基于磁盘临时文件流按需读取 `word/document.xml`，解析 380MB 标书的内存峰值从 **893MB 暴降至 <30MB（降幅 >96%）**。
3. 在 `document-converter-sidecar/tests/test_memory_guard.py` 中新增 `test_docx_pre_process_memory_bench` 内存峰值基准测试（`tracemalloc` 断言 peak < 20MB）。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 第三方转换库内部隐藏的全量解包 | 很多第三方文档转换库（如 MarkItDown）在处理特定格式时，内部会隐藏 `zipfile.read()` 导致 2x~3x 内存膨胀 | 接入第三方解析库时，必须通过 `tracemalloc` 进行大文件内存火焰图/基准测试，禁止在没有内存断言的情况下直接上线 |
| 测试不测根因导致的假修复 | 单测只用几 KB 的 mock 文件测流程，单测全绿却掩盖了 383MB 文件在真实解析调用链中的内存峰值 | 涉及内存敏感或 OOM 缺陷的修复，必须编写带有几十兆媒体负载的真实 Benchmark 单测，用数据证明内存峰值受控 |

### 相关文件

- `specs/051-sidecar-markitdown-pre-process-memory-optimization/`
- `document-converter-sidecar/app.py`
- `document-converter-sidecar/tests/test_memory_guard.py`

---

## 147. 标书降级读取 Files.readString 导致 JVM 堆外 DirectBuffer OOM 与 500 异常击穿（2026-08-21）

### 问题背景

在测试环境中对项目 1351（投标文件 `四川能投办公投标文件（V1)20260605.docx` 高达 **1.66GB**）触发 AI 标书质量核查时，前端等待 70 秒后报错 500。

全链路日志排查发现：
1. 1.66GB 的 docx 文件在传输或转换异常时，Java 端 `SidecarStreamDocumentAdapter` 触发了本地降级分支；
2. 降级代码直接调用了 `Files.readString(filePath, StandardCharsets.UTF_8)`，试图将 1.66GB 的二进制 docx 全量读入堆外内存，导致 JVM 分配 1.74GB `DirectByteBuffer` 时内存超限，抛出 `java.lang.OutOfMemoryError: Cannot reserve 1743831704 bytes of direct buffer memory`；
3. 原代码的 catch 块仅捕获 `Exception`，未拦截 `java.lang.Error`，导致 OOM 致命错误击穿 Spring 框架，请求超时 70 秒后返回未处理的 HTTP 500。

### 修复方案

1. 改造 `SidecarStreamDocumentAdapter`：
   - 增加 `isSmallPlainTextFile` 白名单检查（仅针对 `.txt`, `.md`, `.json`, `.csv` 等且 <= 5MB 的纯文本文件允许本地读取）；
   - 对 `.docx`, `.pdf`, `.zip` 等二进制或大文件，跳过本地文本读取降级，仅计算 SHA-256 哈希并返回空文本，坚决禁止调用 `Files.readString`；
   - 将 catch 异常范围统一扩展为 `Throwable`，彻底杜绝 JVM Error 逃逸导致 500。
2. 编写单元测试 `SidecarStreamDocumentAdapterTest`，覆盖二进制文件和超大文件的安全降级行为。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 二进制/大文件随意调用 Files.readString | `Files.readString` 内部会分配大块 DirectBuffer，对几十兆以上或非文本文件调用极易引发堆外 OOM | 禁止在未经后缀白名单与文件大小（<=5MB）校验的情况下对任意上传文件调用 `Files.readString` 或全量加载内存 API |
| catch 只捕获 Exception 导致 JVM Error 逃逸 | 底层 OOM 等 `Error` 会绕过 `catch (Exception e)` 直穿 Spring 框架导致 500 崩溃 | 关键系统边界和资源降级方法必须捕获 `Throwable` 或前置防御，确保服务韧性与明确的业务错误返回 |

### 相关文件

- `specs/052-sidecar-docx-stream-filter-and-backend-guard/`
- `backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java`
- `backend/src/test/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapterTest.java`

---

## 148. 超大 Docx 标书媒体前置流式过滤与 HTTP 传输超限秒级优化（2026-08-21）

### 问题背景

在超大标书（如 1.66GB docx）AI 标书质量核查场景中，虽已修复 DirectBuffer OOM，但直接向 Sidecar 发送 1.66GB HTTP 请求体时，触发了 Sidecar 512MB (`MAX_UPLOAD_SIZE_BYTES`) 安全上传上限（HTTP 413）与网络传输超时，导致 Java 前置同步校验报 `422: 无法执行核查：投标文件解析失败`。

排查证实：
1. 1.66GB 的 docx 文件中，99.99% 的体积为 `word/media/` 内嵌的高清图片，核心文本 `word/document.xml` 仅 147KB；
2. 文本解析服务（MarkItDown/Mammoth）在提取 Markdown 时仅依赖 XML 文本与段落结构，内嵌图片在提取纯文本时完全多余；
3. 将 1.66GB 完整多媒体通过 HTTP 传输给 Sidecar 是性能与网络瓶颈的唯一根源。

### 修复方案

1. 新增纯 JDK 流式过滤器 `DocxMediaFilter`：
   - 当检测到 `.docx` 文件大小超过 20MB 时，使用 `java.util.zip.ZipInputStream` 与 `ZipOutputStream` 流式滤除 `word/media/` 媒体条目；
   - 过滤过程内存占用 $< 1\text{MB}$，耗时 $< 0.5$ 秒，将 1.66GB docx 精简为 1.5MB 的纯文本骨架 docx；
   - 原始文件 SHA-256 哈希不受影响，保持全局唯一指纹；
2. 改造 `SidecarStreamDocumentAdapter`：
   - 优先发送瘦身后的临时 docx，请求体从 1.66GB 骤降至 1.5MB（缩小 99.9%）；
   - 在 `finally` 块中通过 `Files.deleteIfExists` 严格原子清理临时文件；
3. 单元测试 `DocxMediaFilterTest` 与 `SidecarStreamDocumentAdapterTest` 覆盖大文件过滤、小文件跳过与损坏 zip 回退。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 向解析微服务全量传输含媒体的大文件 | docx/pptx 等复合归档中 99% 为媒体数据，直接全量传输会导致网络瓶颈、HTTP 413 超限与无谓内存消耗 | 在调用下游纯文本提取服务前，应在调用端或管道首端对归档结构进行流式去媒体瘦身，按需传输 |
| 临时文件残留风险 | 瘦身生成的临时文件若未清理会导致磁盘耗尽 | 临时文件生命周期必须绑定 `try-finally` 或使用受控临时目录，确保即用即清 |

### 相关文件

- `specs/053-docx-media-stream-filter-fast-intake/`
- `backend/src/main/java/com/xiyu/bid/compliance/service/DocxMediaFilter.java`
- `backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java`
- `backend/src/test/java/com/xiyu/bid/compliance/service/DocxMediaFilterTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapterTest.java`

---

## 149. 异步任务触发接口禁止同步重操作与大事务阻塞进度可见性（2026-08-21）

### 问题背景

在 `https://winbid-test.ehsy.com/project/1351` 触发标书质量核查时，前端弹出 `请求超时，请检查网络后重试`。

排查证实：
1. **触发接口假异步**：`triggerQualityCheck` HTTP POST 请求在返回 `taskId` 之前，同步调用了 `targetLoader.loadBidDocument` 与 `tenderDocumentLoader.loadTenderDocument`（执行大文件下载与 Sidecar 文本提取，耗时达 60.4 秒）。
2. **超时截断**：前端 Axios 全局超时配置为 30 秒（`API_CONFIG.timeout = 30000`），请求在 30 秒时被 Axios 主动断开，用户感知为“请求超时”。
3. **重复解析与大事务阻塞**：后台异步线程 `executeAsync` 再次全量解析文件（单文件重复解析 2 次）；且 `executeAsync` 整个方法加了 `@Transactional`，中间阶段的 `updateTaskStep`（20%、60%、80%）未独立提交，导致前端轮询在整个任务完成前始终读到 `INIT 0%`。

### 修复方案

1. **触发接口纯化为毫秒级响应**：
   - `triggerQualityCheck` 仅校验数据库中是否存在有效投标文件记录（元数据检查，< 5ms）；
   - 保存 `INIT` 任务实体后立即返回 `taskId`，HTTP 接口响应时间降至 $< 10\text{ms}$，彻底根除 30 秒超时；
2. **耗时逻辑完全异步化**：
   - 文件的物理加载、瘦身、Sidecar 解析、SHA-256 缓存命中检测与 LLM 裁决全量交由后台异步线程；
   - 若文件不可解析，异步线程更新状态为 `FAILED` 并记录具体错误原因；
3. **独立事务进度更新器**：
   - 引入 `BidQualityCheckProgressUpdater`，使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)`；
   - 每次任务步骤变更（20% 解析 -> 60% 知识库核查 -> 80% AI 语义裁决 -> 100% 完成/失败）即时独立 commit 到数据库，保证前端轮询秒级感知真实进度。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 异步触发接口混杂长耗时同步 I/O | 在 HTTP 触发端点中执行大文件下载与微服务解析极易击穿前端 Client 30s 超时阈值 | 异步任务的触发接口（POST /trigger）必须在 50ms 内完成元数据校验与任务记录创建，禁止在主线程同步调用外部重 I/O |
| 异步方法整体大事务导致中间进度不可见 | `@Transactional` 标注在异步顶层方法上会导致所有中间进度更新滞留在事务缓冲区，直到整个长任务结束才一次性提交 | 异步长任务的进度与状态更新必须使用独立事务（`REQUIRES_NEW`）或无大事务即时提交，确保轮询端点秒级读到真实状态 |
| 触发端与异步端重复执行相同解析 | 未理清生命周期职责导致文件被下载并解析 2 次 | 耗时任务的执行管道必须单一源，消除冗余重复操作 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckTaskAppService.java`
- `backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckAsyncExecutor.java`
- `backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckProgressUpdater.java`
- `backend/src/test/java/com/xiyu/bid/compliance/application/BidQualityCheckTaskAppServiceTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/application/BidQualityCheckTriggerPreconditionTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/application/BidQualityCheckAsyncExecutorTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/application/BidQualityCheckProgressUpdaterTest.java`



## 150. 全量回归暴露三类系统性测试债：projectScoped 联动、种子写域、契约漂移（2026-08-22）

### 问题背景

对 main 做全量 `mvn test`：8386 个用例中 **49 Failures + 38 Errors**，且 E2E 栈启动即崩（`OpenAiScoreAnalyzer` Bean 缺失）。隔离复现确认全部为存量问题，CI 未拦截——因 ci.yml 后端只跑架构测试 + Expense/BarCertificate 两个集成类，绝大多数失败处于盲区。

根因聚成三类：

1. **projectScoped 审计迁移缺测试联动（约 80 个 403）**：Service 加 `@Auditable(ln = true)` 后，调用链经 `ProjectAccessScopeService.resolveCurrentUser` 强制查库解析当前用户；而 `@WithMockUser` 默认 username=`user` 在 H2 测试库不存在 → 统一 403「当前用户不存在或不可用」。引入点如 `553822ac6` 给 CalendarService 补 `ln=true` 时未跑受影响集成测试。
2. **测试基建写错数据库**：修复时新增的全局 TestExecutionListener 种子器第一版无条件写库，把 seed 行写进了本地共享 MySQL 容器库（`xiyu_bid_verify` 跨 JVM 运行持久），导致 `FlywayMysqlContainerTest` 的 V1092「role_id NULL 计数」假失败（首轮绿、二轮红）；seed 的 `admin` 还与 `TenderCrmLinkIntegrationTest` 自建用户撞唯一键。
3. **公共契约变更后测试侧漂移积压**：spec039 下载收口换签名（trySign→signObsDirect）、spec037 清理配置键、CO-590 DTO 加必填、任务接口加参、eede8d1e1 控制器加依赖、V1091 收敛 role 枚举、048 加 profile 条件——每次都只改了生产侧，测试侧欠账累积到一次全量回归集中爆发。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| projectScoped 迁移后集成测试批量 403 | `@PreAuthorize` 只看 authority 看不出来，真正的闸门在 service 层查库；mock 用户不在库 = 全部拒绝（fail-closed 是对的，测试没跟上） | 给 Service 加 `@Auditable(ln=true)`/接入 scope 守卫时，必须同步跑受影响集成测试；新写集成测试优先自建同名真实用户（参考 AiDeepCapabilityIntegrationTest 范式）；存量 `@WithMockUser` 由 `IntegrationMockUserSeeder` 兜底 |
| 测试种子器污染共享持久容器 | 本地（非 CI）AbstractMysqlIntegrationTest 家族共享一个跨运行持久的 MySQL 库；Flyway 迁移类断言对 users 表数据敏感，任何额外 INSERT 都会造成"首轮绿二轮红"的假失败 | 测试数据种子必须限定写入域：检查 JDBC URL 为 `jdbc:h2:mem:` 才落库；自定义 username 不进全局清单，由测试类显式补种（避免撞唯一键） |
| 公共契约变更不同步测试侧 | 方法签名/DTO 校验注解/配置键/Bean 条件装配每变一次就是一批隐性测试债；CI 面窄时债会静默滚雪球 | 改公共契约（service 方法签名、DTO @NotNull、删除配置键、@Profile/@Conditional、控制器构造器参数）时，同一次提交内 grep 测试目录引用并同步更新；定期跑全量 `mvn test` 作为门禁补充 |

### 相关文件

- `backend/src/test/java/com/xiyu/bid/testsupport/integration/IntegrationMockUserSeeder.java`
- `backend/src/test/java/com/xiyu/bid/testsupport/integration/MockAuthSeedTestExecutionListener.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/E2eNoopScoreAnalyzer.java`
- `implementation-notes.html`（本次修复完整决策矩阵与验证证据）

---

## 151. 复杂/大体量标书 PDF 评分标准解析准确率系统性治理（Sidecar 换行与表格保真 + 跨维度编号防碰撞 + 聚焦切片召回）（2026-08-24）

### 问题背景

用户反馈"AI评分标准解析功能在某些招标文件（如 203 页、3MB、双标的的大型央国企电子商城招标文件）完全解析不准，很多评分项丢失或无法识别"。

全链路实测定位出 4 大系统性缺陷：
1. **Sidecar PDF 转换回退丢失换行与表格**：`markitdown` 在处理 PDF 时，由于 `plain_pages > form_pages` 回退到 `pdfminer.extract_text`，受字体与排版 matrix 影响输出 0 个 `\n` 换行符（全篇十万字变成单行长串），导致后端所有基于行正则与 Markdown 表格的提取器完全失效。
2. **候选池合并策略将编号视为全局唯一导致跨维度误吞**：`ScoreItemMergePolicy` 的 `mergeKey` 仅以 `candidate.code()` 为键；当商务评分项编号为 `1`、技术评分项编号也为 `1` 时，技术评分项及后续重号项因 `sameDim=false` 且非子串被**静默丢弃**。
3. **盲目全篇 Chunking 稀释信号并引发跨页割裂**：`StructuralDocumentChunker` 在无 Markdown 标题元数据时切出 25 个 4000 字 chunk 全篇机械送 LLM，不仅评分表格跨页被腰斩，且 90% 均为非评分合同噪声，拖慢耗时并增加幻觉。
4. **表格定位器缺乏纯文本与中文大章节感知**：`MarkdownScoreSectionLocator` 仅匹配 `|` 管道表格，对纯文本排版招标文件无法定位核心评分章节。

### 修复方案

1. **Sidecar PDF 结构化保真转换（`converter.py` & `app.py`）**：
   - 引入 `pdfplumber` 逐页提取表格为标准 Markdown 管道表格（`| col | col |`），单内换行转 `<br>`，彻底保留文本 `\n` 换行结构。
   - 增强 `extract_headings` 支持中文招标文件标准层级（`第X章 ...`、`评审办法前附表`、`1 评审方法` 等），输出带完整 breadcrumb 路径的结构化 section 元数据。
2. **评分合并策略防碰撞重构（`ScoreItemMergePolicy.java`）**：
   - 区分"同维度同编号合并补强"与"跨维度同编号独立保留"：当 `code` 相同但 `dim` 属于不同维度且内容互不包含时，作为独立评分项保留（例如商务项 1 与技术项 1 各自保留）。
3. **评分章节双模定位（`MarkdownScoreSectionLocator.java`）**：
   - 支持 Markdown 管道表格定位，并在无表格时自动回退为纯文本评分大章节定位，支持中文标题向上溯源。
4. **LLM 全文语义聚焦切片（`OpenAiScoreAnalyzer.java`）**：
   - 在 `recallLane4LlmFullText` 中增加 `filterRelevantChunks`，优先仅将包含评分关键词与章节路径的高信号 chunk 送入 LLM，大幅削减 80%+ 的无关合同噪音调用，响应提速数倍且大幅降低幻觉。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| PDF 解析单行化（0 换行）导致下游正则全军覆没 | PDF 文本抽取库（如 pdfminer/markitdown）容易在复杂排版/多栏/字体 matrix 下丢失换行符与表格结构 | PDF 转换必须保留表格结构与段落换行（使用 pdfplumber 提取表格转化为 Markdown 管道表）；入库或下游分析前校验换行符密度 |
| 合并去重 Key 范围过大导致真数据被误删 | 真实招标文件各章节/维度通常各自从 1 开始编号，编号仅在章节内唯一，不能作为全局唯一 Key | 合并策略必须考虑维度隔离（`dim + code` 复合防碰撞），仅在同维度或明确为同项原文扩展时才合并，跨维度不同项必须各自保留 |
| 200 页文档盲目全篇切片调用 LLM | 全文几十个 chunk 无差别调用 LLM 耗时极长、成本高昂且 90% 为噪声 | 语义召回前应基于文档目录/大章节/关键词做高信号 chunk 过滤与聚焦，让 LLM 专注于评分章节 |

### 相关文件

- `document-converter-sidecar/converter.py`
- `document-converter-sidecar/app.py`
- `document-converter-sidecar/tests/test_app.py`
- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemMergePolicy.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/structure/MarkdownScoreSectionLocator.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiScoreAnalyzer.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreItemMergePolicyTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/structure/MarkdownScoreSectionLocatorTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiScoreAnalyzerTest.java`

## 152. nohup 启动 vite 未重定向 stdin：PTY 关闭后 dev server 主线程阻塞假死，E2E 全军覆没（2026-08-24）

### 问题背景

本地起 1323 前端（`nohup npx vite --port 1323 ... > /tmp/xx.log 2>&1 &`）后，首请求 200 正常；跨 shell 会话后 Playwright E2E 全部 `page.goto` 90s 超时（`net::ERR_ABORTED`），连 `curl http://127.0.0.1:1323/` 都挂起——TCP 能握手但永不响应，vite 进程 0% CPU。

### 根因

`nohup ... &` 只处理了 SIGHUP 与 stdout/stderr，**stdin 仍挂在启动它的伪终端（PTY）上**。工具会话（Agent shell）结束时 PTY master 关闭，Node 主线程随后在死 TTY 上执行阻塞 `read()`，整个事件循环卡死（`sample` 采样栈 100% 停在 `uv__stream_io → read`）——进程活着、端口在听、但永远不处理任何请求。

### 修复方案与规范

启动命令补全 stdin 重定向：

```bash
nohup npx vite --port 1323 --host 127.0.0.1 < /dev/null > /tmp/xx.log 2>&1 &
```

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| dev server "启动正常、跨会话必假死"（TCP 可连无响应、CPU 0%） | `nohup` 不重定向 stdin 时，进程 stdin 继承 PTY；PTY 关闭后 Node 阻塞 read 卡死事件循环 | 后台化长驻进程必须三件套：`< /dev/null` + `> log 2>&1` + `&`；排查此类假死直接 `sample <pid> 3` 看主线程栈 |
| 按 `pkill -f "vite --port"` 杀进程失败 | 实际命令行是 `node .../vite.js --port`，模式没匹配上 | 杀端口进程用 `kill $(lsof -nP -iTCP:<port> -sTCP:LISTEN -t)` 按端口定位 |

### 相关文件

- 无代码改动（运维教训）；受影响命令：本地前端启动（qoder1 worktree，端口 1323）


## 153. 配置默认值陷阱：`${VAR:mock}` 让"漏配"伪装成"显式 mock"，生产静默假数据（2026-08-25）

**现象**：PR !2396 给 `RoutingAiProvider.resolveActiveConfig()` 加 `ai.provider=mock → null（回落 MockAiProvider）`分支后，任何未设置 `AI_PROVIDER` 的环境全部 AI 接口静默返回假数据，健康检查 UP(mockMode) 掩盖信号。生产仅因服务器 backend.env 手工配了 `AI_PROVIDER=custom` 才未触发。

**根因**：`application.yml` 写的是 `provider: ${AI_PROVIDER:mock}`——默认值本身就是 mock。**默认值让"未设置"与"显式设置"在代码里不可区分**，作者以为 mock 是 E2E 显式声明，实际是全局缺省。次生根因：共享方法新增 null 返回路径时，5 个接口方法只给 4 个加了 null 回落（漏 `embed()`），且未 grep 全部直接调用方（`QualificationAiParserService` 无 null 检查直接 `config.model()`）。

**修复**：默认值改空串只认显式 mock（e2e 栈在 application-e2e.yml 显式声明不受影响）；补两处 null 防护；preflight.sh + remote-deploy.sh 在 prod profile 下断言 `AI_PROVIDER` 非空且非 mock，违规在任何服务变更前中止部署。

**教训**：
- 带行为语义的开关（mock/demo/shadow）默认值必须留空，让漏配走 fail-fast 而非静默降级
- 改共享方法的返回契约（如新增 null 路径），必须 grep 全部调用方逐个核对，接口内每个方法都要过一遍
- 生产正确性依赖的 env 不能只活在运维手册/服务器配置里，必须进部署预检断言

---

## 155. AI 案例沉淀触发时机由结项（CLOSED）提前至结果确认（RESULT_PENDING）：数据就绪度与异步解耦（2026-08-25）

**现象与需求**：原 AI 案例沉淀生成挂在项目第 6 阶段（结项 CLOSED），由于结项审批往往滞后，导致项目复盘（第 5 阶段）无法及时引用已生成的案例，案例库沉淀也严重滞后。

**根因与架构重构**：
1. **数据就绪度分析**：案例沉淀依赖的标书全文在第 2 阶段（编制）已归档，评分项在第 2 阶段（招标文件解析）已就绪，投标结果（WON/LOST）在第 4 阶段（结果确认）登记完成后即已确定。案例生成核心 Prompt 不依赖复盘总结与保证金退回，数据在第 4 阶段即已 100% 齐备。
2. **触发源与前置条件迁移**：
   - 前置校验：`CasePrecipitationAppService.getReadiness` 校验由要求进入 CLOSED 阶段调整为校验结果已确认（`projectResultRepository.findByProjectId.isPresent()`）。
   - 自动触发：由 `ProjectStageService`（CLOSED 推进）迁移到 `ProjectResultRegistrationService`（结果登记成功后发布异步事件）。
   - 上下文读取：`ProjectClosedEventListener` 优先从 `project_result` 表获取最新确认的投标结果。
   - 前端感知：在结果确认阶段已登记状态下提供 `🤖 AI自动生成案例` 手动补发/重新生成按钮及异常 tooltip 场景化提示。

**教训与规范**：
- 复杂流程中的业务副作用（如沉淀/分析/生成）触发时机应严格由“数据最小充分就绪点”决定，避免机械地绑定到最末尾的流程终态。
- 异步沉淀任务由领域事件驱动解耦，不阻塞主流程状态推进；落库前保持评分项级别的幂等去重（`uk_source_proj_scoring_point`）。

---

## 156. 报价评分跨页截断专项评审修复（2026-08-25）

### 问题背景

PR !2399 报价评分截断治理提交后，经严格评审发现 2 个 CRITICAL 和 4 个 MAJOR 问题：
1. `converter.py` 的跨页表合并启发式过于宽松（"空首格+无评分词即合并"），在全局路径上会把语义无关的表拼接成畸形表，且只支持成对合并导致 3 页以上长表仍截断；
2. `OpenAiScoreAnalyzer.filterRelevantChunks` 邻近扩展原地修改布尔数组导致链式传播，表格密集文档退化为全量召回；
3. `ScoreItemMergePolicy` 报价跨 chunk 拼合未做行去重与长度上界防护；
4. `MarkdownScoreSectionLocator` 孤立块以长度判定过于宽松；
5. `ScoringItemExtractor` 管道行处理逻辑复杂度过高；
6. Python/Java 跨语言关键词缺乏自动化 CI 同步校验。

### 根因与修复

1. **跨页合并启发式多信号交集**：删除弱判据，改用「延续块命中报价参数关键词 ∩ 首行管道数=主块主体行众数 ∩ 间隔≤3 空行」多信号交集，并在循环中持续吸收支持跨 3+ 页长表链式合并；
2. **邻近扩展独立数组**：`filterRelevantChunks` 使用独立 `expanded` 数组，仅相对初始命中扩张一跳，不回写 `isRelevant`；
3. **按行去重与长度上界**：`ScoreItemMergePolicy` 引入 `PRICE_DETAIL_MAX_CHARS = 6000` 与 `LinkedHashSet` 行级 trim 去重；
4. **长词计数与上下文差异化**：`MarkdownScoreSectionLocator` 使用 `countDistinctLongParams >= 2`（嵌套词只算一次）收紧孤立块兜底，`CONTEXT_AFTER_LINES` 区分普通项（3 行）与报价项（8 行）；
5. **管道行分类重构**：`ScoringItemExtractor` 提取 `classifyPipeRow` 降低复杂度，三道判据（别名守卫 → 参数行/主项行区分 → 评分行保留）；
6. **自动化同步校验**：由 `scripts/check-keyword-sync.mjs`（pre-push §5.6 门禁）保证 Python 与 Java 常量强一致。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 全局转换路径上的启发式过于激进 | 影响所有文档解析的通用代码，必须采取保守条件 + 强签名匹配（关键词 + 列数特征），不可用弱假设 | 全局后处理必须有多信号交集与完备的负向用例防护 |
| 跨模块依赖反转导致循环 slice 违规 | `scoreparse` 已向下依赖 `biddraftagent`，常量下沉至 `biddraftagent.domain` 方可维持单向依赖 | ArchUnit 30 条规则必须在每变前运行验证 |
| 跨语言代码镜像缺乏自动化门禁 | Python 与 Java 分别维护同名集合极易漂移 | 必须提供自动化比对脚本并在 pre-push 或 CI 中执行 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/biddraftagent/domain/PriceScoringKeywords.java`
- `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/ScoringItemExtractor.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemMergePolicy.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/structure/MarkdownScoreSectionLocator.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiScoreAnalyzer.java`
- `document-converter-sidecar/converter.py`
- `document-converter-sidecar/tests/test_merge_split_price_tables.py`
- `scripts/check-keyword-sync.mjs`（跨语言关键词契约门禁，挂 pre-push §5.6）

---

## 157. `table-layout: fixed` 表格改列对齐布局：按最坏文本宽度预留列宽 + 布局守卫断言要限定容器作用域（2026-08-25）

**背景**：AI 评分标准解析抽屉（`ScoreParseTable.vue`）合计行从 `colspan=8` 横向 flex 条重构为对齐到「满足状态」「评分类别」等列下方的竖排单元格布局。

**踩坑 1：列宽按当前文案算，极端文案溢出**。`table-layout: fixed` 下 `white-space: nowrap` 的超宽内容不会撑开列，而是直接渲染溢出到相邻单元格。「评分类别」列按「客观项权重 12」算 96px 够用，但三位数权重「客观项权重 100」超出内容区 4px；高亮得分框 `min-width: 64px` 大于得分列内容区 60px 同样微溢出。修复：列宽按最坏文案（三位数）预留，评分类别 96→100px、得分列 76→80px。

**踩坑 2：布局守卫断言未限定作用域**。同页两个表格共用 `ScoreParseTable`（阶段 1 v-show 常驻 + 阶段 2 条件渲染），阶段 2 场景下 `findAll('.tfoot-stack-cell')` 会命中 4 个而非 2 个。修复：断言限定到 `.scoring-table-container .tfoot-stack-cell`。

**教训**：
- fixed 布局表格新增/改列内容时，列宽必须按最坏长度文案（最大位数、最长枚举）核算，内容区 = 列宽 - 左右 padding；`nowrap` 内容溢出是静默视觉缺陷，单测与构建都拦不住，需人工核算或浏览器目检
- 共享组件在同页多实例渲染时，测试断言选择器必须限定到区分性容器，避免跨实例计数污染

---

## 158. 评估表草稿保存 503：前端恒发 `recommendation={shouldBid:null}`，后端仅判 `rec==null`，显式插 null 击穿 NOT NULL DEFAULT（2026-08-20）

### 问题背景

评估表「保存草稿」接口偶发 503，根因是 SQL 1048（Column 'should_bid' cannot be null）。前端草稿保存**恒定**发送 `recommendation={shouldBid:null}`（非整体缺省），后端判定逻辑只检查 `recommendation == null`——对象非 null 但字段为 null 时，会**显式**向 `should_bid` 列插入 NULL，使该列 `NOT NULL DEFAULT` 约束不生效（DEFAULT 仅在列缺省时触发，显式 NULL 不算缺省），直接 1048 报错。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 前端恒发空壳对象 `{shouldBid:null}` | 序列化层"字段存在但值为 null"与"字段不存在"语义不同，后端不能只判外层对象 null | 判空要穿透到字段级：`recommendation == null || recommendation.shouldBid() == null` 才视为"无建议内容"，跳过插入 |
| NOT NULL DEFAULT 被显式 NULL 击穿 | MySQL DEFAULT 只对"列未出现"生效，JPA 显式 set null 会写入 NULL 触发 1048 | 实体落库前对可选字段做 null 短路（不 set / 整体不持久化），不要依赖数据库 DEFAULT 兜底 |
| 偶发 503 掩盖真实 SQL 错误 | 网关/异常转换层把 DataIntegrityViolation 包成 503，误导为服务不可用 | 排查 5xx 先看后端原始异常栈（SQL 1048 这类约束错误），不要只信状态码 |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/tender/service/TenderEvaluationSubmissionMapper.java`
- `backend/src/test/java/com/xiyu/bid/tender/service/TenderEvaluationSubmissionServiceTest.java`（回归测试）

---

## 159. Flyway 迁移版本号被 origin/main 占用：rebase 后本地脚本需重命名更高版本号（2026-08-19，contactNature 全链路）

### 背景

标讯联系人性质（contactNature）全链路实现中，本地迁移脚本最初使用 V1189，后两次撞号：origin/main 先被占用 V1189（analytics 权限迁移），重命名为 V1198 后再次发现 origin/main 已占用 V1198（collation 统一迁移），最终重命名为 V1200（V1199 亦被 origin/main 占用）；合并前 origin/main 又占用了 V1200（opportunity-prediction-reminder 迁移），再次重命名为 V1201。首次撞号时启动报 `Found more than one migration with version 1189`。

### 根因与连锁问题

1. **版本号冲突**：多 Agent 并行开发时各自挑选"当前最大版本号+1"，push 先后决定占用权，后合入方必须重命名。
2. **target 残留**：`target/classes` 中残留旧版本号文件，即使源文件已重命名，未 `mvn clean` 仍会重复加载。
3. **数据库脏状态**：首次失败启动已执行部分 DDL（建列）并写入失败历史记录，重命名后重跑报 `Duplicate column name`。

### 修复动作

- 迁移脚本与回滚脚本同步重命名为更高版本号（V1189/U1189 → V1201/U1201，期间 V1198、V1199、V1200 相继被占用），amend 提交。
- `mvn clean` 清理 target/classes 残留旧迁移文件。
- 手动删除 `flyway_schema_history` 中的失败记录与已建的空列，再重新应用迁移。

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 版本号挑选竞争 | 多 Agent 并行时版本号随时可能被占用，可能连续撞号 | 推送/启动失败报 "more than one migration with version X" 时，先 `git fetch` 核对 origin/main 已占用版本，重命名为当前最大版本号之上，回滚脚本同步改名；push 前必须再次比对 origin/main 的迁移目录 |
| target 缓存残留 | 重命名迁移文件后旧 class 副本仍在 classpath | 改动迁移文件后必须 `mvn clean` 再启动 |
| 失败启动留下脏库 | Flyway 失败记录 + 部分 DDL 会让重试报 Duplicate column | 重命名迁移前先清理 flyway_schema_history 失败行与已建列，禁止直接重跑 |

### 相关文件

- `backend/src/main/resources/db/migration-mysql/V1201__add_contact_nature_to_tenders.sql`
- `backend/src/main/resources/db/rollback/migration-mysql/U1201__add_contact_nature_to_tenders.sql`

---

## 160. AI 评分标准解析重构：从“AI摘要概括”转向“标书原文表格保真直抽”与 8 列标准排版（2026-08-25）

**背景**：AI 评分标准解析历史表现不准，根因在于过度依赖大模型对表格单元格做语义总结与提炼，导致长条款被改写、公式参数被丢弃、分值列与要素错位。

**重构解法**：
1. **8 列标准顺序重排**：`1 编号` ➔ `2 标书原文` ➔ `3 评分项` ➔ `4 权重` ➔ `5 满足状态` ➔ `6 评分类别` ➔ `7 预计/实际得分` ➔ `8 得分依据`。
2. **标书原文保真展示**：第 2 列主内容采用 `white-space: pre-wrap; word-break: break-word;`，忠实保留原始段落与换行排版。
3. **Prompt 严格原文约束**：重构 `buildCandidateExtractionPrompt`，明确要求 `detail` 必须为招标文件评分表格/条款中的评审细则逐字原文，绝对禁止任何形式的 AI 摘要、概括、缩写或改写。
4. **详情弹窗追溯**：弹窗首行文案对齐为「标书原文」，并在存在出处时展示「招标文件位置出处（如 `P47 评分办法表 第3行`）」。
5. **知识库对标与 100 分闭环无缝兼容**：基于高保真原文进行实体对标，5 大知识库（资质/人员/业绩/仓库/授权）匹配命中率更高，分值闭环与编号连续性校验继续作为机器门禁护航。

**教训**：
- 严肃招投标业务场景中，“标书原文 1:1 保真”是信任基石，严禁大模型做自作聪明的二次摘要。
- 底层数据保持结构化与保真文本，上层展示明确列边界，既能满足下游 AI 精准对标算分，又能给业务人员绝对确定感。

---

## 161. Flyway 角色权限同步：menu_permissions 追加权限键禁止带字面双引号（2026-08-25）

**背景**：specs/055 新增 `competitor.create`/`competitor.govern` 权限键，需 Flyway 迁移写入 `roles.menu_permissions`。

**陷阱**：历史迁移 V1118/V1121/V1122/V1123 用 `CONCAT(menu_permissions, ',"personnel.manage"')` 把**字面双引号 `"` 也写进了字段值**，导致存储为 `"personnel.manage"`（带引号），前端 `perms.includes('personnel.manage')` 与后端 `hasAuthority('personnel.manage')` 都匹配失败，权限静默失效。V1174 才批量 `REPLACE(menu_permissions, '"', '')` 修复。

**正确写法**（V1189/V1203 模式，权限键**不带引号** + FIND_IN_SET 防重）：
```sql
UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',competitor.create'),
    updated_at = NOW()
WHERE code IN ('bid-Team', 'bid-TeamLeader', 'bid-projectLeader', '/bidAdmin', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('competitor.create', menu_permissions) = 0;
```

**教训**：
- 含点号的权限键（`competitor.create`）也**不带引号**存储，`FIND_IN_SET` 才能正确防重。
- 写权限同步迁移前先 grep 历史迁移，认准 V1174 之后的正确范式（V1189），别照抄 V1121 的带引号写法。


## 162. 事件总线联调"没打通"：流水表 0 行才是真根因，wire 契约是假嫌疑（2026-08-26，CO 标讯事件推送）

**背景**：标讯创建事件推送西域 CRM 事件总线长期"没打通"，对接方评审把矛头指向"没用 SDK 的 `SystemInteractiveService`、绕过 SDK 直连 HTTP"。

**排查过程**：
- 反编译 `ClientSDK-release_0.0.2.jar` 字节码核实：`SystemInteractiveServiceImpl.sendEvent` 的 `data` 字段取自 `eventContent` 入参，trace 走 HTTP Header，无签名/协议封装——此前"SDK 把 data 硬编码为 trace"的论断**不成立**，直连与 SDK 的 wire 契约完全一致。
- 查开发库 `tender_event_logs`：**0 行**。功能开关 `XIYU_TENDER_EVENT_SDK_ENABLED` 从未在任何环境配置（`.env.example`/部署脚本均无），推送链路从未真正执行过。"没打通"与用不用 SDK 无关。

**修复**：`TenderEventHttpSender` 改为委托 SDK `sendEvent`（`@Import` 单类注册 `SystemInteractiveServiceImpl`，不能整包 `@Import SDKClientConfiguration`——会激活 Kafka/ZK/注册等消费侧组件）；`.env.example` 补开关与总线地址变量；新增 MockWebServer wire 契约测试锁定请求格式。

**教训**：
- 联调"没打通"先查**运行时证据**（流水表行数、发送日志），确认请求是否发出过，再怀疑 wire 契约。0 行流水 = 链路从未执行，方向就错了。
- 对第三方 SDK 的行为论断（尤其要据此绕过它时）必须**反编译字节码核实**，不能凭文档或印象；本次错误论断还写进了 javadoc 和 wiki，误导后续排查。
- 系统级 jar（system scope）的传递依赖要显式补齐（fastjson/httpclient），否则运行期 `ClassNotFoundException`，且本地 dev 不加载对应配置时 mvn test 无法暴露（同教训 152 fastjson crash-loop）。
## 163. 语义漂移字段口径修复只落首发模块：同根因消费方必须全量排查（2026-08-26，specs/055）

**背景**：§127（PR !2339）修复了 M4 竞品分析「是否中标」整列误显的口径错配，但同一根因的另一个消费方——M1 多维度趋势分析的竞品维度——未被同步排查。用户在 M1 图表选「竞品公司」时发现中标数恒等于投标数才暴露：图表 `computeDimensionTrend` 对全部维度统一用 `projects.status=WON` 计中标数，下钻 `TrendDrillDownService` 的明细过滤与 summary.totalWins 也是同口径。

**根因**：修"唯一数据源"类 bug 时只改了报告问题的首发链路（M4），没有 grep 该字段与业务词的全量消费方；且图表与其下钻是同一口径的两个出口，改一个没查另一个。

**教训**：
1. **修语义漂移字段口径先做消费方普查再动手**：grep 字段名（`prc.notes`）+ 业务词（`已中标`）+ 同义推导源（`status.*WON`），列出所有读写点逐个判定是否需同步。
2. **图表数字与下钻明细必须同源**：任一口径改动必须同时核对计算层、下钻过滤层、下钻汇总层三处，否则产生"图上 3 家点开 5 家"的二次事故。
3. **口径解析函数提取共享 support 单一来源**：本次把 `parseCompetitorWon` 与「已中标」字面量提取至 `CompetitorWonStatusSupport`，M1/M4 共用，杜绝第三处定义漂移。

**修复**：specs/055（分支 `agent/ox/fix-analytics-competitor-won-notes`）两个原子 commit：①提取共享 support（M4 委托零行为变化）②M1 图表 + 下钻切换 notes 口径。投标数维持不变。

---

## 164. 评分解析大数击穿数据库字段：分值必须设业务上界与多层纵深防御（2026-08-26，spec 047/055）

**背景**：AI 评分标准解析功能在复杂长文档（山焦物资采购项目 6 万字标书）解析时，抛出 `could not execute statement [Data truncation: Out of range value for column 'original_total_weight' at row 1]` 崩溃。

**根因**：
1. **测试不测根因**：单测仅用 10/20/60（合计 90）或 50/150（合计 200）等人造合法数据，从未覆盖年份（2024）、金额（100,000,000）、面积（40,000）等非分值大数被误提取为 weight 的脏数据场景。
2. **业务合理性上界缺失**：`filterInvalidWeights` 只校验 `weight > 0`，无上限拦截；`WeightOverloadTrimPolicy` 只剔除无编号/无原文的嫌疑项，带文本的假项穿透；`WeightNormalizationPolicy` 盲目累加得到超大 `total`。
3. **环境差异与物理约束脱节**：MySQL RDS 严格模式（`STRICT_TRANS_TABLES`）对 `DECIMAL(10,2)` 溢出（>= 100,000,000）直接报错中断，而 H2/本地宽松模式掩盖了物理字段溢出风险。

**三层纵深防御治理**：
1. **入参过滤层**：`filterInvalidWeights` 明确单项分值合理上界 `MAX_SINGLE_WEIGHT = 100`，超 100 的非法分值（年份/金额/面积等）直接丢弃。
2. **归一化策略层**：`WeightNormalizationPolicy` 增加原始合计合理上限 `MAX_REASONABLE_TOTAL = 1000`，超 1000 分判定为严重污染，拒绝归一化，`originalTotal` 返回 `null`。
3. **守卫与持久化层**：`ScoreMergeResultGuard.isHighConfidence` 增加 `weight <= 100` 约束防假项回补；`setOriginalTotalWeight` 强制 `setScale(2, HALF_UP)` 安全持久化。

---

## 165. Hibernate 6 STRING 枚举默认校验为原生 ENUM 列：varchar 迁移列必须显式 columnDefinition 对齐（2026-08-26，第 144 次部署打包门禁拦截）

**背景**：specs/055 V1202 给 competitors 表加 `status VARCHAR(20)`（已应用到测试库，已发布迁移不可变），`Competitor` 实体用 `@Enumerated(EnumType.STRING) @Column(length = 20)` 映射。本地打包门禁 `FlywayMysqlContainerTest` 报 `Schema-validation: wrong column type ... found [varchar], but expecting [enum ('active','disabled')]`，4 errors，部署阻断。

**根因**：
1. **Hibernate 6.x 行为变更**：MySQL 方言对 `@Enumerated(EnumType.STRING)` 默认生成/校验**原生 ENUM 列类型**（`enum('active','disabled')`），不再是 Hibernate 5 时代的 varchar。
2. V1202 建的是 `VARCHAR(20)`，实体无 `columnDefinition` → 类型期望不一致。
3. `application-prod.yml` / `application-e2e.yml` / `application-flyway-mysql.yml` 三处均 `ddl-auto: validate` —— 这个 jar 若直接部署，后端启动即 crash-loop。

**修复**：单行改动，改实体对齐 DB 真相（不动数据库）：
```java
@Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
private CompetitorStatus status = CompetitorStatus.ACTIVE;
```

**教训**：
- **打包门禁容器测试是 crash-loop 的最后防线**：本次 P0 被 `package-release.sh` 内置的 FlywayMysqlContainerTest（Testcontainers 真实 MySQL 全量回放 B73~V1203 + validate）在本地拦截，未流到服务器。为赶部署跳过容器测试等于亲手拆掉防线（同 §163 tenderevent bean 冲突教训）。
- **Hibernate 5→6 升级后存量 STRING 枚举要普查**：grep `@Enumerated(EnumType.STRING)` 逐个核对底层列类型；对应 varchar 列的必须显式 `columnDefinition`，新表则 DDL 与实体口径二选一并全库统一。
- **类型冲突只能改实体侧**：禁止回改已发布迁移、禁止追加"修正列类型"的新迁移（会导致 e2e/flyway-mysql 回放与线上历史分叉）。

---

## 166. 实体落库层必须对外部/LLM输入做全字段非空与长度防御（2026-08-26，spec 041/047/055）

**背景**：在山焦物资采购项目（6 万字标书）解决 `original_total_weight` 溢出后，重新解析推进到 90%「评分项落库」时抛出 `could not execute statement [Column 'dim' cannot be null] [insert into score_item ...]`，前端轮询最终报"解析超时，请检查文件大小或稍后重试"。

**根因**：
1. **追症状不追根因**：第一轮修复仅修复了溢出报错的单点，未对数据管道下游实体映射（`score_item` 表的 `NOT NULL` 与长度约束）做全量防御性审计。
2. **外部输入非受控透传**：`ScoreGlobalMergeExecutor`（全局归并 LLM）输出可能返回 `dim = null`，直接透传给 `ScoreCandidate`，穿透至持久化层。
3. **单测未覆盖空维度**：单测构造的 candidate 均填写合法维度名称，测试假绿，掩盖了真实 LLM 输出缺少 `dim` 时的 NPE / 数据库完整性约束冲突。

**多层纵深防御治理**：
1. **LLM 归并映射层（`ScoreGlobalMergeExecutor`）**：`toCandidates` 中对 `item.dim` 增加 `resolveDim` 兜底，若为 null 或空白，回退到 `item.detail` 前 50 字或兜底 `"评分项"`。
2. **实体落库层（`ScoreItemPersistenceService`）**：持久化构造 `ScoreItem` 实体时对 `code`、`dim`、`detail` 增加非空与长度截断守卫（`code <= 50`，`dim <= 200` 非空，`detail` 非空），保证数据库写入 100% 满足物理 DDL 约束。

---

## 167. 须知前附表误入评分候选池：结构性排除必须发生在召回层而非依赖 LLM 自觉（2026-08-27，山焦物资标书，PR !2427）

**背景**：AI 评分标准解析对山焦物资采购项目（6 万字标书）产出大量伪评分项——开启程序、履约保证金、成交结果公示、采购预备会等采购程序条款混入候选池。

**根因**：
1. **表头词歧义性**：须知前附表是 `| 条款号 | 条款内容 | 编列内容 |` 三列表，正文行含"综合评分"/"综合得分"字样（如 6.2.2 推荐候选供应商排序行），命中 `TABLE_SCORE_PATTERN` 的通用"评分"子串即整表入池。
2. **过滤时机过晚**：结构性排除若只靠 prompt 软约束，LLM 输出不稳定，且 token 成本白白消耗在程序性文本上。

**三层防御治理（PR !2427）**：
1. **结构层硬过滤**：`MarkdownScoreSectionLocator.isProcedureNoticeTable` 按表格首行判别——含"编列内容"（前附表专有列名）且不含"评分因素/评分标准"列名即整体跳过；混合表头保留不跳过。判定理由唯一落在方法 Javadoc。
2. **候选提取 prompt 禁令**：新增 3 条硬性规则（前附表程序条款 / 形式·资格·响应性评审条款 / 总分构成汇总行均禁止提取），并有 contains 锚定测试。
3. **归并裁决丢弃指令**：全局归并硬性规则第 6 条——程序条款混入候选池归并时必须丢弃，同样有锚定。

**遗留盲区（评审 jshell 实证，技术债）**：
① 表格被空行截断后，续块首行无 marker 即绕过防护；
② 前附表与真实评分表纵向胶连（无空行相邻）时整块跳过会吞掉真实评分行；
③ 纯文本通道 `locatePlainChapterSections` 无此防护（对照 §151 纯文本一等输入的升级方向）。

**教训**：
- **能用廉价可靠的首行特征判定的内容类别，必须在进入 LLM 候选池之前挡掉**；下游模型自觉是最贵的过滤器。
- **prompt 硬性规则文字是关键行为契约，必须有测试锚定**（contains 断言），否则误删改静默失效——纵深防御不能一半有测试一半靠纸面。

---

## 168. 新增权限键必须同步 OSS 菜单映射源：架构守卫不在 pre-push 列表等于没有守卫（2026-08-27，specs/055 competitor）

**现象**：ce2b4b939（specs/055 竞品治理）注册了 `competitor.create/govern` 权限键（RoleProfileCatalog + Flyway V1203），但漏了在 `application.yml xiyu.integrations.organization.directory.menu-code-to-permission-key-mappings` 补来源。`PreAuthorizeAuthorityOssMappingCoverageTest` 因此在 main 基线上持续红（2 failures），且未阻塞该 PR 合入——因为该守卫测试不在 pre-push gate 的 mvn 测试清单里，只在全量 CI 才跑，而本仓库无远端 CI。

**根因链**：
1. **守卫盲区**：`check-test-entry-coverage.sh` 的 guard-pattern 扫描早就把它列进 ⚠ 提示清单（"14 个 guard-pattern 测试不在 pre-push 列表中，不阻断"）——提示型门禁对"main 已带伤"无拦截力；
2. **合入路径无全量测试**：Gitee 无远端 CI，合入质量靠本地 pre-commit/pre-push 门禁 + 开发者自觉；PR 分支跑定点测试集时天然看不到无关模块的守卫失败；
3. **权限键是三处联动契约**：RoleProfileCatalog（角色授予）/ Flyway（存量角色回填）/ yml 映射表（OSS 用户 authorities 来源）——改一处漏一处的典型多源头同步缺失。

**修复**（agent/gemini/fix-competitor-oss-mapping）：competitor.create→1003（创建入口在结题阶段项目流程内）、competitor.govern→1010（治理页挂 settings 分区）；同时把 `PreAuthorizeAuthorityOssMappingCoverageTest` 加入 pre-push-gate.sh 的 mvn 清单与逐项判定块。

**教训**：
- **新增 `@PreAuthorize(hasAuthority('xxx'))` 或 catalog 权限键时的 checklist：Catalog 授予 → Flyway 回填 → yml 映射源，三处缺一即 OSS 用户 403**；
- **守卫测试只有进入 push/CI 强制路径才算存在**——躺在 ⚠ 提示清单里的守卫等价于纸面防线（同 §167 prompt 硬性规则必须有 contains 锚定的结论一致）；
- **在无远端 CI 的仓库，一个模块的 PR 也要偶尔跑全量套件**——main 带伤往往正是从"PR 定点绿、基线悄悄红"开始的。

---

## 169. 表单数值字段初值 0 + blur 静默补零，让"显示假值"伪装成"列表与详情数据不一致"（2026-08-27，测试环境 /project/1365，PR !2436）

**背景**：用户反馈测试环境项目 1365 列表「计划入围供应商数量」列显示 `-`，但点进详情"有值"，疑似列表查询漏数。实测三层兜底链（ProjectMapper→立项表→评估表）代码正确，1366/1367/1368 反向验证均正常返回。

**根因**：
1. **数据源真相**：该项目是当天 17:27 测「关联标讯按钮→转立项」生成的半成品——标讯 2300 手动创建后仅预填了立项行（`createdBy=NULL, reviewStatus=DRAFT`），`expected_bidders` 与 `planned_shortlisted_count` 从未持久化过任何值；审计日志无后续保存记录。列表返回 null 显示 `-` 是正确行为。
2. **显示层造假（金额字段）**：`annualEcommerceAmount` / `customerRevenue` / `depositAmount` 初值写死 `0`，且存在配套 hack `handleAmountBlur：空/null → 强制回填 0`。
3. **显示层造假（计划入围供应商数量，报障主字段）**：`expectedBidders` **从未绑 blur**。假值来自初值 `0` + `el-input-number :min="1"`：Element Plus `verifyValue` 把 0 clamp 成 1 并 emit。叠加 `application.yml default-property-inclusion: non_null` 会省略 null 字段，`load()` 的 `Object.assign(form, data)` 写不进该 key，初值 0 一直留着被 clamp。**列表和详情读的是同一张表，真正的分裂发生在前端显示层。**

**修复（PR !2436）**：
1. 数值业务字段初值 `0→null`（expectedBidders/annualEcommerceAmount/annualRevenue/customerRevenue）；删除 focus 清零 / blur 补零这对 hack 及 defineExpose 暴露。
2. 同步删除 `autoFillFromTender` 中 `src.expectedBidders` 死兜底（tenders 表与 ProjectDTO 均无此字段，永不生效还误导排查）。
3. `depositAmount` 保持 0 默认：needDeposit='NO' 时输入框 v-if 隐藏不产生误导，且 ON-OFF 切换由 onDepositChange 显式管理。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 报障描述≠真实数据流："详情有值"先当 UI 观察、不当 DB 事实 | 表单受控组件把未保存/默认值渲染得和已保存数据一样 | 「列表 vs 详情不一致」类报障第一优先级：API 双端实测 + DB 直查三方对账，再定位层 |
| 用交互 hack（focus 清零/blur 补零）绕初始值设计缺陷 | 改初值怕影响提交类型校验，选择运行时打补丁 | 初值语义缺陷要在初值上根治（null 即空）；绕行 hack 会叠加出新的显示 bug 并长期潜伏 |
| 死兜底污染排查方向：`src.expectedBidders ?? ...` 看似有第四数据源 | autofill 映射从模板复制时未核对实体列是否存在 | 写跨实体映射前 grep 目标表列清单；死代码宁可立即删 |
| 回归只测 404 初值，锁不住生产路径 | Jackson `non_null` 省略字段 + InputNumber min clamp 才是已有立项的显示路径 | 回归必须覆盖「响应省略 key + 真 InputNumber min clamp」；stub 不绑 v-model 等于没测 clamp |

---

## 170. 外部对接定位键必须与数据签发方同源：先盘点实体入库链路，再定义接口定位语义（2026-08-27，接口六 CRM 联调 27 连 404）

**现象**：CRM 联调 `PUT /api/integration/projects/CRM/{sourceId}`，5 小时内换键盲试 27 次全部 404（试过商机编号 CC20260729796 / CC20260827846、纯数字 943），一次未通。全链路日志（SOP §23 Layer 2）显示认证、路由均正常，内部真实消息是 `标讯不存在: CRM:943`，被 M-03 脱敏策略统一出口为"请求的资源不存在"，调用方零上下文无法自诊断。

**根因链**：
1. **双链路数据形态不同源**：标讯进入平台有两条合法链路——P 链路（CRM 推送，`source_type=CRM_OPPORTUNITY`，external_id=`CRM:{bidId}`）与 M 链路（平台侧手工建+关联商机，`source_type=MANUAL_SINGLE`，仅写 `crm_opportunity_id` UNIQUE 列、external_id=NULL）。测试库里两群并存；
2. **接口六只认 external_id 单列**：初版按 `(sourceSystem, sourceId)` 复用推送幂等键定位——该键只有 P 链路数据才有。M 链路的存量数据对外部触达天然不可达；CRM 手里的商机编号更是第三种 ID 维度（bidId/chanceId/code 三轨）；
3. **契约断层放大故障**：对外正典文档停在 v3.10（无接口六章节），sourceId 定义为"来源系统的数据唯一 ID"，而内部 spec 037 已把推送链路 sourceId 锁定为 bidId——两句话从未调和；M-03 出口脱敏让服务端已知的真实原因（含正确键格式提示位）到不了调用方。

**修复**（agent/qoder/integration-project-tender-channel，对 !2409 的投产前契约修正）：单通道替换定位参数 sourceId → tenderId（平台标讯主键 Long，与幂等键解耦）；projectId 同传时一致性断言替代 externalId 比对；来源键前缀轻校验（手工标讯 externalId=null 跳过不误伤）；标讯存在但未立项改返回 200 + `applied=false` 跳过响应；`GlobalExceptionHandler.handleTypeMismatch` 重写把非数字路径参数拦成统一 400。

**教训**：
- **新增"按来源键定位"的外部接口前，必须盘点能把该实体带进系统的所有入库链路（API 推送/UI 手工/Excel 导入/迁移回填），逐条验证新接口可达性**——单测全绿只证明自己造的数据能走通，不证明真实半边业务可达（同七大根因"测试不测根因"）；
- **外部对接定位键优先用调用方手里有的键**：调用方持有内部主键时直接用主键（tenderId），没有主键时用其一等业务标识并保证库内可反查（crm_opportunity_id 带 UNIQUE 即天然安全反查键），禁止要求对方记住一个他们系统里看不到的平台内部字符串拼接格式；
- **三种以上 ID 并存的集成域必须在 wiki 里维护 ID 字典表**（谁是签发方/落在哪列/谁引用它），对接前发给对方——本次 ID 字典表事后才画出来，是排查成本的主要来源；
- **对外错误响应与安全脱敏要平衡**：404 全部统一为"请求的资源不存在"时，联调期至少应在文档/wiki 提供 404 分型说明或临时诊断手段（如按 traceId 查询的运维通道），避免把"追症状"制度化给外部团队。

---

## 171. Vue 3 `<script setup>` 内联 `h()` 函数式组件破坏 Scoped CSS 继承与引入过度封装（2026-08-28，工作台商机预测时间单元格）

**现象**：
在工作台 AI 商机预测表格中，为了给时间字段添加排版样式，开发者在 `<script setup>` 中编写了内联函数式组件 `const DateCell = (props, { slots }) => h('span', { class: 'op-date' }, ...)` 并在模板中调用 `<DateCell :value="row.bidOpenTime" />`。
随之产生的问题：
1. `<style scoped>` 中定义的 `.op-date` 样式完全失效，必须依赖 `:deep()` 或无作用域的类名去穿透修复；
2. 开发者在函数式组件内部做字符串拆分与拼接（年-月 / —日 分两行显示），造成日期（日）部分字体颜色变灰且字号不一，用户体验劣化；
3. 增加了不必要的 `h()` 依赖与伪组件 props 注册（`DateCell.props = ['value', 'emphasized']`）。

**根因**：
- Vue 3 SFC 编译 Scoped CSS 时，仅针对模板内部由编译器解析的 DOM 元素自动附加 `data-v-xxxx` 属性。
- 由 `<script setup>` 中 `h()` 运行时动态创建的 VNode 节点**无法获得父组件的 scoped 作用域属性**，导致 scoped 样式规则不匹配。
- 同时，微型内联组件模式属于过度设计（AI Slop），破坏了 Vue SFC 模板的声明式可读性。

**修复**：
- 彻底移除 `DateCell` 函数式组件，在 template 中直接使用标准插槽语法 `<span class="op-date">{{ formatDate(...) }}</span>`；
- 数据格式化由简洁纯函数 `formatDate(value)` 提供；
- Scoped CSS 恢复标准局部作用域规则，移除多余的 `:deep()` 穿透补丁。

**教训与规范**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| `<script setup>` 内联 `h()` 组件丢失 Scoped CSS | Vue 3 SFC scoped 属性注入只在模板编译期对模板 AST 生效 | 严禁在 `<script setup>` 中使用 `h()` 封装小组件，简单 UI 直接写 template 插槽 |
| 样式失效后使用 `:deep()` 或全局类盲目修补 | 未定位 scoped 属性丢失的根因，采用破坏边界的方式打补丁 | 遇到 Scoped CSS 不生效时，先排查元素是否带有 `data-v-xxxx` 属性 |
| 过度封装微型组件导致排版混乱 | 将格式化逻辑与 DOM 结构强行绑定在复杂组件中 | UI 展示与数据纯函数分离，保持模板简单明了 |

---

## 172. 选项模板复制导致伪枚举渗透：字段矩阵需对照蓝图真相，严禁模板化盲目添加"其他"（2026-08-28，商机预测与项目列表客户类型筛选）

**背景**：
用户反馈在工作台-商机预测与 /project 投标项目列表页面的筛选区中，「客户类型」下拉框中存在无意义的「其他」选项，要求将其移除，并排查引入历史以防复发。

**根因链**：
1. **源头模板复制渗透（PR !270，2026-06-07）**：
   开发者在新建 `useProjectSearch.js` 时，定义 `projectTypeOptions`（项目类型包含“其他”）后，直接复用模板定义 `customerTypeOptions`，顺手将 `{ value: 'OTHER', label: '其他' }` 复制进入客户类型筛选项；
2. **后端归一化盲目对齐（Commit 10037e44，2026-07-02）**：
   后续开发者修复客户类型筛选问题时，为使前后端字段矩阵 1:1 对齐，反向在后端 `InitiationFieldPolicy.CustomerType` 和映射表中补充了 `OTHER` 枚举，进一步固化了该多余选项；
3. **真相与各域分裂**：
   系统蓝图（蓝图 4.1.1.2、4.5）、标讯中心、数据分析以及业绩中心中，标准客户类型自始至终只有 5 类（政府机关/事业单位/高校、央企、地方国企、民企、港澳台及外企），唯独项目搜索层带上了“其他”。

**修复与防复发**：
1. 从 `useProjectSearch.js` 中彻底删除 `customerTypeOptions` 的 `OTHER` 选项，商机预测与项目列表自动同步生效；
2. 增加 `useProjectSearch.spec.js` 与 `OpportunityPredictionCard.spec.js` 回归测试，显式断言筛选项集合不包含“其他”，防止未来被模板化再次带入；
3. 展示层 `projectListFormatters.js` 保留对历史脏数据的容错兜底映射，避免存量数据渲染异常。

**教训与规范**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 模板化复制导致非标准枚举渗透 | 从同构字段（如项目类型）复制结构定义，未核实业务蓝图字段定义 | 新增/修改枚举与筛选项时，必须以蓝图和领域模型（唯一真相）为准，严禁随意添加通用"其他" |
| 修复对齐时将前端 bug 反向沉淀为后端枚举 | 看到前端有 6 个选项，误以为后端遗漏而反向补充 | 全链路对齐字段矩阵时，先确认业务真相是否合法，不能盲目双向兼容 |
| 缺乏筛选项集合锁定测试 | 仅测试筛选过滤逻辑，未锁定选项自身集合 | 核心业务枚举筛选项必须有断言数组内容与长度的单测锚定防复发 |



---

## 173. 外部接口的校验范围必须是契约开放字段集的子集：复用内部全量校验前先裁剪到调用方可控域（2026-08-28，接口六 CRM 首联 6 连 422）

**现象**：接口六（PUT /api/integration/projects/{sourceSystem}/{tenderId}）上测试环境后 CRM 首次对接即 `PUT .../CRM/2300` 6 连 422「缺少必填字段：ownerUserId,departmentSnapshot」，一次未成功。

**排查**（全链路日志排查 SOP §23 Layer 2，一次定位未走弯路）：nginx access.log 按 User-Agent `forest/1.6.3` 锁定调用 → ExceptionLogger WARN（M-03 脱敏前）抓到真实请求体——CRM 只传 `projectName + evaluationBasic（4 评估字段）+ evaluationCustomerInfos（1 行）` → 代码链路确认 `mergeInitiation` 对合并后全量跑 `InitiationFieldPolicy.validate` → DB 末端验证立项 1365（tender 2300 对应）`owner_user_id=NULL, department_snapshot=NULL, review_status=DRAFT, created_by=NULL`（正是 §169 记录过的 M 链路半成品立项）。

**根因链**：
1. **契约与校验集合错位**：接口六请求体（`ProjectUpdateRequest`/`EvaluationBasicDTO` + `toInitiationPatch`）只开放 6 个评估字段回写，`ownerUserId`/`departmentSnapshot` 等**内部组织字段根本没有传入入口**，patch 恒 null → `mergeForUpdate` 恒保留立项行原值 → 合并后这些字段 = DB 现状，**与调用方行为零相关**；
2. **复用 UI 全量校验未裁剪**：`mergeInitiation` Javadoc 自述「口径与 UI 立项 doUpdate 一致」，把 UI 全量表单保存的必填 Policy 原样套到外部部分回写（PATCH 语义）上——UI 全量保存要求必填合理，外部回写要求「合并后全量必填」等于要求调用方补齐它无法控制的字段；
3. **数据形态死锁**：M 链路手工标讯的预填立项行（半成品，§169）内部组织字段天然 NULL → CRM 无论怎么构造请求都必然 422。

**为什么测试没拦住**：25 个 Service 测试全绿，但 fixture 全是**完整态**立项（ownerUserId=5L、departmentSnapshot 有值）——「半成品立项 + 部分回写」这一真实数据形态从未进测试设计（§170「测试不测根因」同款：单测全绿只证明自己造的数据能走通）。

**修复**（方案 A，PR 待合）：`mergeInitiation` 删除合并后全量必填校验（保留 `validateUpdate` 字段锁校验），PATCH 语义贯彻——merge 的 null 保留特性保证回写不可能把完整立项改残缺；立项完整性由平台「提交审核」环节的同一 Policy 把守（链路语义：外部回写只管自己带来的字段，完整性在提交流程收口）。新增根因行为测试 `incompleteInitiationPartialWrite_succeeds`（半成品 fixture：ownerUserId/departmentSnapshot=NULL + 部分回写 → 200 落库）。

**教训**：
- **外部接口的校验范围必须是契约开放字段集的子集**——校验调用方无法影响的字段 = 制造与调用方行为无关的随机失败（本次 422 只取决于平台侧数据现状）；复用内部全量 Policy 前，先裁剪到契约域，或显式论证字段可得性；
- **PATCH 语义接口（非 null 覆盖/null 保留）不做合并后全量必填校验**——完整性守门放在「提交/审核」等收口动作上，而非每次部分更新；
- **fixture 必须覆盖真实数据的残缺形态**——联调环境里真实存在的半成品数据（§169 的 2300/1365）就是最好的测试素材；测试数据全用完整态 = 系统性盲区；
---

## 174. 压测多虚拟用户并发限流隔离与数据规范陷阱（2026-08-28，测试环境全链路压力测试，k6 压测实战）

**背景**：在测试环境执行 100 并发全链路压力测试时，初版 k6 脚本因所有虚拟用户（VU）共用单一管理员 Token，导致请求在执行第 100 笔后瞬间触发 `RateLimitFilter` 的单用户 100 req/min 限流规则（HTTP 429 Too Many Requests），产生高错误率假象；同时动态创建写入接口因地域缺少二级行政区（如"上海" vs "上海市上海市"）触发 400 参数校验失败。

**根因分析**：
1. **限流维度机制**：系统后端 `RateLimitFilter` 在用户已鉴权时优先按 `resolveUserRateLimitKey`（提取 JWT 中的 `user:username`）进行计数，默认阈值每分钟 100 次（`rate.limit.default.max: 100`）。压测工具若以 100 VU 共用同一 Admin Token 高频施压（70+ req/s），实质上是“100 个线程模拟同一个人”，在 1.5 秒内即打满单用户配额被拦截；
2. **登录端点暴力破解保护**：若将 `authenticate()` 放在 VU 循环内，每秒几十次高频请求 `/api/auth/login` 会触发 IP 级 15 分钟临时锁定（HTTP 429 Retry-After: 900s）；
3. **入库字段严校验**：标讯写入受 `@HeadquartersRegion` 严格约束（必须在 `TenderRegionCatalog` 白名单内，如 `广东省深圳市`、`上海市上海市`），且截止时间必须为未来时间（ISO 8601 格式）。

**解决方案**：
1. **虚拟用户隔离与 API Key 配额**：压测请求 Header 注入 `X-API-Key: stress_vu_${__VU}`，为每个并发 VU 分配独立的 1000 req/min 配额桶，精准模拟真实分布式多用户行为；
2. **前置鉴权 (setup lifecycle)**：利用 k6 的 `setup()` 生命周期在压测启动前完成一次性 Token 颁发，循环体内复用 Token，杜绝登录接口无效冲击；
3. **写入数据规范化与可清理性**：严格按 `TenderRegionCatalog` 组织一级+二级地名，动态生成带 `[压测动态]` 前缀及毫秒戳的数据，配套 `cleanup-stress-test-data.sql` 实现无污染回退。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 压测把正常限流当系统崩溃 | 多 VU 共用单个账号 Token 打满单用户 100 req/min 阈值 | 压测必须为每个 VU 分配独立虚拟身份/`X-API-Key`，真实模拟多用户并发 |
| VU 循环内调用登录被 429 锁死 | 每次迭代都去请求 `/api/auth/login` | 鉴权逻辑收敛到 `setup()` 钩子，循环体内只压测业务 API |
| 压测造数不合规触发 400 假死 | 忽视了 Bean Validation 对地域等字典字段的严格校验 | 造数前先看 DTO 约束（如 `@HeadquartersRegion`），确保 payload 完全合规 |

## 175. 禁用按钮必须给出原因；"上传成功但没保存"类工单先查请求是否发出（2026-08-28，标讯人工录入"未上传"假象）

**背景**：生产用户 03683 在 `/bidding/create` 人工录入标讯：上传 docx + AI 解析全部成功（后端全程 200），但标讯从未创建。用户感知"没报错但没成功、附件一直显示未上传"，在页面上卡了近 5 小时。三方证据交叉（Nginx access.log 全天无 `POST /api/tenders` + journalctl traceId 后端正常 + MySQL 创建记录为 0）确认浏览器从未发出创建请求。

**根因分析**：
1. **保存按钮静默禁用**：`:disabled="!canSave || parsingDocument"` 置灰无任何提示。公开谈判采购无开标时间 → AI 按口径留空 → canSave=false → 点击无反馈；
2. **平行定义漂移**：`canSave` 的必填字段口径（7 项）与 `MANUAL_FORM_RULES`（8 项，多 projectType）不一致——即使补齐 7 项，点保存仍被表单红字拦截，提示口径漏报；
3. **el-upload 状态假象**：`:auto-upload="false"` 时上传由自定义 handler 完成，但 el-upload 文件对象 `status` 恒为 `ready`，永远不显示绿色 ✓，用户误以为"未上传"；
4. **文案驱动控制流**：提示级别靠 `msg.includes('暂不可用')` 等关键字匹配决定 warning/success，改文案即静默降级。

**解决方案**：
1. 禁用按钮包 `el-tooltip` + `<span>`（disabled 按钮不触发鼠标事件必须包 span），展示"还差必填项：开标时间、客户类型"；
2. 必填口径收口单一模块 `tenderRequiredFields.js`（canSave / tooltip / AI 缺失提示三处共用），并与 `MANUAL_FORM_RULES` 对齐 + 口径契约测试锚定；
3. 附件回填 fileUrl 时显式置 `status='success'`（EP 源码确认 fileList 同引用深响应，安全）；
4. parseFn 返回 `{message, level}`，文案与控制流解耦。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| "没报错但没成功"工单先翻后端日志 | 排障从后端入手浪费 1 小时 | 先查 Nginx access.log 看目标 POST 是否发出，再决定查哪端 |
| 禁用按钮不给原因 | 用户点了没反应，无从自救 | 禁用必须配 tooltip/文案说明缺什么 |
| 同一概念两处定义 | canSave 与表单规则各自维护必填集，必然漂移 | 口径收口单一模块 + 契约测试；新增平行定义前先找既有真相源 |
| 消息文案当控制流开关 | 级别靠关键字匹配，文案一改行为就变 | 返回显式 `{message, level}` 结构 |

## 176. 外部接口字段名以实际响应为准：@JsonProperty 契约漂移靠 @JsonAlias 双兼容兜底（2026-08-28，测试环境 tender 4136 座机号丢失）

**背景**：测试环境项目 4136 关联商机 CC20260828857 后，评估表客户信息的 TEL（对接人座机号）始终为空。排查取双证据：journalctl 中 `CrmHttpClient` 响应体日志显示 CRM 实际返回 `"fixedPhone":"021-56478789"`（驼峰），而 DB `tender_evaluation_customer_info` 无 TEL 行。

**根因分析**：`ContactPersonInfoVO` 按接口文档声明 `@JsonProperty("fixed_phone")`（下划线），CRM 实际返回驼峰 `fixedPhone`。VO 上的 `@JsonIgnoreProperties(ignoreUnknown = true)`（CO-329 为容错加的）把驼峰 key 当未知字段静默丢弃 → `fixedPhone` 反序列化为 null → `CrmEvaluationMapper.addIfHasValue` 跳过 TEL 行。容错注解把"字段名不匹配"从显式报错降级成静默丢数据。且单测 mock 一律用下划线 key，掩盖了不匹配。

**解决方案**：`@JsonProperty("fixed_phone")` 旁加 `@JsonAlias("fixedPhone")` 双兼容两种字段名形态。`@JsonAlias` 只作用于反序列化，序列化输出 key 仍为 `fixed_phone`，前端消费契约不变。测试补驼峰（日志实锤形态）+ 下划线（文档契约形态）双用例。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 按文档写字段名，不看实际响应 | 文档契约与线上实现漂移无人发现 | 集成类 VO 字段用真实抓包/日志响应写测试，不抄文档 |
| ignoreUnknown 吞掉字段名不匹配 | 容错注解把显式错误变成静默 null | 外部接口字段丢失类工单，直接翻 `CrmHttpClient` 响应体日志比对 key |
| 单测 mock 与文档同源 | mock 用下划线 key 恰好绕开不匹配 | 至少一条用例必须来自真实环境日志原文 |

## 177. 外部系统对接禁止绕过官方 SDK 手写 HTTP POST 组装私有契约（2026-08-28，西域事件库 BidTenderChange 契约漂移）

**背景**：标讯创建向西域事件总线推送 `BidTenderChange` 事件，服务端已收到请求（网关返回 `code=00000`），但在下游 CRM 消费端日志中，事件载荷 `data` 显示为双重转义字符串 `"{\"tenderId\":2311}"`，导致 CRM 端解析失败。排查代码发现，虽然 `TenderEventSdkProducer` 类注释声明使用 SDK 标准通道 `SystemInteractiveService#sendEvent`，但底层实现 `TenderEventSdkSender` 实际上绕过了 SDK API，手工组装了 8 字段 JSON body 并直接调用底层 `HttpClientUtil.httpPost` 发起请求。

**根因分析**：
1. **多轮补丁导致契约漂移（元模式：修症状不修根因）**：在历史修复链中，为解决"网关传对象报 400"和"需补 key/time"等表象问题，多次手工修改 HTTP 请求体并绕过 SDK 封装，导致发出的消息格式与西域事件库下游消费端的反序列化契约错位；
2. **注释与代码实现分离**：Producer 的 JavaDoc 宣称使用了 SDK `sendEvent`，但底层 `Sender` 却手写了 HTTP POST，给后续接手者造成误导；
3. **code:0 谎言**：网关受理成功（HTTP 200 / `code=00000`）只代表网关收到了请求，并不代表消息能被下游 CRM 正常消费，排查必须溯源到消费端真实日志。

**解决方案**：
1. 废弃 `TenderEventSdkSender` 中的手写 HTTP POST / 自定义 8 字段拼装逻辑；
2. 彻底回归使用客户 ClientSDK 官方接口 `SystemInteractiveService.sendEvent(eventCode, eventSource, eventContent, eventTrackReq)`；
3. 修正 wire 契约测试与单元测试，锁定 SDK 标准 4 字段 body（`serviceName`, `eventTopic`, `eventSource`, `data`）+ Trace Header 规范。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 官方 SDK 存在却手写 HTTP POST 拼装请求 | 追逐单点报错打补丁，绕过 SDK 封装 | 凡有官方 SDK 的外部集成，必须使用 SDK 统一入口，禁止私自拼装 HTTP 请求 |
| 注释宣称使用 SDK，代码实际绕过 | 架构重构不彻底，改动未闭环 | Code Review 严格检查类注释与方法调用的真实实现是否一致 |
| 看到网关 HTTP 200 即认为修复 | 忽略下游消费链路（code:0 谎言） | 事件类集成必须验证到最终消费者业务 Handler 的执行结果 |

## 178. 招标文件评分章节整段捕获与多层级评分项叶子节点细则提取（2026-08-28，Project 1347 & 1362 评分解析漏项）

**背景**：
在真实客户招标文件解析中（如 Project 1347 中国雅江、Project 1362 河南能源），用户反馈 1347 多标包混排采购文件解析完全错误（只提取出 3 个粗粒度大项 66分/7分/27分，丢失全部 11 项真实评分细则），1362 第一项（公司财务状况 10分）下丢失了营业收入 (2.5分) 与现金流 (2.5分)，且评分项名称粗暴归为"公司财务状况"。

**根因分析**：
1. **MarkItDown 转换 PDF 导致表格碎裂与文本混排**：PDF 转换为 Markdown 时，跨页、多合并单元格的表格经常被拆碎为纯文本段落（如`条款号 评分因素 评分标准 权重`）而非连续的 `|` 管道表格。原 `MarkdownScoreSectionLocator` 强依赖 `isTableLine`（`|` 开头），非管道行立即截断，导致 1347 的 130 行真实评分细则在第一阶段被 100% 丢弃！
2. **多层级表格权重提取优先级倒置**：原 `ScoreTableRowParser` 优先取列 1 的括号分值（如"公司财务状况（10分）"），导致叶子项的实际满分（如最右侧列的 2.5分）被父大类总分覆盖；且未将评分因素（注册资本金、营业收入）作为具体 `dim` 提取。

**解决方案**：
1. **章节优先定位器（Chapter-First Range Locator）**：按大章节标题（`第X章 评审办法`、`详细评审表`）直接整段扫描至下一大章节，不受碎片表格或非管道文本影响，保障完整上下文送入归并池；
2. **多层级叶子分值优先与因素层级化**：`ScoreTableRowParser` 与 Prompt 明确优先提取最右侧纯分值列（2.5分）作为叶子分值，并将具体评分因素与大类维度解耦；
3. **多标包隔离与排除汇总行**：Prompt 明确多标包场景下仅提取综合评分法标包的评分项，严格剔除最低价法等非评分标包的程序性文字与纯大类总览行。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 假设 Markdown 表格必定为标准管道符行 | PDF 转 Markdown 跨页表格常被切碎为散落文本 | 评分章节定位必须按整章/整段范围捕获，禁止按连续 `|` 单行切片 |
| 父分类分值覆盖叶子项分值 | 取括号分值优先级高于最右侧纯分值列 | 评分表格解析必须优先以末级叶子分值列为准，分类名称与因素名称分层展示 |
| 多标包混排误提程序条款 | 未隔离综合评分法标包与最低价法标包 | 全局归并层与 Prompt 必须明确标包隔离与末级叶子项提取规则 |

## 179. 评分标准解析边界陷阱：复合编号排序沉底、% 百分比权重漏识与长文本过渡截断（2026-08-28，Project 1347 验收缺陷）

**背景**：
在 PR !2459 部署后，用户核对 Project 1347 时反馈三个明显缺陷：
1. 编号 `2.2.4(1)` 的"响应文件的完整性"被排在表格最后一行，而非首行；
2. 财务状况（6% 权重）在表格中完全丢失；
3. "平台供应商质量监管能力"长文本段落中间内容被丢弃截断。

**根因分析**：
1. **排序策略仅支持简单编号**：`ScoreItemOrderPolicy` 正则仅匹配 `^(\d+)(?:[-—.](\d+))?$`（如 `4-1`），对 `2.2.4(1)` 这类带括号或多级小标的复合编号无法提取排序键，直接判定为 `unparsed` 稳定追加到已排序列表之后，导致首项沉底；
2. **权重正则遗漏百分比符号**：`ScoreTableRowParser.PURE_WEIGHT_COLUMN` 仅匹配 `分` 结尾，对 `6%` / `10%` 等百分比权重列无法识别为分值列，导致三列表格无法提取正确权重而失效；
3. **章节定位截断阈值过窄**：`MarkdownScoreSectionLocator` 设定连续 10 行非评分行即中断章节扫描。MarkItDown 将长单元格或分档细则转换为多行连续普通文本时极易超过 10 行无关键词，导致后半段及后续评分表被直接截断丢弃。

**解决方案**：
1. `ScoreItemOrderPolicy` 增加 `COMPOUND_CODE_PATTERN`，提取首段整数与括号内子段复合排序键，保留复合编号在文档中的自然顺序；
2. `ScoreTableRowParser.PURE_WEIGHT_COLUMN` 扩展为支持 `(?:分|%)?`，正确识别百分比分值；
3. `MarkdownScoreSectionLocator` 将连续非评分行容忍阈值由 10 提高到 20，并引入 `GRADING_PATTERN` 识别分档/评分等级语义行，防止长文本单元格被误切。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 复合编号排序沉底 | 编号解析器做强格式假设，不支持 `2.2.4(1)` / `3.1(2)` 格式 | 排序策略需覆盖复合编号模式，无法解析时应保留原始文档顺序而非粗暴沉底 |
| 百分比权重列被忽略 | 权重匹配器硬编码只认"分" | 权重匹配需支持 `分`、`%`、纯数值等多种表达 |
| 长文本描述行截断章节 | 扫描终止计数器仅按局部行关键词判断 | 扩大过渡行容差，并识别"分档打分"等非关键词型评分语义 |

## 180. 评分解析从"一标一改"到"2D网格锚定与金标防回退"架构演进（2026-08-28，Spec 058）

**背景**：
在过去数十个 PR 的迭代中，评分解析陷入了"纯大模型容易幻觉与漏项 $\rightarrow$ 增加 Java 正则强行提取 $\rightarrow$ 新文件变异导致正则失效 $\rightarrow$ 再加正则导致历史用例冲突"的恶性循环。

**根因分析**：
1. **纯大模型方案缺陷**：大模型自由生成容易产生概括缩写（破坏法律级逐字保真）、长表格随机漏项、将总分/公式误提取为独立幽灵项；
2. **纯正则方案缺陷**：将 2D 表格强行拍平成 1D 字符串流后，试图用单行正则猜测列索引和分值，对复合编号、百分比权重、合并单元格、换行文本极其脆弱。

**解决方案（单元格锚定抽取 + 金标基准锁）**：
1. **降维定位而不是生成**：LLM 仅负责分析表格前几行推断列语义属性（`TableColumnMapping`），不参与任何细则文本生成；
2. **代码层物理单元格直抽**：`CellAnchoredScoreExtractor` 按识别的列坐标直接读取物理单元格原文，100% 逐字保真、0 幻觉、天然保持物理行自然顺序；
3. **类型交叉验真防线**：`ColumnTypeCrossVerifier` 检验权重列数值比例与细则列长度，杜绝全表列错位；
4. **金标回归基线（Golden Baseline）**：将 1347、1362、212 等标书固化为确定性金标用例（`GoldenCasesReplayTest`），任何修改必须达到 F1=1.0 且全绿，构筑不可突破的防回退底线。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 试图用单行正则替代 2D 空间理解 | 表格被拍平为纯文本流丢失网格空间关系 | 引入 `TableGrid` 保持二维矩阵，自动完成合并单元格 Fill-down |
| 让大模型生成最终评分细则 | 模型倾向于概括润色导致法律级保真度受损 | 大模型只做列语义分类，文本 100% 由代码引擎从单元格直抽 |
| 修复新格式导致历史文件回退 | 缺乏端到端确定性全量文件回归集 | 建设 `GoldenCasesReplayTest` 金标套件，纳入 pre-push 门禁硬拦截 |

## 181. 评审程序条款（如"推荐成交候选人3名"）被误当评分项导致权重归一化稀释（2026-08-28）

**现象**：
Project 1347 解析结果中出现了 `4.4.1 推荐成交候选供应 3`，同时原本 50% 权重的价格分被稀释为 48 分，其他 5% 权重被稀释为 4.8 分（四舍五入显示 5 分）。

**根因分析**：
1. **程序性条款混入**：标书第 4 章评审程序前附表中包含 `4.4.1 推荐成交候选供应商的顺序: 评审小组按合格供应商的最终报价由低到高顺序推荐 3 名成交候选供应商`。表格末列为数字 `3`（名），被表格抽取器误识别为分值 `3分`；
2. **归一化等比稀释**：多出的 3 分幽灵项导致原始权重合计达到 103+，`WeightNormalizationPolicy` 在进行 100 分归一化缩放时，将 50% 的价格分等比缩减为 `50 * (100 / 104) = 48.07`（四舍五入为 48 分），导致全部评分项权重失真。

**解决方案**：
1. **程序条款多层拦截**：在 `CellAnchoredScoreExtractor`、`ScoreTableRowParser` 和 `GhostCandidateFilter` 中建立统一的 `NON_SCORE_PROCEDURE_PATTERN` 拦截器（过滤 `推荐成交候选|成交候选|定标|保证金|监督` 等），并阻断以 `4.` 开头的程序性章节编号；
2. **金标回归覆盖**：将包含真实 11 项评分项及第 4 节程序条款表格的标书固化为 `case-1347-compound-percent` 金标用例，断言 `4.4.1` 100% 滤除、价格分准确为 50、总分 100。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 程序条款中的数量数字被当分值 | 前附表包含"推荐候选人3名"等带数字的程序条款 | 建立严格的非评分程序条款关键词过滤，阻断程序章节 |
| 权重总分被幽灵项带偏 | 归一化策略对池内所有候选一视同仁等比缩放 | 在进入归一化前必须经过程序条款守卫滤净非评分项 |

## 182. 多行混排无表格线文本中相邻评分项权重颠倒串行（如信用评价误配6%）（2026-08-28）

**现象**：
Project 1347 解析结果中，"商务部分评分标准/信用评价"被赋予了 6% 权重，而原本标书中信用评价为 5%、财务状况为 6%，两者分值发生了相邻项颠倒错位。

**根因分析**：
在 PDF 转换后未生成标准 Markdown 管道表格（`| ... |`）的多行文本中，各评分因素名称、细则与权重数字分散在不同行，大模型在阅读多行混排文本时，将后文财务状况段落附近的 `6%` 错配给了前文的信用评价项，导致信用评价 6%、财务状况 5% 的反向颠倒，并进而影响了后续评分项的比重平衡。

**解决方案**：
1. **Prompt 权重精准绑定防线**：在 `ScoreParsePrompts.buildCandidateExtractionPrompt` 和 `buildGlobalMergePrompt` 中建立严格的"权重与评分因素一对一就近绑定与防串行错位"规则，明确指示逐项核对原文真实权重，严禁相邻项权重颠倒借用；
2. **金标基准固化**：在 `case-1347-compound-percent` 中严格断言信用评价为 5%、财务状况为 6%、完整性为 4%、业绩为 5%、价格为 50%、各项技术能力为 5%，总分 100%。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 无表格线混排文本导致权重错配 | 文本转换未生成管道符导致行列对应关系模糊 | 提示词层强制要求权重与所属评分项一对一精准绑定与验真 |
## 183. 并列能力与方案评分项（如运营服务能力5%）在文本穿插中遗漏导致总分缺额与权重拼凑放大（2026-08-28）

**现象**：
Project 1347 解析结果中遗漏了"技术部分评分标准/运营服务能力"（包含商品运营方案、新增商品需求报价响应方案等内容，5% 权重），导致技术部分 6 项只提取了 5 项（合计 25%），总分仅为 95 分，下游归一化策略在缩放补齐 100 分时通过最大余数法将差额拼凑分摊，导致信用评价（5 分变 6 分）等其他评分项权重失真。

**根因分析**：
1. **多列展平排版穿插**：在 OCR/MarkItDown 转换无边框表格时，第二列的列名与分值 `运营服务能力 5%` 被穿插在第三列细则长句中间（`...新增商品需求报 运营服务能力 5% 价响应方案...`）；由于 6 项技术能力均共用相同的档位打分句（`评审标准按A、B、C、D分档打分。`），大模型在提取时若缺少并列项完整性约束，容易忽略穿插出现的列名而漏提该项；
2. **缺额触发归一化余数分摊（拼凑权重）**：漏提 5 分导致原始总分只有 95 分，`WeightNormalizationPolicy` 启动按比例缩放（scale = 100/95 ≈ 1.0526）并使用最大余数法分发剩余额度，将 3 分余数分配给价格、财务状况与信用评价，使信用评价从 5% 变成 6%，产生拼凑失真。

**解决方案**：
1. **候选提取 Prompt 强化穿插识别**：在 `buildCandidateExtractionPrompt` 中增加"并列能力与方案评分项完整提取（防穿插遗漏）"硬性规则，明确指示即使列标题与分值穿插在描述文本中间，也必须完整识别并提取每一项；
2. **完整性回补 Prompt 增加大项分值闭环复查**：在 `buildGapRecheckPrompt` 中增加大项分值和与子项完整性复查，明确大项（如技术部分 30%）若子项分值和不足大项总分（如只有 5 个 5% 子项），必须在原文中逐段复查回补遗漏的并列子项；
3. **全局归并 Prompt 建立分值闭环规则**：在 `buildGlobalMergePrompt` 中建立规则 11"并列评价细则与分值闭环完整性"，确保 6 项技术能力全部保留、总分闭环 100%，杜绝下游因缺项产生权重拼凑。

## 184. 数据分析多维度趋势部门两级筛选误把组织架构作为一级（虚拟根未解包）导致真实二级被裁剪（2026-08-29）

**现象**：
数据分析-多维度趋势分析页面中，部门筛选下拉只展示"组织架构"及其直接子部门（真实一级部门），而真实业务二级部门（如华东区、销售一部等）在下拉树中完全消失，且后台 X 轴分组将真实一级部门按二级处理。

**根因分析**：
OSS 组织机构同步表（`organization_departments`）包含顶层虚拟根容器（`department_code = 'rootorg'`，`department_name = '组织架构'`，`parent_department_code = null`）。业务一级部门在数据库中的父节点指向 `rootorg`。此前两级截断逻辑直接从数据库物理根构建树，将"组织架构"当作 Level 1，真实业务一级被当作 Level 2，导致真实的业务二级部门被判定为 Level 3 并在两级截断（`pruneFilterTree(roots, 1)`）时被错误裁掉。

**解决方案**：
1. **统一识别并解包虚拟根**：在 `FilterOptionsQueryService` 中提炼 `findVirtualRootCodes`，识别 `rootorg`/`root` 及顶层"组织架构"节点；
2. **前台树与后台层级对齐**：在 `fetchDepartmentTree()` 组装前台树与 `loadDepartmentNodes()` 构建后台深度计算节点时，统一跳过虚拟根节点并将直接子部门提升为 Level 1 根节点；
3. **测试锁定**：在 `FilterOptionsDepartmentTreeTest` 中新增包含虚拟根的完整层级测试用例，确保前后台两级语义一致。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 组织表物理顶层包含虚拟容器 | 外部系统（如 OSS）导出的组织数据通常包含全量根目录容器节点（如"组织架构"） | 在消费组织树进行层级裁剪或业务分组前，必须先解包（Unwrap）虚拟根节点，以业务实际一级作为根 |
| 前台展示与后台深度计算口径脱节 | 前台树组装与后台 `levelOf` 深度计算若分开处理不同步，容易导致前后台层级判定不一致 | 保证数据提取层（`FilterOptionsQueryService`）提供统一的一级解包数据源，让前后台使用相同的层级基准 |
## 185. LLM 裁决响应解析过严导致"响应结构非法"技术文案直接暴露给用户（2026-08-30）

**现象**：
AI 标书质量核查中，语义项检查结论出现"LLM 响应结构非法，无法解析裁决结果"这类技术性文案，用户无法获得有意义的检查结论。

**根因分析**：
`LlmVerdictParser.parse` 直接 `readTree(原始响应)`，四类常见模型输出均会导致整体解析失败：① 用 ```json 代码围栏包裹；② JSON 前后附加解释文字；③ 返回中文状态（通过/不通过/待确认）被三值校验拒绝；④ MANUAL 且 conclusion 为空被整体拒绝。失败后置 Invalid，兜底文案又把技术细节直接展示给用户。

**解决方案（三层防御）**：
1. **解析器容错**（`LlmVerdictParser`）：配平状态机扫描提取首个完整 JSON 对象（容忍围栏与前后缀文字）；status 大写化 + 中文同义归一（通过→PASS/不通过→FAIL/待人工→MANUAL）；MANUAL 缺 conclusion 兜底默认文案；
2. **边界重试**（`BidQualityCheckAsyncExecutor.buildLlmPort`）：仅解析失败追加"仅输出 JSON"强约束重试 1 次（`orElseGet` 惰性链，Noop/Failure 不重试，不放大调用异常）；
3. **可读兜底**（`LlmAdjudicator`）：Invalid/Failure 路径结论改为"AI 本次未能自动裁决该条款（返回结果异常/调用异常），已转人工确认，请对照检查内容人工复核"，技术细节仅留日志。
4. **历史快照兼容**：报告读取侧 `toReportDTO` 按 itemCode 回填标准名称/说明（`withCanonicalDefinition`），无需数据迁移。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 把 LLM 输出当严格 JSON 直接解析 | 模型输出天然带围栏/解释文字/语言变体，严格解析失败率不可控 | 解析层必须容错：配平提取首个 JSON 对象 + 字段归一化 + 空值兜底 |
| 技术性错误文案直达用户 | 兜底文案照搬内部异常描述 | 纯核心兜底结论必须用户可读，技术细节只进日志 |
| 解析失败即降级无重试 | 首次失败多因格式问题而非能力问题 | 边界适配器对解析失败追加格式强约束重试 1 次，调用异常不重试 |

## 186. AI 标书质量核查前缀硬截断致使长标书中后段失明：全标书 RAG 语义分页检索与结构化输出通道演进（2026-08-30）

**现象**：
AI 标书质量核查中，长标书（超过十几页、数万字）的语义检查项（如评分项响应、前附表关键条款、偏离表响应、资质证明等）频繁被判定为"待人工确认"（MANUAL），AI 未能识别出标书中后段已经做出的明确响应。

**根因分析**：
1. **前缀硬截断盲区**：`LlmAdjudicator.buildPrompt` 中硬编码了 `BID_EXCERPT_BUDGET = 6000` 与 `TENDER_EXCERPT_BUDGET = 4000`，通过 `substring(0, budget)` 简单截取开头。一份几十页的标书动辄几万到几十万字，技术方案、偏离表、后段资质附录等关键章节全部落在 6000 字符之后，大模型因输入缺失无法分析，只能降级为人工复核；
2. **格式解析脆弱性根因**：大模型裁决采用裸文本 `chatCompletion` 调用，依靠提示词约定 JSON 结构，天然容易受围栏和解释文字干扰；虽然前期通过容错解析机制进行了缓解，但消灭解析失败的根本方案是切换为底层网关的 `response_format: {"type": "json_object"}` 结构化输出通道。

**解决方案**：
1. **全标书 RAG 语义分页检索器（`SemanticPageRetriever`）**：
   - 彻底废除 `substring` 硬截断，提升字符预算至 60,000（标书）/ 30,000（招标要求）；
   - 预算内文档 100% 全文无损透传；超预算时按 7 项语义检查项特征（标题命中 +50、关键词命中 +10、词频 +2）进行多因子打分并检索 TOP-N 页面，按自然物理页码升序还原组装；
   - 组装过程保留 `--- [第 X 页] ---` 物理页码标识，确保 AI 生成结论能精确引用真实页码；
2. **结构化输出通道与原子降级标记（`OpenAiCompatibleClient`）**：
   - `AiProvider` 接口扩展 `chatCompletionJsonObject` 结构化请求方法；
   - `OpenAiCompatibleClient` 请求体注入 `"response_format": {"type": "json_object"}`；遇到部分网关不支持 400 报错时，通过 `AtomicBoolean` 一次性原子标记降级为普通调用，免除后续请求双倍网络重试；
   - `BidQualityCheckAsyncExecutor.buildLlmPort` 切换走结构化输出通道，并完整保留 `LlmVerdictParser` 作为末道容错防线；
3. **真实 Fixture 与全链路验收**：
   - 构造 60 页 39,500+ 字符测试夹具 `BidDocumentTestFixtures` 与全链路验收测试 `FullDocumentSemanticRetrievalAcceptanceTest`，逐项断言命中物理偏移量 > 28,000 字符的中后段真实内容。

## 187. AI 评分标准解析大模型长文本输出致数据库字段截断异常：全链路长度安全截断与双层防御加固（2026-08-31）

**现象**：
Project 36 评分标准解析任务在 90% 评分项落库阶段崩溃失败，错误日志：`Data truncation: Data too long for column 'location' at row 1`，导致任务整批回滚，用户端表现为“一直在转圈/无解析结果”。

**根因分析**：
1. **大模型输出长度不确定性**：LLM 结构化提取评分项的 `location` 定位字段时，输出了详细的多段解释文字（超过 200 字符）；
2. **Mapper 边界缺乏防御截断**：`ScoreCandidateMapper.fromLlmCandidate` 仅对 `sourceText` 做了 500 字符截断，对 `location`、`dim`、`code` 未做安全截断；
3. **数据库字段强约束引发硬报错**：MySQL 严格模式下，`score_item.location VARCHAR(200)` 写入溢出抛出 `DataTruncationException`，使 Spring `@Transactional` 事务标记 rollback-only，最终任务置为 FAILED。

**解决方案**：
1. **源头映射安全收敛**（`ScoreCandidateMapper`）：
   - 提取并统一 `LOCATION_MAX_CHARS(200)`、`CODE_MAX_CHARS(50)`、`DIM_MAX_CHARS(200)`、`SOURCE_TEXT_MAX_CHARS(500)` 四大常量；
   - 纯函数 `truncate(String, int)` 对 `fromLlmCandidate` 与 `fromCriterion` 入参做源头截断；
2. **持久化层双层防御**（`ScoreItemPersistenceService`）：
   - 新增 `resolveLocation` 兜底方法，落库前对 `candidate.location()` 二次截断，彻底阻断数据库截断异常；
3. **任务状态阶段描述保护**（`ScoreParseTaskStateService`）：
   - `updateProgress` 中对 `stage` 描述增加 50 字符长度限制（`VARCHAR(50)`）。

**教训**：
## 188. AI 评分标准解析误采合同履约考核表与外部系统评分声明诊断机制（2026-08-31）

**现象**：
Project 36（中国一汽 MRO 项目）招标文件正文中未附带评分细则，而是明确声明 `2.2 详细评审标准：详见 ZBJ 格式采购文件“评审项目”页签`。然而解析器却将文末合同附件《供应商季度/年度合同履约考核表》（总分 95 分）误识别为评标打分项，且由于权重归一化策略（`WeightNormalizationPolicy`）无条件将 95 分按比例等比放大并分配余数强行凑成 100 分，导致向用户展示了错误的评分标准。

**根因分析**：
1. **合同履约考核表与招标评分表语义混淆**：文末合同附件包含《供应商履约考核表》（考核项目、考核内容、分值，如周期保证 25 分、质量保证 25 分等，总分 95 分）。表格解析器（`ScoreTableBlockLocator`、`ScoreTableRowParser`、`CellAnchoredScoreExtractor`、`HeuristicTableColumnMapper`）与 `GhostCandidateFilter` 仅拦截了“须知前附表程序性条款”，未对“合同管理/违规扣款/供应商日常考核/履约考核”等合同执行期评价表进行过滤，导致表格直抽召回了 9 个非评分项；
2. **外部系统评分声明未做显式诊断拦截**：现代电子招投标（如中国一汽、中招联合等）常将详细评分标准放置在专用软件（如 ZBJ/电子交易平台系统页签）中，标书正文仅作声明引用。定位器未识别该声明，误跨章节扫描了文末附件表格；
3. **权重归一化与缺陷回补机制的短路穿透**：当提取候选总权重为 95 分（<100）时，`WeightSumCheck` 触发了 `recheckGaps`，但因全文无其他评分项返回空；随后 `ScoreCandidatePostProcessor` 无条件调用了 `WeightNormalizationPolicy.normalize()`，将 95 分强制缩放为 100 分，掩盖了解析结果与实际评分体系不符的问题。

**解决方案（多层防御）**：
1. **五层召回与过滤通道全面拦截非评分考核表**：
   - `GhostCandidateFilter` & `ScoreTableRowParser` & `CellAnchoredScoreExtractor`：扩展 `NON_SCORE_PROCEDURE_PATTERN` 拦截 `考核项目|考核内容|履约考核|供应商考核|季度考核|年度考核|违约责任|违约金|违规行为判定|违约责任承担|判定依据|扣款细则|履约评价|日常考核|供应商评价|考核细则|扣款标准`；
   - `ScoreTableBlockLocator` & `MarkdownScoreSectionLocator`：在表头拦截与章节扫描中加入 `CONTRACT_ASSESSMENT_HEADER` 与附件章节截断；
   - `HeuristicTableColumnMapper`：若表头仅包含考核项目/考核内容/违约金等字段且无评分标准字段，直接标记全表行数跳过抽取；
2. **外部交易系统评分声明识别与用户友好诊断失败提示**：
   - `MarkdownScoreSectionLocator` 提供 `hasExternalSoftwareScoreDeclaration(markdown)` 识别 `详见...ZBJ/电子交易平台“评审项目”页签`；
   - `ScoreCandidatePostProcessor` 在有效候选数为 0 时，若检测到外部系统评分声明，精准抛出 `"招标文件明确说明评分标准详见 ZBJ 格式采购文件或电子交易平台“评审项目”页签，正文中未包含可解析的评分细则"`；
3. **LLM Prompt 边界契约同步**：
   - `ScoreParsePrompts.buildCandidateExtractionPrompt` 增加硬性禁令：明确禁止提取合同履约考核表与违约扣款细则，禁止从附件考核表中拼凑替代外部系统评分项。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 误将履约考核/违约金表当作评标评分表 | 看到"考核项目/分值"便以为是打分表，忽视了评标期与履约期表格的本质区别 | 表格抽取、行解析、网格映射、幽灵过滤全链路注入履约考核/违约责任表拦截规则 |
| 外部系统软件评分无对应细则导致误扫附件 | 标书正文声明“详见ZBJ/电子系统页签”，定位器扫描未在正文处截断而向后误扫 | 识别外部系统评分声明，当正文无评分表时输出准确诊断，避免向下游污染 |
| 权重归一化掩盖错误解析 | 当总分未达 100 且无法回补时，归一化算法强制乘系数分配余数凑满 100 分 | 结合前置拦截，当非评分表被过滤为 0 项时触发空项失败中断，禁止伪造满分 |

---

## 189. AI 标书质量核查异步线程池显式绑定与事务提交解耦加固（2026-08-31）

**现象**：
西域测试环境用户在项目详情标书编制阶段点击【AI 标书质量核查】时，前端弹出 `请求超时，请检查网络后重试`。

**根因分析**：
1. **`@Async` 遗漏线程池 Bean 名称**：`BidQualityCheckAsyncExecutor` 仅标注了无参 `@Async`。系统中存在 8 个专用 `TaskExecutor` Bean 且无默认 `taskExecutor`。在某些调度或负载情况下可能出现线程池解析歧义，若发生 `CallerRunsPolicy`，长达 60~180s 的重 I/O 与 LLM 裁决在 Tomcat 主请求线程同步执行，直接击穿前端 Axios 30s 全局超时；
2. **事务未提交即触发异步执行的竞态**：`triggerQualityCheck` 为 `@Transactional` 方法，在 `save(taskEntity)` 后立即同步调用 `asyncExecutor.executeAsync`。若后台异步线程在主事务尚未提交前启动，并在独立事务（`Propagation.REQUIRES_NEW`）中调用 `updateProgress`，可能读不到尚未提交的主任务实体。

**解决方案**：
1. **显式绑定专用 AI 线程池**（`BidQualityCheckAsyncExecutor`）：
   - 将 `@Async` 明确指定为 `@Async("aiTaskExecutor")`，与系统其他 AI 异步服务标准对齐；
2. **事务提交后安全触发异步执行**（`BidQualityCheckTaskAppService`）：
   - 使用 `TransactionSynchronizationManager.isSynchronizationActive()` 检查当前事务上下文；
   - 在活动事务下通过 `registerSynchronization` 的 `afterCommit` 钩子安全触发异步流水线，消除事务可见性竞态；非活动事务下保持直接执行。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 多 Executor 容器下使用无参 `@Async` | 默认依赖 Spring 解析，忽视了无 `@Primary` 时的调度歧义与 CallerRuns 阻塞 HTTP 线程风险 | 系统中存在多个线程池时，所有 `@Async` 必须显式指定目标 Executor 名称（如 `@Async("aiTaskExecutor")`） |
| `@Transactional` 方法内直接触发异步线程 | 异步线程在主事务提交前启动，导致异步线程内的独立事务读不到未提交数据 | 使用 `TransactionSynchronizationManager` 注册 `afterCommit` 钩子，确保数据 100% 物理落库后再触发异步作业 |

---

## 190. AI 标书质量核查前端轮询超时放宽与长任务状态隔离（2026-08-31）

**现象**：
西域测试环境在执行【AI 标书质量核查】时，前端在等待过程中弹出 `核查任务超时，请稍后刷新重试`；同时抽屉下方却已经加载出了完整的 25 项核查报告（核查时间正常落库），形成“上边红框报错、下边结果已完成”的错乱现象。

**根因分析**：
1. **两次超时的概念混淆与前端轮询阈值过窄**：前置 PR 修复了触发核查时的 Axios 30s HTTP 请求超时；但在进入任务轮询阶段后，`useQualityCheckDrawer.js` 中 `MAX_POLL_TIMEOUT_MS` 被硬编码为了 120 秒（2 分钟）。标书质量核查涉及 Word 解析、招标要求 LLM 结构化提取、4 知识库真实检索以及 7 项语义条款逐项大模型深度裁决（含 RAG 页码检索与格式重试），在真实模型调用下耗时通常在 130~180 秒，导致前端在 120 秒误判超时中断轮询；
2. **未完成任务与已完成报告的浅引用混杂**：抽屉在 `loadLatestReport` 和 `triggerCheck(true)` 重新核查时，未能将进行中的任务（`status !== COMPLETED`）与已完成报告隔离，且重新核查时未重置 `report.value`，导致轮询因超时中断时 Vue 模板根据 `v-else-if="report"` 回退展示报告视图，出现报错与旧报告/完成报告并存。

**解决方案**：
1. **放宽轮询超时上限对齐系统长任务规范**（`useQualityCheckDrawer.js`）：
   - 将 `MAX_POLL_TIMEOUT_MS` 提升至 600,000 ms（10 分钟 / 300 次轮询），与评分标准解析等系统 AI 长任务对齐；
2. **严格隔离任务进行态与报告展示态**（`useQualityCheckDrawer.js`）：
   - `loadLatestReport` 中若检测到未完成状态（`INIT`/`PARSING`/`CHECKING`），直接启动轮询而不将未完成对象赋值给 `report.value`；
   - `triggerCheck(true)` 重新核查时显式置空 `report.value = null`，确保轮询期间 UI 稳定渲染进度条；
3. **更贴近实际的进度预估文案**（`QualityCheckDrawer.vue`）：
   - 将“通常需要 10~30 秒”调整为“通常需要 1~2 分钟”。
## 191. PR !2481 评分解析修复四类交付缺陷：真实标书 PII 入库、金标静默失效、词表放射注入、拆分语义假设（2026-08-31）

**现象**：
PR !2481（Project 21 MRO 评分解析修复）功能正确、205 项测试全绿，但严格审查（三透镜）发现四类行为正确性之外的问题，其中一项构成合入否决。

**根因分析**：
1. **真实采购文件全文作为测试夹具入库**：6909 行真实标书含真实联系人姓名、电话、邮箱、两个银行账号、银行行号与楼宇级地址；仓库存在 Gitee→GitHub 单向镜像，推送即扩散，事后删除无效。此前所有金标夹具均为合成样本，该 PR 打破先例未被察觉；
2. **金标测试静默通过反模式**：`if (!Files.exists(fixturePath)) return;` 使"防回退金标"在夹具路径失效时无声变绿，防线归零；同 PR 前一个 commit 刚把旧测试的同一反模式改为 `@Disabled`，新测试又复制了它；
3. **过滤词表放射状注入多个消费方且部分无逃生门**：CONTRACT_ASSESSMENT_PATTERN 新增 10 词同时注入 3 个消费方，其中两个 locator 的表头判定无条件命中即跳过——表头含"考核指标/扣分项"等考核词的真实评分表会被整表丢弃（"考核指标"恰是评分语境高频词）；
4. **价格分细拆隐含加法语义假设**：`size >= 2` 即拆分并跳过父行，对"取最高/封顶"型价格分会虚增总权重，破坏下游总分闭环。

**解决方案**：
1. 夹具全量脱敏（人名/电话/邮箱/账号/行号/地址 → 占位符），grep 终扫零残留；合成样本优先的夹具纪律写入 wiki；
2. 金标测试改 classpath 加载 + `assertThat(in).isNotNull()` 硬失败 + 精确 `hasSize(13)` 断言；
3. 两处 locator 表头判定补 `SCORE_HEADER_ESCAPE_PATTERN`（评分因素|评审因素 强评分列名）逃生门，收窄口径经复审 jshell 实证（"评分标准/分值"是考核表也有的泛词，放行会泄漏整张考核表）；词表提取共享常量消除双写；逃生门行为与泄漏面各配锚定测试；
4. 价格细拆加"子项之和==父行分值"数值一致性门控，取最高型自动退回父行整项。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 前端轮询超时过短导致任务在后台完成但前端报错 | 开发时只测了无大模型或单项快速返回，未估算多轮 LLM + RAG 累积耗时 | AI 多步骤/多轮裁决长任务的前端轮询上限必须 >= 10 分钟，严禁将轮询超时设为 1~2 分钟 |
| 轮询异常时界面回退并残留旧数据 | 重新开始任务时未清空 `report.value`，`polling` 变为 false 时条件判断回退到了旧报告 | 触发新任务时显式清空旧数据，未达终态 `COMPLETED` 前不向展示模型赋值 |
| 真实客户文档作测试夹具 | 贪真实代表性，忽视 PII/金融信息合规与镜像扩散 | 夹具必须脱敏（人名/电话/账号/地址→占位符）；入库前 grep 扫描 PII 模式清单 |
| 金标测试 fixture 缺失静默 pass | 复制旧测试模板，静默 return 反模式代际传染 | 金标夹具用 classpath 加载 + 存在性硬断言；测试加固时先全仓搜同类反模式 |
| 过滤词表一处编译多处引用 | 词表集中管理被误当成"一处修改全局生效"的优点，忽视各消费方语义不同 | 词表消费方必须有逃生门（强评分列名白名单）；每扩一词枚举全部消费方逐一评估非目标场景 |
| 文本枚举拆分假设可加性 | MRO 样例是加法型便按加法实现 | 拆分前做数值一致性校验（子项和==父值）；"取最高/封顶"型必须在测试中显式反例锚定 |

## 192. 历史案例 OBS 原文直链下载：中文对象键签发、CNAME 下载链路与六边形合规整改（2026-09-01）

**现象**：
案例库历史切片案例点击"标书原文"此前只能拿到 ZIP 或无响应；本次打通 OBS 预签名直链下载原版 docx（端点 `GET /api/cases/{caseId}/download-url`，spec 058），测试环境 300/300 条签发抽测全通。

**关键事实与经验**：
1. **中文对象键的签发编码分工**：桶内对象键在库中（`knowledge_case.obs_bid_file_key`）一律存**明文中文键**，percent-encode 只发生在签发环节（V2 签名 CanonicalizedResource 编码）；纯核心 `ObsObjectKeyPolicy.filenameOf` 因此**不做** URL 解码。若入库侧存编码值、签发侧再编码会双重编码导致 403；
2. **CNAME 下载链路对中文键可用**：`HuaweiObsDownloadUrlGateway` 双客户端中，CNAME 自定义域名（`widbid-obs.ehsy.com`）下载客户端实测可签发并下载 99.6MB 中文键 docx（zipfile 完整性校验通过），无需降级标准域名；
3. **签发安全面收敛**：签发端点只接受库内案例 id，对象键只信导入管道写入的库内值（入库侧 `is_valid_obs_bid_key`：非空/根前缀 `2026年（投标文件）/`/无 `..`/非绝对路径），杜绝前端传路径导致的任意对象签发；
4. **新服务必须走端口/适配器**：`CaseDownloadUrlAppService` 首版直接依赖 `KnowledgeCaseRepository` 被 `BidCaseSliceArchitectureTest` 拦截；正解是新增端口 `CaseObsBidFileReader`（domain/gateway）+ 适配器，而非加 `APPLICATION_INFRASTRUCTURE_EXEMPTIONS` 豁免；
5. **FP-Java 纯核心的隐蔽违规**：端口接口内嵌的 record 被判定依赖 `.gateway.` 包，模型必须提为 `domain/model` 顶层类型；`filenameOf` 抛异常违反"失败返 Optional"规则；
6. **Flyway 迁移必配回滚脚本**：每个 `V*.sql` 必须有 `db/rollback/migration-mysql/U*.sql`（四行头注释含 `-- Input:` 指回源迁移），否则 `FlywayRollbackScriptCoverageTest` 红。

**验证证据**：端点 200/404/409/403 抽测全通；定向 28 用例绿；部署 598f7608c（V1205 生效，health UP）；临时验证账号与服务器临时文件已清理。

## 193. 历史切片案例状态/类型错配：跨表字段真值位置与终态映射完整性（2026-09-01）

**现象**：
测试环境案例库 300 条历史切片案例中，174 条"项目状态"显示"未知"（项目详情里明明有状态），300 条"项目类型"与项目信息不一致（案例侧为空/对不上）。

**根因**（均在导入脚本 `scripts/import-case-slices-to-knowledge-case.py` 写侧）：
1. **读错字段**：脚本从 `projects.industry` 取项目类型，但历史项目的"项目类型"真值在 `tenders.project_type`（`import-historical-projects.py` 导入时写入，中文原值：办公/综合/集采/工业品）；`projects.industry` 历史项目全空 → 案例 `project_type` 300 条全 NULL。
2. **终态映射不完整**：`build_row` 写死 `'WON' if status == 'WON' else None`，把来源项目真实状态 LOST(139)/FAILED(35) 丢弃 → `bid_result` NULL → 前台显示"未知"。
3. **值域不一致**：`tenders.project_type` 存中文原值，前台案例标签/筛选期望枚举码（OFFICE/COMPREHENSIVE/COLLECTIVE/INDUSTRIAL/OTHER）——修复时必须经映射归一，直接拷中文会使类型筛选失效。

**修复**：
- 加载来源项目改 `LEFT JOIN tenders` 取真值；新增 `PROJECT_TYPE_MAP`（中文→枚举码）与 `BID_RESULT_MAP`（WON/LOST/FAILED 全终态）。
- 前台 `CaseDetailDialog` 补第三态：`FAILED` → "已流标"（warning 标签）+ 组件测试锚定三态映射。
- 存量回填：脚本新增 `--backfill-meta` 模式（复用白名单守卫，范围锚定【历史标书切片】标记 + 历史 source_module，支持 --dry-run）。切片 JSONL 已被清理无法重导，故原地回填；测试库实测回填 174 条状态 + 300 条类型，复核分布与取证完全一致。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 想当然按表名猜字段真值位置 | 未核实"项目类型"实际落在 tenders 而非 projects.industry | 导入/同步类脚本取值前，先用 SQL 分布统计核实候选字段的非空率与值域，不凭表结构猜 |
| 状态映射只写目标分支（"仅 WON 生效"） | 初期只关心中标案例，未考虑其余终态在下游的展示语义 | 枚举映射必须覆盖来源侧全部终态；显式决策"其余值写什么"，不得隐式丢成 NULL |
| 源表中文值与消费侧枚举码值域不一致 | 两张表由不同脚本在不同时期写入，值域约定未对齐 | 跨表搬运字段时核对消费侧值域（前台标签/筛选的枚举），搬运时归一 |
| 一次性产物（/tmp 切片 JSONL）被清理后无法重导 | 导入依赖的中间产物未归档 | 一次性导入的中间产物至少保留到数据验收完成；回填能力（--backfill-meta/--purge）随脚本交付 |

---

## 194. 评分项编号排序策略脱离层级上下文导致跨大类全局杂糅乱序（2026-09-01）

**现象**：
测试环境项目 1371（Project 21 MRO 标书）执行 AI 评分标准解析后，15 个评分项总分为 100.0 分，但在列表上呈现商务评分、报价评分、技术评分的子项编号 1、1、1、2、2、2 跨大类交错穿插展示，且无编号的技术评分项被丢到末尾。

**根因分析**：
1. **局部编号被误当全局编号扁平重排**：不同大类/父维度内部存在各自的子项编号（商务-财务状况下的 1/2，商务-网点下的 1/2，报价下的 1/2）。`ScoreItemOrderPolicy` 原实现仅提取 `code` 中的数字做全局扁平升序排序，将不同大类的编号强行聚拢，彻底打乱了章节和大类结构；
2. **无编号项被全局置底**：原策略将无法解析编号（`code=null`）的项全部放到最后，导致原本在招标文件正文中间的技术评分方案项全部被沉底到报价之后；
3. **单元测试与全流程流水线脱节**：底层单测仅验证了启发式表格召回器 `ScoreTableRecallLane.recall()`，该层输出为原始自然序；但全流程中经过了持久化层 `ScoreItemOrderPolicy.sort()`，导致测试环境真实解析时暴露出全局杂糅缺陷。

**解决方案**：
1. 重构 `ScoreItemOrderPolicy` 遵循四层排序纪律：
   - 第一关键字：所属顶级大类（商务评分 -> 技术评分 -> 报价评分）首次出现顺序（自然出现序）；
   - 第二关键字：所属父维度（财务状况 -> 服务网点）首次出现顺序；
   - 第三关键字：同父维度内部按局部编号（1, 2, 3...）数值序重排；
   - 第四关键字：同组内无编号或无法解析项排在该组有编号项之后，并严格保持原始相对输入序；
2. 补齐 `ScoreItemOrderPolicyTest` 多大类局部编号隔离测试与父维度内局部重排测试；并在 `RealProjectScoreParsingTest` 中增加端到端全流程排序与总分闭环校验。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 局部编号全局排序导致跨章节数据穿插 | 假设所有编号都在同一全局序列下，未考虑各子章节的局部编号（1/2）复用 | 排序策略必须以大类/父维度自然出现序为第一关键字，严禁脱离层级做全局扁平数值排序 |
| 单测仅测提取器未测持久化排序 | 召回层单测直接返回原始行，未走持久化策略导致排序 bug 逃逸 | 涉及排序/持久化的核心业务，集成测试必须覆盖从召回、归并到 OrderPolicy 排序的完整管道 |


## 195. 闲置推荐端点候选池恒空：客户类型枚举跨填项目类型过滤位（2026-09-01）

**背景**：验证历史切片案例是否接入 AI 智能案例推荐时，发现闲置端点 `GET /api/cases/recommend/project`（`KnowledgeCaseRecommendAppService`，前端无调用方）对任何项目都返回 0 条。主推荐链路 `POST /api/projects/{id}/cases/recommend` 不受影响（切片案例 126/160 命中）。

**根因分析**：
1. **跨枚举错填**：`buildCriteria()` 与 `recommendForProject()` 均把 `projects.customer_type`（客户类型枚举：民企/央企…）塞进 `criteria.projectType`；
2. **过滤位语义错配**：`fetchCandidates()` 在无评分类别时用 `knowledge_case.project_type = criteria.projectType` 过滤——项目类型与客户类型是两套不同枚举（项目类型=综合/工业品/办公…），两值域无交集，候选池恒为空；
3. **打分侧连带污染**：`KnowledgeCaseMatchPolicy` 第 3 条"项目类型一致 +15 分"拿的也是这个错填值，即使候选池非空也是假匹配。

**解决方案**：
1. `projects` 表本无项目类型真值字段（实体核查确认），`criteria.projectType` 一律置 `null`；
2. `fetchCandidates()` 移除跨枚举过滤谓词，仅保留案例自身字段过滤（`status` / `scoringCategory`），与主链路 `findAllActive()` 语义对齐；
3. 补回归测试 `recommendForProject_ShouldNotFilterCandidatesByMismatchedProjectType`：捕获 Specification 并对 CriteriaBuilder 求值，断言 `root.get("projectType")` 永不被调用。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 跨枚举字段顶替（客户类型填进项目类型位） | 两字段都叫"类型"，开发时就近取值顶替 | 与 #193 同源教训：跨字段灌值前必须校验目标字段的值域枚举；无真值时置空，不得跨枚举回退 |
| 闲置端点缺陷长期潜伏 | 无前端调用方，功能验证永远绕开它 | 接口审计（验证对接链路时顺带全量枚举同域端点）能暴露无流量覆盖的死代码缺陷 |
| Specification 过滤逻辑无测试 | 过滤谓词藏在 lambda 里，单测直接 mock 掉 repository | 谓词逻辑要用 ArgumentCaptor 捕获 + CriteriaBuilder 求值做行为断言 |


## 196. AI 评分标准解析细则（detail）截断：提示词候选池摘要硬编码截断与守卫升级缺失（2026-09-01）

**背景**：在测试环境执行招标文件 AI 评分标准解析时，发现评分项细则（如 MRO 综合服务方案 10 分）的文本在第 2 点中间（"出库配"）被截断，售中方案后半截与第 3 点售后服务方案完全丢失。

**根因分析**：
1. **输入摘要过早截断**：`ScoreGlobalMergeExecutor` 在构造发给大模型的候选池 JSON 时，硬编码 `POOL_DETAIL_TRUNCATE = 200`，将候选池中原本由表格抽取层拿到的 400+ 字符完整条款强制切为 200 字符；
2. **大模型直接复述截断文本**：LLM 在生成结构化 JSON `items` 时，倾向于直接复制候选池摘要中的 200 字符截断文本，而非从章节长文本重新完整摘抄；
3. **守卫层缺少全量真值升级机制**：`ScoreMergeResultGuard` 采纳了 200 字符截断项（因为前缀确实在原文中），且在候选池回补检查时判定池内 400+ 字符的完整候选已被"覆盖"，未将截断项与池内完整项进行最长文本升级合并，导致物理抽取的全量逐字细则被模型截断版永久覆盖。

**解决方案**：
1. 将 `ScoreGlobalMergeExecutor.POOL_DETAIL_TRUNCATE` 上限从 200 提高至 4000，确保候选池中单条细则全量透传给大模型；
2. 在 `ScoreMergeResultGuard` 采纳项处理中增加 `upgradeWithLongestPoolDetail` 逻辑：当采纳项在候选池中存在文本互含的对应项且池项 `detail` 更长时，自动升级为池中的最完整逐字细则；
3. 增强 `ScoreParsePrompts` 全局归并硬性规则第 4 条，明确要求包含 1/2/3 全量子条款，严禁中途截断；
4. 补齐 `ScoreMergeResultGuardTest` 截断自动升级单测、`ScoreGlobalMergeExecutorTest` 摘要测试与 `RealProjectScoreParsingTest` 全量条款子要点断言。

## 198. AI 评分标准解析表格抽取重构：从行级正则特征工程转向整表级一次性 LLM 结构化抽取（2026-09-01）

**背景**：AI 评分标准解析长期依赖 `HeuristicTableColumnMapper`（网格列推断）与 `ScoreTableRowParser`（行级正则解析），累积了 30+ 表头词正则、行级过滤规则、括号分值提取与逃生门。每次遇到复杂格式变体（如跨行合并单元格、括号分值列、考核表混排）就修补正则，导致频繁连锁回归与特征工程债务爆炸。

**根因分析**：
1. **行级正则丢失空间上下文**：表格中的跨行合并单元格（如第2列"商务评分标准 56分"跨6行）在单行解析时丢失父维度与分值关系；
2. **规则特征工程脆弱性**：依靠关键词黑白名单无法穷举央国企多样化的标书排版变体；
3. **分层职责割裂**：列推断与行抽取割裂为两阶段导致坐标对齐极其复杂脆弱。

**解决方案**：
1. **落地整表级一次性 LLM 结构化抽取**：新增 `LlmTableScoreExtractor` 与 `TableScoreExtractionPrompt`，通过单次 Structured Output JSON Schema 同时完成表格分类（识别并过滤合同考核表/程序表）与多层级评分项抽取；
2. **文本逐字保真与弹性对齐**：Prompt 强约束 `detail` 必须逐字复制表格单元格原文，并经 `ScoreOriginalTextAligner` 消除模型复述时的空白/断词漂移；
3. **主备平滑降级**：在 `ScoreParseAppService` 中优先采用 LLM 表格抽取，在 LLM 异常或无返回时自动回退至既有启发式规则（`ScoreTableRecallLane`），保证生产高可用与零回归；
4. **全量测试覆盖**：新增 Prompt 与 Extractor 专项单测，全量通过金标回放测试与架构门禁。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 复杂非结构化表格强行用单行正则提取 | 误以为正则更快更省，忽略了跨行跨列排版的空间语义复杂性 | 复杂表格与排版理解应由具备视觉空间与语义感知能力的 LLM 一次性结构化抽取，规则仅做校验与兜底 |
| 表格分类与行解析分多步造成多次调用开销 | 习惯分步推理思维 | 整表 Prompt 结合 JSON Schema 可单次同时完成分类判断与明细项抽取，减少网络开销与延迟 |




## 197. 展示层改三级但查询口径仍两级：部门筛选值静默丢弃退化为"暂无数据"+空列表 fail-open（2026-09-01）

**现象**：
M1 部门筛选树锚定西域集团(0001)子树三级展示（PR !2474）后，勾选二级/三级部门（业务中心、销售一部等）点确认，图表显示"暂无数据"；此前即使无数据，X 轴也会显示选中部门名的 0 值柱。

**根因分析**：
1. **展示层与查询口径各自为政**：锚定 PR 只改 `FilterOptionsQueryService.fetchDepartmentTree` 的展示树，并明确决策"X 轴部门维度口径不动"。`AnalyticsDepartmentHierarchySupport.axisNames` 只认物理组织树 1/2 级，而锚定后 UI 二级/三级 = 物理 3/4 级，选值被静默丢弃 → `bucketNames` 空（X 轴期望柱空）+ `expand` 空 → 补零机制失效 → 前端判定空态；
2. **空列表 fail-open 语义歧义**：`expand` 的空列表结果经 `isNotEmpty([])=false` 使部门 SQL 过滤条件整体被跳过，退化成"不过滤"——若库中有部门数据将越权展示全部门；
3. **行为变化解释**：旧路径选值都在物理 1/2 级 → 期望分类非空 → `assembleSeries` 补 0 柱（"无数据也显示图表"）；新路径期望分类为空 → `categories=[]` → "暂无数据"。

**解决方案**：
1. 口径泛化为"显示层级"：`displayLevelOf` 相对锚点计算（锚点直接子节点=一级），锚点未命中回退物理深度，`axisNames` 按层级自底向上折叠——可见下级勾满折叠上级、部分勾选忽略上级保留下级（既有两级规则原样泛化，8 个既有两级用例原样保持绿）；
2. 部门筛选三态语义：null=不过滤；空列表=零命中 → fail-closed 直接返回空（`queryDimension` / `fetchRegionRows` / `fetchDistinctPersons` / 下钻 `drillDown` 四处都在拼 IN 之前返回空结果）；
3. 11 个锚定三级用例 + 2 个服务层 fail-closed 用例锁定。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 展示层改了、查询口径没改 | 锚定 PR 只改"展示"，未枚举选值的下游消费方（expand / bucketNames / toBucket / 下钻） | 改树层级/截断口径前必须 grep 选值的全部消费方，同步评估口径一致性 |
| 不识别的层级被静默丢弃 | `axisNames` 白名单式分层，其他层级无 else 分支、无日志 | 选择型口径禁止静默丢值：要么接纳，要么 fail-closed 零命中，并用测试锁定 |
| 空列表退化为不过滤 | `isNotEmpty` 无法区分"未启用"与"启用但零命中" | 过滤参数用三态语义：null=未启用 / 空列表=零命中 fail-closed（对齐 regionMatchedIds 空集既有模式） |


## 199. 质量核查整篇标书退化为 1 页：\f 分页符跨管线无人生产（2026-09-01）

**背景**：测试环境 project/1352（134+ 页 docx 标书）AI 标书质量核查"完全不准"：DOCUMENT_COMPLETENESS 误报"页数少于3页"、所有确定性证据 pageNumber=1 且 quote 恒为封面文本、6/7 语义项 LLM 裁决"返回结果异常"转人工。

**根因分析**（三层连锁，单一根因）：
1. **隐式契约断裂**：Java `SidecarStreamDocumentAdapter.buildDocumentContent` 仅按 `\f` split 分页，但 Sidecar（markitdown/mammoth docx 路径）从不输出 `\f`（Word 自动分页只存在于渲染层）；pdfplumber PDF 路径页间也是 `\n\n`。分页符的"生产端"根本不存在，"消费端"却按存在设计；
2. **单页退化三重失效**：`pages().size()=1<3` → 完整性误报 FAIL；`findFirstPageContaining` 恒命中唯一页 + `snippet` 取页首 200 字 → 证据全部错位成封面；`SemanticPageRetriever` 超预算分支 `selected.isEmpty()` 短路——首个页面无条件全量塞入 prompt（60k 预算失效）→ 巨型 prompt 导致 LLM 输出异常，6/7 语义项降级转人工；
3. **历史变更的副作用盲区**：8-25 PR !2399（scoreparse 报价表跨页合并）在 converter 加了 `full_md.replace("\x0c", "\n\n")`，进一步消灭了 pdfminer 路径可能残留的页分隔——变更时只盘点了 scoreparse 消费方，未发现 compliance 也依赖页边界。

**解决方案**（PR agent/codex/quality-check-pagination）：
1. Sidecar `/convert` 响应新增结构化 `pages` 字段（PDF 经 `convert_pdf_document` 单次解析同时产出合并 markdown 与逐页切片；docx 物理页不可得，pages 为空数组）；
2. Java 分页优先级：结构化 pages → `\f` 切分 → `DocumentContent.virtualPages` 虚拟分页兜底（>4k 字符按 markdown 标题切块、行累积断块、单段 6k 硬切）；
3. `SemanticPageRetriever` 超预算单页硬截断；确定性证据 `snippetAround` 围绕关键词命中位置取窗；BID_OPENING_TABLE 词表补齐现场用词；PRICE_CONSISTENCY 加全文跨行回退（DOTALL + 300 字符窗口）。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 跨管线隐式契约（\f 分页符）无生产端也无 golden 测试 | Java 单测用手工拼 `\f` 的 fixture，从未对真实 Sidecar 输出做契约测试 | 跨语言/跨进程数据契约必须有真实端到端 golden 测试锚定；mock 层测试验证不了"上游真的会生产这个格式" |
| 共享转换管线变更只盘点直接调用方 | PR !2399 为 scoreparse 改 markdown 后处理，compliance 消费同一输出但未被排查 | 改共享输出格式前 grep 全部消费端字段用途；新增/删除输出字段（哪怕是清洗字符）都要过一遍下游 |
| 单页退化的失败模式不显式报错而是"全都算对但全都没意义" | 页数判断、证据定位、检索预算三个消费方都在"1 页"输入上静默给出错误结论 | 对"退化输入"设计哨兵：单页超阈值文本应显式告警或兜底切块，而非让下游各自 silently wrong |


## 200. 全文无界表格扫描混入非评分表格导致候选池污染与权重裁决失真（2026-09-01）

**背景**：测试环境 project/1352（张家口银行等 134+ 页长篇标书）在引入整表 LLM 抽取后出现严重解析回归，原本非常准的评分解析出现大量错项或核心打分项缺失。

**根因分析**：
1. **全文表格无界扫描**：`LlmTableScoreExtractor.extractFromDocument` 盲目扫描全文所有表格，宽泛正则导致《投标人须知前附表》（仅有总括汇总项）、《合同履约违约考核表》（扣款扣分细则）、《报价开标一览表格式》等 10+ 个非评标打分表格全部被送入 LLM 并抽取；
2. **多表格混淆造成权重爆炸**：各表格提取的候选堆叠在同一候选池，总权重膨胀至 200~300 分，下游 `WeightOverloadTrimPolicy` 与全局归并裁决在互相矛盾的汇总项和细则项中做出错误剔除，导致真实打分项被误删或分数严重缩放；
3. **标准与非标分流不当**：标准清晰的评分表本就应由确定性启发式/网格抽取毫秒级精准解析，全量无差别走 LLM 既增加延迟又引入幻觉风险。

**解决方案**：
1. **分级分流策略**：`ScoreParseAppService` 优先使用确定性启发式表格抽取（毫秒级、0 网络开销、标准标书 100% 保真）；当且仅当启发式未命中（非标复杂排版、合并单元格等）时平滑启用 LLM 整表抽取；
2. **章节定位空间约束**：`LlmTableScoreExtractor` 优先基于 `MarkdownScoreSectionLocator` 定位评分章节（如第三章 评标办法），仅抽取章节内的评分表格；
3. **前置表格守卫拦截**：严格过滤 `isProcedureNoticeTable`（前附表）与 `CONTRACT_ASSESSMENT_HEADER`（合同履约考核表），彻底防止非评标表格误入。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 对全文所有表格无差别调用 LLM 抽取 | 认为 LLM 能在表格内自行判断，忽略了单表脱离全文语境时极易将前附表或考核表误判为评分表 | LLM 抽取前必须进行文档级章节定位与表格类型守卫过滤，缩小空间范围 |
| 破坏了原本确定性且准确的存量路径 | 重构时全量切换而非保留高质量确定性主路径 | 重构必须坚持「确定性主路优先 + LLM 智能兜底」原则，避免标准场景被非标解法反噬 |



## 201. 语义项裁决全线截断于 ~60 token：thinking 系模型的 max_tokens 计入内部推理（2026-09-01）

**背景**：分页修复（#199）合入后测试环境复检，确定性 lane 全部恢复，但 7/7 语义项仍报"AI 返回结果异常"。ssh 测试环境（releaseId 899ebc0a9）抓 journald 实证：LLM（gemini-3.5-flash 经自建网关）响应全部截断于 44-68 字符，正好写完 status/conclusion 在 suggestion 中途断掉。

**根因分析**：
1. `LLM_ADJUDICATION_MAX_TOKENS = 1024` 的设计假设是"裁决层只输出小 JSON"——在非 thinking 模型上成立；
2. gemini-3.5-flash 等 thinking 系模型经 OpenAI 兼容网关时，**内部推理（thinking）token 同样计入 max_tokens 输出预算**：1024 被 thinking 消耗殆尽，正文仅剩 ~60 token，JSON 必然不配平 → 解析失败 ×2 → LOW_CONFIDENCE 转人工；
3. 同文件对照：`TenderRequirementsExtractor.MAX_TOKENS = 4096` 同一网关同一模型正常工作——差异只剩 token 预算。

**解决方案**：`LLM_ADJUDICATION_MAX_TOKENS` 1024 → 4096（与招标要求提取对齐），注释记录 thinking 语义防止回改。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| max_tokens 按"输出正文大小"估算，忽略 thinking 系模型的内部推理消耗 | 供应商模型从非 thinking 切换到 thinking 系（网关换模型）时，token 语义静默变化 | thinking 系模型下 max_tokens ≥ 正文需求 × 富余系数（≥4×）；换模型后必须对"小输出"调用做冒烟验证 |
| "返回结果异常"文案掩盖了系统性截断 | 每项失败都归档为 LOW_CONFIDENCE 转人工，无人看原始响应 | LLM 响应解析失败日志（含原始响应截断）应纳入告警视野：同类失败 ≥N 次/任务即为配置问题而非内容问题 |

## 202. AI 智能案例推荐中评分项带编号与大类归档导致召回 0 条（2026-09-01）

**背景**：在项目详情"AI 智能案例推荐"抽屉中，选择评分项 `2.2.4(3) 商城系统对接能力` 时，页面展示"匹配案例 0 个结果，暂无与本评分项相似的历史案例"。但在知识库中以关键词"商城系统对接能力"检索时，能精准命中 1 条标题为"技术方案"的历史案例。

**根因分析**：
1. **评分项编号前缀污染**：评分项名称/维度通常带有如 `2.2.4(3)`、`1.1`、`一、`、`【1】` 等层级编号，旧匹配逻辑未做正则剥离，导致分词受到干扰；
2. **跨层标题/正文匹配缺失**：旧算法 `calculateTitleSimilarity` 仅将评分项标题与案例标题做 Jaccard 相似度计算。当历史案例以大类（如"技术方案"）作为标题时，得分判定为 0 分，未跨层检查评分项纯净业务名是否在案例正文（`responseText`）或原始需求（`requirementRaw`）中出现；
3. **长条款分母稀释**：旧 `calculateDetailOverlap` 纯粹以 Token 计数作为分母，面对长段落标书条款时，即使关键短语完全命中，得分也被稀释为个位数；
4. **类别智能推断缺失**：具体评分项（"商城系统对接能力"）与案例大类（"技术方案"）缺少语义推断，导致类别分（15分）丢失；
5. **前端阈值拦截**：因上述 4 点，综合得分仅 18~25 分，被前端 `score >= 50` 阈值过滤，呈现为 0 条。

**解决方案**：
1. 实现 `cleanTitle(text)` 静态剥离方法，自动清除所有层级编号前缀；
2. 跨层正文语义匹配：纯净业务名直接匹配候选案例正文与需求，命中即给满分（20分）；
3. 改进条款重叠度计算：结合 Token 命中率与绝对命中加成；
4. 类别智能推断：建立技术/商务/资质/团队等大类与业务关键词的推断映射；
5. 同步更新 `ScoreItemCaseMatchPolicy` 与 `KnowledgeCaseMatchPolicy`。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 标题只对标题比较，忽略大类分类下的正文包含 | 案例库沉淀时常用大类命名（如"技术方案"），而评分项是具体小项（"商城对接"） | 智能匹配必须具备跨层穿透能力：评分项名称命中案例正文/原始需求同样算作高置信度匹配 |
| 标书解析出的评分项包含序号前缀未清洗 | 招标书天然带有 `2.2.4(3)` 等章节编号 | 文本匹配/检索前必须统一过清洗管道剥离序号 |

## 203. 标讯事件总线注册无退避重试与未关闭线程导致测试挂起（2026-09-01）

**背景**：执行全量单元与集成测试（`mvn -q test`）时，`TenderEventSdkConfigTest` 测试启动了标讯事件总线客户端服务注册逻辑，配置了假域名 `http://event-bus.local`，导致测试过程长时间无法退出，拿不到退出码。

**根因分析**：
1. **测试用例配置未解析的外部域名**：`TenderEventSdkConfigTest` 在装配测试中硬编码了 `http://event-bus.local`；
2. **SDK 内置无休眠死循环**：`ClientRegisterComponent.register()` 底层使用 `while (isRun && enableRegister)` 循环发起 HTTP 请求。DNS 解析失败时捕获异常返回 `null`，因返回非成功码，循环立即无休眠重试，持续发起 DNS 阻塞请求；
3. **缺少生命周期优雅关闭**：`TenderEventRegistrar` 在虚拟线程中运行该死循环，但未实现 `AutoCloseable`，在 Spring Context 销毁后未能将 `ClientRegisterComponent.isRun` 设为 `false` 并中断线程，导致后台线程常驻 JVM 造成 Maven Surefire 无法回收退出。

**解决方案**：
1. `TenderEventRegistrar` 实现 `AutoCloseable` 并在 `close()` 中反射重置 `isRun = false` 且中断后台线程；
2. `TenderEventSdkConfig` 声明 `destroyMethod = "close"` 绑定容器生命周期；
3. `TenderEventSdkConfigTest` 使用 `MockWebServer` 提供本地快速响应，隔离真实网络。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 集成/装配测试使用未配置的 `.local` 假域名 | 误以为只要不是生产地址就安全，忽略了 DNS 解析失败与网络调用的阻塞耗时 | 涉及 HTTP 调用的测试一律使用 `MockWebServer` 监听 `127.0.0.1:port`，杜绝使用虚假域名 |
| 第三方 SDK 注册重试线程不受容器生命周期管控 | 仅关注启动阶段调用，忽略了 Spring 容器销毁时的清理与线程回收 | 任何启动常驻/重试后台线程的组件必须实现 `AutoCloseable`，并在销毁时显式终止内部循环 |

## 204. AI 智能案例推荐中泛化停用词刷分与项目专属实体断层缺失导致错误推荐（2026-09-02）

**背景**：在项目 1358（三峡集团采购项目）的"AI 智能案例推荐"抽屉中，期望最高匹配度的应该是历史"三峡案例"，但实际推荐得分第一位却是"新疆中泰案例"。

**根因分析**：
1. **长文本泛化停用词虚高刷分**：招标文件通常包含大量通用招投标术语（如"系统、服务、管理、方案、要求、保障、实施、综合、评审"等）。候选案例如果包含冗长的通用说明，会在条款重叠度（Jaccard/n-gram）中命中大量非特征词汇，稀释了业务专有特征词的权重；
2. **项目/客户专属实体识别与加权断层缺失**：项目名称（如"中国长江三峡集团2026年办公及工业品电商化采购项目"）与招标客户主体中包含极其关键的专有名词（如"三峡"）。旧算法未将项目主体专有名词作为核心特征加权，导致同客户历史案例的绝对优势无法体现；
3. **评分项选择空态下的全局匹配缺失**：当用户未选定特定评分项时，旧逻辑缺少项目名称与案例来源项目名称的兜底语义相似度比对。

**解决方案**：
1. 建立招投标通用 `STOP_WORDS` 停用词库，在条款重叠度分词比对中自动剔除泛化词；
2. 实现 `TextSimilarityPolicy.extractEntityTokens` 专有名词抽取器，智能剥离年份与通用业务动作词，精准识别项目与客户专属实体；
3. 在 `ScoreItemCaseMatchPolicy` 中新增"项目专属核心实体匹配"维度（最高 25 分），命中专属实体（如"三峡"）给予断层式优先加分，并输出推荐理由 `命中项目专属实体(三峡)`；
4. 抽离 `CaseCategoryMatchPolicy` 实现纯核心类别推断，遵守单文件 300 行预算。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 通用长文本案例凭借大量泛化行业术语在 Jaccard 相似度中刷分 | 未引入领域停用词过滤，所有命中词权重等同 | 招投标文本匹配必须前置过滤招投标通用停用词，仅对具区分度的特征词计分 |
| 同客户/同主体历史案例未体现明显断层优势 | 算法只在单点条款做 n-gram 比对，丢失了项目级专有实体特征 | 引入项目/客户专属实体识别与断层加权（+25分），确保专属客户案例稳居第一梯队 |

## 205. 标书质量核查四项核心缺陷：正向承诺被误判为违规反标、附件免除项盲目假通过与基准断层（2026-09-02）

**背景**：测试环境项目 1358 标书质量核查暴露 4 个严重影响用户信任的问题：
1. 资质匹配说明写"联动资料库"，实际是对照标书条件检查且在未提取到要求时报"招标文件要求不可用"；
2. 提示"未检测到分标段独立章节"不符合实际（标书本身已是独立章节）；
3. 标书中对否决项进行正向履约承诺/引用违约责任条款，检查却判定"识别到废标/反标敏感词"而不通过；
4. 标书不涉及保证金和电子 CA，但附件清单却全部显示"通过"（假通过）。

**根因分析**：
1. **三方比对基准依赖与文案割裂**：资质匹配本质是"招标文件资格要求 $\rightarrow$ 企业资质库 $\rightarrow$ 投标文件"三方比对，文案强调"联动资质库"给用户单向检索的错觉，且招标文件未提取出资格要求时抛出技术性提示，造成用户认知冲突；
2. **多标段规则误套单标段与 RAG 切片局限**：招标文件通用模板中包含"分标段投标"条款被 LLM 提取，且 RAG 页面切片只抽取部分页面，导致 LLM 无法全局审视目录与单标段属性；
3. **机械敏感词扫描与语境反转缺失**：`SubItemAndAttachmentRules` 纯正则扫描"转包/违法分包/违约解除"等词，未识别前缀否定词（"严禁"、"承诺不"、"杜绝"）和合同特别声明中正向承诺遵守违约责任的合法语境，将正向履约承诺误判为违规事实；
4. **缺乏招标文件前置适用性判断与粗暴单词命中**：无论项目类型固定核查保证金与 CA，且只要标书中出现"开户银行"或字母"CA"就无脑判定 PASS，形成严重的"虚假通过"。

**解决方案**：
1. 优化 `BidQualityCheckItem.QUALIFICATION_MATCH` 描述为"依据招标文件资格要求，对照企业资质库与投标文件逐项比对"，基准缺失时友好提示人工复核标书资质证明；
2. `LlmAdjudicator` Prompt 注入显式裁决准则：单标段投标或标书正文已包含清晰独立章节划分，不得判定为缺失分标段独立章节；
3. `SubItemAndAttachmentRules.checkVetoKeyword` 引入 `isNegatedOrCommitment` 识别前置 20 字符内的否定/承诺修饰语境（"承诺不"、"严禁"、"无"、"杜绝"等），对正向合规承诺予以豁免；
4. `SubItemAndAttachmentRules` 的保证金与 CA 规则增加招标文件前置适用性判断（`isDepositNotRequired` / `isCaNotRequired`），明确免收时判定为"免收/不涉及"，并加固特征词组合，杜绝单字包含即假通过；
5. 新增 17 项全量单元测试与反向违规测试，全绿通过 ArchUnit 纯核心架构门禁。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 敏感词匹配只看单词性，未做否定语境识别 | 忽略了标书编制中必然会原样引用招标违约条款并签署反向承诺函 | 正则/关键词扫描必须结合上下文前缀窗口检测否定/承诺修饰语境，避免颠倒合规结论 |
| 附件检查不看招标文件要求，单字包含即打勾 | 固定 25 项硬编码，且规则实现过于粗暴（如出现"CA"或"开户银行"即判定包含凭证） | 附件核查必须前置解析招标文件的适用性（免收/纸质标），凭证核查必须匹配核心特征组合词 |
| 规则提示暴露底层基准缺失异常 | 领域 Guard 将"未能从招标文件提取到资质"直接转为技术性错误 | 统一领域提示口径，引导用户进行标书资质核对，消除体验断层 |

## 206. 清理 prompt 硬编码时按方法逐个改导致漏项，且原测试在正向断言硬编码存在（2026-09-02）

**背景**：评分解析管道 Phase 3 要求清除 `ScoreParsePrompts.java` 中来自项目 1347/1358 的特定硬编码数值与维度名，避免单个项目的示例污染其他项目的解析。改动覆盖 `buildCandidateExtractionPrompt` 与 `buildGlobalMergePrompt` 两个方法后自认为已完成。

**根因分析**：
1. **按方法逐个改而非按 token 全局清**：清理时以"方法"为单位推进，改完两个方法即收工；`buildGapRecheckPrompt` 第 100 行仍硬编码项目 1358 的 `"运营服务能力 5%%"` 与 `"商品运营方案与新增商品需求报价响应方案"`，未被触及；
2. **测试成了硬编码的守护者**：`ScoreParsePromptsTest` 中三处 `assertThat(prompt).contains("运营服务能力")` 在**正向断言该项目特定维度名必须出现**。清理硬编码必然使这些断言失败——测试不是在守护规则语义，而是在守护我们想消灭的东西；
3. **失败信号被误读为"改坏了"**：断言失败的第一直觉是回退生产代码，而正确动作是改断言。若当时回退，Phase 3 治理目标即被测试锁死。

**解决方案**：
1. 全文件 `grep` 目标 token（而非逐方法检查），定位到 `buildGapRecheckPrompt` 的遗漏行并删除项目特定举例，保留项目无关的算术推理示例（`30% / 5个5% / 合计25% / 缺1个5%`）——该部分是通用教学信号，不是硬编码；
2. 三处 `contains("运营服务能力")` 改为断言各 prompt 中真实存在的**规则尾句**（`严禁遗漏并列项中的任何一项` / `并列能力子项被遗漏` / `绝对禁止遗漏任何并列子项`），规则锚点与百分比断言保留；
3. 新增 `prompts_containNoProjectSpecificDimensionNames` 回归守卫，显式断言三个 prompt 均不含项目特定维度名，防止将来重新硬编码。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 清理硬编码按方法逐个改，漏掉同类残留 | 以"代码结构"为推进单位，而硬编码是按"token"散布的 | 清理类任务一律先全文件 `grep` 目标 token 建立完整清单，再逐项消除，最后复跑 grep 确认归零 |
| 测试正向断言了应当被清除的硬编码 | 测试写于硬编码存在期，断言的是"当时的实现细节"而非"契约" | 治理类改动前先检查是否有测试在守护待清除内容；断言应锚定规则语义/契约，不要锚定具体示例值 |
| 断言失败即回退生产代码 | 把测试当唯一真相，忽略测试本身可能过期 | 断言失败先判定"是代码错了还是断言过期了"，治理场景下断言过期是常态，应改断言并补反向守卫 |

## 207. 给生产方法加参数重载静默破坏 Mockito strict stubbing，本地自选测试全绿仍被门禁拦下（2026-09-02）

**背景**：Phase 5 词汇注册表需要把词汇示例注入 LLM prompt，`ScoreGlobalMergeOrchestrator` 由调用 executor 的 2 参 `resolve(pool, sectionText)` 改为 3 参 `resolve(pool, sectionText, vocabularyExamples)`，旧 2 参签名保留为委托以向后兼容。本地运行自选的 9 个测试类得 101/101 全绿后提交，被 pre-commit hook 拦下：`Tests run: 113, Failures: 2, Errors: 4`。

**根因分析**：
1. **重载改的是生产调用点，测试 stub 未同步**：`ScoreGlobalMergeOrchestratorTest` 的 4 处 `when(mergeExecutor.resolve(anyList(), anyString()))` 仍 stub 2 参重载，而生产代码已调 3 参。Mockito strict stubbing 抛 `PotentialStubbingProblem`（"Strict stubbing argument mismatch"）；
2. **自选测试集 ≠ 门禁测试集**：我按"我改过哪些类"挑选测试，漏掉了未改动但**依赖被改方法签名**的 `ScoreGlobalMergeOrchestratorTest`。hook 的 `scripts/check-testing-standards.sh` 按变更目录推导完整选择集（114 个），覆盖到了；
3. **"保留旧签名委托"给了虚假安全感**：向后兼容只保护了**外部调用方**，保护不了**测试 stub**——stub 匹配的是运行时实际被调的那个重载，不是"仍然存在的"那个。

**解决方案**：
1. 7 处 matcher 全部升为 3 参：`resolve(anyList(), anyString(), any())`，同时覆盖 `when(...)` stub 与 `verify(..., never())` 校验（后者若留在 2 参会变成永真断言，失去意义）；
2. 提交前直接运行门禁脚本本身 `bash scripts/check-testing-standards.sh` 预检，而非逆向推断其 `TEST_LIST`；确认输出 `testing-gate: passed`。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 加参数重载后测试 stub 静默失配 | 只关注生产调用方是否兼容，忽略 mock 匹配的是运行时实际重载 | 改任何被 mock 的方法签名后，`grep` 全测试目录中该方法的 `when(`/`verify(` 调用点并同步升级 |
| `verify(mock, never())` 留在旧签名上变永真 | 旧重载确实从未被调用，断言"通过"但已无意义 | 签名迁移时 `never()` 校验必须一并迁到新签名，否则等于删除了该防护 |
| 本地自选测试全绿即提交 | 以"我改了哪些文件"选测试，而回归面是"谁依赖我改的签名" | 提交前跑门禁脚本本体（如 `check-testing-standards.sh`）而非自选子集；hook 的选择集推导比人工判断更全 |


## 208. Sidecar 适配器吞异常静默返回"空册"，"失败即中止"设计与 mock 抛异常测试双双失效（2026-09-02）

**背景**：AI 标书质量核查多册合并（PR !2510）设计语义为"任一册解析失败 → 整个任务失败、报错指名该册"。初版实现在 `ComplianceTargetLoader.loadBidDocument` 中 `catch (RuntimeException)` 包裹 sidecar 解析调用，并用 `when(sidecarAdapter.convertDocument(...)).thenThrow(...)` 的单测验证该语义，测试全绿。独立评审发现该防护在生产路径**不可达**：真实失败形态是扫描件/损坏册被静默并入合并文档，用 2/3 内容出"合格"报告。

**根因分析**：
1. **适配器吞异常的降级契约**：`SidecarStreamDocumentAdapter.convertDocument` 内部 `catch (Throwable)` 全吞，任何解析失败（sidecar 非 2xx、网络异常、二进制/超限跳过本地降级）都返回 `new DocumentContent(fileName, hash, "", [])`——**空文本 + 空页 + 有哈希**，从不抛异常；
2. **"失败即中止"建立在"会抛异常"的假设上**：catch 块包裹的异常路径在生产中不存在，空册静默穿透所有下游校验（异步链路只校验合并后 fullText 是否为空，其他册有文本则整体非空）；
3. **mock 抛异常验证失败语义 = 假绿**：被 mock 的适配器真实契约是"永不抛、返回空册"，`thenThrow` 验证的是一个生产中不存在的失败形态，测试与实现互为回声。

**解决方案**：
1. 调用侧显式拦截真实失败形态：每册解析后校验 `fullText().isBlank() && pages().isEmpty()` → 指名该册抛 `BusinessException`（宁可整体失败不可部分假查）；
2. 测试改为 mock 真实失败形态（返回空文册），原 `thenThrow` 用例降级保留为"适配器意外抛异常"的防御路径用例；
3. catch 包装保留为防御层（`catch (BusinessException) 先行重抛避免二次包裹`已扁平化，见 commit 9de2cf689）。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 上层"失败即中止"对吞异常的降级组件失效 | 假设依赖组件"失败会抛异常"，未核实其真实失败形态（返回哨兵值/空对象） | 对"内部 catch-all + 降级返回"的依赖，必须在调用侧对**返回值**做失败判据（如空文本+空页），不能只靠 try-catch |
| mock 验证失败语义用 `thenThrow` 假绿 | 写测试时按"我希望它怎么失败"构造 mock，而非"它实际怎么失败" | mock 第三方/适配器失败行为前先读其源码确认真实失败形态；降级返回空对象的组件，测试必须 mock 返回空对象而非抛异常 |
| 评审前的自测无法发现"防护不可达" | 测试断言的是自己刚写的 catch 分支，天然绿 | 对"安全网"类代码问一句：这条路径在生产里真的会走到吗？让不熟悉实现的人（或独立评审）从契约反推 |

## 209. 树结构叶子判定写成单点自判导致根叶颠倒，配对断言才能暴露（2026-09-03，spec 059 评审 P0）

**背景**：评分解析证据化（spec 059 Phase 2）的 `ScoreFact.isLeaf()` 实现为 `parentFactId == null || isBlank()` 返回 true——把"无父节点"（根节点）当成了"叶子"。校验器据此放行大类父项自动落库、拦截真正的细则子项，方向完全颠倒；更糟的是单测把反向断言（父项 `assertTrue(isLeaf())`、子项 `assertFalse`）硬编码钉死，测试全绿。

**根因分析**：
1. **叶子是集合属性，单点自判在数学上不可正确**：某 fact 是否为叶子取决于"集合中是否有其他 fact 把它的 factId 引用为 parentFactId"，单个对象自身无法回答。用 `parentFactId` 为空近似"叶子"偷换了"根"与"叶"的概念；
2. **测试跟着错误实现写**：断言从实现反推而非从领域语义推导（"origin 是合并大类所以是父项→不该是叶子"），错误语义被 `assertTrue` 固化成"预期行为"；
3. **单点断言天然测不出方向性错误**：只测 `fact.isLeaf()` 的返回值，没有"父项被拦截 + 子项放行"的成对校验行为断言。

**解决方案**：删除不可能正确的实例方法，改为 `ScoreFact.isLeaf(fact, facts)` 静态方法 + `parentIds(facts)` 求被引用 ID 集；校验器按 `parentIds.contains(factId)` 拦截；测试重写为成对断言（父项 violation + 子项无 violation 同例验证）。修复见 commit d6d0be693。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 叶子/根判定写成实体自判方法 | `parentFactId` 为空与"是叶子"表面相似，实现顺手取了近义条件 | 树语义（叶/根/环）判定一律以集合为参数；单 fact 方法只能回答"有没有父亲"，回答不了"有没有孩子" |
| 单测从实现反推断言 | 先写实现后补测试时，断言描述的是代码行为而非领域规则 | 树/图类测试必须成对断言正反两面（父项应被拒 + 子项应被放），单向断言无法区分"方向正确"与"整体颠倒" |
| 语义颠倒类 bug 全绿合入 | 每个测试单独看都自洽，缺一个跨实体行为用例 | "计数/判定方向"类逻辑让第二个视角（评审/成对不变量）复核一次 |

## 210. OOXML vMerge 纵向跨度按"任一 continuation"近似推断，被多列错位合并击穿（2026-09-03，spec 059 评审 P0）

**背景**：Sidecar `table_extractor.py` 的 `_v_merge_span_from_tc` 注释自认"无法从单个 tc 恢复列坐标"，改为"该行存在任一 vMerge continuation 即计一行"。当表格存在两列错位独立合并（列 0 在 r12-r13 合并、列 1 在 r24-r25 合并）时，列 0 的跨度被列 1 的 continuation 拉长（实测可从 2 误算成 4），随后 Java 端 `CanonicalTableGridFactory` PASS 4 覆盖校验发现 origin 槽位被原生单元格占据，把完全正常的表格判成 STRUCTURE_INCOMPLETE。

**根因分析**：
1. **近似推断违反 Plan 显式约束**：plan 明确"使用 Word XML 的 vMerge/gridSpan 显式计算合并覆盖范围，不再以'发现任一 continuation'推断整行跨度"，实现时用注释承认偏离而非解决偏离；
2. **调用点信息不足是自造成的**：跨度计算发生在行遍历内，此时逻辑列号 `c_idx` 已可解析——把计算提前到列号解析之前，才"不得不"近似。

**解决方案**：调用点移到光标解析出真实逻辑列之后，传入 `(logical_col, colspan)`；函数内对下方每行按 gridSpan 累加定位覆盖重启列的 tc，只有该 tc 自身是 vMerge continuation 才计数；新 restart 或未合并单元格终止跨度。新增错位双列合并回归测试（旧实现下必然失败）。修复见 commit db7d94613。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 用"存在任一特征"近似"对应坐标的特征" | 恢复精确坐标需要重构调用链，先抄近路 | OOXML 合并语义（vMerge/gridSpan）必须按列槽位精确算；注释可以承认妥协，但契约类妥协必须挂账而非固化 |
| 妥协注释写进代码后无人回查 | "NOTE: we approximate" 看起来像已知限制 | 评审对"承认近似的注释"按 bug 候选对待：它往往违反上游显式约束 |
| 单列合并测试全绿掩盖多列错位场景 | 夹具只有单处合并，污染无从发生 | 合并类夹具至少覆盖：单列纵、行内横、纵横复合、**多列错位**四种 |

## 211. 临时 worktree 无 node_modules 时 pre-push 门禁 eslint 假失败，与环境型失败混淆（2026-09-03）

**背景**：spec 059 任务 worktree（纯源码约定，不装依赖）推送时 `pre-push-gate.sh` 报"eslint — 有 lint 错误"。实际 `npm run lint` 的真实输出是 `sh: eslint: command not found`——门禁把"命令不存在"（环境缺失）与"lint 发现错误"（代码问题）同样按失败处理，输出文案误导排查方向。

**根因分析**：门禁脚本只看 `npm run lint` 退出码；`command not found` 也是非零退出，被归因为 lint 错误。分支全量 diff 实际零前端文件。

**解决方案**：本次在 worktree 内 `npm install`（545 包，约 1 分钟）后门禁 28 项全通过，未用 `PRE_PUSH_GATE=0` 绕过。另发现同环境 `npm ci` 不可用（仓库无 package-lock.json，只有 npm install 路径）。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 门禁把环境缺失误报为代码问题 | 只看退出码不区分失败形态 | 门禁失败先看**原始报错**再定性；"command not found / 模块缺失"走装依赖路径，不绕门禁 |
| 临时 worktree 每次收尾都会踩一遍 | worktree 纯源码约定与门禁的 lint/test 步骤天然冲突 | 长期对策（挂账）：门禁对 eslint 缺失给出明确文案或检测 node_modules；短期：收尾前先 `npm install` |
| `npm ci` 在本仓库不可用 | 仓库从未提交 package-lock.json | 前端依赖一致性靠 install 现算；如需可复现构建应提交 lockfile（另行决策） |

## 212. 外部 API 错误响应被当数据解析：401 字典被计成 1 条记录，鉴权失败被伪装成"无数据"（2026-09-03，gitee-pr-helper.sh list 崩溃）

**背景**：`scripts/gitee-pr-helper.sh list` 对 `curl -s` 响应不做任何校验，直接 `len(data)` 计数再逐条 `pr.get(...)`。token 失效时 Gitee 返回 401 错误字典 `{"message":"..."}`——被 `len()` 数出 1 个键，打印"找到 1 个开放 PR"后在解析循环抛 `AttributeError: 'str' object has no attribute 'get'`；非 JSON 响应（网关 HTML）则让 python 失败、被 `2>/dev/null || echo "0"` 吞成 0，把鉴权失败伪装成"无开放 PR，建议创建"。

**根因分析**：
1. **错误响应与正常数据同形地流入解析管道**：HTTP 状态码和 JSON 形状（list vs dict）两层校验全部缺失，`len(错误字典)` 与 `len(数据数组)` 在语法上都合法；
2. **单行管道 + stderr 静默**：`curl | python3 -c ... 2>/dev/null || echo "0"` 把"解析失败"与"空列表"折叠成同一个返回值，假阴性比崩溃更危险——它给出一个看起来正常的错误结论；
3. **同类坏模式在同一脚本里复制了 4 处**（list / create 已有 PR 检查 / check / mergeable 预断言），而 `approve` 子命令里其实早有正确的 `isinstance(data, list)` 写法——说明修复成本极低，缺的是统一收口。

**解决方案**：统一 `gitee_get`（curl `-w` 取状态码、body 落临时文件）+ `require_json_response`（非 2xx 显式报错、401/403 附带 token 更新提示、list/object 形状断言）；新增 `GITEE_API_BASE` 覆盖供 mock 契约测试；5 场景 18 项断言全绿（scripts/test/gitee-pr-helper-contract.sh）。修复见 commit c5ee9dfce（PR !2513）。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 错误字典被当数据计数/遍历 | 只写 happy-path 解析，`dict` 与 `list` 语法上都能被 `len()`/`for` | 调外部 API 一律先验 HTTP 状态码、再验 JSON 形状，两层都过才进业务解析 |
| 解析失败静默降级为"空结果" | `2>/dev/null \|\| echo "0"` 把异常折叠成正常值 | "失败"与"空"必须可区分；鉴权/网络类失败要带可操作提示（如更新 token） |
| token 有多套配置（shell 环境变量 vs MCP config.json headers），且 MCP 连接在会话启动时建立 | 改了 `~/.zshenv` 以为全好了；MCP 401 误判成 token 无效 | 认证链路分段验证：先用 curl 直接打 API 确认凭证本身；MCP 报 401 先分辨"配置错"还是"连接未刷新"（改配置必须重启会话） |

## 213. 阶段性切换 Worktree/分支感知漂移与重复修改不可变迁移陷阱（2026-09-03）

**背景**：在连续推进 Phase 5 合入后开启 Phase 6 时，本地 worktree 的 HEAD 曾被外部工具检出到旧分支 `review/pr-2515`（基线停留在 Phase 2，缺失后续提交），导致在查看代码时误以为 `score_parse_task` 缺失 `pipeline_version` 和 `decision_status` 字段，险些触发对已合入 main 的 `V1208` 迁移进行重写。

**根因分析**：
1. **多阶段流转中的分支感知盲区**：在分支频繁切换时，如果未在操作前用 `git status` / `git log -1` 确认 HEAD 与 `origin/main` 的对齐关系，很容易基于过期快照做出误判；
2. **直觉驱动的破坏性补救冲动**：面对"缺失字段"的第一反应若是不查历史、直接改写已发布 migration，将严重违反 Flyway 不可变性铁律（`check-flyway-immutable.sh`），导致生产环境 checksum mismatch 崩溃；
3. **真相源核验意识**：数据库变更的唯一真相源是 `origin/main` 上的 migration 序列（`V1207` 实际早已合入上述字段并建有索引）。

**解决方案**：
1. 立即切回目标分支 `agent/codex/score-evidence-phase6` 并 rebase 到 `origin/main`；
2. 坚守 Flyway 不可变性纪律，禁止对已合入 main 的 `V1208` 进行任何篡改；
3. 将全链路架构测试（`ScoreParseArchitectureTest`）与金标回放测试（`ScoreParseEvidencePipelineGoldenReplayTest`）纳入门禁与 CI。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 过期分支代码引发假阳性"字段缺失" | HEAD 停留在历史 PR review 分支，缺少主干最新 commit | 开工三部曲必看 `git status` + `git log -1`，确保本地基于最新 `origin/main` |
| 试图修改已合并的 Flyway 迁移 | 误以为新增需求可以通过修改前序迁移解决 | 已合入 main 的 `V*.sql` 具有永久不可变性，任何新增字段必须开辟下一版本迁移号 |

## 214. 算法策略重构与前端解释性 Tooltip 文案脱节，导致规则呈现算术溢出（2026-09-03）

**背景**：在"AI 智能案例推荐"抽屉中，悬浮展开的"评分依据说明"显示匹配度计算规则满分 100 分，但各项明细分值相加（40 + 20 + 15×2 + 15 + 5 = 110 分）超出 100 分，直接暴露算术硬伤并引起用户质疑。

**根因分析**：
1. **初期文案照抄旧策略常数且语义扭曲**：旧 `KnowledgeCaseMatchPolicy` 定义了 6 项常数（40/20/15/15/15/5，合计 110，靠 `Math.min(score, 100)` 截断）。前端在实现浮层时直接写为"项目/客户类型一致：各 +15 分"，将原本就溢出的分值固化到了 UI 上；
2. **算法重构后遗留文案未闭环同步**：PR !2507 为解决三峡项目被抢占问题，将核心打分算法（`ScoreItemCaseMatchPolicy`）重构为包含专属实体断层加权的 8 维度加权模型（实体 25 / 条款 30 / 标题 20 / 类别 15 / 项目类型 10 / 客户类型 5 / 搜索 5 / 中标 5），但未同步修改前端解释性 Tooltip，致使 UI 规则描述与后端真实实现严重脱节。

**解决方案**：
1. 更新 `AiRecommendDrawer.vue` 的 Tooltip 文案，完整对齐 `ScoreItemCaseMatchPolicy` 的真实 8 维度权重（专属实体 25、条款 30、标题 20、类别 15、项目 10/客户 5、搜索/中标各 5），彻底消除 110 分溢出；
2. 同步更新 `AiRecommendDrawer.spec.js` 的断言及 `.wiki/pages/ai-case-recommend-data-source-fix.md` 知识库文档。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 后端算法权重优化，前端解释性 Tooltip 遗漏同步 | 变更只关注了算法类和单测，未将配套的 UI 文案纳入变更清单 | 算法策略重构必须全链路检索前端引用与 Tooltip/Popover 文案，保持 UI 解释性与后端实现同步 |
| 规则说明文案分值加和超过声明的满分 | 盲目照抄带 min(score, 100) 兜底的后端常量，未做文案层算术验算 | UI 展示的满分规则必须在表面逻辑上自洽，分项分值加和必须与总分严格吻合 |

## 215. Gitee token 多源打架：有效 token 被"看不见"，兜底工具兜回死 token（2026-09-03，各 Agent 提 PR 反复"找不到 token"数月根因）

**背景**：远端迁移 Gitee 后数月，各 Agent 完成任务提交 PR 时反复报"找不到 GITEE_TOKEN"或 401，同机不同时刻结局不同，呈"玄学"。实测定位：同一台机器上 `~/.zshenv` 的 `GITEE_TOKEN` 打 API 是 **200 有效**，而 `scripts/gitee-token.sh`（专门写的解析器）返回的 token 却是 **401 死 token**；`~/.git-credentials` 里积累 3 条历史 gitee 条目；`launchctl`（GUI 应用）环境里 token 为空。

**根因分析**：
1. **token 可达性取决于进程树的 shell 类型，而非仓库**：`~/.zshenv` 只有 zsh 读（`~/.zshrc` 仅交互式 zsh 读，bash/GUI 全都不读）。从交互终端启动的 agent 能继承 token；bash 子进程、GUI/launchd 启动的应用、清理过 env 的 harness 全部读不到。同一个 `LINEAR_API_KEY` 只写在 `~/.zshrc`，非交互会话实测为空——机制在自家环境就有活体证据。
2. **脚本防线不一致**：`pr-create.sh` 有人补过 `~/.zshenv` grep 兜底；主路径 `gitee-pr-helper.sh` 无任何兜底（env 空直接报错）；`agent-finish-task.sh` 与 `gitee-auto-merge-gate.sh` 在 token 缺失时**静默跳过**（PR 明明开着却报"分支尚未合入"/"跳过自动合并"）。Agent 走不同链路，结局完全不同。
3. **兜底工具优先级倒挂**：`gitee-token.sh` 把 `git credential fill` 排在环境变量**前面**，而 `~/.git-credentials` 积累的 3 条历史条目里第一条就是过期 PAT——兜底工具自己兜回 401 死 token。
4. **报错文案误导**：401 统一提示"请更新 ~/.zshenv 中的令牌"，用户更新后部分链路照旧 401（token 是好的，链路读不到），形成"改了没用"的循环困惑。

**解决方案**：
1. `scripts/gitee-token.sh` 重写为四级解析链：**env → `~/.config/xiyu/credentials.env`（推荐唯一真相源，chmod 600）→ `~/.zshenv` grep（历史兼容）→ `git credential fill`（最后手段，且 `GIT_TERMINAL_PROMPT=0` 防挂死）**；`GITEE_TOKEN_DEBUG=1` 可打印命中来源（不打印 token 值）。
2. 五个 Gitee API 脚本统一接线：`gitee-pr-helper.sh` / `pr-create.sh` / `agent-finish-task.sh` / `check-branch-hygiene.sh` / `gitee-auto-merge-gate.sh` 在 token 为空时自动调解析器；"静默跳过"全部改为显式提示 + 修复路径；401 文案改为指向解析链而非单指 `~/.zshenv`。
3. 契约测试 `scripts/test/gitee-token-contract.sh`：假 HOME 隔离覆盖 4 级优先级、引号写法兼容、调试输出、四级全空报错、helper 集成兜底与 401 提示，14 项断言（存量 helper 契约 18 项回归全绿）。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 凭证只放 shell rc 文件，agent 读不到 | 误以为 `~/.zshenv` 是全局的；实际 bash/GUI/非交互进程全不读 | 凭证落盘到 shell 无关的文件（`~/.config/xiyu/credentials.env`，600 权限），脚本侧用解析链兜底，脱离 shell 类型可解析 |
| 兜底解析器把 `git credential fill` 排第一 | fill"看起来最标准"，未意识到 `~/.git-credentials` 会积累过期条目且 fill 只回第一条 | credential store 类来源永远排解析链最后；优先显式注入的环境变量与显式维护的凭证文件 |
| token 缺失时脚本静默跳过/统一报"令牌过期" | 降级逻辑只求不崩，没考虑调用方（Agent）会把降级误读为业务结论 | 降级必须显式宣告"为什么跳过 + 去哪修"；报错文案指向修复动作而非猜测原因 |

## 216. 标书质量核查「投标函格式规范」在谈判/采购场景对《报价函》与公文称谓的误判与目录干扰（2026-09-03）

**背景**：在项目 1358（中国雅江集团供应商引入谈判采购项目）标书质量核查中，系统报告“投标函格式规范：不通过，未检索到投标函独立章节”，但用户指出标书实际包含极其规范的独立函件章节。

**根因分析**：
1. **识别词表过度单一，未覆盖法定采购函件名称**：`BasicInfoCheckPolicy.checkBidLetterFormat` 中硬编码只检索 `"投标函"`。在竞争性谈判、竞争性磋商、询价等采购项目中，依法定范本与招标文件，其独立章节命名为《报价函》、《响应函》或《谈判响应函》。该标书中“投标函”出现 0 次，而《报价函》作为法定章节完整存在，导致被直接判为未检索到章节；
2. **贪婪首页匹配陷入目录（TOC）陷阱**：文档首页通常为目录（TOC），包含 `[二、 文件二 报价函 3](#_Toc...)` 超链接。若只按关键词简单取第一页命中，会把第 1 页目录当成章节页，而目录页不含函件正文与称谓，造成假阳性/降级复核；
3. **公文称谓（Salutation）校验过于僵化**：代码原先只支持字面 `致：` 或 `致:`，而央国企招标文件模板中广泛采用 `xxx公司（采购人名称）：`、`（采购人名称）：`、`（招标人名称）：` 等公文称谓规范，导致无法识别标准公文抬头。

**解决方案**：
1. 扩充识别词表：定义 `BID_LETTER_KEYWORDS`（覆盖投标函、报价函、响应函、谈判响应函、磋商响应函等及其空格分隔形式），并统一选页判定与证据摘录（`snippetAround`）；
2. 优化选页逻辑：遍历候选页，优先选择同时具备称谓与承诺要素的实质正文章节页（跳过纯目录链接干扰）；
3. 增强称谓多模态识别：除 `致：` 外，正则支持采购人/招标人法定占位符及公文单位结尾加冒号抬头；
4. 补充 5 组针对报价函、目录择优、响应函及人工复核分支的纯函数单元测试。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 质量核查规则只适配公开招标，忽视谈判/磋商采购业务场景 | 开发者潜意识将标书等同于"投标函"，忽略了政府采购与非招场景法定用语是"报价函/响应函" | 招投标领域规则词表设计必须覆盖采购、谈判、磋商全生命周期的法定文书变体 |
| 关键词检索直接取第一页，被目录页截胡 | 标书开头通常带目录（TOC），目录中必然包含章节名 | 选页判定必须结合实质要素（如称谓+承诺），优先命中具备正文特征的页面，抵御 TOC 链接干扰 |
| 称谓校验只写死 `致：` | 未调研真实央国企标书模板多样性 | 文本特征匹配应兼容行业公文标准格式（如 `（采购人名称）：`） |

## 217. AI 评分解析「越改越错」：all-or-nothing 落库门禁静默丢弃成功解析 + 合成夹具循环验证（2026-09-03）

**背景**：项目 1358（中国雅江集团供应商引入谈判采购）「AI 评分标准解析」反复被报「漏识别/评分项缺失」。团队已提交近百个 commit 优化解析链路，但用户反馈「越改越错」。测试服实查一锤定音：1358 当天**实际解析成功**（11 项、合计 100 分），却被判 `REVIEW_REQUIRED` + `WEIGHT_SUM_MISMATCH`，整批结果**不落库**，前端只展示昨天的 4 个陈旧垃圾项（报价 78 分）。

**根因分析（两层叠加）**：
1. **脆弱启发式假阳性**：`WeightSumCheck.checkCandidates` 的 `declaredMismatch` 交叉校验，把「声明值来自子项 contextNote、但子项 dim 不含大类词、各自归到完整 dim 桶」的大类，误判为「声明 vs 实际不符」。该大类在 `familySums` 中根本没有任何成员，交叉校验无意义，却触发 `needRecheck` → `REVIEW_REQUIRED`。
2. **all-or-nothing 落库门禁（spec 059 Phase 4）**：`ScoreParseAppService` 一旦决策非 `AUTO_APPROVED` 就**整批不落库**，却照样 `markCompleted`，且**无人工复核放行通道**。于是一次成功的解析被静默丢弃，用户只能看到上次陈旧项——这正是「漏识别」的真相，也是「越改越错」的机制：不断加门禁/启发式，每次假阳性都让成功结果消失得更彻底。

**解决方案（A+B，含单测）**：
1. **Fix B（消除假阳性）**：`WeightSumCheck` 加 `familySums.containsKey(F)` 守卫——只跳过「无任何成员」的大类交叉校验，保留合法的「声明 vs 实际分布不符」校验（`checkCandidates_declaredDimensionMismatch_triggersRecheck` 仍绿）。1358 据此回到 `AUTO_APPROVED`。
2. **Fix A（数据无关兜底）**：`REVIEW_REQUIRED` 不再 all-or-nothing 丢弃。新契约——产出有效项且结构未损坏（`AUTO_APPROVED`/`REVIEW_REQUIRED`）即落库展示，复核标记仍留在 `task.decisionStatus/fallbackReason/normalizationWarning` 供前端提示；仅 `STRUCTURE_INCOMPLETE` 保留现有项不覆盖。**永不静默丢弃一次成功的解析。**

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 把「需复核」实现成「不落库」，还照样 markCompleted | 误以为「不展示可疑结果」是安全的；实际是把成功结果连同假阳性一起静默吞掉，用户看到的是更糟的陈旧数据 | 失败要响、失败要安全：可疑结果应**落库 + 打复核标记**，而非 all-or-nothing 丢弃；任何「成功但被拦截」的路径都必须有可见的人工放行通道 |
| 启发式交叉校验对「无成员大类」假阳性 | 只校验「声明值 vs 实际和」，没先确认该大类在实际分桶里有没有成员 | 任何 A vs B 交叉校验，先确认 B 侧非空再比较；空集不是「不符」，是「无从比较」 |
| 用合成夹具「对齐已通过的测试布局」做验证 | 真实 1358 文档不可访问，遂用合成夹具复刻猜测布局，测过即认为修对——典型循环验证 | 验证必须锚定**真实失败文档**端到端（含 LLM/落库断言）；合成夹具只能钉确定性单元行为，不能替代真实回归（根治项 Fix C：用真实 1358 建可执行金标，本轮未做） |
| 「不断加门禁」被当成「不断优化」 | 每加一道启发式/门禁都局部合理，但叠加后假阳性面积指数增长，且无端到端真实回归兜底 | 解析类链路每加一道否决门禁，必须配一条「该门禁不会误杀已知成功样本」的真实回归断言 |

**Fix C 后续落地（2026-09-03，闭环本挂账）**：已建可执行端到端金标根治循环验证——
- **脱敏金标（CI 常驻）** `ScoreCompletenessAcceptanceTest#incident1358_closedLoopWithDeclaredFamilyLackingMembers_persistsAllItems_autoApproved`：用结构忠实复现 1358 触发形态的 11 项 / 满分恰好 100 闭环夹具（商务组 `dim` 不含大类词、`contextNote` 声明「商务评分标准（56分）」→ `declaredFamilyWeights={商务:56}` 在 `familySums` 无成员），跑通**真实生产链** `executeParseAsync → ScoreCandidatePostProcessor → WeightSumCheck → persistItems`，断言 `AUTO_APPROVED` + 无复核标记 + 11 项全部落库；还原 Fix B 守卫即转红，证明它真能抓回归而非自证。
- **真实数据金标（语料门控，CI 跳过）** `Incident1358RealCorpusGoldenTest`：`@EnabledIfEnvironmentVariable(XIYU_EVAL_CORPUS_DIR)`，本地放置离线录制的真实候选快照 `incident-1358/candidates.json` 后，用真实数据跑同一条生产链断言相同金标。真实文档仍**禁止入仓**（合规），green 路径需有语料的开发机验证。
- 登记见 `docs/lessons/scoreparse-golden-corpus/index.md`「1358（权重闭环/落库）」行；回归切片 629 tests / 0 failures / 1 skipped（Part B 门控跳过）。

## 218. AI 标书质量核查资质匹配"0 匹配"与无文件解析根因及三层穿透（2026-09-03）

**背景**：
用户反馈 AI 标书质量核查中的资质匹配完全没有核查效果，核查几个项目都是"无数据"或报"未在招标文件中提取到明确的硬性资格要求"。排查发现三大根因：
1. **短串反向包含长串导致 0 匹配**：`KnowledgeBaseOnDemandLoader.matchQualifications` 使用 `q.getName().contains(kw)`，将短证书名（如"ISO9001质量管理体系认证"）反向包含大模型提取的完整长句（如"投标人须具备有效的质量管理体系认证（ISO9001）证书且在有效期内"），导致生产环境下匹配恒为 false，资质库 0 匹配。而旧单测因使用人工简写 `"ISO27001"` 掩盖了该缺陷。
2. **招标文件未穿透回退引发空文档熔断**：`TenderDocumentLoader` 仅查 `project_documents` 的 `TENDER`/`TENDER_FILE` 分类。若项目立项时仅在标讯带入 `Tender.source_document_file_url`，而未实体化落盘 project_documents，直接返回空文档，进而触发 `requirements.missing() = true` 熔断。
3. **核查结果缺乏结构化条目**：前端详情弹窗只有单段文本，缺少逐项"招标要求 vs 我司证书 vs 标书页码"的结构化对照。

**解决方案**：
1. **纯核心智能匹配策略（`QualificationMatchPolicy`）**：建立 FP-Java 纯函数算法，支持标点空格归一化、双向包含、标准体系代号（ISO9001/ISO27001/CMMI/ITSS等）精准正则提取、行业领域词识别、有效 LCS 判定及临期预警，并在 `KnowledgeBaseOnDemandLoader`（资质库与人员库）全面应用。
2. **招标文件三级穿透加载（`TenderDocumentLoader`）**：一级查 `project_documents` 标准 TENDER 分类；二级按文件名启发式（"招标/标书/采购文件"等）识别项目文档；三级向上穿透回退到 `project -> tender.source_document_file_url`，彻底打通招标文件来源。
3. **结构化比对清单下发与弹窗呈现**：新增 `QualificationDetail` 领域模型并集成到 `QualityCheckItemResult` 与报表 DTO，前端在 `QualityCheckDetailDialog.vue` 中渲染结构化比对表格，清晰展示招标要求、我司持有证书、剩余天数、标书页码与核查说明。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| `dbShortName.contains(llmLongSentence)` 反向包含假阴性 | 单元测试使用人工简写字符串（"ISO27001"），掩盖了生产中大模型提取长自然语言句子的事实 | 关键词匹配必须支持双向模糊匹配、标准代号提取与归一化，单测必须使用大模型真实长文本作为测试用例 |
| 跨模块数据未打通回退路径导致空文档熔断 | 模块各自为政，假定所有项目在立项后都已在 project_documents 实体化落盘 TENDER 文档 | 关键资源加载器应具备三级优雅回退机制（显式分类 -> 启发式文件名 -> 溯源实体 URL） |
| 核查依据仅以非结构化字符串展示 | 忽略了用户需要逐条对照资质、证书到期日、投标文件页码的直观交互需求 | 核心质检项必须在领域模型中下发结构化比对明细，提供直观的对照表格/卡片 |

## 219. 招标文件资格与资质要求"漏识别"根因、代码围栏容错与双轨融合架构（2026-09-03）

**背景**：
在第 156 次部署（含 PR !2531 解决 0 匹配与文件穿透）后，用户在测试环境复测项目 1358（`https://winbid-test.ehsy.com/project/1358`）依然提示"未在招标文件中提取到明确的硬性资格要求，请对照标书资质证明文件人工复核"。
用户指出关键领域常识：**"任何招标文件都一定是有资格和资质的，不然的话没法招标。所以不存在没有，只是我们没有识别出来。"**
真实招标文件 1358 原文包含极其清晰的公文结构：
- `3. 供应商资格要求`
- `3.1 通用资格条件`（法人、民事责任能力、信誉、信用中国失信名单等）
- `3.2 专用资格条件`
  - `3.2.1 资质要求：供应商应具有以下证书：①增值电信业务经营许可证... ②公安部信息系统安全等级保护备案证明第三级或第三级以上证书。`

排查发现两大底层技术根因：
1. **`TenderRequirementsParser` 缺少 Markdown 代码围栏剥离（致命反序列化崩溃）**：
   `TenderRequirementsParser.parse` 直接调用 `OBJECT_MAPPER.readTree(llmJson)`。由于大模型常返回带 ````json` 围栏或前后带有说明文字的文本，Jackson 解析直接抛出 `JsonProcessingException` 并降级为 `missingSentinel`（`missing = true`），从而在 `SemanticRulesLane` 触发兜底提示。
2. **全篇 30000 字符粗粒度提取与注意力稀释**：
   全篇分为大 chunk 送给大模型，同一 prompt 中同时要求提取 5 种类目，注意力被稀释，且完全依赖大模型概率，缺少基于中国招投标标准公文格式的确定性规则锚定。

**解决方案**：
1. **纯核心确定性资格提取器（`DeterministicQualificationExtractor`）**：
   纯 FP-Java 策略，正则锚定 `(?:供应商|投标人|申请人)?(?:资格要求|资质要求|资格条件|资质条件|资格审查|专用资格条件|通用资格条件)` 章节，通过标号（`①-⑩`、`（1）-(99)`、`3.1.1-3.2.9` 等）精细切分条款，清洗汉字折行与标号前缀，自动过滤占位符（如 `人员要求：/`、`财务要求：/`），精准捕获专用资质（`增值电信业务经营许可证`、`等保三级`、`ISO`、`施工资质`、`安全生产许可证` 等）与通用资格（法人资格、信用中国失信限制等）。
2. **`TenderRequirementsParser` 增加首个配平 JSON 对象提取（`extractJsonObject`）**：
   引入括号配平扫描器，彻底剥离 markdown 围栏与前后缀文字，杜绝格式噪声引发的降级。
3. **质检主流水线双轨融合（Rule-First + LLM-Enhanced）**：
   在 `BidQualityCheckAsyncExecutor` 中执行双轨融合：规则提取的确定性资质与 LLM 提取的资质并集去重（`requirements.withMergedQualifications(deterministicQuals)`）。只要规则或大模型任一侧提取出资质，`missing` 即置为 `false`，彻底杜绝误报。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 纯依赖大模型概率提取领域刚性公文条目 | 误以为大模型是万能提取器，忽略了招投标文件具备强法律规范与标准章节编排（《招标投标法》第 18 条） | 核心业务刚性字段必须"规则兜底优先 + 大模型增强提炼"，任何公文型文档必须有确定性章节正则定位器 |
| LLM 响应解析直接 `readTree(raw)` 未防守代码围栏 | 未对大模型可能输出的 ````json` 围栏和对话前后缀做防御，导致合法 JSON 内容因格式噪声被整段判废 | 所有 LLM JSON 解析适配器必须先做括号配平扫描提取（`extractJsonObject`），剥离围栏与对话文字 |
| 任务级 `missing` 哨兵一刀切屏蔽比对 | 单次 LLM 提取失败直接将整个 `TenderRequirements` 置为 missing，抹杀了已有规则提取成果 | 状态设计应支持增量融合，只要局部规则提取出有效资格，即可解除 missing 并放行后续匹配 |

## 220. 质检查错企业资质库底层表导致"资质库未维护"误报，及国字头前缀语义对齐（2026-09-04）

**背景**：
在 PR !2533 / !2534 解决确定性资格提取后，项目 1358 成功提取出专用资质要求：
`增值电信业务经营许可证，业务种类及覆盖范围需包括信息服务业务（互联网信息服务）及在线数据处理与交易业务（经营类电子商务）`，标书第 167 页也正确定位到证明材料。但系统界面提示：`（资质库未匹配）需关注 资质库未维护该证书，但在标书第 167 页检索到证明材料，请人工核查有效性`。
用户指出：**增值电信业务经营许可证在资质库里明明有，为什么没识别出来？**

**排查发现两大根因**：
1. **质检查错了底层表（致命数据源错配）**：
   `KnowledgeBaseOnDemandLoader` 历史注入了单数实体 `QualificationRepository`，其映射的物理表为 `qualifications`。在生产/测试 RDS 上查询该表行数恒为 0（早期废弃空表）。
   而系统真正的企业资质库业务模块使用的是表 `business_qualifications`（对应 `BusinessQualificationRepository`，内含 62 条在库资质，包含 `id=46, name='中华人民共和国增值电信业务经营许可证', status='IN_STOCK', retired=0`）。因为加载器查的是空表，查出列表恒为 `[]`，比对引擎拿不到任何证书，因此必然判定为"资质库未维护该证书"。
2. **国字头前缀与电信专有名词语义未对齐**：
   招标文件提取的要求为`增值电信业务经营许可证...`，资质库中登记的标准全称为`中华人民共和国增值电信业务经营许可证`。原 `QualificationMatchPolicy` 的 `DOMAIN_TERMS` 未收录"增值电信"等词，且未剥离"中华人民共和国"等常见行政前缀，导致直接 `contains` 无法双向穿透。

**解决方案**：
1. **优先接入真实企业资质库 `BusinessQualificationRepository`**：
   在 `KnowledgeBaseOnDemandLoader` 注入 `BusinessQualificationRepository`，优先查询 `business_qualifications` 表中未注销（`retired = false`）有效记录，转换为资质模型；同时保留对历史单数表的降级兼容与单测兼容。
2. **国字头前缀剥离与行业专有词扩充**：
   在 `QualificationMatchPolicy` 中建立 `GOV_PREFIX_RE` 正则（剥离"中华人民共和国/全国/中国/国家"等前缀），在双向包含判断中对剥离前缀后的核心资质名执行穿透包含；在 `DOMAIN_TERMS` 中扩充"增值电信"、"电信业务"、"互联网信息服务"、"等保"等词库；在标书页码定位 `cues` 中加入剥离国字头后的证书名，确保标书页码定位精准命中。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 质检加载器查错已废弃的历史空表 | 模块演进过程中新建了 `business_qualifications`（DDD 重构产物），但 compliance 模块仍旧依赖旧版 `qualifications`，单测又全部使用 Mock 伪造数据，掩盖了真实库为空的事实 | 1. 跨模块调用优先使用该领域活跃的 Domain Port（如 `BusinessQualificationRepository`）；2. 单元测试除 Mock 外，关键业务链路需对齐真实物理表命名与数据形态 |
| 资质名称带有法定行政前缀（"中华人民共和国"）导致长句包含失败 | 资质库录入法定正式全称（国字头），招标文件需求通常省略前缀直接写简称 | 资质匹配算法必须在归一化阶段剥离通用行政修饰前缀（`stripGovPrefix`），用核心法定名称与需求做双向模糊穿透 |

## 221. 资质匹配 LCS 算法通配词泛滥与贪心有效期排序致证书错配，及章节导语误提取（2026-09-04）

**背景**：
在 PR !2536 对接真实业务资质库后，项目 1358 再次实测质检比对清单时出现两大严重瑕疵：
1. **清单出现多余标题导语行**：提取列表中出现了 `2.1 资质要求：供应商应具有以下证书`，属于引导句而非资质条目；
2. **资质严重错配为无关证书**：
   - `增值电信业务经营许可证，业务种类及覆盖范围...` 被错配为 `医疗器械经营许可证`；
   - `1.1 供应商须为中华人民共和国境内合法注册的独立法人...` 被错配为 `中华人民共和国增值电信业务经营许可证`。

**排查发现根本原因**：
1. **LCS 停用词库未包含通用证照后缀与国家前缀**：
   原 `STOP_WORDS` 仅过滤了 `投标人须具备`、`资质证书` 等少数字词，未过滤 `"经营许可证"`（5 字符）与 `"中华人民共和国"`（7 字符）。
   - 招标需求 `增值电信业务经营许可证...` 与资质库 `医疗器械经营许可证` 共享公共子串 `"经营许可证"`（长度 5 >= 4 阈值），导致被判定为匹配；
   - 通用法人类条款与 `中华人民共和国增值电信业务经营许可证` 共享公共子串 `"中华人民共和国"`（长度 7 >= 4 阈值），也被误判匹配。
2. **多候选挑选机制贪心按有效期倒序（忽略匹配相关度）**：
   `findBestMatchingCert` 在匹配到多个候选证书后，直接执行 `matched.sort(Comparator.comparing(getExpiryDate).reversed())` 取第一个。
   在资质库中，`医疗器械经营许可证` 有效期至 2030-08-13（剩余 1439 天），而 `中华人民共和国增值电信业务经营许可证` 有效期至 2030-03-28（剩余 1301 天）。
   虽然增值电信是 100% 核心领域完全包含，但因有效期短于医疗器械许可证，竟被弱相关的医疗器械证书强行顶替篡位！
3. **提取器未对冒号结尾及指示性导语做结构化判别**：
   `DeterministicQualificationExtractor` 在遇到 `2.1 资质要求：供应商应具有以下证书：` 时，将其独立切块，并因命中"资质"与"证书"关键词而通过校验放行；前导标号清洗未防守分号`;`，导致后续条款携带 `; （4）` 残留。

**解决方案**：
1. **`calculateMatchScore` 相关度多级打分与打分第一排序原则**：
   重构 `QualificationMatchPolicy` 匹配机制：完全包含（1000+） > 标准代码（500+） > 行业专有词（200+） > LCS（100+） > 0。
   在挑选最佳匹配证书时，**以匹配相关度打分为绝对第一主键**，有效期仅作为相同打分梯队内的决胜键。
2. **全面收紧通用停用词**：
   扩充 `STOP_WORDS`（加入 `"中华人民共和国"`, `"全国"`, `"中国"`, `"经营许可证"`, `"许可证"`, `"业务经营许可证"`, `"生产许可证"`, `"管理体系认证"`, `"服务认证"`, `"独立法人"`, `"民事责任能力"`, `"合法注册"` 等通用词）；
   `isStopword` 逻辑升级：优先保护 `DOMAIN_TERMS` 核心专有名词，对其余通用词严格拦截。
3. **提取器与模型层双重导语过滤及标点清洗**：
   在 `DeterministicQualificationExtractor` 与 `TenderRequirements` 中增加 `isIntroductoryHeading` 正则判定（过滤包含"以下证书"、"下列条件"、冒号结尾及章节纯标题），并完善前导 `;` 等标号标点清洗，彻底清除标题噪点。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 匹配系统用业务属性（有效期）替代相关度（Relevance）做首要排序 | 开发时直觉认为"同一类证书应该选有效期最长的"，却忽视了模糊匹配中可能混入弱相关假阳性候选 | 任何检索与比对系统必须严格遵循"相关度优先"原则，业务倾向属性（如最新、最长有效期、最高星级）只能在同等相关度打分集合内作为 Tie-breaker |
| LCS 等字符重合算法未对通用实体词（"经营许可证"、"中华人民共和国"）做强阻断 | 汉字证照名称结构通常为`[行政前缀] + [专有领域] + [证件通用类别后缀]`，只重合前缀或后缀根本不是同一证书 | 证照类名称模糊匹配必须以[专有领域词]或[核心证书名完全包含]为必要条件，通用类别后缀绝不可单独作为判定依据 |
| 文本切块未识别引导句（以冒号结尾或带"以下/下列"） | 正则分条通常只按标号和换行切分，把承上启下的段落提示语切成了一整条独立数据 | 提取规则必须包含指示代词过滤（"以下"、"下列"）和冒号导语识别，从源头剔除章节引导性噪点 |

---

## 222. AI 案例推荐切词碎末污染与大类穿透缺陷，及标点分层实体抽取与 FP-Java 架构重构（2026-09-04）

**背景**：
在项目投标方案编制阶段，用户反馈 AI 智能案例推荐中匹配案例的“命中关键词”极不准确，出现大量碎片无意义切词（如针对“朱双双的标识不可编辑”切出了“双双”、“双的”、“的标”、“不可”、“可编”等）。同时，技术评分项常常误推荐纯商务/财务案例，违背了“推荐案例必须符合招标文件和投标文件内容，尤其是技术方案和商务方案精准匹配”的第一性原理。

**根因分析**：
1. **机械字符滑窗与停用词穿透**：`TextSimilarityPolicy.extractTokens` 对长于 8 字的子句退化为字符级 2-gram/3-gram 暴力滑窗，且跨词碎片（如“双的”、“的标”）完美避开停用词库，优先抢占了 `matchedKeywords` 前 8 个展示槽位，把顿号（`、`）连接的核心业务实体挤出界面；
2. **大类软加分缺乏互斥门禁**：`ScoreItemCaseMatchPolicy` 历史仅给大类一致性赋予 15 分软加分，在缺少互斥否决的情况下，纯财务案例凭借客户与项目加分即可轻松突破 50 分阈值渗透至技术评分项下方；
3. **纯核心 FP-Java 规范违背**：前序尝试中提取器方法返回 `void` 且原地修改入参集合，违反了 FP-Java 第 1 条与第 4 条约束，被 `FPJavaArchitectureTest` 门禁拦截。

**解决方案**：
1. **标点分层实体抽取器（`PunctuationEntityExtractor`）**：
   纯 FP-Java 纯函数设计，分层利用分号/换行切分子句、顿号（`、`）提取高纯度并列业务实体、引号提取专有名词，配合正负向环视正则保护连词（避免切断“及时响应/涉及范围”），从源头阻断滑动碎片进入展示关键词池；
2. **大类冲突一票否决门禁（`CaseCategoryMatchPolicy`）**：
   重塑宏观大类模型（解耦 `TECHNICAL`、`COMMERCIAL` 与 `PERFORMANCE` 业绩类），在评估入口设立强互斥门禁：纯技术与纯财务案例冲突时一票否决得 0 分，杜绝项目加分穿透；
3. **严格重构为无副作用纯函数**：
   消除所有修改入参的隐蔽代码，引入只读 Record `EntityMatchResult`，让策略类通过 `FPJavaArchitectureTest`、`check:line-budgets` 等 6 道工程化门禁。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 为计算相似度而切出的 n-gram 碎词直接外溢到前端展示 | 混淆了"内部相似度数学计算的 token"与"面向用户可读的高质量展示关键词"的职责边界 | 内部打分可保留 n-gram 或 embedding 计算，但展示关键词必须有独立的实体抽取与质量守卫流水线 |
| 跨大类匹配仅用软加分，未设强互斥门禁 | 认为多维度加权算分即可自然拉开差距，忽视了项目实体/客户名称的断层高加分会导致跨大类渗透 | 具有业务硬约束的场景（技术标 vs 商务标）必须在评分引擎最前置设立一票否决门禁（Hard Gate） |
| 领域策略方法通过修改集合入参传递中间结果 | 沿用传统面向对象或指令式编程习惯，隐式修改入参集合 | 严格遵守 FP-Java 规范：纯核心方法必须返回新对象或不可变数据结构，禁止任何形式的原地参数变异 |

---

## 223. AI 案例推荐二次复发：追症状不追根因导致 2-gram 机械滑窗遗留，及基于真实日志回放与语义层级提炼的根治（2026-09-04）

**背景**：
首轮修复（PR !2540）合并并部署到测试系统后，用户反馈抽屉里的命中关键词依然毫无变化，依然出现“朱双/双双/双的/的标/讯不/不可/编辑”等碎片词，并询问是否是缓存或为何无法重新生成。

**全链路日志排查 SOP 现场证据**：
1. **缓存排除**：进入测试服务器（172.16.38.78），查验 `deployed-release.json` 确认 Release `4fd73000` 已经部署；查验源码与 JVM 运行指标，确认 `ScoreItemCaseRecommendAppService.recommend` 是**纯内存无状态动态计算**（耗时 80~170ms），根本没有任何 Redis 或本地持久化缓存，每次前端打开抽屉都是全新实时算分，不存在“缓存未更新”问题。
2. **真实日志与接口回放**：
   在 `/var/log/xiyu-bid/application.json.log` 中捕获真实请求 `POST /api/projects/1358/cases/recommend`（traceId=`fd3ff3ddb363427eb513dd4951e7916a`）；
   使用测试凭证在测试服务器本地回放项目 1358（项目名：`朱双双的标讯不可编辑`），抓获真实返回：
   - `keywords`: `["朱双双的标讯不可编辑", "朱双", "双双", "标讯", "讯不", "不可", "编辑"]`
   - `reasons`: `["命中项目专属实体(的标、不可)"]`

**七大根因归属判定（engineering-discipline.md）**：
1. **首要根因：【追症状不追根因】（典型案例 CO-361）**：
   第一轮修复在看到用户反馈的“双的/的标/可编”碎片词后，直接假设是评分项 detail 字段被顿号切碎，仅在 detail 的提取器和噪音清洗上打补丁；压根没有抓真实服务器日志追溯输入源，没有发现这些词的“零号病人”其实来自当前项目名称字段（`projectName: "朱双双的标讯不可编辑"`）。
2. **次要根因：【测试不测根因】+【盲目相信“已修复”】**：
   首轮编写的测试只验证了 detail 标点抽取函数，单测全绿（99/99）形成了“已修复”的假象，未覆盖带长项目名/特殊项目名的推荐策略评估，合入后也未第一时间在测试环境回放验证。

**零号病人代码**：
在 `TextSimilarityPolicy.extractEntityTokens` 中：
```java
if (cleanPart.length() >= 4) {
    for (int i = 0; i <= cleanPart.length() - 2; i++) {
        String sub = cleanPart.substring(i, i + 2);
        if (!STOP_WORDS.contains(sub)) {
            entities.add(sub);
        }
    }
}
```
该段代码对 `>= 4` 字符的 cleanPart 执行机械式 2-gram 滑窗切片。当输入“朱双双的标讯不可编辑”时，切出“朱双、双双、双的、的标、标讯、讯不、不可、编辑”，被 `ScoreItemCaseMatchPolicy.calculateEntityScore` 拾取为“项目专属实体”，由于专属实体具有最高优先级，直接把展示关键词占满，并在 reasons 中生成荒谬的“命中项目专属实体(的标、不可)”。

**根治方案**：
1. **彻底根除 2-gram 机械滑窗切片**：物理删除 `cleanPart.substring(i, i + 2)` 循环，严禁在实体提取层产生任何非语义切片；
2. **组织与项目专名层级提炼**：
   - 增加业务分隔符正则 `ORG_DELIMITER_PATTERN`，纳入 `不可编辑|可编辑|标讯|标包|标段|测试|项目` 等招投标高频噪音词；
   - 引入地域/央企/行政前缀层级剥离正则 `ORG_GEO_PREFIX_PATTERN`，对“中国长江三峡”层级提炼出“长江三峡”、“三峡”，保留高价值专名实体，但绝不切出“国长”、“江三”等碎词；
3. **清洗与停用词增强**：
   - 在 `PunctuationEntityExtractor.cleanEntityToken` 中增加前后导助词清洗（`stripParticles`），避免“朱双双的”因尾助词被当作整体或丢弃；
   - 在 `STOP_WORDS` 补充“不可、编辑、不可编辑、可编辑、标讯、标包、标段、测试”等停用词；
4. **推荐打分与展示双重门禁**：
   - `ScoreItemCaseMatchPolicy.calculateEntityScore` 对提取出的实体必须先过 `isQualityEntity` 检验才允许进入 `hitEntities`，杜绝类似“的标”进入 `reasons`；
   - `assemblePrioritizedKeywords` 增加 `entity.length() <= 10` 长度门禁，防止将整句长项目名当 tag 展示；
5. **防回归测试锁定**：
   在 `TextSimilarityPolicyTest` 与 `ScoreItemCaseMatchPolicyTest` 编写针对真实项目“朱双双的标讯不可编辑”的防回归测试，断言绝对不含任何 2-gram 碎片。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 同一 bug 二次复发（追症状不追根因） | 看到报错或碎片词就想当然定位在某一处函数打补丁，未抓全链路日志排查数据源头 | 遇到缺陷必须严格按照【全链路日志排查 SOP】，进测试服务器抓真实请求 payload/response 与 traceId，先拿铁证再动代码 |
| 试图用机械滑窗（n-gram）提高召回率 | 为了让子串能被模糊匹配到，粗暴切出全部 2-gram 字符，导致无语义碎片污染展示词与匹配原因 | 实体抽取必须走语义规则与层级前缀剥离，展示词绝对禁止暴露机械滑动切片产物 |
| 单测全绿但线上依然存在 bug（测试不测根因） | 测试只测了新改的局部分词函数，未针对线上真实的完整数据链路（真实项目名称输入）编写回归用例 | 针对复现的 bug 必须编写包含真实生产/测试样本的端到端策略测试，并确保红转绿 |

---

## 224. 评分解析四列契约：分类扫细则、仓库无约束满分、行身份用局部编号（PR !2547 / 2026-09-04）

**背景**：
winbid-test 项目 1371（Project 21 MRO）评分标准四列反复错：财务权重被档位分顶替、财务项匹配成人员、库里有仓仍显示不满足、局部编号 `1/2` 跨组插队。约 100 次 prompt/正则修补不粘。PR !2547 把四列钉成金标后，审查发现仓库列被修成「有一座在用仓即 FULL」。

**根因**：
1. **分类扫 `dim+detail`**：细则里的「人员配置 / 品牌覆盖 / 授权」决定知识库类别，财务/技术方案被送进人员库/品牌库。
2. **仓库 `nameKeywords` 来自细则切词**：招标长句 `LIKE` 仓名，空匹配 → 假阴性「不满足」。纠偏时把 `nameKeywords` 置空，匹配层只滤在用仓，`MatchTierPolicy` 要求数 null 当分母 1 → 有仓即 FULL（假阳性满分）。
3. **行 `:key` / 结果表用局部 `code`**：商务/技术复用 `1/2`，财务行渲染成人员行满足状态。
4. **测试锁 helper 不锁生产链**：金标只跑 `recall()+sort()+categorize()`，仓库「禁止 LIKE」写在测试名里，却 `findAll(any(Specification))` 或 mock FULL。

**修复（PR !2547）**：
- 分类只看因素名 `dim`；因素名含「方案」→ OTHER。
- 仓库估计：抽到正数面积才调 `WarehouseMatchService`；抽不到 → PENDING，禁止存在性满分。面积正则覆盖千分位/小数，`≤0` 当抽取失败。
- 前端 `lookupScoreResult` 按 `id:` 查找，有 id 不回退 code；表格 `:key` 用 `item.id`。
- 父维编号重复时 `ScoreItemOrderPolicy` 保持物理行序。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 用存在性满分修假阴性 LIKE | 看到「库里有仓却不满足」就关掉名称过滤 | 抽不到可执行约束（面积/覆盖/点名仓）必须 PENDING，不得 `WarehouseMatchRequest(null,null,null,null)` |
| 金标 DisplayName 夸大覆盖 | 测试名写「禁止 LIKE / 局部 1/2」，夹具 code 为空、match 被 mock | 用户可见列必须走到 `estimateForProject`；用 ArgumentCaptor 锁 `minArea`/`nameKeywords`，禁止 `any(Specification)` 冒充无 LIKE |
| 局部编号当行身份 | Vue `:key="item.code"` + `results[code]` | 落库后身份是 `item.id`；lookup 有 id 时 fail-closed，不得回退 code |

**相关**：`docs/implementation-notes/scoreparse-1371-column-invariants.md`；`.wiki/pages/score-parse-service.md`；§194（排序层级，本条补仓库/分类/行身份）。




---

## 225. 评分标准解析编号乱码、权重错位与缺项乱序（项目 1359 / 2026-09-05）

**背景**：
winbid-test 项目 1359（AI 评分标准解析）存在 4 个顽固问题：
1. **编号乱码**：编号出现字符串 `"null"`（如 `"null.1"`）；
2. **比分权重错误**：Markdown 参差行坐标映射错位，且最高分列被单元格括号分值覆写；
3. **评分项缺项与乱序**：商务评分中诚信评价（扣分项）被程序性条款正则过滤；综合评价小项在抽取合并后顺序错乱；
4. **业绩与仓储满足状态**：停用词过多导致满足状态判定为不满足。

**根因**：
1. LLM/OCR 抽取的 `code` 偶尔为字面量 `"null"` / `"NULL"`，后端未做全链路清洗，前端未过滤无效占位符直接拼接；
2. Markdown 表格参差行（ragged rows）缺少右对齐左补空，列头映射器在识别到显式分值列时被后续单元格括号提取的叶子分值冲刷；
3. 诚信评价条款包含扣分或程序性前附表排他词，被程序性条款正则误杀；综合评价子项合并时未保留输入物理行顺序；
4. 业绩与仓储匹配时过度分词引入宽泛停用词导致匹配评分失败。

**修复**：
- `ScoreCandidateMapper` 与 `ScoreCandidatePostProcessor` 全链路清洗 `"null"` / `"NULL"` / `"undefined"`；前端 `scoreParseTask.js` 过滤无效 code 为安全占位符；
- `TableGrid` 支持 Markdown 参差行对齐，`HeuristicTableColumnMapper` 与 `CellAnchoredScoreExtractor` 建立最高分列防覆写保护与扣分项救生通道；
- `ScoreItemOrderPolicy` 和 `ScoreMergeResultGuard` 锁定表格物理行序与父维度优先；
- `ProjectMatchService` 与 `WarehouseMatchService` 优化停用词与满足状态判定。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 字符串字面量 "null" 泄漏到 UI | 序列化与上游提示词返回了 "null" 字符串而非空值，仅做 `!= null` 判断漏防 | 统一做 `isInvalidCode` / 字符串全链路清洗正则过滤 `^(null|undefined|none|n/a)$` |
| 表格解析参差行导致列索引位移 | Markdown 表格单元格数与表头不一致时按左对齐填充，破坏列映射关系 | 参差行按右对齐左补空，严格按物理表头对齐 |
| 扣分项被误当成程序性条款过滤 | 扣分/诚信条款常含扣除、违规等强特征词，被统一黑名单拦截 | 增设扣分与给分条款救生白名单（whitelisting） |

---

## 226. 基于真实数据底座的历史案例精准推荐（Feature 061 / 2026-09-05）

**背景**：
在编制标书打开“AI 智能案例推荐”抽屉时，历史案例推荐存在大类错位（如微服务技术项推荐财务审计）、细分项召回为 0、重点客户无断层优势及 2-gram 机械碎词等痛点。先前方案试图做虚构的全生命周期竞品报价与胜率预测，脱离了真实数据底座。本次重构回归第一性原理：“在智能推荐抽屉中准确推荐相似历史案例”，依托 OBS 70 个项目 8144+ 切片构建高纯度数据底座。

**根因**：
1. **数据底座断层**：前台 `knowledge_case` 表存量仅 300 条样本，海量 8144+ 离线切片未结构化清洗入库，细分技术项无案例可推；
2. **缺乏业务子主题标记**：原方案仅粗分为技术/商务大类，缺乏售后 SLA、应急预案、系统对接、仓储物流等 10 大细分主题标记，长文本停用词容易冲淡匹配；
3. **缺乏专属客户断层加权**：重点专名客户（三峡、建行）未与当前投标主体建立强绑定，导致同客户历史案例无法排在第一梯队；
4. **长文本分词碎片**：滑动切词产生“双的”、“的标”、“不可”等无语义碎末。

**修复（Feature 061）**：
- **特征抽取流水线**：编写 `scripts/extract-historical-case-features.py`，实现企业专名抽取（剥离前缀/地名）、10 大业务子主题规则分类器、质量阶梯打分（0-100）及样板段落物理拦截（开标表、发票、声明 100% 过滤）；
- **数据迁移与模型扩展**：新增 Flyway `V1211__add_sub_topic_to_knowledge_case.sql` 与 `U1211` 回滚脚本，扩展 `sub_topic` 与 `quality_score` 字段及索引；实体与导入脚本完整对齐；
- **FP-Java 纯核心加固**：
  - 新增 `CaseSubTopicPolicy` 纯核心推断，结构化一致加 25 分，正文补偿加 15 分；
  - 加固 `ScoreItemCaseMatchPolicy`：大类冲突一票否决（0分）、专属客户断层优先（最高 25 分）、高质量案例加成（5分），行数严格精简在 291 行（<300行）；
- **前端联动与标签**：`AiRecommendDrawer.vue` 规则对齐，`CaseCard.vue` 增设子主题徽标与匹配依据浮层展示。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 脱离真实数据底座设计宏大叙事 | 在无真实竞品报价与胜率标注时凭空设计预测模型 | 牢记第一性原理：先做真实特征抽取与数据清洗流水线，再谈模型推荐 |
| 跨大类推荐采用软扣分导致错配上浮 | 技术评分项在扣减 20 分后仍因通用词重叠而高于普通技术案例 | 大类互斥（技术 vs 商务）必须作为硬门禁一票否决（直接置 0 分） |
| 算分权重压缩导致通用优质案例无法评为“优质” | 引入新维度时过度压缩条款/标题分，无实体匹配的案例最高分不足 60 | 采用累加后封顶在 100 分的策略，既保障子主题/专属客户的断层领先，又保证优质通用案例正常评级 |

---

## 227. 评分模型权重自洽性与前端解释层算术对齐（Feature 062 / 2026-09-05）

**背景**：
用户在使用“AI 智能案例推荐”抽屉时反馈“前端看不到变化”且“评分依据说明分数不对”——提示浮窗中各维度列出分值加和达 140 分（25+25+30+20+10+10+5+15），与标题“满分 100 分”产生直接算术冲突，且开发宿主未重启导致案例子主题标签未渲染。

**根因**：
1. **解释层算术硬伤**：旧策略中各维度最大分值未做归一化，后端虽有 `Math.min(score, 100)` 兜底，但前端 Tooltip 罗列出的各分项最大值相加远超 100 分，缺乏“基础项”与“加分项（Bonus）”的严格区分，严重影响用户与评审信任；
2. **开发宿主进程滞后**：开发宿主 18089 端口运行着早晨启动的旧 jar 包，推荐 API 响应 JSON 未携带 `subTopic` 字段，导致前端组件 `subTopicLabel` 为空从而未渲染黄色标签。

**修复（Feature 062）**：
- **100 分自洽模型重构**：
  - 基础 5 维相加严丝合缝为 100 分：业务子主题（25）+ 条款深度契合（25）+ 专属实体（20）+ 评分点标题核心匹配（20）+ 类别一致（10）= 100 分；
  - 显式声明加分机制：项目/客户类型一致加成（最高 5 分 / 3 分）、搜索词/中标/质量加成（各 +5 分）作为额外 Bonus，综合得分封顶 100 分；
  - 保持硬门禁：大类冲突一票否决（0分）；评级标准 ≥80% 优质，≥60% 良好，<60% 一般；
- **前端文案与单测同步**：更新 `AiRecommendDrawer.vue` 浮窗排版与测试断言，清晰展示基础 100 分体系与加分机制；
- **开发宿主进程刷新**：重新构建 jar 包并热重启开发宿主 18089 端口，使数据库 V1211 的 `sub_topic` 与新版算分策略即刻生效。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| UI 规则文案各分项最大值相加溢出总分 | 随意堆砌新增维度加分，未梳理基础分与加分项关系 | 基础维度分值总和必须严格等于满分 100 分；超出部分必须显式标记为额外 Bonus 加分项 |
| PR 合并后开发环境未重启导致前端看不到变化 | 多 Agent 协作模式下，开发宿主运行的是长驻后台进程 | 交付收尾前必做端到端 API 实测，确认新字段与新逻辑已在开发宿主生效 |

---

## 228. 标书质检「投标保证金」误判根因与破局：两阶段结构化契约抽取 + 凭证事实要素反假通过（2026-09-05）

**背景**：
在项目 1358 标书质量核查中，标书实际未缴纳且未附投标保证金回单（招标文件明确免收），但系统第 23 项「投标保证金凭证」却给出通过判定，结论显示：“已附投标保证金汇款回单或电子保函凭证”，证据盲目指向标书第 1 页。排查发现团队历史曾为评分项解析打补丁提了 100+ 个 PR 仍无法穷举语法变体。本次彻底放弃“增补正则词性打补丁”的老路，转向“两阶段结构化契约抽取 + 事实要素检验”的语义架构根治方案。

**根因（双重破防）**：
1. **招标文件前附表格式盲区**：旧逻辑 `isDepositNotRequired` 仅硬编码 6 句自然语言整句正则（如“无需缴纳投标保证金”），面对项目 1358 前附表 Markdown 表格形式（如 `| 投标保证金 | 不适用/免收/0元 |`）无法提取，导致第一步误判为“招标方要求缴纳保证金”；
2. **标书检索强词过宽与目录页（TOC）陷阱**：标书页检索 `strongMatch` 包含了 `"汇款凭证"`, `"缴纳凭证"` 等宽泛词。标书第 1 页为目录（TOC），行内出现 `附件七 投标保证金汇款凭证` 即被当成实质回单命中并返回 PASS；且旧逻辑只要找到包含关键词的页面即断言通过，完全未核验金额、收款人、流水号、印章等真实凭证事实要素。

**修复方案（根治方案）**：
1. **阶段一（招标文件端）：结构化契约抽取 + 确定性表格双轨融合**：
   - 契约化模型：在 `TenderRequirements` 与 `TenderRequirementsExtractor` 新增 `depositRequirement` JSON 契约（`required: boolean`, `amount: string`, `form: string`, `reason: string`），利用大模型上下文语义精确提取；
   - 确定性双轨兜底：新建纯 FP-Java `DeterministicDepositExtractor`，覆盖前附表表格提取与显式免除规则，秒级兜底融合；
2. **阶段二（标书核验端）：反假通过事实校验 + 免收联动**：
   - 确定性防假：在 `SubItemAndAttachmentRules` 中收紧 `strongMatch`，剔除泛化词，新增 `isActualDepositVoucherPage` 剔除目录、占位符与承诺书说明页；
   - 免收硬信号直出：若招标明确免收，`SemanticRulesLane` 与 `BidDocumentQualityEngine` 直接直出 PASS 并联动第 25 项 `DEPOSIT_REFUND` 豁免，免除 LLM 调用并杜绝幻觉；
   - LLM 事实凭证校验：若招标明确要求缴纳，将保证金条款摘要与候选页喂给 `LlmAdjudicator`，要求严格检验银行回单/保函的金额、收款人、转账凭证号或公章等事实要素，禁止仅凭目录或占位符判定通过。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 正则补丁难以穷举表格与格式变体 | 试图用单一简单自然语言短语正则匹配复杂的招标文件前附表 | 采用“LLM 结构化契约抽取 + 物理表格双轨融合”模型，既抗格式扰动又具确定性兜底 |
| 标书目录页（TOC）超前命中导致假通过 | 检索关键词太宽，且防假逻辑采用反向否定（!hasToc）导致无要素正文误放行 | 附件凭证类检查必须采用“凭证类型+交易事实数据”双重正向必要条件，缺一不可 |
| 语义裁决接线半途断裂 | 仅在 LlmAdjudicator 写准则与 cues，但引擎未对该项触发 LLM 调用，导致死代码 | 引擎必须保证非免收场景真正调用 LLM 裁决通道，并在单测中显式验证端到端调用发生 |
| 合并策略偏向免收放大 LLM 幻觉 | 盲目采用“只要任一段免收就以免收为准”，导致 LLM 一次幻觉即全盘误判 | 交叉验证必须硬事实优先（具体金额优先于概括免收）；冲突且无金额时保持审慎合规判缴纳 |
| 确定性保底假设过宽 | 全文未搜到“保证金/保函”即断定免收，遗漏“投标担保”与中文大写金额 | 招投标术语必须对齐“投标担保/保证”，金额支持大写；无词时返回 null 绝不武断断言免收 |
| 确定性强凭证仍二次调 LLM | 标书已有确凿真实回单时无条件调 LLM，增加 Token 成本与模型抖动风险 | 确定性强凭证（带证据事实）直通保留通过结果，仅在可疑（MANUAL_REVIEW/FAIL）时调 LLM 深度裁决 |
| 双轨融合金额双有数被 LLM 覆盖 | LLM 总结金额存在缩写或四舍五入，默认取首个参数导致正则原文被覆盖 | 金额为强事实字段，双有数时坚决以确定性从原文正则提取的金额为准，防止模型改写 |
| 单测反例未压到被测函数内部分支 | 测试用例文字完全未命中强关键词，在第一步未检索到即退出，未真正压到防假逻辑 | 反例用例必须包含强检索词（如含回单/保函字样），但缺乏流水号/印章要素，确保打满防假校验函数 |

---

## 229. 标书质检「投标函格式规范」语义重构：契约化抽取 + 目录点线/模板占位符反假 + 强凭证直通（2026-09-05）

**背景**：
标书质量核查第 2 项「投标函格式规范」长期依赖自然语言短语正则与弱启发式选页。在真实项目中暴露三大硬伤：
1. **目录索引回退误判**：标书如果只在目录页（TOC）列出「一、投标函及投标函附录 ...... 1」而正文漏附投标函时，选页策略因找不到同时具备称谓与承诺的正文页，回退返回 `firstMatch`（即目录页），并给出“已找到投标函，但未检测到标准致采购人称谓，建议复核”的误判，把缺失正文严重缺陷掩盖为抬头不规范；
2. **占位符模板漏防**：标书直接复制模板且未替换【采购人名称】或下划线留白，系统无法识别未填占位符；
3. **缺乏招标端契约比对**：无法对照招标文件明确要求的函件类型（如指定《报价函》）、致采购人全称与投标有效期（如 90 天）做实质性比对校验。

**根因**：
1. **反向否定与兜底回退陷阱**：旧 `findBestBidLetterPage` 将包含关键词的任意首页作为 `firstMatch`，当未匹配到正文时兜底返回 `firstMatch`，导致目录索引被误采纳；
2. **单轨正则与无契约核验**：未将招标文件的致采购人名称与有效期天数作为结构化输入输入核查链条；
3. **缺乏强凭证直通与存疑裁决分流**：标书已包含规范正文与要素时若盲目调 LLM 易受提示词抖动影响，而目录点线被拦截后若直接判定又缺乏 AI 综合理解上下文的兜底。

**修复方案（标准 6 步门禁）**：
1. **招标端结构化契约（`BidLetterRequirement`）**：在 `TenderRequirements` 定义函件类型、采购人称谓、有效期天数及原文章节描述；
2. **纯核心确定性双轨提取（`DeterministicBidLetterExtractor`）**：建立 `DeterministicBidLetterExtractor`，兼容 Markdown 表格行非数字跨度（`[^0-9\\n]{0,30}`）提取天数与单位全称，并通过 `fuseWithDeterministic` 实现原文硬事实优先融合；
3. **标书端正向事实防假（`BasicInfoCheckPolicy`）**：
   - 彻底拦截目录点线页（`isCatalogOrOutlinePage`：识别 `......`、`……` 与连续带点线页码结构，绝不作为 `firstMatch` 回退）；
   - 拦截未替换占位模板（`UNFILLED_PLACEHOLDER_PATTERN`：拦截【采购人名称】、下划线留白等未替换模板转 MANUAL_REVIEW）；
   - 契约交叉核验：比对致采购人抬头是否与招标要求一致；提取标书承诺有效期天数，若低于招标要求直接 FAIL；
4. **引擎强凭证直通与存疑裁决接通（`BidDocumentQualityEngine`）**：
   - 确定性强通过直通：标书具备规范函件正文、称谓及承诺落款时直接 PASS 直通，LLM 调用次数恒为 0，降低 Token 成本与抗抖动；
   - 存疑场景接通 LLM：仅有目录行被拦截、抬头存疑或未检索到实质正文时，接通 `LlmAdjudicator` 深度裁决；
5. **测试矩阵闭环**：落地 `BidLetterCheckPolicyTest`、`DeterministicBidLetterExtractorTest` 与 `BidDocumentQualityEngineTest` 直通测试，实现 100% 覆盖。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 选页策略回退返回首个候选页导致目录页被采纳 | 以为只要后续找不到更优页，退回首个命中文档总比没有强，忽视了首页往往是目录索引 | 目录页/大纲页必须在循环开始前物理排除，禁止进入候选列表；找不到正文时必须返回 null 判定缺失 |
| 表格行提取正则是跨度贪婪匹配失败 | Markdown 表格中存在竖线 `\|` 和列宽空格，简单用 `[\s:：]*` 无法跨过表格列线捕获天数 | 正则提取表格要素时应采用非数字跨度限制（如 `[^0-9\n]{0,30}`），精准穿透表格分隔符 |
| 异步执行器测试因新增存疑调用导致聚告警单测失败 | 增加了格式规范项的存疑 LLM 接线，全未命中用例的 LLM 调用次数增加，导致失败计数超过阈值 3 | 架构演进扩展语义项接线时，需同步梳理全链路调用预算与聚告警阈值用例期望，保持测试自洽 |

---

## 230. 标书质检「法定代表人授权书」语义重构：契约化抽取 + 目录点线/模板占位符反假 + 法人亲签豁免 + 强凭证直通（2026-09-05）

**背景**：
标书质量核查附件凭证类第 3 项「法定代表人授权书」（AUTHORIZATION_LETTER）原先由通用规则进行弱文本匹配，在实际交付中暴露四个典型假阳性/假阴性风险：
1. **目录索引超前命中假通过**：标书目录页（TOC）列出「二、法定代表人授权书 ...... 5」，被弱关键词检索误当成正文直接判定通过，正文完全漏附授权书却未被拦截；
2. **占位模板留白放行**：标书直接粘贴未填写模板，正文残留【委托代理人姓名】、【身份证号】或下划线空行未实际签署，缺乏占位符检测机制；
3. **法定代表人亲自投标场景误杀**：招标文件和招投标法规定法定代表人本人亲自签署并参与投标的，仅需提供法定代表人证明书而免附授权委托书；旧规则机械要求授权委托书导致合法标书被误判 FAIL；
4. **单轨正则与无招标端契约比对**：未从招标文件提取关于授权书格式模板标题、是否允许法定代表人亲自签署免授权书的明确要求。

**根因**：
1. **反假判据不完整**：缺乏点线目录与未填模板的过滤机制，凭证有效性未与“委托人/受托人/授权权限/身份证”等双角色法律要素绑定；
2. **法定代表人亲自投标豁免分支缺失**：未在领域模型中建模 `allowLegalRepresentativeOnly` 豁免契约，导致合法免除场景无法自适应通过；
3. **缺乏强事实直通与存疑分流**：标书已提供完备受托人身份证号与授权范围的正规文书时，若再无条件调用 LLM 容易引入提示词幻觉与增加调用耗时，而存疑时又缺乏结合上下文深度裁决的通道。

**修复方案（标准 6 步门禁）**：
1. **招标端结构化契约（`AuthorizationRequirement`）**：在 `TenderRequirements` 建立 `authorizationRequirement` 契约（是否必须、是否允许法人亲自参加免除、指定模板标题、原文章节描述）；
2. **纯核心确定性双轨提取（`DeterministicAuthorizationExtractor`）**：建立纯核心确定性提取器，基于正规正则识别自然人免除、法人亲自参加签署免除及模板标题要求，支持双轨融合；
3. **标书端正向事实防假策略（`AuthorizationLetterCheckPolicy`）**：
   - 彻底拦截点线目录页（`MULTI_DOT_PATTERN` 与 `CATALOG_DOT_LINE_PATTERN`，`isCatalogOrOutlinePage`）；
   - 拦截未替换占位模板（`UNFILLED_PLACEHOLDER_PATTERN`：拦截【被授权人姓名】、下划线留白等转 MANUAL_REVIEW）；
   - 严格双角色要素检验：检验委托人（法定代表人）、受托人（委托代理人）、身份证号（18 位/15 位加掩码）、授权权限与期限；
   - 法定代表人本人投标豁免自适应通过：标书明确法定代表人亲自参与并附身份证明时判定 PASS，理由注明“法人亲自参与投标”；
4. **引擎强凭证直通与存疑接线（`BidDocumentQualityEngine`）**：
   - 确定性强通过直通：标书具备真实授权要素或法人免除事实时直接 PASS 直通，LLM 调用次数恒为 0，抗抖动且节省 Token；
   - 存疑或占位未填场景接通 `LlmAdjudicator` 准则 5 深度裁决，并结合 `SemanticPageRetriever` 候选页检索；
5. **测试矩阵闭环**：落地 `AuthorizationLetterCheckPolicyTest`、`DeterministicAuthorizationExtractorTest`、`TenderRequirementsParserTest`，并更新 `BidDocumentQualityEngineTest` 与 `BidQualityCheckAsyncExecutorTest` 保持全链路自洽。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 附件凭证类易被目录超前命中 | 标书通常在第 1~3 页就有完整目录，检索词匹配到目录就以为找到凭证 | 凭证核查必须采用“正文凭证类型 + 核心事实要素”双重正向充分条件，并强校验排除点线目录（`isCatalogOrOutlinePage`） |
| 未填占位模板易漏网 | 标书直接粘贴模板时文本完全包含“授权委托书/法定代表人/委托代理人”，关键词全部命中 | 必须对中括号占位符（如【姓名】）及连续下划线空行建立反假规则，命中未填模板立即拦截转人工复核 |
| 法定代表人亲自投标被误判缺失 | 忽视了招投标实践中法定代表人亲自签署并投标依法无需授权委托书的法定豁免场景 | 在招标端契约建模 `allowLegalRepresentativeOnly`，标书端识别法人亲自投标事实并自适应通过 |

---

## 231. 标书质检「营业执照有效性」语义重构：契约化抽取 + 目录点线/未填占位符反假 + 18位USCC/主体要素校验 + 强凭证直通（2026-09-05）

**背景**：
标书质量核查关键资格类第 4 项「营业执照有效性」（BUSINESS_LICENSE，级别 CRITICAL）原先在 `BasicInfoCheckPolicy` 中依赖弱关键词匹配 `findFirstPageContaining("营业执照", "统一社会信用代码")` 与宽泛正则，在实际交付中暴露典型假阳性/假阴性风险：
1. **目录索引超前命中假通过**：标书目录页列出「四、营业执照及资质证书 ................. 5」，被弱关键词检索误当成正文直接判定通过，正文漏附扫描件却被放行；
2. **占位模板留白放行**：标书直接粘贴未填写模板，正文残留【此处粘贴营业执照副本】或统一社会信用代码连续下划线留白，缺乏模板占位符反假机制；
3. **主体类型兼容性缺失**：仅识别企业营业执照，对于高校、科研院所或事业单位投标人的「事业单位法人证书」等替代主体凭证缺乏正向适配，导致合法标书被误判缺失；
4. **单轨正则与无招标端契约比对**：未从招标文件提取关于证照类型、加盖公章、原件或复印件要求的结构化契约。

**根因**：
1. **反假判据不完整**：缺乏点线目录与未替换占位模板的过滤机制，凭证有效性未与 GB 32100-2015 18 位标准代码及主体登记要素（法定代表人/经营范围/营业期限/登记机关）强绑定；
2. **缺乏主体证照扩展模型**：未在招标端与标书端支持非企业主体的合规凭证识别通道；
3. **缺乏强事实直通与存疑分流**：标书已提供完备 18 位统一社会信用代码与登记主体事实的真实扫描件时，若再无条件调用 LLM 容易引入提示词幻觉与调用耗时；而目录拦截或模板留白时又缺乏深度裁决机制。

**修复方案（标准 6 步门禁）**：
1. **招标端结构化契约（`BusinessLicenseRequirement`）**：在 `TenderRequirements` 建立 `businessLicenseRequirement` 契约（是否必须、允许的证照类型列表、原件/复印件/公章要求、描述）；
2. **纯核心确定性双轨提取（`DeterministicBusinessLicenseExtractor`）**：建立纯核心确定性提取器，正则提取营业执照要求、事业单位法人证书等替代类型及公章要求，与 LLM 提取双轨融合；
3. **标书端正向事实防假策略（`BusinessLicenseCheckPolicy`）**：
   - 物理拦截点线目录页（`MULTI_DOT_PATTERN` 与 `CATALOG_DOT_LINE_PATTERN`，`isCatalogOrOutlinePage`）；
   - 拦截未替换占位模板（`UNFILLED_PLACEHOLDER_PATTERN`：拦截【此处粘贴营业执照】、下划线留白等转 MANUAL_REVIEW）；
   - GB 32100-2015 规范 18 位统一社会信用代码（剔除易混字母 I, O, Z, S, V）严格校验；
   - 登记事实要素综合判定（法定代表人/经营范围/营业期限/登记机关/公章等）；
   - 兼容事业单位法人证书及主体登记号；
4. **引擎强凭证直通与存疑裁决接通（`BidDocumentQualityEngine`）**：
   - 确定性强通过直通：标书具备真实 18 位代码与登记事实时直接 PASS 直通，LLM 调用次数恒为 0，防止模型抖动与节约 Token；
   - 存疑场景接通 LLM：仅有目录行被拦截、未填占位符或未检索到实质正文时，接通 `LlmAdjudicator` 准则 6 深度裁决，并结合 `SemanticPageRetriever` 候选页检索；
5. **测试矩阵闭环**：落地 `BusinessLicenseCheckPolicyTest`、`DeterministicBusinessLicenseExtractorTest`、`TenderRequirementsParserTest`，并更新 `BidDocumentQualityEngineTest` 与 `BidQualityCheckAsyncExecutorTest` 保持全链路自洽。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 统一社会信用代码正则过于宽泛导致非代码数字串误判 | 旧正则使用通用 `\b[0-9A-Z]{18}\b`，容易将 18 位身份证号或长流水号误当成统一社会信用代码 | 严格采用 GB 32100-2015 标准正则 `[0-9A-HJ-NPQRTUWXY]{2}\d{6}[0-9A-HJ-NPQRTUWXY]{10}` 剔除 I, O, Z, S, V 易混淆字母；注意纯数字 18 位串（如身份证号）仍可命中该正则，真正的防线是"代码 + 登记要素（法定代表人/经营范围/期限/登记机关）+ 同页证照关键词"三重 AND 判定，不可单靠正则 |
| 事业单位与非企业主体凭证被误判漏附 | 现实中存在科研院所、高校、社团组织等非企业法人投标，其证照名称为事业单位法人证书，若仅匹配“营业执照”会导致误判 | 在契约与策略中统一建立主体资格替代凭证（allowCertificates）白名单与解析通道，兼顾各类法定主体凭据 |
| 测试断言结论关键词与实现轻微不符导致单测失败 | 在策略实现中对结论描述进行了精准表述（如“已包含营业执照且识别到有效统一社会信用代码”），测试断言写成“已提供有效营业执照” | 编写单元测试时应严格校验策略输出契约，或基于核心业务要素断言，保持测试与生产代码的一致性 |

---

## 232. 标书质检「法定代表人及代理人身份证明」语义重构：契约化抽取 + 目录点线/未填占位符反假 + GB 11643-1999 18位身份证号要素核验 + 社保证明联动与强凭证直通（2026-09-05）

**背景**：
标书质量核查关键资格类第 21 项「法定代表人及委托代理人身份证明」（ID_AUTH_LETTER，级别 HIGH/CRITICAL）原先在 `SubItemAndAttachmentRules` 中依赖弱关键词匹配 `findFirstPageContaining(bidDoc, "身份证", "社保", "社会保险")`，在实际交付中暴露典型假阳性/假阴性风险：
1. **目录索引超前命中假通过**：标书目录页列出「五、法定代表人身份证明及授权委托书 ................. 8」，被弱关键词检索误当成正文直接判定通过，正文漏附身份证扫描件却被放行；
2. **占位模板留白放行**：标书直接粘贴未填写模板，正文残留【此处粘贴委托代理人身份证人像面/国徽面】或身份证号码连续下划线留白，缺乏模板占位符反假机制；
3. **要素完备性与证件规范性缺失**：未按国家标准 GB 11643-1999 校验 18 位公民身份号码与真实版面要素（人像面/国徽面/有效期限/签发机关），导致任意出现"身份证"字样即可蒙混过关；
4. **单轨正则与无招标端契约比对**：未从招标文件提取关于身份证正反面、近月社保证明、加盖公章、原件或复印件要求的结构化契约，无法在招标明确要求社保凭据时联动核验。

**根因**：
1. **反假判据不完整**：缺乏点线目录与未替换占位模板的过滤机制，凭证有效性未与 GB 11643-1999 18 位标准公民身份号码及人像面/国徽面版面要素强绑定；
2. **缺乏社保证明联动模型**：未在招标端结构化契约中提取社保证明要求，标书端无法联动核验社保凭据事实；
3. **缺乏强事实直通与存疑分流**：标书已提供完备 18 位身份证号码与人像面/国徽面事实的真实扫描件时，若再无条件调用 LLM 容易引入提示词幻觉与调用耗时；而目录拦截或模板留白时又缺乏深度裁决机制。

**修复方案（标准 6 步门禁）**：
1. **招标端结构化契约（`IdAuthLetterRequirement`）**：在 `TenderRequirements` 建立 `idAuthLetterRequirement` 契约（是否必须、是否要求正反面、是否要求社保证明、原件/复印件/公章要求、描述）；
2. **纯核心确定性双轨提取（`DeterministicIdAuthLetterExtractor`）**：建立纯核心确定性提取器，正则提取身份证正反面要求、社保证明（近N个月社保/养老保险等）要求及公章/原件要求，与 LLM 提取双轨融合；
3. **标书端正向事实防假策略（`IdAuthLetterCheckPolicy`）**：
   - 物理拦截点线目录页（`MULTI_DOT_PATTERN` 与 `CATALOG_DOT_LINE_PATTERN`，`isCatalogOrOutlinePage`）；
   - 拦截未替换占位模板（`UNFILLED_PLACEHOLDER_PATTERN`：拦截【此处粘贴身份证】、下划线留白等转 MANUAL_REVIEW）；
   - GB 11643-1999 规范 18 位公民身份号码严格校验（含号码脱敏保护隐私）；
   - 真实版面要素综合判定（人像面/国徽面/有效期限/签发机关/姓名/职务/公章等）；
   - 若招标文件要求社保证明，联动核验标书中的社保凭据（社保缴纳记录/参保证明/养老保险等），缺失时降级为 MANUAL_REVIEW 提示人工复核；
4. **引擎强凭证直通与存疑裁决接通（`BidDocumentQualityEngine`）**：
   - 确定性强通过直通：标书具备真实 18 位身份证号码与身份证明要素时直接 PASS 直通，LLM 调用次数恒为 0，防止模型抖动与节约 Token；
   - 存疑场景接通 LLM：仅有目录行被拦截、未填占位符或未检索到实质正文时，接通 `LlmAdjudicator` 准则 7 深度裁决，并结合 `SemanticPageRetriever` 候选页检索；
5. **测试矩阵闭环**：落地 `IdAuthLetterCheckPolicyTest`、`DeterministicIdAuthLetterExtractorTest`、`TenderRequirementsParserTest`，并更新 `BidDocumentQualityEngineTest` 与 `BidQualityCheckAsyncExecutorTest` 保持全链路自洽。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 身份证号等敏感信息直接明文输出 | 在质检证据或日志中直接输出识别到的公民身份号码 | 建立号码脱敏函数（如 `maskIdCard` 保留前6后4），保护自然人隐私安全 |
| 委托代理人社保证明易漏检 | 部分高门槛招标要求提供近3个月社保证明，仅核验身份证无法闭环 | 在契约中提取社保证明联动标志，标书端若未提供社保凭据及时告警，防止因社保缺失导致废标 |
| 模板占位符在双面粘贴时易残留半边 | 投标人可能只贴了人像面，国徽面保留【此处粘贴国徽面】占位符 | 占位符拦截规则对人像面/国徽面/正面/反面各类占位模板均需进行反假校验 |
| 中文词汇子串包含关系（"身份证明"包含"身份证"）导致判定失真 | 汉字"身份证明"前3个字正好是"身份证"，直接使用 `contains("身份证")` 会误将身份证明当作身份证命中 | 涉及层级/特定词汇判断时，必须排斥上位泛化词（如 `contains("身份证") && !contains("身份证明")`），消除子串语义遮蔽 |

---

## 233. 标书质检「财务状况证明/财务审计报告」两阶段契约与反假重构：结构化双轨提取 + 目录点线/未填占位符反假拦截 + 财务三表与审计印章事实核验 + QUALIFICATION_REVIEW 确定性硬信号直通（2026-09-06）

**背景**：
在政府采购与招投标法中，《政府采购法》第 22 条第 2 款明文规定供应商必须具备“健全的财务会计制度”，通常要求提供近 1~3 年经第三方会计师事务所审计的财务审计报告（含资产负债表、利润表、现金流量表及事务所印章）。在原系统中，标准 25 项第 14 项「资格性评审条款响应」（QUALIFICATION_REVIEW）长期处于空心化状态：
1. **仅有知识库可用性守卫，缺乏招标端结构化要求**：原代码仅做 `guardForComparison`，未从招标文件结构化提取所需年度、是否必须审计报告、所需具体财务报表（三表或附注）、是否要求会计师事务所公章等关键事实；
2. **缺乏标书端反假过滤机制**：标书在目录索引页列出「财务报表及审计报告 ............... 15」易被点线索引误触发；或者标书直接粘贴未填写的空白模板（如【此处粘贴经会计师事务所审计的财务审计报告】、【财务报表附后】、连续下划线留白 `______年度资产负债表`）而缺乏反假拦截；
3. **缺乏确定性强凭证直通与存疑分流**：标书已提供完备真实的三表（资产负债表、利润表、现金流量表）与事务所审计意见、审计印章时，若无条件调用大模型容易引入抖动与 Token 浪费；而仅有一两张报表或年份存疑时又缺乏大模型准则 8 的深度裁决支撑；
4. **内部类累积导致核心 Record 超出 300 行红线**：`TenderRequirements` 历史版本内部累积了 `DepositRequirement`、`BidLetterRequirement` 等多个内部类，行数逼近 289 行，若继续加入财务契约必破单文件行预算红线。

**根因**：
1. **资格审查条款缺乏针对法定财务制度的专用策略引擎**：将最为客观和规范的财务状况审查直接转交泛化的大模型处理，既浪费算力又丧失了确定性反假能力；
2. **模板占位符与目录页未做前置剔除**：未将未替换占位模板页面（`UNFILLED_PLACEHOLDER_PATTERN`）与目录点线页（`isCatalogOrOutlinePage`）从报表有效页中物理剥离；
3. **架构内部类耦合过紧**：契约模型未遵循按领域独立顶层 Record 拆分原则，导致聚合类行数膨胀。

**修复方案（标准 6 步门禁）**：
1. **契约解耦与行预算防爆**：将 `DepositRequirement`、`BidLetterRequirement` 及新增的 `FinancialRequirement`（财务状况与审计要求契约）全部独立拆分为同包顶层 Record，使 `TenderRequirements` 瘦身至 198 行，架构对称清晰；
2. **双轨确定性提取（`DeterministicFinancialExtractor`）**：纯核心正则单次扫描提取年份要求（如 2023、2024、近三年等）、三表要求（资产负债表/利润表/现金流量表）、审计报告及事务所公章要求，支持显式免除判定，与 LLM 提取双轨融合；
3. **正向事实防假与反假拦截策略（`FinancialStatementCheckPolicy`）**：
   - 物理拦截点线目录页（`isCatalogOrOutlinePage`）；
   - 拦截未填占位模板（`UNFILLED_PLACEHOLDER_PATTERN`：拦截【此处粘贴...】、留白下划线等），发现占位符立即判定 FAIL 反假拦截；
   - 核心财务三表及会计师事务所审计意见、印章综合判定；
   - 报表年份覆盖度严格核验；
4. **全链路流水线集成与确定性直通**：
   - 将 `FinancialStatementCheckPolicy` 挂载为 `QUALIFICATION_REVIEW`（资格审查）的第一阶段硬信号（`qualificationReviewFirstStage`）；
   - 具备完整三表与审计印章强事实时直通 PASS，LLM 调用次数恒为 0；未填占位模板直接 FAIL 拦截；部分报表或存疑场景流转至大模型；
   - 在 `LlmAdjudicator` 注入准则 8 深度裁决提示词，严禁点线目录与占位模板放行通过；
5. **测试矩阵闭环**：完善 `DeterministicFinancialExtractorTest`、`FinancialStatementCheckPolicyTest`、`TenderRequirementsParserTest`、`BidDocumentQualityEngineTest` 与 `FPJavaArchitectureTest`，验证无异常、无框架依赖与强凭证直通。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 模板占位符页因包含“资产负债表”字样被误当成有效报表页 | 占位模板正文常含有“______年度资产负债表”，单纯依据标题判断会误将占位符页当成已提供报表 | 在页面扫描循环中，一旦探测到未填占位符（`UNFILLED_PLACEHOLDER_PATTERN`），立即记录证据并 `continue` 跳过，坚决不将其视作有效报表页面 |
| Record 内部类累积突破 300 行限制 | 迭代中图省事把小 Record 随手写在外层类内部，日积月累导致聚合类膨胀 | 坚持顶层 Record 拆分原则，每个子领域契约抽取独立文件，既满足 300 行红线，又提高包内模型对称度与复用性 |
| 报表多页跨页与年份断言严谨性 | 财务报表与审计报告通常长达数页，年份可能写在表头，报表内容在下方 | 收集全标书财务相关页面的年份集合（`yearsFound`），基于年份集合对齐招标文件要求年份，支持“近三年/前两年度”等相对年份柔性匹配 |

---

## 234. 标书质检「报价一致性 + 开标一览表」两阶段契约与反假重构：最高投标限价废标红线核验 + 表函总价一致性比对 + 目录点线与未填模板拦截 + 强凭证直通与存疑裁决（2026-09-06）

**背景**：
《招标投标法实施条例》第 51 条明文规定：“投标报价高于招标文件设定的最高投标限价的，评标委员会应当否决其投标”。在标书质量核查体系中，第 6 项「报价一致性」（PRICE_CONSISTENCY，级别 CRITICAL）与第 22 项「开标一览表/报价表」（BID_OPENING_TABLE，级别 CRITICAL）直接关联投标人的法定废标风险与实质响应：
1. **最高投标限价废标红线漏判**：原系统未从招标文件提取最高限价/招标控制价/采购预算，当投标人报价超过最高限价时依然误判为 PASS，带来严重的漏判与废标风险；
2. **开标一览表弱匹配与假通过**：原代码仅依靠弱关键词 `containsAny("开标一览表", ...)` 命中，极易被标书目录页的点线索引（如 `三、开标一览表........................45`）或者标书直接粘贴未填占位符（如 `【此处粘贴开标一览表及分项报价清单】`）蒙混过关；
3. **投标函与开标一览表总价脱节**：过去只检查投标函单页内的大写与小写数字是否一致，开标一览表（或报价明细表）与投标函各自独立核查，无法识别“投标函报 500 万，开标一览表填 480 万”的表函冲突（实际评标中此项属于重大澄清或否决风险）；
4. **缺乏两阶段强凭证直通机制**：大写小写金额相符且未超限价的规范标书无条件调用 LLM，产生提示词幻觉与 Token 浪费；而对于超限价或严重表函冲突的确定性废标项，大模型有时会给出模糊的宽容结论。

**根因**：
1. **契约未闭环**：招标端未设立专用的报价要求契约（最高限价数值、报价方式、明细表要求、开标一览表要求），导致质检时缺乏比对基准；
2. **反假机制不完善**：未过滤目录点线页与未填模板占位符；
3. **缺乏跨章节总价勾稽核验**：未提取开标一览表中的总报价，与投标函总报价形成闭环核验；
4. **裁决未分层**：确定性硬失败（超限价、表函冲突、模板未填）未硬性阻断，允许模型幻觉覆盖。

**修复方案（标准 6 步门禁）**：
1. **招标端结构化契约（`PriceRequirement`）**：建立独立顶层 Record 契约，封装最高限价金额（BigDecimal）、限价原文、报价方式、明细表/一览表要求及双轨融合逻辑；
2. **双轨确定性提取（`DeterministicPriceExtractor`）**：纯核心正则提取最高投标限价、控制价、采购预算（支持万元折算、纯数字及中文大写），提取费率/总价报价方式，与 LLM 提取双轨融合；
3. **开标一览表反假与总价提取（`BidOpeningTableCheckPolicy`）**：
   - 物理剔除目录点线页（`isCatalogOrOutlinePage`）；
   - 拦截未替换模板占位符（`UNFILLED_PLACEHOLDER_PATTERN`），未填占位符直接判定 FAIL 反假拦截；
   - 结构化表头要素校验（表头必须包含序号、名称、规格、数量、单价、合价等实质结构）；
   - 提取表格总报价（`extractTablePrice`）供报价一致性联动核验；
4. **报价一致性与限价核验策略（`PriceConsistencyCheckPolicy`）**：
   - 支持公文括号格式（如“（大写）：”、“（小写）：”）及大写折算数字与小写阿拉伯数字比对；
   - 逐页未命中时进行跨页全文比对；
   - **最高限价超限硬核验**：投标报价高于最高限价，立即判定 FAIL 并标明法定废标风险；
   - **表函总价一致性核验**：开标一览表总价与投标函总价不一致，立即判定 FAIL；
5. **引擎全链路集成与双层裁决**：
   - 规范标书强凭证直通 PASS（0 次 LLM 耗时）；
   - 硬性违背（超限价、表函冲突、占位符未填）硬性 FAIL 阻断，大模型禁止覆盖；
   - 存疑或未提取场景流转至大模型，注入准则 9（开标一览表反假）与准则 10（限价超限与报价一致性）；
6. **测试矩阵闭环**：落地 55 个单测与架构测试，严格遵循单文件 `<= 300` 行与 FP-Java 零异常流纪律。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 公文带括号格式阻断大小写匹配 | 标书正文常见公文写作方式如 `投标总价（大写）：`，若正则写死 `大写[：:]` 会因括号而漏配 | 正则应使用 `[（\\(]?大写[）\\)]?\\s*[：:]?` 容忍全半角公文括号 |
| DOTALL 跨行正则贪婪吞食字符 | 大写捕获组若用 `\\s+` 结尾，在 DOTALL 模式下会将换行符及后文整段吃掉导致截断错误 | 以明确的汉字金额单位（如 `元(?:整|正)?`）作为大写正则明确捕获边界 |
| DocumentContent fullText 跨页回退为空 | 转换器或单测构建 DocumentContent 时第三参数 fullText 可能为空字符串 | 跨页回退查找前必须优先 `combinePages(bidDoc)` 将各页拼接，防止被空字符串阻断 |
| 纯核心业务流使用 try-catch | 为了容错捕获 NumberFormatException，在 pure-core 中随手写了 try-catch | FP-Java 门禁严查 pure-core 中的 try-catch 块，数字解析前必须前置正则格式校验，杜绝以异常驱动控制流 |

---

## 235. 标书质检「电子标 CA 证书与签章有效性」两阶段契约与反假重构：纸质标免收直通 + 真实证书强凭证直通 + 目录点线与未填模板拦截 + 阶段 1 文书附件凭证类 100% 全面大收官（2026-09-06）

**背景**：
在标书质量核查体系中，第 24 项「电子标 CA 证书」（CA_CERTIFICATE）属于电子投标关键响应项。以往核查存在四大痛点：
1. **纸质标/线下标虚假拦截**：招标文件明确为纸质标、线下开标或无需电子签章时，原系统依然盲目核查 CA 证书，产生严重误报；
2. **目录页点线索引欺骗假通过**：标书仅在目录中包含 `五、CA数字证书及签章有效性证明......58`，被弱包含规则误判为已提供真实证书；
3. **未填占位符模板放行**：标书正文直接粘贴 `【此处粘贴CA数字证书】` 或下划线留白，无实际证书却假通过；
4. **规范标书重复耗时调用 LLM**：标书已提供完备真实的 CFCA/沃通/契约锁/北京CA 证书序列号与有效期，却仍走大模型，增加延迟与抖动。

**根因**：
1. **契约未闭环**：招标端未独立建立 `CaCertificateRequirement`，无法精准表达交易平台名称、免收信号（纸质标/线下标）与电子签章要求；
2. **反假与要素核查缺失**：未剔除目录点线页，未拦截模板占位符，未强约束真实颁发机构与证书序列号/有效期等事实特征；
3. **双层裁决未贯通**：缺乏免收直通与真实强凭证直通，存疑场景未注入准则 11 深度裁决。

**修复方案（标准 6 步门禁）**：
1. **招标端结构化契约（`CaCertificateRequirement`）**：独立顶层不可变 Record 契约，支持交易平台名称、证书复印件/有效性证明要求、电子签章要求，提供双轨优先融合 `fuseWithDeterministic`；
2. **双轨确定性提取（`DeterministicCaCertificateExtractor`）**：纯核心正则识别纸质标/线下标等免收信号，提取政采云/公共资源交易平台等指定平台，支持 DocumentContent 与 String 双重重载；
3. **电子标 CA 证书反假核验策略（`CaCertificateCheckPolicy`）**：
   - 物理剔除目录点线页（`isCatalogOrOutlinePage`）；
   - 拦截未替换模板占位符（`UNFILLED_PLACEHOLDER_PATTERN`），未填占位符直接判定 FAIL 反假拦截；
   - 强凭证事实要素核验（权威 CA 机构如 CFCA/沃通/契约锁/天威诚信/北京CA等，以及证书序列号、算法、有效期等事实特征）；
4. **引擎全链路集成与阶段 1 全面大收官**：
   - 纸质标/免收 CA 确定性直出 PASS（0 次 LLM 耗时抗抖动）；
   - 规范真实证书强凭证直通 PASS（0 次 LLM 耗时）；
   - 存疑或未附带凭证场景流转至大模型，注入准则 11 深度裁决；
   - 阶段 1（6 项文书附件凭证类）100% 全部高质量收官达成；
5. **测试矩阵与架构闭环**：落地 58 个测试用例，全套 ArchitectureTest 架构守卫测试 100% 绿灯，严格保持单文件 `<= 300` 行与 FP-Java 契约。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 单方法重载参数为 null 引发歧义 | 调用 `SubItemAndAttachmentRules.checkCaCertificate(bidDoc, null)` 时，因同时存在 `(DocumentContent, String)` 和 `(DocumentContent, CaCertificateRequirement)` 重载导致编译歧义 | 在单一参数重载中明确委托无参调用 `checkCaCertificate(bidDoc)` 或强制显式转型强类型，杜绝模糊 null 传递 |
| 状态机与任务单测 mock 次数联动 | 引入新质检项走 LLM 兜底时，全量异步任务执行器的总调用数发生变化 | 任务级集成单测中若测试“解析失败阈值告警”，mock 次数需随参与大模型裁决的检查项总数同步递增维护 |
| 双重重载支持纯文本与分页文档 | Extractor 内部可能面对拼接纯文本 fullText 亦可能面对 PageContent 分页结构 | 提取器应同时提供 `extract(DocumentContent)` 与 `extract(String)`，兼顾性能与各层调用便利 |

---

## 236. 标书质检「前附表关键条款与偏离表响应」两阶段契约与反假重构：下划线留白/括号未填拦截 + 偏离表负偏离阻断/无偏离直通 + 提取器聚合解耦防爆行数 + 阶段 2 深度语义项首战告捷（2026-09-06）

**背景**：
在标书质量核查阶段 2「深度语义项」体系中，前附表关键条款响应（`FRONT_TABLE_RESPONSE`）与偏离表响应（`RESPONSIVENESS_REVIEW`）是评审专家否决投标与扣分的重灾区。以往核查存在五大痛点：
1. **模板留白伪造欺骗**：标书直接复用模板，包含下划线未填留白（`工期：______日历天`）或括号占位符（`交货期：【请填写】`），无实质内容却逃逸；
2. **目录页点线索引欺骗假通过**：标书仅在目录页出现 `三、投标人须知前附表响应承诺......15` 或 `商务偏离表......26`，被弱包含规则误判为已有完整响应；
3. **偏离表负偏离放行与无偏离抖动**：偏离表中存在负偏离（“有偏离”、“负偏离”、“部分满足”、“无法满足”）未被硬性阻断；而规范全项“无偏离/完全响应”的标书却仍走大模型耗时且带来裁决抖动；
4. **多提取器直接堆叠导致外壳层行数超标**：各确定性提取器（`Deterministic*Extractor`）在异步执行器直接手工融合，导致 `BidQualityCheckAsyncExecutor` 逼近 300 行红线；
5. **Prompt 准则文本在测试断言中发生文字重叠**：Prompt 模板注入裁决准则后，若单测粗粒度断言包含项名，会与全局准则文本重叠误判。

**根因**：
1. **契约未闭环**：招标端缺少前附表结构化契约 `FrontTableRequirement`（工期、质保、交货地点、付款等），无法表达核心条款与确定性融合；
2. **反假核验缺失**：规则层未剔除目录点线页，未拦截下划线留白与模板占位符，未强约束负偏离即阻断；
3. **缺乏确定性融合器**：异步执行器层承担了过多提取器组装职责，缺少纯核心层 `DeterministicRequirementsFuser` 统一编排；
4. **测试断言粒度过粗**：单元测试断言直通时直接搜索 item title，受全局 Prompt 准则污染。

**修复方案（标准 6 步门禁）**：
1. **招标端结构化契约（`FrontTableRequirement`）**：独立顶层不可变 Record 契约，支持工期、质保期、交货地点、付款方式等条款定义与不可变合并 `fuseWithDeterministic`；
2. **双轨确定性提取与统一融合（`DeterministicFrontTableExtractor` + `DeterministicRequirementsFuser`）**：
   - 提取器支持正则抓取前附表工期/质保/有效期/交货/付款事实；
   - 抽象 `DeterministicRequirementsFuser` 统一融合 7 大确定性提取器，将 `BidQualityCheckAsyncExecutor` 行数从 295 降至 243 行，彻底消解行数风险；
3. **前附表响应反假核验策略（`FrontTableResponsePolicy`）**：
   - 严格剔除目录点线页（`isCatalogOrOutlinePage`）；
   - 正则拦截下划线留白（`______`）与括号占位符（`【待填】`），直接硬拦截 FAIL；
   - 针对核心条款与强响应词匹配实现强事实直通 PASS（0 次 LLM 耗时）；
4. **偏离表响应核验策略（`ResponsivenessReviewPolicy`）**：
   - 结构核验偏离表章节与列头；
   - 检测到明确负偏离词汇直接硬性 FAIL 阻断；
   - 规范标书全项“无偏离/完全响应”强事实直通 PASS；
5. **解耦拆分与 FP-Java 纪律遵守**：
   - 拆分 `RequirementSectionParser`（`TenderRequirementsParser` 降至 108 行）；
   - 拆分 `AdjudicationGuidelines`，遵循 FP-Java 纯值返回 `String allGuidelines()`，消除 void 变更方法；
6. **测试矩阵与架构闭环**：
   - 新增 6 组单测套件共 20+ 个测试用例，覆盖空留白拦截、负偏离阻断、强事实直通、融合器与端到端集成；
   - 修复测试断言边界，使用项专属判定文本杜绝 Prompt 准则误判；
   - 严格遵循单文件 `<= 300` 行与 FP-Java 架构约束。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| FP-Java 纯核心禁止 void 业务方法 | 抽取 `AdjudicationGuidelines` 时最初写了 `void appendAllGuidelines(StringBuilder)` | FP-Java 严禁在纯核心定义 void 业务方法修改入参；重构为纯函数返回不可变 `String` |
| Prompt 全局准则污染单测断言 | 全局 Prompt 中含有准则 12/13 标题与内容，直接断言 `prompt.contains(item.getTitle())` 会误匹配为包含该项的裁决调用 | 测试断言必须精准匹配结构化裁决上下文，如 `prompt.contains("裁决检查项「" + item.getTitle() + "」")` |
| 异步执行器累积融合逻辑导致行数超标 | 每增加一个确定性提取器，外壳层新增 10~15 行调用与对象构建，使执行器接近 300 行上限 | 提取纯核心域 `DeterministicRequirementsFuser`，聚合所有提取器与融合逻辑，外壳执行器仅保留一行调用 |

---

## 237. 标书质检「一票否决条款与资格评审」两阶段深度穿透与反假重构：废标条款确定性提取 + 正向履约承诺保护与实质违规硬阻断 + 资格材料占位留白拦截与反短路纪律（2026-09-06）

**背景**：
在标书质量核查阶段 2 深度语义项中，一票否决与资格前置（`QUALIFICATION_VETO`）与资格性评审（`QUALIFICATION_REVIEW`）是关乎标书生死的底线检查项。以往核查存在四大关键痛点：
1. **机械匹配误杀与漏网**：以往 `QUALIFICATION_VETO` 第一阶段粗暴依赖 `mandatoryQualifications` 字符串包含，标书有同义表述易误杀；更致命的是未真正消费招标文件废标条款（`vetoClauses`）；
2. **正向合规承诺与实质性违规不分**：标书常见的合规承诺（如“承诺不转包、不违法分包”、“承诺无串通投标行为”）若按单纯关键词扫描容易误判为违规，而实质性违规自述（如“拟将主体工程分包给某某公司”）却缺乏模式拦截；
3. **资格评审反假覆盖薄弱**：以往仅在财务报表维度实现了 `FinancialStatementCheckPolicy.isHardAntiFakeFail`，但在依法缴纳税收、社会保障资金凭证、信用中国核查记录等资格证明材料中，模板留白占位符（`【此处粘贴完税凭证】`、`______`）未被有效拦截；
4. **违反准则 8「反短路」纪律风险**：在资格性评审条款中，财务报表齐全绝对不得短路其他资格条件（企业信誉、纳税、社保、类似项目业绩门槛等），否则会造成严重漏检。

**根因**：
1. **确定性抽取断层**：缺少招标文件废标/一票否决专章与条款确定性提取器（`DeterministicVetoExtractor`）；
2. **策略职责杂揉**：一票否决和资格评审第一阶段逻辑内嵌于 `SemanticRulesLane`，无法承载细粒度的占位符扫描、目录页过滤与句式判定；
3. **正负向语义未分流**：缺少区分正向履约承诺与实质性负向违规的语义模式正则；
4. **资格证明反假未闭环**：反假占位符拦截仅局限于财务报表单项，未推广至全部资格证明文件。

**修复方案（标准 6 步门禁）**：
1. **确定性废标条款提取与融合（`DeterministicVetoExtractor` + `DeterministicRequirementsFuser`）**：
   - 支持废标专章定位与行内废标后果条款扫描，提取条款原文及核心场景关键词（分包、转包、串通投标、弄虚作假、最高限价、签章等）；
   - 融合器中统一执行 `withMergedVetoClauses`，保障原文硬事实优先；
2. **一票否决核验策略（`QualificationVetoPolicy`）**：
   - 物理剔除目录点线页，杜绝目录欺骗假通过；
   - 扫描资格/否决声明中的未填占位符（`_{3,}`、`【请填写】`），直接硬拦截 FAIL；
   - 正向合规承诺（“承诺不转包”等）安全放行；实质性违规（“拟将主体工程分包”等）直接硬阻断 FAIL；
   - 专用行业资质证书正文缺失核验；其余无硬信号场景移交 LLM lane 深度裁决；
3. **资格评审反假与反短路策略（`QualificationReviewPolicy`）**：
   - 继承财务审计报告造假硬拦截；
   - 新增税收凭证、社保清单、信用中国等资格证明页占位符与空白留白反假拦截；
   - 严格遵循准则 8「反短路」硬约束：无硬造假信号一律返回 `Optional.empty()`，移交 LLM lane 穿透核验信誉、业绩、税收、社保等全部条款；
4. **解耦与外壳减重**：
   - `SemanticRulesLane` 纯核心职责收敛，将一票否决与资格评审第一阶段完全委托给对应 Policy，代码行数从 168 降至 106 行；
5. **Prompt 裁决准则与证据链升级**：
   - `AdjudicationGuidelines` 新增准则 14（一票否决合规承诺保护与违规硬阻断），强化准则 8（资格评审反短路与多维度核验）；
   - `EvidenceAssembler` 增强废标条款与资格材料证据检索；
6. **全套 TDD 与门禁通过**：
   - 新增 3 组单测套件共 12 个测试用例，覆盖废标提取、正向承诺放行、违规分包拦截、占位符拦截与反短路验证；
   - 86 个关联测试与架构测试（`FPJavaArchitectureTest`、`MaintainabilityArchitectureTest`）全绿通过。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| FP-Java 纯核心内部辅助方法禁止 void | `DeterministicVetoExtractor` 中最初将扫描行内条款写为 `void scanInlineVetoClauses` 修改入参 | 纯核心私有辅助方法也必须遵守纯函数原则，返回不可变 `List<VetoClause>` |
| 法律效果特征词严禁包含单个“废标”词 | 若包含单个“废标”词，像“没有任何废标说明”等普通否定描述也会误触发提取 | 废标效果特征词必须是具有明确处置动作的词组（如“作废标处理”、“视为废标”等） |
| 废标专章内的条款天然具有否决性 | 专章下的具体情形（如“（1）未经盖章的”）不一定包含“不得/禁止”词汇 | 专章内条目去除导语后，各标号条目默认即为否决条款，只需清洗前导标号与标点即可 |

## 238. 标书质检「资质要求智能匹配（QUALIFICATION_MATCH）」多维升级：资质等级层级判定 + 跨专业领域词冲突防护 + 标书正文穿透排障与占位留白硬拦截（2026-09-06）

**背景**：
在标书质量核查深度语义项中，资质匹配（`QUALIFICATION_MATCH`）负责将招标文件硬性资格要求与企业资质库及标书证明材料进行逐项智能比对。以往比对算法存在以下关键痛点：
1. **等级判定机械化与漏报**：以往仅支持等级字符串全等比对（`reqLevel.equals(certLevel)`），无法支持招投标常见的“二级及以上”由“一级”或“特级”向下兼容满足的业务事实；同时由于第 1 步双向直接包含未做等级校验，如果去除等级后的基底词相同，可能会导致低等级充抵高等级的严重漏报；
2. **跨专业领域词错配**：建筑承包类资质中，“施工总承包”、“专业承包”属于承包形式，并非行业专有领域。以往在 LCS（最长公共子串）比对时，未将“承包一级”等通用词列为停用词，且未校验专业领域冲突，导致如“电力工程施工总承包一级”误匹配到“建筑机电安装工程专业承包一级”；
3. **标书页码检索被目录点线页欺骗**：标书页码定位直接调用 `EvidenceLocator` 未过滤目录页，经常将目录索引行（如“目录 ... 资质证书 ... 120”）误判为证明材料所在页；
4. **资质证明章节留白未拦截**：标书在资质证书分卷中若存在未填写的占位模板（如`【此处粘贴资质证书扫描件】`、`______`），以往规则 lane 缺乏前置硬拦截，直接放行给大模型可能造成漏判。

**根因**：
1. 缺乏资质等级层级映射与比较策略（`QualificationGradePolicy`）；
2. 停用词库缺少工程承包通用类别（总承包、专业承包、施工承包），且 LCS 缺乏领域词冲突守卫（`hasConflictingDomainTerms`）；
3. 标书定位未接入目录页过滤机制（`BidLetterCheckPolicy.isCatalogOrOutlinePage`）；
4. 语义规则 lane（`SemanticRulesLane`）中 `QUALIFICATION_MATCH` 仅有元数据 guard，未接入第一阶段确定性反假核验（`checkFirstStage`）。

**修复方案（标准 6 步门禁）**：
1. **独立资质等级层级判定策略（`QualificationGradePolicy.java`，157 行，纯核心）**：
   - 建立四大主流资质等级层级体系（工程施工承包：特级>一级>二级>三级；设计/咨询：综合甲级>甲级>乙级>丙级；网络安全等保：等保四级>三级>二级；软件成熟度：CMMI5>4>3）；
   - 支持向下兼容覆盖：高等级满足低等级要求，低等级明确判定等级不足（`incompatible` 并附注说明）；
2. **核心匹配策略升级与领域冲突防护（`QualificationMatchPolicy.java`，290 行，纯核心）**：
   - 在打分计算前置加入 `QualificationGradePolicy.isGradeCompatible` 门禁，等级不足一票否决为 0 分；
   - 扩充 `STOP_WORDS`，引入 `hasConflictingDomainTerms` 机制：当招标要求与证书分属不同专业（如机电安装 vs 电力工程）时，严格阻断 LCS 错配；
   - `buildSingleDetail` 智能识别等级不足证书并生成带等级说明的结构化明细；
   - 标书检索自动剔除目录索引页，确保页码穿透到真实证书正文页；
3. **第一阶段确定性核验（`checkFirstStage`）与规则 Lane 集成**：
   - 检测资质证书章节中的模板占位留白（`【此处粘贴...】`、`_{3,}`），直接硬阻断输出 FAIL；正常内容平滑移交 LLM lane；
   - `SemanticRulesLane` 接通 `checkFirstStage`；
4. **裁决准则与证据链增强**：
   - `AdjudicationGuidelines` 新增《准则 15：资质匹配核验准则》；
   - `EvidenceAssembler` 增强招标具体资质条款与标书证明正文的真实定位；
5. **单元测试全覆盖**：
   - `QualificationGradePolicyTest`（7/7）、`QualificationMatchPolicyTest`（12/12）、`QualificationMatchPenetrationTest`（4/4）；
   - 78 个全量关联测试与架构测试全绿通过。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| LCS 匹配缺少行业专业领域冲突校验 | “施工总承包”、“专业承包”等承包形式词长达 4~5 字且非专业名称，容易让不同专业的资质产生公共子串错配 | 明确区分承包类别词（列入停用词）与行业专有领域词，在 LCS 匹配前先执行 `hasConflictingDomainTerms` 互斥校验 |
| 标书证据定位未过滤目录页造成假定位 | 目录页包含章节标题和页码，字符串搜索会优先命中目录页 | 标书定位必须先经 `BidLetterCheckPolicy.isCatalogOrOutlinePage` 过滤点线目录页，仅在有效正文页检索 |
| 单文件 300 行硬门禁约束 | 资质匹配算法原有逻辑已达 296 行，若将等级层级判定全部堆叠将严重超标 | 将等级层级判定抽取为纯核心策略类 `QualificationGradePolicy`，各自保持在 150~290 行单一职责 |

## 239. 标书质检「评分项响应（SCORING_RESPONSE）」与「澄清文件响应（CLARIFICATION_RESPONSE）」两阶段确定性穿透与反假升级（2026-09-06）

**背景**：
作为 `/unified-dev-flow` 阶段 2 七项深度语义项的收官项，`SCORING_RESPONSE`（评分标准逐项对应与支撑材料）与 `CLARIFICATION_RESPONSE`（补遗/澄清文件响应）负责保障投标文件在技术方案深度、评分细则对标以及最新答疑澄清吸收等核心维度上的全面合规。以往实现存在以下关键痛点：
1. **评分技术方案占位留白与假响应**：投标文件在技术方案、项目实施、项目管理机构章节中可能保留模板占位（如 `【请填写技术方案】`、`_{3,}`），但以往规则 lane 缺乏前置硬拦截，直接放行给大模型存在误判或漏判风险；
2. **目录点线假定位欺骗**：标书仅在目录页列出“评分细则对应表”或“澄清确认函”条目，正文无实质性章节，单纯关键字检索容易把目录索引行当成证明页；
3. **澄清答疑免除场景误判**：招标文件若未发布任何澄清答疑文件，标书未包含独立确认函属于正常合规事实，系统应能自动识别免除而不能机械判定缺失；若标书包含真实签署的澄清答疑确认函，应在第一阶段确定性直通；
4. **编译期注解处理灾难防范**：当类中存在重复方法定义等编译阻断错误时，JDK 21 javac 会中断注解处理器（Lombok），导致后续数千个依赖 `@Data` / `@Getter` 的类级联报错（如 `cannot find symbol: method getId()` / `variable log`），排查极具迷惑性。

**根因**：
1. 缺乏专职的评分标准与澄清答疑核验策略类（`ScoringResponsePolicy`、`ClarificationResponsePolicy`）；
2. 语义规则 lane（`SemanticRulesLane`）中 `SCORING_RESPONSE` 仅做了缺失 guard，`CLARIFICATION_RESPONSE` 完全走 default empty，未接入第一阶段确定性反假；
3. 大模型裁决准则库缺少评分逐项对标与澄清吸收的专属准则（准则 16 与准则 17）；
4. `AdjudicationGuidelines.java` 中此前意外残留重复的 `allGuidelines()` 方法定义。

**修复方案（标准 6 步门禁）**：
1. **独立评分响应策略（`ScoringResponsePolicy.java`，128 行，纯核心）**：
   - 检索评分响应核心章节（`评分标准对应表`、`评分细则响应表`、`得分点对应表`、`技术方案`、`施工组织设计`、`项目管理机构`）；
   - 第一阶段确定性门禁：全目录点线页硬拦截 FAIL，正文检测到占位留白硬拦截 FAIL；无硬伤平滑移交 LLM 进行技术方案深度与得分点匹配度评估；
2. **独立澄清答疑响应策略（`ClarificationResponsePolicy.java`，161 行，纯核心）**：
   - 识别确认函（`澄清文件确认函`、`答疑纪要确认函`、`补遗书确认函`）与招标澄清发布状态；
   - 目录点线与模板占位硬拦截 FAIL；真实签署确认函直接直通 PASS；若招标文件未发布澄清答疑，自动免除并直通 PASS；若招标有澄清但标书无独立确认函，移交 LLM 语义核验技术/商务是否吸收答疑参数；
3. **语义规则 Lane 与证据链集成（`SemanticRulesLane.java` & `EvidenceAssembler.java`）**：
   - `SemanticRulesLane` 分别接通 `ScoringResponsePolicy` 与 `ClarificationResponsePolicy` 的第一阶段核验；
   - `EvidenceAssembler` 增强招标评分办法与澄清要求、标书对应正文真实页码定位，过滤目录页干扰；
4. **准则库修复与准则 16/17 追加（`AdjudicationGuidelines.java`，42 行）**：
   - 移除重复的 `allGuidelines()` 声明，彻底杜绝 Lombok 处理器连锁失效隐患；
   - 追加《准则 16：评分标准逐项对应核验准则》与《准则 17：澄清与答疑文件响应核验准则》；
5. **单元测试与全链路回归**：
   - `ScoringResponsePolicyTest`（5/5）、`ClarificationResponsePolicyTest`（6/6）、`ScoringAndClarificationIntegrationTest`（2/2）；
   - 架构门禁（`MaintainabilityArchitectureTest` 3/3、`FPJavaArchitectureTest` 8/8、`BidDocumentQualityEngineTest` 30/30、`LlmAdjudicationContractTest` 15/15）全部通过。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| Java 语法错误引发 Lombok 处理器级联雪崩 | javac 在源码存在严重语法错误（如方法重复定义）时，会终止后续注解处理器执行，导致上千个原本正确的 Lombok 实体类报找不到 getter/setter/log 错误，极具迷惑性 | 遇到大面积 Lombok 符号缺失报错时，首先用 `mvn compile` 翻到最顶部查找首个非 Lombok 引起的直接语法或类定义错误 |
| 澄清响应未区分项目发布澄清状态 | 多数正常招标项目根本没有答疑澄清，若要求无条件提供确认函会导致大量假错报 | 检查招标文档是否存在答疑发布，未发布且标书未提及澄清时明确判定免除响应并直通 PASS |
| 评分项响应的深浅两阶段分工 | 评分涉及大量主观技术深度与人员业绩，无法纯靠正则定性 | 第一阶段专职防假（严打未填模板与仅目录列出），无硬伤则移交 LLM 配合准则 16 进行全维度深入打分，保持两阶段契约清晰 |

### Lesson 240: 报价一致性(PRICE_CONSISTENCY)分项算术与商务条款一致性(TERM_CONSISTENCY)深度重构

**背景**：
在 `/unified-dev-flow` 阶段 3（重构 7 项逻辑一致性与计算核验类）的 Sub-task 3.1 攻坚中，针对招投标核心痛点：
1. **多表报价矛盾与分项算术错误**：投标函总报价、开标一览表总价与分项报价明细表金额存在差异，分项报价中单价与数量相乘不等于合价，或分项合价累加总和与总报价不一致；
2. **分项报价与商务条款留白占位**：分项明细表保留未填模板占位符（如 `【待报价】`、`______`），工期与质保期保留下划线留白，逃避实质性响应；
3. **工期超期与质保期缩水**：标书响应工期超过招标文件允许的最高工期上限（法定重大偏离废标红线），或质保期短于招标文件要求的最低保证期限（商务负偏离）。

**根因**：
1. `PriceConsistencyCheckPolicy` 此前仅比对了投标函大写与小写，以及投标函与开标一览表总价，未深入解析分项报价表（货物/工程/服务清单）进行算术校验与累加核算；
2. `BasicInfoCheckPolicy.checkScheduleResponse` 原实现仅做了简单的文本包含匹配（contains "工期" / "交货期"），缺乏招标商务条款（工期上限、质保下限、投标有效期）的结构化比对断言。

**修复方案（标准 6 步门禁）**：
1. **商务条款要求契约与提取器（`BusinessTermsRequirement.java` & `DeterministicTermsExtractor.java`）**：
   - 建立纯核心 `BusinessTermsRequirement` 结构化契约（工期上限日历天、质保期下限月数、投标有效期天数等）；
   - 实现 `DeterministicTermsExtractor` 从招标文件确定性抽取商务关键条款，并在 `DeterministicRequirementsFuser` 中优先融合；
2. **分项报价计算与多表交叉校验助手（`ItemizedPriceCalculator.java`，147 行，纯核心）**：
   - 识别分项报价明细表格，提取行序号、名称、数量、单价与合价；
   - 逐行算术校验（数量 $\times$ 单价 $=$ 合价），累加所有分项合价并与表内标明合计核对；
   - 扫描占位符（`_{3,}`、`【待报价】`）并实时上报；
3. **报价一致性策略升级（`PriceConsistencyCheckPolicy.java`，192 行，纯核心）**：
   - 融合大小写核验、最高限价一票否决、开标一览表与投标函一致性；
   - 联动分项报价表算术错误拦截、占位留白拦截及分项累加和与总报价交叉校验；
4. **商务条款一致性策略（`TermConsistencyCheckPolicy.java`，180 行，纯核心）**：
   - 排除目录点线页干扰，穿透定位真实商务响应正文页；
   - 拦截未填占位符（工期留白、质保待填）；
   - 严格比对：响应工期 $>$ 招标上限判定 FAIL，响应质保期 $<$ 招标下限判定 FAIL，响应有效期 $<$ 招标要求判定 FAIL；
5. **领域模型与引擎接通（`TenderRequirements.java`、`DeterministicCheckPolicy.java`、`BidDocumentQualityEngine.java`）**：
   - 扩充 `TenderRequirements` 契约，接通 `BusinessTermsRequirement` 并更新《准则 18》；
6. **单元测试与全链路回归**：
   - `PriceConsistencyCheckPolicyTest`（8/8 全绿）、`TermConsistencyCheckPolicyTest`（5/5 全绿）、`PriceAndTermIntegrationTest`（2/2 全绿）。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 正则表达式未覆盖中文系词助词 | 中文标书中经常出现“服务工期为：180天”，若正则仅允许冒号或空白字符（`[：:\\s]`），会由于“为”字导致匹配失败，漏提取工期数值 | 商务条款数值正则统一扩充系词与助词允许集（`[：:\\s为]{1,10}`），确保鲁棒性 |
| 回退检索页码污染证据链 | 当多页比对中未在正文页直接提取到全部参数时，若回退到通用关键词检索，必须严格跳过目录点线页，否则会将目录页索引行当作有效证据页码 | 在任何页码挑选或证据摘录方法中，前置执行 `BidLetterCheckPolicy.isCatalogOrOutlinePage` 过滤 |
| 算术核算单文件超标风险 | 报价核验包含大写小写、最高限价、开标一览表以及分项明细的复杂算术解析，放在一个类中极易突破 300 行硬上限 | 按照 FP-Java 原则将分项表格算术解析抽离至独立的纯核心计算助手 `ItemizedPriceCalculator`，主策略保持简洁编排 |

### Lesson 241: 签章完备性(SEAL_INTEGRITY)与表格填写完整性(TABLE_CELL_INTEGRITY)深度重构与反假

**背景**：
在 `/unified-dev-flow` 阶段 3（重构 7 项逻辑一致性与计算核验类）的 Sub-task 3.2 攻坚中，针对两项普遍存在的形式与响应性痛点：
1. **未填签署模板与假签章蒙混**：部分投标文件直接使用模板，落款保留 `法定代表人：______`、`投标人：______`、`【此处盖公章】`，既无真实签字也无企业公章或 CA 电子防伪签章，旧逻辑仅按简单关键词检索容易造成假通过；
2. **表格空单元格与无意义斜杠留白**：响应表格（前附表响应表、规格参数表等）出现空单元格未填、或随意使用 `/`、`\`、`--`、`——` 留白逃避实质性响应，缺乏确定性表格结构化检验；
3. **偏离说明列无偏离正常表述的误伤风险**：在偏离情况或说明列，投标人填写 `/` 或 `无` 代表“无偏离/完全响应”，若不加语义区分一刀切拦截会导致严重误判。

**根因**：
1. `BasicInfoCheckPolicy.checkSignatureSeal` 与 `SubItemAndAttachmentRules.checkCommitmentSeal` 原仅做了简单的文本关键词查找（如包含“（盖章）”或“承诺书”），缺少真实签章要素（法人印章、单位公章、国密SM2/CA验签、骑缝章）与模板未填留白的反假断言；
2. 标书表格填写校验此前未建立确定性 Markdown 表格逐行解析器，无法捕获具体行、列的空值与无效留白。

**修复方案（标准 6 步门禁）**：
1. **表格填写完整性助手（`TableCellIntegrityCalculator.java`，纯核心，192 行）**：
   - 解析 Markdown 规范表格行与单元格，跳过表头分隔行；
   - 过滤目录点线页，识别空单元格、无效斜杠横杠留白及模板占位符；
   - 建立语义列豁免机制（对“偏离说明”、“偏离情况”、“备注”、“说明”等列豁免斜杠与留白）；
   - 输出结构化 `CellViolation`（包含真实页码、行号、列头名称、问题类型与原始文本）；
2. **签章完备性核验策略（`SealIntegrityCheckPolicy.java`，纯核心，168 行）**：
   - 覆盖法定代表人签字/印章、企业公章、防伪验签标记（CA数字签名/国密SM2）与骑缝章覆盖标记；
   - 对未替换占位符（`_{3,}`、`【此处盖公章】` 等）进行反假拦截，若无真实签章直接判定 FAIL；
   - 强事实直通：防伪验签或双要素齐全直通 PASS，单要素或存疑移交人工复核；
3. **业务层联动集成**：
   - `BasicInfoCheckPolicy.checkSignatureSeal` 委托至 `SealIntegrityCheckPolicy.check`；
   - `FrontTableResponsePolicy` 接入 `TableCellIntegrityCalculator.validateText`，表格填写缺陷直接输出带行列明细的 FAIL 拦截；
   - `SubItemAndAttachmentRules.checkCommitmentSeal` 接入真实签章与反假校验；
   - `AdjudicationGuidelines` 扩充《准则 19：签章完备性与表格填写完整性核验准则》；
4. **单元测试与架构验证**：
   - `SealIntegrityCheckPolicyTest`、`TableCellIntegrityCalculatorTest`、`FrontTableResponsePolicyTest`、`BasicInfoAndAttachmentRulesTest` 全部全绿；
   - 严格通过 `MaintainabilityArchitectureTest`（单文件 `<= 300` 行）与 `FPJavaArchitectureTest`（纯值返回、零异常控制流）。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 表格偏离列斜杠留白语义混淆 | 在商务/技术偏离表中，投标人填写 `/` 常代表“无偏离”，若作为通用表格留白拦截会引发假阳性误报 | 必须引入列头语义识别与豁免规则（`isExemptColumn`），对偏离说明、备注列放行无偏离表述 |
| Record 访问器命名习惯与标准 JavaBean 混淆 | 自定义 Record 自动生成的方法名直接为字段名（如 `rowNumber()`、`violationType()`），跨类调用时若凭习惯写成 `row()` 或 `issueDescription()` 会在编译期或反射运行时引发编译错误 | 严格核对 Record 定义中声明的字段名称，使用标准 record accessor |
| 跨策略联动防假需兼顾人工复核兜底 | 线下纸质标盖章扫描件由 OCR 转为纯文本时可能丢失红色印章信息，若无明确占位留白直接误判 FAIL 会阻断正常标书 | 明确区分“未填占位符（直接 FAIL 反假）”与“未识别到印章文本（MANUAL_REVIEW 提示人工核验）”，保障业务鲁棒性 |

## 242. 评分/澄清响应占位正则三漏检：此处家族、确认收到编号留白、抄文误放 PASS（2026-09-06）

**背景**：
PR !2570 合入后复审发现三处漏检：`【此处填写…】`/`【此处粘贴…】`逃逸占位拦截（Lesson 239 正文自己即以`【此处粘贴…】`举例）；PR 描述的拦截示例`【确认收到第__号澄清】`（仅 2 条下划线）双逃逸（bracket 要求`【`后紧跟请/填写且第二分支要求以`第`开头，underline 要 `_{3,}`）；澄清直通 PASS 门以裸`投标人`/`日期`与通用词`无异议`为充分条件，抄招标要求的段落亦可误放 PASS。

**根因**：
1. 占位正则首选分支的起始词集为 `请|在此|在此处|填写…`，“此处”≠“在此”且缺“粘贴”；
2. 编号留白分支 `【(?:第.*号澄清|…)】` 要求`【`后紧跟`第`，真实模板多为`【确认收到第__号澄清】`（有前缀且仅 2 条下划线）；
3. PASS 门的签署信号与确认措辞过弱，且证据定位器 `EvidenceLocator.locate` 首命中即返回，展示证据可指向目录页（裁决逻辑本身已过滤目录，仅证据页码受影响）。

**修复方案**：
1. 两处占位正则起始词集增补 `此处|粘贴`（`[…]` 变体同步）；
2. 澄清编号留白分支改为允许前缀：`【[^】]{0,15}(?:第.{0,10}号澄清|澄清文件|盖章)[^】]{0,15}】`（保留对`澄清文件/盖章`旧匹配，不扩大 underline `_{3,}` 阈值以免误伤正文）；
3. 澄清 PASS 门收紧：签署信号仅认 `盖章|签署|公章|法定代表人|授权代表|电子签章`（删裸`投标人/日期`），确认措辞删通用词`无异议`；无强签署一律移交 LLM；
4. 新增 `EvidenceLocator.locateSkippingCatalog` + `EvidenceAssembler.locateBidSkippingCatalog`，占位 FAIL/确认函 PASS 证据与评分/澄清语义证据改走正文页定位；“仅目录列出”FAIL 证据保留原定位（目录页即罪证）。

**验证**：
- 先红后绿：新增 7 个回归测试（评分此处×2、澄清编号留白×1、抄文无章×1、无异议×1、跳目录定位×2）修前 5 红（运行）+2 红（编译），修后 24/24 绿；
- 宽回归：policy 包 + 架构门禁 362/362 全绿。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| PR 描述示例自逃逸 | 写 PR 时的示例占位未用真实正则跑一遍 | 占位/留白类 PR 必须把描述中的每个示例字符串写成单测断言（示例即用例） |
| 集成测试交叉污染掩盖单点缺口 | 首个集成测试用例的双占位落在同一虚拟页，下划线命中掩盖了 bracket 对`此处`的漏检 | 反假测试优先单占位单页，交叉场景另起用例 |
| 展示证据与裁决逻辑双轨 | 裁决过滤了目录页，证据定位没过滤 | 证据定位器默认即应提供跳目录变体，裁决与证据同过滤 |

## 243. 财务三表平衡勾稽(FINANCIAL_STATEMENT_ACCURACY)与进度计划逻辑合理性(TIMELINE_REASONABLENESS)深度重构（2026-09-06）

**背景**：
在 `/unified-dev-flow` 阶段 3（重构 7 项逻辑一致性与计算核验类）的 Sub-task 3.3 攻坚中，针对财务审计与进度计划两大核心合规逻辑的痛点：
1. **财务报表勾稽关系缺失**：原财务报表核验仅检查年份连续性与审计机构盖章，未核验资产负债表（资产总计 = 负债合计 + 所有者权益合计）、利润表（营业收入 - 营业成本 - 税金及附加 - 期间费用 = 营业利润）、现金流量表（现金及现金等价物净增加额勾稽）的会计恒等式平衡关系，容易漏报造假或错报财务数据；
2. **进度计划开竣工逻辑矛盾**：标书中经常出现计划开竣工日期倒挂（开工日期晚于竣工日期）、自述总工期与招标要求最大工期矛盾、横道图/甘特图占位模板未填、以及开竣工跨度推算天数与自述工期差距过大的逻辑异常。

**根因**：
1. 原 `FinancialStatementCheckPolicy` 仅包含宏观结构检查，缺乏逐表财务会计恒等式的数值提取与勾稽差额容差（绝对误差与相对比率结合）校验能力；
2. 原系统缺乏针对进度计划专项目标的专用核验策略，日期推算容易因格式多样或年/月/日整数直接相减（例如将 `YYYYMMDD` 序数简单相减产生非真实天数）导致工期跨度计算错误。

**修复方案（标准 6 步门禁）**：
1. **财务三表平衡勾稽计算助手（`FinancialStatementAccuracyCalculator.java`，纯核心，242 行）**：
   - 覆盖资产负债平衡式（资产 = 负债 + 所有者权益）、利润平衡式（收入 - 成本 - 税金 - 费用 = 营业利润）、净利润平衡式、现金净增加额勾稽等 4 大财务勾稽维度；
   - 引入金额自适应容差比较器（绝对差额 $\le 1.0$ 元或相对差额 $\le 1\%$ 视为平衡，避免四舍五入与万元转换尾差误报）；
   - 输出结构化 `AccuracyResult`，精准暴露失衡会计科目、账面数值与差额证据链；
2. **进度计划逻辑合理性策略（`TimelineReasonablenessPolicy.java`，纯核心，198 行）**：
   - 检测横道图/甘特图模板占位符（如 `[插入横道图]`、`【此处附施工进度计划甘特图】`）；
   - 提取计划开工、计划竣工日期，检测倒挂与跨度超标；
   - 基于纯整数公历纪元推算天数算法（杜绝日期解析异常），推算真实的纪元天数跨度，并与自述工期比对（允许 10 天工作日/日历天偏差，超出者转人工复核）；
   - 校验开竣工跨度及自述工期是否超出招标文件最大工期天数；
3. **集成与准则升级**：
   - `FinancialStatementCheckPolicy` 接入 `FinancialStatementAccuracyCalculator`；
   - `BasicInfoCheckPolicy` 接入 `TimelineReasonablenessPolicy`；
   - `AdjudicationGuidelines` 扩充《准则 20：财务三表平衡勾稽与进度计划合理性核验准则》；
4. **单元测试与架构保障**：
   - `FinancialStatementAccuracyCalculatorTest`（14/14 全绿）、`TimelineReasonablenesspolicyTest`（11/11 全绿）；
   - 严格通过 FP-Java 无副作用、单文件行预算（$\le 300$ 行）、无 Mockito/无框架纯单测。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 日期序数直接相减导致天数失真 | 将 `YYYYMMDD` 编码为整数（如 20250401 与 20250101）后直接相减得到 300，而不是实际跨度 90 天，导致误触发“工期超标”硬拦截 | 计算真实日期间隔天数必须使用真实纪元天数算术算法，杜绝简单十进制日期数字相减 |
| 财务报表尾差假阳性 | 财务报表数据可能以“万元”或“元”呈现，打印版财务报表常存在 0.01~1.00 元或万元截断的四舍五入尾差 | 勾稽判定必须设置双重容差阈值（绝对差 $\le 1.0$ 或相对差 $\le 1\%$），兼顾防假严格性与容差鲁棒性 |
| 未填横道图模板的反假区分 | 施工组织设计中常直接粘贴通用模板文字但未替换甘特图，需将其与“未提供进度计划”严格区分 | 建立占位模板关键词识别集（`【此处附...】`、`[插入横道图]` 等），发现直接以 FAIL 判定反假，倒逼真实图表交付 |

## 244. 偏离表结构化提取与响应性深度核验（DEVIATION_EXTRACT + RESPONSIVENESS_REVIEW）（2026-09-06）

**背景**：
在 `/unified-dev-flow` 阶段 3（重构 7 项逻辑一致性与计算核验类）的 Sub-task 3.4 攻坚中，针对商务与技术偏离表核验的痛点：
1. 原策略仅做全文包含匹配，未对 Markdown 表格进行分表（商务偏离表 vs 技术偏离表）与行列单元格解析；
2. 表头中出现“偏离情况（正偏离/负偏离/无偏离）”选项说明时，旧版粗糙正则误判为存在实质性负偏离并触发误拦截 FAIL；
3. 表格数据行中真实的负偏离（如“负偏离”、“部分满足”、“工期延长”）容易被文末模板式的“承诺无偏离”掩盖；
4. 正偏离（优于招标要求）与负偏离未解耦，缺少亮点放行机制。

**根因**：
1. 原 `ResponsivenessReviewPolicy` 缺乏 Markdown 表格结构化解析器，无法提取表头各列索引（条款、招标要求、投标响应、偏离类型、偏离说明）；
2. 全文粗暴搜索非否定“负偏离”关键字，未剔除表头中的多选提示文本（如 `/负偏离/`、`正偏离/负偏离`）；
3. 辅助函数直接用 `void` 方法修改外部集合引用，违反了 FP-Java 纯函数值返回架构守卫。

**修复方案（标准 6 步门禁）**：
1. **偏离表结构化提取器（`DeviationTableExtractor.java`，纯核心，240 行）**：
   - 解析 Markdown 表格，识别商务偏离表（`COMMERCIAL`）与技术偏离表（`TECHNICAL`）；
   - 逐行提取条款、响应与偏离说明，分类为 `NONE`、`POSITIVE`、`NEGATIVE`、`UNKNOWN`；
   - 严格遵守 FP-Java 契约，私有辅助方法均返回不可变集合，杜绝 void 方法修改参数引用；
2. **响应性评审策略重构（`ResponsivenessReviewPolicy.java`，纯核心，226 行）**：
   - 优先核验提取器识别到的占位模板与结构化负偏离行，精准输出负偏离条款与说明；
   - 优化非表格自述负偏离检测，精准排除表头选项文本干扰；
   - 结构化偏离表全表无偏离或正偏离直通 PASS，附带正偏离亮点提示；
3. **扩充准则与门禁验证**：
   - `AdjudicationGuidelines.java` 扩充《准则 21：商务与技术偏离表提取与响应性核验准则》；
   - 先红后绿：`DeviationTableExtractorTest` (8/8) + `ResponsivenessReviewPolicyTest` (9/9) 全绿；
   - 架构与门禁：`FPJavaArchitectureTest` (8/8) + `MaintainabilityArchitectureTest` (3/3) 全绿，单文件预算严格控制在 300 行以内。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 表头选项说明引发负偏离假阳性 | 表头常包含“偏离情况(正偏离/负偏离/无偏离)”等提示性文字，全文包含式负偏离检测会将表头误判为违规 | 在非表格自述匹配中增加表头多选模式上下文识别，排除选项枚举干扰，以表格数据行解析结果为准 |
| 偏离说明列留白与占位混淆 | 投标人若无偏离，常在“偏离说明”列填写“/”或“无”，若笼统作为表格空白拦截会产生严重误报 | 严格将“/”、“无”等作为偏离说明列的安全豁免项，仅对未填占位符（如【请填写说明】、下划线留白）进行硬拦截 |
| FP-Java 架构对纯函数值返回的严格断言 | 在私有辅助方法中使用 void 方法修改入参集合会被 ArchUnit 规则 `pure_core_business_methods_must_return_values` 判定违规 | 纯核心域内所有方法（包括私有助手）均必须返回独立的值对象或集合，由调用方通过不可变方式组合 |

## 245. 偏离表对抗性鲁棒升级与正偏离合规审慎机制（2026-09-06）

**背景**：
在 Sub-task 3.4 验收后，基于对抗性思想与深度自我批判，发现了 4 个深层思维漏洞：
1. **长距离否定被截断误判**：旧版本仅用 4 字符滑动窗口判断否定词，面对“本公司在此声明并未对该条款作出负偏离”时长前缀漏网，导致合法声明被误判为违规负偏离；
2. **跨句否定词污染**：无标点隔离时，上一句结尾的“无任何”可能错误覆盖下一句开头的“负偏离”；
3. **“自述正偏离即直通 PASS”的合规盲区**：投标人单方自述的“正偏离”（如“交货期90天，优于行业平均”）可能包含利己性虚标或实质性缩水，直接免审直通 PASS 存在制度性漏洞；
4. **表头列识别的字典窄门与 Tab 排版脱漏**：表头列若命名为“采购需求”、“差异情况”、“偏离情况说明”时列映射错位；制表符 `\t` 分隔表格未被识别。

**根因**：
1. 依赖硬编码固定字符窗口，未引入子句标点截断（逗号/句号/分号/换行/管道符）；
2. 误将“字面上的正偏离词汇”等同于“经过实质性对比认定的加分项”，导致合规免审边界过于宽松；
3. 列优先级判定中“偏离”列早于“偏离说明”列检查，导致“偏离说明”列抢占了偏离类型列。

**修复方案（标准 6 步门禁）**：
1. **子句隔离变长否定判定（`hasNegationPrefix`）**：
   - 从关键字向前回溯至最近标点边界（或最多 18 字符），在子句前缀中匹配包含复合否定词（`绝无`、`绝不包含`、`并不存在`、`我方未作`、`未作出`、`不构成` 等）；
2. **正偏离审慎机制（`ResponsivenessReviewPolicy.java`）**：
   - 确立“严格全表无偏离（纯 `NONE`）”为唯一免审直通 PASS 条件；
   - 凡包含投标人自述“正偏离”（`positiveRows` 非空）的标书，坚决不免审直通，而是返回 `null` 存疑移交专家/LLM 交叉比对招标指标真实性，封堵合规漏洞；
3. **复合列名优先级优化与输入归一化（`DeviationTableExtractor.java`）**：
   - 调整列类型匹配顺序，优先识别“说明/备注/原因”为说明列，避免抢占类型列；
   - 增加对制表符 `\t` 分隔表格行的自动预处理归一化；
   - 扩充中国招投标经典合规短语（如“满足技术参数要求”、“出具检验合格报告”等）至 `NONE_KEYWORDS`。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 固定滑动窗口判断汉语否定的局限 | 中文修饰语长（“郑重声明并未作出任何”）会导致固定 4 字符窗口完全切不到否定词根 | 采用变长窗口向前扫描，结合标点符号（逗号、句号、换行）做子句天然边界隔离，杜绝跨句污染 |
| 盲信投标人单方自述正偏离 | 投标人为迎合系统可能将指标变更单方面标为“正偏离”，系统免审直通将导致严重失职 | 确定性策略只对严格无偏离（NONE）直通，任何宣称正偏离的必须降级为人工/LLM 复核指标 |
| 复合列名被局部包含覆盖 | “偏离说明”同时命中“偏离”与“说明”，若偏离先匹配会导致说明列丢失且类型列错位 | 严格明确列名互斥优先级，复合列名按语义细化分流，在单测中覆盖各类常见多发表头 |
| 单字否定词在变长窗口中引发假阴性击穿 | 在长距离（18字符）前缀中直接检索单字'未'，导致'未达到要求属负偏离'中的'未'被误当成否定负偏离，放跑真实违规 | 否定词实行分层语义距离约束：复合否定词组支持长距离匹配，单字否定（未/无/零）强制要求紧邻关键字（结尾<=2字符） |

## 246. 标书质量核查大文件 check_details TEXT 字段溢出与前端失败持久化兜底（2026-09-06）

**背景**：
测试环境用户在项目 1358 触发“AI 标书质量核查”并等待数分钟后，页面报错一闪而过且无法重新核查，抽屉回退为空白状态。用户误怀疑为接口或网络超时。

**根因**：
1. **数据库列宽限制（TEXT 64KB）**：`compliance_check_results.check_details` 列定义为 `TEXT`（最大 65,535 字节）。当项目包含大标书/多册招标文件，且 AI 对 25 个核查项输出了详实的比对证据、页码与资质详情时，序列化的 JSON 大小超过了 64KB，在 `completeTask` 独立事务提交时触发 MySQL 严格模式的 `Data truncation: Data too long for column 'check_details'`，导致事务回滚、任务标记为 `FAILED`；
2. **长任务错误呈现欠缺（Toast 3秒即逝）**：前端轮询到 `FAILED` 状态时，仅调用了 Element Plus 默认仅停留 3 秒的 `ElMessage.error`，在经历数分钟长任务等待后错误一闪而过，导致用户形成“超时”假象；
3. **前端状态回退逻辑缺陷**：最新记录状态为 `FAILED` 时，`loadLatestReport` 将 `report` 置为 `null`，导致抽屉回退为“当前项目尚未执行标书质量核查”的空白卡片，隐藏了原本右上角的“重新核查”按钮；点击空白处默认触发的是带缓存的普通核查，反复陷入假死或死循环。

**修复方案（标准 6 步门禁）**：
1. **数据库 DDL 升级与平滑迁移（V1212 / U1212）**：
   - 新增 Flyway 迁移脚本，将 `check_details` 从 `TEXT` 扩容至 `MEDIUMTEXT`（最大 16MB）；
   - 编写配套的 U1212 回滚脚本，`FlywayRollbackScriptCoverageTest` 100% 通过；
2. **后端实体同步映射**：
   - 将 `ComplianceCheckResult.java` 的 `@Column(columnDefinition = "TEXT")` 修改为 `@Column(columnDefinition = "MEDIUMTEXT")`；
3. **前端失败态持久化与交互兜底**：
   - `useQualityCheckDrawer.js` 增加 `errorMessage` 响应式状态，在轮询失败、超时及拉取失败报告时精准保留错误文本，启动新任务与成功时清空；
   - `QualityCheckDrawer.vue` 增加基于 `el-result` 的持久化错误卡片（`v-else-if="errorMessage"`），展示可折叠/滚动的错误详情代码框，并提供“重新核查（跳过缓存）”及“尝试恢复（检查缓存）”明确的操作指引。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 长文本/JSON 列使用 `TEXT` 埋下容量炸弹 | 早期简单样例 JSON 仅 20~30KB，`TEXT` 看似够用；随着规则扩充（25 项深度比对）和真实大标书接入，中文字符与详细证据迅速撑爆 64KB | 存储结构化复杂长 JSON/详情的 MySQL 列，统一使用 `MEDIUMTEXT`（16MB）或 `JSON` 类型，杜绝随意使用 `TEXT` |
| 异步长任务错误仅靠全局 Toast 提示 | 异步任务动辄运行数分钟，执行失败时仅用 3 秒自动消失的全局 Toast，用户极易错过信息并误判为超时 | 异步长任务抽屉/页面必须具备持久化的失败状态视图（Result / Alert），完整展示错误摘要与重试按钮 |
| 失败状态被当作“未开始”渲染 | 报告加载遇到 `FAILED` 状态仅简单置空 `report`，导致页面退回初始空白态，关键重新核查入口消失 | 失败态是独立的第一类业务状态，必须有专门的状态机分支处理，不可粗暴降级为空白态 |

## 247. 标书质检确定性策略同义词窄门：政采“响应担保”遗漏导致免收项误报待确认（2026-09-06）

**背景**：
用户在项目 1358（竞争性谈判采购）进行 AI 标书质量核查。该项目招标文件明确规定“不要求递交响应担保”（即免收保证金），标书也在对应章节注明“本项目不适用”。然而系统在附件清单检查中：
1. 第 22 项“投标保证金回单”显示为绿色“通过”，但未标注“不涉及”，引发用户对“未附回单为何通过”的误解；
2. 第 24 项“保证金退款信息表 / 收据”被误报为黄色警告 `MANUAL_REVIEW`（“未检索到退款信息表”）。

**根因**：
1. **行业标准术语覆盖盲区（政采/谈判/询价使用“响应担保”）**：
   竞争性谈判等政府采购与非招标采购方式中，官方法定术语为“响应担保/响应保证金”，句式常为“不要求递交响应担保”。而确定性提取器 `DeterministicDepositExtractor` 词表中仅收录了“投标保证金”、“投标担保”，遗漏了“响应担保”以及“不要求递交”句式。
2. **规则前置确定性穿透失效引发连带误判**：
   `BidDocumentQualityEngine` 原本设计了“若招标文件免收保证金，则第 22 项直出免收说明、第 24 项联动直出免除退款信息”的确定性闭环。但因确定性提取器遗漏了“响应担保”，未能提取出 `depositRequirement.required() == false`，导致联动机制失效，进而使得第 24 项退款信息表落入通用检查逻辑，报出不合理的待确认警告；
3. **核查结论语义缺少统一的“【不涉及】”显式标识**：
   系统数据模型仅有 PASS/FAIL/MANUAL 三态，免收项按合规逻辑归入 PASS；但在结论文案中缺少统一的前缀标签，导致用户一眼看去无法区分是“附了回单而通过”还是“免收不涉及而通过”。

**修复方案（标准 6 步门禁）**：
1. **扩充政采响应担保同义词与“递交/提交”句式（`DeterministicDepositExtractor.java`）**：
   - 增加“不要求递交响应担保”、“不要求递交投标保证金”、“免收响应担保”、“不收取响应担保”、“无需提交/递交响应担保”等 24 个常见政采与谈判免收短语；
   - 升级条目与表格正则 `NOT_REQUIRED_CELL_PATTERN`，全面覆盖“响应担保 / 响应保证金”与“不要求递交/不要求提交/无需递交”；
2. **全链路规范输出带“【不涉及】”结论前缀**：
   - `BidDocumentQualityEngine.java` 与 `SubItemAndAttachmentRules.java` 在识别到免收/不涉及时，统一输出带有 `【不涉及】` 前缀的结构化结论文案；
3. **增强语义页面召回检索词（`SemanticPageRetriever.java`）**：
   - 为 `DEPOSIT_RECEIPT` 与 `DEPOSIT_REFUND` 补充“响应担保”、“响应保证金”、“免收响应担保”等 cues，确保语义召回与双轨融合闭环。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 招投标术语体系的不对称假设 | 研发往往只按标准公开招标（“投标保证金”）设计规则，忽视了竞争性谈判、磋商等政采场景下的法定术语（“响应担保”、“响应保证金”） | 确定性提取器必须覆盖全采购模式的法定同义词矩阵（招标/谈判/磋商/询价），并在单测中引入真实采购前附表测试用例 |
| 联动依赖上游确定性事实，上游漏词引发下游误报雪崩 | 第 24 项“退款信息表”本身依赖前置“免收”判定；前置漏判导致下游无法触发免除逻辑，误报黄色待确认，损害系统可信度 | 存在强依赖关联的检查项（如回单与退款账户），联动策略需双向自洽，并在提取层建立多维度语义闭环 |
| “免收”直接归为“通过”引发用户认知歧义 | 业务系统未设计独立的 NOT_APPLICABLE 状态，合规通过与免收不涉及在 UI 上混杂为相同绿色标签 | 在无法立即重构状态机枚举的情况下，结论文本首部必须带上 `【不涉及】` 统一标识，消除用户疑惑 |

---

## 340. 质检引擎严禁以生硬确定性模板覆盖大模型深度比对证据链；前端标签自适应【不涉及】彻底消解认知歧义（2026-09-06）

**背景**：
在解决项目 1358（政采竞争性谈判）投标保证金免收识别问题中，开发曾尝试在后端 `BidDocumentQualityEngine` 中以确定性免收结论直接短路替换 `DEPOSIT_RECEIPT` 结果。结果导致原大模型（LLM）深度语义分析找出的极其详尽的论据（“招标文件第二章‘供应商须知前附表’第3.4款明确规定‘不要求递交响应担保’；投标文件在‘文件五 响应担保（如有）’章节中明确标注‘本项目不适用’”）被生硬的通用模板覆盖，丧失了清晰的条款与章节依据，被用户一眼指出“改了之后反而没有之前的清晰和有依据”。

**根因**：
1. **混淆了“状态判定”与“证据呈现”的职责边界**：
   大模型在研判此类免收项目时，不仅能判断“是否免收”，更能精确指出招标文件具体哪一章哪一款（如第3.4款）以及投标文件在哪个章节做出了呼应（如“文件五”标注“本项目不适用”）。用固定模板短路覆盖，属于“倒洗澡水连孩子一起倒掉”，抹杀了系统最核心的 AI 深度论证价值。
2. **根源问题是前端展示标签错位，而非后端证据不足**：
   用户最初疑惑的根本原因在于：UI 右侧状态标签显示为绿色的【通过】，让用户误以为系统把“未附回单”当成了“已附回单审核合格通过”。真正应该做的是前端在检查项属于免收/不涉及通过时，将状态标签自适应渲染为专属的【不涉及】（info 标签），既保留大模型详尽的证据依据，又直观传达业务合格属性。

**根治措施**：
1. **后端保留大模型深度比对结论，仅规范化冠以【不涉及】前缀**：
   在启用了 LLM 时，`DEPOSIT_RECEIPT` 优先接通 LLM 进行全面比对；若 LLM 判定免收通过，完整保留大模型关于章节条款的详述，并在结论头部规范化补齐 `【不涉及】` 统一前缀；同时退款信息表（`DEPOSIT_REFUND`）联动免除；
2. **前端自适应状态标签解析（`qualityCheckDetail.js`）**：
   前端在解析检查项状态时，通过 `isNotApplicableItem` 自动识别 `PASS` 且带有免收/不涉及关键词的项，将紧凑行卡片与详情弹窗的状态标签自适应渲染为 `不涉及`（info 灰色/蓝灰色 tag），彻底解决用户认知错位。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 用生硬模板短路覆盖大模型结果 | 误以为“既然免收就直接写免收模板最保险”，忽视了大模型已经找出的章节条款证据价值 | 确定性逻辑用于指导状态流转或兜底降级，严禁无差别覆盖大模型已经产出的高质量条款证据链 |
| 以为是后端问题，实则是 UI 状态标签误导 | 看到用户反馈“没识别出不涉及”，急于在后端做手脚，未洞察用户其实是看到绿色的“通过”标签产生了违和感 | 区分“数据层状态”与“呈现层标签”，针对免收等特殊 PASS 场景，前端应自适应呈现专属标签 |

---

## 341. Sidecar 独立容器发布脱节与跨行评分表父子分值重叠消重根治（2026-09-07）

**背景**：
在项目 1371（通用技术MRO项目）中，用户反馈 AI 评分项标准解析结果不准（原本标准总分应为 100 分，系统解析出 82 分缺失或 104 分膨胀）。
排查发现两重问题：
1. **测试服务器 Sidecar 容器运行的是缺少 `/extract-tables` 接口的旧镜像**，导致 DOCX 原生表格结构化提取接口报 404，静默降级为 lossy 的 Markdown 解析，造成表格合并结构全失、评分细则丢失（总分仅 82 分）；
2. **在恢复结构化表格接口后，出现跨行单元格分值膨胀至 104 分**：“资源实力（6分）”跨 3 行合并单元格，内含 3 个 2 分子规则。提取器在第 1 行提取了大项括号分 6 分，在后 2 行提取了子项各 2 分，导致 6+2+2=10 分，膨胀了 4 分。

**根因**：
1. **发布闭环缺陷导致容器更新脱节**：
   - `package-release.sh` 仅打包了前端与 Java 后端产物，长期遗漏 `document-converter-sidecar/` 目录；
   - `remote-deploy.sh` 仅做前后端文件同步，无容器变更感知机制，远端 Docker 镜像不会自动重新 build 与容器重启；
   - 部署验证仅探活 `/health`（返回 200 即放行），而业务层新增的 `/extract-tables` 接口 404 被静默忽略，依赖降级掩盖了服务缺损。
2. **跨行合并单元格（`rowspan > 1`）父子维度与细则得分包含关系未消重**：
   - 跨行单元格“资源实力（6分）”同时作为父级分类与第 1 行的内容；
   - 当行内已包含评分细则（如“每提供一个合同得2分，满分2分”）时，单项真实最高分应为细则分 2 分，而非大项合并总分 6 分；旧逻辑优先取了大项括号分 6 分，造成大项与子项重复累加；
   - 此外，类似 `3.3(1)` 的条款编号未做清洗，导致父维度混入了非语义编号；并且 innermost 父维度与子维度容易产生自引用。

**根治措施**：
1. **构建与部署流程双闭环（发布制度性防漏）**：
   - **打包闭环（`package-release.sh`）**：打包发布归档时，自动将 `document-converter-sidecar/` 打包为 `sidecar.tar.gz`，并记录校验和 `sidecarSha256`；
   - **自动重构容器（`remote-deploy.sh`）**：部署时比对远端 `.deployed_sha256`，若 sidecar 发生变更，自动在远端执行 `docker build -f Dockerfile.cn` 并重启 `xiyu-sidecar` 容器；
   - **探活强门禁（`deploy-test.sh` / `deploy-prod.sh` Step 6）**：部署验收环节对 `POST http://127.0.0.1:8000/extract-tables` 强制探活，返回 404 或不可达直接阻断部署。
2. **`StructuredTableCandidateExtractor` 跨行父子重叠消重与维度规范化**：
   - **行内细则分优先消重**：检测跨行单元格（`rowspan > 1`），当提取到的 `bracketWeight` 属于该跨行大项，且当前行（或 detail）已存在具体分值 `maxGradeScore < bracketWeight` 时，单项分值取 `maxGradeScore`（2分），彻底消除 6+2+2 的膨胀；
   - **条款编号过滤**：清洗形如 `3.3(1)`、`2.2` 的编号，避免污染父维度名称；
   - **维度层级防自包含**：将最内层父级（如“资源实力”）作为该行 `dim`，上级分类（如“技术评分标准”）作为 `parentDim`，消除 `parentDim == dim` 的无效自引用；
   - **金标测试用例固化**：以 1371 真实 14 张表格入参为金标（`1371_tables_v1.json`），断言严格提取 16 项，商务 56 分 + 技术 44 分 = 100.0 分严格收敛。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| Sidecar 容器依赖手工维护，上线流程无感知 | 认为 Python Sidecar 变动频率低，未纳入 CI/CD 发布包与自动化部署流水线 | 微服务/多容器体系下，所有部署包必须打包全量运行时组件，部署脚本自动比对指纹增量重构容器 |
| 健康检查虚高（`/health` 200 但关键业务接口 404） | 只检查进程存活，未针对业务核心契约进行端到端探活 | 关键功能上线时，在部署验证脚本中增加对应业务端点的探活强门禁，禁止静默失败 |
| 表格解析未处理跨行大项与细则共存 | 招投标表格大量采用首列跨行大项（含大项总分）+ 尾列细则小分的设计 | 跨行单元格提取时必须识别父子包含语义，行内有细则得分时大项总分不得穿透作为单项分值 |

---

## 342. 评分项全量保全白名单、价格公式表识别与超载误杀彻底切断（2026-09-07）

**背景**：
在项目 1359（中海油标讯项目）等招标文件结构中，商务、技术、报价由独立表格呈现（Table 2 商务评审表、Table 3 技术评审表、Table 4 价格公式表），且各表分值权重设计各异（技术表细则小计即为 100 分，同时附带 30% 权重；价格表无显式分值，仅有复杂报价折算公式）。旧机制存在三大致命问题：
1. **价格公式行被直接丢弃**：由于价格表未标注分值数字，旧提取逻辑视其为资格审查或说明行直接过滤，导致报价评分项完全缺失；
2. **总分超载剔除（Overload Trim）对真实评分项无差别绞杀**：技术表 25 项分值和为 100，加上商务表 13 项后总分膨胀至 170+ 分，触发了 `WeightOverloadTrimPolicy`。由于商务表前置单元格未带有 `1.`、`2.` 等严格条款数字编号，被误判为“无编号弱项”而遭到整表 13 项无情抹杀；
3. **Sidecar 协议头缺失隐患**：Java 端 `SidecarTableExtractor` 调用 Sidecar `/extract-tables` 时未声明 `X-Schema-Version: 1.0` 请求头，随时面临 Sidecar 升级 v2 返回嵌套结构导致反序列化抛异常，进而静默降级为 lossy Markdown 的次生灾害。

**根因**：
1. **评分项识别与比重拟合（凑100分）严重耦合**：
   旧系统试图通过超载剔除（WeightOverloadTrimPolicy）来强行把抽取到的多表分值“砍到 100 分”，导致“为了凑总分而随意删减真实项”，本末倒置；真正的诉求应当是 **全量识别、零行丢弃**（Task 1），随后由比重折算算法（Task 2）去按商务/技术/价格大类加权归一。
2. **超载剔除优先级策略存在致命盲区**：
   超载策略单纯依据是否有数字编号（`NUMBER_PATTERN`）来决定剔除次序，忽视了从结构化表格（DOCX Table）抽取的真实候选项本来就具有极高置信度，导致格式没有编号的整张商务表被无情清洗。
3. **价格公式表识别缺失**：
   未识别“报价/价格/公式”等特殊计算表格，未对无分值公式提供安全兜底机制（提取公式作为 detail，weight 赋 0.00）。

**根治措施**：
1. **Sidecar 协议版本强契约**：
   `SidecarTableExtractor` 在调用 `/extract-tables` 时补齐 `.header("X-Schema-Version", "1.0")`，彻底杜绝数据模型不一致引发的静默降级；
2. **价格公式表与无分值项全量兜底**：
   - `StructuredTableCandidateExtractor` 实现 `isPriceFormula`，当表格或单元格命中报价/公式特征时，保留完整公式内容为 `detail`，权重安全兜底为 `BigDecimal.ZERO`；
   - 过滤表尾汇总行（`合计 / 总计 / 汇总 / 总分 / 小计`），杜绝统计行转为评分项；
   - 其余无分值且非公式说明行（如资格审查、普通备注）仍保持过滤，兼顾 1371 等单表结构的干净度；
3. **独立分表章节大类自动推导与维度层级继承**：
   实现 `inferSectionDimension`，针对独立分表精准判定 `商务 / 技术 / 报价` 大类并自动注入维度层级（`parentDim` 或 `dim` 前缀）；同时对包含混合维度的单表（如 1358/1371）保持不污染；
4. **超载剔除免疫机制（彻底阻断误杀真实项）**：
   `WeightOverloadTrimPolicy` 新增 `isProtectedRealItem` 保护屏障：凡是带有父维度（`parentDim`）或维度源明确（`dimSource != UNKNOWN`）的结构化真实项，坚决免疫超载剔除，彻底保护 13 项商务 + 25 项技术 + 1 项价格全部存活；
5. **候选治理链放行**：
   `ScoreCandidatePostProcessor` 放行价格公式项（0 分兜底项），避免被旧的零分过滤清洗；
6. **行预算与架构纯度约束**：
   `StructuredTableCandidateExtractor` 在扩展上述能力后精简至 299 行（严格处于 300 行硬上限内），且零引入 Spring/第三方框架，通过纯核心 FP-Java 测试。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 为了凑100分而随意裁减已抽取的真实评分项 | 将“候选识别”与“权重归一”混淆，误以为总分超 100 必然是多抽了脏数据 | 评分项全量识别必须零漏项（Task 1），分值比重归一化（Task 2）必须解耦，严禁通过剔除真实业务项来凑分 |
| 超载策略以“是否有数字编号”作为生死线 | 假设“所有正规评分项都有序号”，忽略了许多标书的商务表直接以文字分类为行首 | 结构化表格解析出的真实项应具备免疫超载剔除的特权，非必要不删项 |
| 公式型评分无分值导致被判定为噪音抛弃 | 仅以是否解析出分值数字作为评分项的标准，忽略了价格分常以公式体现 | 建立价格公式项专门兜底机制，保留公式全文作为细则，赋予零分兜底等待后续比重折算 |

---

## 343. 评分项全量归并消重、报价细则去重与属性继承保护（项目 1359 修复复盘）（2026-09-07）

**背景**：
项目 1359（https://winbid-test.ehsy.com/project/1359）在 AI 评分标准解析中暴露出 6 大核心缺陷：
1. “财务综合评价（25）”出现两次（条目重复）；
2. 序号 S5 “报价质量（15分）”细则内容整段重复两次；
3. 序号 S9 与 S23 重复（自有配送人员数量，2分），需删除 S9 保留精炼 S23；
4. 序号 S8 与 S22 重复（自有物流配送车辆数量，2分），需删除 S8 保留精炼 S22；
5. 缺失“品牌丰富度（4分）”；
6. 缺失“个性化需求响应度（3分）”。

**根因深度复盘**：
1. **跨通道维度异名但细则相同导致消重穿透与回注重复**：
   - 结构化表格首列大类标题（`二、财务状况（25分）`）因 `rowspan=1` 未被识别为跨行父维度，而被当成行维度 `二、财务状况`，LLM 则提取为精炼要素名 `财务综合评价`。两路候选 detail 完全相同，但旧 `ScoreItemMergePolicy` 要求维度文字重叠或包含，导致判定为不同项不予归并。
   - `ScoreGlobalMergeOrchestrator` 的结构化回注兜底机制（`makeDeduplicationKey`）仅使用精确 `dim + "|" + shortDetail` 比对，误认为表格项被 LLM 遗漏，强行再次回注，产生双份财务评价项。
2. **定性评分项误触价格公式跨 chunk 拼接**：
   - “报价质量”维度命中 `PriceScoringKeywords` 的“报价”词元，被判定为价格类项。
   - 两路召回的细则在标点上有微小差异（`.15分` vs `。15分`），旧逻辑因未做标点归一化而误判定为互补段落，`appendLines` 按行去重失效，把同一段文字追加两次。
3. **单元格内嵌长句导致提取维度过长且无法与精炼项吸收合并**：
   - 表格单元格原文字为“根据应答人自有物流配送车辆数量进行评分自有物流配送车辆数量（2分） 优秀得2分...”，提取器未剥离“进行评分”前缀长句，生成 50+ 字符的冗长维度，且细则提取未能精准分割括号后内容。与 LLM 召回的精炼项合并时，旧包含规则要求 `coreDim.equals`，无法合并。
4. **属性继承截断导致多表超载剔除误杀**：
   - LLM 归并后输出的精炼项 detail 与表格池项长度相同，`ScoreMergeResultGuard.upgradeWithLongestPoolDetail` 仅在 `cand.detail().length() > maxLen` 时才赋 `parentDim` 和 `dimSource`，导致未继承表格通道的 `CELL` 权威属性（`dimSource` 仍为 `UNKNOWN`，`parentDim` 为空）。
   - 由于 1359 是商务 100 分 + 技术 100 分的多表项目，总分 200 > 100.5 触发 `WeightOverloadTrimPolicy`。由于丢失了 `parentDim` 且无数字编号，品牌丰富度与个性化响应度被判定为“无编号弱项”误杀。

**根治措施**：
1. **细则等价强制归并与权威维度优选**：
   - `ScoreItemMergePolicy.findMatchingIndex`：只要细则在标点归一化后相等且长度 >= 15（编号兼容），强制归并为一项。
   - `resolveAuthoritativeDim`：维度选择时，淘汰罗马数字章节标题（`^[一二三四五六七八九十]+、.*`）与超长句子（>25字），优先采纳精炼评分因素名称。
   - `ScoreGlobalMergeOrchestrator`：结构化表格回注增加 `isCoveredByAccepted` 语义覆盖检查，杜绝向结果集中重复回注已存在的项。
2. **定性评分隔离与标点容错**：
   - `ScoreItemMergePolicy.enrich`：`isPriceMerge` 显式排除定性评分项（含“质量/合理性/方案”）；跨 chunk 拼接前与行去重时执行标点归一化（`normalizePunctuation`），杜绝因句号/逗号差异导致的段落重复拼接。
3. **单元格内嵌分值解析与前缀剥离**：
   - `StructuredTableCandidateExtractor`：识别单元格中的 `BRACKET_NUM`（如 `财务综合评价（25）`），自动剥离“进行评分/进行评价”冗余前缀，提取后置文本作为 detail。
4. **属性无条件补齐与原文背书免疫**：
   - `ScoreMergeResultGuard.upgradeWithLongestPoolDetail`：只要细则匹配，无条件继承候选池中结构化项的 `parentDim`、`dimSource` 与 `code`。
   - `WeightOverloadTrimPolicy.isProtectedRealItem`：增加原文背书包含保护（`sourceText.contains(detail)`），确保真实提取项永不被超载清洗。

**纪律与教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 试图通过直接改数据库表解决问题 | 侥幸心理，将“页面显示正确”误认为“问题已解决” | **严禁直接修改数据库数据**！直接改库不仅破坏数据溯源与数据一致性，更无法经受重新解析验证。所有解析问题必须在代码与模型治理层彻底修复。 |
| 两路召回消重过度依赖维度文本字面匹配 | 忽略了不同通道提取的粒度不同（大类标题 vs 细项名称） | 细则全文是条款唯一性的最坚固凭证；当细则高度一致时，应基于细则完成归并，并通过权威策略选择最优质维度。 |
| 属性继承仅在细则更长时触发 | 误将“细则文本更长”作为继承元数据的充要条件 | 元数据（`parentDim`、`dimSource`）反映的是信息源的置信度，与细则字符长短无关，匹配即应无条件继承。 |

---

## 344. 项目 1364 孤儿招标文件 404 删除死锁与通用更新接口状态篡改防御（2026-09-09）

**背景**：
在测试环境项目 1364（`https://winbid-test.ehsy.com/project/1364`）排查中发现：
1. 处于立项阶段的项目，招标文件在前端点击下载和删除均报 404，且点击删除报错后前端卡死无法恢复。
2. 该项目在前端动态中显示此前已通过立项审批并推进到“标书制作”阶段，但数据库状态为待立项（`PENDING_INITIATION`），且没有找到退回立项的日志记录。

**根因分析**：
1. **孤儿外键引用与级联缺失**：
   - 招标文件上传后其 ID（1053）被记录在 `project_initiation_details.tender_document_id` 中。当文件在后续阶段被用户从文件管理表格删除时，`ProjectDocumentWorkflowService.deleteProjectDocument` 仅处理了评标附件的解绑，遗漏了对 `project_initiation_details.tender_document_id` 的级联清理。
   - `project_documents` 中的记录已物理删除，但立项详情依然指向 1053。前端在加载时根据残留 ID 尝试渲染，点击下载与删除时后端均抛出 404；前端在捕获删除 404 错误后直接弹窗中断，未重置本地表单和组件状态，导致用户陷入“无法下载、无法删除、无法重传”的界面死锁。
2. **状态逆向覆写与审计缺失**：
   - 项目生命周期阶段是单向有限状态机（`ProjectStageTransitionPolicy`），业务上不存在倒退回立项阶段的接口。
   - 但通用的项目基本信息更新接口 `PUT /api/projects/{id}`（`ProjectService.updateProject`）既没有 `@Auditable` 注解记录审计日志，又未对状态逆向变更做拦截。若前端表单默认传值或外部脚本调用该接口带上了默认的 `status: "PENDING_INITIATION"`，会静默将已立项项目倒退回待立项。

**解决方案**：
1. **后端级联解绑**：在 `ProjectDocumentWorkflowService.deleteProjectDocument` 中注入 `ProjectInitiationDetailsRepository`，删除文档时若匹配到当前立项的 `tenderDocumentId`，自动将其清空并保存。
2. **后端状态防倒退与审计补齐**：为 `ProjectService.updateProject` 补充 `@Auditable(action = "UPDATE_PROJECT", entityType = "Project", description = "更新项目基本信息", projectScoped = true)` 注解，并在状态更新中强制校验：一旦项目离开初始状态（`existingProject.getStatus() != PENDING_INITIATION`），禁止通过通用更新接口重设为 `PENDING_INITIATION`。
3. **前端 404 容错与回显保护**：
   - `InitiationStage.vue` 中调用文档删除接口捕获到 404 错误时，视作后端已清理，直接静默同步清除本地的 `form.tenderDocumentId` 与 `bidDocFiles`，解除界面死锁。
   - `useInitiationStageActions.js` 中优化招标文件回显，仅在未通过列表接口获取到真实文档时才做兜底展示，避免覆盖真实的文档名称和上传人。

**教训与规范**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 主表外键引用在子表物理删除时未级联清理 | 仅关注了当前模块主流程的删除操作，忽略了跨表或跨阶段的单向字段引用 | 凡存在跨表单向 ID 引用字段（如 `tenderDocumentId`），在被引用对象物理删除时必须执行级联解绑检查或软删除守卫 |
| 前端删除接口失败时直接中断流程未清理悬挂状态 | 默认假设服务端“永远存在该数据”，未针对 404（资源已不存在）做降级容错 | 前端删除资源遇 404 应当作幂等成功，自动清理本地悬挂状态并允许用户重新输入或上传，防止前端状态死锁 |
| 通用更新接口缺失审计与状态机流转守卫 | 通用 CRUD 接口直接接收全量 DTO，未限制生命周期状态字段的随意修改 | 核心业务状态机的状态迁移必须收拢在专门的动作接口中；通用更新接口必须有审计日志，并严格禁止逆向倒退关键状态 |

---

## 345. 客户 SDK 注册必须同时验证 advertised 地址与时间参数（2026-09-09）

测试标讯 `4170`、`4171`（失败流水 `1880`、`1881`）通过 SDK 与相同四字段直连均返回 `result=false`；手工注册 `bid` 后，同一发布结构返回 `result=true`。在同一注册有效窗口内，真实 `POST /api/tenders` 创建的标讯 `4172` 经 SDK 返回 `result=true`，流水 `1882` 为 `SENT`；trace/span 均为 18 位且 parentId 为 `0`。这证明应用到网关受理链路可用，尚不能证明 CRM 已消费。链路 ID 已满足不超过 18 位，失败指向服务注册状态而非发布结构或 ID 长度。

根因有两部分。实际拓扑是 Java 进程监听 `18080`，Nginx 对外监听 `8080` 并反向代理到 `127.0.0.1:18080`。旧实现向事件总线宣告跨网段不可达的 Java 监听端口 `18080`，register/renew 均约需 128 秒；注册完成后的短暂窗口虽可能 publish 返回 `true`，但续约阻塞无法维持 3 秒节奏。宣告事件库可达的对外代理端口 `8080` 后，register/renew 分别约 66 ms、56 ms。其次，SDK 调度器将 `initialDelay`、`period` 按秒使用，却把 `renewalDuration` 原样发送给服务端；客户正式值是 3 秒、3 秒、3000 毫秒，旧实现的 10 秒、30 秒、60 既偏离频率，也把有效期单位解释错。使用 `8080` 并按不超过 3 秒的节奏续约 24 秒后，`getActiveService` 显示 `bid:8080` 为 `UP/ACTIVE`，标准 publish 返回 `result=true`。错误 advertised 地址与错误续约参数共同导致服务不能稳定在线。

教训：服务注册必须同时验证 advertised host/port 的可达性和续约参数，不能把 serviceAddress 当作无关元数据，也不能认为只改续约单位即可。对第三方 SDK 的时间字段，必须同时核对字段语义、数值和消费端单位；名称相邻不代表单位相同。配置应由当前集成的专用项优先，缺省才回退已验证的共享配置。注册成功后还要用 `getActiveService` 验证持续在线，不能把“线程已启动”当成在线证明。网关 `result=true` 只代表受理，最终成功仍需事件库、Kafka 和消费者日志闭环。


---

## 346. 带同步回调的 SDK 注册必须发生在 Web 服务就绪后（2026-09-10）

PR !2601 部署后，advertised 地址和 3/3/3000 续约参数均已正确，但 `bid:8080` 仍保持
`DOWN/WAIT`。应用在 07:03:05.096 由 Bean `initMethod` 发起注册，EventBus 同步回调
`/api/task/startTask` 时 Nginx 返回 502；Spring Boot 到 07:03:05.965 才完成启动。注册接口
仍返回 `code=00000`，使 SDK 启动续约线程，然而 renew 无法激活注册回调失败的实例。

应用就绪后手工 register 一次的对照实验使实例立即变为 `UP/ACTIVE`，此后即使停止手工续约，
SDK 自身仍每约 3 秒更新 `lastRenewalTime`。这排除了 advertised 地址、续约参数和 SDK 续约线程，
并把根因收敛到注册生命周期。修复是移除 `@Bean(initMethod="register")`，改在最低优先级
`ApplicationReadyEvent` 监听器中触发注册。

教训：外部注册协议如果会同步回调本服务，Bean 创建完成不等于服务可被网络访问。必须选择明确
晚于嵌入式服务器启动的生命周期事件。注册 API 的 HTTP 200 或业务成功码只证明请求被接受，
验收还要核对回调状态和服务端的 `UP/ACTIVE` 状态；对照测试应同时断言“就绪前无请求”和
“就绪后注册、续约”，并用旧实现反证测试确认门禁能捕获故障。

---

## 347. 超大文件场景下的流式处理与内存边界约束（2026-09-10）

### 问题背景

测试环境项目 1374 投标文件（1.95GB docx，内含 3,025 张图片与 33.4MB `word/document.xml`、32,651 个正文段落、39.1 万字）在进行标书质量核查时失败，投标文件在前端界面中也下载失败。

### 根因分析

1. **Sidecar 容器内存硬限制与 DOM 树膨胀（核查失败根因）**：
   测试环境 `xiyu-sidecar` Docker 容器的 `--memory` 被硬性限制为 `1g`，而环境变量配置了 `SIDECAR_MAX_UPLOAD_MB=3072`。Python 转换服务使用 `markitdown` / `mammoth` 解析 docx 时，会将 33.4MB 的 `word/document.xml` 全量构建为 DOM / AST 树（包含每个段落、样式、运行块的对象模型），解析过程瞬时内存峰值突破 1GB，触发内核 cgroup OOM Killer（容器退出码 137，上游返回 HTTP 503）。Java 端 `ComplianceTargetLoader` 获取到空文本后拦截并抛错。
2. **前端下载将大文件全量载入 JS 堆内存（下载失败根因）**：
   前端 `downloadWithFilename` 统一走 Axios `httpClient.get(apiUrl, { responseType: 'blob', timeout: 120000 })`。当文件高达 1.95GB 时，Axios 会尝试在浏览器 V8 堆内存中分配 2GB+ 的连续 ArrayBuffer / Blob，超出 Chrome 堆内存限制或在 120s 内因网络传输超时而失败。

### 解决方案

1. **Sidecar 流式 SAX 解析容灾与容器规格提升**：
   - 部署层面：修改 `scripts/release/remote-deploy.sh`，将 `xiyu-sidecar` 容器内存上限从 1GB 提升至 3GB，与 `SIDECAR_MAX_UPLOAD_MB=3072` 保持一致。
   - 解析层面：在 `document-converter-sidecar/app.py` 中增加 `_is_large_docx`（检查 `word/document.xml` 解压大小是否 > 10MB）及 `_extract_docx_streaming_sax` 流式 SAX 提取器。基于 `xml.etree.ElementTree.iterparse` 逐段流式提取标题、段落与表格，并在处理后立即执行 `elem.clear()` 释放内存。实测提取 116.3 万字仅消耗 13.58MB 内存，耗时 3 秒，内存降低 98.7%，彻底免疫 OOM。
2. **前端下载改造为原生流式落盘**：
   - 工具函数：在 `src/utils/download.js` 增加 `triggerNativeDownload(url, filename)`，并在 `downloadWithFilename` 中增加 `options.native` 分支。
   - 界面组件：在 `ProjectDocumentTable.vue` 和 `DraftingStage.vue` 中调用 `downloadWithFilename(downloadUrl, filename, { native: true })`。后端通过 302 重定向至 OBS 签名直链后，由浏览器原生下载引擎接管流式写入本地磁盘并支持断点续传，零 JS 堆内存开销。

### 经验教训与规范

| 维度 | 常见陷阱 | 规范与对策 |
|---|---|---|
| **容器内存** | 环境变量允许 3GB 上传，但 Docker 容器仅分配 1GB 内存，参数矛盾 | 容器资源配额必须与应用配置的最大处理能力严格对齐，禁止小内存容器承载大配额任务 |
| **文档解析** | 全量 DOM/AST 解析大文档引发内存爆炸 | 对超大 XML/文档必须提供流式 SAX/StAX 纯文本提取兜底，处理完即清理节点引用 |
| **文件下载** | 前端统一用 `responseType: 'blob'` 下载，导致大文件打爆浏览器内存 | 文档与交付物等用户上传的大文件下载必须使用原生 `<a>` 标签，交由浏览器内核流式落盘 |

---

## 348. 评分标准解析中的冗余公式项过滤、客观业绩匹配与证据弹窗展示治理（2026-09-10）

### 问题背景

项目 1374（中核集团大标，总分 100 分）在"AI 评分标准解析"中暴露出三个典型问题：
1. **最后一项“评标基准价为”多余且推高总分**：最后一项为 1 分的“评标基准价为”，其细则与上一项 30 分的“价格部分”内容完全重复，使权重和达到 101 分触发 `WEIGHT_SUM_MISMATCH` 与 `REVIEW_REQUIRED` 警告。
2. **客观项“央企案例”有案例却判不满足（DANGER / 0 分）**：业绩库中实际有 113 条签约日期在 2022 年 1 月之后、其中 20 条为 MRO 工业品的央企长协合同，但评分项因未匹配到而判定为不满足（0分）。
3. **详情弹窗中“表格单元格证据”千篇一律**：所有评分项弹窗中均显示 `[自动通过] [LEGACY_FALLBACK] [置信度 100%]`，缺乏真实的单元格定位证据，产生误导。

### 根因分析

1. **表格结构化提取与全局归并去重盲区**：
   - 提取侧：表格单元格中包含“评标基准价为：...”等公式说明文字，`StructuredTableCandidateExtractor` 的 `NON_DIM_TEXT` 未过滤公式说明短语，将其误判为独立评分项；
   - 归并侧：LLM 输出的 detail 包含 Markdown 强调符号（`**投标报价折扣率：**`），而表格候选为纯文本。`ScoreGlobalMergeOrchestrator` 在计算覆盖关系时未剥离 Markdown 符号，导致字符串包含判定失败，未被覆盖而重新回注，导致项数 +1、权重变 101。
2. **客观业绩匹配条件与数据建模脱节**：
   - 关键词提取缺乏维度感知与中文长句切词缺陷：`extractKeywords` 仅扫描 `detail` 并按标点粗切取前 6 项，因首段是通用长句说明，在提取到“中央企业”和“MRO”前就被截断。且 `dim`（"央企案例"）未参与提取。
   - 客户类型枚举与检索字段缺失：业绩库将央企建模在 `customer_type` 枚举（`CENTRAL_SOE`），而原 `ProjectMatchService` 仅模糊匹配 `industry`（行业），未识别 `CustomerType`，也未检索 `contract_name`（MRO 工业品大多在合同名称中）。同时 `EstimatedScoreService` 传递的 `signedAfter` 为 null，未从“时间为2022年1月至今”提取签约起始时间。
3. **前端证据卡片无差别展示旧流水线兜底状态**：
   - 历史回退数据（`LEGACY_FALLBACK`）的 `evidenceCellIds` 为空，前端没有条件守卫，机械渲染固定的 mock 标签，且未映射为易懂的中文。

### 修复方案

1. **多重防御过滤价格公式与归并去重**：
   - 在 `StructuredTableCandidateExtractor` 中将“评标基准价”、“基准折扣率”、“分值计算”等公式短语加入 `NON_DIM_TEXT` 排除规则；
   - 在 `ScoreGlobalMergeOrchestrator` 中引入 `cleanForComparison`，清洗 Markdown 语法（`**`等）、标点与空格，并在价格大项/公式项中增加互含与覆盖防护。
2. **知识库客观业绩匹配全流程增强**：
   - `KnowledgeCategoryPolicy` 增加领域高优先级专有词（央企、中央企业、国企、地方国企、MRO、工业品等），结合 `dim` 和 `detail` 联合提取业务关键词；
   - 增加 `extractSignedAfter` 提取签约日期约束（支持“2022年1月至今”等范围）；增加 `extractProjectRequiredCount` 基于“每增加1个得1分，最高得5分”推导满分所需业绩份数（5份）；
   - `ProjectMatchService` 引入 `CustomerType` 枚举识别与解析，在 JPA Specification 中加入 `customerType` 强过滤，并将文本关键词从单一的 `industry` 扩展为 `industry / contractName / groupCompany` 联合检索。
3. **前端证据展示体验治理**：
   - 在 `ScoreItemDetailModal.vue` 中，当 `evidenceCellIds` 为空且无权重单元格证据时（尤其是 `LEGACY_FALLBACK`），安全隐藏“表格单元格证据”卡片；
   - 增加 `extractionMethodDisplay` 字典，将 `STRUCTURED_TABLE` 等内部标识转换为友好的中文“表格结构化提取”。


---

## 349. 多渠道通知不能共用一个“完成”状态（2026-09-10）

商机预测提醒先创建站内信及企微投递任务，再发布 CRM 事件。旧实现只用
`opportunity_prediction_reminder_log.notified_at` 做一次性去重，事件发布返回 `false` 时仍留下
“已提醒”记录，后续扫描直接跳过，事件永久丢失。若简单把日志改到事件成功后再写，则事件故障期间
会重复创建站内信和企微任务。

教训：一个业务动作包含多个可独立失败的外部渠道时，去重状态必须按渠道拆分。主通知完成后保留
提醒日志，事件库用独立 `event_published_at` 表达受理状态；NULL 状态后续只重推事件，成功后补写时间。
状态拆分仍不够：批量扫描不能用一个长事务包住外部 SDK，也不能只靠可空时间戳防并发。应把 SDK 放在事务外，本地状态变更拆成独立短事务；待发事件先通过带租约超时的条件 UPDATE 原子领取，只有领取者能调用 SDK。必须用真实 Spring/JPA 事务边界测试证明提交、回滚、多实例互斥和超时恢复，手工 new Service 后观察实体 setter 不是持久化证据。

---

## 350. 大体积标书图片过滤与合规核查证据链断裂（2026-09-10）

### 问题背景
项目 1374（中国中检MRO工业品供应商入围采购项目）标书体积达 1994MB，包含 3000+ 张图片。为避免内存溢出（OOM），系统通过 `DocxMediaFilter` 剥离媒体流，且 Sidecar 使用 `mammoth` 将图片渲染为 `<img src="" />`。由于缺乏 `alt` 与有效属性，`markdownify` 在 HTML 转 Markdown 时直接将空标签剔除，导致原本在 docx 表格中粘贴了身份证、营业执照复印件的单元格在 Markdown 中退化为全空 `| | |`。

随后，AI 标书质量核查（LLM 评审与确定性规则）读取 Markdown 文本时，判定为“仅保留空占位表格未附证件”，导致响应函（固定模板称谓被判定抬头不一致）、法人授权书（身份证复印件表格为空）、营业执照等核心条款大面积误报 FAIL。同时，确定性核查策略此前硬编码要求必须从正文中提取 18 位身份证号码与 18 位统一社会信用代码纯文本，而许多投标人在附复印件时不会重复在表格中敲入 18 位数字。

### 经验教训与根因
1. **流水线过滤绝不能吞没“证据存在性”**：大文件瘦身剥离媒体二进制是性能所需，但下游依赖“是否附图”作为核查依据。图片占位符绝不能生成空标签（`<img src="" />` 会被 Markdown 解析器视作空白节点丢弃），必须保留可被文本与结构化规则感知的证据标记（如 `alt="已附图片"` 与 `src="embedded-image"`）。
2. **AI 质检的本质是“核验证据完备性”而非“虚构外部核验”**：投标系统本身没有连接公安户籍库或工商活体接口，标书质检的核心是核查投标人是否真正附上了资质凭据、是否存在未填写的占位模板或空白遗漏。只要表格中实际粘贴了证照图片凭据，即构成充分的合规事实。
3. **招标文件通用模板称谓与说明性后缀的兼容性**：招标文件示范文本中常见的“致采购人或采购代理机构：”属于合规通用称谓；项目名称填报时保留“（项目名称）”等括号说明亦不构成未填占位符废标。核查规则必须精准区分真正的未填占位符（如“【采购人名称】”、“_____”）与固定通用文本。

### 落地解决方案
1. **Sidecar 图片占位语义注入**：`document-converter-sidecar/app.py` 中 `_suppress_docx_image_inlining` 改造为返回带 `alt="已附图片"` 和 `src="embedded-image"` 的占位元素，转换后的 Markdown 表格精准呈现 `| 已附图片 | 已附图片 |`，段落呈现 `![已附图片](embedded-image)`。
2. **确定性核查策略支持图片凭证放行**：
   - `IdAuthLetterCheckPolicy`、`BusinessLicenseCheckPolicy`、`AuthorizationLetterCheckPolicy` 引入 `IMAGE_EVIDENCE_PATTERN`，识别到已附图片证据时放行 PASS，不再强制要求纯文本 18 位号码；
   - `BidLetterCheckPolicy` 增加通用示范称谓白名单，放行“致采购人或采购代理机构：”等抬头。
3. **LLM 评审基线规范对齐**：更新 `AdjudicationGuidelines` 规则 4、5、6、7、19，明确已附图片表格单元格不得判定为空留白。
4. **集成百度 PaddleOCR-VL 客户端**：新增 `BaiduPaddleVlClient`，支持将文档/图片提交至百度 PaddleOCR-VL 进行高精版面分析与结构化 Markdown 提取，并支持平滑降级。

---

## 351. 超大 docx 流式 SAX 解析通道图片节点遗漏致质检误判（2026-09-10）

### 问题背景
在 350 节针对 mammoth / markitdown 链路完成 `alt="已附图片"` 与 LLM 准则更新后，项目 1374 的法人授权委托书与身份证图片核查仍然被 AI 判定为 FAIL：“且身份证复印件部分仅有‘| 身份证（人像页） | 身份证（国徽页） |’的表格占位，未附带任何实际身份证扫描件图片或‘已附图片’凭证”。

### 根因分析（5 Whys）
1. **为什么 LLM 与规则引擎仍未收到“已附图片”？**
   Sidecar 实际返回的文本中，身份证单元格下完全没有任何标记，仍为纯文本空表格。
2. **为什么 350 节的 mammoth 补丁与 markitdown 替换均未生效？**
   项目 1374 的 docx 解压后 `word/document.xml` 大小超过 10MB，触发了 `_is_large_docx` 判断，被直接路由到 `_extract_docx_streaming_sax` 流式 SAX 解析器，根本没有走 mammoth 或 markitdown！
3. **为什么 `_extract_docx_streaming_sax` 会丢失图片？**
   `_extract_docx_streaming_sax` 最初设计用于超大文件的轻量化防 OOM 文本提取，其 `iterparse` 循环仅捕获了 `<w:t>`、`<w:tab>`、`<w:br>` 纯文本节点，对 OpenXML 中的 `<a:blip>` (DrawingML) 与 `<v:imagedata>` (VML) 等图片节点完全未做处理，直接将图片视为空元素略过。因此当身份证图片作为独立单元格或嵌入表格单元格时，单元格文本为空，下游彻底失去图片凭据。

### 落地解决方案
1. **流式 SAX 解析器补齐图片节点感知**：在 `_extract_docx_streaming_sax` 的 XML 解析事件中，对 `tag in ("blip", "imagedata")` 或以 `blip` 结尾的节点进行捕获，向当前段落 `current_p` 添加 `【已附图片】` 证据标记，并做邻近去重保护。
2. **双通道输出一致性收敛**：确保普通 docx（mammoth/markitdown 转换后清洗）与超大 docx（流式 SAX 解析）均能一致产出 `【已附图片】` 纯文本标记，无缝覆盖规则引擎正则 `IMAGE_EVIDENCE_PATTERN` 与大模型语义裁决上下文。
3. **回归测试闭环**：在 `test_docx_streaming_sax.py` 中新增表格内 DrawingML blip 图片与 VML imagedata 图片的双重单元测试，防止多解析器分流时的行为不一致。

## 352. PDF 全量解析 OOM：pdfplumber 常驻结构必须按块重开释放（2026-09-10，项目 1373 根治）

### 问题背景

测试环境项目 1373 投标文件 `湖北中烟工业品标书.pdf`（448,688,453 字节，约 428MB）触发 AI 标书质量核查，三次全部失败：`投标文件解析失败：湖北中烟工业品标书.pdf，请确认文件可正常打开后重新上传`。文件可正常下载查看（OBS 302 字节透传，不经过解析）。

### 根因分析

1. **pdfplumber 整文件常驻（零号病人）**：`document-converter-sidecar/converter.py` 的 `convert_pdf_to_pages` 单次 `open` 后遍历全页，每页 `extract_text + extract_tables` 构建的 chars/edges/tables 对象在整个转换期间常驻，峰值约 7 倍文件大小。428MB 文件以 ~2.98GB RSS 撞 3GB cgroup 上限被 OOM-killer 处决（dmesg 实证，容器 `OOMKilled=true`）。docx 在 #145~148 已获流式 SAX/去媒体三层保护，PDF 路径裸奔——典型的修 A 漏 B。
2. **60s 超时必然撞墙**：Java `SidecarStreamDocumentAdapter` 超时固定 60s，428MB 文件必然转不完；重试撞上已死的 worker 取到 503。三次日志：62s 超时 → 27s 后 503（正中 worker 死亡点）→ 62s 超时。
3. **容器配额与Chunk缺失叠加**：`SIDECAR_MAX_UPLOAD_MB=3072` 放行，但 3GB 内存扛不住 7 倍膨胀；`--memory` 在 `remote-deploy.sh` 写死，测试服 7.4GB/可用 4.5GB、生产 15GB/可用 11GB，4GB 双环境均安全，5GB+ 无 swap 下有宿主 OOM 风险。

### 修复方案

1. **Sidecar PDF 分块重开**：`convert_pdf_to_pages` 先取页数，再每 10 页（`SIDECAR_PDF_CHUNK_PAGES` 可调）open/提取/close，块内引用随函数返回释放，峰值从 O（全文）降为 O（块）。同 100 页表格 fixture 实测 189MB → 74MB，输出字节一致（`test_pdf_chunked_output_matches_whole`），耗时无回归。
2. **Java 超时分档**：`resolveTimeoutSeconds` 纯函数，>50MB 走 300s（`large-file-timeout-seconds`），未知大小回落基准档；永不抛异常契约不变（#208），仅丰富降级日志的文件名/超时档。
3. **容器 3g→4g**：`remote-deploy.sh` 一行，注释写明双宿主余量测算。

### 经验教训与规范

| 维度 | 常见陷阱 | 规范与对策 |
|---|---|---|
| **解析库内存模型** | 以为 pdfplumber 是流式的（实际全量建模），小 fixture 全绿掩盖大文件爆炸 | 接入文档解析库必须用合成大文件做峰值基准（tracemalloc + 断言上限，参照 `test_pdf_memory_guard.py`），禁止只用 KB 级 mock 测流程（#146 同类） |
| **超时配置** | 全局固定超时，大文件必然撞墙后被误报为“文件损坏” | 面向用户上传的转换调用必须按文件大小分档超时；降级日志必须带文件名与生效档位，否则 422 文案永远指向错误方向 |
| **容器配额** | 配额只对齐上传上限，不对齐“膨胀倍数×最大文件” | 加内存前先算宿主余量（无 swap 时双峰值叠加会挤爆宿主）；配额变更注释必须写明测算依据与上限理由 |

### 相关文件

- `document-converter-sidecar/converter.py`（`_convert_pdf_chunk` / `_pdf_chunk_pages`）
- `document-converter-sidecar/tests/test_pdf_memory_guard.py`
- `backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java`（`resolveTimeoutSeconds`）
- `scripts/release/remote-deploy.sh`（`--memory=4g`）

---

## 353. 标书质检资质匹配（QUALIFICATION_MATCH）双轨脱节与长句检索致盲根因及状态校准（2026-09-11）

### 问题背景

用户在 AI 标书质量核查弹窗中发现：“AI 检查结论”显示通过并声称满足全部硬性资格，但“资质比对清单”第一项（如“银行基本账户开户许可证（取消开户许可证的须提供基本存款账户银行备案资料...）”）却显示红色“未匹配”且说明提示“企业资质库及投标文件中均未检索到对应资质证明材料，存在废标风险”，顶部大标签依然显示绿色“✓ 通过”，形成严重的上下数据自相矛盾。

### 根因分析（5 Whys）

1. **为什么明细存在“未匹配/废标风险”总项依然亮绿灯“通过”？**
   整体项裁决与下方清单生成由两条完全独立的链路计算：大模型阅读全文给出 `PASS`，规则引擎独立生成 `qualDetails`。在 `BidQualityCheckAsyncExecutor` 组装结果时，仅调用 `r.withDetails(qualDetails)` 将清单挂载到结果对象上，根本没有根据清单明细状态去校准或降级总项 `r.status()`。
2. **为什么大模型通读理解了开户证明，而比对清单却判为“未匹配”？**
   当招标要求为长达 60 余字的复杂说明与括号注释时（如“银行基本账户开户许可证（取消开户许可证的须提供基本存款账户银行备案资料，包括单位名称、开户银行、账号、法定代表人或单位负责人等信息）”），规则引擎在资质库未匹配后，直接将整段文字传入 `locateInBid` 进行全文硬匹配。标书正文中仅包含“基本存款账户开户证明”或“开户许可证”，无法全字匹配 60 字长句，检索直接失败并跌落入 `UNMATCHED` 分支。

### 落地解决方案

1. **纯核心状态校准器（`QualificationDetailReconciler`，FP-Java，单文件 150 行）**：
   - 若明细存在 `EXPIRED`（证书过期）：一票否决判定为 `FAIL`，并在结论中明确点名过期证书；
   - 若明细存在 `UNMATCHED`（硬性资质缺失）：在资质库与标书均缺失属于法定废标风险，绝不得放行 `PASS`，强制降级为 `FAIL`，结论指出缺失条款；
   - 若明细存在 `ATTENTION`（需关注，标书已定位但资质库未维护或即将过期）：若原状态为 `PASS`，降级为 `MANUAL_REVIEW`（待确认），引导人工结合标书正文复核有效性；
   - 仅全量 `MATCHED` 且 LLM 裁决通过方可输出 `PASS`。
2. **标书正文检索词分层提取（`buildBidSearchCues`）**：
   - 剥离中英文括号及内部补充说明，提取前导核心资质主体名称（如“银行基本账户开户许可证”）；
   - 识别银行开户等典型凭证，自动追加“基本存款账户”、“开户许可证”、“开户证明”等常见变体候选词，消除长句硬匹配导致的失真。
3. **流水线接通与单测覆盖**：
   - 在 `BidQualityCheckAsyncExecutor` 中接通 `QualificationDetailReconciler.reconcile`，联动更新项状态、任务综合合规性与风险分；
   - 补充 `QualificationDetailReconcilerTest`、`QualificationMatchPolicyTest` 与 `BidQualityCheckAsyncExecutorTest` 全量用例。

### 经验教训与规范

| 维度 | 常见陷阱 | 规范与对策 |
|---|---|---|
| **双轨融合一致性** | 宏观大模型裁决与微观确定性规则明细各自独立运行，只挂载数据不核验状态一致性 | 任何携带子明细清单的检查项，必须在应用层组装完成后经过纯核心校准门禁（Reconciler），子明细存在硬违规/高风险时坚决阻断外层 PASS |
| **全文定位关键词提取** | 直接将招标长句（含括号、说明、标号）传入全文搜索，导致长句致盲误判未提供 | 规则引擎文本检索前必须做分层归一化与候选词展开（剥离括号说明、提取前导名词、兼容行业法定等价文书变体） |

---

## 354. 评分标准 3 列表格内嵌分值提取与结构化权威通道防污染加固（2026-09-11）

### 问题背景

在张家口银行项目 1381（《张家口银行股份有限公司宣传品、办公用品和办公耗材电商平台采购项目(最终版).pdf》）中，评分标准为 3 列（序号、评分因素、评分标准），分值内嵌在评分因素括号中（如 `投标报价（40分）`、`公司综合实力（6分）`，共 8 项合计 100 分）。
用户反馈 AI 评分标准解析只解析出 1 项（公司综合实力 6分）。
排查测试环境日志发现：
1. 全局归并大模型实际提取出 8 项（100分），但后置 `ScoreMergeResultGuard` 因严格原文包含校验，将大模型概括重写的 8 个候选全数判为幻觉丢弃（`hallucinationDropped=8`）；
2. 表格直抽候选原标记为 `TABLE_GRID` 而非 `STRUCTURED_TABLE`，无法触发 Phase 3a 原生结构化安全回注；
3. 在 `ScoreItemMergePolicy` 合并过程中，前置无背书 LLM 候选与后置表格候选合并时，表格候选的 `sourceText` 被空串覆盖、`detail` 被 LLM 概括内容覆盖甚至价格拼接污染，导致表格通道权威性丢失。

### 根因分析（5 Whys）

1. **为什么表格直抽项没有在全局归并阶段安全回注？**
   `ScoreGlobalMergeOrchestrator` 的 Phase 3a 结构化回注仅免检保护 `"STRUCTURED_TABLE"` 标记，而 `CellAnchoredScoreExtractor` 生成候选时赋予的是 `"TABLE_GRID"`，无法进入高权威免检回注池。
2. **为什么表格直抽候选的原文背书被冲刷丢失？**
   在 `ScoreItemMergePolicy` 合并候选时，前置 LLM 召回通道先入池；当后置表格通道到来时，旧三元表达式在 `base.sourceText()` 为空串时仍优先保留 base，且 `isPriceMerge` 未拦截非结构化与结构化混拼，导致真实表格物理行原文 `sourceText` 丢失或被污染。
3. **为什么维度名称出现多余空格？**
   表格单元格换行 `<br>` 清洗时简单替换为半角空格，导致汉字词组（如 `公司综合实<br>力`）被切割为带空格的非连续词，影响后续维度匹配。

### 落地解决方案

1. **表格通道原生最高权威性升级**：
   - `CellAnchoredScoreExtractor` 中将语义模式从 `"TABLE_GRID"` 全面升级为 `"STRUCTURED_TABLE"`；
   - 优化 `cleanDimName` 换行平滑清洗逻辑，汉字间换行不再产生空格。
2. **合并策略权威通道防污染保护**：
   - 在 `ScoreItemMergePolicy` 中强化结构化通道与原文背书保护：后置为 `STRUCTURED_TABLE` 或有原文背书而前置无背书时，强锁定结构化通道的 `detail`、`sourceText`、`code` 与权重；
   - 封堵 `isPriceMerge`：严禁结构化表格与非结构化通道拼接，并强制要求两端均具有原文背书（`hasSourceBacking`）。
3. **单测覆盖与全量验证**：
   - `CellAnchoredScoreExtractorTest` 补充张家口银行真实 3 列表格 8 项 100 分闭环单测；
   - `ScoreItemMergePolicyTest` 补充前置无背书 LLM 与后置表格合并的权威保护单测；
   - 针对性测试（53个）与 `scoreparse` 模块 726 个单测全绿。

### 经验教训与规范

| 维度 | 常见陷阱 | 规范与对策 |
|---|---|---|
| **通道权威性等级对齐** | 表格直抽具备 100% 逐字保真，却标记为普通 pattern 导致无法享受最高安全回注 | 确定性表格直抽通道必须对齐到系统顶层最高权威标记（`STRUCTURED_TABLE`），天然豁免大模型概括词句判幻觉阻断 |
| **多通道合并原文穿透与防污染** | 前置通道先入池，用弱信息甚至空串覆盖后置权威通道的真实原文背书与细则 | 多通道合并策略中必须以“结构化权威优先”和“原文背书优先”为铁律，禁止无背书长文本冲刷有背书精准确切文本 |

---

## 355. AI 评分解析 task2：Evidence Pipeline 主链路接入与 REVIEW_REQUIRED 不覆盖旧项（2026-09-12）

### 问题背景

PR #2620 已经补上 Sidecar v1 schema mismatch 的 fail-fast 防线，但 task2 仍存在两个链路级缺口：

1. `SidecarDocumentArtifactExtractor`、`ScoreFactShadowPipeline`、`ScoreCompletenessPolicy` 已经具备 v2 DocumentArtifact → CanonicalTableGrid → ScoreFact → completeness 的证据化能力，但生产 `ScoreParseAppService` 仍优先走旧 v1 table extractor / markdown / LLM 后处理；Evidence Pipeline 停留在“影子只读验证器”，无法承担最终落库决策。
2. legacy 后处理把 `REVIEW_REQUIRED` 仍当成“可展示有效项”直接覆盖 `score_item` 并触发估分。权重缺口、结构不完整、完整性回补触发等状态本质是不可信结果，继续覆盖旧项会把“待复核”伪装成新的生产评分标准。

### 根因分析

- **状态机与落库边界混淆**：`COMPLETED` 只是异步任务终态，不等于解析结果可覆盖业务评分项。真正决定是否覆盖的是 `ScoreParseDecision`。
- **影子链路未接主流程**：v2 artifact 的事实与证据已经可验证，但 AppService 没有在 docx 主入口优先消费 `SidecarDocumentArtifactExtractor`。
- **历史妥协被测试固化**：旧测试命名与断言仍写着 `REVIEW_REQUIRED still persists`，把事故期为了“先展示”的权宜逻辑变成了回归保护。

### 落地规则

1. **docx 主链路优先走 Evidence Pipeline**：
   - `StructuredTableExtractionHandler` 在存在 `SidecarDocumentArtifactExtractor` 时调用 v2 `/extract-tables`；
   - 成功后执行 `ScoreFactShadowPipeline.run`，并把招标正文传入 `ScoreCompletenessPolicy` 解析声明总分，得出 `AUTO_APPROVED / REVIEW_REQUIRED / PARSE_FAILED / STRUCTURE_INCOMPLETE`；
   - `run_summary` 与 task 字段写入 `EVIDENCE_PIPELINE_V1`、决策状态、fallback reason。
2. **只有 `AUTO_APPROVED` 可覆盖 `score_item`**：
   - `AUTO_APPROVED`：双写 facts/evidence，覆盖 `score_item`，触发预计得分；
   - `REVIEW_REQUIRED`：只写 facts/evidence 与 task 复核状态，保留旧 `score_item`，不触发估分；
   - `STRUCTURE_INCOMPLETE`：完成任务但保留旧项；
   - `PARSE_FAILED`：失败终态，保留旧项。
3. **AppService 继续保持纯编排**：Evidence 完成收口放在 `StructuredTableExtractionHandler.markEvidencePipelineCompleted`，`ScoreParseAppService` 保持 300 行以内。

### 回归锚点

- `ScoreParseAppServiceTest#parse_docxEvidencePipelineAutoApproved_skipsLegacyRecallAndPersistsFacts`
- `ScoreParseAppServiceTest#parse_docxEvidencePipelineReviewRequired_preservesExistingItemsButPersistsFacts`
- `ScoreParseAppServiceTest#parse_reviewRequired_preservesExistingItemsWithReviewFlag`
- `ScoreCompletenessAcceptanceTest#invariant3_reviewRequired_preservesExistingItemsWithReviewFlag`

### 经验教训与规范

| 维度 | 常见陷阱 | 规范与对策 |
|---|---|---|
| **任务终态 vs 业务可信度** | 把异步任务 `COMPLETED` 当作“可覆盖评分项”的信号 | 覆盖 `score_item` 必须以 `ScoreParseDecision.AUTO_APPROVED` 为唯一门槛 |
| **影子验证器孤岛** | 领域链路已可验证，但主流程继续绕回 legacy 路径 | 影子链路成熟后必须接入生产入口，并用“跳过 legacy recall”的断言锁定 |
| **复核态误覆盖** | `REVIEW_REQUIRED` 仍写入正式评分项，导致旧可用结果被半成品洗掉 | `REVIEW_REQUIRED` 只能保留 facts/evidence 与复核原因，不覆盖旧项、不估分 |

## 356. AI 评分解析 task3：人工复核闭环必须有可见快照与审计入口（2026-09-12）

### 问题背景

task2 已经把 Evidence Pipeline 接入主链路，并规定 `REVIEW_REQUIRED` / `STRUCTURE_INCOMPLETE` 不覆盖正式 `score_item`。但如果前端只展示“待人工复核”状态，而没有 facts/evidence 快照和复核审计入口，业务上仍然无法闭环：用户看不到需要复核的原文证据，也无法证明“已人工复核并决定保留现有评分项”。

### 根因分析

- **状态提示不等于工作流闭环**：`decisionStatus=REVIEW_REQUIRED` 只是信号，必须配套证据快照与动作入口。
- **审计表未接应用层**：`score_parse_review` 表和实体已存在，但没有 Service/Controller 写入路径，导致 Phase 5 任务看似完成、实际不可用。
- **前端状态易残留**：抽屉复用时，若只在 meta 字段有值时赋值，缺字段响应会沿用上一个项目的复核状态。

### 落地规则

1. 新增 `/api/projects/{projectId}/score-parse/review`：
   - `GET` 返回最新解析任务的 facts/evidence 与 `score_parse_review` 审计记录；
   - `POST` 只写复核审计，严禁借复核动作隐式覆盖正式 `score_item`。
2. 前端在 `REVIEW_REQUIRED` / `STRUCTURE_INCOMPLETE` 时必须展示复核快照，并提供审计动作。
3. 抽屉每次加载都必须重置 `decisionStatus/reviewReason/fallbackReason/pipelineVersion/artifactSha256`，避免复核状态串项。

### 回归锚点

- `ScoreParseReviewAppServiceTest#getReviewSnapshot_returnsFactsEvidenceAndAuditsForLatestParseTask`
- `ScoreParseReviewAppServiceTest#submitReview_savesAuditOnlyAndReturnsSnapshot`
- `useScoreParseDrawer.js#loads review facts for REVIEW_REQUIRED and records retention audit`

### 复核完成后的待复核提示收口（2026-09-13）

### 问题背景

人工点击“记录已复核，保留现有项”后，审计记录已经写入且按钮已置灰，但页面仍由 `decisionStatus=REVIEW_REQUIRED` 显示“待人工复核”。

### 根因分析

- `decisionStatus` 表示原始解析决策，人工保留动作不会也不应把它伪造为 `AUTO_APPROVED`。
- 前端此前只用 `reviewSubmitted` 控制按钮，没有把已存在的 `RETAIN_ORIGINAL` 审计作为人工复核完成信号参与待复核面板和徽章展示。

### 落地规则

保留原始 `decisionStatus` 语义，以成功提交或加载到 `RETAIN_ORIGINAL` 审计派生人工复核完成状态；完成后关闭待复核面板、显示“已完成复核”，刷新/重开仍从持久化审计恢复，失败请求仍允许重试。

## 357. AI 评分解析 1381：验收标准必须回到招标文件评分表全量项（2026-09-12）

### 问题背景

项目 1381 在测试环境部署最新 PR 后仍只展示 1 个评分项。此前多轮修复已经证明 Sidecar、facts、复核快照等局部环节有输出，但用户看到的最终 `/score-parse/items` 仍严重丢项，说明验收口径偏离了业务目标：真正期望不是“链路某处有候选”，而是招标文件评分表中的所有评分项都出现在最终评分标准里。

项目 1381 招标文件“综合评分法”表的完整期望是 8 项：投标报价 40、公司综合实力 6、类似业绩 10、技术方案 10、供货方案 10、人员配备 6、质量管理方案 10、售后服务方案 8，合计 100。原文中“技术方案/供货方案”均编号为 4，应保留原文编号，不自行重排。

### 根因分析

1. **PDF 分页续表断裂**：Sidecar 实际已经抽到“类似业绩（10分）”，但它位于无表头续表页。后端把续表首行当表头处理，导致第 3 项及后续结构错位或丢失。
2. **非评分表污染**：第 20 页资格/形式/响应性评审表含“评审标准”等词，被宽松的 score table 识别误标为评分表，污染事实池。
3. **总分声明取错局部满分**：投标报价规则里有“满分30分/满分10分”，完整性策略先命中局部满分，未优先识别表尾“满分（100分）”。
4. **合计行进入候选风险**：表尾“合计/得分/满分（100分）”不是评分项，必须在事实抽取阶段剔除，否则会造成伪候选或权重判断噪声。

### 落地规则

- PDF 评分表识别必须同时满足“评分表头 + 权重评分项/合计行”，并显式排除资格评审、形式评审、响应性评审等初步评审表。
- 遇到上一张表有评分表头、下一张表无表头但首列继续编号或合计，且列数一致时，应合并为同一逻辑评分表并保留原始 `pdf:page/table/row/col` 证据坐标。
- 完整性策略解析声明总分时，应从全文候选中选择合理的最大正向总分，避免局部规则满分覆盖整表总分。
- 评分事实抽取必须跳过“合计/满分/总分/得分”汇总行。

### 回归锚点

- `document-converter-sidecar/tests/test_pdf_table_artifact.py#test_pdf_score_table_detection_excludes_preliminary_review_table`
- `document-converter-sidecar/tests/test_pdf_table_artifact.py#test_pdf_score_table_continuation_is_merged_for_project_1381_shape`
- `PdfEvidencePipelineTest#project1381LikePdfSplitScoreTable_extractsAllEightScoreItemsAndTotal100`
- `ScoreCompletenessPolicyTest#evaluate_declaredTotalPrefersOverallTotalOverLocalFullScorePhrases`

### 验证证据

- 真实 1381 PDF smoke：第 20 页初步评审表 `isScoreTable=false`；第 24/25 页评分表合并为 10 行，包含 8 项 + 合计行，0 warning。
- Sidecar 回归：`pytest document-converter-sidecar/tests/test_pdf_table_artifact.py document-converter-sidecar/tests/test_extract_tables_v2.py -q` → 19 passed。
- 后端回归：`mvn -f backend/pom.xml -Dtest=DeterministicScoreFactExtractorTest,PdfEvidencePipelineTest,ScoreCompletenessPolicyTest,ScoreCompletenessAcceptanceTest,ScoreParseAppServiceTest test` → 55 tests passed。

## 358. AI 评分解析 1381：声明总分必须来自结构化汇总行，不能依赖损失性 Markdown（2026-09-12）

### 问题背景

项目 1381 的 v2 Artifact 已稳定提取出 8 个评分事实，叶子权重合计也是 100，但完整性策略仍把声明总分判成 30，导致决策为 `REVIEW_REQUIRED / WEIGHT_SUM_MISMATCH`，按安全策略不覆盖正式 `score_item`，前端因而继续展示旧的 1 项。

真实 Sidecar 输出揭示了此前合成测试未覆盖的关键差异：结构化 cell 保留 `合计\n得分` 与 `满分\n（100分）`，而 `/convert` Markdown 将其投影为 `| 合计<br>得分 | 满分<br>（100分） |`。正文正则无法跨 `<br>` 和管道列边界关联“满分”与“100分”，却会命中报价细则中的局部“满分30分/满分10分”，形成 30 分误判。此前测试手工写成连续文本 `合计 得分 满分 （100分）`，因此产生了假绿。

### 根因规则

1. **证据层级倒置**：完整性决策拥有 `CanonicalTableGrid`，却丢弃结构化汇总行，转而从损失性 Markdown 反推总分。
2. **合成夹具过度理想化**：测试没有复刻真实转换器产生的 `<br>` 与列分隔，验证的是正则理想输入而非生产数据形态。
3. **“取正文最大值”只能缓解，不能根治**：正文可能含多个局部满分或其他章节总分；最大值启发式没有表格行、列、cell 锚点，无法证明该数值就是评分表声明总分。

### 落地规则

- `ScoreTableSummaryPolicy` 在纯领域层从评分表汇总行提取声明总分，并保留 `tableId/row/sourceCellId/quotedText` 证据锚点；`DeterministicScoreFactExtractor` 复用同一汇总行判定，避免识别口径分叉。
- `ScoreCompletenessPolicy` 按“结构化汇总行唯一值 → 招标正文兼容兜底 → 默认 100”解析声明总分，并输出 `declaredTotalSource`。
- 同一批结构化汇总行出现多个不同总分时必须进入 `REVIEW_REQUIRED / DECLARED_TOTAL_CONFLICT`，禁止静默取最大值或任意一个值自动覆盖。
- 真实事故回归必须使用 `/convert` 的原始 `<br>` 形态；同时保留“无结构化证据继续走正文”的兼容测试，防止修复误伤扫描件或纯文本输入。

### 回归锚点

- `ScoreTableSummaryPolicyTest#extractsDeclaredTotalFromProject1381SummaryCells`
- `PdfEvidencePipelineTest#project1381LikePdfSplitScoreTable_usesStructuredTotalWhenMarkdownBreakTagsHide100`
- `PdfEvidencePipelineTest#project1381LikePdfWithoutStructuredSummary_keepsLossyMarkdownFallbackBehavior`
- `PdfEvidencePipelineTest#conflictingStructuredSummaryTotals_requireReviewInsteadOfGuessing`
- `ScoreParseAppServiceTest#parse_project1381PdfStructuredSummary_persistsAllEightItems`

## 359. 多张独立评分表按表拆分校验，归一化告警以 weightWarning 为唯一判据（2026-09-13，spec 062）

### 问题背景

一份招标文件含多张独立评分表（如技术 60 / 商务 40 两张）时，旧实现按整份文档合并校验声明总分：文档级 `declaredTotal` 取其中一张表的 60，而全表叶子权重合计是 60+40=100，比较 60 vs 100 必现"声明总分冲突 / 权重失配"→ `REVIEW_REQUIRED`，正式评分项列表为空，用户看到"未识别出评分项"。

### 根因规则

1. **校验单元错位**：文档级 `declaredTotal` 与全表 `totalWeight` 属不同粒度（一张表 vs 全部表之和），多表场景下无直接可比性，比较必现误报。
2. **归一化告警判据混用**：正确判据是"分表后本表 `leafWeightSum` vs 本表声明总分"，旧实现误用文档级声明总分。

### 落地规则

- `ScoreTableSplitPolicy` 以 `sourceTableId` 分组评分项，每张表按本表声明总分独立校验；各表自洽即本表全部评分项落库为正式 `score_item`。
- `StructuredTableExtractionHandler` 归一化告警以 `weightResult.weightWarning()` 为唯一判据（分表后 leafWeightSum vs 本表声明总分），单表失配保留原告警语义。
- 文档级聚合按最差决策（PARSE_FAILED > STRUCTURE_INCOMPLETE > REVIEW_REQUIRED > AUTO_APPROVED）仅用于任务状态标记，**不阻塞按表落库**。
- `run_summary` 新增 `tableSplits` 明细，逐表输出 tableId / declaredTotal / decision，供前端复核展示。

### 回归锚点

- `ScoreTableSplitPolicyTest`（纯核心：分组 / 独立校验 / 文档级聚合）
- `PdfEvidencePipelineTest#multiTableTender_eachOwnTotalSelfConsistent_allTablesAutoApproved`
- `StructuredTableExtractionMarkCompletedTest`（落库层：多表自洽不误报 + tableSplits 明细 + 单表失配告警）
- golden 夹具 `case-multi-table-tech-commercial`（两张独立评分表输入形态锁定，F1=1.0）

> 维护纪律：多表拆分以"表"为基本校验单元，文档级聚合只做状态标记。新增多表形态回归必须同步金标夹具或回放测试。

## 360. 合并维度原行兼具父总分与首个叶子时，事实模型必须一行双产物（2026-09-13，项目 1377）

### 全链路证据与五个为什么

1. 页面显示 0 项，是因为正式 `score_item` 没有被新解析结果覆盖。
2. 没有覆盖，是因为 Evidence Pipeline 对相关评分表给出 `REVIEW_REQUIRED / WEIGHT_SUM_MISMATCH`。
3. 权重失配，是因为表内叶子合计分别只有 95 / 86，而原始评分表声明总分为 100。
4. 少掉的权重都位于纵向合并维度的起始物理行：该行同时包含“维度标题（组总分）”和“第一条细则 + 显式分值”。旧提取器每个物理行只生成一个事实，且显式分值优先，导致起始行被建成父事实，第一条叶子被吞掉。
5. 零号病人是事实建模粒度错误：把“物理行”误当成“一条业务事实”，没有表达同一行同时承载父级结构事实与叶子评分事实的能力。

按工程纪律七类根因归类：主根因是**追症状不追根因**；多轮 PR 只处理合计行、声明总分或分表裁决，没有验证每个真实叶子是否进入事实集。伴随根因是**测试不测根因**与**盲目相信已修复**：旧 golden 甚至把“原行首个叶子缺失”固化成期望数量，局部全绿不能证明生产版式完整。

### 根治契约

- `DeterministicScoreFactExtractor` 仅在五项结构证据同时成立时拆分合并起始行：维度单元格 `rowspan > 1`、标题存在唯一组总分、起始行存在非空细则、显式分值为正且唯一、合并范围内至少还有一条“非空细则 + 正显式分值”的续行。
- 起始行生成两个不同 ID 的事实：父事实使用标题总分和标题单元格证据，状态为 `REVIEW_REQUIRED`，仅参与父子结构；首个叶子使用显式分值与整行证据，状态为 `AUTO_APPROVED`，并指向父事实。
- 后续合并续行全部指向同一父事实；父链索引只接收映射后的维度列合并区域，防止同一行的编号列纵向合并覆盖维度 origin。自动评分只累计叶子，禁止父总分与子项重复相加。
- 仅有空白续行的版式不得误拆；`rowspan` 本身不是父子评分组的充分证据。

### 防回归证据

- 新增脱敏 v2 Artifact 夹具 `merged-parent-first-leaf-table-v2.json`，锁定 90 分独立项 + 10 分合并组（5 + 5）的生产同形态。
- `DeterministicScoreFactExtractorTest#merged_origin_with_group_total_and_first_leaf_emits_both_facts` 锁定编号列与维度列同时纵向合并时的父/首叶双事实、不同且可重放 ID、证据类型和完整父子关系。
- `DeterministicScoreFactExtractorTest#merged_origin_requires_unambiguous_title_first_detail_and_scored_continuation` 锁定三类不拆分反例。
- `ScoreFactShadowPipelineTest#merged_origin_fixture_preserves_parent_total_and_all_leaf_weights` 锁定端到端 3 个叶子合计 100、`AUTO_APPROVED`。
- `GoldenCorpusBaselineTest` 的 1347 / 1362 语料同步纠正：父项仍不计分，但起始行首个子项必须保留。
- Scoreparse 全包回归 774 项通过（2 项按既有真实语料环境条件跳过）；架构测试 14 项、golden replay 12 项通过，golden 汇总 F1=100%。

> 发布纪律：代码、回归与文档通过只证明修复在当前工作树成立；合入、部署后仍必须对项目 1377 重新解析，并以 `/review`、`/items` 与页面三层一致为真实环境验收标准。

## 361. 标书质检资质提取跨章节粘连与评分细则/失信惩戒混淆导致一票否决误判（2026-09-13，项目 1377 / 金标语料 1359）

### 全链路证据与五个为什么

1. 质检弹窗中【基本信息检查-资质匹配】与【分项合规检查-资格条件一票否决】双双报 FAIL，并包含数千字符长文本。
2. 报 FAIL 是因为 `QualificationVetoPolicy.checkMandatoryQualifications` 对提取出的强制资质项在投标文件全文中做 `contains` 匹配失败。
3. 资质项中出现了整套商务/技术评分细则（长达 5229 字符）以及“企业绿码国家级认证：有/无”等应答模板，投标文件中自然不可能一字不差匹配此巨幅段落。
4. 评分细则和模板之所以被提取为 `mandatoryQualifications`，是因为招标文件缺少“三、投标人资格要求”独立章节标题，`DeterministicQualificationExtractor.locateSections` 退回全文扫描；同时 `BULLET_PATTERN` 未将 Markdown 标题（`##`、`###`）和表格边界（`|`）作为强切分点，导致前附表提示与跨章节评分细则粘合成超长 chunk；且 `isSpecificCertificate` 只要含“认证”即判定为资质，未设长度上限与评分/模板特征词过滤。
5. 零号病人是切块粒度缺乏结构边界守卫，以及领域概念分类未做严谨分流（把评分细则、模板说明、失信惩戒与资质证书全混为一谈）。

按工程纪律七类根因归类：主根因是**边界切分不完备与概念分流缺失**；伴随根因是**防御性门禁不足**（核验层盲目信任提取层输出的长文本，未设二次有效性防护）。

### 根治契约

- **结构化强截断**：`DeterministicQualificationExtractor.BULLET_PATTERN` 引入 `\n\s*#{1,6}\s+|\n\s*\|`，任何 Markdown 标题或表格换行均作为物理块断点，彻底杜绝前附表跨章节粘连评分细则或模板。
- **长度与语义硬过滤**：
  - 规定 `MAX_QUALIFICATION_LENGTH = 100`，字数超出 100 坚决不作为专用资质证书提取。
  - 增加 `SCORING_AND_TEMPLATE_EXCLUDE_KEYWORDS`（评分标准、评审细则、打分、分值、应答格式、填写认证名称等），在候选提取阶段彻底阻断。
  - **杜绝过拟合黑名单误杀**：严禁将表格表头局部词（如“认证机构”、“有效期限”、“佐证材料”）加入全局排除黑名单，以防误杀正规资质条款；改用物理结构标记拦截（`clean.contains("|")` 强阻断表格表头与跨列行）。
- **专用资质与通用资格概念分流**：
  - 失信惩戒关键词（`PENALTY_AND_DISCREDIT_KEYWORDS` 如吊销许可证、停产停业等）从专用资质 `isSpecificCertificate` 中剔除，但保留在 `isGeneralCapacity` 通用主体资格中，保证失信被执行人等条款正常核验。
- **一票否决核验层二次守卫与 Nullability 攻击防护**：
  - `QualificationVetoPolicy.checkMandatoryQualifications` 增加守卫：`qualification == null || qualification.isBlank()` 严防 NPE，且长度 > 100 或不符合 `isSpecificCertificate` 的项跳过 contains 阻断，杜绝外部不可控数据（包括 LLM 提取可能产生的幻觉长文本）导致一票否决误判。

### 防回归证据

- `DeterministicQualificationExtractorTest#extract_incident1377_doesNotExtractScoringRulesOrTemplates` 锁定 1359/1377 真实语料回归，断言绝不提取出包含评分细则或应答模板的脏数据。
- `DeterministicQualificationExtractorTest#extract_legitimateQualificationWithCommonWords_isNotFalseExcluded` 锁定正向防误杀测试，确保包含“认证机构”、“有效期限”、“佐证材料”的正规资质证书条款 100% 被正确提取。
- `QualificationVetoPolicyTest` 补充长文本、评分项及 null 元素跳过一票否决硬阻断的守卫用例。
- 合规质检域核心套件（涵盖 `DeterministicQualificationExtractorTest`、`QualificationVetoPolicyTest`、`QualificationMatchPolicyTest`、`QualificationDetailReconcilerTest`、`QualificationGradePolicyTest`、`BidDocumentQualityEngineTest`、`BidQualityCheckAsyncExecutorTest` 等 90 个测试）100% 通过。
- 单文件行数预算审计：`DeterministicQualificationExtractor.java`（275 行）与 `QualificationVetoPolicy.java`（203 行）严格维持在 300 行预算以内。

> 维护纪律：文本提取必须依托物理版式边界（标题/表格）截断，禁止依赖纯换行猜测；严禁用局部业务词汇作全局排除黑名单，必须以结构化标记（表格竖线/格式前缀）阻断表格残留；一票否决等强阻断规则必须做空值、长度与资质类型二次门禁。

## 362. 标书质检一票否决检查结论冗长与确定性闭环断层治理（2026-09-13）

### 全链路证据与五个为什么

1. 质检弹窗中【资格条件一票否决】（`QUALIFICATION_VETO`）检查结论出现大段长篇叙述（数百字符），内容冗长模糊，且不通过时未列出招标文件的完整要求项。
2. 结论冗长是因为旧逻辑在标书正文满足所有提取的硬性资质时直接返回 `Optional.empty()`，移交第二阶段 LLM lane 深度裁决；大模型在此场景下生成了大量套话与大段叙述。
3. 第一阶段策略之所以在资质全部满足时不直接输出结论，是因为原有第一阶段策略被片面设计为“只拦截 FAIL、不自闭环 PASS”，将确定性成立的事实遗漏交给了具备随机性与废话倾向的 LLM。
4. 在不通过分支上，旧逻辑遇到第一个未满足项时直接 break 并单项报错，未能对有效要求项与未满足项进行集合汇总。
5. 零号病人是领域核验策略缺乏“确定性事实优先形成自闭环”的设计意识，把规则核验降级为单纯的单点拦截器，未将比对结果精炼清晰地向用户呈现。

### 根治契约

- **确定性闭环 PASS 与精炼结论**：
  - 在 `QualificationVetoPolicy.checkMandatoryQualifications` 中，对提取并清洗后的专用资质集合 `validQualifications` 做全量正文核验；
  - 若所有资质均在标书中得到满足，直接构造并返回 `QualityCheckItemResult.pass`：
    - 结论格式：`"招标文件要求的一票否决资格项均已满足：【" + String.join("、", satisfied) + "】。"`
    - 证据：同时组装招标文件资质要求定位与标书资质响应证明（物理跳过目录欺骗页）；
    - 彻底阻断此场景退回 LLM lane，从根源上消除了数百字的大段叙述，实现毫秒级确定性响应。
- **不通过分支聚合说明与精准对比**：
  - 若存在未满足项（`!unsatisfied.isEmpty()`），汇总并清晰输出：
    - 结论格式：`"招标文件要求了一票否决资格项【" + String.join("、", validQualifications) + "】，标书中未满足【" + String.join("、", unsatisfied) + "】。"`
    - 建议操作：`"请在投标文件中补充对应资格项的证明材料或在企业知识库中维护有效记录"`
    - 证据：组装招标文件未满足项定位与知识库匹配证据。
- **去重与降级防护**：
  - 资质列表去重保证文案中不重复显示同一证书；
  - 若招标文件未提取出任何专用资质证书，则返回 `Optional.empty()`，保持既有通用条款或人工复核流程平滑运作。

### 防回归证据

- `QualificationVetoPolicyTest#checkFirstStage_allMandatoryQualificationsSatisfied_returnsPass` 锁定全部满足直接 PASS 并输出精炼结论。
- `QualificationVetoPolicyTest#checkFirstStage_catalogDeceptionOnly_returnsFail` 锁定不通过时完整输出要求项与未满足项。
- `QualificationVetoPolicyTest#checkFirstStage_duplicateQualifications_deduplicatedInConclusion` 锁定重复资质提取去重。
- `BidDocumentQualityEngineTest` 32 项用例全绿，全量 1953 个策略测试通过。
- `QualificationVetoPolicy.java` 行数严格维持在 237 行（<= 300 行）。

> 维护纪律：第一阶段确定性核验策略若已具备完全充分的事实依据（如硬性资质全集比对），必须形成 PASS/FAIL 完整自闭环，禁止空转回退给大模型生成冗长废话；比较类结论必须精准列出要求基准与未满足项，禁止仅报单点错误。

---

## 363. 全量 ESLint 扫描范围漂移与基线 warning 无损清理（2026-09-13）

**背景**：全量 ESLint 门禁排查时发现，直接在工程根目录执行 `npx eslint .` 会产生 1310 处 error 和 758 处 warning，而项目规范命令 `npm run lint`（`eslint src e2e`）仅扫描 `src e2e`，实际 0 error、247 warning（退出码 0，正常通过）。

**根因分析**：
1. **全仓扫描范围漂移**：`eslint.config.js` 的 `ignores` 配置了 `'wechat-miniprogram/**'`，但在仓库真实路径为 `'integration/wechat-miniprogram/**'`，导致小程序 CommonJS 与 `wx` 全局被当作浏览器 ESM 扫描；同时 `skills/` 与 `.agents/` 未加入忽略，引入了千余个外部脚本与依赖的语法报错。
2. **调试代码与意图缺失 warning**：基线中 247 个 warning 包含了 12 个 `no-console`（11 处 E2E 测试残留 debug 打印、1 处 Sentry 必要日志缺少 lint 豁免）和 4 个 `no-empty`（`catch {}` 裸空块未书写业务降级意图注释）。

**根治方案**：
1. **无损清理 12 个 no-console**：删除 E2E 测试用例中的 `[DEBUG ...]` 临时日志；对 `src/sentry.js` 关键初始化日志添加 `// eslint-disable-next-line no-console` 显式注解。
2. **意图注释消除 4 个 no-empty**：在 `WarehouseDialog.vue`、`WarehouseDrawer.vue`、`TenderCreatePage.vue` 的静默降级 `catch` 块中补齐清晰中文意图注释，明确说明容错场景。
3. **验证结果**：`npm run lint` 告警数从 247 降至 231（`no-console` 与 `no-empty` 完全清零），前端 3131 项单元测试与 pre-push 门禁全绿。

> 维护纪律：日常静态扫描必须通过统一规范脚本 `npm run lint` 执行；业务与测试代码中禁止遗留临时 `console.log` 调试语句；对合理静默降级的 `catch` 块必须显式补充意图注释，避免触发 `no-empty` 警告。
