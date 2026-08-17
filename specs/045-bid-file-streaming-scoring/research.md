# Research: 投标文件流式落盘与文本量设限打分

**Feature**: 045-bid-file-streaming-scoring | **Date**: 2026-08-17 | **Status**: Complete

> Phase 0 产出。基于 2026-08-17 代码调研（10 项事实清单见文末附录），解决 clarify 遗留到 plan 的全部未知项，并修正两处被代码事实证伪的 clarify 决策前提。

## R1. 快路径分流信号 — 修正 clarify Q6/FR-011（前提被证伪）

**Decision**: 放弃「预判分流」（DB 元数据 / Content-Length 预检），改用**流式自适应 spill**：下载开始写内存 buffer，累计 ≤50MB 全程内存；一旦超过，把 buffer flush 到临时文件并继续流式写盘。快路径由数据本身的大小自动成立，零元数据依赖。

**Rationale（代码事实）**:
1. 打分链路**不读 `bid_file` 表**——`ScoreBidDocumentLookup.findBidDocuments`（L71-80）按 `documentCategory ∈ {BID, BID_FILE, BID_DOCUMENT}` 读 `project_document` 表，fileUrl 来自 `ProjectDocument.fileUrl`。clarify Q6 的主信号 `bid_file.file_size` 在打分链路**不可得**。
2. `ProjectDocument` 虽有 `file_size` 列，但实体映射是 `String size`，写入点 `BidDocumentUploadService.formatSize`（L103-108）格式化为 `"1654.6MB"` / `"512KB"`——**精度丢失**（1 位小数），不可作为可靠分流信号。
3. OBS Content-Length 预检需要额外 HEAD 往返，且 chunked 编码时可能缺失。
4. spill 模式是 `BoundedHttpDownloader.readAtMost`（L62-75，8KB buf 流式累计）骨架的最小扩展——`ByteArrayOutputStream` 换成「内存 buffer + 超限转 FileOutputStream」，Boring Proven。

**Alternatives**:
- ~~`bid_file.file_size` 预判~~：链路不可得（上述事实 1）。
- ~~`ProjectDocument.size` 解析~~：格式化字符串，精度丢失（事实 2）。
- ~~HEAD Content-Length 预判~~：多一次 RTT；chunked 时失效；边界文件（50MB±）仍需 spill 兜底，等于两套逻辑。

**Spec 影响**: FR-011 的分流信号描述需回写（待用户确认后同步 spec.md）。

## R2. sidecar 流式接口形态 — 修正 clarify Q3 细节（文件路径 → 流式上传）

**Decision**: sidecar 新增 `POST /convert-stream`：请求侧 multipart 流式上传（chunked），响应侧 `StreamingResponse` 纯文本流式输出。**不采用文件路径输入**。

**Rationale（代码事实）**:
1. 现有 `POST /convert`（app.py:72-155）**请求侧已是流式**：`await file.read(64KB)` 循环 + `SIDECAR_MAX_UPLOAD_MB`（默认 30MB）超限中止（413），转换中写 `NamedTemporaryFile`。FastAPI `UploadFile` 本身 spool-to-disk。→ 请求侧只需调大上限配置，骨架完全复用。
2. 现有 `/convert` **响应侧是全量 JSON**（`{"markdown": ...}`，app.py:136-144）——这是唯一需要新增流式路径的部分。
3. 文件路径输入耦合部署拓扑（要求 sidecar 与后端同机且进程可读后端临时目录）；sidecar 未来容器化/分机部署时该假设破裂。流式 HTTP 上传无拓扑假设。
4. Java 端 `MarkItDownSidecarExtractor`（docinsight）用 `ByteArrayResource` 整包上传——大文件必须换 `FileSystemResource`/`PathResource`（contentLength 已知，`ResourceHttpMessageConverter` 流式写 body，零整包内存）。

**Alternatives**:
- ~~文件路径输入~~：部署拓扑耦合，容器化即碎。
- ~~改造现有 `/convert` 为流式响应~~：docinsight 模块在用（依赖 JSON 结构：sections/tables/warnings），动它扩大爆炸半径。新增端点隔离风险。

## R3. 临时文件管理 — 命名约定 + 4 类清理挂点，零 DB 迁移

**Decision**: 临时文件按 `{tempDir}/{taskId}.part` 命名（约定优于配置表）；**不加 DB 列**。清理挂 4 类点：

| 挂点 | 时机 | 方式 |
|---|---|---|
| `doExecuteScoring` finally | 任务正常/异常终态 | 删 `{taskId}.part` |
| `ScoreParseTimeoutScanJob.processScan` | 30min 超时兜底 | markTimeout 后删文件 |
| `ScoreParseTaskRecoveryRunner.recover` | 服务重启恢复 | failTask 后删文件 |
| 启动全清（`TempBidFileStore` 初始化） | 进程启动 | 删 tempDir 下全部 `*.part`（进程重启 = 无进行中任务） |

