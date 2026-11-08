---
title: 项目1119重解析84条事实与100分旧结果的归属诊断
space: engineering
category: lessons
tags: [scoreparse, multi-package, production]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewFactMapper.java
  - src/views/Project/stages/components/ScoreCandidateReviewCard.vue
  - src/composables/projectDetail/manualScoreCandidate.js
backlinks:
  - .wiki/pages/_index.md
created: 2026-11-07
updated: 2026-11-07
health_checked: 2026-11-07
archive: true
---

# 项目1119多标包解析诊断

## 现场证据

- 生产release2960e7ea5，与调查源码一致。项目名称明确为“标包2和3”。
- 最新task205于2026-11-07 12:37:54至12:43:03执行，COMPLETED / REVIEW_REQUIRED，原因ORIGINAL_SCHEME_AMBIGUOUS。
- task105、197、205均84条事实；按fact_id比对，84条原文、权重全部相同。文件标识相同，但历史文件哈希为空，不能宣称字节级同源验证。
- task205中t-0007有11条正分事实、合计100；其他73条为零分审查、指引、模板事实。
- 正式score_item仍为task105的11项100分，2026-09-20 14:47:30人工采纳。11项名称、权重、完整细则与task205正分事实一致。

## 原因与边界

- 生产journal与SCHEME_QUALIFICATION证据显示两个原文章节：“第三章 评审办法（综合评分法--适用于标包一）”；“第三章 评审办法（经评审的最低价法--适用于标包二、三）”。100分评分表t-0007归属前者。
- OriginalScoringContext.resolve检出多个方案后返回歧义，阻止自动覆盖。旧100分不能直接作为标包2和3的正确基准，须核对实际投标标包及原文。
- UI的84是事实条数。多方案未选时isManualScoreCandidate不接纳当前候选，显示0项0分，将全部事实置于审计区；这不是丢失16分。
- 产品缺陷：资格/形式审查零分事实仍可被标为candidateEligible；候选过滤仅判断方案归属，未明确区分计分单位与资格审查。页面未清楚解释标包适用关系及最低价法。

## 副作用与调查范围

- 保留旧正式项不代表旧项业务适用性正确；最新候选和旧生效版本须分别核验。不得通过强凑100分、全部纳入、关闭方案校验处理。
- 浏览器登录过期，页面证据来自用户截图；生产SQL和journal为当前证据，未独立复核原始DOCX。
- 未改业务代码、生产数据，未重解析、采纳或部署。本机临时证据在/tmp/xiyu-1119-diagnosis/，真实文件、日志、凭据不入库。

## 关联

- [[_index]]
- [[score-parse-service]]

## 2026-11-08复核补充

- 用户确认下拉可用；问题是选标包二、三后的分数。
- 当日重新只读查询最新任务仍为205：标包一归属61条事实，11个正分项合计100；标包二、三归属23条事实，正分项0、数值合计0。
- 标包二、三持久化原文4.4.1明确：“评审小组按合格供应商的最终报价由低到高顺序推荐3名成交候选供应商。”故其评审语义为合格性审查及报价排序，零权重不应被展示为投标人得零分。
- 尚无“选中标包二、三仍出现100分”的用户证据；此前回复将用户的“分数不对”推断为100分串入，推断撤回。需区分当前生效旧100分表与所选方案候选。
- 当前UI沿用分值合计、未知总分与评分采纳流程，不能表达最低价法。不具备原始DOCX独立复核证据，不将此结论扩大为所有最低价方案无需量化的通用规则。
