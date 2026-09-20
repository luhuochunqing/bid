# Research: 评分依据可信度与中文可读性

## R1 数据源语义

**Decision**: 阶段 1 的业绩评分读取 `performance_record` 业绩库，不读取案例库；用户文案必须称“业绩库”。

**Rationale**: `ProjectMatchService` 依赖 `PerformanceRecordJpaRepository`。案例库搜索不到不能证明业绩库无记录，但现有“知识库命中”掩盖了真实来源。

**Alternatives considered**: 改为案例库匹配会改变产品数据源和计分语义，超出缺陷修复范围。

## R2 自动核验边界

**Decision**: 日期、合同金额、业务类型、客户信息和数量可由现有结构化字段核验；付款流水、发票、盖章客户名单及附件真实性必须人工核验。

**Rationale**: `performance_record` 没有付款流水、发票或附件真实性字段。缺字段时自动判 PASS 属于无证据结论。

**Alternatives considered**: 新增附件字段和识别链路需要迁移与历史补录，后续单独建设。

## R3 降级语义

**Decision**: 无法自动证明时返回待确认，而非零分或满分；候选数量仍可作为人工核验线索展示。

**Rationale**: “无法证明”既不等于满足，也不等于不满足。PENDING 保持信息诚实且避免误导决策。

**Alternatives considered**: 直接零分可能误伤真实合格业绩；继续给分则延续当前缺陷。

## R4 中文化边界

**Decision**: 用户界面展示业务含义，不显示内部枚举；后端新生成的依据也不含 tier、recordType 或 PASS 等代码。

**Rationale**: 内部代码不是用户证据，且用户明确要求全部中文、解释易懂。

**Alternatives considered**: 仅在前端正则清洗会掩盖后端契约问题，其他消费者仍收到不友好依据。
