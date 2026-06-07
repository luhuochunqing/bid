# Calendar 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
日历模块提供投标项目的时间管理和日程提醒能力，覆盖截止日期、会议、里程碑和提交提醒。
该目录负责事件生命周期与查询接口，不承担审批或项目主逻辑。
对外暴露日历事件维护和项目维度查询能力。
日历事件如绑定 `projectId`，写入前通过 `ProjectAccessScopeService` 断言当前用户项目权限，查询后复用纯核心
`ProjectLinkedRecordVisibilityPolicy` 仅返回无项目事件或当前用户可见项目事件。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `CalendarEvent.java` | Entity | 日历事件实体 |
| `EventType.java` | Enum | 事件类型枚举 |
| `CalendarEventRepository.java` | Repository | 日历事件数据访问边界 |
| `CalendarService.java` | Service | 日历业务逻辑，负责项目访问断言与项目关联事件可见性过滤 |
| `CalendarController.java` | Controller | 日历事件 API 边界 |
| `CalendarEventDTO.java` | DTO | 日历事件传输对象 |
| `CalendarEventCreateRequest.java` | DTO | 创建事件请求 |
| `CalendarEventUpdateRequest.java` | DTO | 更新事件请求 |
