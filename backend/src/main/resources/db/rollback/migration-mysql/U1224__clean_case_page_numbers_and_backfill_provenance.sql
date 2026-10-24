-- Input: migration-mysql/V1224__clean_case_page_numbers_and_backfill_provenance.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1224: 回滚 clean_case_page_numbers_and_backfill_provenance
UPDATE knowledge_case
SET source_section_title = '7. 响应性评审标准承诺函\t2003',
    source_section_path = '一、 投标函\t1 > 九、 本项目相关其他内容\t1806 > （一） 近年完成的项目情况表\t1806 > 1. 中国海洋石油集团有限公司\t1808 > 2. 国家电网有限公司\t1820 > 7. 响应性评审标准承诺函\t2003'
WHERE id = 1254;

UPDATE knowledge_case
SET source_section_title = '4.2. 关键流程节点说明',
    source_section_path = '一、 投标函\t1 > 九、 本项目相关其他内容\t1806 > 4.2. 关键流程节点说明'
WHERE id = 1255;

UPDATE knowledge_case
SET source_section_title = NULL,
    source_section_path = NULL,
    source_document_name = NULL
WHERE id IN (1256, 1257, 1258, 1259, 1260, 1261);
