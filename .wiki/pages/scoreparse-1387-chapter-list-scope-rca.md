---
title: 项目1387章名列举误识别与分组总分阻塞
space: engineering
category: debugging
tags: [scoreparse, docx, source-evidence, scheme, diagnosis]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/LogicalRowEvidenceBinder.java
  - e2e/score-parse-candidate-review.spec.js
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestoration.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceSpanResolver.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreReviewAdoptionPolicy.java
  - src/views/Project/stages/components/ScoreParseTable.vue
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/EvidenceText.java
  - src/components/project/detail/ProjectDetailMainColumn.vue
  - src/views/Project/stages/components/ScoreParseDrawer.vue
  - src/views/Project/stages/components/ScoreCandidateReviewCard.vue
  - src/views/Project/stages/components/ScoreExtractionPanel.vue
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringTextScopePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/GroupedScoreTotalPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnit.java
  - docs/lessons/lessons-learned.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalExtractionReviewPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/StructuredTableExtractionHandler.java
backlinks:
  - .wiki/pages/_index.md
  - .wiki/pages/scoreparse-multimodal-primary.md
created: 2026-10-12
updated: 2026-10-13
health_checked: 2026-10-25
archive: false
---

# 项目1387章名列举误识别与分组总分阻塞

## 诊断状态

- 环境：测试172.16.38.78，部署记录release `0f18887de-api8080`。诊断取证阶段未修改服务器代码/数据或触发重新解析；后续本地修复见下文，仍未部署。
- 任务310、311、312均完成但 `REVIEW_REQUIRED / ORIGINAL_TOTAL_UNKNOWN`，每次14条事实，正式评分项0。第三次模型总分100仍被拦截。
- 客户原始文件不入库；本地忽略证据目录 `.runtime/root-cause/1387-20261012/` 保留原文、部署类、日志、数据库资格和探针。`evidence.json` 通过技能机械校验；该校验不证明修复完成。

## 已证实因果

| 边界 | 观察 | 机制 |
| --- | --- | --- |
| 原Word→测试Sidecar | 45465字符，无转换告警；t-0005有商务30、技术40、价格30 | 文件读取与评分表提取正常 |
| 文字章节→方案范围 | 招标文件组成列举中的第三章位于零基行362；真实章节行598；评分行643–656 | SOURCE_HEADING将独立章名列举当一级标题；同名root去重不记录真实章节；伪root在行364第四章处结束，方案外范围延伸至文末1672 |
| 范围→候选资格 | task311全部14条资格OUT_OF_SCHEME/ITEM_SCHEME_SCOPE_MISMATCH | 带text:line[644]等文字定位的评分项被错误章节范围排除；缺定位候选还有独立证据不足，不宣称本缺陷解释全部绑定问题 |
| 原文→总分 | 原表有30/40/30分组，无显式总分合计行；对照修正范围后仍total=null | 当前规则未将本分组表识别为可验证总分证据；是否允许有证据求和需确认契约，禁止采用模型总分替代 |
| 发布→前端 | 正式项0，实际打分禁用 | 发布门禁保留旧正式项；本项目没有旧项，候选不等于正式项 |

## 对照实验与边界

- 用测试部署jar抽取的原始domain class、Java21及同一Sidecar产物执行Probe。
- 隔离文本仅替换正文六个章名列举，保持评分表和artifact不变。
- 原输入评分行644为OUT_OF_SCHEME；对照输入相同行内容移到634，变为IN_SCHEME。两条断言通过，进程exit0。
- 两组原文总分均null，证实总分阻塞独立于范围误判。
- 未获取模型原始响应；后续本地源事实回放见下文，不等同于真实模型及落库验收。仅修章节范围不足以自动发布。

## 后续最小边界

- 区分章名列举与真正章节，处理重复同名标题的真实起点；正常章节、目录、合同考核、其他标包必须保留边界保护。
- 对同一原文评分表的分组与子项建立可验证总分约束，避免父项与子项重复求和。
- 对照真实DOCX入口补回归，并验证候选原文定位、方案资格、总分、正式发布及UI；不得预填正确scheme/status替代入口验证。
- 相关：[[scoreparse-multimodal-primary]]。


