# Data Model: 评分证据完整性

## 既有实体

### ScoreItem

- `detail`: 评分条款原文。
- `weight`: 权重。
- `estScore`: 预计分；证据不完整时为 null。
- `statusStage1`: 证据不完整时为 PENDING。
- `estBasis`: 面向用户的中文依据。
- `kbHit`: 本次语义收紧为“已有足够资料支持自动预判”；仅候选命中但证据不完整时为 false。

### PerformanceRecord

可用证据包括合同名称、项目类型、行业、客户类型、签约日期、合同金额、联系人和属地。现有模型不包含付款流水、发票、盖章文件或附件真实性结论。

## 新增纯核心值对象

### ProjectEvidenceAssessment

- `manualChecks`: 不可由现有结构化资料证明的强制条件中文列表。
- `requiresManualReview`: `manualChecks` 非空时为 true。

该对象不持久化，不引入表结构或状态迁移。

## 状态规则

1. 匹配约束不足 → PENDING / null。
2. 有候选且存在人工核验条件 → PENDING / null。
3. 无人工核验条件 → 沿用匹配比例计分与状态策略。
