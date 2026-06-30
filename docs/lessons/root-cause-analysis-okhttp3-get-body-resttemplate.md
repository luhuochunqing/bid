# OkHttp3 GET body 限制导致 RestTemplate 健康检查全面失败

> 主题：openai-java-client-okhttp 传递依赖引入 okhttp3，RestTemplateBuilder 自动检测后所有 GET 请求带空 body 抛 IllegalArgumentException
> 日期: 2026-06-30
> 排查者: trae
> 涉及 PR: #1362（workaround）→ #1369（根因修复 sidecar）→ #1373（根因修复 organization）
> 影响范围: SidecarHealthIndicator / MarkItDownSidecarExtractor / MarkItDownSidecarTextExtractor / OrganizationDirectoryHttpGateway

---

## 一、问题现象

### 1.1 初始症状（2026-06-30 凌晨）

dev-services.sh restart 后：
- backend `/actuator/health` 返回 `{"status":"DOWN"}`
- backend 日志反复输出：
  ```
  WARN c.x.b.d.i.c.SidecarHealthIndicator - Sidecar health check DOWN at http://127.0.0.1:8009 (IllegalArgumentException): method GET must not have a request body.
  java.lang.IllegalArgumentException: method GET must not have a request body.
          at com.xiyu.bid.docinsight.infrastructure.config.SidecarHealthIndicator.health(SidecarHealthIndicator.java:28)
  ```
- frontend 因 backend health DOWN 而 403「没有操作权限」（cookie 跨 IPv4/IPv6 host 不共享，叠加 H13 HttpOnly cookie 改造）

### 1.2 影响范围

- dev-services.sh restart 在 backend 健康检查阶段中断，frontend 无法启动
- 三处 sidecar 调用全部受影响：
  - `SidecarHealthIndicator.health()` — 健康检查
  - `MarkItDownSidecarExtractor.extract()` — 文档抽取前的健康探测
  - `MarkItDownSidecarTextExtractor.extractText()` — 标书生成时的健康探测
- 客户组织主数据查询也受影响（后期发现）：
  - `OrganizationDirectoryHttpGateway` 通过 `OrganizationDirectoryRestClient.exchange(GET)` 调用

---

## 二、根因发现过程（含弯路）

### 2.1 第一次误判：以为是 sidecar (uvicorn) 拒绝带 body 的 GET

**假设**：LoggingClientHttpRequestInterceptor 给所有请求（包括 GET）传空 `byte[]`，导致 `SimpleClientHttpRequestFactory` 设置 `Content-Length: 0`，被 sidecar (uvicorn) 拒绝。

**尝试 1**：在 `LoggingClientHttpRequestInterceptor.intercept()` 里对 GET 请求传 `null` body。

**结果**：触发 NPE `Cannot read the array length because "body" is null`（在 `InterceptingClientHttpRequest$InterceptingRequestExecution.execute` 第 93 行）。

**尝试 2**：对 GET 请求移除 `Content-Length` header。

**结果**：单元测试通过，但生产仍报错。

### 2.2 第二次误判：改用 JDK HttpClient 绕开（workaround）

**方案**：重写 `SidecarHealthIndicator` 用 `java.net.http.HttpClient`，绕开 RestTemplate。

**结果**：修复成功，dev-services.sh restart 恢复正常。提交为 PR #1362。

**问题**：这是治标不治本的 workaround，根因未消除，其他 RestTemplate 使用点仍可能中招。

### 2.3 思维链 Review 揭示真相

启动 5 维度 Review（过度复杂/架构规范/重复造轮子/性能瓶颈/业务偏差）后，识别出 6 个问题：
- 🔴 问题 1：LoggingClientHttpRequestInterceptor 根本 bug 未修
- 🟡 问题 2：三处 sidecar 健康检查逻辑重复
- 🟡 问题 3：SidecarHealthIndicator 缺单元测试
- 🟡 问题 4：start-frontend.sh 是 workaround
- 🟢 问题 5：sidecar-url 默认值 `http://localhost:8000` 与实际 8009 不一致
- 🟢 问题 6：HttpClient 实例分散

### 2.4 真正根因：OkHttp3 自动检测

通过复现 bug 拿到完整错误栈，定位到真正根因：

```
at okhttp3.Request$Builder.method(Request.kt:258)
at org.springframework.http.client.OkHttp3ClientHttpRequest.executeInternal(OkHttp3ClientHttpRequest.java:88)
```

**完整根因链**：
1. `com.openai:openai-java-client-okhttp:4.32.0` 传递依赖引入 `okhttp3`
2. Spring Boot 的 `RestTemplateBuilder` 自动检测 classpath，发现 OkHttp3 后使用 `OkHttp3ClientHttpRequestFactory`
3. OkHttp3 的 `Request.Builder.method()` 对 GET/HEAD 严格要求 body 为 `null`
4. `LoggingClientHttpRequestInterceptor.intercept()` 对所有请求传空 `byte[]`（包括 GET）
5. OkHttp3 检测到 GET + 非 null body，抛 `IllegalArgumentException("method GET must not have a request body")`

