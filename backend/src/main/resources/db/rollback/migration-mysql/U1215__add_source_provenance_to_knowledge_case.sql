-- Input: migration-mysql/V1215__add_source_provenance_to_knowledge_case.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1215: 回滚 add_source_provenance_to_knowledge_case
ALTER TABLE knowledge_case
    DROP COLUMN source_section_index,
    DROP COLUMN source_page_number,
    DROP COLUMN source_document_name,
    DROP COLUMN source_section_path,
    DROP COLUMN source_section_title;
