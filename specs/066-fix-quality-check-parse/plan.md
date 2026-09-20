# Implementation Plan: AI 标书质量核查解析失败误报修复（大文件超时预算与失败语义分类）

**Branch**: `agent/qoder/fix-quality-check-parse-failure` | **Date**: 2026-09-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/066-fix-quality-check-parse/spec.md`

## Summary

修复生产 4 个项目（22/35/46/580）AI 标书质量核查 100% 失败的根因链：解析等待上限与文档规模错配（60s/300s 硬档 vs 真实解析 75s–1133s）、失败被吞成空文档后统一误报为「文件可能打不开」。技术方案：把「永不抛」契约升级为**分类结果值对象**（OK/TIMEOUT/SERVICE_ERROR/EMPTY_TEXT/UNPARSEABLE），等待上限按**原始文件大小**线性分档（未知大小保守走大文件档，上限 1800s，保留环境变量调节），消费 Sidecar 的 `low_text_density` 警告识别无文本层（扫描件明确提示不支持，不引入 OCR），Sidecar PDF 分块从 10 页调到 100 页（实测重开成本 2.5s×411 次 = 1027s，占解析耗时 90%）并重新标定内存守卫上界，前端等待窗口提升到 30 分钟且到顶进入「后台执行中」态（新增模板分支）。

## Technical Context

**Language/Version**: Java 21（Spring Boot 3.2）/ Python 3.11（FastAPI Sidecar）/ Vue 3 + Vite 5

**Primary Dependencies**: JPA + MySQL 8.0（本特性无迁移）、java.net.http HttpClient、pdfplumber（Sidecar）、Element Plus

**Storage**: 无新增存储；沿用 OBS 原始文件与临时文件链路

**Testing**: JUnit 5 + AssertJ + Mockito（backend）、pytest（sidecar）、Vitest（前端抽屉 spec）

**Target Platform**: Linux 服务器（生产单实例后端 + Sidecar 容器 4GiB 内存、单 Worker）

**Performance Goals**: 441MB 级 PDF 单次解析从 1133s 降到 ≤600s（分块 100 页预期 ~224s）；165MB 级从 203s 降到 ≤100s；等待上限覆盖实测最坏规模且留余量

**Constraints**: Java 单文件硬上限 300 行（适配器现 296 行，必须先拆分）；零 Mock 政策（测试桩允许）；不引入 OCR；无 DB 迁移；不改变 Sidecar 单 Worker 与 4GiB 内存上限

**Scale/Scope**: 4 个生产项目复跑验证；改动 4 个后端类 + 3 个新纯核心/服务文件 + Sidecar 2 文件 + 前端 2 文件 + 对应测试

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
|---|---|---|
| I. FP-Java Architecture | PASS | 分类结果与超时预算下沉为纯核心（`compliance/domain`，record/final，无框架依赖）；Jackson 负载解析留在 service 层（domain 纯度门禁） |
| II. Real-API Only | PASS | 不引入任何 Mock 生产路径；测试桩仅用于单测 |
| III. TDD | PASS（计划） | 先写 RED 测试（超时/连接拒绝/空文本/非法 JSON/档位边界/前端窗口/模板态）再实现；前端改动提交按门禁要求处理 E2E 联动（补 E2E 或 `[skip e2e-scope]`，S4 决策） |
| IV. Split-First & Simplicity | PASS（受控） | 适配器 296→拆分后 ≤200 行；新增 3 个小文件（结果/预算/负载解析），无额外抽象层 |
| V. OSS Integration | N/A | 不涉及 OSS 组织服务 |
| VI. Authorization Unification | N/A | 不新增端点、不改权限 |
| VII. Defensive Collection & Graceful Degradation | PASS | 失败分类即"可诊断的降级"：传输/服务失败不再吞成空值；TenderDocumentLoader 保持容忍降级（空文档+日志），不因分类化抛错阻断招标链路 |
| VIII. Boring Proven Patterns | PASS | 线性分档 + 枚举分类 + 结构化日志，无新框架魔法 |
| 质量门禁（checkstyle/PMD/SpotBugs/行预算/前端门禁） | PASS（计划） | S5 全量执行；无 Flyway 变更 |
| 多 Agent SOP（Sync-First/Lease/WIP） | PASS | 基于 origin/main 新 worktree；who-touches 预检通过；3 个目录锁已登记；收尾 push（授权后） |

**Post-Design Re-check**: 设计未引入新依赖、未新增端点、无数据迁移；原则判定不变，全部 PASS。

## Project Structure

### Documentation (this feature)

```text
specs/066-fix-quality-check-parse/
├── plan.md              # 本文件
├── research.md          # Phase 0：决策记录（含实测基准与评审修订）
├── data-model.md        # Phase 1：值对象与状态语义
├── quickstart.md        # Phase 1：验证指南
├── contracts/
│   └── parse-outcome-contract.md   # 分类→文案→动作契约
├── checklists/
│   └── requirements.md  # 规格质量清单
└── tasks.md             # Phase 2 输出（/speckit-tasks）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/compliance/
├── domain/
│   ├── SidecarParseOutcome.java          # NEW：分类结果值对象（纯核心）
│   └── SidecarTimeoutBudget.java         # NEW：按原始大小的等待上限纯函数（纯核心）
├── service/
│   ├── SidecarStreamDocumentAdapter.java # 修改：HTTP Shell + 分类 + 结构化日志（拆分后瘦身）
│   ├── SidecarPayloadParser.java         # NEW：Sidecar 负载 → DocumentContent（Jackson 留在 service 层）
│   ├── ComplianceTargetLoader.java       # 修改：按分类输出准确文案，指名失败册
│   └── TenderDocumentLoader.java         # 修改：第二调用方，非 OK → 容忍降级空文档+日志
└── application/
    └── BidQualityCheckAsyncExecutor.java # 修改：空文档路径文案对齐分类语义

