# Quickstart: 商机预测实时收件人与历史补发

## 1. 定向红绿验证

```bash
cd backend
mvn -q -Dtest=OpportunityPredictionRecipientPolicyTest,OpportunityPredictionRecipientResolverTest,OpportunityPredictionReminderServiceTest,OrganizationDirectoryHttpGatewayTest test
```

预期：实时目标角色被识别；离职/缺失人员被排除；同一用户去重；OSS 对象型角色中停用角色不参与解析。

## 2. 架构与完整回归

```bash
cd backend
mvn -q -Dtest=ArchitectureTest test
mvn test
cd ..
npm run build
bash scripts/pre-push-gate.sh
```

## 3. 生产 dry-run

按 [operations contract](contracts/operations.md) 执行只读查询，记录 cutoff、应用 commit、数据库备份、实时角色 ID、128/114 基线及预计新增用户通知数。任何范围差异先停止执行并解释。

## 4. 生产 execute 与复核

1. 调用修复后的正常扫描一次，核对新建 reminder log 与完整通知。
2. 运行角色补发脚本 execute，只补已有提醒项目的缺失核心角色。
3. 立即重复 dry-run，预期可新增数量为 0。
4. 核对站内通知、企微 delivery task/outbound log、应用错误日志和健康端点。
5. 删除临时手动扫描入口并重新部署最终代码。
