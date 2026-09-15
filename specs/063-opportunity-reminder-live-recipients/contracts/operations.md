# Production Operation Contract

## Inputs

- `cutoff`: 必填日期，生产本次固定为 `2026-09-15`，包含当日。
- `mode`: `dry-run` 或 `execute`，默认 `dry-run`。
- `confirmation`: execute 时必须与本次操作生成的确认短语完全一致。

## Dry-run output

- 本地有工号身份数
- OSS 返回人数
- 实时投标管理员与投标组长去重人数及本地用户 ID
- 从未提醒且预测日期不晚于 cutoff 的项目数
- 已提醒但缺少实时核心角色的项目数
- 预计新增通知记录数和用户通知关系数
- 因无实时经理、无本地身份、无既有通知内容或接口缺失而跳过的数量

Dry-run 不得创建或更新 notification、user_notification、reminder log、企微任务或事件状态。

## Execute preconditions

1. dry-run 结果已经人工核对生产基线。
2. 当前发布版本包含实时收件人修复且健康检查通过。
3. 数据库备份完成。
4. execute 使用与 dry-run 相同的 cutoff 和实时角色快照口径。

## Execute postconditions

- 从未提醒项目通过现有扫描链路生成日志、通知与事件。
- 已提醒项目只对缺失核心角色创建补发通知，不新增经理接收关系。
- 重复运行新增用户通知关系为 0。
- 企微任务可异步完成；站内成功、企微失败必须分别计数。