**Rationale（代码事实）**:
1. 调研确认 scoreparse 模块当前**零文件操作**（grep `createTempFile|Files.|FileOutputStream` 无命中），全部挂点是新增，无存量冲突。
2. taskId 在任务终态路径（`markCompleted`/`failTask`/`markTimeout`）全程可得，按 taskId 命名让清理无需查 DB。
3. `ScoreParseTaskStateService.failTask` 是三层降级（DB→仅 status→清 Redis 等扫描兜底），文件清理挂调用方（AppService/ScanJob/RecoveryRunner）而非 StateService 内部，避免降级路径漏删——真正兜底是启动全清 + 扫描兜底，两层即可覆盖 SC-003。

**Alternatives**:
- ~~`score_parse_task` 加 `temp_file_path` 列~~：需要 Flyway 迁移 + 回滚脚本，而路径可由 taskId 约定推导——列是冗余状态。
- ~~只挂 StateService 单点~~：failTask 三层降级中「仅更新 status」层拿不到文件路径上下文之外的保证，多点挂载 + 启动全清更稳。

## R4. 磁盘配额实现 — 进程内 AtomicLong + 启动扫描

**Decision**: `TempBidFileStore` 内 `AtomicLong currentTotalBytes`：spill 开始前 CAS 检查 `已用量 + 本次预估 ≤ max-total-bytes(4GB)`；下载流式累计中每 chunk 更新实际值；单文件超过 `max-file-bytes(2GB)` 即中止。进程重启后计数从 0 开始（启动全清保证目录为空，计数归零自洽）。

**Rationale**: 单实例部署（systemd 单进程），无跨进程争用；OBS 单对象 5GB 上限 + 单文件配额 2GB 先行拒绝（Content-Length 预检 + 流式累计双保险，复用 `BoundedHttpDownloader.rejectIfDeclaredTooLarge` 骨架）。

**Alternatives**:
- ~~Redis 分布式配额~~：单实例无收益，引入网络依赖（违反 VIII Boring）。
- ~~文件系统 quota~~：需 root 运维改动，超出代码交付范围。

## R5. 文本量上限度量口径 — 按字符数（5,000,000 chars）

**Decision**: `app.score-parse.bid-file.max-text-chars` 默认 `5_000_000`。10MB 按 UTF-16 度量 = 2 bytes/char ≈ 500 万字符，与 spec「10MB（约 500 万字）」口径一致。实现点：sidecar 流式响应读取循环中累计 char 计数，超限即中止连接（`IOException` → 转 `OversizedBidTextException`），不缓冲完整文本后再判。

**Rationale（代码事实）**: `bidDocText` 全文在 `doExecuteScoring`（L174-209）全程常驻单个 @Async 线程内存，消费方式为摘选器（`ScoreDocExcerptExtractor`，O(n) 扫描）+ 章节哈希（`BidChapterDirtySet.split`）——10MB 文本 ≈ 20MB char[]，单任务可承受，**无需把文本也落盘**（文本落盘会显著复杂化摘选器的随机访问，违反 VIII）。

**Alternatives**:
- ~~按 UTF-8 字节数~~：中英混排时字节数与「字数」直觉偏离（1 汉字 3 字节），用户文案「10MB」与实际拒绝阈值的关系不稳定。
- ~~文本也落盘流式读~~：摘选器需要全文随机访问，流式改造复杂度高，收益仅 20MB 堆——不值。

## R6. 异常语义模型 — 三类拒绝，各自独立类型 + 文案

**Decision**:

| 异常 | 触发 | 文案（MESSAGE 常量唯一源） | 语义类别 |
|---|---|---|---|
| `OversizedBidTextException`（新） | 提取文本 > 5M chars | 「投标文件文本量超过上限（约 500 万字），无法完成打分，请检查是否误传或拆分文档」 | 业务类（FR-004） |
| `BidFileQuotaException`（新） | 单文件 >2GB 或总量超 4GB | 「投标文件过大超出服务器处理配额，请联系管理员」 | 资源类（FR-002） |
| `OversizedBidFileException`（现有） | **语义废止** | 现有「超过 50MB」文案随字节硬限一并移除 | — |

Controller 转换沿用现有模式：`ScoreParseController` L102-112 的 `IllegalArgumentException` 语义码映射新增两条；前端无独立文案文件（调研确认 `src/` 中「超过 50MB」两处命中均为招标文件预校验，与投标链路无关），改 MESSAGE 常量即全链路生效。

**Rationale**: spec FR-002 要求配额拒绝与文本量拒绝「可区分」；独立异常类型是编译器可校验的区分，文案区分只是运行时。

## R7. 执行器占用 — 不拆下载执行器

**Decision**: 流式下载保持在 `scoreParseExecutor`（core=1, max=2, queue=20）内同步执行；新增 `app.score-parse.bid-file.download-timeout-seconds`（默认 600s）作为 `HttpRequest.timeout`。

