# Implementation Plan: 投标文件流式落盘与文本量设限打分

**Branch**: `agent/trae/spec045-clarify` | **Date**: 2026-08-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/045-bid-file-streaming-scoring/spec.md`

## Summary

打分读取侧的 50MB 字节硬限（PR !2314 止血）废止，改为「流式自适应落盘 + 提取后文本量设限」：≤50MB 全程内存（快路径），超过则 spill 到临时文件（配额 单文件 2GB / 总量 4GB）；大文件文本提取走 sidecar 新流式接口 `/convert-stream`（流式上传 + 流式文本输出 + Java 侧 char 计数），文本 >500 万字明确拒绝（FAILED，不部分打分）。临时文件按 `{tempDir}/{taskId}.part` 约定管理，任务终态/超时/重启/启动四挂点清理。零 DB 迁移。

两处 clarify 决策被代码事实修正（详见 research.md R1/R2，**需用户确认后回写 spec**）：
1. FR-011 分流信号 `bid_file.file_size` 在打分链路不可得（链路读 `project_document`，其 size 为格式化字符串）→ 改为流式自适应 spill，零元数据依赖。
2. FR-010 sidecar「文件路径输入」耦合部署拓扑 → 改为流式 multipart 上传。

## Technical Context

**Language/Version**: Java 21（backend）+ Python 3（document-converter-sidecar/FastAPI）+ Vue 3（无前端改动，仅后端文案常量）

**Primary Dependencies**: Spring Boot 3.2（java.net.http.HttpClient、RestTemplate）、FastAPI/StreamingResponse（sidecar）、markitdown（sidecar 既有）

**Storage**: MySQL 8.0（零迁移，现有表不动）+ 服务器本地临时目录（`${java.io.tmpdir}/xiyu-score-parse`，部署时可注入 `/opt` 下路径）+ OBS（流式下载源）

**Testing**: JUnit 5（backend，新组件 TempBidFileStore/StreamingBidFileFetcher + 既有 ScoreBidDocumentLookup/ScoreScoringAppService 回归）+ pytest（sidecar /convert-stream）+ 手工 quickstart 场景（1.62GB 真文件）

**Target Platform**: Linux 服务器（systemd 单实例，内网 OBS）

**Project Type**: web-service（后端为主 + Python sidecar 扩展端点）

**Performance Goals**: 1.62GB 文件打分全链路无 OOM；JVM 堆峰值与文件大小解耦（≤ ~100MB 文本相关占用）；小文件（≤50MB）耗时与现网相当（±10%）

**Constraints**: 单文件 ≤2GB / 并发落盘总量 ≤4GB / 文本 ≤5,000,000 chars / 下载超时 600s（均可配置，research R8）；`scoreParseExecutor` core=1 不扩（research R7）

**Scale/Scope**: 新增 Java 类 4-5 个（每个 <200 行）、sidecar 端点 1 个、删除 `OversizedBidFileException`；改动面集中在 scoreparse 模块 + sidecar，前端零改动

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. FP-Java | 纯核心（配额策略/文本计数）与外壳（下载/文件 IO）分离；`LoadedBidFile` sealed 二态，不可变 | ✅ PASS |
| II. Real-API Only | 全链路真实 OBS + 真实 sidecar，无 mock | ✅ PASS |
| III. TDD | 新组件（TempBidFileStore/StreamingBidFileFetcher/sidecar 端点）先写失败测试；ArchitectureTest 保持全绿 | ✅ PASS（tasks 阶段落实） |
| IV. Split-First & Simplicity | 新文件各 <200 行；`ScoreScoringAppService` 当前 300 行预算已满——新逻辑放新类，不回灌该文件 | ✅ PASS（结构上预防） |
| V. OSS Integration | 不涉及 OSS 组织架构服务（OBS 是文件存储，非本条管辖） | ✅ N/A |
| VI. Authorization Unification | 权限守卫 `assertCurrentUserCanAccessProject` 原样保留，无新端点 | ✅ PASS |
| VII. Defensive Collection | 新代码无 `Collectors.toMap`；异常路径（下载断/磁盘满/sidecar 不可用）全部降级为任务 FAILED + 清理，不崩模块；5xx handler 不新增 | ✅ PASS |
| VIII. Boring Proven Patterns | 复用 `BoundedHttpDownloader` 流式骨架/FastAPI 既有流式读循环/`failScoringWithPending` 既有出口；拒绝 Redis 配额/独立执行器等花活（research R4/R7） | ✅ PASS |

**Post-Phase 1 re-check**: 设计未引入 Constitution 违反项，Complexity Tracking 无需填。

## Project Structure

### Documentation (this feature)

```text
specs/045-bid-file-streaming-scoring/
├── plan.md              # This file
├── research.md          # Phase 0 output（10 项决策 + 事实附录）
├── data-model.md        # Phase 1 output（零 DB 迁移 + LoadedBidFile/TempBidFileStore）
├── quickstart.md        # Phase 1 output（7 个验证场景）
├── contracts/
│   ├── sidecar-streaming-api.md   # /convert-stream 契约
│   └── api-error-semantics.md     # 打分 API 错误语义（新旧对照）
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
document-converter-sidecar/
├── app.py                                    # 新增 POST /convert-stream（流式响应，复用既有上传流式读循环）
└── tests/                                    # pytest：/convert-stream 契约测试

