---
title: 案例子主题与质量分的写入缺口
space: engineering
category: guide
tags: [案例推荐, knowledge_case, sub_topic, quality_score, 结项沉淀, 回填]
created: 2026-11-11
updated: 2026-11-11
health_checked: 2026-11-11
sources:
  - backend/src/main/java/com/xiyu/bid/casework/domain/policy/KnowledgeCaseFeatures.java
  - backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java
  - backend/src/main/java/com/xiyu/bid/casework/application/KnowledgeCaseFeatureBackfillRunner.java
  - backend/src/main/java/com/xiyu/bid/casework/application/port/KnowledgeCaseFeatureBackfillPort.java
  - backend/src/main/java/com/xiyu/bid/casework/infrastructure/KnowledgeCaseFeatureBackfillAdapter.java
  - backend/src/main/java/com/xiyu/bid/casework/infrastructure/KnowledgeCaseRepository.java
  - backend/src/test/java/com/xiyu/bid/casework/infrastructure/KnowledgeCaseFeatureBackfillAdapterTest.java
  - backend/src/main/java/com/xiyu/bid/casework/domain/policy/CaseSubTopicPolicy.java
  - scripts/extract-historical-case-features.py
  - scripts/import-case-slices-to-knowledge-case.py
backlinks:
  - _index
  - ai-case-recommend-data-source-fix
---
# 案例子主题与质量分的写入缺口

> **一句话**：匹配器早就读 `knowledge_case.sub_topic` 和 `quality_score`，但结项沉淀和历史导入都没写这两列，精确 25 分和质量加分从未生效。

## 事实

- 匹配在 `ScoreItemCaseMatchPolicy` 现场计算，不读库存分数。
- `sub_topic` 与推断枚举名相同：25 分。列为 null、空白或 `OTHER`，且正文命中至少 1 个关键词：15 分。写成另一个具体枚举：0 分，不再走 15 分兜底。
- `quality_score >= 80` 再加 5 分。实体默认值是 60，60 不加分。
- 2026-11-11 生产只读：354 行 `sub_topic` 全空，在架案例 `quality_score` 全是 60。打分类文件与生产 jar 内的类哈希一致，公式本身没有分叉。

## 谁该写、以前为什么没写

| 写入方 | 旧行为 | 现在 |
| --- | --- | --- |
| `ProjectClosedEventListener` | 新案例不设两列，质量分留在默认 60 | 保存前调用 `KnowledgeCaseFeatures.assign` |
| `import-case-slices-to-knowledge-case.py` | JSONL 无字段时写成 60；`INSERT IGNORE` 不更新旧行；`--backfill-meta` 只改中标结果和项目类型 | 未改。不要用它补这两列，也不要把生产主机加进白名单 |
| `KnowledgeCaseFeatureBackfillRunner` | 不存在 | 仅当 `XIYU_CASE_FEATURE_BACKFILL` 为 `1` 或 `true` 时调用端口。默认不跑 |
| `KnowledgeCaseFeatureBackfillAdapter` | 不存在 | 只回填 `sub_topic IS NULL` 的行。新应用类不直接依赖基础设施，所以写库放在适配器 |

子主题复用 `CaseSubTopicPolicy.inferSubTopic`，存枚举名，包含 `OTHER`。质量分与 `scripts/extract-historical-case-features.py` 同一阶梯：不足 120 字为 0，否则从 50 分起按字数、非空行数、条目符号和数字承诺加分，上限 100。沉淀路径没有抽取脚本的 `para_count`，段落按非空行计。

## 回填注意

- 只处理空主题。写成 `OTHER` 后再次启动不会再改。
- 回填独立事务只 UPDATE `sub_topic` / `quality_score`，不修改或保存查询得到的实体，防止全列 UPDATE 撤销并发下架、置顶、复用次数及来源修改。
- 写入时原子校验 `sub_topic IS NULL`、原质量分、原评分项标题和正文。标题与正文用 `CAST AS BINARY` 精确比较，包含大小写与尾随空格；冲突跳过，返回实际更新条数，后续启用回填时可重试。
- 标题先命中具体主题后，跨主题正文不再拿 15 分。同主题从 15 分变成 25 分。
- 启动参数含 `prod` 也会装上这个 Runner。没设环境变量就不会查询、不会保存。
- 生产回填要单独确认。本页不授权直接 UPDATE。

## 这两列补不上的分

- 销售额、起订额、服务案例在库里没有对应正文时，推断仍是 `OTHER`。
- 客户类型是原文精确匹配。项目写「央企」、案例写 `CENTRAL_SOE` 时，那 3 分不会出现。
- 项目行业为空时，项目类型那 5 分不会出现。
