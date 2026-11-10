# Validation

`mvn -o -q -f backend/pom.xml -Dtest=SemanticHeuristicCorpusTest,Quality1360BusinessContractTest,QualityHtmlEvidenceContractTest,QualityVerdictParsingContractTest,CertificateEvidenceScopeContractTest -DexcludedGroups= test`

修期望与生产重构前后均应778项全绿；默认不带excludedGroups覆盖时93项，语料仍非默认门禁。范围限定无服务启动。最终日志、哈希及审查由analysis.md与私有Git review-gate记录。
