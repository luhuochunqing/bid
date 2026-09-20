# Specification Analysis Report — 066

**Date**: 2026-09-20 | **Scope**: spec.md / plan.md / tasks.md / contracts / constitution（只读分析）

## Findings

| ID | Category | Severity | Location | Summary | Recommendation |
|----|----------|----------|----------|---------|----------------|
| F1 | Inconsistency | HIGH | spec.md FR-008 vs research D7/entry gate | FR-008 要求"前端等待窗口覆盖后端最长任务时长"，但多册极端场景（N×1800s + LLM）必然超过 30 分钟窗口 | 修订 FR-008 为"覆盖单册最长任务时长；多册极端场景以后台执行态兜底（不误报失败）"，与 D7 残余限制一致 |
| F2 | Ambiguity | MEDIUM | spec.md SC-005 | "≤600s；若测量无法达标则以等待上限覆盖"带条件兜底，严格可测性弱 | 保留测量目标但明确验收口径：以 T032 真机复跑耗时为准；不达标时须记录理由并仍满足等待上限覆盖 |
| F3 | Coverage | LOW | spec.md FR-013 | 负向约束（不引入 OCR）无直接任务 | 无需任务；已由 D3/契约与 580 文案测试（T023-T025）间接覆盖 |
| F4 | Terminology | LOW | plan/research/contract | "等待上限/超时预算/生效上限"混用 | 契约以"等待上限（生效上限）"为准，实现命名 `SidecarTimeoutBudget`，不阻断 |
| F5 | Coverage | LOW | spec.md SC-005 | 441MB 解析耗时的 sidecar 侧单测无法离线复现（需真机文件） | 已由 T032 真机复跑覆盖；sidecar 侧以分块完整性 + 内存守卫（T013-T015）代理 |

## Coverage Summary

| Requirement | Has Task? | Task IDs |
|---|---|---|
| FR-001 大文件可完成 | 是 | T009-T016, T032 |
| FR-002 上限按规模+可配 | 是 | T005, T007, T012 |
| FR-003 分类可区分 | 是 | T017, T018 |
| FR-004 文案匹配原因 | 是 | T019, T020, T025 |
| FR-005 扫描件提示 | 是 | T023-T025 |
| FR-006 超时文案 | 是 | T017-T020 |
| FR-007 服务异常文案 | 是 | T017-T020 |
| FR-008 前端窗口 | 是 | T026-T029（F1 修订后一致） |
| FR-009 可观测 | 是 | T012, T022 |
| FR-010 原始大小档位 | 是 | T009, T012 |
| FR-011 多册指名 | 是 | T019, T020 |
| FR-012 自动化测试 | 是 | T005/T006/T009/T010/T013/T017/T019/T023/T026 |
| FR-013 不引入 OCR | 约束（无需任务） | — |
| SC-001 复跑完成 | 是 | T032 |
| SC-002 580 文案 | 是 | T025, T032 |
| SC-003 误报 0 | 是 | T019, T020, T032 |
| SC-004 前端不误报 | 是 | T026-T029 |
| SC-005 解析提速 | 是 | T013-T015, T032 |
| SC-006 测试全绿 | 是 | T031 |

**Constitution Alignment**: 无 CRITICAL；TDD（III）由 RED 任务序列满足；行预算（IV）由 T011 拆分满足；无 Mock（II）无违反。

**Unmapped Tasks**: T001-T004、T030-T035 为流程/门禁任务（无故事标签，符合格式）。

**Metrics**: Requirements 13+6；Tasks 35；Coverage 100%（FR-013 为负向约束）；Ambiguity 1；Duplication 0；Critical 0；High 1（F1 已修订）。

## Next Actions

- F1 已修订（spec FR-008）→ 可进入 `/speckit-implement`
- F2/F5 在 T032 真机复跑中闭环；F3/F4 不阻断
