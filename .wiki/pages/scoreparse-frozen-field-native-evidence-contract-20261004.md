---
title: 冻结字段与原生Word证据分开绑定的验收
space: engineering
category: lessons
tags: [scoreparse, frozen-fields, native-evidence, regression]
sources:
  - .wiki/pages/scoreparse-comparison-reference-and-word-bridge-20261004.md
  - AGENTS.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-04
updated: 2026-10-04
health_checked: 2026-10-25
archive: false
---

# 冻结旧字段、完整原生证据、显式差异

- 外部报告：/Users/user/xiyu/experiments/scoreparse-realfile-20261004/phase12-frozen-fields-native-evidence/results/report.md。
- 1109既定11项绑定通过11/11；覆盖34原生行、67原文单元。旧候选全文件哈希不变，原生评分标准与证明要求作为独立补充证据。
- 独立核验：原始XML表头定位列，按评分名称单元格vMerge取得完整续行，对照上轮序号单元格分组；下一独立评分项作为结束边界。
- 56/56错误注入被拒绝：改字段11次、删末尾单元11次、混入其他项11次、隐藏差异11次、删完整续行8次、删条件/范围限定后文4次；全部附加检查89/89。
- 差异按去空白后的文本编辑报告，11项旧规则差异仍未解决；来源绑定通过不等于字段修复、模型关联回归或新文件泛化通过。
- 副作用：名称与序号合并单元格的续文都需完整；评分标准解释也是证据；旧规则不能被原文覆盖而消除冲突标记。
- 6986旧实验及基准文件哈希未变；业务代码、配置、评分字段未改；未请求新模型、未部署。
- 相关：[[scoreparse-comparison-reference-and-word-bridge-20261004]]、[[_index]]。