## 本地最小修复（working_tree，未部署）

- 章节根替换4行，保留真实非评分边界；同表三组完整且子项闭合才求和。
- 必要第三依赖：无序号名称/分值表的逻辑行被旧ID＋名称守卫拒绝，导致FULL_RULE_UNIT_UNRESOLVED、sourceText空；补互斥新路径，保持旧ID契约及匿名续行拒绝。
- 无歧义完整原文规则的事实回放用于验证12个100分发布候选与发布证明；该证据不等同于真实LLM重新解析或线上数据库/UI验收。
- 原始全文标量回放成功；撤回任一章节/总分/无序号逻辑行修复断言重新失败。原始文件、输出和部署class位于任务区忽略的.runtime/root-cause/1387/，不入库。
- 关联根因和约束追加到docs/lessons/lessons-learned.md §429；短名称全文重复时仍拒绝歧义绑定，不放宽发布守卫；最终测试结果以.omc/verify-1387.md为准。

## 人工复核入口（2026-10-12）

项目详情曾固定 `show-extraction-review=false`，造成状态提示与可操作内容分离。恢复已有面板显示全部提取候选和原文，管理操作复用 `allowExtractionActions`；历史候选卡使用只读属性。已知原文总分不足/超过提示人工核对，未知总分不计算差额。此改动不提升候选来源资格，不自动采纳，不新增手工补录；真实解析与上线验收仍须单独验证。

## 任务313价格30分原文绑定（2026-10-12）

13条方案内候选为商务30+技术40；第14条价格30分被排除，locator为空。价格完整模型引文的乘号为 `*`，Markdown原文为 `\*`，`EvidenceText`此前未处理该布局转义。最小纯核心处理星号转义，不改变资格/总分/发布守卫；本地回放定位恢复到 `text:line[656]` 且资格IN_SCHEME，完整规则恢复仍为空，尚非正式发布或线上验收。证据只留忽略.runtime，原始客户文件不入库。

## 2026-10-12 生效项缺原文的后续取证（未修复）

- 在基线 `c3bce5519b145b41a0c8edb381226e4f2a1e9f7d`，同一已归档原标书及价格引文回放：`boundMetadataSpans=1`、`artifactResolvedSpans=0`、`locator=text:line[656]`、`scope=IN_SCHEME`、`eligible=true`，但 `restoredRuleLength=0`。文本定位保存在事实元数据，恢复器重新解析物理artifact引用不能取得该文本行；该证据仅证明此输入的恢复断点，不证明截图最新任务完全相同。
- 隔离纯核心采纳探针：空 `sourceText`、5分候选得到 `success=true,total=5,detail=—`；“空原文必须拒绝生效”的断言实际失败、退出1。权重合计与完整评分规则是独立条件，人工采纳成功不能证明原文恢复完整。
- 前端 `ScoreParseTable` 直接展示 `item.code`，`normalizeScoreItem`仅清理空/null码，不把业务标识转换为展示序号。不能为了改展示而覆盖内部标识及结果关联键。
- 证据在任务隔离目录 `.runtime/score-rule-evidence/`，客户原文不入库。最新12项所属任务、采纳动作与接口字段尚未取得：SSH握手多次超时，网页可访问但调查浏览器未登录。未修改业务源代码、未重解析、未采纳、未部署；须补齐最新链路再确定最小修复。

## 最小修复候选：闭合物理行恢复，不以文本引文冒充完整规则

既有LogicalRowEvidenceBinder增加完整Markdown整行与同一物理表闭合逻辑行的唯一精确匹配；保留物理cells，原文名称从名称列恢复，既有OriginalRuleRestoration取得完整规则。重复/篡改/跨方案/未闭合反例拒绝。采纳纯核心拒绝空白或历史—占位；前端对纯英文/下划线标识显示行序号，保留内部code与数字编号关联。未修改全局归一化、总分门禁或现有数据。

原标书固定输入回放：价格模型引文不变，修复后3个物理cell、方案内、候选有效、完整规则424字符；同表12明细行生成完整Markdown引文的结构回放12/12原文完全一致，可采纳100分。这不是新模型调用或最新线上12项验收。最新任务仍待测试部署后的在线取证。

