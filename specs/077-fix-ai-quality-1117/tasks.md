# Tasks: 项目 1117 AI 标书质量核查纠偏

## Phase 1 - RED 基线

- [x] T001 在 `BasicInfoAndAttachmentRulesTest` 增加空格日期与保证金图片证据不足用例并记录失败。
- [x] T002 在 `DeterministicCaCertificateExtractorTest` 增加纸质模板词与电子/CA 强证据冲突用例并记录失败。
- [x] T003 在 `QualificationDetailReconcilerTest` / `QualificationQueryDelegateTest` 增加三项 ISO 组合拆分与逐项关联用例并记录失败。
- [x] T004 在 `ScoreTypeClassificationPolicyTest` 增加仓库数量、服务方案、应急方案分类用例；基线已通过，确认为旧激活数据问题。

## Phase 2 - 最小实现

- [x] T005 扩展 `BasicInfoCheckPolicy` 的日期格式容忍度。
- [x] T006 调整 `DeterministicCaCertificateExtractor` 为正反证据汇总决策，移除全文首次命中短路。
- [x] T007 调整 `SubItemAndAttachmentRules`，保证金证据不足返回 MANUAL_REVIEW。
- [x] T008 在资质纯核心中规范化并拆分 ISO 标准，在查询委托中逐项严格匹配且过滤过期证书。
- [x] T009 分类器基线正确，未制造实现改动；生产候选激活保留为部署后受控步骤。

## Phase 3 - GREEN 与回归

- [x] T010 运行五个目标测试类并取得 GREEN。
- [ ] T011 运行 compliance/scoreparse 相关回归及 ArchitectureTest。
- [ ] T012 运行 backend 完整测试、`git diff --check` 和范围审计。

## Phase 4 - 清理与评审

- [x] T013 执行 AI slop cleaner Mode B，合并重复 ISO 拆分逻辑并重跑受影响测试。
- [x] T014 执行 xiyu-code-review、code-review、production-risk-review；日期完整性、CA 冲突与证书有效期发现已处置，激活路径列为后续门禁。
- [ ] T015 生成 verify 工件，确认无 commit/push/PR/deploy/生产数据写入。

## Dependency Order

T001-T004 → T005-T009 → T010-T012 → T013-T015。每条实现必须由对应 RED 证据驱动；评分链若基线已 GREEN，不得制造无意义代码修改。
