# 标讯源配置模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
提供标讯源配置的测试连接功能，支持验证第三方商机服务API端点的连通性。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/TenderSourceController.java` | Controller | 暴露 `/api/tender-sources/test-connection` 接口 |
| `application/TenderSourceConnectionTestService.java` | Application Service | 标讯源连接测试编排 |
| `domain/TenderSourceConnectionTestPolicy.java` | Pure Core | 连接测试业务逻辑 |
| `domain/TenderSourceConnectionResult.java` | Value Object | 连接测试结果 |
| `dto/TenderSourceTestRequest.java` | DTO | 测试连接请求 |
| `dto/TenderSourceTestResponse.java` | DTO | 测试连接响应 |

## API 端点
- `POST /api/tender-sources/test-connection` - 测试标讯源连接

## 架构边界
- `domain/*` 满足 FP-Java Profile：`final class` + `private` 构造 + `static` 方法，返回不可变值对象
- `application/*` 仅做参数转换与调用编排，不含业务规则
- Controller 仅做路由与权限控制
