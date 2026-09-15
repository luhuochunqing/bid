# Quickstart: 商机预测实时收件人与历史补发

## 1. 定向红绿验证

```bash
cd backend
mvn -q -Dtest=OpportunityPredictionAlertMessageTest,OpportunityPredictionHistoryReplayServiceTest,OpportunityPredictionHistoryReplayTaskTest,OpportunityPredictionReminderTransactionBoundaryTest,OpportunityPredictionRecipientPolicyTest,OpportunityPredictionRecipientResolverTest,OpportunityPredictionReminderServiceTest,OrganizationDirectoryHttpGatewayTest test
```

预期：历史候选只包含 `predictedBidDate <= cutoff` 且 `notifiedAt < runDate 00:00` 的记录；完整实时接收人去重；preview 零写；同项目重跑不再新建；启动事件永远只预演；正常提醒与事件发布测试保持通过。

## 2. 架构与完整回归

```bash
cd backend
mvn -q -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest test
mvn test
cd ..
npm run build
bash scripts/pre-push-gate.sh
```

完整测试若命中已知基线失败，必须同时给出定向测试通过证据、失败用例和与本改动无关的证据；不得把基线失败写成通过。

## 3. 测试环境启动 preview

按 [operations contract](contracts/operations.md) 和 `xiyu-server-deploy` SOP 部署到测试服务器 `172.16.38.78`，仅注入：

```text
XIYU_OPPORTUNITY_HISTORY_REPLAY_ENABLED=true
XIYU_OPPORTUNITY_HISTORY_REPLAY_MODE=EXECUTE
XIYU_OPPORTUNITY_HISTORY_REPLAY_RUN_DATE=2026-09-16
XIYU_OPPORTUNITY_HISTORY_REPLAY_CUTOFF=2026-09-16
```

服务启动完成后必须先出现 `mode=PREVIEW` 汇总，且 notification、user_notification、reminder log、事件状态均无写入。预期历史候选 274、未来项目 324 不进入候选；实时角色人数和接收关系数以本次 OSS 快照为准。任何候选差异先停止 09:00 执行并解释。

## 4. 测试环境 09:00 execute 与复核

1. 核对 09:00 日常扫描与历史补发各自只有一次开始/结束日志。
2. 历史补发新增通知的来源必须为 `OpportunityPredictionHistoryReplay`，每个项目最多一条，接收人为当时在职项目经理与两个目标角色的去重并集。
3. 核对原 `opportunity_prediction_reminder_log` 行数、`event_published_at` 和领取字段均未被补发改变。
4. 分开统计站内通知创建与企微 delivery task/outbound log；企微失败不得冒充站内未创建。
5. 立即用相同参数再做 preview，预期可新增通知数为 0，已补发跳过数等于首轮成功项目数。
6. 测试验证完成后把 `XIYU_OPPORTUNITY_HISTORY_REPLAY_ENABLED` 设回 `false` 并重启确认任务不再运行。

本 quickstart 不授权任何生产部署或生产补发；生产必须重新确认截止日、preview、备份和风险审查。
