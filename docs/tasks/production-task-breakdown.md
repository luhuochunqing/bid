# 投标管理平台 - 生产化任务分配

> 制定时间: 2026-03-19
> 目标: 将 POC 转换为生产可用系统
> 预计周期: 4 周

---

## 第一周：P0 紧急修复

### Task-1: 安全加固 - 加密密钥外置

**负责人**: 后端开发 A
**优先级**: P0 - CRITICAL
**预计工时**: 2 小时

**文件**: `backend/.../PasswordEncryptionUtil.java`

**当前代码**:
```java
private static final String DEFAULT_KEY = "hardcoded-32-byte-key...";
```

**修复方案**:
```java
private static final String ENCRYPTION_KEY =
    System.getenv("PLATFORM_ENCRYPTION_KEY") != null
        ? System.getenv("PLATFORM_ENCRYPTION_KEY")
        : DEFAULT_KEY; // fallback for dev
```

**验收标准**:
- [ ] 生产环境从环境变量读取密钥
- [ ] 开发环境保留 fallback（方便本地调试）
- [ ] 添加启动校验：如果环境变量为空且非 dev 环境，拒绝启动
- [ ] 更新部署文档，说明环境变量配置

---

### Task-2: Dashboard 性能重构

**负责人**: 后端开发 B
**优先级**: P0 - HIGH
**预计工时**: 4 小时

**文件**: `backend/.../DashboardAnalyticsService.java`

**问题分析**:
```java
// 问题 1: 全表拉取
List<Project> all = projectRepository.findAll();

// 问题 2: N+1 查询
for (Project p : all) {
    tasks = taskRepository.findByProjectId(p.getId());  // N 次查询
    fees = feeRepository.findByProjectId(p.getId());    // N 次查询
}
```

**修复方案**:

1. 新增 Repository 聚合查询方法:
```java
// ProjectRepository.java
@Query("""
    SELECT new com.example.dto.ProjectStatsDTO(
        p.status,
        COUNT(DISTINCT p.id),
        COUNT(DISTINCT t.id),
        SUM(f.amount)
    )
    FROM Project p
    LEFT JOIN p.tasks t
    LEFT JOIN p.fees f
    GROUP BY p.status
""")
List<ProjectStatsDTO> getStatsByStatus();
```

2. 添加必要的数据库索引:
```sql
CREATE INDEX idx_project_status ON project(status);
CREATE INDEX idx_task_project_id ON task(project_id);
CREATE INDEX idx_fee_project_id ON fee(project_id);
```

**验收标准**:
- [ ] `getOverview()` 不再使用 `findAll()`
- [ ] 消除循环内的 `findByProjectId` 调用
- [ ] 添加数据库索引
- [ ] 单元测试通过
- [ ] 性能基准: 1000 条数据下响应 < 500ms

---

### Task-3: AI 异步执行统一化

**负责人**: 后端开发 B
**优先级**: P0 - HIGH
**预计工时**: 2 小时

**文件**: `backend/.../AiService.java`

**问题分析**:
```java
// 双重异步 - 线程池混乱
@Async
public CompletableFuture<AiResult> analyze(String id) {
    return CompletableFuture.supplyAsync(() -> {
        // 哪个线程池在执行？不确定
    });
}
```

**修复方案**:
```java
// 方案 1: 使用 Spring 管理的线程池
@Async("aiExecutor")
public CompletableFuture<AiResult> analyze(String id) {
    AiResult result = doAnalyze(id); // 同步执行
    return CompletableFuture.completedFuture(result);
}

// 配置线程池
@Configuration
public class AsyncConfig {
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        return new ThreadPoolTaskExecutor() {{
            setCorePoolSize(4);
           setMaxPoolSize(10);
            setQueueCapacity(100);
            setThreadNamePrefix("ai-async-");
        }};
    }
}
```

**验收标准**:
- [ ] 移除 `CompletableFuture.runAsync()` 的使用
- [ ] 统一使用 `@Async("aiExecutor")`
- [ ] 异常能正确传播
- [ ] 单元测试验证异步行为

---

### Task-4: 前端路由跳转修复

**负责人**: 前端开发
**优先级**: P0
**预计工时**: 30 分钟

**文件**: `src/api/client.js:106-107`

**问题**: 使用 `window.location.href` 绕过 Vue Router

**修复**:
```javascript
import { useRouter } from 'vue-router'

// 在拦截器中
const router = useRouter()
if (window.location.pathname !== '/login') {
  router.push('/login')  // 改为使用 router
}
```

**验收标准**:
- [ ] 登录跳转使用 Vue Router
- [ ] 路由守卫正常工作
- [ ] 无控制台错误

---

## 第二周：P1 修复 + 测试

### Task-5: 缓存统一化

**负责人**: 后端开发 C
**优先级**: P1 - HIGH
**预计工时**: 4 小时

**问题**: analytics 模块使用本地 `SimpleCacheManager`，与全局 Redis 配置冲突