**为什么假设 1 错了**：根本不是 sidecar (uvicorn) 拒绝，而是 OkHttp3 在客户端发送前就抛异常了。错误消息 `method GET must not have a request body` 是 OkHttp3 的，不是 sidecar 的。

---

## 三、修复方案演进

### 3.1 PR #1362（workaround，2026-06-29）

- `SidecarHealthIndicator` 改用 JDK `java.net.http.HttpClient`
- `start-frontend.sh` 改用 `--host 127.0.0.1` 和 `VITE_API_BASE_URL=http://127.0.0.1:18089`（修 IPv6 cookie 问题）
- 治标不治本，根因未消除

### 3.2 PR #1369（根因修复 sidecar，2026-06-30）

**核心修复**：在 `MarkItDownSidecarClientConfig` 显式指定 `SimpleClientHttpRequestFactory`：

```java
@Bean(name = "markItDownSidecarRestTemplate")
public RestTemplate markItDownSidecarRestTemplate(
        RestTemplateBuilder builder,
        @Value("${app.doc-insight.sidecar-connect-timeout-ms:5000}") long connectTimeoutMs,
        @Value("${app.doc-insight.sidecar-read-timeout-ms:60000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) connectTimeoutMs);
    factory.setReadTimeout((int) readTimeoutMs);
    return builder
            .requestFactory(() -> factory)
            .build();
}
```

**关键点**：
- `builder.requestFactory(() -> factory)` 显式指定，覆盖 `RestTemplateBuilder` 的自动检测
- 三处 sidecar 调用共用同一个 `markItDownSidecarRestTemplate` bean，**修复一处即生效三处**
- 回滚 PR #1362 的 JDK HttpClient workaround，回到 RestTemplate 统一风格

**测试**：
- `LoggingClientHttpRequestInterceptorGetBodyTest`（2 个测试）：验证修复后 GET 请求正常
- `SidecarHealthIndicatorTest`（4 个测试）：覆盖 UP/DOWN/5xx/长响应截断

### 3.3 PR #1373（根因修复 organization，2026-06-30）

**遗漏点发现**：stash 中发现 `OrganizationDirectoryHttpGateway` 也应用了同样的修复模式，是同根因的另一个 RestTemplate 使用点。

**核心修复**：抽取 `buildRestTemplate` 私有静态方法：

```java
private static RestTemplate buildRestTemplate(RestTemplateBuilder builder, long connectTimeoutMs, long readTimeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) connectTimeoutMs);
    factory.setReadTimeout((int) readTimeoutMs);
    return builder
            .requestFactory(() -> factory)
            .build();
}
```

**关键点**：
- 普通 + 批量两个 RestTemplate 都走此方法
- 与 `MarkItDownSidecarClientConfig` 修复模式一致

---

## 四、经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 错误消息 `method GET must have a request body` 被误判为 sidecar 拒绝 | 错误消息要看完整调用栈，不能只看消息文本 | 排查时先 `grep -A 20` 看完整 stacktrace，确认抛异常的类属于哪一层 |
| 第一次修复在拦截器里传 null body 触发 NPE | 拦截器契约要求 `body` 非 null，传 null 会破坏 Spring 内部不变量 | 不要在拦截器里破坏 `ClientHttpRequestExecution.execute(request, body)` 契约 |
| 改用 JDK HttpClient 是 workaround | workaround 治标不治本，根因未消除，其他使用点仍会中招 | 修复后必须做 5 维度 Review，识别 workaround 并追问根因 |
| OkHttp3 通过 openai-java-client-okhttp 传递依赖引入 | 传递依赖会改变框架行为，RestTemplateBuilder 自动检测不可靠 | 显式指定 `requestFactory`，不要依赖自动检测 |
| 修复一处后以为完事，结果 stash 中发现另一个使用点也中招 | 同一根因可能影响多个使用点，必须全局排查 | 修复后用 `grep -rn "RestTemplateBuilder" backend/src/main` 列出所有使用点 |
| SidecarHealthIndicator 缺单元测试 | 健康检查是关键路径，必须有测试覆盖 | 关键 HealthIndicator 必须有 UP/DOWN/超时/5xx 至少 4 个测试 |
| dev-services.sh restart 因 health DOWN 中断 | 健康检查失败会阻塞 dev-services 启动流程 | 排查 restart 失败时先看 backend.log 的 health indicator 报错 |

---

## 五、检测和预防

### 5.1 检测命令

