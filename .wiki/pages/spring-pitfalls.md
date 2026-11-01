---
title: Spring Boot 陷阱集
space: engineering
category: guide
tags: [Spring Boot, 事务, @Transactional, @Async, @ConditionalOnBean, 配置优先级, SPRING_CONFIG_IMPORT, 代理失效, 构造函数注入, @Autowired, JJWT, ServiceLoader, 并发安全, RateLimitFilter]
created: 2026-07-10
updated: 2026-10-24
health_checked: 2026-11-01
sources:
  - backend/src/main/java/com/xiyu/bid/
  - backend/src/main/resources/application.yml
  - .wiki/pages/lessons-learned.md
backlinks:
  - _index
  - ai-provider-configuration
  - configuration-binding-silent-failure
  - engineering-discipline
  - notification-system-pitfalls
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

## 10.5. @Async self-invocation（同类内部调用）导致代理失效（CO-602 / 2026-08-04）

### 事故

在 `@Service` 或 `@Component` 中直接调用自身的 `@Async` 方法（self-invocation），Spring AOP 代理无法拦截，`@Async` 注解失效，方法在调用线程同步执行。

### 影响

- HTTP 请求线程被阻塞（Nginx 60s 超时 → 502）
- 线程池配置形同虚设（不会使用配置的 executor）
- MDC 上下文无法透传（MdcTaskDecorator 不生效）

### 正确做法

将 `@Async` 方法提取到独立的 Spring Bean 中，通过依赖注入调用。

参考实现：`PerformanceBundleExportAsyncExecutor`、`WarehouseExportAsyncExecutor`、`WarehouseImportAsyncExecutor` 均为独立 Bean。

### 预防

`AsyncConfig.java` 中已添加详细 Javadoc 警告，新增 `@Async` 方法时必须检查是否为 self-invocation。

---

## 10.6. `@Component/@Service` 多构造函数必须用 `@Autowired` 明确标记注入入口（PR !2301 / 2026-08-17）

### 事故

同步 PR !2300（feat: 得分 LLM 消费防护）到本地后后端启动 crash：

```
BeanInstantiationException: Failed to instantiate [InitiationTenderTextResolver]: No default constructor found
Caused by: NoSuchMethodException: InitiationTenderTextResolver.<init>()
```

`InitiationTenderTextResolver` 在"单构造函数"版本能正常启动；PR !2300 新增了一个 package-private 的 7 参数测试专用构造函数（第 2 个构造）后，Spring 在启动时无法决策走哪条，回退到"找无参构造函数"，但该类字段全是 `final`，没有无参构造，启动失败。

### 根因（Spring 构造函数注入规则）

| 场景 | 行为 |
|------|------|
| 单构造函数（任何可见性） | 即使无 `@Autowired` 也自动使用（Spring 4.3+ 约定，语法糖） |
| 多构造函数（≥2），其中一个有 `@Autowired` | 使用被标记的那一个（正确） |
| 多构造函数（≥2），**都没有** `@Autowired` | Spring 视为「类希望 setter/字段注入」→ 尝试无参构造 → 找不到就 `No default constructor found` |

本次 bug：**构造函数数量从 1 → 2，但没有补 `@Autowired`**，从"单构造免注解"的隐式安全区滑落到"多构造无入口"的失败路径。

### 为什么单测发现不了

单测代码直接 `new InitiationTenderTextResolver(arg1, ..., arg7)` 手工调用 package-private 构造，**完全绕过 Spring 容器**。多构造函数冲突、DI 装配失败这类问题，在纯 Mockito/手工 new 模式下永远不会暴露。同类事故 Lesson 111（漏加 `@Service`）也触发了相同的"测试绕开 Spring 容器 → 上线才 crash"路径。

### 正确做法

```java
@Component
public class InitiationTenderTextResolver {
    @Autowired  // ← 多构造场景必须显式标记生产注入入口
    public InitiationTenderTextResolver(ProjectDocumentRepository repo, ..., ObsShareUrlSigner signer) {
        this(repo, ..., signer, new BoundedHttpDownloader()::get);
    }

    // 测试专用构造：package-private，参数更多，用于 stub UrlContentFetcher
    InitiationTenderTextResolver(ProjectDocumentRepository repo, ..., UrlContentFetcher fetcher) { ... }
}
```

### 预防

1. **`@Component/@Service` 类新增构造函数 = 必查 checklist**：
   - 单构造 → 建议仍显式加 `@Autowired`（防止后续再加第 2 个时忘记回头补）。
   - 多构造 → 立即在生产入口构造上加 `@Autowired`；CR 时不通过视为 blocker。
2. **测试专用构造**（package-private / 参数更多）：不加 `@Autowired`，但在类头注释中注明用途。
3. **新增类/构造函数变更的 PR**：本地必跑至少一次 `mvn spring-boot:run` 观察 `Started XiyuBidApplication` 成功日志再 push。
4. Code Review 规则：凡是 `@Component/@Service` 的 diff 中新增了构造函数，第一反应问——"生产构造函数上有 `@Autowired` 吗？"

