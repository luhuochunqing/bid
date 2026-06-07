# Calendar Module - 功能清单

## 模块概述
日历模块（Calendar Module）提供完整的日历事件管理功能，支持多种事件类型和查询方式。

## 实现的功能

### 1. 实体层 (Entity)
- [x] CalendarEvent - 日历事件实体
  - 支持事件日期
  - 支持6种事件类型（DEADLINE, MEETING, MILESTONE, REMINDER, SUBMISSION, REVIEW）
  - 支持标题和描述
  - 支持项目关联
  - 支持紧急标记
  - 自动记录创建和更新时间

- [x] EventType - 事件类型枚举
  - DEADLINE - 截止日期
  - MEETING - 会议
  - MILESTONE - 里程碑
  - REMINDER - 提醒
  - SUBMISSION - 提交
  - REVIEW - 审核

### 2. 数据传输对象 (DTO)
- [x] CalendarEventDTO - 完整的事件数据传输对象
- [x] CalendarEventCreateRequest - 创建请求对象
  - 包含验证注解
  - eventDate必填
  - eventType必填
  - title必填
- [x] CalendarEventUpdateRequest - 更新请求对象
  - 所有字段可选
  - 支持部分更新

### 3. 数据访问层 (Repository)
- [x] CalendarEventRepository - JPA Repository接口
  - findByEventDateBetween - 按日期范围查询
  - findByProjectId - 按项目ID查询
  - findByEventType - 按事件类型查询
  - findByIsUrgentTrue - 查询紧急事件
  - findUpcomingEvents - 查询即将到来的事件
  - 继承JpaRepository的所有CRUD方法

### 4. 业务逻辑层 (Service)
- [x] CalendarService - 业务服务类
  - createEvent - 创建事件
    - 输入验证
    - 自动设置默认值
    - 记录审计日志
  - updateEvent - 更新事件
    - 存在性检查
    - 部分更新支持
    - 记录审计日志
  - deleteEvent - 删除事件
    - 存在性检查
    - 记录审计日志
  - getEventsByMonth - 按月份查询
    - 月份验证
    - 自动计算日期范围
  - getEventsByProject - 按项目查询
  - getUrgentEvents - 查询紧急事件
  - getUpcomingEvents - 查询即将到来的事件

### 5. 控制器层 (Controller)
- [x] CalendarController - REST API控制器
  - GET /api/calendar - 获取日期范围内的事件
  - GET /api/calendar/month/{year}/{month} - 获取指定月份的事件
  - GET /api/calendar/project/{projectId} - 获取项目事件
  - GET /api/calendar/urgent - 获取紧急事件
  - POST /api/calendar - 创建事件
  - PUT /api/calendar/{id} - 更新事件
  - DELETE /api/calendar/{id} - 删除事件

### 6. 测试覆盖
- [x] 单元测试
  - CalendarEventTest - 实体测试 (8个测试用例)
    - 创建事件测试
    - Builder模式测试
    - 所有事件类型测试
    - 空值处理测试
    - 默认值测试
    - 边界条件测试
    - 长文本处理测试

  - CalendarServiceTest - 服务层测试 (20个测试用例)
    - 创建事件成功测试
    - 输入验证测试（空标题、空日期、空类型）
    - 更新事件测试
    - 删除事件测试
    - 查询功能测试
    - 异常处理测试
    - 边界值测试

  - CalendarControllerTest - 控制器测试 (18个测试用例)
    - API端点测试
    - 请求验证测试
    - 权限控制测试
    - 异常处理测试
    - XSS防护测试

  - CalendarEventRepositoryTest - 数据访问测试 (10个测试用例)
    - 查询方法测试
    - 空结果测试
    - 边界条件测试
    - null值处理测试

- [x] 集成测试
  - CalendarIntegrationTest - 端到端测试 (10个测试场景)
    - CRUD完整流程测试
    - 多事件处理测试
    - 日期边界测试
    - 权限测试
    - 空项目ID测试

### 7. 安全性
- [x] 认证和授权
  - 所有端点需要认证
  - ADMIN/MANAGER可以创建、更新、删除
  - ADMIN/MANAGER/STAFF可以查询
- [x] 输入验证
  - @Valid注解验证请求
  - 自定义验证逻辑
