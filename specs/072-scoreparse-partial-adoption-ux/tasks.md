# Tasks: 评分候选部分采纳与中文校验说明

## Phase 1: 回归测试（RED，已完成）

- [x] T001 [US1] 在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppServiceTest.java` 增加 91/100 人工采纳并保存实际 91 分的用例。
- [x] T002 [US1] 在同一后端测试增加 101/100 拒绝且不持久化用例。
- [x] T002a [US1] 增加 100/-9 调整后拒绝且不持久化用例，防止负分抵消绕过总分上限。
- [x] T003 [US1] 在 `src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js` 增加 91/100 可采纳提示和 101/100 禁用用例。
- [x] T004 [US2] 在该组件测试增加机器错误码/JSON 映射为中文且不泄露的用例。
- [x] T005 验证后端及前端目标测试按预期因尚未实现而失败。

## Phase 2: 人工部分采纳（US1，已完成）

- [x] T006 [US1] 在后端采用边界仅拒绝有效候选总分大于已验证声明总分，并确保拒绝在写入前。
- [x] T006a [US1] 在纯领域采纳策略拒绝任一候选负分。
- [x] T007 [US1] 在前端警告中说明差额，允许不超总分的有效候选人工采纳，继续禁用超额、冲突、未知多表或未选方案。
- [x] T008 [US1] 复核实际采纳总分仍作为实际值保存，声明总分证据及采纳审计不被覆盖。

## Phase 3: 中文解释（US2，已完成）

- [x] T009 [US2] 在候选卡展示层将类别/引用行不一致、权重来源未知、总分差异转换为中文操作说明。
- [x] T010 [US2] 对未知校验结构使用通用中文提示；不渲染原始验证状态、机器诊断串、JSON 和内部定位符。
- [x] T011 [US2] 保留原始后端诊断与来源证据，不改写审计数据。

## Phase 4: 验证与审查

- [x] T012 运行后端 `ScoreParseReviewAppServiceTest` 与 `ScoreReviewAdoptionPolicyTest`。
- [x] T013 运行前端 `ScoreCandidateReviewCard.spec.js`。
- [x] T014 执行 Speckit 规格一致性分析及自审；更新执行计划和 PR 验收边界。