### 相关

- `backend/src/main/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolver.java`（修复位置）
- PR !2300（得分 LLM 消费防护）—— 引入第 2 个构造函数的变更
- PR #2301（修复）—— 追加 `@Autowired` 注解
- [[lessons-learned]] §111：同类"Spring 装配门禁全空"模式（漏 `@Service`）。
- [[lessons-learned]] §116：完整根因分析与操作规范。

---

## 10.7. @Async 线程内调 Repository 写操作抛 `TransactionRequiredException`（T025 终验 / 2026-08-17）

### 事故

`ScoreScoringAppService` 的 `@Async` 打分执行路径中，成功/失败出口直接调 `resultRepository.deleteByScoreItemIdIn(...)` + `saveAll(...)`，测试环境触发失败任务时抛：

```
javax.persistence.TransactionRequiredException: No EntityManager with actual transaction available
- cannot reliably process 'remove' call
```

任务状态卡死、结果不落库、进度缓存不清理。

### 根因

- `@Async` 方法本体**没有** `@Transactional`，异步线程无事务上下文（同 §3 的 Session 不传递，但 §3 症状是 `LazyInitializationException`，本条是写操作直接被拒）。
- `deleteBy...` 这类需要事务的写操作在无事务上下文中由 JPA 直接抛 `TransactionRequiredException`——**不是静默失败，是硬异常**，且发生在异步线程里没有全局兜底，任务终态落空。
- 隐蔽性：单测用 Mockito mock repository，`deleteBy...` 只是普通 mock 调用，永远不暴露；只有真实EntityManager 才会炸。

### 正确做法

写操作抽独立事务组件，编排类只调用不落库：

```java
@Component
@RequiredArgsConstructor
public class ScoreResultOverwriter {
    private final ScoreResultRepository resultRepository;

    @Transactional  // ← Spring 代理保证异步线程内开启事务
    public void replaceAtomically(List<Long> scoreItemIds, List<ScoreResult> results) {
        resultRepository.deleteByScoreItemIdIn(scoreItemIds);
        resultRepository.saveAll(results);
    }
}
```

注意：给 `@Async` 方法本体直接加 `@Transactional` 也能修，但长事务包住分钟级 LLM 调用是反模式；**短事务组件 + 编排类无事务**才是正解（顺带满足行预算 300 拆分）。

### 预防

1. @Async 方法内任何 repository 写操作，必须经由带 `@Transactional` 的组件方法。
2. 单测注释要写明：mock 模式下 `@Transactional` 不生效，生产端事务由 Spring 代理保证——别把"测试绿"当"事务对"。
3. 涉及 DELETE+INSERT 覆盖语义（先删结果再删 item）的场景，事务边界必须包住两步。

### 相关

- `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreResultOverwriter.java`（修复组件）
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringAppService.java`（编排类改造）
- [[score-parse-service]] §7 —— 配额 512MB 对齐 + 本条事务修复的模块侧记录
- spec 045 T025 终验（docs/release/deploy-report-2026-08-17-131st-test.md）

---

## 10.8. `@Value`/`@Autowired` 注入静态字段反模式（TenderAttachmentUrlResolver 重构 / 2026-08-19）

### 现象

`TenderAttachmentUrlResolver`（CO-280 403 修复体系核心）用 setter 把 `@Value("${xiyu.public-base-url:}")` 和 `ObsShareUrlSigner` 注入**静态字段**，所有方法被迫 static。后果：

1. 依赖 Spring 容器初始化顺序，Bean 创建前调用得到 null；
2. 单测必须 `ReflectionTestUtils.setField(类, null, ...)` 设静态字段，还需 @AfterEach 清理，易漏；
3. 多 Spring 上下文测试（@SpringBootTest 并行）相互污染静态状态；
4. 静态工具类无法拆分，文件顶格 300 行硬上限。

### 修复（分支 agent/qoder/resolver-di-refactor）

- Resolver 改构造器注入 + 实例方法；新建 `IntegrationEndpointUrls` 承载配置与端点路径常量（Resolver 300 → 252 行）。
- 删除 `TenderIntegrationMapper.toDownloadUrl` 静态委托（migrate-callers-then-delete：调用方 `TenderMapper`/`TenderQueryService`/`ProjectResultPayloadAssembler` 同波改直接注入 Resolver，不留兼容层）。
- 循环依赖规避：`TenderIntegrationMapper` 已持有 `TenderMapper` 字段，故 `TenderMapper` 反向只能直接注入 Resolver，不能再注入 `TenderIntegrationMapper`。
- 测试全部改实例构造（`new TenderAttachmentUrlResolver(new IntegrationEndpointUrls(""), null)`），反射设静态字段全部移除。

### 预防

1. 新代码禁止 setter 注入静态字段；code review 看到 `static` + `@Value`/`@Autowired` 直接打回。
2. 配置值需要多处使用时，抽 `@Component` 配置承载类（如 `IntegrationEndpointUrls`）而非静态字段。
3. 静态工具方法只留给无状态的纯函数（如 URL 编码、字符串拼接）；任何依赖外部配置/Bean 的逻辑必须实例化。

### 相关

- `backend/src/main/java/com/xiyu/bid/integration/external/TenderAttachmentUrlResolver.java`（重构主体）
- `backend/src/main/java/com/xiyu/bid/integration/external/IntegrationEndpointUrls.java`（新增配置承载类）
- [[crm-integration-lessons]] —— CO-280/CRM 回调 URL 体系（本重构的上游背景）
- docs/exec-plans/tech-debt-tracker.md —— 对应条目已标记 resolved

---

## 10.9. JJWT ServiceLoader 并发竞争与 Filter 旁路防御（2026-10-24）

### 现象

线上偶发 `GET /api/workbench/deadline-items?period=week` 返回 HTTP 500，异常堆栈为：
```text
java.util.NoSuchElementException
    at java.base/java.util.ServiceLoader$LayerLookupIterator.next(ServiceLoader.java:1010)
    at io.jsonwebtoken.lang.Services.loadFirst(Services.java:83)
    at io.jsonwebtoken.Jwts.parser(Jwts.java:119)
    at com.xiyu.bid.auth.JwtUtil.extractAllClaims(JwtUtil.java:184)
    at com.xiyu.bid.config.RateLimitFilter.resolveUserRateLimitKey(RateLimitFilter.java:132)
