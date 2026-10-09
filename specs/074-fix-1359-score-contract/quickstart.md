# Verification

```bash
mvn -f backend/pom.xml test -Dtest=Project1359ScoringContractTest
mvn -f backend/pom.xml test -Dtest=GoldenCasesReplayTest
npm run eval:scoreparse:report
npm run build
mvn -f backend/pom.xml test
```
数据库提交命令只在审查通过且备份验证后执行；验收记录真实项目SQL/API输出，不把期望100写成实测100。

一次性1359数据库恢复已完成；Python运维工具、原始快照和SQL保留本地，不在源码PR中，也不属于本清单的可运行步骤。历史恢复及未部署边界见acceptance.md。全量后端已有失败必须单列，不当作绿色。
