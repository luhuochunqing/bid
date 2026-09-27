# Data Model: PDF评分条目来源关系

本修复复用DocumentArtifact证据，新增版本化提取快照与生效关系；该文档描述批准契约，具体实体/API映射实施后补齐。

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

## ExtractedScoreItem / snapshot
- 稳定itemId、源factId、schemeId、file identity、名称与类别分别保存。
- rawMaximum、schemeRatio、effectiveMaximum可空；未知不写0。
- 完整规则、材料要求、来源span、父子/聚合、验证diagnostics与computability分别保存。
- 快照属于parseTaskId，保存全体候选；历史记录缺payload时保守读取旧列，不能伪造已验证。

## Active version
- 项目当前生效task/scheme/item subset与规则指纹独立于latest task。
- 事务项目锁、expectedActiveFingerprint CAS；空/失败/过期任务不能替换。
- 人工编辑改变指纹，阻止自动覆盖；评分结果绑定该指纹。

## Compatibility
增量迁移facts.item_payload、可空权重与items.source_fact_id；如需版本行允许增量增加。保留旧API与历史列，不回填伪造来源。新特性开关默认关闭；开启后首份单方案可计算结果可用，重解析不完整只保存候选。关闭不删除快照或活动结果。

## Task1 integration interface
SourceSpan(documentId,fileSha256,fileType,schemeId,pageNumber,tableId,sourceId,startOffset,endOffset,rawText,locator).
ScoreSourceMetadata(modelRuleText,modelQuotedText,sourceSpans,diagnostics) carried by ScoreFact and MultimodalScoreFact with backward-compatible constructors. Mapper sourceText is original source; modelRuleText remains separate. Missing provenance stays empty/unknown.

ModelSourceClaim(sectionTitle,pageNumber,tableId,sourceCellIds,schemeId,scopeStatus) is explicitly MODEL_CLAIM, never original verification. Metadata also retains modelCategory/modelScoreType. A source-unverified item may be computable under approved semantics; verified OUT_OF_SCHEME remains excluded from target activation.
