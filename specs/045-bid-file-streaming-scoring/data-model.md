# Data Model: 投标文件流式落盘与文本量设限打分

**Feature**: 045-bid-file-streaming-scoring | **Date**: 2026-08-17

## DB 变更：无

零 Flyway 迁移。依据 research R3：临时文件路径由 `{tempDir}/{taskId}.part` 命名约定推导，`score_parse_task` 不加列；配额状态是进程内运行时状态（R4），不落库。

现有表不动：`score_parse_task` / `score_item` / `score_result` / `project_document` / `bid_file`。

## 核心数据结构（新增，均为 Java 类型非 DB 实体）

### LoadedBidFile（二态值对象，scoreparse/application）

```java
sealed interface LoadedBidFile permits InMemory, OnDisk {
    /** ≤ ram-buffer-bytes（默认50MB）：全程内存，快路径（FR-011） */
    record InMemory(byte[] content, String fileName, String contentType) implements LoadedBidFile {}
    /** spill 后：内容在临时文件，零整包堆持有（FR-001） */
    record OnDisk(Path tempFile, String fileName, String contentType) implements LoadedBidFile {}
}
```

- sealed + 穷尽 switch：非法中间态不可表示（Constitution I：不可变数据消除隐式副作用）。
- 构造后只读；`OnDisk.tempFile` 的删除责任在 `TempBidFileStore`（单一所有权）。

### TempBidFileStore（有状态组件，scoreparse/application）

| 状态/操作 | 说明 |
|---|---|
| `AtomicLong currentTotalBytes` | 当前落盘总字节（配额基数，R4） |
| `Path reserve(String taskId, long declaredBytes)` | spill 前配额检查 + 登记路径（Content-Length 已知时传入；unknown 传 -1 走流式累计校验） |
| `void release(String taskId)` | 删文件 + 递减计数（幂等：文件不存在 no-op） |
| `void cleanupAll()` | 启动全清（删 `*.part` + 计数归零） |

不变式（单实例内由 synchronized/Atomic 保证）：
- `currentTotalBytes == Σ 存在的 .part 文件大小`（启动全清建立基线）
- 任意时刻 `currentTotalBytes ≤ max-total-bytes`（CAS 检查；流式累计中发现超限即中止并回滚本次登记）

### 配额/上限常量（@Value 注入，research R8 清单）

`ram-buffer-bytes` / `max-file-bytes` / `max-total-bytes` / `temp-dir` / `max-text-chars` / `download-timeout-seconds` —— 见 research.md R8 表格，此处不重复。

## 状态转换

### 临时文件生命周期（新）

```
(无) --reserve--> RESERVED(.part 已登记未写入)
     --spill/write--> WRITTEN(流式写入中/完成)
     --release(任务终态|扫描兜底|启动全清)--> (已删除)
```

- 无 FAILED 中间态：写失败（磁盘满/网络断）由调用方 catch 后立即 `release`，文件不残留。
- 与任务状态机的对应：`PENDING/PROCESSING → 终态(COMPLETED/FAILED)` 触发 release；`ScoreParseTimeoutScanJob` 与 `ScoreParseTaskRecoveryRunner` 对超时/重启任务补 release；进程启动 `cleanupAll()` 兜底（此时不可能有进行中任务）。

### 打分任务状态机（不变）

`PENDING → PROCESSING → COMPLETED/FAILED` 语义与迁移完全不变（FR-009）；新增的两类拒绝（文本量/配额）均表现为 `FAILED + errorMessage`，不引入新状态。

## 验证规则（来自 spec Requirements）

| 规则 | 实现落点 | 触发值 |
|---|---|---|
| FR-001 全链路无 byte[] 整包 | `OnDisk` 分支：下载流写文件，提取走 sidecar 流式 | >50MB 文件 |
| FR-002 配额 | `TempBidFileStore.reserve` + 流式累计双保险 | 单文件>2GB 或总量>4GB → `BidFileQuotaException` |
| FR-003 清理 | release 四挂点（research R3 表） | 任务终态/超时/重启/启动 |
| FR-004/005 文本量 | sidecar 响应流式读取 char 计数 | >5,000,000 chars → `OversizedBidTextException` |
| FR-011 快路径 | `InMemory` 分支：行为与现网一致 | ≤50MB 文件 |
