# Config 模块 (系统配置层)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
配置层承载系统级横切能力，包括安全、JWT、限流、异步和分页常量。这里不放业务规则，只放基础设施配置与启动级约束。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `SecurityConfig.java` | Config | 安全与权限配置 |
| `JwtConfig.java` | Config | JWT Bean 配置 |
| `AsyncConfig.java` | Config | 异步线程池配置，含操作日志线程池 |
| `RateLimitConfig.java` | Config | 速率限制配置 |
| `RateLimitFilter.java` | Config | 登录限流过滤器 |
| `PaginationConstants.java` | Config | 分页常量 |
| `ExportConfig.java` | Config | 导出能力配置 |
| `E2eDemoDataInitializer.java` | Config | E2E 演示数据初始化 |
