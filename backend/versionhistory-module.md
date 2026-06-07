# 版本历史模块 (Version History Module)

## 模块概述

版本历史模块提供文档版本管理功能，支持版本创建、查询、比较和回滚操作，确保文档变更的可追溯性。

## 目录结构

```
backend/src/main/java/com/xiyu/bid/versionhistory/
├── entity/
│   └── DocumentVersion.java           # 文档版本实体
├── dto/
│   ├── DocumentVersionDTO.java        # 文档版本数据传输对象
│   ├── VersionCreateRequest.java      # 创建版本请求DTO
│   └── VersionDiffDTO.java            # 版本差异DTO
├── repository/
│   └── DocumentVersionRepository.java # 版本数据访问层
├── service/
│   └── VersionHistoryService.java     # 版本历史业务逻辑
└── controller/
    └── DocumentVersionController.java # 版本管理HTTP端点

backend/src/test/java/com/xiyu/bid/versionhistory/
├── VersionHistoryServiceTest.java     # 服务层单元测试 (42个测试)
└── DocumentVersionControllerTest.java # 控制器集成测试 (29个测试)
```

## 核心功能

### 1. 实体: DocumentVersion

文档版本实体，存储文档的历史版本信息。

**字段说明:**
- `id`: 版本唯一标识
- `projectId`: 关联项目ID
- `documentId`: 外部文档引用ID（可选）
- `versionNumber`: 版本号（自动递增）
- `content`: 文档内容（TEXT类型）
- `filePath`: 大文件存储路径（可选）
- `changeSummary`: 变更摘要
- `createdBy`: 创建用户ID
- `createdAt`: 创建时间
- `isCurrent`: 是否为当前版本

**数据库索引:**
- `idx_project_id`: 项目ID索引
- `idx_document_id`: 文档ID索引
- `idx_project_current`: 项目+当前版本组合索引

### 2. Repository: DocumentVersionRepository

数据访问接口，提供版本查询操作。

**核心方法:**
```java
// 获取项目的下一个版本号
Integer getNextVersionNumber(Long projectId);

// 按创建时间倒序获取项目的所有版本
List<DocumentVersion> findByProjectIdOrderByCreatedAtDesc(Long projectId);

// 获取项目的当前版本
Optional<DocumentVersion> findCurrentVersionByProjectId(Long projectId);

// 根据ID查询版本
Optional<DocumentVersion> findById(Long id);

// 统计项目的版本数量
Long countByProjectId(Long projectId);
```

### 3. Service: VersionHistoryService

业务逻辑层，实现版本管理的核心功能。

#### 3.1 创建版本 (createVersion)

```java
@Transactional
public DocumentVersionDTO createVersion(VersionCreateRequest request)
```

**功能:**
- 创建新的文档版本
- 自动分配版本号
- 将旧版本标记为非当前
- 记录审计日志

**验证规则:**
- 项目ID不能为空
- 内容不能为空
- 创建用户不能为空
- 未提供变更摘要时使用默认值

**示例:**
```java
VersionCreateRequest request = VersionCreateRequest.builder()
    .projectId(100L)
    .documentId("doc-001")
    .content("文档内容")
    .changeSummary("更新了第三章")
    .createdBy(1L)
    .build();

DocumentVersionDTO version = versionHistoryService.createVersion(request);
```

#### 3.2 查询版本 (getVersionsByProject)

```java
public List<DocumentVersionDTO> getVersionsByProject(Long projectId)
```

**功能:**
- 获取指定项目的所有版本
- 按创建时间倒序排列

#### 3.3 获取单个版本 (getVersion)

```java
public DocumentVersionDTO getVersion(Long versionId)
```

**功能:**
- 根据版本ID获取版本详情
- 版本不存在时抛出ResourceNotFoundException

#### 3.4 获取最新版本 (getLatestVersion)

```java
public DocumentVersionDTO getLatestVersion(Long projectId)
```

**功能:**
- 获取项目的当前版本（isCurrent=true的版本）
- 不存在时抛出ResourceNotFoundException

#### 3.5 比较版本 (compareVersions)

```java
public VersionDiffDTO compareVersions(Long versionId1, Long versionId2)
```

**功能:**
- 比较两个版本的差异
- 使用逐行比较算法
- 返回差异列表

**差异类型:**
- 内容添加: "Content added: ..."
- 内容删除: "Content removed: ..."
- 行变更: "Line X changed from '...' to '...'"
- 行添加: "Line X added: ..."
- 行删除: "Line X removed: ..."

#### 3.6 回滚版本 (rollbackToVersion)

```java
@Transactional
public DocumentVersionDTO rollbackToVersion(Long projectId, Long versionId, Long userId)
```

**功能:**
- 回滚到指定历史版本
- 创建新版本而非直接修改
- 记录回滚操作到审计日志
- 将新版本标记为当前版本

