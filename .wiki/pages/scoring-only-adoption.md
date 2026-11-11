---
title: 评分事实与非计分审查事实的采纳边界
space: engineering
category: lessons
tags: [scoreparse, qualification, adoption]
sources:
  - specs/084-scoring-only-adoption/spec.md
  - specs/084-scoring-only-adoption/plan.md
  - docs/lessons/lessons-learned.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreReviewAdoptionPolicy.java
backlinks:
  - .wiki/pages/_index.md
created: 2026-11-11
updated: 2026-11-11
health_checked: 2026-11-11
---

# 评分项采纳边界

- 来源方案归属不等于可计分。同评审章节里的营业执照、形式审查、响应性或否决条款必须保留证据，但不能仅据IN_SCHEME标记成为正式项。
- 旧快照投影：非正分不能仅凭旧candidateEligible=true入选，完整原文核验扣分除外；原scopeStatus/方案/来源定位仍保留。原因SCORE_WEIGHT_NOT_CONFIRMED不等于已证明该条是资格项。
- 人工采纳：应用调整后的分值，负分失败，无证据零分/缺分跳过，全无有效计分项则失败；显式ID与默认采纳一致。真实分值未识别项可人工明确纳入并补分；这不自动证明原文核验成功。
- 前端共享选择器：非正分默认进审计，人工纳入用于编辑。引用资格条件的真实正分评分项保持，不用资格关键词一刀切。
- 诊断：1120正式task203（49项/38零分），44正式task215（56项/41零分），总分都100；审查零分不会破坏总分，因此总分正确不证明集合正确。历史诊断不是发布后验收。
- 跨模块副作用：重新解析产生候选不会自动替换正式项；采纳新集合可能失效旧实际评分。当前未部署/改生产；恢复按specs/084-scoring-only-adoption/quickstart.md执行预检、备份、预览和正常API采纳，不直接删库。

- 独立审查修正：保留SourceDeductionSelectionPolicy.verified既有零分扣分契约，以服务端SOURCE_VERIFIED_ZERO_DEDUCTION证据绑定原文；修改原文或来源缺失不允许继承。内部来源candidateEligible仍用于原文覆盖，不等同前端可采纳计分资格。
