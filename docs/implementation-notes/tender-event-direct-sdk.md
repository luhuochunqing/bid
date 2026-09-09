# 标讯事件直接调用客户 SDK 实现笔记

## 2026-09-09 决策

- 客户明确要求直接调用 `SystemInteractiveService.sendEvent`，因此删除自定义 Sender/HTTP body 维护层。
- 用户明确 traceId 不超过 18 位。生产 Pod 有 `HOSTNAME` 时，底层 ID 由客户 SDK `SnowflakeIdUtil.generateId()` 生成，本地仅将完整 long 无损编码为 base36 并恢复 t/s 类型前缀，最长 14 位；不截断、不取模。无 `HOSTNAME` 的本地环境使用 87 位 `SecureRandom` 值，base36 加前缀后最长 18 位，避免触发 SDK 静态初始化空指针。
- `TenderEventSdkSender` 被标讯与商机预测共用，删除时两个发布器必须一起迁移，否则会留下双路径。
- SDK 的 `ServiceRegisterProperties` 会受 Spring `client.register` 绑定影响；继续在配置类中构造隔离实例并通过构造器交给发布器，不把该 SDK 实例暴露为会被后置处理器重新注入的普通 Bean。
- 本任务不调整 data 的 20 字段范围。客户样本只含 tenderId，但仓库记录存在后续全量字段需求，需单独确认后再变更。

## 2026-09-09 实施进度

- `TenderEventSdkProducer` 与 `OpportunityPredictionEventPublisher` 已分别直接注入隔离的 `SystemInteractiveService` 实例并调用四参数 `sendEvent`。
- 删除 `TenderEventSdkSender` 及其 wire 测试，测试改为在发布器边界锁定 SDK 参数和返回值语义。
- 首发商机事件的 `parentId` 收敛为客户规范要求的 `"0"`。
- 标讯与商机复用非空且长度不超过 18 的 MDC traceId；超长或空值改用不超过 18 位的 t/s + base36 链路 ID。生产由客户 SDK Snowflake 生成，本地无 `HOSTNAME` 时由安全随机值兜底。
- SDK 开关关闭时仍装配商机发布器及其轻量隔离 SDK 实例，以延续既有依赖注入行为；`publish` 在入口由开关拦截，不发出请求。
- ClientSDK `release_0.0.2` 的 `SnowflakeIdUtil` 静态初始化依赖 `HOSTNAME`：该环境变量缺失时会在 `getDataCenterId()` 内触发空指针。实现先检查环境变量，生产 Kubernetes Pod 走客户 SDK；无 `HOSTNAME` 的本地环境走独立 87 位安全随机兜底，不触发 SDK 类初始化。
- 专项验证覆盖两个发布器、Spring 隔离装配、雪花 ID 与标讯编排服务。
- 最终专项验证为 5 类 22 个用例通过；FPJava/Maintainability 架构测试、checkstyle、line-budget、wiki-check 与 diff-check 通过。
- 全量 `mvn test` 运行约 10 分钟仍在执行大量集成测试与数据库迁移，期间未发现与本改动有关的失败；为避免继续占用环境已人工终止。本次以专项与架构门禁作为完成证据。
