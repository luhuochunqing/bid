# Research: CRM BaseUrl 配置重构

## Decision: 多 BaseUrl 配置方案

**Decision**: 将单一 `baseUrl` 拆分为 `authBaseUrl`、`customerBaseUrl`、`messageBaseUrl` 三个独立配置项

**Rationale**:
- 客户环境明确使用 3 个不同域名：base-oss-test.ehsy.com（鉴权/组织架构）、cac-test.ehsy.com（客户查询）、crm-api-java-test6.ehsy.com（消息）
- 单一 baseUrl 无法对接真实多服务环境
- 拆分后各服务可独立配置，便于运维调整

**Alternatives considered**:
- 使用 Map<String, String> 动态配置：过于灵活，缺乏类型安全
- 保持单一 baseUrl + 路径前缀区分：与客户实际域名结构不符

## Decision: Settings 集成方案

**Decision**: 复用现有 `Settings` 表，新增 `crmConfig` JSON 字段存储运行时参数

**Rationale**:
- 现有 WeCom 配置已使用类似模式（参考 `SettingsResponse` / `SettingsUpdateRequest`）
- JSON 字段灵活，无需频繁迁移数据库
- 与现有系统设置页面集成成本低

**Alternatives considered**:
- 新建 `crm_settings` 独立表：增加复杂度，与现有设置体系割裂
- 每个参数一个字段：表结构膨胀，不利于扩展

## Decision: 实时生效机制

**Decision**: 使用 Spring `@RefreshScope` + 配置变更事件监听

**Rationale**:
- `@RefreshScope` 是 Spring Cloud 标准方案，与现有技术栈一致
- 配置变更后触发 `ContextRefreshedEvent` 或自定义事件
- 5 秒内生效目标可满足

**Alternatives considered**:
- 定时轮询 Settings 表：延迟不可控，资源浪费
- 手动重启服务：用户体验差

## Decision: YAPI 路径处理

**Decision**: 代码中预留路径配置项，联调时填充真实值

**Rationale**:
- 客户 YAPI 文档需要登录查看，当前无法获取真实 Path
- 路径格式可能为 `/interface/api/{apiId}` 或包含 project 前缀
- 将路径提取为 `CrmProperties` 配置，避免硬编码

**Path 映射表（待确认）**:

| 接口 | YAPI 地址 | 代码配置键 |
|------|----------|-----------|
| applyToken | project/406/api/23352 | `auth.applyTokenPath` |
| logout | project/406/api/23370 | `auth.logoutPath` |
| searchCustomers | project/509/api/25338 | `customer.searchPath` |
| getCustomerContacts | project/509/api/25259 | `customer.contactsPath` |
| getMenuTree | project/406/api/35642 | `auth.menuTreePath` |
| getEmployeeByToken | project/406/api/23358 | `auth.employeePath` |
| sendMessages | project/557/api/35649 | `message.sendPath` |
