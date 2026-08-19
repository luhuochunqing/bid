# Feature Specification: 投标文件多模态图片评分（方案A）

**Feature Branch**: `agent/trae/score-parse-image-multimodal`

**Created**: 2026-08-18

**Status**: Draft

**Input**: User description: "投标文件中的图片（证书、图表、截图等）是评分的核心证据，但当前评分解析链路只把文档转成纯文本喂给 AI，图片内容完全丢失。模型已切换到多模态 Gemini-3.5-Flash。方案A：sidecar 抽图 + transport 支持图片 content + prompt 加图，让 AI 能看图对标打分。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 客观项标书截图/证书/图表能看图打分 (Priority: P1)

用户在项目详情页上传投标文件并点击「AI 实际打分」。当评分项需要核验的是**图片内容**（如资质证书、业绩证明扫描件、技术方案中的图表/架构图），系统在 AI 对标时应把该评分项相关的投标文件图片一并给到多模态模型，使对标结果不再因"图片内容在纯文本里不存在"而误判为不满足。

**Why this priority**: 这是方案A的根因。当前阶段2打分只喂 `bidDocExcerpt` 文本（[ScoreDocExcerptExtractor.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreDocExcerptExtractor.java)），图片内容在转换时丢失一整类证据。多模态模型已具备看图能力，喂图是直接命中用户价值的能力启用，优先于一切优化。

**Independent Test**: 对一个"须提供 xx 证书扫描件"的客观评分项，投标文件正文只有占位文字、实际信息在图内，AI 打分前能读到对应图片并据此给出"满足"结论及引用依据。

**Acceptance Scenarios**:

1. **Given** 一个客观评分项要求核验图片类证据（如资质证书），投标文件对应区域含相关图片，**When** 用户点击「AI 实际打分」完成对标，**Then** 该评分项可正确判定"满足"，评分依据引用或描述了图片内容，不再因图片内容缺失而误判"不满足"。
2. **Given** 投标文件某评分项对应区域无图片、仅文本，**When** 打分执行，**Then** 走现有纯文本路径，行为与现状一致（无回归）。
3. **Given** 评分执行期间图片加载/解码失败（如图片损坏），**When** 打分，**Then** 该评分项降级为纯文本对标，不阻塞整体打分任务，并记录可观测的失败信号。

---

### User Story 2 - 图片按评分项正确关联配给 (Priority: P1)

系统必须把投标文件的图片**按评分项正确关联**并配给对应打分请求，而不是把整份文件的所有图全部塞给每个评分项。关联依据复用现有"评分项 ↔ 章节/文档区域"的映射（[ScoreItemChapterMatch.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemChapterMatch.java)），同时受 token/成本约束。

**Why this priority**: 图片 token 开销远高于文本，若每个评分项都接收全量图，既浪费 token 又可能把无关图当作证据导致误判。正确关联是"喂图有价值且可控"的前提，与 P1 并列。

**Independent Test**: 对含多章节、多张图的投标文件，某评分项的请求中只携带与其章节相关的图片子集，其它评分项的图不会混入。

**Acceptance Scenarios**:

1. **Given** 投标文件含 N 张图且分散在若干章节，**When** 对某章节的评分项打分，**Then** 该请求只附带与该章节匹配的图片（必要时含相邻区域），不会附带全部 N 张图。
2. **Given** 某评分项在文档内无图片命中，**When** 打分，**Then** 请求不含任何图，走纯文本。
3. **Given** 一个评分项命中超过单次成本上限的图片数，**When** 打分，**Then** 系统按确定性规则裁剪到成本预算内（宁可降级少图，也不超支吞掉任务），并保留裁剪可见信号。

---

### User Story 3 - 多模态带来的 token 成本可观测可控 (Priority: P2)

引入图片后 token 消费显著上升，系统必须延续现有花费守卫能力，让运维/管理员能感知每次打分因"喂图"产生的额外成本，并存在兜底开关在异常高涨时关闭图片能力，回退到纯文本打分。

**Why this priority**: 已有多模态/sidecar 之外，图片 token 是新增且可能最大的成本项。无守卫地接入会造成不可控开销；可关闭能力是底线兜底。

**Independent Test**: 在一次含图打分中，响应记录能反映该次调用附带了几张图、图片部分的 token 占比；关闭开关后打分完全不携带图片。

**Acceptance Scenarios**:

1. **Given** 某次打分携带了图片，**When** 完成，**Then** 调用记录可观测到携带的图片数与图片相关 token 占比。
2. **Given** 运维关闭图片能力开关，**When** 再次打分，**Then** 系统回到纯文本打分，不携带任何图片。
3. **Given** 单次请求图片 token 估算逼近配置上限，**When** 打分，**Then** 触发裁剪逻辑（见 US2 场景 3），避免请求被网关拒绝或超预算。

---

### Edge Cases

