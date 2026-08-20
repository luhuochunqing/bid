# Feature Specification: AI 自动生成案例超大标书流式化与 LLM 摘录化

**Feature Branch**: `agent/zcode/case-precipitation-streaming`

**Created**: 2026-08-20

**Status**: Draft

**Input**: 交接文档 `docs/implementation-notes/case-precipitation-oversize-handoff.md` + 用户需求："AI 自动生成案例项目 1350 入库 0 条——目标是成功生成案例并入库，而不是把超 30MB 的标书全部拒掉。"

## 背景与问题陈述

- 测试环境项目 **1350（"ai测试01"）** 点击【AI自动生成案例】期望**成功生成并入库案例**，当前结果是「入库 0 条」或「失败通知」。
- 真实业务矛盾：全部真实标书 >30MB（几百 MB ~ 2G，项目 1350 实测 **401MB docx**）。任何设字节上限拦截的方案都会拒绝全部真实数据。
- 历史止血（PR !2357）：`MarkItDownSidecarExtractor` 入口加 30MB 上限抛 `DocInsightException`，`ProjectClosedEventListener` 走失败通知。止住了 OOM，但真实文件全部被拒——这正是用户不满意处。
- 真实数据实测（2026-08-20，测试环境）：
  - OBS 预签名 URL 流式下载 401MB docx：**3.2s**，全程流式。
  - sidecar `/convert-stream` 转换 401MB docx：**33s**，sidecar 容器峰值内存 **927MB**（主机 7.4G 可承受，无 OOM），输出 markdown **105.6 万字符、14124 行**（有效标书正文，8275 个非空行）。
  - sidecar `SIDECAR_MAX_UPLOAD_MB=3072`，容器无内存限制，`RestartPolicy=always`。
  - 401MB docx 的 `word/document.xml` 仅 13MB（其余 1941 个文件几乎全是图片）——markitdown 文本转换内存需求小，**超大 docx 的转换不是瓶颈**。
- 根因（承接 lessons-learned §132 + handoff）：案例沉淀链路 `ProjectClosedEventListener.loadBidFileMarkdown` 用 `RestTemplate.getForObject(byte[].class)` 整文件读 JVM 堆；且 `CaseAiMatcher.extractSlicesWithAi` 把**整份提取后文本拼进单个 LLM prompt**。
- 本规格复用 spec 045 已验证的流式模式，但**不能简单照搬**：打分链路是"全文 O(n) 扫描 + 每评分项 12K 字符摘录喂 LLM"，案例沉淀当前是"全文拼单 prompt"——后者是独立于 OOM 的第二堵墙（模型上下文窗口装不下 105 万字符）。

## Clarifications

### Session 2026-08-20（立项时已收敛的关键决策）

- Q: 案例沉淀读取侧如何解 OOM？ → A: **复用 spec 045 已验证的流式加载**：`StreamingBidFileFetcher`（≤50MB 内存快路径 / 超限 spill 落盘，512MB 硬上限）+ `SidecarStreamTextExtractor`（/convert-stream 磁盘临时文件流式上传 + 500 万字符文本量上限）+ `TempBidFileStore`（临时文件生命周期与磁盘配额）。OBS 签名 URL 由现有 `ObsShareUrlSigner.trySign` 提供。与打分链路共用同一批组件，避免从零实现。依据 Constitution VIII（复用已验证模式）；045 已实测 1.62GB 文件走该链路无 OOM。
- Q: `MarkItDownSidecarExtractor` 的 30MB `DocInsightException` 上限怎么办？ → A: 案例沉淀链路**不再走** `MarkItDownSidecarExtractor`（它属于 docinsight 模块，服务文档洞察其他场景）。案例沉淀改用流式加载组件后，字节上限自然解除；`DocInsightException` 保持为 docinsight 模块的既有防护，不回退、不扩大影响面。
- Q: LLM 上下文超限（第二堵墙）如何解？ → A: **摘录化改造**：复用打分链路的 `ScoreDocExcerptExtractor.extractRelevantExcerpt`（评分项关键词段落打分，每项 ≤12K 字符摘录），`CaseAiMatcher` 改为**逐评分项调用 LLM**，输入为摘录而非全文。105 万字符全文仅做 O(n) 扫描选段。
- Q: 提取后文本超过 500 万字符怎么办？ → A: 复用 `SidecarStreamTextExtractor` 的 `max-text-chars`（500 万）上限，超限抛 `OversizedBidTextException` → 走失败通知（明确拒绝，不截断假成功）。105 万字符实测远低于上限。
- Q: 超大文件（>512MB 或 2G 级）怎么处理？ → A: **不在本规格范围**。markitdown 对 >512MB 输入有 OOM-kill sidecar 风险（代码注释记录 1.62GB 实测）。400MB 验收目标本规格覆盖；2G 级需按文件格式分片流式转换（Phase 3，另立 spec）。本规格须在失败通知中明确提示"文件过大需压缩/拆分"，避免用户误判为链路故障。
- Q: `@Async + @Transactional` 长事务问题？ → A: 案例沉淀事件是异步监听，重活（下载+转换+逐项 LLM，可能数分钟）全程持有 DB 连接。本规格将**重活移出事务边界**：事务只包裹 case 落库，下载/转换/LLM 调用不持连接。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 超大标书能成功生成案例并入库 (Priority: P0)

