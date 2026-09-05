---
title: TenderEvent SDK Bean 冲突陷阱
space: engineering
category: pitfall
tags: [spring, bean-conflict, tenderevent, sdk, @Primary]
sources:
  - backend/src/main/java/com/xiyu/bid/integration/tenderevent/infrastructure/sdk/TenderEventSdkConfig.java
  - docs/release/deploy-report-2026-08-26-143rd-test.md
backlinks:
  - _index
  - hibernate-string-enum-varchar-validate
created: 2026-08-26
updated: 2026-08-26
health_checked: 2026-09-05
---
# TenderEvent SDK Bean 冲突陷阱

> PR !2416 引入 `@Import(SystemInteractiveServiceImpl.class)` 后，SDK 的 `SDKClientConfiguration` 被扫描注册了 `serviceRegister` bean，与项目 `TenderEventSdkConfig` 的 `serviceRegisterProperties` bean 类型冲突，导致后端 crash-loop。

## 根因

1. `TenderEventSdkConfig` 通过 `@Import(SystemInteractiveServiceImpl.class)` 引入 SDK 服务
2. `SystemInteractiveServiceImpl` 依赖 `ServiceRegisterProperties` 类型 bean
3. SDK 的 `SDKClientConfiguration` 也定义了同类型 bean（name=`serviceRegister`）
4. 项目的 `TenderEventSdkConfig` 定义了同类型 bean（name=`serviceRegisterProperties`）
5. Spring 找到两个候选，无法决定 -> `NoSuchBeanDefinitionException` -> crash-loop

## 冲突链路

```
scoreAnalysisController
  -> scoreAnalysisService
    -> tenderCommandService
      -> tenderEventPublishService
        -> tenderEventPublishPort
          -> systemInteractiveService (@Autowired ServiceRegisterProperties)
               ↑ 找到 2 个 bean: serviceRegister + serviceRegisterProperties
```

## 修复

在 `TenderEventSdkConfig.serviceRegisterProperties()` 方法上添加 `@Primary`：

```java
@Bean
@Primary
public ServiceRegisterProperties serviceRegisterProperties(TenderEventSdkProperties properties) {
```

## 陷阱特征

- **难以提前发现**：编译通过、单元测试通过，只在 Spring 全量上下文装配时暴露
- **容器测试可拦截**：`FlywayMysqlContainerTest` 会启动完整 Spring 上下文，能提前发现此类 bean 冲突
- **跳过容器测试 = 放弃此防线**：本次部署因 Docker 未运行跳过了容器测试，直接在服务器暴露

## 防护建议

1. **不要跳过容器测试**：`XIYU_SKIP_CONTAINER_TEST=true` 仅在明确知道无需 Spring 上下文验证时使用
2. **@Import SDK 类时检查 bean 重复**：SDK 内部可能定义了与项目配置类同类型的 bean
3. **@Primary 优于 @Qualifier**：对于"项目配置优先于 SDK 默认"的场景，`@Primary` 更简洁
4. **部署前确保 Docker 可用**：容器测试是 crash-loop 的最后一道防线
