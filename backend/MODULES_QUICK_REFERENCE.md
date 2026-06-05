# 标讯管理、项目管理与任务交付物模块 - 快速参考

## 模块概览

| 模块 | 实体 | 状态枚举 | API端点数 | 测试类数 |
|------|------|----------|-----------|----------|
| 标讯管理 | Tender | 4 (PENDING, TRACKING, BIDDED, ABANDONED) | 9 | 4 |
| 项目管理 | Project | 6 (INITIATED, PREPARING, REVIEWING, SEALING, BIDDING, ARCHIVED) | 13 | 4 |
| **任务交付物** | **Task, TaskDeliverable** | **5 (TODO, IN_PROGRESS, REVIEW, COMPLETED, CANCELLED) + 5 种 DeliverableType** | **19 (+6)** | **37 (+31)** |

## 关键文件位置

### 标讯管理模块
```
src/main/java/com/xiyu/bid/
├── entity/Tender.java                          # 实体
├── dto/TenderDTO.java                          # 数据传输对象
├── dto/TenderRequest.java                      # 请求对象（带验证）
├── repository/TenderRepository.java            # 数据访问层
├── service/TenderService.java                  # 服务层
└── controller/TenderController.java            # 控制器

src/test/java/com/xiyu/bid/
├── entity/TenderTest.java                      # 实体测试
├── repository/TenderRepositoryTest.java        # Repository测试
├── service/TenderServiceTest.java              # Service测试
└── integration/TenderIntegrationTest.java      # 集成测试
```

### 项目管理模块
```
src/main/java/com/xiyu/bid/
├── entity/Project.java                         # 实体（含 Task.Status 枚举）
├── dto/ProjectDTO.java                         # 数据传输对象
├── dto/ProjectRequest.java                     # 请求对象（带验证）
├── repository/ProjectRepository.java           # 数据访问层
├── service/ProjectService.java                 # 服务层
└── controller/ProjectController.java           # 控制器

src/test/java/com/xiyu/bid/
├── entity/ProjectTest.java                     # 实体测试
├── repository/ProjectRepositoryTest.java       # Repository测试
├── service/ProjectServiceTest.java             # Service测试
└── integration/ProjectIntegrationTest.java     # 集成测试
```

### 任务交付物模块（新增）
```
src/main/java/com/xiyu/bid/task/
├── core/
│   ├── TaskTransitionPolicy.java              # 状态流转守卫策略（纯静态）
│   ├── DeliverableAssociationPolicy.java      # 交付物关联规则策略（纯静态）
│   └── BidSubmissionPolicy.java             # 标书提交校验策略（纯静态）
├── entity/
│   └── TaskDeliverable.java                 # 交付物实体
├── repository/
│   └── TaskDeliverableRepository.java      # 交付物数据访问
├── dto/
│   ├── TaskDeliverableDTO.java               # 交付物 DTO
│   ├── TaskDeliverableCreateRequest.java    # 创建请求 DTO
│   ├── DeliverableCoverageDTO.java            # 覆盖度 DTO
│   ├── BidSubmissionResponse.java             # 提交响应 DTO
│   └── TaskDeliverableAssembler.java         # Entity↔DTO 转换
├── service/
│   ├── TaskDeliverableService.java          # 交付物 CRUD 编排服务
│   └── BidProcessService.java             # 标书提交流程服务

src/test/java/com/xiyu/bid/task/
├── core/
│   ├── TaskTransitionPolicyTest.java         # 策略测试 (14 cases)
│   ├── DeliverableAssociationPolicyTest.java  # 策略测试 (11 cases)
│   └── BidSubmissionPolicyTest.java         # 策略测试 (6 cases)
└── controller/
    └── TaskDeliverableContractTest.java    # API 契约测试 (6 cases)
```

## API端点速查

### 标讯管理 API

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| GET | /api/tenders | 获取所有标讯 | ADMIN, MANAGER, STAFF |
| GET | /api/tenders/{id} | 获取单个标讯 | ADMIN, MANAGER, STAFF |
| POST | /api/tenders | 创建标讯 | ADMIN, MANAGER |
| PUT | /api/tenders/{id} | 更新标讯 | ADMIN, MANAGER |
| DELETE | /api/tenders/{id} | 删除标讯 | ADMIN |
| POST | /api/tenders/{id}/analyze | AI分析标讯 | ADMIN, MANAGER, STAFF |
| GET | /api/tenders/status/{status} | 按状态查询 | ADMIN, MANAGER, STAFF |
| GET | /api/tenders/source/{source} | 按来源查询 | ADMIN, MANAGER, STAFF |
| GET | /api/tenders/statistics | 获取统计数据 | ADMIN, MANAGER |

