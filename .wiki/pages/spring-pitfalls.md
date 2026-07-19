---
title: Spring Boot 陷阱集
space: engineering
category: guide
tags: [Spring Boot, 事务, @Transactional, @Async, @ConditionalOnBean, 配置优先级, SPRING_CONFIG_IMPORT, 代理失效]
sources:
  - backend/src/main/java/com/xiyu/bid/
  - backend/src/main/resources/application.yml
  - .wiki/pages/lessons-learned.md
backlinks:
  - _index
  - lessons-learned
  - architecture
created: 2026-07-10
updated: 2026-07-10
health_checked: 2026-07-19
---
# Spring Boot 陷阱集

> 从 8 个工作区历史对话中提取的 Spring Boot 实战陷阱。
> 涵盖事务、异步、条件装配、配置优先级、外部配置覆盖、代理失效等高频踩坑点。

---

## 1. @Transactional REQUIRES_NEW + try-catch 反模式

### 1.1 事故

外层事务抛异常后，内层 `REQUIRES_NEW` 事务的回滚标记被传播，内层事务也回滚，但 try-catch 静默吞掉异常，导致数据不一致。

### 1.2 反模式

```java
// ❌ 错误
@Transactional
public void outerMethod() {
    try {
        self.newTransactionMethod(); // REQUIRES_NEW 但被回滚
    } catch (Exception e) {
        log.error("内层失败", e); // 静默吞掉，但回滚标记已传播
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void newTransactionMethod() { ... }
```

### 1.3 根因

- Spring 事务管理器在 catch 之前就已经把 rollback-only 标记写入
- `REQUIRES_NEW` 挂起外层事务时，rollback-only 标记可能传播

### 1.4 正确做法

```java
// ✅ 正确：REQUIRES_NEW 内层事务自己处理异常，不依赖外层
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void newTransactionMethod() {
    try {
        // 业务逻辑
    } catch (Exception e) {
        log.error("失败", e);
        // 自己决定是否回滚，不依赖外层
        throw e; // 或不抛，但显式决策
    }
}
```

### 1.5 教训

- **@Transactional 不要和 try-catch 混用**，否则 rollback-only 标记会传播
- **REQUIRES_NEW 的内层事务要自己处理异常**，不依赖外层 catch
- **事务方法不要吞异常**，要显式决策回滚还是提交

---

## 2. @Async 自调用导致代理失效

### 2.1 事故

```java
@Service
public class ImportService {
    public void importBatch(List<Item> items) {
        for (Item item : items) {
            self.processAsync(item); // 期望异步执行
        }
    }

    @Async
    public void processAsync(Item item) { ... }
}
```

结果 `processAsync` **同步执行**，因为 `self` 引用是原始对象，不是代理。

### 2.2 根因

Spring AOP 基于动态代理，自调用（this.method()）不经过代理，@Async 不生效。

### 2.3 解决方案

```java
// 方案 1：拆到另一个 Service
@Service
public class ImportService {
    private final AsyncProcessor asyncProcessor;

    public void importBatch(List<Item> items) {
        for (Item item : items) {
            asyncProcessor.processAsync(item); // 通过代理调用
        }
    }
}

@Service
public class AsyncProcessor {
    @Async
    public void processAsync(Item item) { ... }
}
```

```java
// 方案 2：注入自己的代理（不推荐，但有时必要）
@Service
public class ImportService {
    @Autowired
    private ImportService self;

    public void importBatch(List<Item> items) {
        for (Item item : items) {
            self.processAsync(item); // 通过代理
        }
    }
}
```

### 2.4 教训

- **@Async / @Transactional / @Cacheable 等基于代理的注解，自调用都不生效**
- **拆分 Service 是最干净的做法**
- **@Async 还要注意 Hibernate Session**：异步线程没有共享 Session，需要 new transaction

---

## 3. @Async + Hibernate Session 中毒

### 3.1 事故

@Async 方法中操作 Entity，抛 `LazyInitializationException` 或 Session 已关闭错误。

### 3.2 根因

主线程的 Hibernate Session 不会传递到异步线程，异步线程没有 Session 上下文。

### 3.3 解决

```java
@Async
@Transactional // 必须加，让异步线程有自己的 Session
public void processAsync(Item item) {
    // 现在 Session 可用
}
```

### 3.4 教训

- **@Async 方法操作数据库必须加 @Transactional**
- **异步线程不继承主线程的 Hibernate Session**
- **@Async + @Transactional 组合时，事务在异步线程内独立**

