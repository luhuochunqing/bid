# metrics 模块（业务监控指标）

> 一旦我所属的文件夹有所变化，请更新我。

## 职责

自定义业务指标，用于 Prometheus 采集和 Grafana 可视化。

## 目录结构

```
metrics/
└── BusinessMetrics.java  # 业务指标注册器
```

## 指标列表

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `project_created_total` | Counter | 项目创建总数 |
| `project_exported_total` | Counter | 项目导出总数 |
| `bid_submitted_total` | Counter | 投标提交总数 |
| `auth_login_failure_total` | Counter | 登录失败次数 |
| `auth_login_success_total` | Counter | 登录成功次数 |

## 使用方式

```java
@Autowired
private MeterRegistry registry;

public void createProject(Project project) {
    // 业务逻辑
    registry.counter("project_created_total", "type", project.getType()).increment();
}
```
