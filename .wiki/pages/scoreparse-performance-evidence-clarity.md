---
title: 业绩预计得分证据可信度与中文解释契约
space: engineering
category: guide
tags: [评分解析, 预计得分, 业绩证据, 保守计分, 中文解释, spec067]
created: 2026-09-21
updated: 2026-10-25
health_checked: 2026-10-25
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ProjectEvidenceRequirementPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/KnowledgeCategoryPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/IncrementalCountScorePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/match/ProjectMatchService.java
  - src/views/Project/stages/components/ScoreItemDetailModal.vue
  - specs/067-score-evidence-clarity/spec.md
backlinks:
  - _index
  - score-parse-service
---

# 业绩预计得分证据可信度与中文解释契约

> **一句话**：知识库检索到候选业绩，只能证明“存在可能相关的记录”，不能直接证明该记录满足金额、日期、合同、付款流水和发票等全部评分条件；必要条件无法验证时必须保守计分，并向用户清楚说明缺口。

## 用户可见问题

项目 1117 的“公司业绩”显示 30/30，但详情只给出“知识库命中”和内部字段，案例库又无法检索到可对应的业绩。用户无法回答三个核心问题：命中了哪条记录、该记录满足了哪些招标条件、为什么足以获得满分。

## 根因链路

1. **约束提取缺口**：合同金额提取失败后，请求省略金额过滤，候选记录仍会返回；“每提供一个”中的中文数量词未被识别时，所需数量回退为 1。
2. **事实层级混淆**：`performance_record` 是候选来源，不是“所有条件已验证”的结论。付款流水、发票等材料无法从普通业绩记录自动证明。
3. **解释层泄漏内部协议**：来源码、匹配层级、状态枚举直接显示在页面，既不易懂，也掩盖了待核验条件。
4. **阶梯计分最高分正则边界（项目 22）**：`每增加1个得1分，最高得7分` 包含逗号 `，`，原正则 `[^，。；;\n]*?` 无法跨逗号匹配最高分，导致所需数量未能正确解析为 7 份而是回退使用大项权重 15 分。优化后放行逗号/空白衔接。
5. **首项零分与项目类型泛化（项目 22）**：规则实际要求 1 项不得分、之后每增加 1 项得 1 分、8 项才满 7 分；同时“办公用品”必须收敛为 `OFFICE`，不能与普通“业绩/合同/案例”关键词做 OR 查询，否则无关工业业绩会造成虚假满分。

## 修复后的判定契约

| 层级 | 能证明什么 | 不能据此推导什么 |
|---|---|---|
| 候选命中 | 知识库存在可能相关的业绩记录 | 该记录已满足全部评分条件 |
| 条件验证 | 金额、日期、品类或材料中的某项已有明确证据 | 未验证条件自动视为满足 |
| 计分结论 | 全部必要条件和数量要求均有证据支撑 | 用候选数量替代有效业绩数量 |

规则包含金额、日期或人工佐证材料时，任何必要条件无法可靠解析或自动验证，都应返回“待核验”，不得自动满分。中文“一个”等数量表达按明确数量解析，不再以默认值掩盖失败。

## 面向用户的解释

详情弹窗统一用中文展示：证据来源、匹配范围、匹配记录、已验证条件、待核验条件和计分结论。内部枚举仍可保留在接口与程序契约中，但必须在展示层映射为清晰中文，不得直接暴露给业务用户。

## 回归与生产边界

- 后端规则测试覆盖金额缺失、中文数量、人工佐证和保守计分；前端测试覆盖内部字段中文化与依据分层展示。
- 黄金样例 `case-1117-performance-evidence` 固定项目 1117 类评分条款的单项结构和 100 分总分。
- 项目 22 的 PDF 换行形态固定在 `case-project-1382-pdf-real-shape`：分类必须为客观项，满分所需数量为 8，8 条合格业绩得 7/7；明确项目类型存在时，匹配查询必须使用枚举条件收敛。
- 测试环境浏览器验收需同时覆盖阶段 1 与阶段 2：阶段 1 显示知识库命中、预计 7/7；阶段 2 使用同一生效评分版本和投标 PDF，实际得分 7/7。旧版本结果不能替代当前版本验收。
- 本次变更不自动改写历史预计得分，也不触发生产项目重算；存量项目需在后续获得明确授权后重新执行评分，才能生成新契约下的结果。
