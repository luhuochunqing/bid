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

## Current evidence 2026-09-27 (supersedes stale environment notes above)
- 运行0d8543c92，任务274；40facts中目标13叶子100分已出现，人工部分采纳8项44分，缺少56分。
- 五项未绑定：中文换行、三种跨页引文、物流引文包含模型错字/重复。当前Sidecar真实原始80cells与存档一致；旧artifact的section需重新生成。
- 当前阻塞还含类别/名称混用、源规则被model summary替换、验证状态覆盖、按物理表丢总分、MANUAL_APPROVED后隐藏未采纳项。
- PDF重新下载SHA18061117e166335881c6a1864f708e5109988af773aea321b26c0f4b751ae18f，1607617bytes。当前40facts通过只读事务取证，私有.runtime/root-cause/1382-v2。
- 用户确认：候选允许评分；首次可用、不完整重解析保留旧版、显式切换。故旧“不迁移/不改prompt”约束由批准的新设计替代：增量持久化与来源锚点prompt，但模型配置不改。
- 本次不从旧Flyway失败笔记推断当前服务不可用；真实部署和重解析另验收。
