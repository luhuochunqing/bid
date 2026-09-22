# Implementation Plan: 评分解析上游完整性修复

## 技术方案

1. 在纯领域层新增 parentCode 标准化策略：以 `tableId + sectionTitle + code` 建立事实池，按精确 code、事实 ID、路径最后一段进行唯一匹配；不唯一或不存在时返回未解析状态。
2. 在多模态适配入口先标准化父项编码，再执行证据校验和完整性判定；删除继承改动中的“按原文自动改写模型权重”行为，保留原始模型值并转人工复核。
3. 让完整性策略的叶子判断成为唯一权重口径；父项仅通过 `parentFactId` 被排除，不把父项权重与叶子权重相加。
4. 调整多表拆分：有单表汇总证据的表独立校验；多表无可归属总分证据时按表生成 UNKNOWN/REVIEW_REQUIRED 结果；只有单表场景继续保留正文/默认 100 兼容路径。
5. 保持正式发布门禁：只有 `AUTO_APPROVED` 且候选非空时写入正式 `score_item`，多表部分通过或总分未知只保留事实快照。

## 文件范围

- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalFactParentCodeNormalizer.java`（新增纯策略）
- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreCompletenessPolicy.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableSplitPolicy.java`
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/MultimodalFactParentCodeNormalizerTest.java`（新增）
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreCompletenessPolicyTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreTableSplitPolicyTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandlerTest.java`
- `specs/069-score-parse-upstream-integrity/*`

## 不变约束

- 不自动修正错误权重。
- 不合并不同评分表的总分。
- 不修改前端 adopt 禁用条件。
- 不引入迁移或远端数据变更。

## 验证策略

- 先运行新增 RED 测试，确认旧行为可复现。
- 运行 scoreparse 领域与应用定向回归。
- 运行后端完整测试及项目要求的架构/质量门禁。
- 若代码源文件修改，执行 ai-slop-cleaner 模式 B 后重新跑全部受影响验证。