测试环境项目 1350 点击【AI自动生成案例】，标书为 401MB docx。系统流式下载标书、流式转换为文本、按评分项摘录后逐项调用 LLM，最终**成功生成并入库案例**，用户收到成功通知（入库 >0），无需压缩、无需重新上传。

**Why this priority**: 这是本规格存在的理由——现方案把所有真实标书（100% >30MB）挡在案例沉淀之外。

**Independent Test**: 用项目 1350 触发【AI自动生成案例】，确认成功通知、`knowledge_case` 表新增记录 >0。

**Acceptance Scenarios**:

1. **Given** 项目 1350 有 401MB docx 标书且已解析评分项，**When** 用户点击【AI自动生成案例】，**Then** 系统流式下载（不整包入堆）→ 流式转换 → 逐项 LLM 摘录匹配 → 案例成功入库，收到成功通知，`knowledge_case` 新增 >0 条。
2. **Given** 任意大小（≤512MB）的真实标书，**When** 触发案例沉淀，**Then** 全链路无 `byte[]` 整包持有原始文件内容，无 `OutOfMemoryError`。

---

### User Story 2 - 服务内存安全（不 OOM） (Priority: P0)

大文件下载与文本提取不得以牺牲服务可用性为代价：任意大小文件不得触发 JVM OOM；临时文件有配额、有清理，不得写满磁盘。

**Why this priority**: 修复 OOM 的初衷不能被新方案重新引入（磁盘版 OOM / 文本版 OOM）。

**Independent Test**: 触发项目 1350 案例沉淀，监控后端 JVM 堆峰值（无 GB 级尖峰）与临时目录占用（任务结束被清理）。

**Acceptance Scenarios**:

1. **Given** 任务开始下载 401MB 标书，**When** 下载进行中，**Then** JVM 堆内存占用与文件大小无关（流式 spill 落盘）。
2. **Given** 案例沉淀任务完成或失败，**When** 任务进入终态，**Then** 对应临时文件被 `TempBidFileStore.release` 删除（含异常路径）。
3. **Given** 提取后文本量超 500 万字符上限，**When** 触发案例沉淀，**Then** 明确失败通知（含上限与建议），不产生半截案例。

---

### User Story 3 - LLM 调用不超上下文（摘录化） (Priority: P1)

案例匹配的 LLM 输入从"整份 105 万字符全文"改为"每评分项 ≤12K 字符相关摘录"，确保任何真实标书都能成功完成 LLM 匹配。

**Why this priority**: 即使 OOM 解决，全文拼单 prompt 仍会让 LLM 调用因上下文超限失败或静默产出低质量结果（第二堵墙）。摘录化是"成功生成案例"的充分条件。

**Independent Test**: 对项目 1350 的评分项列表，确认 `CaseAiMatcher` 每次 LLM 调用输入为按项摘录（≤12K 字符），而非全文。

**Acceptance Scenarios**:

1. **Given** 提取出 105 万字符 markdown 与 N 个评分项，**When** 执行案例匹配，**Then** 发起 N 次 LLM 调用（每次输入该评分项相关摘录），全部成功返回合法 JSON。
2. **Given** 某评分项在全文无关键词命中，**When** 执行摘录，**Then** 用兜底段落（如全文开头/结尾）或该评分项明确标记为"无相关证据"，不崩溃、不伪造证明片段。

---

### User Story 4 - 失败语义清晰（不假成功） (Priority: P2)

延续现有纪律：OBS 读取失败、转换失败、LLM 调用失败均走**失败通知**，禁止回退占位文本假"成功但入库 0 条"。

**Why this priority**: 这是 case 沉淀 OOM 根因修复确立的纪律（lessons-learned §132），本规格延续。

**Independent Test**: 模拟 OBS 读取失败，确认收到失败通知且无假成功。

