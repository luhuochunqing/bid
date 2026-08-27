-- V1204: score_parse_task 增加 run_summary 运行档案列（spec 057 FR-04）
-- 单次解析持久化 lane 贡献/对齐率/guard 计数/模型名 JSON，失败可追溯、按模型归因（lessons §7 qwen 空返回事故复盘）
ALTER TABLE score_parse_task
    ADD COLUMN run_summary TEXT NULL COMMENT '运行档案JSON: lanes/aligned/merge计数/finalTotalWeight/llmModel (spec 057)';