```bash
# 1. 列出所有 RestTemplateBuilder 使用点，确认是否显式指定 requestFactory
grep -rn "RestTemplateBuilder" backend/src/main --include="*.java" | grep -v "requestFactory"

# 2. 检查 OkHttp3 是否在 classpath
mvn dependency:tree -Dincludes=com.squareup.okhttp3 2>&1 | grep okhttp3

# 3. 验证 sidecar 健康检查
curl -s http://127.0.0.1:18089/actuator/health | python3 -m json.tool
# 期望：{"status":"UP"}

# 4. 查看 sidecar health 日志
grep "Sidecar health check" .runtime/dev-services/backend.log | tail -5
# 期望：health check OK；不应有 IllegalArgumentException
```

### 5.2 预防规范

1. **所有 `RestTemplateBuilder` 使用点必须显式指定 `requestFactory`**，不依赖自动检测
2. **新增 HealthIndicator 必须配单元测试**，覆盖 UP/DOWN/超时/5xx 至少 4 个场景
3. **关键日志拦截器必须有测试**，验证对 GET/POST/PUT/DELETE 各种方法的行为
4. **传递依赖变更时跑架构测试**，`ArchitectureTest` 会捕获部分问题
5. **修复 bug 后做 5 维度 Review**，识别 workaround 并追问根因
6. **同一根因修复后全局排查**，用 `grep` 列出所有同类使用点

### 5.3 推荐的 RestTemplate 配置模板

```java
// 标准模板：显式指定 SimpleClientHttpRequestFactory
@Bean(name = "xxxRestTemplate")
public RestTemplate xxxRestTemplate(
        RestTemplateBuilder builder,
        @Value("${xxx.connect-timeout-ms:5000}") long connectTimeoutMs,
        @Value("${xxx.read-timeout-ms:60000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) connectTimeoutMs);
    factory.setReadTimeout((int) readTimeoutMs);
    return builder
            .requestFactory(() -> factory)
            .build();
}
```

---

## 六、相关代码

### 6.1 已修复的文件

| 文件 | PR | 修复方式 |
|---|---|---|
| `backend/src/main/java/com/xiyu/bid/docinsight/infrastructure/config/MarkItDownSidecarClientConfig.java` | #1369 | 显式 `SimpleClientHttpRequestFactory` + 详细注释 |
| `backend/src/main/java/com/xiyu/bid/docinsight/infrastructure/config/SidecarHealthIndicator.java` | #1369 | 回滚到 RestTemplate 版本（撤销 #1362 的 JDK HttpClient workaround） |
| `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGateway.java` | #1373 | 抽取 `buildRestTemplate` 方法 |

### 6.2 新增的测试

| 文件 | 测试数 | 覆盖场景 |
|---|---|---|
| `backend/src/test/java/com/xiyu/bid/logging/config/LoggingClientHttpRequestInterceptorGetBodyTest.java` | 2 | GET 请求不抛异常 / GET 请求正常返回 |
| `backend/src/test/java/com/xiyu/bid/docinsight/infrastructure/config/SidecarHealthIndicatorTest.java` | 4 | UP / DOWN / 5xx / 长响应截断 |

### 6.3 相关依赖

```xml
<!-- pom.xml 中新增的测试依赖 -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <version>4.12.0</version>
    <scope>test</scope>
</dependency>
```

---

## 七、修复闭环全景

```
PR #1362 (workaround)
  ├─ SidecarHealthIndicator 改用 JDK HttpClient
  └─ start-frontend.sh 改用 127.0.0.1（修 IPv6 cookie）
       ↓
思维链 Review 识别 6 个问题
       ↓
PR #1369 (根因修复 sidecar)
  ├─ MarkItDownSidecarClientConfig 显式 SimpleClientHttpRequestFactory
  ├─ 回滚 SidecarHealthIndicator 到 RestTemplate
  ├─ 新增 LoggingClientHttpRequestInterceptorGetBodyTest (2)
  ├─ 新增 SidecarHealthIndicatorTest (4)
  └─ pom.xml 加 mockwebserver 依赖
       ↓
stash 发现 OrganizationDirectoryHttpGateway 也中招
       ↓
PR #1373 (根因修复 organization)
  └─ OrganizationDirectoryHttpGateway 抽取 buildRestTemplate
       ↓
完整闭环：所有 RestTemplate 使用点都显式指定 SimpleClientHttpRequestFactory
```

---

## 八、关键引用

- `AGENTS.md` §不可妥协的底线 — 真实 API 唯一源、复杂任务必走 Spec Kit
- `ARCHITECTURE.md` §Agent Contract — FP-Java Profile + Split-First Rule
- `RELIABILITY.md` §关键硬约束 — 原子提交 + 测试证据
- `docs/lessons/spring-boot-actuator-gotchas.md` — 同类健康检查陷阱
- `docs/lessons/build-gotchas.md` — Maven target 残留旧文件导致打包版本冲突
