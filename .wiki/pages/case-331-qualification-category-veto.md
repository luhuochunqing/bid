---
title: 项目44等保评分项漏荐案例331
space: engineering
category: guide
tags: [案例推荐, 等保, 资质证书, CO-652, 生产验收]
created: 2026-11-18
updated: 2026-11-18
health_checked: 2026-11-18
sources:
  - docs/release/deploy-report-2026-11-17-44th-prod.md
  - specs/089-case-topic-relevance/spec.md
  - backend/src/main/java/com/xiyu/bid/casework/domain/policy/CaseCategoryMatchPolicy.java
  - backend/src/main/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicy.java
  - backend/src/main/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendAppService.java
  - src/views/Project/stages/components/AiRecommendDrawer.vue
backlinks:
  - _index
  - ai-case-recommend-data-source-fix
---

# 项目44等保评分项漏荐案例331

- 第44次生产部署报告把233条推荐记在配送项 `code=45`。等保项 `code=43` 的观察是案例331零命中。
- 本地用既存349条生产 ACTIVE 候选重放纯策略。43项标题与条款来自部署报告和现场反馈，项目背景来自同项目配送项快照。

| 输入 | !3043 合入前 `a20d1a7b4` | 第44次部署基线 `0ba490bcd` |
|---|---|---|
| 43项 × 331原字段 | 0分，大类冲突一票否决 | 0分，大类冲突一票否决 |
| 43项 × 331，仅在内存把案例分类改为「资质证书」 | 78分 | 78分 |
| 45项 × 331原字段 | 40分 | 0分，与评分项内容不相关 |
| 45项 × 331，仅在内存把案例分类改为「资质证书」 | 30分 | 0分，与评分项内容不相关 |

- 43项条款含「证书」，归为资质类。修复前，案例331的分类和标题均为「网站安全等级防护能力」，据此判为技术类。大类否决发生在读取应答正文和关键词加分之前，因此正文中的「等级保护」未参与判断。
- CO-652 禁止用方案替代证书，并为资质项查询企业资质库原件。!3043 正向测试使用显式资质标签，331的存量字段形状只用于配送负例。
- 接口 `items` 是历史案例，`matchedCertificates` 是原件候选。资质项的 `total` 取原件数，抽屉将它用于「匹配案例」标签，原件区还有独立的前端资质判断。仅凭 `total=0` 无法判断 `items` 是否为空。

## 本地修复与验收

- 历史案例沿用技术标签时，独立应答明确持有同类且等级达标的等保备案证明，可参加资质评分项的相关性评分。应答可省略主语，显式主语仅接受「西域」「我方」「我司」「本公司」。招标原条款和合作方持证均无法解除大类否决。
- 在修复提交 `540114aec` 重放上述349条候选，43项的331得68分，排第1，输入「等保」仍排第1。45项的331为0分，无推荐，仍返回233条。
- 线上验收需分别核对项目44两项的 `items`。企业资质原件的生产命中与卡片展示待验。

相关诊断见 [[ai-case-recommend-data-source-fix]]。
