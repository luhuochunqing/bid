-- V1099: Add completion_notes column to tasks table
-- §4.1 #1/1 CO-344 — 任务详情交付物/完成情况说明为空，需持久化completion_notes字段
ALTER TABLE tasks ADD COLUMN completion_notes TEXT;
