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
- [x] T009A 补充网上开标/远程解密强证据，并接入保证金 PDF 页面图片视觉事实提取与保守融合。

## Phase 3 - GREEN 与回归

- [x] T010 运行五个目标测试类并取得 GREEN。
- [x] T011 运行 compliance 相关回归及 ArchitectureTest；复用 `ScoreExtractionPersistenceMysqlTest.incompleteUnknownAndManualChangesProtectActiveProjection` 验证候选不覆盖激活版。
- [ ] T012 运行 backend 完整测试、`git diff --check` 和范围审计（`git diff --check` 已通过；完整门禁随 push 执行）。

## Phase 4 - 清理与评审

- [x] T013 执行 AI slop cleaner Mode B，合并重复 ISO 拆分逻辑并重跑受影响测试。
- [x] T014 执行 xiyu-code-review、code-review、production-risk-review；日期完整性、CA 冲突与证书有效期发现已处置，激活路径列为后续门禁。
- [ ] T015 更新 verify 工件并推送既有 PR；确认无 deploy、重算、激活或生产数据写入。

## Dependency Order

T001-T004 → T005-T009 → T010-T012 → T013-T015。每条实现必须由对应 RED 证据驱动；评分链若基线已 GREEN，不得制造无意义代码修改。

## 2026-10-25 CA 追加任务（本轮范围）
- [x] T016 [US1] 在 DeterministicCaCertificateExtractorTest / CaCertificateEvidenceTest / CaCertificateCheckPolicyResourceTest / BidDocumentQualityEngineTest 增加 FR-013—016 RED 用例。
- [x] T017 [US1] 实现 CaPlatformNamePolicy，更新提取器、证据、需求融合和引擎查询失败保护（FR-013—016）。
- [x] T018 [US1] 执行清理、目标与关联回归、ArchUnit、npm build，记录证据（SC-006）。
- [x] T019 [US1] 更新wiki/lessons，执行独立评审与spec-plan-tasks一致性分析。

本轮状态：T017未闭合（电子招标投标交易平台/本次交易平台仍误判）；T019已执行独立3轮审查与一致性分析，Standards仍NEEDS_FIXES，禁止交付。详见`docs/reviews/2026-10-25-ca-platform-review.md`。

## 人工裁决后续任务
- [x] T020 [US1] 在CaCertificateCheckPolicyResourceTest增加未知身份（含原文泛称/陌生企业/伪匹配）的RED。
- [x] T021 [US1] CaPlatformNamePolicy/CaCertificateCheckPolicy落实正向身份边界，保留候选证据和独立占位FAIL。
- [x] T022 [US1] 关联回归、架构、冻结输入回放、两轴独立审查与记录更新。

用户裁决后最终状态：T017/T019/T020—022闭合，566项关联回归通过，两轴独立审查均MERGE_READY。前述NEEDS_FIXES为恢复前历史，不代表当前结论。只交付本地源码。
