---
title: 评分引用单入口实验及新文件来源泛化失败
space: engineering
category: lessons
tags: [scoreparse, references, activation, docx, retrieval, generalization]
sources:
  - AGENTS.md
  - .wiki/WIKI.md
  - .wiki/pages/scoreparse-structured-retrieval-experiment-20261003.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-03
updated: 2026-10-03
health_checked: 2026-10-25
archive: false
---

# 评分引用单入口实验及新文件来源泛化失败

## 结论和实验边界

- phase9已知焦点PASS，新文件泛化NOT_PASSED。实验目录`/Users/user/xiyu/experiments/scoreparse-realfile-20261003/phase9-entry-constraints/`；完整证据在`results/report.md`、`results/summary.json`。
- 固定phase8的9份来源、28个入口、检索索引和原文体；未改业务代码、配置、数据或评分字段，未重解析或部署。视觉只核对原件，模型图片输入0。
- 延续 [[scoreparse-structured-retrieval-experiment-20261003]]；其219两项缺原文结论需更正：旧校验器用无序集合拼接跨段原文，实际上五段已齐。前轮原响应和文件保持，校验重判记录另存；旧基线应18 PASS/5 FAIL，不是16/7。

## 单入口、角色与完整候选

- 最终23项焦点与34项额外检查均PASS；原24复跑仍17正向PASS、4禁止串项PASS、3未知已报告，包含保留旧DIRECT证据，不是新模型独立完成17项。
- 各入口独立提供原句和自己的目标。共用前附表不能让商务/技术或专用表所有项自动适用；1382两张表与基准价完整对应，未选方法单独INACTIVE；1373商务9/技术4分开，1377引用第10条与目标第9条保持冲突。
- 每code恰有一个分类状态；证据另标APPLICABLE/INACTIVE/CONTEXT/CONFLICT/UNKNOWN。只按模型声明投影适用依据，全部原始角色保留，不能按项目删除错误。合法位置、HTTP200和完整候选键不能证明范围正确。
- 五版本均保留失败：r1漏细则/错误ID，r2仍混未启用方法，r3漏候选状态，r4把1363多个入口混成全文关系；r5每次单入口通过。版本同时改变多因素且每入口一次采样，不作单因素因果或稳定性声明。
- r1至r3公共freeze记录曾被prepare覆盖，事后重建明确标注未倒填；r4/r5调用前独立冻结。目标审计误送评分装配器的编排KeyError恢复时逐字节采用实际PICK原响应，原失败日志保留。

## 新文件真实反例

- 已知门禁通过后重新从服务器读d1109/项目1361 DOCX和d1050/项目223 PDF，ID及原件哈希排除既有API样本；首次抽取21+11项随即冻结，未修字段。
- Word：372个原文单位匹配后，全树w:p遍历进入txbxContent文本框；此前递归采集把其文字聚合进外层段落，顺序/数量不一致。祖先含drawing、Choice、AlternateContent与外层w:p。来源校验BLOCKED，未跳段、重编号或适配本文件。转换物理页60评分表右侧裁切，视觉记录不修模型。这只证明实验适配器缺口，不冒充生产同根因已验证。
- PDF：1094原文单位，关键词策略仅提交183个；独立8个焦点入口找到3个、漏5个。“清单内”“招标文件供货要求”“按本章条款计算”未进入预筛选，关联层没有输入。所有原引用及扫描错误保留，未注入漏掉入口制造通过。
- PDF的14个已找到入口机械合法；7项追加语义6 PASS/1 FAIL。前附表v00001只含物理页23–24商务，v00002另含页25–26技术。技术入口只拿到v00001，模型正确报告缺技术原文但未完整关联；原件确有技术续表，不是附件缺失。

## 验证与下一轮

- 实际尝试100次：98 HTTP200、2 SSH/传输未知后重试成功；总provider token5421766，图片0。已知边界17 PASS、新文件完整性15 PASS；6845基准文件、HEAD/status、前轮来源、模型配置和发布不变；临时凭证已删且无泄漏。
- 字段冻结不等于评分字段真值已验收；外部条款、缺附件、名称/编号冲突继续报告未解决，不把关联正确当闭合。
- 下一轮只实验通用来源约束：Word主/备用分支及文本框形成唯一可回指序列；评分区域所有原文单位进入引用审计；PDF物理网格与逻辑续表分开，具体引用缺范围须PARTIAL。先复验本轮失败和旧正确案例，再换陌生文件，不落业务代码或加项目专属补丁。
- 可复核证据：`evaluation/baseline-measurement-correction.json`、`checks-r5.json`、`semantic-audit-r5.json`、`regression-r5.json`；`holdout/evaluation/d1109-source-blocker.json`、`d1050-results.json`、`source-derived-focal-inventory.json`、`d1050-crosspage-evidence.json`及两份`final-verification.json`。