**验证规则:**
- 项目ID不能为空
- 版本ID不能为空
- 用户ID不能为空
- 目标版本必须属于指定项目
- 必须存在当前版本

#### 3.7 标记当前版本 (markAsCurrent)

```java
@Transactional
public void markAsCurrent(Long versionId)
```

**功能:**
- 将指定版本标记为当前版本
- 将原当前版本标记为非当前
- 用于版本恢复场景

### 4. Controller: DocumentVersionController

REST API控制器，提供HTTP端点。

#### 4.1 获取项目所有版本

```
GET /api/documents/{projectId}/versions
```

**响应示例:**
```json
{
  "success": true,
  "code": 200,
  "data": [
    {
      "id": 2,
      "projectId": 100,
      "versionNumber": 2,
      "content": "更新内容",
      "changeSummary": "更新了第三章",
      "createdAt": "2024-03-02T10:00:00",
      "isCurrent": false
    },
    {
      "id": 1,
      "projectId": 100,
      "versionNumber": 1,
      "content": "初始内容",
      "changeSummary": "初始版本",
      "createdAt": "2024-03-01T10:00:00",
      "isCurrent": true
    }
  ]
}
```

#### 4.2 获取最新版本

```
GET /api/documents/{projectId}/versions/latest
```

#### 4.3 获取指定版本

```
GET /api/documents/{projectId}/versions/{versionId}
```

#### 4.4 创建新版本

```
POST /api/documents/{projectId}/versions
Content-Type: application/json

{
  "projectId": 100,
  "documentId": "doc-001",
  "content": "新的文档内容",
  "filePath": "/path/to/file.docx",
  "changeSummary": "更新了第三章",
  "createdBy": 1
}
```

**响应:** 201 Created

#### 4.5 比较两个版本

```
GET /api/documents/{projectId}/versions/{v1}/compare/{v2}
```

**响应示例:**
```json
{
  "success": true,
  "data": {
    "version1Id": 1,
    "version2Id": 2,
    "version1Number": 1,
    "version2Number": 2,
    "content1": "初始内容",
    "content2": "更新内容",
    "differences": [
      "Line 1 changed from '初始内容' to '更新内容'"
    ]
  }
}
```

#### 4.6 回滚到指定版本

```
POST /api/documents/{projectId}/versions/{versionId}/rollback?userId=1
```

**响应示例:**
```json
{
  "success": true,
  "message": "Rolled back successfully",
  "data": {
    "id": 3,
    "projectId": 100,
    "versionNumber": 3,
    "content": "初始内容",
    "changeSummary": "Rollback to version 1",
    "isCurrent": true
  }
}
```

## 测试覆盖

### 单元测试 (VersionHistoryServiceTest)

**测试数量:** 42个

**测试覆盖:**

1. **createVersion测试** (8个)
   - 正常创建版本
   - 缺少必填字段验证
   - 空内容验证
   - 已有当前版本的处理
   - 默认变更摘要
   - 超长内容处理

2. **getVersionsByProject测试** (3个)
   - 获取版本列表
   - 空列表处理
   - 项目ID验证

3. **getVersion测试** (3个)
   - 正常获取版本
   - 版本不存在异常
   - 版本ID验证

4. **getLatestVersion测试** (3个)
   - 获取最新版本
   - 无版本异常
   - 项目ID验证

5. **compareVersions测试** (8个)
   - 正常比较
   - 相同内容
   - 缺少版本ID验证
   - 版本不存在异常
   - null内容处理
   - 多行内容比较

6. **rollbackToVersion测试** (7个)
   - 正常回滚
   - 必填字段验证
   - 版本不存在异常
   - 项目不匹配异常
   - 无当前版本异常
   - 当前版本状态更新
   - 大项目ID处理

7. **markAsCurrent测试** (4个)
   - 正常标记
   - 版本ID验证
   - 版本不存在异常
   - 无当前版本处理
   - 已是当前版本处理

8. **边界情况测试** (6个)
   - 特殊字符处理
   - 空字符串比较
   - 反向比较
   - 空文档ID处理

### 集成测试 (DocumentVersionControllerTest)

**测试数量:** 29个

**测试覆盖:**

1. **GET /api/documents/{projectId}/versions** (4个)
   - 正常获取
   - 空列表
   - 无效项目ID
   - 零项目ID

2. **GET /api/documents/{projectId}/versions/latest** (3个)
   - 正常获取
   - 项目不存在
   - 无效项目ID

3. **GET /api/documents/{projectId}/versions/{versionId}** (3个)
   - 正常获取
   - 版本不存在
   - 无效版本ID

4. **POST /api/documents/{projectId}/versions** (6个)
   - 正常创建
   - 项目ID不匹配
   - 内容验证
   - 创建者验证
   - 超大内容处理
   - 特殊字符处理

