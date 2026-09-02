-- V1206: score_parse_task 增加 normalization_warning 显式告警列（Phase 2 spec 057 FR-05）
--
-- 背景：WeightNormalizationPolicy 在合计偏离 100 时按比例缩放（容差 ±0.5），
--   原实现静默洗白——用户看到"合计=100"但实际项缺失/权重错误无从知晓。
--   Phase 2 引入显式告警：缩放幅度 >5% 时落库告警消息，前端展示"已自动调整，请人工复核"。
-- Source: specs/057-score-eval-and-guard/spec.md FR-05；回滚见 rollback/migration-mysql/U1206
ALTER TABLE score_parse_task
    ADD COLUMN normalization_warning VARCHAR(255) NULL COMMENT '权重归一化显式告警（Phase 2 spec 057 FR-05）';
