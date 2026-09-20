# Phase 0 Research: AI 标书质量核查解析失败误报修复

**Date**: 2026-09-20 | **Branch**: `agent/qoder/fix-quality-check-parse-failure`

## 生产证据基线（R0 只读取证）

| 项目 | 文件 | 规模 | 失败机制（生产实证） | 失败耗时 |
|---|---|---|---|---|
| 22 | 通用技术集团投标文件-办公.pdf | 165MB / 2462 页 / 有文本层 | 300s 大文件档超时（sidecar 侧被项目 30 重试风暴挤占） | 301s |
| 35 | 郑州市轨道交通 投标文件7.30.docx | 565MB / 正文 18.6 万字+表格 12.9 万字 / 媒体占 97% | 瘦身后 <50MB 掉回 60s 档，实际未完成 | 65s |
| 46 | 国铁商城…投标文件-0916.pdf | 441MB / 4101 页 / 有文本层 | 300s 档超时；评分链路同文件 1133s 才完成 | 302s |
| 580 | 商务技术评审文件.pdf | 177MB / 1225 页 / **每页 chars=0、images=1（纯扫描件）** | Sidecar 75s 返回 200 空文本 + `low_text_density`；Java 丢弃警告误报 | 75s |

补充证据：生产 `backend.env` 无 `APP_COMPLIANCE_SIDECAR_TIMEOUT_SECONDS` 覆盖（60s 代码默认生效）；前端 `MAX_POLL_TIMEOUT_MS=600000`（10 分钟）必然误报长任务。

## 性能基准（生产 Sidecar 容器内实测，441MB PDF）

| 测量 | 结果 |
|---|---|
| `pdfplumber.open()` 重开成本（5 次） | 2.48 / 2.53 / 2.58 / 2.57 / 2.57 秒 |
| 单次 open 内连续提取 100 页 | 2.88 秒（≈0.029s/页） |
| 现实现（10 页/块，411 次重开） | 411×2.5 + 4101×0.029 ≈ **1147s**（与实测 1133s 吻合） |
| 预期（200 页/块，21 次重开） | 21×2.5 + 4101×0.029 ≈ **171s** |
| 预期（100 页/块，42 次重开） | 42×2.5 + 4101×0.029 ≈ **224s** |

结论：解析耗时约 90% 来自「每 10 页重开一次 PDF」的固定成本，分块调参是数量级收益，优于任何等待上限放宽。

## Decision Records

### D1 等待上限：按原始文件大小线性分档（上限 1800s）

- **Decision**: `SidecarTimeoutBudget.secondsFor(baseSeconds, originalSizeBytes)`：
  - ≤50MB → `baseSeconds`（默认 60s，沿用 `app.compliance.sidecar.timeout-seconds`）
  - >50MB → `baseSeconds×5 + (sizeMB−50)×3`，封顶 1800s
  - **大小探测失败（未知/负数）→ `baseSeconds×5`（大文件档下限）**，不回落 60s（评审 P1：大文件探测失败静默回落会再次误报）
  - 档位按**原始上传文件大小**判定（瘦身前的物理文件），不按瘦身后临时文件
- **Rationale**: 实测吞吐 1.2–2.6 s/MB（旧分块）与 0.5 s/MB（新分块），3s/MB+300s 是覆盖现状且留余量的保守包络；1800s 上限防挂死占用异步线程；未知大小保守取值把"探测失败"从静默错误变为安全侧。
- **Alternatives considered**: (a) 统一 600s——441MB 旧分块 1133s 仍不够，且无规模区分；(b) 继续 ×5 固定档——正是本次事故形态；(c) 同步 HTTP 改异步 job + 轮询——架构改动大，非本次根因最小修复；(d) 平滑 50MB 边界跳变——收益小、增加公式复杂度，不做。

### D2 失败分类：值对象返回，保留"永不抛"契约

- **Decision**: `SidecarParseOutcome`（record）承载 `kind/content/detail/warnings`，五分类：`OK / TIMEOUT / SERVICE_ERROR / EMPTY_TEXT / UNPARSEABLE`。适配器不抛异常；两个生产调用方按各自语义映射：
  - `ComplianceTargetLoader`（质量核查）：非 OK → `BusinessException(422, 分类文案)`，指名失败册。
  - `TenderDocumentLoader`（招标文件解析）：非 OK → 空文档 + `log.warn`（招标链路容忍降级，不得因分类化抛错阻断评分解析）。
- **分类边界（评审修订）**：
  - `HttpTimeoutException` 中 `HttpConnectTimeoutException`（连接建立超时）→ `SERVICE_ERROR`（服务不可达），仅请求读超时 → `TIMEOUT`。
  - 4xx：401/413 → `SERVICE_ERROR`（413 文案为"文件超过解析服务上限"，不承诺"稍后重试"）；其余 4xx（400/422 等文档级错误）→ `UNPARSEABLE`。
  - 5xx / 连接拒绝 / 其他 IOException → `SERVICE_ERROR`。
  - 2xx 但 JSON 解析失败（协议异常）→ `SERVICE_ERROR`，不得误报文件损坏。
- **Rationale**: 现有"永不抛 + 空文档哨兵"让失败不可区分（本次根因）；值对象保持 FP-Java 与调用侧零异常流，同时可携带诊断信息。评审确认 `convertDocument` 有 2 个生产调用方（另一为 `TenderDocumentLoader:89`），已纳入范围。
- **Alternatives considered**: (a) 直接抛受检/运行时异常——破坏既有契约、调用侧改动大；(b) 在 `DocumentContent` 加失败字段——污染领域模型（内容对象不应承载失败语义）。

