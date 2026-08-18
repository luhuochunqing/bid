# Implementation Plan: 根治评分项权重合计缺口

**Branch**: `agent/trae/047-fix-score-weight-gap` | **Date**: 2026-08-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/047-fix-score-weight-gap/spec.md`

## Summary

评分项权重合计 ≠ 100 的场景，当前 `WeightSumCheck` 仅透出 `weightWarning` 不修正，导致落库 items 权重合计为 90/200 等非 100 值。前端 `useScoreParseDrawer.js:37` 的 `totalWeight` 是 items 实时 reduce 求和，`ScoreParseTable.vue:58` 在 `totalWeight !== 100` 时展示"权重合计与 100 分不符"。

本 feature 在**超载剔除之后、落库之前**插入纯核心 `WeightNormalizationPolicy`：对合计超出容差（±0.5，排除 0）的候选集按"按比例缩放 + 最大余数法确定性分配"归一至合计恰=100，并持久化原始合计信号（`ScoreParseTask.originalTotalWeight`）供 `summary` 透出。使**落库 items 权重合计恒=100**，前端 weightWarning 告警自然消失，且保留"原合计 X 已补齐"可见性。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2（后端）｜ Vue 3 + Vite 5（前端仅验证信号，不涉及展示改动，见 FR-007）

**Primary Dependencies**: Jackson（DTO 序列化）、JPA/Hibernate（ScoreParseTask 持久化）、Flyway（迁移）

**Storage**: MySQL 8.0。`ScoreParseTask` 新增 `original_total_weight DECIMAL`（可空）承载原始合计信号，类比 `skipped_chunks` 既有先例（V1193）。

**Testing**: JUnit 5 纯单测（`WeightNormalizationPolicyTest`，纯核心无框架）；可选 `ScoreParseAppServiceTest` 集成编排校验。pre-commit 全部门禁。

**Target Platform**: Linux 服务器（后端）+ 浏览器（前端）

**Project Type**: Web 应用（后端 Spring Boot / 前端 Vue）——本 feature 仅后端改动

**Performance Goals**: 归一化 O(n) 级，单次解析候选≤百项，无可感知开销

**Constraints**:
- 纯核心（`scoreparse/domain`）不得依赖 Spring/框架（Constitution Principle I）
- 单文件软上限 200 行、硬上限 300 行（Constitution Principle IV）
- 真实 API 唯一源，禁止 Mock（Principle II）
- 新迁移脚本 MUST 走 `scripts/new-migration.sh` 取号（CLAUDE.md §数据库迁移规范）

**Scale/Scope**: 单次评分解析任务级（候选集通常 ≤100 项），无并发热点

## 架构定位与整改映射

### 当前流程（ScoreParseAppService.parse，170-210 行）
```
valid(候选) → WeightSumCheck.checkCandidates → gap recheck → WeightOverloadTrimPolicy → ItemCountCheck → persistItems
                              ↑                        ↑
                       weightResult(仅标记,不改权重)   weightResult 重算
```

### 目标流程（本 feature 插入点）
```
valid → checkCandidates → recheck → trim → WeightNormalizationPolicy.normalize(valid)
                                                       │
                                                       ├─ total∈(0, 100±0.5 外) → 归一化，返回 (normalizedList, originalTotal)
                                                       ├─ total∈[99.5,100.5]    → 原样，originalTotal=null
                                                       └─ total=0              → 原样，originalTotal=null（保留 weightWarning 兜底）
                                                        │
                                          persistItems(normalizedList)
                                          ScoreParseTask.originalTotalWeight = originalTotal（非null时）
