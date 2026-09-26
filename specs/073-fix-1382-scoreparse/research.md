# Research: 评分项证据链修复

## Evidence and current-baseline questions
- 诊断工作树记录的部署版本为 `e2502f5de`；本修复候选固定在 `a068f52fa38aa47bd925080202e85c187016d5cb`，不可把旧版本故障直接外推到候选。
- 固定取证材料位于忽略目录 `.runtime/root-cause/1382-post2767/`。真实PDF SHA和服务器原件关系需在任何端到端结论前核对；不将客户PDF提交。
- 旧日志显示272任务有13个eligible候选但正式项仍为旧266任务3项58分；273任务56候选零eligible，43 locator failures、12 scope mismatches、1 unverified scheme。两任务因候选不合格保留旧正式项是预期保护行为，UI隐藏最新候选是另一个可用性缺陷。

## Hypotheses to falsify on a068f52fa
1. Sidecar chapter-boundary regression: current implementation includes heading/event sequencing, unresolved breaks and continuity checks; run target tests and real table structural replay before touching it.
2. PDF cross-cell evidence binding: PDF cells lack `logicalRowId`; current binder may still only exact-match scalar evidence. Reproduce with fixed quotation/cell payload.
3. Category/name and maximum-weight semantics: dimension/category may be conflated with row identity; table/text paths may route evidence differently. Probe the shared pure policy with both pathways.
4. Batch file format identity: artifact may only retain first source `fileType`. Trace construction for each document; prove with reversed mixed-format order.
5. UI display: inspect current task candidate visibility condition and test old formal results plus pending latest task.

## Decisions
- No fuzzy string association, forced total, project-specific rule, global qualification relaxation, model prompt/temperature/OCR change.
- PDF-specific evidence path only when provenance is positively known. DOCX and text paths retain existing binder.
- Ambiguous duplicates, cross-chapter collisions, discontinuous continuations and conflicting weight statements remain review-required.
- The server backend is not healthy due Flyway 1218 failure; no live E2E in this implementation run.
