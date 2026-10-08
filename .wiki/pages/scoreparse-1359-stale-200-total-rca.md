---
title: 项目1359旧200分结果与最新解析阻断
space: engineering
category: debugging
tags: [scoreparse, subtotal, weighting, stale-result]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/StructuredTableExtractionHandler.java
  - backend/src/test/resources/scoreparse-golden/1359_sidecar.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreSubtotalColumnPolicy.java
  - docs/lessons/lessons-learned.md
  - src/composables/projectDetail/useScoreParseDrawer.js
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-08
updated: 2026-10-08
health_checked: 2026-10-08
archive: false
---

# 项目1359旧200分结果与最新解析阻断

## 实测证据

- 2026-10-08先通过测试服务器172.16.38.78只读查询MySQL与journalctl；后完成用户授权数据库事务与Chrome真实页面验收，见末节。
- score_item项目1359：任务207，38项，SUM(weight)=200.00，创建于2026-09-13 18:38:45，active_version_id为空。
- 任务207 AUTO_APPROVED事实：商务t-0002，13项100分；技术t-0003，25项100分。价格t-0004只有待复核0分事实；任务整体REVIEW_REQUIRED。
- 仓库1359原文：总分100，技术50%、商务20%、价格30%；商务合计最低12/最高100；技术合计最低38/最高100。
- 任务295：2026-10-08 16:58:18创建，17:00:44完成，REVIEW_REQUIRED / ORIGINAL_TOTAL_CONFLICT。事实表仅一条COMPREHENSIVE、weight=100、source_table_id和source_text为空。
- 日志：读取44678字，gemini-3.5-flash返回facts=1、modelTotal=100、originalTotal=null、leafTotal=0、violations=8；明确记录“保留现有评分项不覆盖”。
- run_summary：技术TABLE_SUBTOTAL_MISMATCH expected=38 actual=0；商务expected=12 actual=0。actual=0还涉及模型明细缺失，不能只归因于小计列错误。

## 因果链

1. 历史正式数据将两张原始100分表直接相加，缺少50%/20%贡献换算，也没有价格30%的有效评分单位。
2. 最新任务未产出完整替代结果，保护机制保留旧数据，因此仍显示200分。
3. 修复前OriginalScoringContext第109-116行将合计行所有数字列纳入小计，第144-147行保留先到的最低分12/38，再与最高分100形成冲突。缺少列语义过滤是当前源码中的独立缺陷。
4. StructuredTableExtractionHandler第239-246行正式发布要求文档级AUTO_APPROVED、原文验证通过、候选非空；保留此门禁。

## 最小方案与验证边界

- 单项目恢复建议：逐项复核原文完整规则，保留原始满分，技术乘0.50、商务乘0.20、价格乘0.30形成最终贡献，通过受控生效流程替换。当前旧项目无版本快照，须先核实接口能力，用户已授权直接修改1359数据库，使用备份、前置断言和服务端事务恢复。
- 最小独立代码修复：合计行仅读取来源明确的最高分/满分列；最低分列不参与，列不明仍待复核。完整明细提取与分部权重换算是另外两个交付条件。
- 禁止把旧200分除以2或仅改显示，也不放宽待复核发布门禁。
- 回归范围：最低/最高双列、单满分列、未知列、直接100分表、50/20/30方案和缺价格方案；注册真实Golden fixture，运行GoldenCasesReplayTest及eval:scoreparse:report，人工恢复后核对正式表、来源与UI；因不部署，本次不触发重解析。
- 本地已修复小计列语义并完成117项相关回归；服务器未部署、版本开关未打开。人工数据恢复经独立DATA_READY后已执行并完成数据库/页面核对，不把本地源码通过当作已部署。

## 跨模块副作用

- 旧实际打分器不能正确处理加权后的分数；用户确认本次仅恢复评分标准39项/100分，AI实际打分暂不可用，按钮未强制禁用，不触发评分。
- 保留既有来源验证、父子去重、方案资格和正式发布保护。

## 2026-10-08 人工恢复验收

- 用户明确授权直接修改1359数据库，不部署、不打开版本开关；另外确认只恢复评分标准、AI实际打分暂不可用。
- 原文2采购文件.docx SHA256=c30713962fa2c76f2e36cef36849b361f7c2e15c602c32e872ec33273bdb3c23；旧38项逐项唯一对应，商务13项×0.2=20、技术25项×0.5=50，补价格公式1项×0.3=30。不是旧200除2。
- 正式score_item实测39项/SUM(weight)=100.00；38旧ID/原文/详情/来源/满足状态保持，已有预计分仅按对应比例换算，价格PENDING/实际分null。原始扣分规则两条保留。
- task207 original_total_weight=200保留历史原始语义，旧run_summary内容保持，仅追加manualRecovery39/100；39条MANUAL_ADJUST审计。task295继续REVIEW_REQUIRED且updated_at不变。
- Chrome读取39条当前生效标准逐项与数据库的原文/名称/权重一致，合计100、PRICE-1权重30，实际打分未触发；PID29513和APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=false保持。
- 服务端SQL存储过程EXIT HANDLER保证失败整体回滚；MySQL8七场景实测涵盖正向、重复拒绝、精确回滚审计、任务/说明/价式漂移拒绝、所有DML后失败回滚与其他项目哨兵。实际执行SQL冻结SHA115ee0377f24fb84d346848efeb960908941dece3ca3670ef1a11ca82249bfff；本地最新版又补强BINARY漂移守卫，不在已恢复线上重复执行。
- 本地117项相关回归、10项恢复材料测试、Golden旧27案F1=100%、npm build通过；全量后端975/2fail/42error仍失败，独立基线复現两项断言失败。源码未部署、不承诺自动重解析或实际评分已修复。
- 验收报告：specs/074-fix-1359-score-contract/acceptance.md；备份、真实原文、执行SQL和最终回滚SQL均在隔离sandbox的.omc/evidence/，权限仅当前用户可读。

## 源码PR交付范围

用户已授权提交Gitee PR；包含最高分列策略、真实回归、SpecKit和lesson419，保留运维恢复事实说明。一次性数据库工具/SQL/备份只在本地，不作为版本发布动作。源码评审与构建门禁重新执行；未授权合并或部署。
