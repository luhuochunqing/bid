# 验收
## 本地
`mvn -f backend/pom.xml test -Dtest=EstimatedScoreServiceTest,ScoreEstimateRefreshMysqlTest,ScoreEstimateFreshnessTest,CertificateScorePolicyTest,WarehouseRegionalScorePolicyTest`
`npx vitest run src/composables/projectDetail/useScoreEstimateRefresh.spec.js src/views/Project/stages/components/ScoreParseDrawer.spec.js src/api/modules/scoreParse.spec.js`
`npm run build:api`
真实MySQL与浏览器记录位于隔离工作区.omc/mysql-evidence.md、browser-evidence.md。测试容器为专用schema，不复用开发/生产数据库。
## 授权发布后的现场验收（尚未执行）
1. 记录部署SHA与1117/1119当前评分项ID、完整规则、候选快照、score_result；冻结前后对比范围。
2. 进入标书制作→AI评分标准解析，折叠第一部分，确认刷新按钮仍可见。
3. 点击更新知识库预判，核对POST refresh摘要、GET items、数据库已提交字段、UI逐项一致；0分计入数值得分，空项不可显示成功。
4. 1119业绩进入真实匹配，新依据不能沿用主观项不计分；无合格业绩时明确缺失约束或记录原因，不保证满分。
5. 1117证书/业绩/六区域仓储全部重新计算；横向排序的未决分值只说明待评比，不伪造。
6. 财务/信用缺少自动评估底表时，摘要无分值并逐项说明。对比候选和实际评分全行/完整载荷无变化。
7. 记录业务接受结果，未满足即返回修复，不用HTTP200或部署健康替代业务验收。
