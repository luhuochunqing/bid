# Research: 评分解析生产风险收口

## 1. 自动解析门闩放在哪

**Decision**: `GET /items` 的 `meta` 增加 `lastParseStatus`（无任务为 `null`）与 `lastParseError`。前端仅当 `items` 为空且 `lastParseStatus === null` 时自动 **POST 新建**。`PENDING`/`PROCESSING` 跟随已有任务（POST 复用或轮询 status），不算新建。`FAILED` 展示原因，不自动 POST。无可用正文时仍 400，但先落 FAILED 任务，避免每次打开抽屉再自动打。

**Rationale**: 「自动开始」= 新建 PARSE 任务。进行中跟随是 US1/AC4，不是再开一轮。无源若只 400 不建任务，`lastParseStatus` 会一直是 null，形成自动重试循环。

**Alternatives considered**:
- 改 `GET /parse/status` 在无任务时返回 `NONE`：前端要多打一次状态接口，且会改变「无任务抛错」的旧语义。
- 纯前端记 sessionStorage：换浏览器/同事会再自动打一轮，不符合「项目从未有过任务」。

## 2. 50MB 如何截断而不整包进堆

**Decision**: 远程下载用流式读取，累计达到 50MB + 1 字节即中止并失败；若响应头 `Content-Length` 已 > 50MB 则根本不开始读 body。本地 storage 路径先看字节长度再交给抽取器。

**Rationale**: 立项/项目文档上限已是 50MB；审查点名禁止无界 `ofByteArray()`。先看 Content-Length 可避免对超大 OBS 对象白下。

**Alternatives considered**:
- 只信 Content-Length：头可缺或可伪造，必须同时做累计截断。
- 把上限做成配置项：本规格明确沿用 50MB，不做新配置以免漏配。

## 3. hasSource 与 resolve 对齐

**Decision**: `hasSource` / `resolve` 都走 `resolveIntake`。立项能读出非空正文 → 用立项；否则非空快照 → 用底稿；否则 empty，并区分 `emptyReason`：超大无底稿用「招标文件超过 50MB，无法解析」，其余用「请先在立项阶段上传招标文件」。立项读失败只打日志，不挡底稿。

**Rationale**: 规格 FR-006 要求两入口同一套成功条件。抛异常会让 trigger 建任务后立刻 FAILED，并挡住可用底稿。

**Alternatives considered**:
- 立项失败直接 FAILED、不回退：违反 User Story 3。
- hasSource 只看「有没有文档行」：正是 #2296 审查坐实的不一致。

## 4. HttpClient 生命周期

**Decision**: 解析器持有一个可注入的共享 `HttpClient`（构造时创建一次，或 Spring Bean），禁止每次 `newHttpClient()`。测试仍注入 `UrlContentFetcher`。

**Rationale**: 审查 P2；Java 21 `HttpClient` 应复用。注入点已存在，改动面小。

**Alternatives considered**:
- 每次 try-with-resources 新建：合法但无必要，连接池用不上。

## 5. 初稿文案与上线说明

**Decision**: `ProjectTenderBreakdownDialog` 提示改为服务拆解/评分解析；在 `docs/implementation-notes/score-parse-initiation-tender.md` 写明初稿入口下线是产品决定及现行路径。

**Rationale**: 规格 FR-009/010。说明放已有实施笔记，不另开营销页（Assumptions）。

**Alternatives considered**:
- 恢复初稿按钮：产品已否决。
