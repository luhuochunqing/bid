---
title: 多依赖入口拆分与证据记录齐全仍未保证关联语义
space: engineering
category: lessons
tags: [scoreparse, references, evidence, regression]
sources:
  - .wiki/pages/scoreparse-native-reference-scope-regression-20261004.md
  - AGENTS.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-04
updated: 2026-10-04
health_checked: 2026-10-04
archive: false
---

# 多依赖入口拆分与逐项证据实验

- 外部报告：/Users/user/xiyu/experiments/scoreparse-realfile-20261004/phase14-dependency-splitting-and-evidence/results/report.md。
- 同67原文两轮找到16/15入口，保留14材料入口，资格前提分别建入口成功；内部定义引用单列存在轮间差异。两轮各22数字/裸名称控制未误建入口。
- 本轮16 Word关联审计通过，29条APPLIES逐条具有当前入口与完整已提供原文；资格前提目标AMBIGUOUS，材料本体未提供，仍未闭合。
- 原11字段差异冻结；模型仅3/16独立报告差异，外部差异封套不能充当模型warnings。
- 旧28入口实际复跑：焦点21/23、严格31/34。1382一级表串非一级项、基准价父组入口错误，1386父组缺入口证明；不退步验收失败。
- 结构化5例14检查过2失败：非一级项误留CONDITIONAL，模糊资格前提误升APPLIES。证据记录齐全不能代替适用语义与目标闭合。
- 54实际成功请求，1传输状态0重试；11冻结边界检查过。字段、业务代码、配置、数据库不变；未做新文件泛化。联合门禁NOT_PASSED。
- 跨项副作用：父组必须独立证明入口；子项完整证据不可替代。目标模糊与外部材料未知必须分别保留，不能以完整本地原文宣布闭合。
- 相关：[[scoreparse-native-reference-scope-regression-20261004]]、[[_index]]。
