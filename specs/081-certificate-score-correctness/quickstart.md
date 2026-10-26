# 验证入口
1. mvn -Dtest=CertificateScorePolicyTest,CertificateScoreMysqlTest,ScoreEstimateRefreshMysqlTest test（backend目录）。
2. pnpm exec vitest run src/composables/projectDetail/useScoreParseDrawer.spec.js src/views/Project/stages/components/ScoreParseDrawer.spec.js。
3. npm run build；backend执行受影响域测试和ArchitectureTest。
4. 真实本地API中使用历史证书评分项：读取STALE→点击独立更新→读取FRESH与逐证书证据；核对数据库、候选表和阶段2不变。生产1117不作写操作。

## 本地验收记录（2026-10-26）
- 最终后端受影响回归726项零失败/错误/跳过；含纯核心、真实MySQL证书/刷新、旧分隐藏、候选/阶段2不变、并发CAS及架构测试。独立审查在人工确认路由契约后通过；前3轮失败记录用于修复反例，未跳过门禁。
- 前端57项与npm build通过。
- 实际Drawer组件→真实HTTP controller/application→JPA/MySQL隔离验收：历史三ISO10与企业荣誉5均隐藏；刷新三ISO10/OK、未知企业荣誉null/PENDING，汇总10；退役ISO45001刷新0/DANGER、荣誉仍null/PENDING，汇总0。保护候选与阶段2逐字不变。
- 三ISO完整原文和四荣誉完整原文回放通过；未知额外要求/显式ISO版本/人员培训证不误判满足。明确证书维度不依赖动词或细则语法。
- 迁移V1225/U1225在真实本地MySQL重复forward/rollback通过；只增加nullable列，不重写历史评分。
- 此证据为隔离组件harness，不是完整生产项目页或生产UAT。没有部署或线上重算。证书全库重复读取的性能基准尚未进行。

后端重现：在本机创建专用cert_score_test_* MySQL schema后执行：
```bash
mvn -q -f backend/pom.xml -Dcertificate.test.mysql.url=jdbc:mysql://127.0.0.1:3306/cert_score_test_local -Dtest='com.xiyu.bid.scoreparse.domain.*Test,CertificateScoreMysqlTest,ScoreEstimateRefreshMysqlTest,CertMatchServiceTest,EstimatedScoreServiceTest,ScoreEstimateReadRegressionTest,ScoreParseDualWriteTest,ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest' test
```
MySQL测试使用真实持久化；未提供专用本机schema时走Testcontainers，需要Docker。