### D3 无文本层识别：消费 Sidecar warnings，不引入 OCR

- **Decision**: 200 响应且 `markdown` 空、`pages` 空（或全空文本）→ `EMPTY_TEXT`，detail 携带 `low_text_density` 警告；文案为"未能提取文本内容（图片扫描件/无文本层），当前 AI 核查不支持，请提供可复制文字的版本"。
- **Rationale**: Sidecar 已产出该信号（`app.py` markdown <100 字符即 `warnings:["low_text_density"]`），Java 侧丢弃是纯浪费；用户已确认无 OCR 范围。
- **Alternatives considered**: (a) OCR 接入——超出范围（另立项）；(b) 仅按文件大小/后缀猜测——不可靠。

### D4 Sidecar PDF 分块：默认 10 → 100 页（评审修订：200 → 100）

- **Decision**: `_pdf_chunk_pages()` 默认值 10 → 100（保留 `SIDECAR_PDF_CHUNK_PAGES` 环境覆盖）；内存守卫测试按 100 页分块**先实测再定预算**，保持"峰值不随总页数增长（O(chunk)）"断言。
- **Rationale**: 见性能基准（100 页 → 预期 ~224s，满足 SC-005 ≤600s）；评审 P1 指出原定 200 页会使现有 100 页/100MB 内存守卫用例退化为整篇单开并击穿预算，100 页是提速与内存安全的折中；生产 4GiB 容器有充足余量。
- **Alternatives considered**: (a) 200 页——提速略优但内存守卫需大幅放宽，风险收益比差；(b) 50 页——内存更稳但重开成本回升至 ~327s，仍可用但收益减半；(c) 保持 10 页只放宽超时——441MB 仍需 ~19 分钟且反复重试成本高。

### D5 前端等待窗口：10 → 30 分钟 + 到顶非错误态

- **Decision**: `MAX_POLL_TIMEOUT_MS` 提升至 30 分钟；到顶停止轮询并进入"任务仍在后台执行，可稍后刷新查看"提示态（不设置 errorMessage、不显示错误卡片）；重开抽屉继续按任务真实状态加载/轮询。
- **Rationale**: 后端最坏规模（441MB 旧实现 19 分钟 + LLM 裁决）超出 10 分钟；即使提速后仍可能超窗，误报"执行异常"与本次故障同源。
- **Alternatives considered**: (a) 无限轮询——挂起资源且用户无预期；(b) 保持 10 分钟只改文案——长任务仍看不到结果。

### D6 可观测性：Sidecar 每次转换结构化日志

- **Decision**: `/convert` 与 `/convert-stream` 完成后记录 `file/size/duration_ms/chars/pages/warnings`（INFO；空文本时 WARN）；Java 适配器记录 kind、生效上限、耗时。
- **Rationale**: 本次排障只能靠时间戳对账推断；日志缺失使根因定位成本极高（discipline：可观测性是根因定位基础）。
- **Alternatives considered**: 仅在 Java 侧记录——拿不到 Sidecar 内部耗时与页数。

### D7 范围外记录

- Sidecar 单 Worker 串行导致解析饥饿（项目 30 重试风暴放大）→ 另立任务（并发风险）。
- 评分标准解析链路同类超时（600s/1800s）→ 独立问题。
- 解析产物按哈希缓存复用（避免重试重解析）→ 登记 `docs/exec-plans/tech-debt-tracker.md`。
- 扫描件 OCR → 用户明确排除。

### D8 S3 独立评审修订（2026-09-20，verdict: PLAN_NEEDS_FIXES → 已修订）

| 评审发现 | 级别 | 修订 |
|---|---|---|
| `TenderDocumentLoader:89` 是第二生产调用方（计划漏算影响面） | P0 | 纳入范围；非 OK → 容忍降级空文档+日志，不抛错 |
| 停止轮询后 `QualityCheckDrawer.vue` 落入「尚未执行」空白态 | P0 | 补入 `.vue` 模板分支 + `backgroundRunning` 状态 |
| 连接超时（HttpConnectTimeoutException）会被误标文档 TIMEOUT | P1 | 归 SERVICE_ERROR；分类边界写入 D2 |
| 413 提示"稍后重试"永不成功；200 非法 JSON 未定义 | P1 | 413 文案改为"超过解析服务上限"；非法 JSON → SERVICE_ERROR |
| 瘦身后掉档仅写意图，无对应测试 | P1 | T2/T3 增加"原始大小不降档"RED 测试 |
| 未知大小静默回落 60s | P1 | 未知 → 大文件档下限 base×5 |
| 分块 200 与 100 页/100MB 内存守卫冲突 | P1 | 默认改 100，守卫按新分块实测重标 |
| 适配器 296 行，净增必破 300 硬限 | P1 | 拆分：负载解析外移 `SidecarPayloadParser`，目标 ≤200 行 |
| EMPTY_TEXT 判据（warnings vs 文本）矛盾 | P2 | 判据定为「markdown 空且 pages 全空」，warnings 仅补充 |
| FR-008 与多册 N×1800s 冲突 | P2 | 后台执行态兜底，产品接受；已记录为残余限制 |

## Open Questions

无（规格 0 个 NEEDS CLARIFICATION；实现细节在 S4 由测试锁定）。
