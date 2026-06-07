# Calendar Module - 日历模块

## 概述
日历模块提供了完整的日历事件管理功能，支持创建、查询、更新和删除日历事件，以及按多种条件筛选事件。

## 技术栈
- Java 21
- Spring Boot 3.2.0
- Spring Data JPA
- MySQL 8.0 / H2
- Lombok
- JUnit 5
- Mockito

## 目录结构
```
calendar/
├── entity/           # 实体类
│   ├── CalendarEvent.java
│   └── EventType.java
├── dto/              # 数据传输对象
│   ├── CalendarEventDTO.java
│   ├── CalendarEventCreateRequest.java
│   └── CalendarEventUpdateRequest.java
├── repository/       # 数据访问层
│   └── CalendarEventRepository.java
├── service/          # 业务逻辑层
│   └── CalendarService.java
└── controller/       # 控制器层
    └── CalendarController.java
```

## 核心功能

### 1. 实体 (CalendarEvent)
```java
@Entity
@Table(name = "calendar_events")
public class CalendarEvent {
    private Long id;
    private LocalDate eventDate;
    private EventType eventType;
    private String title;
    private String description;
    private Long projectId;
    private Boolean isUrgent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 2. 事件类型 (EventType)
- `DEADLINE` - 截止日期
- `MEETING` - 会议
- `MILESTONE` - 里程碑
- `REMINDER` - 提醒
- `SUBMISSION` - 提交
- `REVIEW` - 审核

### 3. Repository查询方法
- `findByEventDateBetween(LocalDate start, LocalDate end)` - 按日期范围查询
- `findByProjectId(Long projectId)` - 按项目ID查询
- `findByEventType(EventType type)` - 按事件类型查询
- `findByIsUrgentTrue()` - 查询紧急事件
- `findUpcomingEvents(LocalDate startDate)` - 查询即将到来的事件

### 4. Service方法
- `createEvent(CalendarEventCreateRequest)` - 创建事件
- `updateEvent(Long id, CalendarEventUpdateRequest)` - 更新事件
- `deleteEvent(Long id)` - 删除事件
- `getEventsByMonth(int year, int month)` - 获取指定月份的事件
- `getEventsByProject(Long projectId)` - 获取项目事件
- `getUrgentEvents()` - 获取紧急事件
- `getUpcomingEvents()` - 获取即将到来的事件

### 5. API端点

#### 获取事件
- `GET /api/calendar?start=&end=` - 获取日期范围内的事件
- `GET /api/calendar/month/{year}/{month}` - 获取指定月份的事件
- `GET /api/calendar/project/{projectId}` - 获取项目事件
- `GET /api/calendar/urgent` - 获取紧急事件

#### 创建事件
- `POST /api/calendar` - 创建新事件

#### 更新事件
- `PUT /api/calendar/{id}` - 更新事件

#### 删除事件
- `DELETE /api/calendar/{id}` - 删除事件

## 测试覆盖

### 单元测试
- `CalendarEventTest` - 实体测试
- `CalendarServiceTest` - 服务层测试
- `CalendarControllerTest` - 控制器层测试

### 集成测试
- `CalendarIntegrationTest` - 端到端集成测试

### 测试覆盖的功能点
1. 创建事件的各种场景
2. 更新事件的各种场景
3. 删除事件的各种场景
4. 查询事件的各种场景
5. 输入验证
6. 权限控制
7. 异常处理
8. 边界条件测试

## 数据验证规则

### 创建请求 (CalendarEventCreateRequest)
- `eventDate` - 必填
- `eventType` - 必填
- `title` - 必填，非空
- `description` - 可选
- `projectId` - 可选
- `isUrgent` - 可选，默认false

### 更新请求 (CalendarEventUpdateRequest)
- 所有字段都是可选的
- 只更新提供的字段

## 安全性
- 所有端点需要认证
- 创建/更新/删除操作需要ADMIN或MANAGER角色
- 查询操作需要ADMIN、MANAGER或STAFF角色
- 所有用户输入都经过清洗，防止XSS攻击

## 审计日志
所有CUD操作（Create、Update、Delete）都通过@Auditable注解自动记录审计日志：
- 操作类型
- 实体类型
- 操作描述
- 时间戳
- 用户信息

## 数据库索引
```sql
CREATE INDEX idx_event_date ON calendar_events(event_date);
CREATE INDEX idx_event_type ON calendar_events(event_type);
CREATE INDEX idx_project_id ON calendar_events(project_id);
CREATE INDEX idx_urgent ON calendar_events(is_urgent);
CREATE INDEX idx_date_range ON calendar_events(event_date, event_type);
```

## 使用示例

### 创建事件
```bash
curl -X POST http://localhost:8080/api/calendar \
  -H "Content-Type: application/json" \
  -d '{
    "eventDate": "2024-03-15",
    "eventType": "DEADLINE",
    "title": "项目截止日期",
    "description": "标书提交截止日期",
    "projectId": 100,
    "isUrgent": true
  }'
```

### 获取月份事件
```bash
curl -X GET http://localhost:8080/api/calendar/month/2024/3
```

### 获取项目事件
```bash
curl -X GET http://localhost:8080/api/calendar/project/100
```

### 获取紧急事件
```bash
curl -X GET http://localhost:8080/api/calendar/urgent
```

### 更新事件
```bash
curl -X PUT http://localhost:8080/api/calendar/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "更新后的标题",
    "isUrgent": false
  }'
```

### 删除事件
```bash
curl -X DELETE http://localhost:8080/api/calendar/1
```

## 响应格式
所有API响应都遵循统一格式：
```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "eventDate": "2024-03-15",
    "eventType": "DEADLINE",
    "title": "项目截止日期",
    "description": "标书提交截止日期",
    "projectId": 100,
    "isUrgent": true,
    "createdAt": "2024-03-01T10:00:00",
    "updatedAt": "2024-03-01T10:00:00"
  }
}
```

## 错误处理
- `400 Bad Request` - 输入验证失败
- `401 Unauthorized` - 未认证
- `403 Forbidden` - 权限不足
- `404 Not Found` - 资源不存在
- `500 Internal Server Error` - 服务器错误

## 扩展性考虑
1. 支持事件重复（每日、每周、每月）
2. 支持事件提醒（邮件、短信、推送）
3. 支持事件参与者
4. 支持事件标签
5. 支持事件附件
6. 支持日历导出（iCal格式）
7. 支持多时区

## 性能优化
1. 使用数据库索引优化查询
2. 使用分页查询大量数据
3. 使用缓存减少数据库查询
4. 使用异步处理审计日志

## 相关模块
- Project模块 - 项目管理
- Alert模块 - 预警通知
- AuditLog模块 - 审计日志
