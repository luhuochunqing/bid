# Verification

在此任务目录执行 `mvn -f backend/pom.xml -Dtest=ScoreFactChunkMergerTest,SourceRecheckPlanTest,BaiduOcrFailureDiagnosticsTest,BaiduOcrConfigurationTest test` 保存RED/GREEN日志。
完整入口测试与影响域命令、真实PDF hash、反事实日志记录到本地 `.runtime/score43`，对外只记录脱敏计数与证据路径。禁止提交PDF、日志中的请求信息或密钥。
