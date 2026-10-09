---
title: 评分引用入口与片段作用域实验未通过整体验收
space: engineering
category: lessons
tags: [scoreparse, references, source-scope, holdout-evaluation]
sources:
  - AGENTS.md
  - PLANS.md
  - .wiki/WIKI.md
  - .wiki/pages/scoreparse-cross-format-strategy.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-03
updated: 2026-10-03
health_checked: 2026-10-03
archive: false
---

# 评分引用入口与片段作用域实验未通过整体验收

## 状态和授权边界

- 实验结论：NOT_PASSED；仅真实文件/真实模型实验，未改业务代码、配置或数据，未重解析、提交、部署。
- 脚本、原件、请求、原响应、验收和完整报告留在仓库外：`/Users/user/xiyu/experiments/scoreparse-realfile-20261003/phase6-callsite-scope/`。
- 本页按AGENTS.md复合查询回填纪律追加结论，不修改之前的实验数据或历史Wiki正文。
- 关联：[[scoreparse-cross-format-strategy]]。

## 三个目标反例

| 对照 | 1382两个报价表 | 219资格业绩 |
| --- | --- | --- |
| 全文和入口归属，模型抄写引用 | 归属正确但遗漏填报细则 | 目标错误ID，混入其他资格要求 |
| 入口绑定原文片段，模型选片段 | 完整且分支分开 | 仍选整段无关资格子句 |
| 每入口独立、每片段判相关/不相关 | 表1.1归一级，表2.1归非一级 | 两个实际入口归既有performance，只保留业绩条件及续文 |

- 三个核心案例同提示词、同完整输入做三轮，9/9案例检查通过；其他候选excluded/unknown分类仍有变化。
- 共用方法四基准价和三个月账期保留此前正确直接证据，仍作用于两个子项；不复制整条规则的适用集合给每个引用。
- 所有评分字段冻结；新样本仅首次提取后冻结，未修其字段错误；没有独立人工gold。

## 尚未解决的退化和泛化问题

- 原有24项检查：17正向PASS、4禁止串项PASS、3UNKNOWN_REPORTED。检查依赖保留的既有正确证据，不代表本轮新解决17项。
- 严格追加审查：1382另一个3.2.2(2)入口将未勾选“□方法一/二/三”挂入非一级报价证据；三块此前未保留，构成新增语义退化。原清单通过不能证明整体不退步。
- 219另一个报价表入口缺相应CALLSITE证明，被机械校验拒绝。
- 四份未参加之前API实验且哈希独立的真实文件，两PDF、两DOCX；提示词在新样本读取前冻结，未按新样本调参：
  - 1386：分类附件找到但跨四页超长范围被拒，未完整绑定。
  - 1377：应急方案/价格系数归属局部通过；第10条与实际第9条冲突，报价明细表未提供，仍未闭合。
  - 1373：引用扫描漏了明确商务/技术详细评审表入口，把大量评分正文当引用；字段逐字检查通过也不能掩盖入口漏识别。
  - 1346：原页可读，文字块顺序打散；11个引用声明均非逐字，被拒绝，后续关联跳过，不计为通过。
- 新样本7项局部检查：2 PASS_SCOPE、1 UNRESOLVED_REPORTED、1 FAIL、2 FAIL_DISCOVERY、1 BLOCKED_SOURCE；不是全文件准确率。
- 缺失附件、外部条款、编号冲突、无效范围和下游未解保留未知。结构CLOSED_PROVISIONAL不是语义闭合。

## 来源与校验边界

- 59次真实HTTP200请求，原响应保留；14项纯校验边界检查通过，纯校验输入不冒充模型响应。
- 原文片段由实际块和字符偏移直接绑定，模型只选ID，不生成替代引文；位置有效仍不能证明启用选项/作用域正确。
- 原页视觉仅由Agent补核对证据，本轮未给模型图片，不改字段、引用状态或作用域，不声称视觉提升。
- 实验批次校验器的整文/单批覆盖边界曾误用，已在仓库外纠正并记录；没有重写原响应。
- 无扫描PDF评分正样本、真实二进制DOC评分正样本，不能推广至任意格式。

## 后续验证和跨模块副作用

- 先验证统一的入口召回、原文阅读顺序/跨页范围、启用/未启用/背景/证明角色与片段作用域约束；不得针对单文件加业务分支。
- 上游入口遗漏、原文乱序、目标无效或编号冲突会使关联阶段无法完整闭合；评分字段相同和sourceId存在不能作为自动采纳依据。
- 下一轮复用新增失败案例，再选另一组未参与实验的原件；这四份现在已是实验样本，不能再次称未知验证集。
- 完整证据：`results/report.md`、`results/callsite-ledger.md`、`evaluation/holdout-checks.json`、`evaluation/post-call-scope-counterexamples.json`、`evaluation/final-verification.json`。
