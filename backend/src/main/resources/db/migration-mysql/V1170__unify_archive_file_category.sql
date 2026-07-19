-- CO-592: 项目档案文档分类统一为 6 个中文选项
-- 将 archive_file 表中已废弃的历史分类值（CONTRACT/PROCESS/RETROSPECTIVE）归一化为 OTHER
-- 废弃分类说明：
--   CONTRACT（合同文件）、PROCESS（过程文件）、RETROSPECTIVE（复盘文件）
--   这 3 个分类在 CO-420 后已不再写入（DocumentCategoryNormalizer 统一归一化到 6 个标准枚举）
-- 本次迁移清理存量历史数据，使档案台账展示与 6 个标准中文选项一致
UPDATE archive_file
SET document_category = 'OTHER'
WHERE document_category IN ('CONTRACT', 'PROCESS', 'RETROSPECTIVE');
