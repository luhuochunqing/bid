# 数据模型

Candidate record：id, sourceProjectId, responseText, scoringPointTitle, sourceSectionTitle, sourceSectionPath, sourceSectionIndex, sourceDocumentName, sourcePageNumber。
Update record：id, sourceSectionTitle, sourceSectionPath, sourceSectionIndex, sourceDocumentName, sourcePageNumber。
所有字段保持原可空语义；更新五来源字段全部null表示清空；无数据库结构变化。