**Rationale（代码事实）**: 打分任务本身是分钟级 LLM 流水线（每项一次 LLM 调用），排队是该池的既有语义；内网 OBS 下载 2GB 量级（10-60MB/s）约 0.5-3.5 分钟，在 30min 任务超时内。`BoundedHttpDownloader.get` 现有 60s 超时（L34）对 50MB 上限合理，对 2GB 不够——改为配置项。CallerRunsPolicy 队列满阻塞 HTTP 线程的风险在现状已存在，非本 spec 引入。

**Alternatives**:
- ~~独立下载执行器（tenderImportExecutor 范式）~~：下载完成后仍需回到打分流水线，跨池编排引入状态传递复杂度；收益仅是队列视觉隔离（违反 VIII）。

## R8. 配置项清单 — 沿用 @Value 范式

**Decision**: scoreparse 模块无 `@ConfigurationProperties` 先例（调研事实：全部 `@Value` + 默认值），新配置沿用：

| 配置键 | 默认值 | 用途 |
|---|---|---|
| `app.score-parse.bid-file.ram-buffer-bytes` | `52428800`（50MB） | spill 阈值：内存 buffer 上限，超过即转临时文件 |
| `app.score-parse.bid-file.max-file-bytes` | `2147483648`（2GB） | 单文件配额（FR-002） |
| `app.score-parse.bid-file.max-total-bytes` | `4294967296`（4GB） | 并发总量配额（FR-002） |
| `app.score-parse.bid-file.temp-dir` | `${java.io.tmpdir}/xiyu-score-parse` | 临时目录（部署时按环境注入 /opt 下路径） |
| `app.score-parse.bid-file.max-text-chars` | `5000000` | 文本量上限（FR-004，R5 口径） |
| `app.score-parse.bid-file.download-timeout-seconds` | `600` | 单请求下载超时（R7） |
| `SIDECAR_MAX_UPLOAD_MB`（sidecar 侧） | `2048` | sidecar 接受上传上限（从 30 调大，对齐单文件配额） |

## R9. 生产磁盘余量 — 部署前检查项（无法本地研究）

测试环境 `/opt` 98G 余 ~8GB（已实测）；生产 99G 盘余量**未实测**。落地为部署 runbook 检查项：上线前 `df -h /opt`（或 temp-dir 实际挂载点）确认 ≥6GB（4GB 配额 + 50% 安全垫）。若不足，`max-total-bytes` 生产环境独立调小。

## R10. 上传侧关联确认

PR !2311 已解除上传侧 50MB（spec Assumptions 引用）；本 plan 不动上传链路。`BidDocumentUploadService` 的 `formatSize` 精度丢失问题（R1 事实 2）记录为独立技术债，不在本 spec 范围内修（避免扩大边界）。

---

## 附录：调研事实索引（2026-08-17）

| # | 事实 | 位置 |
|---|---|---|
| 1 | sidecar 仅 2 路由（/convert 全量 JSON + /health），请求侧已流式（64KB chunk + 30MB 上限） | `document-converter-sidecar/app.py:72-161` |
| 2 | docinsight 调 sidecar：RestTemplate + ByteArrayResource 整包上传 | `docinsight/infrastructure/extractor/MarkItDownSidecarExtractor.java:89-107` |
| 3 | BoundedHttpDownloader 已流式骨架（ofInputStream + 8KB buf 累计 + Content-Length 预检） | `scoreparse/application/BoundedHttpDownloader.java:31-75` |
| 4 | ScoreBidDocumentLookup 三链路（OBS 签名/本地/doc-insight），capSize 挂每个返回点 | `scoreparse/application/ScoreBidDocumentLookup.java:82-131` |
| 5 | 打分 fileUrl 来自 project_document（BID_FILE 分类），非 bid_file 表；ProjectDocument.size 为格式化 String | `ScoreBidDocumentLookup.java:71-80`、`BidDocumentUploadService.java:103-108` |
| 6 | 任务终态挂点：markCompleted/failTask/markTimeout + ScanJob(5min) + RecoveryRunner(启动) | `ScoreParseTaskStateService.java`、`infrastructure/scheduler|bootstrap/` |
| 7 | scoreparse 配置范式：@Value + 默认值，无 ConfigurationProperties | `ScoreParseTimeoutScanJob.java:39-48` |
| 8 | LLM 仅消费 12000 字摘选；全文常驻 doExecuteScoring 线程（L174-209）供摘选器+章节哈希 | `ScoreItemAssessor.java:21,33-55`、`ScoreDocExcerptExtractor.java` |
| 9 | OversizedBidFileException 文案唯一源 + Controller 映射；前端无独立投标文案 | `OversizedBidFileException.java`、`ScoreParseController.java:102-112` |
| 10 | scoreParseExecutor core=1/max=2/queue=20/CallerRuns；无总超时，30min 扫描兜底 | `config/AsyncConfig.java:131-139` |
