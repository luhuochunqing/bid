# Tasks: add-xiyu-org-event-sdk-and-crm-client

> 实现前必须先获得 proposal 与 design 的 review 通过；本 tasks.md 仅为基线规范的实现路径草图，编码动作进入 apply 阶段后再执行。

## 0. 前置依赖（等客户提供，与本仓库节奏解耦）
- [ ] 0.1 取得 `com.ehsy.eventlibrary:ClientSDK` 的 Nexus 坐标或离线 jar
- [ ] 0.2 取得测试环境 broker / zookeeper / serverRegisterUrl / consumerGroup 命名规则
- [ ] 0.3 取得组织架构主数据接口的完整 YAPI 字段映射（部门 / 员工 / 任职 / 职位 / 部门树）
- [ ] 0.4 取得 CRM 7 个接口的生产域名、Token 鉴权方式、错误码字典、敏感字段清单
- [ ] 0.5 取得双方网络白名单（IP + 端口）与 HTTPS 证书

## 1. 组织架构：依赖与配置
- [ ] 1.1 在 `backend/pom.xml` 引入 `com.ehsy.eventlibrary:ClientSDK:release_0.0.2`（按 0.1 的交付方式接入）
- [ ] 1.2 在 `application.yml` 增加 `xiyu.integrations.organization.sdk.*` 节：`enabled`、`serviceName`、`serverRegisterUrl`、`renewal.*`、`broker.*`、`consumerGroup`
- [ ] 1.3 在 `OrganizationIntegrationProperties` 中扩展 `Sdk` 内嵌配置类
- [ ] 1.4 在 `ArchitectureTest` 中加入约束：SDK adapter 必须放在 `integration/organization/infrastructure/sdk/` 包，不允许跨包引用

## 2. 组织架构：SDK 主链路
- [ ] 2.1 新增 `OrganizationEventSdkConsumer`，实现 `OrganizationEventSdkConsumerPort`
- [ ] 2.2 在 SDK 消费方法上挂 `@AcceptEvent(eventTopic = "BaseOssDept", consumerGroup = "${...}")`、`BaseOssUser` 各一份
- [ ] 2.3 消费方法委托给 `OrganizationDirectorySyncAppService.receiveWebhook` 等价的入口（提取共用 `processNotice`）
- [ ] 2.4 返回 `EventResult code=200` / `code=500`，与文档第 6.2 节一致
- [ ] 2.5 集成测试：Mock SDK 投递 BaseOssDept / BaseOssUser，验证 inbox + 回查 + 落库

## 3. 组织架构：HTTP 中转灾备（已实现部分回归）
- [ ] 3.1 验证 `OrganizationEventWebhookController` 与 SDK 主链共用同一份 `OrganizationDirectorySyncAppService`
- [ ] 3.2 验证 HMAC + IP 白名单 + 启停开关在 SDK 启用时仍可作为灾备入口
- [ ] 3.3 文档同步：`backend/src/main/java/com/xiyu/bid/integration/README.md` 把"SDK 缺失，第一版以中转为主"改为"SDK 主链 + HTTP 灾备"双轨表述

## 4. 组织架构：初始化全量同步
- [ ] 4.1 新增 `OrganizationInitializationAppService.runFullInitialization(startAt, endAt)`，分页调用 `listDepartmentsByWindow` + `listUsersByWindow`
- [ ] 4.2 提供一次性入口：管理后台按钮（`POST /api/integrations/organization/initializations`）或 CLI（`scripts/org-init.sh`），二选一
- [ ] 4.3 初始化完成后写一条"初始化完成"标记（复用 `organization_event_logs.status = PROCESSED + event_topic = INIT`），后续重复触发幂等
- [ ] 4.4 单元 + 集成测试：覆盖全量初始化、断点续传、重复触发不重复写库