---

## 4. @ConditionalOnBean 在 @Service 上不可靠

### 4.1 事故

```java
@Service
@ConditionalOnBean(OrganizationEventSdk.class)
public class OrganizationEventConsumer { ... }
```

某些环境下 `OrganizationEventSdk` 已存在，但 `OrganizationEventConsumer` 未被创建。

### 4.2 根因

- `@ConditionalOnBean` 依赖 Bean 注册顺序
- `@Service` 的注册时机可能早于被依赖的 Bean
- 在 `@Configuration` 类中更可靠，在 `@Service` 上不稳定

### 4.3 替代方案

```java
// 方案 1：用 @ConditionalOnProperty（更稳定）
@Service
@ConditionalOnProperty(name = "xiyu.org.event-sdk.enabled", havingValue = "true")
public class OrganizationEventConsumer { ... }

// 方案 2：用 @Autowired(required = false)
@Service
public class OrganizationEventConsumer {
    @Autowired(required = false)
    private OrganizationEventSdk sdk;
}

// 方案 3：在 @Configuration 类中声明
@Configuration
public class OrganizationEventConfig {
    @Bean
    @ConditionalOnBean(OrganizationEventSdk.class)
    public OrganizationEventConsumer consumer(OrganizationEventSdk sdk) {
        return new OrganizationEventConsumer(sdk);
    }
}
```

### 4.4 教训

- **@ConditionalOnBean 优先在 @Configuration 类中使用**
- **@Service 上用 @ConditionalOnProperty 更可靠**
- **Bean 注册顺序不可预测**，条件装配不要依赖顺序

---

## 5. SPRING_CONFIG_IMPORT 外部配置覆盖 jar 内配置

### 5.1 事故

代码已合入 main 并打包部署，但运行时行为仍是旧版本。根因：`/etc/xiyu-bid/application-org-mappings.yml` 外部配置文件覆盖了 jar 内的配置，导致代码修复无效。

详见 [[lessons-learned]] §50 和 [[production-deployment-lessons]]。

### 5.2 根因

```bash
# /etc/xiyu-bid/backend.env
SPRING_CONFIG_IMPORT=file:/etc/xiyu-bid/application-org-mappings.yml
```

Spring Boot 加载顺序：
1. jar 内 `application.yml`
2. jar 内 `application-{profile}.yml`
3. **`SPRING_CONFIG_IMPORT` 指定的外部文件（最高优先级）**

外部文件中的配置会覆盖 jar 内的同名配置，导致代码修复无效。

### 5.3 修复

```bash
# 删除 SPRING_CONFIG_IMPORT
unset SPRING_CONFIG_IMPORT

# 或在 backend.env 中删除该行
# SPRING_CONFIG_IMPORT=file:/etc/xiyu-bid/application-org-mappings.yml
```

让 jar 内 `application.yml` 成为唯一配置源。

### 5.4 教训

- **"jar 内配置正确 ≠ 运行时配置正确"**
- **SPRING_CONFIG_IMPORT 是隐形杀手**，外部配置会静默覆盖 jar 内配置
- **部署后必须检查 SPRING_CONFIG_IMPORT 是否存在**
- **配置变更要同步代码、文档、服务器三处**

---

## 6. application.yml 重复 YAML 文档优先级

### 6.1 事故

`application.yml` 中包含多个 YAML 文档（用 `---` 分隔），后面的文档优先级高于前面，导致配置被意外覆盖。

### 6.2 根因

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate

---
# 后面的文档会覆盖前面
spring:
  jpa:
    hibernate:
      ddl-auto: none  # 这个值最终生效
```

### 6.3 教训

- **application.yml 中的多个 YAML 文档不是"配置片段"，是"覆盖关系"**
- **后面的 `---` 文档优先级更高**
- **检查 application.yml 时要看完所有 `---` 段**

---

## 7. CurrentUserResolver @RequestScope 在组织事件 SDK 启动失败

### 7.1 事故

启动时组织事件 SDK 消费 Kafka 消息，调用 `CurrentUserResolver`，但此时没有 HTTP 请求上下文，`@RequestScope` bean 无法创建，启动失败。

### 7.2 根因

`CurrentUserResolver` 是 `@RequestScope`，依赖 HTTP 请求。Kafka 消费者不在 HTTP 请求上下文中。

### 7.3 解决

```java
// 方案 1：给 Kafka 消费者提供静态上下文
public void onMessage(ConsumerRecord<String, String> record) {
    CallerContext context = CallerContext.external("kafka");
    service.process(record.value(), context);
}

