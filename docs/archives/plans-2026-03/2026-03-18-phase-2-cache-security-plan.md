# Phase 2 执行计划：缓存统一与生产安全收紧（2026-03-18）

## 目标

在不扩大业务改造面的前提下，完成两项 Phase 2 工作：

1. 缓存统一：移除 analytics 模块自定义本地 `SimpleCacheManager`，统一回到 Spring/Redis 缓存配置
2. 生产安全收紧：缩小 actuator 暴露面，并收紧生产环境错误信息回显策略

## 风险与边界

### 缓存统一

- 风险：如果直接删除本地 cache manager，但没有补齐 cache 名称或序列化配置，可能导致启动失败或缓存行为变化
- 处理方式：优先使用 Spring Boot 的 Redis cache 自动配置，必要时只保留轻量级的 cache name 自定义，不再覆盖全局 `CacheManager`

### 生产安全收紧

- 风险：如果收得过猛，可能影响现有健康检查、Prometheus 采集或运维排障
- 处理方式：保留 `health` / `info`，把 `prometheus` 从匿名白名单移出；在 prod 配置中显式关闭 message/binding/stacktrace 回显

## 开发拆分

### Task A：缓存统一

范围：

- `backend/src/main/java/com/xiyu/bid/analytics/config/CacheConfig.java`
- `backend/src/main/resources/application.yml`
- 必要的缓存相关测试

验收标准：

1. 不再注册与 Redis 配置冲突的 `SimpleCacheManager`
2. dashboard 缓存继续可用
3. 启动与测试通过

### Task B：生产安全收紧

范围：

- `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java`
- `backend/src/main/resources/application-prod.yml`
- 必要的安全相关测试

验收标准：

1. `/actuator/prometheus` 不再匿名放行
2. prod 环境不再暴露 message/binding/stacktrace 细节
3. `health` / `info` 仍保持可用

## 验证计划

1. 跑缓存/安全相关定向测试
2. 跑一次联合测试，确认 Phase 0 与 Phase 2 改动不冲突
3. 做一次代码审查，重点看行为回归与配置误伤
