---
title: 项目1123满分汇总行重复计入候选导致200分
space: engineering
category: lessons
tags: [scoreparse, summary-row, production]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableSummaryPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/DeterministicScoreFactExtractor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - src/composables/projectDetail/manualScoreCandidate.js
  - src/composables/projectDetail/useScoreCandidateAdoption.js
backlinks:
  - .wiki/pages/_index.md
created: 2026-11-11
updated: 2026-11-11
health_checked: 2026-11-11
archive: true
---

# 项目1123候选200分根因

## 现场与状态

- diagnose，只读取业务实现及线上接口；未修复、未重解析、未采纳、未部署。
- 本地HEAD：57164cb98a95477bd2d11f86006759968b605e8e；生产部署记录releaseId：2960e7ea5。核心汇总识别、事实提取、总分解析和人工候选过滤文件与该commit无差异；未反编译运行JAR。
- 生产项目1123，task220 / d7cc647d-48ec-42aa-a785-2eaff2127d75，2026-11-11 16:30:58至16:38:43，COMPLETED / REVIEW_REQUIRED / WEIGHT_SUM_MISMATCH。
- review接口：24条事实，单方案“第三章 评审办法（综合评估法）”，表baidu_tbl_28_0、baidu_tbl_29_0，UNKNOWN_TOTAL。
- 12条正分明细：14+12+8+14+12+4+4+4+7+8+4+9=100；11条零分审查事实；另有一条“满分”100，合计200。
- 重复项fact-e6a93a9f：code=满分，weight=100，sourceText=满分 | 100，来源baidu_tbl_29_0_r16_c2；名称错误显示为结算服务能力及无息账期。
- 当前下载文档341的PDF物理32页（印刷31页）明确列出满分100。未取得任务输入文件哈希，不声明字节级同源。

## 致错链

1. ScoreTableSummaryPolicy.isSummaryLabel仅接纳合计、总计、合计得分、总计得分，未接纳单独“满分”。hasTotalMarker虽包含满分，但仍要求汇总标签成立。
2. DeterministicScoreFactExtractor跳过汇总行失败，按显式权重列生成100分普通事实。线上快照已证明错误在提取后的事实阶段出现，并非前端算术相加错误。
3. OriginalScoringContext的结构化汇总行标签也仅匹配合计/总计；总分证据未建立，页面错误提示原文未声明统一总分。
4. 该事实candidateEligible=false、scopeStatus=SOURCE_UNVERIFIED。人工复核过滤isManualScoreCandidate允许同方案且非OUT_OF_SCHEME事实，仍纳入24条人工候选；effectiveCandidateWeight将其按无父级普通事实加到明细100分上。
5. UNKNOWN_TOTAL提示不阻止人工采纳，不能将警告视为重复分值已被隔离。

## 区分实验

- 隔离Java编译当前ScoreTableSummaryPolicy，以同样四列网格仅切换标签：满分→summary=false、facts=1、declared=[]；合计→summary=true、facts=0、declared=100。
- Node加载真实manualScoreCandidate.js，对生产review快照运行：24项、200分、summaryIncluded=true；只读排除code=满分后100分。
- 取证记录/tmp/xiyu-1123-diagnosis/evidence.json通过技能check_evidence.py --phase diagnose机械检查。原始PDF/接口快照仅保留在本机临时目录，不入库。

## 影响与最小修复边界

- 应统一汇总行语义，使表尾满分100成为总分证据并退出评分事实；不能只将页面总数硬改100，也不能简单排除全部SOURCE_UNVERIFIED，否则会丢掉合法跨行明细。
- 应覆盖汇总排除、声明总分识别及前端真实候选计算的回归。此处为诊断建议，尚未实施。
- 已生效items是另一批15项，合计108；未追溯其历史生成原因。修复候选200不代表旧正式项已恢复正确，后续需分别验收。

## 关联

- [[_index]]
- [[scoreparse-1119-multiple-package-review]]

## 后续本地修复（2026-11-11）

- 新规格、计划和任务：specs/085-standalone-maximum-total/；本地working_tree范围，不提交/推送/部署。
- 共享精确汇总标签补独立“满分”；OriginalScoringContext复用规则，排除无数值的“满分”权重列表头。三技能审查发现直接排除整个启发式表头范围会丢失无表头“合计 | 满分100分”表格总分；有明确数值证据的汇总行改按无表头规则解析。
- 新回归覆盖明细40/60与汇总100、两物理表总分、合并汇总格、正常单项满分与满分列表头；金标通过实际网格提取与原文总分解析，前端仅测试计分契约。
- 验证和审查原始工件在.omc/evidence-score1123及.omc/verify-score1123.md、.omc/review-score1123.md；历史诊断结论保留，上线数据未被本地改动重算。
- 本轮新增headerlessSummaryRemainsSourceTotalBesideDetailTable：同方案40/60明细与独立汇总表，覆盖合计/总计/满分，断言总分100、来源数值格及原文引用。修复前三例均因总分为空失败；本轮独立复审由主执行器继续调度，旧审查工件不证明本轮通过。
