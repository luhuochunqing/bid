# Document Editor Module

## Overview
文档编辑器模块提供了完整的文档结构和章节管理功能，支持创建、编辑、删除和重新排序文档章节。

## Features

- **文档结构管理**
  - 创建和获取项目文档结构
  - 支持多层级章节组织

- **章节管理**
  - 支持多种章节类型：CHAPTER, SECTION, SUBSECTION, TABLE, IMAGE, ATTACHMENT
  - 创建、更新、删除章节
  - 章节树形结构查询

- **章节排序**
  - 批量重新排序章节
  - 维护章节间的顺序关系

- **审计日志**
  - 自动记录所有修改操作
  - 支持操作追踪和审计

## Entities

### DocumentStructure
文档结构实体，表示一个项目的文档结构。

```java
@Entity
@Table(name = "document_structures")
public class DocumentStructure {
    private Long id;
    private Long projectId;
    private String name;
    private Long rootSectionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### DocumentSection
文档章节实体，表示文档中的一个章节。

```java
@Entity
@Table(name = "document_sections")
public class DocumentSection {
    private Long id;
    private Long structureId;
    private Long parentId;
    private SectionType sectionType;
    private String title;
    private String content;
    private Integer orderIndex;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

## API Endpoints

### Document Structure

#### Get Document Structure
```
GET /api/documents/{projectId}/editor/structure
```
获取指定项目的文档结构。

**Response:**
```json
{
  "success": true,
  "code": 200,
  "data": {
    "id": 1,
    "projectId": 100,
    "name": "标书文档",
    "rootSectionId": null,
    "createdAt": "2024-03-01T10:00:00",
    "updatedAt": "2024-03-01T10:00:00"
  }
}
```

#### Create Document Structure
```
POST /api/documents/{projectId}/editor/structure
```
创建新的文档结构。

**Request:**
```json
{
  "projectId": 100,
  "name": "标书文档"
}
```

**Response:**
```json
{
  "success": true,
  "code": 201,
  "data": {
    "id": 1,
    "projectId": 100,
    "name": "标书文档",
    "rootSectionId": null,
    "createdAt": "2024-03-01T10:00:00",
    "updatedAt": "2024-03-01T10:00:00"
  }
}
```

### Document Sections

#### Get Section Tree
```
GET /api/documents/{projectId}/editor/sections/tree
```
获取完整的章节树形结构。

**Response:**
```json
{
  "success": true,
  "code": 200,
  "data": [
    {
      "id": 1,
      "structureId": 1,
      "parentId": null,
      "sectionType": "CHAPTER",
      "title": "第一章 项目概述",
      "content": "项目概述内容...",
      "orderIndex": 0,
      "children": [
        {
          "id": 2,
          "structureId": 1,
          "parentId": 1,
          "sectionType": "SECTION",
          "title": "1.1 项目背景",
          "content": "项目背景内容...",
          "orderIndex": 0,
          "children": []
        }
      ]
    }
  ]
}
```

#### Add Section
```
POST /api/documents/{projectId}/editor/sections
```
添加新章节到文档结构。

**Request:**
```json
{
  "structureId": 1,
  "parentId": null,
  "sectionType": "CHAPTER",
  "title": "第一章 项目概述",
  "content": "项目概述内容...",
  "orderIndex": 0
}
```

**Response:**
```json
{
  "success": true,
  "code": 201,
  "data": {
    "id": 1,
    "structureId": 1,
    "parentId": null,
    "sectionType": "CHAPTER",
    "title": "第一章 项目概述",
    "content": "项目概述内容...",
    "orderIndex": 0,
    "metadata": null,
    "createdAt": "2024-03-01T10:00:00",
    "updatedAt": "2024-03-01T10:00:00",
    "children": []
  }
}
```

#### Update Section
```
PUT /api/documents/{projectId}/editor/sections/{id}
```
更新章节内容。

**Request:**
```json
{
  "title": "更新后的标题",
  "content": "更新后的内容",
  "metadata": "{\"key\": \"value\"}"
}
```

**Response:**
```json
{
  "success": true,
  "code": 200,
  "data": {
    "id": 1,
    "structureId": 1,
    "parentId": null,
    "sectionType": "CHAPTER",
    "title": "更新后的标题",
    "content": "更新后的内容",
    "orderIndex": 0,
    "metadata": "{\"key\": \"value\"}",
    "createdAt": "2024-03-01T10:00:00",
    "updatedAt": "2024-03-01T10:05:00",
    "children": []
  }
}
```

#### Delete Section
```
DELETE /api/documents/{projectId}/editor/sections/{id}
```
删除指定章节。章节必须没有子章节才能被删除。

**Response:**
```json
{
  "success": true,
  "code": 200,
  "message": "Section deleted successfully"
}
```

#### Reorder Sections
```
PUT /api/documents/{projectId}/editor/sections/reorder
```
批量重新排序章节。

**Request:**
```json
{
  "structureId": 1,
  "sectionOrders": {
    "1": 1,
    "2": 0,
    "3": 2
  }
}
```

**Response:**
```json
{
  "success": true,
  "code": 200,
  "message": "Sections reordered successfully"
}
```

## Section Types

| Type | Description |
|------|-------------|
| CHAPTER | 主要章节 |
| SECTION | 小节 |
| SUBSECTION | 子小节 |
| TABLE | 表格 |
| IMAGE | 图片 |
| ATTACHMENT | 附件 |

## Service Methods

### DocumentEditorService

```java
// 创建文档结构
DocumentStructureDTO createStructure(StructureCreateRequest request)

// 获取文档结构
DocumentStructureDTO getStructure(Long projectId)

// 添加章节
DocumentSectionDTO addSection(SectionCreateRequest request)

// 更新章节
DocumentSectionDTO updateSection(Long sectionId, SectionUpdateRequest request)

// 删除章节
void deleteSection(Long sectionId)

// 重新排序章节
void reorderSections(SectionReorderRequest request)

// 获取章节树
List<DocumentSectionDTO> getSectionTree(Long projectId)
```

## Testing

### Unit Tests
单元测试覆盖所有服务方法：
- 测试正常场景
- 测试边界条件（null、空值）
- 测试异常场景（资源不存在、权限验证）

### Integration Tests
集成测试覆盖所有API端点：
- 测试完整的请求-响应流程
- 测试数据库操作
- 测试权限控制

### Running Tests

```bash
# 运行单元测试
mvn test -Dtest=DocumentEditorServiceTest

# 运行集成测试
mvn test -Dtest=DocumentEditorControllerIntegrationTest

# 运行所有测试
mvn test

# 查看测试覆盖率
mvn test jacoco:report
```

## Database Schema

### document_structures Table
```sql
CREATE TABLE document_structures (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    root_section_id BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### document_sections Table
```sql
CREATE TABLE document_sections (
    id BIGSERIAL PRIMARY KEY,
    structure_id BIGINT NOT NULL,
    parent_id BIGINT,
    section_type VARCHAR(50),
    title VARCHAR(500) NOT NULL,
    content TEXT,
    order_index INTEGER,
    metadata TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

## Error Handling

所有错误都遵循统一的API响应格式：

```json
{
  "success": false,
  "code": 400,
  "message": "Error message describing what went wrong"
}
```

### Common Errors

| Code | Description |
|------|-------------|
| 400 | Bad Request - Invalid input |
| 404 | Not Found - Resource doesn't exist |
| 500 | Internal Server Error - Server error |

## Security

- 所有端点都需要身份验证
- 部分端点需要特定角色（ADMIN, MANAGER, STAFF）
- 所有操作都被审计日志记录

## Dependencies

- Spring Boot 3.2.0
- Spring Data JPA
- Lombok
- MySQL 8.0/H2
- Spring Security

## Future Enhancements

- 版本控制和章节历史
- 协作编辑支持
- 章节模板
- 富文本编辑器集成
- 导出为PDF/Word
- 章节搜索和全文检索
- 权限细化到章节级别
