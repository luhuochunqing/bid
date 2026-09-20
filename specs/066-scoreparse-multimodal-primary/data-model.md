# Data Model

## MultimodalScoreFact

`sectionTitle`, `code`, `parentCode`, `dimension`, `weight`, `ruleText`, `scoreType`, `pageNumber`, `tableId`, `sourceCellIds`, `quotedText`, `confidence`。

不把父项重复计入叶子总分。`quotedText` 必须是指定原始单元格的逐字子串。

## ScoreFactValidationReport

- `decision`: `AUTO_APPROVED | REVIEW_REQUIRED`
- `declaredTotal`, `leafTotal`
- `facts`: 全量候选（包括错误项）
- `violations`: `factId/field/code/message/expected/actual/evidence`

强校验：局部序号连续、总分闭合、父子分值一致、来源存在且逐字引用、重复项。

## KnowledgeRecordProof

- `recordType`, `recordId`, `recordName`
- 可审计字段：主体/客户、日期、金额、类别、有效期等
- `conditions[]`: `conditionId`, `description`, `status(PASS|FAIL|MISSING_EVIDENCE)`, `evidence`
- `eligible`: 所有必选条件 PASS 才为 true