```
仅在偶发高并发窗口出现，单并发复测正常。

### 根因

1. **JJWT 0.12.3 `Services.loadFirst()` 共享内部静态缓存**：底层非线程安全 `ServiceLoader` 在多线程并发执行 `loadFirst()` 时未加锁同步，且缺少 `hasNext()` 边界保护（官方已知 Bug #873）。
2. **`JwtUtil.extractAllClaims()` 每次请求重复 `build()`**：每次校验 Token 均调用 `Jwts.parser().verifyWith(secretKey).build()`，频繁触发 SPI 服务发现扫描，放大了并发竞态窗口。
3. **`RateLimitFilter` 旁路异常泄漏**：限流中间件仅捕获了 `JwtException` / `IllegalArgumentException`，未捕获 `NoSuchElementException` 等非预期运行时异常，导致非阻塞式辅助逻辑的故障穿透打垮了核心业务主链路。

### 正确做法

1. **单例化 `JwtParser`**：`JwtParser` 是线程安全且不可变的对象，在 `JwtUtil` 构造器中预先 build 完成（`this.jwtParser = Jwts.parser().verifyWith(this.secretKey).build();`），运行时解析复用单例，彻底规避重复 build 和 SPI 扫描。
2. **Filter 旁路降级防线**：限流/审计等非核心链路必须做好异常隔离，精准捕获 `NoSuchElementException | IllegalStateException`，记录 WARN 日志并安全降级到客户端 IP 限流，坚决不允许旁路故障阻断业务请求。
3. **依赖升级**：将 `jjwt.version` 由 0.12.3 升级至 0.12.5（官方已修复 `Services.loadFirst()` 的线程安全缺陷）。

### 预防

1. 任何第三方 SDK 提供的解析器/客户端（如 JWT Parser、ObjectMapper、HttpClient、TemplateEngine），若官方保证不可变且线程安全，必须组件级单例化，严禁在每次 Request Hot Path 中 `build()`。
2. Filter/Interceptor 中的辅助功能（限流、监控、上下文提取）必须遵守「非侵入、可降级」原则，异常捕获需防御非预期运行时异常并设计兜底路径（如回退至 IP）。

### 相关

- `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java`（单例化修复）
- `backend/src/main/java/com/xiyu/bid/config/RateLimitFilter.java`（旁路异常捕获与降级）
- `backend/pom.xml`（jjwt 0.12.3 → 0.12.5）
- `docs/lessons/lessons-learned.md` §449

---

## 11. 相关文档

- [[lessons-learned]] §50 — SPRING_CONFIG_IMPORT 外部配置覆盖案例
- [[lessons-learned]] §449 — JJWT ServiceLoader 并发竞争与 Filter 旁路防御
- [[production-deployment-lessons]] — 生产部署中的 Spring 配置陷阱
- [[architecture]] — 架构合成（FP-Java Contract）
- [[crm-integration-lessons]] §9 — 外部服务异常状态码处理
- SECURITY.md §权限守卫 — @PreAuthorize 规范

---

## 12. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取 Spring Boot 陷阱 |
| 2026-08-04 | 追加 §10.5 @Async self-invocation 警告（CO-602 PR 设计评估修复） |
| 2026-08-17 | 追加 §10.6 多构造函数 @Autowired 陷阱（PR !2301） |
| 2026-08-17 | 追加 §10.7 @Async 线程事务缺失抛 TransactionRequiredException（spec 045 T025 终验） |
| 2026-08-19 | 追加 §10.8 静态字段注入反模式与 TenderAttachmentUrlResolver DI 重构 |
| 2026-10-24 | 追加 §10.9 JJWT ServiceLoader 并发竞争与 Filter 旁路防御（线上 500 修复） |
