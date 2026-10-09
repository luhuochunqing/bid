---
title: LangExtract提取层AB小试未证明替代收益
space: engineering
category: lessons
tags: [scoreparse, langextract, extraction, experiment]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractor.java
  - .wiki/pages/scoreparse-extraction-stability-code-review-20261004.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-04
updated: 2026-10-04
health_checked: 2026-10-04
archive: false
---

# LangExtract真实提取层AB小试

- 外部报告：/Users/user/xiyu/experiments/langextract-ab-20261004/results/report.md。
- 2真实文件d1142/d1167，当前代码自动评分范围的同一MD，gemini-3.5-flash同提供方，各2次运行。A为实际生产SDK提取器，B为LangExtract1.7.0实际提取、2遍调度、合并与对齐；provider仅传输，原答不修补。
- A每轮输出14/13单元，可由原文对应参考且最高分一致。B d1142输出14/15，d1167输出14/14，报价单元父子拆分粒度与参考不一致；没有直接求和作为分值正确性证明。
- B具体名称输出更直接，但定位完整d1142为13/14、15/15，d1167为12/14、11/14；业绩/项目团队续文和第二轮整体方案有partial定位。一个证书extraction_text插入management。A也存在引文改写and等，两者均未通过严格原文门禁。
- 8试验、12HTTP200。A4请求60993tokens/平均43.91秒；B8请求182561tokens/平均137.51秒。不同提示/示例/遍数，非等预算单因素因果实验。
- 参考AGENT_SOURCE_VERIFIED非人工金标准。共享评分范围使本试不验证全文召回、原件转换、现有后处理或DB/UI；A原始dimension常为类别，不能凭原始名称差异推断最终系统名称丢失。
- 8边界检查过，业务与数据库未改，私有运行文件删除、无凭证泄漏。只回填Wiki，无提交部署。
- 跨项副作用：原文定位可部分匹配，存在位置不等于完整标准证据；父子输出粒度与最终分值聚合须区分。小试未证明LangExtract整体更好，不宣布接入成功。
- 相关：[[scoreparse-extraction-stability-code-review-20261004]]、[[_index]]。
