---
title: 跨标书评分项稳定提取的代码缺口审查
space: engineering
category: lessons
tags: [scoreparse, extraction, coverage, code-review]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceRecheckPlan.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreTextChunker.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreFactChunkMerger.java
  - document-converter-sidecar/table_extractor.py
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-04
updated: 2026-10-04
health_checked: 2026-10-04
archive: false
---

# 稳定提取评分项的源码审查

- 审查本地HEAD 0a1a29b09，与已缓存origin/main 501b5e160比较；生产代码仅复核异常日志变化，以下主链判断适用于两者。未验证远端最新版本或当前服务器运行配置，未运行新模型或测试。
- ScoreParseAppService:164按模型是否存在、版本开关路由，默认关闭开关时LegacyTextScoreExtraction:17先纯文字模型抽取，:19后获取来源。新路径MultimodalStructuredExtractionHandler:79先带来源提取，:168绑定与校验。不能把代码存在等同于线上已启用。
- table_extractor.py:126只遍历Word正文直接子块；:99的textBlocks为空，段落仅用于标题路径；:629拼接所有w:t，段落边界和嵌套表结构不能由此完整保留。PDF有独立来源和OCR路径，不能说系统没有PDF/OCR能力。
- ScoreTextChunker:22按字符容量与换行切分，重叠不是完整逻辑评分项边界保证。
- ScoreFactChunkMerger:42键含名称、规则、引文、来源等全部字段，仅精确去重；模型对同一来源的不同表达可能保留多项，不能视为逻辑来源合并。
- MultimodalScoreFactValidator:70检查字段、证据、重复、编号、父子及:83总分闭合；SourceRecheckPlan:26有无owner锚点回补，但:30匹配特定分值文字，:18最多8锚点，且只有校验未通过等条件才触发。不是评分区域每个候选单元均被处理的覆盖证明。
- GoldenCasesReplayTest:77由预期构造部分事实，Project1382FullPipelineIntegrationTest:61加载事实快照；这些用例能验证下游校验，不足以证明任意原件真实模型召回稳定。现有其他测试未全量审查，未宣称全库无端到端测试。
- 修复方向：补统一原文单元与段落/嵌套/续表边界；结构感知分片；按来源身份归并、保留冲突；评分区域建立所有单元的处理状态和未覆盖队列；实际完整链路人工标准答案与重复运行验收。引用解析仅在决定评分字段时参与。
- 跨项副作用：完整覆盖不能靠输出自证；总分相等不证明逐项正确。不能承诺任意标书100%自动准确，只能给定义格式和结构范围的验收保证，并让未支持结构可见地进入复核。
- 相关：[[_index]]。