- [x] XSS防护
  - InputSanitizer清洗用户输入
  - 标题限制500字符
  - 描述限制5000字符

### 8. 审计日志
- [x] @Auditable注解
  - createEvent - CREATE操作
  - updateEvent - UPDATE操作
  - deleteEvent - DELETE操作
- [x] 使用IAuditLogService接口
- [x] 异步记录审计日志

### 9. 数据库优化
- [x] 索引设计
  - idx_event_date - 事件日期索引
  - idx_event_type - 事件类型索引
  - idx_project_id - 项目ID索引
  - idx_urgent - 紧急标记索引
  - idx_date_range - 日期范围复合索引

### 10. 错误处理
- [x] 全局异常处理
  - IllegalArgumentException -> 400 Bad Request
  - RuntimeException -> 404 Not Found
- [x] 友好的错误消息
- [x] 统一的错误响应格式

### 11. 代码质量
- [x] 遵循项目代码规范
- [x] 使用Lombok减少样板代码
- [x] 完整的注释文档
- [x] 头部注释说明
  - Input - 依赖
  - Output - 输出
  - Pos - 位置
  - 更新提示

### 12. 文档
- [x] CALENDAR_MODULE.md - 完整模块文档
  - 概述
  - 技术栈
  - 目录结构
  - 核心功能
  - API文档
  - 使用示例
  - 扩展性考虑

## 测试统计
- 总测试文件: 5个
- 总测试用例: 66+个
- 覆盖的场景:
  - 正常流程
  - 异常情况
  - 边界条件
  - 权限控制
  - 输入验证
  - 并发访问（通过事务隔离）

## API端点总结

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| GET | /api/calendar | 获取日期范围内的事件 | ADMIN/MANAGER/STAFF |
| GET | /api/calendar/month/{year}/{month} | 获取指定月份的事件 | ADMIN/MANAGER/STAFF |
| GET | /api/calendar/project/{projectId} | 获取项目事件 | ADMIN/MANAGER/STAFF |
| GET | /api/calendar/urgent | 获取紧急事件 | ADMIN/MANAGER/STAFF |
| POST | /api/calendar | 创建事件 | ADMIN/MANAGER |
| PUT | /api/calendar/{id} | 更新事件 | ADMIN/MANAGER |
| DELETE | /api/calendar/{id} | 删除事件 | ADMIN/MANAGER |

## 数据库表结构
```sql
CREATE TABLE calendar_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_date DATE NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    project_id BIGINT,
    is_urgent BOOLEAN,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_event_date (event_date),
    INDEX idx_event_type (event_type),
    INDEX idx_project_id (project_id),
    INDEX idx_urgent (is_urgent),
    INDEX idx_date_range (event_date, event_type)
);
```

## 文件清单

### 源代码
```
src/main/java/com/xiyu/bid/calendar/
├── entity/
│   ├── CalendarEvent.java (118行)
│   └── EventType.java (40行)
├── dto/
│   ├── CalendarEventDTO.java (38行)
│   ├── CalendarEventCreateRequest.java (40行)
│   └── CalendarEventUpdateRequest.java (35行)
├── repository/
│   └── CalendarEventRepository.java (45行)
├── service/
│   └── CalendarService.java (198行)
└── controller/
    └── CalendarController.java (186行)
```

### 测试代码
```
src/test/java/com/xiyu/bid/calendar/
├── entity/
│   └── CalendarEventTest.java (150行)
├── unit/
│   ├── CalendarServiceTest.java (320行)
│   ├── CalendarControllerTest.java (280行)
│   └── CalendarEventRepositoryTest.java (200行)
└── integration/
    └── CalendarIntegrationTest.java (240行)
```

## TDD流程遵循
1. RED - 先写失败的测试
2. GREEN - 实现最小代码使测试通过
3. REFACTOR - 重构优化代码
4. 验证 - 确保所有测试通过

## 后续扩展建议
1. 事件重复功能（每日、每周、每月）
2. 事件提醒（邮件、短信、推送）
3. 事件参与者管理
4. 事件标签系统
5. 事件附件支持
6. 日历导出（iCal格式）
7. 多时区支持
8. 事件同步（Google Calendar、Outlook）
9. 事件模板
10. 事件统计和报表
