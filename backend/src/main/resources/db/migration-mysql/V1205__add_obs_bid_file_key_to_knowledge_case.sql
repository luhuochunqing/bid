-- V1205: knowledge_case 增加历史切片原版投标文件的 OBS 对象键
-- 仅由历史切片导入管道（scripts/import-case-slices-to-knowledge-case.py）写入；
-- 签发端点据此生成预签名下载链接，不接受外部传入路径。
ALTER TABLE knowledge_case
    ADD COLUMN obs_bid_file_key VARCHAR(512) NULL COMMENT '历史切片原版投标文件 OBS 对象键（导入管道写入，如 2026年（投标文件）/项目目录/文件名.docx）';