backend/src/main/java/com/xiyu/bid/scoreparse/
├── application/
│   ├── ScoreBidDocumentLookup.java           # 改造：loadBytes→loadBidFile（二态），三链路接入 fetcher；capSize/OversizedBidFileException 移除
│   ├── BoundedHttpDownloader.java            # 不动（招标链路继续用；投标链路改用新 fetcher）
│   ├── StreamingBidFileFetcher.java          # 新增：流式下载 + 自适应 spill（内存≤50MB→临时文件）+ 配额双保险
│   ├── TempBidFileStore.java                 # 新增：{tempDir}/{taskId}.part 管理 + AtomicLong 配额 + 启动全清
│   ├── SidecarStreamTextExtractor.java       # 新增：/convert-stream 客户端（FileSystemResource 流式上传 + 响应 char 计数→OversizedBidTextException）
│   ├── OversizedBidFileException.java        # 删除（语义废止，FR-006）
│   ├── OversizedBidTextException.java        # 新增（业务类拒绝，FR-004）
│   ├── BidFileQuotaException.java            # 新增（资源类拒绝，FR-002）
│   └── ScoreScoringAppService.java           # 改造：接入二态加载 + finally release + 新异常映射（行数不增，新逻辑在新类）
├── controller/
│   └── ScoreParseController.java             # 改造：语义码映射表（+QUOTA，-OVERSIZED_BID_FILE）
├── infrastructure/
│   ├── scheduler/ScoreParseTimeoutScanJob.java   # 改造：markTimeout 后 release
│   └── bootstrap/ScoreParseTaskRecoveryRunner.java # 改造：recover 时 release
└── src/test/java/com/xiyu/bid/scoreparse/
    ├── application/TempBidFileStoreTest.java         # 新增（TDD 先行）
    ├── application/StreamingBidFileFetcherTest.java  # 新增（TDD 先行）
    ├── application/ScoreBidDocumentLookupTest.java   # 回归改造
    └── application/ScoreScoringAppServiceTest.java   # 回归改造
```

**Structure Decision**: 单体仓库既有分层（application/infrastructure/controller），全部改动落在 scoreparse 模块内 + sidecar 目录；docinsight 的 `MarkItDownSidecarExtractor` 与 `/convert` **零改动**（隔离爆炸半径，research R2）。

## Complexity Tracking

> 无 Constitution 违反项需要辩护——不填。

## 待用户确认事项（blocking tasks 阶段）

1. **FR-011 修正**：分流信号从「`bid_file.file_size` 预判」改为「流式自适应 spill」（research R1，事实证伪原前提）→ 确认后回写 spec.md。
2. **FR-010 修正**：sidecar 输入从「文件路径」改为「流式 multipart 上传」（research R2，去部署拓扑耦合）→ 确认后回写 spec.md。
3. 生产磁盘余量（research R9）：部署前 `df -h` 检查 ≥6GB，落在部署 runbook。