backend/src/test/java/com/xiyu/bid/compliance/
├── domain/SidecarTimeoutBudgetTest.java          # NEW
├── service/SidecarStreamDocumentAdapterTest.java # 修改
├── service/ComplianceTargetLoaderStorageDispatchTest.java # 修改
├── service/TenderDocumentLoaderTest.java         # 修改（容忍降级语义）
└── application/BidQualityCheckAsyncExecutorTest.java      # 修改

document-converter-sidecar/
├── app.py                       # 修改：单次转换可观测日志（大小/耗时/页数/字符数/warnings）
├── converter.py                 # 修改：PDF 分块默认 10 → 100 页（env 可覆盖）
└── tests/
    ├── test_converter_pdf.py    # 修改：分块参数与跨块完整性
    ├── test_pdf_memory_guard.py # 修改：按新分块重新标定 O(chunk) 上界（先实测后定值）
    └── test_app.py              # 修改：可观测日志断言

src/views/Project/stages/
├── composables/useQualityCheckDrawer.js       # 修改：等待窗口 30 分钟 + 后台执行态
├── composables/useQualityCheckDrawer.spec.js  # 修改：到顶不误报、窗口内完成行为不变
└── components/QualityCheckDrawer.vue          # 修改：后台执行态模板分支（防"尚未执行"空白态）
```

**Structure Decision**: Web application 三层结构（Java 后端纯核心+Shell / Python Sidecar / Vue 前端），沿用现有模块边界，不新建模块。

## Design Decisions (Phase 0/1 摘要，详见 research.md)

- **D1 等待上限**：`secondsFor(baseSeconds, originalSizeBytes)`：≤50MB 用基准档（默认 60s，`app.compliance.sidecar.timeout-seconds` 可调）；>50MB 起 `base×5`，并按 `+3s/MB` 线性增长，硬上限 1800s；**大小探测失败（未知）保守取 `base×5` 下限**。判定基于**原始上传文件大小**（瘦身不降档）。
- **D2 失败分类**：适配器返回 `SidecarParseOutcome`（OK/TIMEOUT/SERVICE_ERROR/EMPTY_TEXT/UNPARSEABLE），保持"不抛异常"契约；loader 统一映射用户文案；`ComplianceTargetLoader` 非 OK → BusinessException 准确文案，`TenderDocumentLoader` 非 OK → 空文档+日志（招标链路容忍降级）。「请确认文件可正常打开后重新上传」仅保留给 UNPARSEABLE/存储缺失。
- **D3 无文本层**：`EMPTY_TEXT` 判据 = 2xx 且 markdown 空且 pages 全空；`low_text_density` 警告作为补充证据写入 detail；文案为"图片扫描件/无文本层不支持"（无 OCR）。
- **D4 解析提速**：Sidecar PDF 分块 10→100（实测 reopen 2.5s/次 × 411 次 = 1027s；100 页连续提取 2.88s；预期 441MB 解析 1133s → ~224s）；内存守卫测试按 100 页分块重新标定（保持 O(chunk) 断言，实测后定预算值）。
- **D5 前端窗口**：`MAX_POLL_TIMEOUT_MS` 10 → 30 分钟；到顶转为「任务仍在后台执行」态（新增 `backgroundRunning` ref + `QualityCheckDrawer.vue` 模板分支，避免落入"尚未执行"空白态）；重开抽屉恢复真实进度。
- **D6 可观测性**：Sidecar 每次转换记录 `file/size/duration_ms/chars/pages/warnings`（空文本 WARN）；Java 侧记录 kind、原始大小、生效上限、耗时。
- **D7 范围外（记录不修）**：Sidecar 单 Worker 饥饿与重试风暴；评分链路同类超时；解析产物缓存（技术债）；OCR；多册 N×1800s 超前端窗口的残余场景（后台态兜底）。

## Slices & Checkpoints (Large 复杂度要求)

| Slice | 内容 | Checkpoint |
|---|---|---|
| T1 | 纯核心：`SidecarTimeoutBudget` + `SidecarParseOutcome` + 单测（RED→GREEN） | 单测绿；无 I/O；未知大小走大文件档 |
| T2 | 适配器拆分（负载解析外移 `SidecarPayloadParser`）+ 分类化 + 日志（RED→GREEN） | 适配器测试绿；≤200 行；连接拒绝/超时/非法 JSON 分类正确 |
| T3 | Loader/Executor/TenderDocumentLoader 文案与容忍语义（RED→GREEN） | 相关测试绿；多册指名；招标链路不回归 |
| T4 | Sidecar 分块 100 + 可观测日志 + pytest（RED→GREEN，含内存实测标定） | sidecar pytest 绿；O(chunk) 守卫保持 |
| T5 | 前端等待窗口 + 后台执行态（.js + .vue）（RED→GREEN） | vitest 该 spec 绿；无空白态死角 |
| T6 | 全量验证（backend mvn test、npm build、sidecar pytest、门禁脚本）+ 真实环境复跑 | S5 证据落盘 |

## Complexity Tracking

> 无 Constitution 违规，无需豁免。
