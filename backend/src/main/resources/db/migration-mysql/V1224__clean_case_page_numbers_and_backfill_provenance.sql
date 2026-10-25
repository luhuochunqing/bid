-- V1224: clean_case_page_numbers_and_backfill_provenance
-- 说明: 1. 清洗 knowledge_case 案例 1254/1255 来源路径内嵌页码与制表符
--       2. 回填 2033厦门ABB招投标项目（ID 1381，标书文件为张家口银行投标书）遗漏的 6 条案例（1256-1261）来源标题与路径

-- 1. 清洗 1254/1255 来源路径与标题内嵌页码
UPDATE knowledge_case
SET source_section_title = '7. 响应性评审标准承诺函',
    source_section_path = '一、 投标函 > 九、 本项目相关其他内容 > （一） 近年完成的项目情况表 > 1. 中国海洋石油集团有限公司 > 2. 国家电网有限公司 > 7. 响应性评审标准承诺函'
WHERE id = 1254;

UPDATE knowledge_case
SET source_section_title = '4.2. 关键流程节点说明',
    source_section_path = '一、 投标函 > 九、 本项目相关其他内容 > 4.2. 关键流程节点说明'
WHERE id = 1255;

-- 2. 回填 1256-1261 遗漏的来源标题、路径与来源文档名
UPDATE knowledge_case
SET source_section_title = '（一） 近年完成的项目情况表',
    source_section_path = '九、 本项目相关其他内容 > （一） 近年完成的项目情况表',
    source_document_name = '张家口银行——投标6.16.docx'
WHERE id = 1256;

UPDATE knowledge_case
SET source_section_title = '（一） 技术方案',
    source_section_path = '七、 服务方案 > （一） 技术方案',
    source_document_name = '张家口银行——投标6.16.docx'
WHERE id = 1257;

UPDATE knowledge_case
SET source_section_title = '（二） 供货方案',
    source_section_path = '七、 服务方案 > （二） 供货方案',
    source_document_name = '张家口银行——投标6.16.docx'
WHERE id = 1258;

UPDATE knowledge_case
SET source_section_title = '（三） 人员配备',
    source_section_path = '七、 服务方案 > （三） 人员配备',
    source_document_name = '张家口银行——投标6.16.docx'
WHERE id = 1259;

UPDATE knowledge_case
SET source_section_title = '1. 商品安全溯源',
    source_section_path = '七、 服务方案 > （四） 质量管理方案 > 1. 商品安全溯源',
    source_document_name = '张家口银行——投标6.16.docx'
WHERE id = 1260;

UPDATE knowledge_case
SET source_section_title = '1. 售前咨询',
    source_section_path = '七、 服务方案 > （五） 售后服务方案 > 1. 售前咨询',
    source_document_name = '张家口银行——投标6.16.docx'
WHERE id = 1261;