// 方案 2：用 ObjectFactory 包装延迟解析
@Autowired
private ObjectFactory<CurrentUserResolver> currentUserResolverFactory;

public void onMessage(ConsumerRecord<String, String> record) {
    CurrentUserResolver resolver = currentUserResolverFactory.getObject();
    // 但在非 HTTP 上下文仍会失败，需要 try-catch
}
```

### 7.4 教训

- **@RequestScope bean 在非 HTTP 上下文不可用**
- **Kafka/定时任务/webhook 等异步入口要显式传递上下文**（CallerContext 模式）
- **Service 方法不要隐式依赖 HTTP 上下文**

---

## 8. ResponseEntityExceptionHandler 子类重写陷阱

### 8.1 事故

自定义异常 handler 继承 `ResponseEntityExceptionHandler` 后，重写 `handleException` 时漏调用 `super`，导致其他异常类型不被处理。

### 8.2 反模式

```java
// ❌ 错误：漏调 super
@Override
protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    // 只处理自己的异常，其他异常被吞掉
    return new ResponseEntity<>(customBody, headers, statusCode);
}
```

### 8.3 正确做法

```java
// ✅ 正确：调 super 处理其他异常
@Override
protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (ex instanceof MyException) {
        body = new MyErrorResponse(ex.getMessage());
    }
    return super.handleExceptionInternal(ex, body, headers, statusCode, request);
}
```

### 8.4 教训

- **重写框架方法必须调 super**，否则其他异常处理链断裂
- **ResponseEntityExceptionHandler 的处理链是顺序的**

---

## 9. 外部服务异常必须保留原始 HTTP 状态码

### 9.1 事故

调用 CRM/OSS 接口失败时，统一抛 500，前端无法区分是"CRM 不可用"还是"本地业务异常"。

### 9.2 反模式

```java
// ❌ 错误：所有外部异常都 500
try {
    crmClient.sync(tender);
} catch (Exception e) {
    throw new RuntimeException("CRM 同步失败", e); // 500
}
```

### 9.3 正确做法

```java
// ✅ 正确：区分状态码
try {
    crmClient.sync(tender);
} catch (HttpClientErrorException e) {
    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new ResourceNotFoundException("CRM 商机不存在");
    }
    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        throw new AuthenticationException("CRM 认证失败");
    }
    throw new IntegrationException("CRM 调用失败", e); // 502
} catch (ResourceAccessException e) {
    throw new ServiceUnavailableException("CRM 不可用", e); // 503
}
```

### 9.4 教训

- **外部服务异常要映射到合适的 HTTP 状态码**（502/503/504）
- **不要把所有外部异常都包装成 500**
- **区分"客户端错误"（4xx）和"服务端错误"（5xx）**

---

## 10. @PreAuthorize 语法错误导致认证绕过

### 10.1 事故

```java
// ❌ 错误（漏括号）
@PreAuthorize("hasRole('admin') or permitAll")  // permitAll 不是有效的表达式

// ❌ 错误（字符串引号）
@PreAuthorize("hasRole(admin)")  // admin 应该加引号
```

错误的表达式可能导致：
- Spring Security 解析失败，默认拒绝所有请求（403）
- 或更糟的，表达式被解析为 `true`，认证被绕过

### 10.2 正确语法

```java
// ✅ 正确
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'BID_ADMIN')")
@PreAuthorize("hasAuthority('tender:create')")
@PreAuthorize("permitAll()")  // 公开接口
@PreAuthorize("isAuthenticated()")  // 仅需登录
```

### 10.3 教训

- **@PreAuthorize 表达式语法必须严格遵循 Spring Security 规范**
- **角色名要加引号，方法要加括号**
- **新加 @PreAuthorize 必须测试**（包括"应该拒绝"和"应该允许"两种场景）
- **详见 SECURITY.md §权限守卫**

---

## 11. 相关文档

- [[lessons-learned]] §50 — SPRING_CONFIG_IMPORT 外部配置覆盖案例
- [[production-deployment-lessons]] — 生产部署中的 Spring 配置陷阱
- [[architecture]] — 架构合成（FP-Java Contract）
- [[crm-integration-lessons]] §9 — 外部服务异常状态码处理
- SECURITY.md §权限守卫 — @PreAuthorize 规范

---

## 12. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取 Spring Boot 陷阱 |
