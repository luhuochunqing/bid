-- Input: V1079__add_evaluation_source_to_tenders.sql
-- CO-232: 评估表数据来源标记
-- Rollback: remove evaluation_source column
ALTER TABLE tenders DROP COLUMN evaluation_source;
