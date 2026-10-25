---
title: 评分引用结构化检索实验与语义边界
space: engineering
category: lessons
tags: [scoreparse, references, retrieval, docx, source-binding]
sources:
  - AGENTS.md
  - .wiki/WIKI.md
  - .wiki/pages/scoreparse-source-constraints-experiment-20261003.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-03
updated: 2026-10-03
health_checked: 2026-10-25
archive: false
---

# 评分引用结构化检索实验与语义边界

## 结论与授权

- phase8整体NOT_PASSED；9份既有真实文件、28个固定引用入口，23项焦点检查最终16 PASS、7 FAIL。未打开新陌生文件，未改业务代码、配置、数据、评分字段，未部署或重解析。
- 实验目录：`/Users/user/xiyu/experiments/scoreparse-realfile-20261003/phase8-structured-retrieval/`；完整证据见`results/report.md`和`evaluation/checks-r3.json`。
- 延续 [[scoreparse-source-constraints-experiment-20261003]]；入口从前轮实际输出固定，不能称本轮已验证入口召回。

## 来源可完整，关联仍可错误

- Word XML控制符独立保留；已确认Wingdings 2的00A3/0052状态，未知编码不猜测。纯来源15 PASS不能证明模型启用判断通过。
- Word真实表边界、PDF网格与跨页结构检索、完整表目录及真实表头使1386主入口四页169块完整关联；1382两张报价表分支正确且正文细则完整。
- 1371长表完整绑定，标题加7259个原文单元；名称冲突明确报告。实际三轮长Word请求均HTTP200，输入239767/337976/338019 token；不再全文多次重复并不证明语义正确。
- 三轮焦点分别11/12、15/8、16/7（PASS/FAIL）；每版本只一次模型采样且改变多因素，不作稳定性或单因素因果声明。

## 七个失败入口

- 219两处业绩入口漏选“3个（含）以上办公类物资”“服务范围应为全集团”；整资格章节发送完整，选择证据仍不完整。
- 1373商务与技术两处入口均关联所有商务9项和技术4项，独立入口范围未隔离。
- 1382两处基准价入口选入未启用方法，合法原文位置不是启用证明。
- 1377第10条与实际第9条编号冲突被整表FOUND掩盖；查到上层容器不是具体引用闭合。

## 冻结、回归与下一步

- 原24项仍17正向PASS、4禁止串项PASS、3未知已报告，包含保留旧DIRECT证据，不能用来抵消新增负例失败。
- 54实际请求均HTTP200，provider总token2540129；冻结验证31 PASS，6845基准文件及2474前轮文件不变，9份候选不变；临时凭证删除且无泄漏。
- 缺失报价附件、商品管理要求、外部ZBJ页签和认证平台仍未解决。完整表绑定与适用原文选择分开验收。
- 下一轮只实验入口评分范围、目标内部条款/启用分支和冲突保留；先解决七个失败入口且原结果不退步，再运行未参与实验的真实文件；评分字段继续冻结。