- **图片损坏 / 解码失败**：该评分项降级纯文本，不阻断整体任务，记录失败信号（US1 场景 3）。
- **文档无任何图片**：全链路与现状一致，零额外 token，行为无回归。
- **图片==关键证据但正文无文字**：必须能看图判定满足（US1 核心）。
- **图片数量超过单次成本上限**：确定性裁剪（如按章节评分项匹配分排序取前 K 张），并有可见信号。
- **图片存在但模型不支持**：调用失败时回退纯文本，不静默吞掉任务。
- **多章节多图正确隔离**：某评分项只收到其对应章节图，不发生图错配。
- **安全**：图片内容为投标文件受控数据，传输与存储遵循既有文档链路的安全契约；不外泄到无关请求。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在投标文件转换阶段抽取并保留文档图片（含其在章节/区域中的位置归属），使图片内容在评分链路中可被访问，替代当前"转纯文本即丢弃图片"的处理。
- **FR-002**: 评分调用（objective 对标）MUST 能向多模态模型发送混合内容：一条请求可同时含文本 prompt 与一条或多条图片（图像内容）。
- **FR-003**: 系统 MUST 在分发图片给某评分项时，按其 `章节/区域` 关联进行配给，避免把无关图片当作该评分项证据；无命中时该评分项走纯文本。
- **FR-004**: 图片按评分项配给时 MUST 受单次成本预算约束，超预算时按确定性规则裁剪（保留高相关图、丢弃低相关图），并保留裁剪可见信号，禁止无界携带导致任务超支。
- **FR-005**: 当评分项无图片、或图片加载/解码失败、或模型不支持时，该评分项 MUST 优雅降级为纯文本对标，不阻断整体打分任务（呼应 constitution Principle VII 优雅降级）。
- **FR-006**: 系统 MUST 提供可观测的图片调用记录（每条含图片携带数与图片相关 token 占比），并在异常趋势下（可选）提供关闭图片能力的兜底开关，回退纯文本打分。
- **FR-007**: 图片抽取与多模态调用的核心规则 MUST 以纯核心（Pure Core / FP-Java）实现、不依赖框架可独立单测；命令式外壳只做编排（呼应 constitution Principle I）。
- **FR-008**: 图片传输与暂存 MUST 遵循投标文件既有安全/size 边界契约，不把图片内容泄漏到与该评分项无关的请求或响应中。

### Key Entities

- **文档图片 (DocumentImage)**: 从投标文件中抽取的图片，含图像内容（base64/引用）、所在章节/页码/区域归属、尺寸与顺序。是评分证据与图片配给的单元。
- **图片配给决策 (ImageAssignment)**: 某个评分项实际携带的图片子集 + 裁剪判断。为纯核心值对象，携带"是否裁剪/降级"信号。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 对一个含整页图片类证据（如证书扫描页）的客观评分项，AI 打分能正确判定"满足"并可引用图片内容；而纯文本基线在该场景必然漏判。以真实投标文件样本验证通过。
- **SC-002**: 图片按评分项正确关联——多章节多图文档中，任一只携带其章节子集，不发生跨章节错配（单测 + 真实样本校验）。
- **SC-003**: 无图片/图片损坏/超预算三种降级路径均不阻塞任务，且各自留下可观测信号（纯单测覆盖 ≥ 3 用例）。
- **SC-004**: 含图打分产生可观测的"携带图片数 + 图片 token 占比"；关闭开关后打分完全回到纯文本、零图片。
- **SC-005**: 图片配给逻辑被纯单测覆盖，覆盖"无图/单图/超预算裁剪/损坏降级/多章节隔离"分支，全部通过。

## Assumptions

- 方案A范围明确为三处：sidecar 抽图 → transport 支持图片 content → 调用层按评分项配图入 prompt（本 feature 的饿换骨架）。
- 多模态模型为已配置的 Gemini-3.5-Flash（`custom` provider 承载），具备读图能力；若实际网关为不支持图像的模型，按 FR-005/FR-008 优雅降级，不作为阻塞。
- **已知限制（2026-08-19）**：Gemini-3.5-Flash 仅在测试环境可用；本地 dev 栈 activeProvider=deepseek（纯文本，不支持图片）且无 `GEMINI_API_KEY`/DB key 配置。故 US1 的 SC-001（含图证据客观项 evidence 引用图片判定满足）需在测试环境配置 Gemini 后验证；本地已完成 sidecar 抽图链路（T011）与打分降级路径（T020 场景 2/3）验证。测试环境限定 winbid-01.test.rds.ehsy.com / 172.16.38.78（禁止碰生产）。
- 图片以 base64 内联进请求（OpenAI 兼容 `image_url` 格式）为默认传输方式；若 token/体积超限按 FR-004 裁剪。
- "评分项 ↔ 章节"复用既有 [ScoreItemChapterMatch.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemChapterMatch.java) 与打分编排已有的 excerpt 定位逻辑，不重造文档结构解析。
- 阶段 1（招标文件解析）不在本 feature 范围；本 feature 只针对阶段 2（投标文件评分）的客观项对标喂图。主观项沿用现状。
- 成本上限默认值、画像裁剪 K 值、todo 的兜底开关形态由 plan/实现阶段按现网指标确定，spec 阶段定义行为语义而非具体数值。
- 前端 UI 不做改造（不对用户暴露图/ token 细节），本 feature 为后端链路能力。