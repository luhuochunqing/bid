---
title: 非适用分支排除失败与模糊目标禁止确定适用实验
space: engineering
category: lessons
tags: [scoreparse, references, evidence, regression]
sources:
  - .wiki/pages/scoreparse-dependency-splitting-and-proof-record-experiment-20261004.md
  - AGENTS.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-04
updated: 2026-10-04
health_checked: 2026-10-04
archive: false
---

# 排除与模糊目标门禁集中实验

- 外部报告：/Users/user/xiyu/experiments/scoreparse-realfile-20261004/phase15-exclusion-and-ambiguity-gates/results/report.md。
- 冻结5入口两轮10实际请求，只有统一提示增加排除和禁止模糊目标确定适用规则；输入逐字节与phase14一致。
- 一级表引用两轮均误将非一级评分项CONDITIONAL，模型以其他报价表缺失为理由；上下文共用规则范围仍扩展到独立专用表入口，EXCLUDED门禁失败。
- 模糊资格前提两轮均保留tech_2 CONDITIONAL，无APPLIES，候选和警告保留；目标未消歧，引用未闭合。
- 基准价和完整169条分类目标正例两轮通过。内部定义两轮归属正确，第一轮漏完整行序号、标题及c000882材料要求续文，第二轮完整。
- 两轮检查13/15、14/15，共27 PASS/3 FAIL；整体NOT_PASSED。10/10存在有效入口和目标证据不能代替完整原文审计。
- 7冻结边界检查通过，10HTTP200、图像0；业务与字段未改，无新文件泛化，未全量旧回归，不能宣称不退步。
- 跨项副作用：共用规则可共享不代表专用表入口可跨分支；另一入口附件缺失不应使当前无关评分项变成条件关联；目标存在、适用正确与完整行绑定必须分别验收。
- 相关：[[scoreparse-dependency-splitting-and-proof-record-experiment-20261004]]、[[_index]]。