## 2026-10-13 引文格式对照诊断（源码缺陷已复现，最新任务待验证）

- 版本 `ef704cfe6`；同一归档artifact的t-0005行2/14，固定分值和商务/价格维度，仅改引文格式：完整Markdown行恢复100/424字符；同一行去首尾管道、名称分值短引文均恢复0字符；唯一名称引文恢复100/424字符。
- 机制：LogicalRowEvidenceBinder新增物理整行路径依赖首尾管道。失败后TextPrimaryEvidenceBinder保存text:line和真实metadata span；OriginalRuleRestoration重新通过SourceSpanResolver查artifact物理cells/textBlocks，不消费metadata文本span，最终空规则。不能直接把短span冒充完整规则，也不能全局删除公式中的绝对值管道。
- 反证：SUM空规则父项+完整子项可采纳；空规则子项被拒绝；计分父项MAX为空时被拒绝。因此截图商务父项空白不能独自解释拒绝原因。
- 逃逸：上轮AllSourceRowsProbe生成完整行引文、跳过分组父项且父关系为空，未覆盖真实模型引文格式与父子结构。
- 执行：完整规则一致性8断言=4通过/4失败/0错误/0跳过，exit1；父项采纳3断言=3通过/0失败/0错误/0跳过，exit0。缺陷相关源码重编译，辅助依赖来自既有target；未执行真实模型、DB/API/UI验收。
- 第226次测试部署报告记录ef704cfe6已部署，但未实时核对运行指纹；截图projectId/parseTaskId尚未确认。已证实当前源码缺陷，不声称截图唯一根因已闭环。
- 证据与重现：忽略目录 `.runtime/root-cause/score-rule-recheck-20261013/`，执行 `python3 .runtime/root-cause/score-rule-recheck-20261013/run_probe.py`。原始客户文件不入库，未修改业务代码或服务器数据。

## 2026-10-13 测试现场续证（task315）

- 用户确认test后只读SSH172.16.38.78：运行ef704cfe6-api8080/PID6017/07:52:48启动。最新task315于09:25:51创建，UUID5c8b801e-0696-4ad2-bc50-e873e59114b2；09:31:18完成，facts14，originalTotal/modelTotal/leafTotal均100，violations10，REVIEW_REQUIRED/ITEM_WEIGHT_SOURCE_UNKNOWN。
- 14条原文长度全0，14条资格eligible=true，定位text:line[643..656]；14条实际保存引文均无首尾管道。首条商务引文与截图相同。09:37:43及09:37:57两次采纳POST均400；日志未打印响应体，不冒称取得实际错误正文。
- 真实保存引文保留父子结构本地回放：12有效计分项，完整规则0；仅补首尾管道对照后完整规则12。2对照断言通过，exit0，证明格式限制的因果；未修改业务代码，不等于修复完成。
- 本轮未取得原始LLM响应、采纳请求/响应体，未重新下载标书核对SHA；回放复用归档同45465字符标书和artifact。未触发解析、采纳、部署或修改服务器数据。
- 原诊断记录已追加实时证据与SHA256，位置 `.runtime/root-cause/score-rule-recheck-20261013/evidence.json`。

## 第三次批准修复的边界（2026-10-13）

- LogicalRowEvidenceBinder局部比较完整、缺首、缺尾、缺首尾四种表格布局，保留内部cell内容及公式管道、同表同方案唯一闭合约束。表格引文零匹配仍保留跨cell逐字精确引用；精确路径也失败后不得按名称/分值重建，真实名称维度的篡改反例已先RED。
- MultimodalScoreFactValidator纯核心接受同一引用cells的换行表示，保留无分隔匹配和既有校验；MRO规则开头的假QUOTE_NOT_VERBATIM有RED回归。
- 任务315人工采纳与自动发布分开验收：12有效规则/100分采纳，未验证父项权重不能强行自动发布。最终fresh验证结果以任务.omc/verify-score-row-quotation.md为准；当前未部署，旧任务不会自动恢复。
