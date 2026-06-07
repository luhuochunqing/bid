# Dashboard 模块

一旦我所属的文件夹有所变化，请更新我。

## 文件清单

| 文件 | 功能 |
| --- | --- |
| `controller/DashboardLayoutController.java` | 提供工作台布局配置的 HTTP 接口。 |
| `dto/DashboardLayoutDTO.java` | 承载工作台布局和组件配置的数据传输对象。 |
| `entity/DashboardLayout.java` | 持久化用户工作台布局。 |
| `entity/DashboardWidget.java` | 持久化布局中的组件配置。 |
| `repository/DashboardLayoutRepository.java` | 访问工作台布局数据。 |
| `repository/DashboardWidgetRepository.java` | 访问工作台组件数据。 |
| `service/DashboardLayoutService.java` | 编排工作台布局查询和保存。 |
