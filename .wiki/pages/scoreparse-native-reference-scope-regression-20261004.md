---
title: 原生证据逐入口映射正确但完整性与严格回归未通过
space: engineering
category: lessons
tags: [scoreparse, native-evidence, references, regression]
sources:
  - .wiki/pages/scoreparse-frozen-field-native-evidence-contract-20261004.md
  - AGENTS.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-04
updated: 2026-10-04
health_checked: 2026-10-25
archive: false
---

# 原生Word证据逐引用关联与严格回归

- 外部报告：/Users/user/xiyu/experiments/scoreparse-realfile-20261004/phase13-reference-scope-and-regression/results/report.md。
- 11项67原文单元经实际模型扫描得到14证明入口；逐入口14/14归属正确、完整原生行选入、其他叶项排除。材料本体未提供或核验，不能视为引用闭合。
- 模型仅2/14独立报告旧字段差异；外部封套14/14原样保留模型原答和冻结来源差异，不能将来源记录伪装成模型warnings。
- 原文复核发现c000871“在满足资格条件的基础上”未被自动扫描单列；人工补做诊断为AMBIGUOUS、tech_2 CONDITIONAL，不计自动发现通过。
- 旧28入口输入冻结一致，实际新模型复跑：23焦点全过；34严格审计32过2失败。1386 technical_score父组缺当前入口证明；1346 system_certificates缺TARGET证明。
- 旧24混合证据检查为17正例PASS、4反例PASS、3UNKNOWN_REPORTED；保留DIRECT证据，不能替代新模型全链或34项严格审计。
- 副作用：父组也要独立证明实际入口；完整原文证据无法弥补未发现入口；差异必须作为独立来源元数据保留；外部材料未知与关联相关性分开报告。
- 45真实请求HTTP200、0图像；11边界检查过、语义联合验收未通过。字段、业务代码、配置、数据库未改；未进入新文件泛化。
- 相关：[[scoreparse-frozen-field-native-evidence-contract-20261004]]、[[_index]]。