### 项目管理 API

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| GET | /api/projects | 获取所有项目 | ADMIN, MANAGER, STAFF |
| GET | /api/projects/{id} | 获取单个项目 | ADMIN, MANAGER, STAFF |
| POST | /api/projects | 创建项目 | ADMIN, MANAGER |
| PUT | /api/projects/{id} | 更新项目 | ADMIN, MANAGER |
| DELETE | /api/projects/{id} | 删除项目 | ADMIN |
| PUT | /api/projects/{id}/status | 更新状态 | ADMIN, MANAGER |
| PUT | /api/projects/{id}/team | 更新团队 | ADMIN, MANAGER |
| GET | /api/projects/status/{status} | 按状态查询 | ADMIN, MANAGER, STAFF |
| GET | /api/projects/manager/{id} | 按经理查询 | ADMIN, MANAGER, STAFF |
| GET | /api/projects/tender/{id} | 按标讯查询 | ADMIN, MANAGER, STAFF |
| GET | /api/projects/active | 获取活跃项目 | ADMIN, MANAGER, STAFF |
| GET | /api/projects/search | 搜索项目 | ADMIN, MANAGER, STAFF |
| GET | /api/projects/statistics | 获取统计 | ADMIN, MANAGER |

## 测试命令

```bash
# 运行所有测试
mvn clean test

# 运行特定模块测试
mvn test -Dtest=Tender*          # 标讯模块所有测试
mvn test -Dtest=Project*         # 项目模块所有测试

# 运行特定类型测试
mvn test -Dtest=*IntegrationTest # 集成测试
mvn test -Dtest=*RepositoryTest  # Repository测试
mvn test -Dtest=*ServiceTest     # Service测试

# 生成覆盖率报告
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

## 请求示例

### 创建标讯
```bash
curl -X POST http://localhost:8080/api/tenders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "title": "智慧城市建设项目招标",
    "source": "政府采购网",
    "budget": 1500000.00,
    "deadline": "2024-06-30T23:59:59",
    "status": "PENDING"
  }'
```

### AI分析标讯
```bash
curl -X POST http://localhost:8080/api/tenders/1/analyze \
  -H "Authorization: Bearer <token>"
```

### 创建项目
```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "name": "智慧城市投标项目",
    "tenderId": 1,
    "status": "INITIATED",
    "managerId": 100,
    "teamMembers": [101, 102, 103],
    "startDate": "2024-01-01T00:00:00",
    "endDate": "2024-06-30T23:59:59"
  }'
```

### 更新项目状态
```bash
curl -X PUT "http://localhost:8080/api/projects/1/status?status=PREPARING" \
  -H "Authorization: Bearer <token>"
```

### 更新项目团队
```bash
curl -X PUT http://localhost:8080/api/projects/1/team \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '[201, 202, 203, 204]'
```

## 数据模型

### Tender（标讯）
```java
{
  "id": 1,
  "title": "智慧城市建设项目招标",
  "source": "政府采购网",
  "budget": 1500000.00,
  "deadline": "2024-06-30T23:59:59",
  "status": "PENDING",          // PENDING|TRACKING|BIDDED|ABANDONED
  "aiScore": 75,                 // 0-100
  "riskLevel": "MEDIUM",         // LOW|MEDIUM|HIGH
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00"
}
```

### Project（项目）
```java
{
  "id": 1,
  "name": "智慧城市投标项目",
  "tenderId": 1,
  "status": "INITIATED",         // INITIATED|PREPARING|REVIEWING|SEALING|BIDDING|ARCHIVED
  "managerId": 100,
  "teamMembers": [101, 102, 103],
  "startDate": "2024-01-01T00:00:00",
  "endDate": "2024-06-30T23:59:59",
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00"
}
```

### ApiResponse（统一响应）
```java
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

## 验证规则

### TenderRequest验证
- `title`: 必填，最大500字符
- `source`: 可选，最大200字符
- `budget`: 必填，非负数，最多15位整数2位小数
- `deadline`: 必填，必须是未来时间
- `aiScore`: 可选，0-100

### ProjectRequest验证
- `name`: 必填，最大500字符
- `tenderId`: 必填，正数
- `managerId`: 必填，正数
- `teamMembers`: 必填，非空列表
- `startDate`: 必填
- `endDate`: 必填，必须晚于startDate

## 常见错误码

| 状态码 | 描述 |
|--------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

## TDD流程回顾

本次实现严格遵循TDD流程：

1. **RED阶段** - 先编写测试
   - TenderTest.java
   - TenderRepositoryTest.java
   - TenderServiceTest.java
   - TenderIntegrationTest.java
   - ProjectTest.java
   - ProjectRepositoryTest.java
   - ProjectServiceTest.java
   - ProjectIntegrationTest.java

2. **GREEN阶段** - 实现代码
   - Tender.java
   - TenderRepository.java
   - TenderService.java
   - TenderController.java
   - Project.java
   - ProjectRepository.java
   - ProjectService.java
   - ProjectController.java

3. **REFACTOR阶段** - 重构改进
   - 提取DTO
   - 添加验证注解
   - 统一异常处理
   - 优化查询方法

## 统计数据

- **实体类**: 2个
- **Repository接口**: 2个
- **Service类**: 2个
- **Controller类**: 2个
- **DTO类**: 6个（TenderDTO, TenderRequest, ProjectDTO, ProjectRequest等）
- **测试类**: 8个
- **API端点**: 22个
- **数据库表**: 3个（tenders, projects, project_team_members）
- **索引**: 10+个

## 下一步扩展

1. **功能扩展**
   - 标讯外部 API 同步和解析
   - AI分析服务集成
   - 工作流引擎
   - 文档管理
   - 通知系统

2. **性能优化**
   - Redis缓存
   - 批量操作
   - 查询优化
   - 分页查询

3. **监控运维**
   - 应用性能监控
   - 业务指标收集
   - 日志分析
   - 告警系统
