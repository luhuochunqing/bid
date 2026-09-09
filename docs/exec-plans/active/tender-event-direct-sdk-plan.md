# 标讯事件直接调用客户 SDK 实施计划

## 目标

删除自定义事件发送协议层，让标讯和商机事件发布器直接调用客户 `SystemInteractiveService.sendEvent`；维持 traceId/spanId 不超过 18 位。

## 范围

- 基础设施发布器与 Spring 装配
- SDK 契约单元测试
- 实现笔记与 Wiki 结论更正

## 验收

- 生产源码不再引用 `TenderEventSdkSender` 或 `HttpClientUtil.httpPost` 发送事件。
- `sendEvent(topic, source, JSON字符串, EventTrackReq)` 参数由测试锁定。
- `resp == null`、异常、`result=false` 返回失败；`result=true/null` 返回成功。
- 生产 Pod 的 traceId/spanId 基于客户 SDK Snowflake ID，使用 t/s + 无损 base36 适配（最长 14 位）；无 HOSTNAME 的本地环境使用 87 位安全随机兜底（最长 18 位）。

## 进度

- [x] 客户成功日志与 SDK 字节码核对
- [x] 测试改造
- [x] 生产代码收敛
- [x] 专项与门禁验证（5 类专项测试 22 个用例通过）
- [x] 独立代码审查（最终复核通过）
