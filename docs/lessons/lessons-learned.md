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

---

## 114. Hibernate `week()` 函数仅接受 1 参数——MySQL WEEK() 默认 mode 0 兼容（2026-08-16）

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
