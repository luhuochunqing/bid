# Validation

前提Java21和本机Maven缓存；不启动服务。

1. `mvn -o -f backend/pom.xml -Dtest=Quality1360UnblockTest test`：原基线。
2. 迁移/结构阶段：`mvn -o -f backend/pom.xml -Dtest=Quality1360BusinessContractTest,QualityHtmlEvidenceContractTest,QualityVerdictParsingContractTest,SemanticHeuristicCorpusTest -DexcludedGroups= test`。
3. 默认门禁：`mvn -o -f backend/pom.xml -Dtest=Quality1360BusinessContractTest,QualityHtmlEvidenceContractTest,QualityVerdictParsingContractTest,SemanticHeuristicCorpusTest test`，报告不得出现语料。
4. 最终最小范围契约与compliance域回归、架构门禁；显式语料差异只记录，不驱动补规则。
5. 完整审查命令见执行计划，最多三轮；触发熔断即停止。
