-- V1202: 竞品字典治理——status 软停用 + name 唯一约束 + seed 24 个现有竞品
--
-- 背景：specs/055 竞品公司自定义输入与竞争对手库治理（方案 B1）。
-- 录入端从"前端硬编码 24 常量"改为"后端 competitors 动态加载 + 输入即建"，
-- 需为全局竞品字典补 status（软停用）、name 唯一（幂等/并发防重兜底），
-- 并将现有 24 个硬编码竞品 seed 进字典，保证录入体验不退化。
--
-- 现状：competitors 表当前无 seed 数据（demo-data.sql 未插入），无需清理重复。

-- 1. 加 status 软停用列
ALTER TABLE competitors ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '竞品状态(ACTIVE/DISABLED)';

-- 2. name 加唯一约束（幂等与并发防重的数据库兜底）
ALTER TABLE competitors ADD CONSTRAINT uk_competitor_name UNIQUE (name);

-- 3. seed 现有 24 个硬编码竞品（幂等：撞唯一约束时跳过，避免与已手动录入数据冲突）
INSERT IGNORE INTO competitors (name, status, created_at) VALUES
('震坤行', 'ACTIVE', NOW()),
('鑫方盛', 'ACTIVE', NOW()),
('浙江物产', 'ACTIVE', NOW()),
('欧菲斯', 'ACTIVE', NOW()),
('领先未来', 'ACTIVE', NOW()),
('浙江宏伟', 'ACTIVE', NOW()),
('咸亨国际', 'ACTIVE', NOW()),
('企事通', 'ACTIVE', NOW()),
('一线达通', 'ACTIVE', NOW()),
('京东', 'ACTIVE', NOW()),
('苏宁', 'ACTIVE', NOW()),
('科力普', 'ACTIVE', NOW()),
('得力', 'ACTIVE', NOW()),
('史泰博', 'ACTIVE', NOW()),
('齐心', 'ACTIVE', NOW()),
('广博', 'ACTIVE', NOW()),
('一出科技', 'ACTIVE', NOW()),
('怡亚通', 'ACTIVE', NOW()),
('申合信', 'ACTIVE', NOW()),
('大江科技', 'ACTIVE', NOW()),
('诚和致远', 'ACTIVE', NOW()),
('阳采', 'ACTIVE', NOW()),
('德致商成', 'ACTIVE', NOW()),
('全程速达', 'ACTIVE', NOW());
