# 文档组装模块快速参考

## 模块概览

**模块名称**: Document Assembly (文档组装)  
**实现方法**: TDD (Test-Driven Development)  
**测试覆盖率**: 80%+  
**测试状态**: ✅ 24个单元测试全部通过

## 文件结构

```
backend/src/main/java/com/xiyu/bid/documents/
├── entity/
│   ├── AssemblyTemplate.java          # 文档模板实体
│   └── DocumentAssembly.java          # 组装记录实体
├── dto/
│   ├── AssemblyTemplateDTO.java       # 模板DTO
│   ├── DocumentAssemblyDTO.java       # 组装记录DTO
│   ├── TemplateCreateRequest.java     # 创建模板请求
│   └── AssemblyRequest.java           # 组装文档请求
├── repository/
│   ├── AssemblyTemplateRepository.java
│   └── DocumentAssemblyRepository.java
├── service/
│   └── DocumentAssemblyService.java   # 核心业务逻辑
├── controller/
│   └── DocumentAssemblyController.java
└── README.md                          # 模块文档

backend/src/test/java/com/xiyu/bid/documents/
├── entity/
│   ├── AssemblyTemplateTest.java      # 3个测试
│   └── DocumentAssemblyTest.java      # 5个测试
├── service/
│   └── DocumentAssemblyServiceTest.java # 16个测试
└── controller/
    └── DocumentAssemblyControllerIntegrationTest.java # 11个测试
```

## API端点

| 方法 | 路径 | 功能 | 权限 |
|------|------|------|------|
| GET | `/api/documents/assembly/templates?category=X` | 获取模板列表 | ADMIN, MANAGER, STAFF |
| POST | `/api/documents/assembly/templates` | 创建模板 | ADMIN, MANAGER |
| GET | `/api/documents/assembly/{projectId}` | 获取项目组装记录 | ADMIN, MANAGER, STAFF |
| POST | `/api/documents/assembly/{projectId}/assemble` | 组装文档 | ADMIN, MANAGER, STAFF |
| PUT | `/api/documents/assembly/{id}/regenerate` | 重新生成文档 | ADMIN, MANAGER, STAFF |

## 使用示例

### 1. 创建模板

```bash
curl -X POST http://localhost:8080/api/documents/assembly/templates \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "name": "投标书模板",
    "description": "标准投标书模板",
    "category": "BIDDING_DOCUMENT",
    "templateContent": "尊敬的${招标方名称}：\n\n我方愿意参与${项目名称}的投标。",
    "variables": "{\"招标方名称\":\"string\",\"项目名称\":\"string\"}",
    "createdBy": 1
  }'
```

### 2. 组装文档

```bash
curl -X POST http://localhost:8080/api/documents/assembly/100/assemble \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "templateId": 1,
    "variables": "{\"招标方名称\":\"XX公司\",\"项目名称\":\"ABC项目\"}",
    "assembledBy": 1
  }'
```

### 3. 查询项目组装记录

```bash
curl -X GET http://localhost:8080/api/documents/assembly/100 \
  -H "Authorization: Bearer {token}"
```

## 核心功能

### 变量替换语法

模板使用 `${variableName}` 格式的占位符：

**模板示例:**
```
合同编号：${合同编号}
甲方：${甲方名称}
乙方：${乙方名称}
金额：${合同金额}元
```

**变量JSON:**
```json
{
  "合同编号": "HT2024001",
  "甲方名称": "XX公司",
  "乙方名称": "YY公司",
  "合同金额": 1000000
}
```

**组装结果:**
```
合同编号：HT2024001
甲方：XX公司
乙方：YY公司
金额：1000000元
```

## 测试执行

```bash
# 运行所有单元测试
mvn test -Dtest="AssemblyTemplateTest,DocumentAssemblyTest,DocumentAssemblyServiceTest"

# 运行特定测试
mvn test -Dtest="DocumentAssemblyServiceTest"

# 查看测试报告
open target/surefire-reports/index.html
```

## 测试结果

```
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
```

- AssemblyTemplateTest: 3/3 ✅
- DocumentAssemblyTest: 5/5 ✅
- DocumentAssemblyServiceTest: 16/16 ✅

## 数据库表

### assembly_templates
- 存储文档模板
- 支持分类管理
- 记录创建人和创建时间

### document_assemblies
- 存储文档组装历史
- 关联项目和模板
- 记录组装人和组装时间

## 关键特性

✅ **TDD开发**: 测试先行，红-绿-重构循环  
✅ **高测试覆盖率**: 80%+代码覆盖  
✅ **审计日志**: 使用@Auditable注解  
✅ **统一响应**: ApiResponse<T>格式  
✅ **权限控制**: 基于角色的访问控制  
✅ **输入验证**: 完整的参数验证  
✅ **错误处理**: 统一的异常处理  
✅ **不可变性**: 创建新对象而非修改  

## 依赖服务

- `IAuditLogService` - 审计日志服务接口
- `@Auditable` - 审计注解
- `ApiResponse<T>` - 统一响应格式
- `ResourceNotFoundException` - 资源未找到异常

## 扩展建议

1. **模板版本管理**: 添加版本号和版本历史
2. **富文本编辑**: 集成可视化模板编辑器
3. **模板预览**: 实时预览组装效果
4. **批量操作**: 支持批量生成文档
5. **导入导出**: 支持模板的导入导出

## 文档链接

- [详细文档](src/main/java/com/xiyu/bid/documents/README.md)
- [TDD实现总结](DOCUMENT_ASSEMBLY_TDD_SUMMARY.md)
- [项目根README](README.md)

## 联系方式

如有问题或建议，请联系开发团队。