**Acceptance Scenarios**:

1. **Given** OBS 标书读取失败，**When** 触发案例沉淀，**Then** 收到失败通知，提示真实原因，`knowledge_case` 无新增。
2. **Given** 文件 >512MB 或文本量超限，**When** 触发案例沉淀，**Then** 收到失败通知，明确提示"文件过大需压缩/拆分"或"文本量超限"，不静默。

---

### Edge Cases

- 标书文件为空或提取文本为空：走失败通知（"未能提取出有效正文"）。
- 项目缺少标书或评分项：维持现有前置条件硬校验（跳过 + 失败通知）。
- 并发触发同一项目（重复事件）：维持现有幂等（DataIntegrityViolationException → 幂等成功）。
- LLM 返回非 JSON 数组：维持现有解析降级（解析到 0 片段），不崩溃。
- 临时目录磁盘配额超限：`BidFileQuotaException` → 失败通知（配额提示）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 案例沉淀读取 OBS 标书 MUST 使用流式下载（≤50MB 内存快路径 / 超限 spill 落盘），不得整文件读 JVM 堆。
- **FR-002**: 落盘大文件 MUST 通过 sidecar `/convert-stream` 流式转换（磁盘临时文件上传 + 响应流式读），MUST 复用 `SidecarStreamTextExtractor`（含 500 万字符文本量上限与 600s 超时）。
- **FR-003**: 临时文件生命周期 MUST 由 `TempBidFileStore` 管理（`{taskId}.part` 命名、用毕即释、异常路径清理、磁盘配额）。
- **FR-004**: `CaseAiMatcher` MUST 改为按评分项摘录（≤12K 字符/项）逐项调用 LLM，不得将整份提取文本拼入单个 prompt。
- **FR-005**: 摘录逻辑 MUST 复用 `ScoreDocExcerptExtractor`（关键词段落打分 + top 段落 + 兜底），评分项无关键词命中时 MUST 用兜底段落且不伪造证明片段。
- **FR-006**: 案例沉淀重活（下载/转换/LLM 调用）MUST 移出事务边界；事务仅包裹 `knowledge_case` 落库与幂等校验。
- **FR-007**: OBS 读取失败、转换失败、LLM 调用失败 MUST 走失败通知（真实原因），禁止回退占位文本假成功。
- **FR-008**: 文件 >512MB 或提取文本量 >500 万字符 MUST 明确失败通知（提示压缩/拆分或文本量超限），不静默、不截断。
- **FR-009**: 现有语义保持：缺少标书/评分项 → 前置校验失败通知；重复事件 → 幂等成功；LLM 解析到 0 片段 → 成功但入库 0（诚实降级，随成功通知说明）。

### Key Entities

- **LoadedBidFile**（复用 scoreparse 已有）：二态值对象（InMemory / OnDisk），承载流式加载结果。
- **KnowledgeCase**（已有，无结构变更）：案例沉淀目标实体，`scoring_point_title` 唯一性做幂等。
- **ExtractedDocument**（已有）：转换结果，本规格改为流式链路产出。
- 本规格**不新增数据库表**，无 Flyway 迁移。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 测试环境项目 1350 触发【AI自动生成案例】→ 收到成功通知，`knowledge_case` 新增 **>0** 条（验收核心指标）。
- **SC-002**: 全链路后端 JVM 无 `OutOfMemoryError`，堆峰值无 GB 级尖峰（与 401MB 文件大小解耦）。
- **SC-003**: 401MB docx 从触发到案例落库的端到端耗时可接受（下载 3.2s + 转换 33s + LLM 逐项调用，目标 < 10 分钟）。
- **SC-004**: 真实 >30MB 标书不再被字节上限拦截（无 "文档文件超过转换上限 XMB" 类失败通知）。
- **SC-005**: 失败语义可区分：OBS 失败 / 转换失败 / LLM 失败 / 超限拒绝，用户能根据通知定位原因。

## Assumptions

- sidecar 与后端已部署且 `/convert-stream` 可用（测试环境已实测）。
- markitdown 对 docx 文本转换内存可控（实测 401MB docx → 927MB 容器峰值）。
- 401MB 级标书是本规格验收范围；>512MB（markitdown OOM 风险）与 2G 级文件由 Phase 3 另立 spec 覆盖。
- 评分解析链路（spec 045）的流式组件 API 形态稳定，案例沉淀直接注入复用，不改其签名。
- 评分项数量规模（单项目几十项）在逐项 LLM 调用的成本与耗时可接受范围（与打分链路同口径）。
