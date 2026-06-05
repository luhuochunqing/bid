-- V1009: 新增 basic_info_saved_at 字段，用于区分标讯录入流程中的"基本信息未保存"与"基本信息已保存"两个阶段
-- 阶段一：基本信息未保存（basic_info_saved_at IS NULL）
-- 阶段二：基本信息已保存（basic_info_saved_at IS NOT NULL）
-- 分配后进入 TRACKING 状态，basic_info_saved_at 保持已有值

ALTER TABLE tenders
  ADD COLUMN basic_info_saved_at DATETIME NULL COMMENT '基本信息最近一次保存时间，用于前端判断录入流程阶段' AFTER updated_at;

-- 为已有标讯设置默认值：已分配（project_manager_id IS NOT NULL）的标讯视为已完成基本信息填写
UPDATE tenders SET basic_info_saved_at = COALESCE(updated_at, NOW()) WHERE project_manager_id IS NOT NULL;
