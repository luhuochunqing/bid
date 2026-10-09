---
title: 比较基准引用审计通过但Word规则兼容性未闭合
space: engineering
category: lessons
tags: [scoreparse, comparison-reference, word, experiment]
sources:
  - .wiki/pages/scoreparse-canonical-source-and-logical-tables-20261003.md
  - AGENTS.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-04
updated: 2026-10-04
health_checked: 2026-10-04
archive: false
---

# 比较基准引用与Word来源兼容性

- 外部报告：/Users/user/xiyu/experiments/scoreparse-realfile-20261004/phase11-comparison-and-word-compatibility/results/report.md。
- 相同117评分区单元的两轮真实模型审计：8/8聚焦引用入口、4/4自包含反例均通过；未证明全文件或新文件泛化。
- 1109四处旧聚合从原始XML独立复现4/4；21个名称唯一定位。候选的PDF坐标和Word原生XML坐标各自保留，不以不同坐标空间误称PDF坐标无效。
- 原生证据补充覆盖19个合并评分行及2个正文分组；65段冻结字段文字中54段唯一字面匹配，11段规则文字不匹配，完整兼容性仍未通过。
- 副作用：vMerge续行可能不含名称，但仍属同一评分项；名称定位不能证明规则完整；补证据不能暗改旧规则或掩盖旧候选来源错误。
- 联合门禁未通过，未跑关联回归/新文件泛化；业务代码、业务配置和评分字段未改。
- 相关：[[scoreparse-canonical-source-and-logical-tables-20261003]]、[[_index]]。
