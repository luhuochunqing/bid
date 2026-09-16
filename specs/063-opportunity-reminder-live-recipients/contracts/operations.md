# Production Operation Contract

## Inputs

- `enabled`: 默认 `false`；必须显式开启。
- `runDate`: 必填执行日期，任务只在该日 09:00 运行。
- `cutoff`: 必填截止日期，包含当日。
- `mode`: `preview` 或 `execute`，默认 `preview`。

`cutoff` 不得晚于 `runDate`；违反该约束视为无效配置，启动和 09:00 调度都不得写入。

## Trigger lifecycle

1. `enabled=false` 时，启动与调度均不处理历史补发。
2. `enabled=true` 时，应用启动完成后立即用同一候选查询和收件人解析执行一次强制 preview；此步骤不受 `mode=execute` 影响且永远零写入。
3. `runDate` 当天 09:00 触发一次调度：`mode=execute` 才执行写入，否则再次 preview。
4. 非 `runDate` 日期、错过 09:00 后的服务重启以及配置不完整，均不得补执行写入。

## Dry-run output

- 本地有工号身份数
- OSS 返回人数
- 实时投标管理员与投标组长去重人数及本地用户 ID
- 预测日期不晚于 cutoff 且原提醒时间早于 runDate 零点的历史项目数
- 已有历史补发通知并幂等跳过的项目数
- 含实时在职经理的项目数
- 预计新增通知记录数和用户通知关系数
- 因无实时接收人、项目缺失或实时接口失败而跳过的数量

Dry-run 不得创建或更新 notification、user_notification、reminder log、企微任务或事件状态。

## Execute preconditions

1. preview 结果已经人工核对目标环境基线。
2. 当前发布版本包含实时收件人修复且健康检查通过。
3. 目标服务确认只有一个执行实例。
4. execute 使用已核对的 runDate、cutoff 和实时角色快照口径。
5. 生产执行仍需单独授权、数据库备份与生产风险审查；测试授权不得外推。

## Execute postconditions

- 正常扫描继续处理从未提醒项目，历史补发不重复处理执行日新提醒。
- 每个历史候选项目按当前完整实时接收人创建至多一条独立补发通知。
- 重复运行新增通知和用户通知关系均为 0。
- 原 reminder log 数量、内容与事件发布状态不变。
- 企微任务可异步完成；站内成功、企微失败必须分别计数。

## Test execution baseline

- 环境：测试服务器 `172.16.38.78`，禁止触碰生产。
- `runDate=2026-09-16`，`cutoff=2026-09-16`。
- 部署并启用后先从启动 preview 核对：预期历史候选 274，未来项目 324；实时组织人数和接收人关系数以该 preview 为准。
- execute 后立即再次 preview，预期新增通知为 0；任何候选数量变化先停止并解释。
