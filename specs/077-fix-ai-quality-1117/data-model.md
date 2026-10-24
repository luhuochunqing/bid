# Data Model

本次不新增数据库表或迁移，仅约束既有领域对象语义。

- **QualityCheckItemResult**: PASS / FAIL / MANUAL_REVIEW；证据不足不能表达为确定结论。
- **QualificationDetail**: 一个详情对应一个 ISO 原子要求；不同标准不可复用同一命中语义。
- **Score Parse Version**: candidate 与 active 分离；candidate → validated → active，本任务不执行转换。
