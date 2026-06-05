# Formal UAT Signoff

## Purpose
把自动化 UAT、恢复演练和业务侧签字放到同一份交付包里，形成正式上线前的 Go / No-Go 证据链。

## Required Inputs
- 候选版本号或提交号
- 最新自动化 UAT 报告
- 最新恢复演练报告
- 业务负责人、销售代表、财务/资源代表、QA、技术负责人签字

## Execution
1. 触发 `Staging Gate` 工作流，输入 `release_candidate` 和 `uat_owner`
2. 等待工作流完成并下载 `staging-gate-artifacts`
3. 打开 `docs/reports/formal-uat-signoff-*.md`
4. 由业务负责人组织正式 UAT 走查
5. 在签字表中填写 `Go / No-Go`、日期、备注
6. 将签字后的文档回传到发布记录或工单系统

## Mandatory Signers
- 业务负责人
- 销售代表
- 财务/资源代表
- QA
- 技术负责人

## Decision Rule
- 任一签字角色给出 `No-Go`，则版本不得进入正式上线窗口
- 若自动化 UAT 或恢复演练为失败状态，也不得进入正式上线窗口
