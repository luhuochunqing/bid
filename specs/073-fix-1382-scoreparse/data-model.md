# Data Model: PDF评分条目来源关系

本修复优先使用已存在DocumentArtifact字段，不增加数据库实体。

## SourceDocument
- `documentId` / artifact sha256: 单一源文件身份；混合批次内稳定且唯一。
- `fileType`: PDF、DOCX等格式，必须与documentId绑定，不能使用批次首文件类型代表全批。

## SourceSpan
- `documentId`, `sourceSection`, `tableIndex`, `rowIndex`, `cellIndex`。
- 原文片段及可用坐标/字符区间；不存在的坐标必须为空而不是伪造。

## ScoreItemEvidence (immutable domain value)
- `nameSpan`: 评分项名称的来源。
- `ruleSpans`: 一项或多段规则原文的来源集合。
- `categorySpan`: 上级商务/技术等分组来源，可为空。
- `maximumWeightSpan`: 本条最大分声明，可为空或歧义状态。
- `continuity`: 跨页关联只有在文档、方案、连续性及行结构均确定时为真。
- `bindingStatus`: UNIQUE / MISSING / AMBIGUOUS；只有UNIQUE可被确定性规范化使用。

该值对象是规划目标；实现前先确认现有模型能否无损表达。旧产物缺少documentId或证据span时不得推断其为PDF、不得自动提升资格。

## Compatibility
不持久化新字段，不迁移。若现有API DTO确需暴露候选任务来源，只复用既有task id/time字段；兼容测试覆盖空字段和旧任务。