```

### FR-005 信号透出链路
- 落库信号：`ScoreParseTask.originalTotalWeight`（新增列，normalization 实际发生时写入）
- 聚合透出：`getItems` 组装 DTO 时，从 latest PARSE task 读到 `originalTotalWeight`，写入
  `ScoreParseItemsDTO.Summary.originalTotalWeight`（新增字段，null 表示未归一化）
- 前端：不为本 feature 改展示逻辑（FR-007）。`summary.originalTotalWeight!=null` 时前端**可**择机呈现
  "原合计 X 已补齐"，由产品后续确认图文案，不阻塞后端交付。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 合规要求 | 本 plan 满足 |
|------|----------|--------------|
| I. FP-Java | 纯核心不依赖框架，record/final 不可变 | ✅ `WeightNormalizationPolicy` history 无 Spring 依赖，输入 output record |
| II. Real-API Only | 禁止 Mock | ✅ 走真实解析链路，单测纯核心正常量 |
| IV. Split-First | 单文件 ≤300 行，新代码默认 <100 行 | ✅ 新类独立，逻辑约 80-120 行 |
| VII. Defensive Collection | toMap 必须带 merge function | ✅ 归一化不使用 toMap；若涉及 map 装配用 3 参数版 |
| DB Migrations | Flyway 取号，V+U 成对 | ✅ 走 `new-migration.sh`，回滚脚本配套 |

*GATE 判定：无违反 → 通过，进入 Phase 0/1。*

## Project Structure

### Documentation (this feature)

```text
specs/047-fix-score-weight-gap/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
├── contracts/
│   └── score-parse-api.md  # Phase 1 输出（Summary 契约增量）
└── tasks.md             # Phase 2 输出（/speckit-tasks）
```

### Source Code (repository root)

```text
backend/
└── src/
    ├── main/java/com/xiyu/bid/scoreparse/
    │   ├── domain/
    │   │   ├── WeightNormalizationPolicy.java   # [新增] 纯核心归一化
    │   │   └── ScoreCandidate.java              # [不变] weight 字段由归一化改写，其余字段不变
    │   ├── application/
    │   │   └── ScoreParseAppService.java        # [改] 177/196 行间插入 normalize；落库 originalTotalWeight
    │   ├── entity/
    │   │   └── ScoreParseTask.java              # [改] 新增 originalTotalWeight 字段
    │   └── dto/
    │       └── ScoreParseItemsDTO.java          # [改] Summary 新增 originalTotalWeight
    ├── main/resources/db/migration-mysql/
    │   └── V<next>__add_original_total_weight.sql  # [新增] Flyway 迁移
    └── main/resources/db/rollback/migration-mysql/
        └── V<next>__add_original_total_weight.sql  # [新增] 回滚
    └── test/java/com/xiyu/bid/scoreparse/domain/
        └── WeightNormalizationPolicyTest.java   # [新增] 纯单测 ≥10 用例
```

**Structure Decision**: 复用既有 `scoreparse` 包域。归一化作为 domain 纯核心策略类新增，编排在
`ScoreParseAppService.parse`（imperative shell 只编排）。信号持久化遵循 `skipped_chunks` 既有模式
（task 列 + Meta/Summary 透出），不新增独立告警机制。

## Complexity Tracking

无 Constitution 违反，无需记录。

## 关键设计决策（归一化算法）

**目标**：把候选集权重从 `total`（≠100，且 0<total）按比例缩放，使等差和为 100，再用**最大余数法**
确定性分配四舍五入余数，保证落库和恰=100 且可复现。

1. `scale = 100 / total`（total>0 才执行）
2. 对每项 `weight * scale`，保留若干位小数（如 2 位）作为理想值
3. **最大余数法（largest remainder method）**：取每项 floor 值求和得 `floorSum`，剩余
   `100 - floorSum` 分配给余数最大（相同则按 index 升序，保证确定性）的若干项各 +1
4. 输出带 `originalTotal` 的 record；`total=0` 或 `total∈[99.5,100.5]` 时输出原样 + `originalTotal=null`

*完整性说明*：超载（>100）场景 spec 046 `WeightOverloadTrimPolicy` 已在归一化前把 total 修剪回
≤100.5；归一化只负责把 (0,100.5] 区间收敛到恰=100。total=0 保持既有 weightWarning 兜底语义不归一。