5. **GET /api/documents/{projectId}/versions/{v1}/compare/{v2}** (6个)
   - 正常比较
   - 相同版本比较
   - 版本不存在
   - 无效版本ID验证
   - 反向比较

6. **POST /api/documents/{projectId}/versions/{versionId}/rollback** (4个)
   - 正常回滚
   - 缺少用户ID
   - 版本不存在
   - 不同用户回滚

7. **边界情况测试** (3个)
   - 特殊字符处理
   - 超大项目ID
   - 可选字段处理

## 测试执行

### 运行所有版本历史测试

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test -Dtest='*Version*'
```

### 运行服务层测试

```bash
mvn test -Dtest=VersionHistoryServiceTest
```

### 运行控制器测试

```bash
mvn test -Dtest=DocumentVersionControllerTest
```

## 测试质量指标

- **总测试数:** 71个
- **单元测试:** 42个
- **集成测试:** 29个
- **测试覆盖率:** 80%+
- **通过率:** 100%

## 技术特性

### 1. 数据不可变性
- 所有版本数据创建后不可修改
- 回滚创建新版本而非覆盖
- 保证历史数据的完整性

### 2. 审计日志集成
- 创建版本记录审计日志
- 回滚操作记录审计日志
- 支持操作追溯

### 3. 事务管理
- 创建版本使用@Transactional
- 回滚版本使用@Transactional
- 确保数据一致性

### 4. 异常处理
- ResourceNotFoundException: 资源不存在
- IllegalArgumentException: 参数验证失败
- GlobalExceptionHandler统一处理

### 5. 性能优化
- 数据库索引优化查询
- 分页支持（可通过Repository扩展）
- 差异算法优化

## 依赖项

```xml
<!-- Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-inline</artifactId>
</dependency>
```

## 使用示例

### 场景1: 创建文档版本

```java
// 1. 创建初始版本
VersionCreateRequest request1 = VersionCreateRequest.builder()
    .projectId(100L)
    .documentId("proposal-2024")
    .content("投标方案初稿")
    .changeSummary("初始版本")
    .createdBy(1L)
    .build();
DocumentVersionDTO v1 = versionHistoryService.createVersion(request1);

// 2. 更新内容，创建新版本
VersionCreateRequest request2 = VersionCreateRequest.builder()
    .projectId(100L)
    .documentId("proposal-2024")
    .content("投标方案初稿\n\n第二章：技术方案")
    .changeSummary("添加技术方案章节")
    .createdBy(2L)
    .build();
DocumentVersionDTO v2 = versionHistoryService.createVersion(request2);
```

### 场景2: 比较版本差异

```java
// 比较版本1和版本2
VersionDiffDTO diff = versionHistoryService.compareVersions(v1.getId(), v2.getId());

System.out.println("差异列表:");
diff.getDifferences().forEach(System.out::println);
// 输出:
// Line 2 added: 第二章：技术方案
```

### 场景3: 回滚到历史版本

```java
// 回滚到版本1
DocumentVersionDTO v3 = versionHistoryService.rollbackToVersion(100L, v1.getId(), 1L);

// v3是版本号为3的新版本，内容与v1相同
assert v3.getVersionNumber() == 3;
assert v3.getContent().equals(v1.getContent());
assert v3.getChangeSummary().equals("Rollback to version 1");
```

### 场景4: 查看版本历史

```java
// 获取项目的所有版本
List<DocumentVersionDTO> versions = versionHistoryService.getVersionsByProject(100L);

// 获取最新版本
DocumentVersionDTO latest = versionHistoryService.getLatestVersion(100L);

// 获取特定版本
DocumentVersionDTO v1 = versionHistoryService.getVersion(1L);
```

## 最佳实践

1. **版本命名**
   - 使用有意义的变更摘要
   - 简洁描述变更内容
   - 包含变更的章节或部分

2. **回滚策略**
   - 回滚前先比较差异
   - 记录回滚原因
   - 考虑创建分支而非直接回滚

3. **内容管理**
   - 大文件使用filePath存储
   - 小文本使用content存储
   - 定期清理旧版本

4. **权限控制**
   - 创建版本需要写权限
   - 回滚需要管理员权限
   - 查看版本需要读权限

## 未来扩展

- [ ] 版本标签功能（如"稳定"、"发布"）
- [ ] 版本分支支持
- [ ] 版本合并功能
- [ ] 差异可视化
- [ ] 版本导出
- [ ] 自动版本清理策略
- [ ] 版本搜索和过滤

## 相关模块

- **DocumentEditor:** 文档编辑器
- **Collaboration:** 协作模块
- **AuditLog:** 审计日志模块

## 联系方式

如有问题或建议，请联系开发团队。
