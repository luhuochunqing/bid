# 本次PR交付门禁的限域处理

用户在当前会话明确同意：仅处理4项已知测试隔离与90项基线失败正式豁免，再复核PR就绪；不扩大业务修复。本文件与delivery-waiver.json记录该授权，不是代理自行审批。

## 四项测试隔离
方法级EnabledIfSystemProperty，仅当 `-Dxiyu.responsiveness.known-defects=true` 执行。没有删测试、改断言或全局排除；默认15项中11项执行、4项报告跳过。四项问题仍未修复，由本次用户作为范围决策人确认保留；见delivery-waiver.json的精确方法列表。

默认验收：
```bash
mvn -f backend/pom.xml -Dtest=Report147ResponsivenessRegressionTest test
```
显式复现（预期4项失败，退出1，不作为修复成功）：
```bash
mvn -f backend/pom.xml -Dxiyu.responsiveness.known-defects=true '-Dtest=Report147ResponsivenessRegressionTest#missingTenderCannotTurnAnUnattributedRuleIntoAFailure+bidderAcknowledgingAProhibitionIsNotADeviation+bidderSuppliedRequirementMustNotReplaceTenderRequirement+bidderValueIsNotMatchedBySubstringOfDifferentTenderValue' test
```

## 90项基线失败正式豁免
审批来源为上述用户授权；用途仅当前分支当前候选的PR就绪审查。原始全后端11444项90失败、0错误、18跳过，退出1，绝不改写为绿。未改HEAD复跑10类205项，90项失败身份与候选完全相同。精确(classname,testcase name)清单见baseline-failure-identities.json，日志摘要SHA和生产/验收测试SHA见delivery-waiver.json。

豁免不修改Maven配置、不使用failure-ignore、不排除任何评分解析测试。只接受清单中的既有90项，任何新增失败、验收缺失/新增跳过、源码或测试SHA变化均失效。基线或HEAD改变须重新复核；到2026-11-07、PR合入或放弃，以最早者失效。此记录不授权合并，不适用于测试/生产部署放行，不替代同文件整链路E2E。审批对象是允许本次PR带已披露基线异常进入审查，不是声称产品没有其他缺陷。

## 本轮证据复用边界
生产源文件与原11444项回归时完全一致；本轮仅4个测试方法增加条件，其他测试体与断言逐字不变。故重新运行受影响的15项默认入口和显式4项入口；原排除该类的11444项回归不受本轮条件注解影响，保留原退出1和HEAD对照，不无变化重复全量30分钟测试。最终独立审查绑定当前SHA并另记录结论。
