-- V1215: knowledge_case 增加来源标题与章节 provenance 五列
-- 仅在结项沉淀或显式导入可证明时写入，不可证明时保持 NULL。
-- 不建索引/外键，不修改历史旧列。
ALTER TABLE knowledge_case
    ADD COLUMN source_section_title VARCHAR(500) NULL COMMENT '应答片段在标书原文中的最近有效章节标题';
ALTER TABLE knowledge_case
    ADD COLUMN source_section_path VARCHAR(2000) NULL COMMENT '标书原文章节层级路径，使用 > 分隔';
ALTER TABLE knowledge_case
    ADD COLUMN source_document_name VARCHAR(255) NULL COMMENT '来源标书用户可读文件名';
ALTER TABLE knowledge_case
    ADD COLUMN source_page_number INT NULL COMMENT '来源标书可证明的逻辑页码';
ALTER TABLE knowledge_case
    ADD COLUMN source_section_index INT NULL COMMENT 'Markdown 章节扫描稳定序号';