## 5. 组织架构：日常对账
- [ ] 5.1 现有 `OrganizationSyncRunController` 接入 Spring `@Scheduled`（每天凌晨 02:00 跑最近 3 天时间窗）
- [ ] 5.2 对账差异写入 `organization_sync_items`（已有表），失败项进入退避重试
- [ ] 5.3 长期失败（`retry_count > 阈值`）单独标记 `DEAD_LETTER` 状态，触发告警

## 6. 组织架构：日志、监控、安全
- [ ] 6.1 SDK / HTTP / 初始化 / 对账四条路径都写入统一日志（`traceId/spanId/eventTopic/key/consumerGroup/elapsed`）
- [ ] 6.2 接入 Micrometer 指标：`org_event_success_total`、`org_event_failure_total`、`org_directory_call_latency_seconds`
- [ ] 6.3 配置 P95/P99 处理延迟、消费成功率、积压量告警阈值
- [ ] 6.4 敏感字段脱敏：`Masker` 工具 + ArchUnit 守卫

## 7. CRM：基础设施
- [ ] 7.1 新建模块 `backend/src/main/java/com/xiyu/bid/integration/crm/`：`domain` / `application` / `infrastructure` / `dto`
- [ ] 7.2 新增 `CrmIntegrationProperties`（`xiyu.integrations.crm.*`）：`baseUrl`、`appKey`、`appSecret`、`timeout.*`、`tokenCacheTtlSeconds`、`ipWhitelist`
- [ ] 7.3 新增 `CrmHttpClient`：基于 `RestTemplate` / `WebClient`，封装 Authorization Header、traceId 透传、code/msg/data/success 响应解析
- [ ] 7.4 新增 `CrmTokenCache`（内存缓存 + 单飞 + 过期前续约），失效时立即清空
- [ ] 7.5 新增 `CrmBusinessException`（4xx + code != 0）与 `CrmTransientException`（5xx + 网络异常 + 超时）

## 8. CRM：7 个出向接口
- [ ] 8.1 `CrmTokenClient.applyToken()` / `logout()`：缓存到本地，按 TTL 续约
- [ ] 8.2 `CrmCustomerClient.searchCustomers(name)`：返回前 20 条（短名靠前）
- [ ] 8.3 `CrmCustomerClient.listOwners(customerIds)`：批量查客户负责人
- [ ] 8.4 `CrmMenuClient.fetchMenuTree(systemType)`：按系统类型取菜单树
- [ ] 8.5 `CrmEmployeeClient.fetchEmployee(token)`：取当前员工信息
- [ ] 8.6 `CrmMessageClient.sendMessage(target, content, channels)`：发送企微 + 站内消息
- [ ] 8.7 单元测试：Mock 后端响应（成功 / 业务失败 / 网络异常）

## 9. CRM：日志与安全
- [ ] 9.1 出向请求 / 响应日志包含 `requestId/path/elapsed/statusCode`，body 走脱敏视图
- [ ] 9.2 银行账号 / 证件号 / 手机号 / 邮箱按 `Masker` 统一脱敏
- [ ] 9.3 Token 不出现在任何日志或异常 message 中（含 stacktrace）
- [ ] 9.4 `CrmHttpClient` 在 4xx/5xx 时返回脱敏后的错误，调用方拿不到原始密钥

## 10. 文档与联调
- [ ] 10.1 更新 `.wiki/pages/integration-organization-event-sdk.md`：把"SDK 缺失，第一版只走 HTTP"清洗成"SDK 主链 + HTTP 灾备 + 初始化 + 对账"四件套
- [ ] 10.2 新建 `.wiki/pages/integration-crm-client.md`：覆盖 7 个出向接口的契约 / 错误码 / 鉴权
- [ ] 10.3 更新 `backend/src/main/java/com/xiyu/bid/integration/README.md`：补 `crm/` 目录与新边界
- [ ] 10.4 联调用例：TC-01 ~ TC-08（组织架构）+ CRM 7 个接口的最小成功样例 + 关键失败样例
- [ ] 10.5 `openspec validate add-xiyu-org-event-sdk-and-crm-client --strict` 通过
