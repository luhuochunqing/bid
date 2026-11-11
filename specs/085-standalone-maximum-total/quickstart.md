# 验证

Java21与Maven；从隔离任务目录运行。

```sh
cd backend
mvn -Dtest=ScoreTableSummaryPolicyTest,StandaloneMaximumSummaryTest,GoldenCasesReplayTest test
```

根目录运行`npx vitest run src/composables/projectDetail/useScoreCandidateAdoption.spec.js`和`npm run build`。期望明细100，无额外满分项。线上重解析不属于本次。