**修复方案**:
```java
// 移除本地缓存配置
// @Bean
// public CacheManager cacheManager() { ... }

// 使用统一的 RedisCacheManager
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        return RedisCacheManager.builder(factory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues())
            .build();
    }
}
```

**验收标准**:
- [ ] 所有模块使用 RedisCacheManager
- [ ] 统一 TTL 配置
- [ ] 缓存 key 有统一前缀
- [ ] 多实例部署缓存一致

---

### Task-6: 生产安全配置收紧

**负责人**: 后端开发 + 运维
**优先级**: P1 - HIGH
**预计工时**: 2 小时

**配置文件**: `application-prod.yml`

**修复内容**:
```yaml
# 1. 收紧 Actuator 暴露面
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: never  # 生产环境不显示详情

# 2. 错误信息不暴露内部细节
server:
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
    include-exception: false

# 3. Prometheus 仅内网访问
management:
  prometheus:
    metrics:
      export:
        prometheus:
          enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

**验收标准**:
- [ ] Actuator 仅暴露必要端点
- [ ] 错误页面不显示堆栈
- [ ] Prometheus 仅内网访问
- [ ] 健康检查正常工作

---

### Task-7: 单元测试补充

**负责人**: 后端开发 A/B
**优先级**: P1
**预计工时**: 4 小时

**目标**: 覆盖率从当前提升到 70%+

**重点测试**:
1. DashboardAnalyticsService 性能改造后的测试
2. AiService 异步行为测试
3. 密码加密工具测试
4. 权限校验测试

**验收标准**:
- [ ] 新增测试用例 20+
- [ ] 覆盖率 > 70%
- [ ] 所有 P0 修复都有测试覆盖

---

## 第三周：集成测试 + 部署准备

### Task-8: 前后端联调测试

**负责人**: 全员
**优先级**: P1
**预计工时**: 8 小时

**测试场景**:
1. 用户登录/登出
2. 标讯 CRUD + AI 分析
3. 项目创建 + 状态流转
4. 任务分配 + 更新
5. 知识库操作
6. 费用提交 + 审批
7. 账户借用 + 归还
8. Dashboard 数据展示

**验收标准**:
- [ ] 8 个核心场景全部通过
- [ ] 无控制台错误
- [ ] API 响应时间 < 1s

---

### Task-9: 部署脚本编写

**负责人**: 运维 + 后端开发
**优先级**: P1
**预计工时**: 4 小时

**交付物**:
1. Docker 镜像构建脚本
2. Docker Compose 配置
3. 数据库初始化脚本
4. Nginx 配置
5. 环境变量模板

**验收标准**:
- [ ] 一键部署成功
- [ ] 包含健康检查
- [ ] 日志正确输出

---

## 第四周：灰度上线 + 监控

### Task-10: 灰度发布

**负责人**: 全员
**优先级**: P1

**发布计划**:
- Day 1: 内部测试用户（2-3人）
- Day 2-3: 扩大到 10% 用户
- Day 4-5: 50% 用户
- Day 6+: 全量

**验收标准**:
- [ ] 每阶段无严重 bug
- [ ] 性能指标达标
- [ ] 有回滚预案

---

### Task-11: 监控告警配置

**负责人**: 运维
**优先级**: P1

**监控指标**:
1. 应用健康状态
2. API 响应时间（P50/P95/P99）
3. 错误率
4. 数据库连接池
5. JVM 内存/GC
6. Redis 连接

**告警规则**:
- 错误率 > 1%
- 响应时间 P95 > 3s
- 应用健康检查失败
- 数据库连接池耗尽

---

## 后续规划（一个月后）

### Phase 2 功能补全

| 功能 | 优先级 | 预计工时 |
|------|--------|----------|
| AI 真实大模型对接 | HIGH | 1-2 天 |
| 日历模块 | MEDIUM | 1 天 |
| 版本历史 | MEDIUM | 2 天 |
| 协作评论 | MEDIUM | 2 天 |
| 文档编辑器 | LOW | 3-5 天 |

---

## 依赖关系图

```
Task-1 (安全) ──────────────┐
                          ├──→ Task-8 (联调)
Task-2 (性能) ──────────────┤       │
                          ├──→ Task-9 (部署) ─→ Task-10 (灰度)
Task-3 (异步) ──────────────┤       │
                          ├──→ Task-11 (监控)
Task-4 (前端) ──────────────┘       │
                                   │
Task-5 (缓存) ──────────────────────┤
                                   │
Task-6 (安全配置) ────────────────────┘
Task-7 (测试) ───→ Task-8
```

---

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| P0 修复引入新 bug | 必须有单元测试覆盖 |
| 性能优化效果不达预期 | 预先建立性能基准 |
| 部署环境差异 | 使用 Docker 统一环境 |
| 用户不适应新系统 | 保留 Mock 模式快速回滚 |

---

## 每日站会检查项

- [ ] 今天的任务是什么？
- [ ] 有什么阻塞？
- [ ] 明天计划做什么？
- [ ] 需要什么支持？

---

*任务分配完成 - 2026-03-19*
