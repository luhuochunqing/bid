# API Key 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
API Key 模块负责管理外部系统访问密钥的生成、验证、轮换与吊销，支持投标平台 API 对外开放时的认证授权。
密钥采用安全加密存储，支持设置权限范围、有效期和调用频率限制。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `application/ApiKeyService.java` | Service | API Key 管理服务 |
| `dto/ApiKeyResponse.java` | DTO | API Key 视图对象 |
| `dto/CreateApiKeyRequest.java` | DTO | 创建 API Key 请求 |
| `dto/CreateApiKeyResponse.java` | DTO | 创建 API Key 响应 |
| `entity/ApiKey.java` | Entity | API Key 数据实体 |
| `entity/ApiKeyStatus.java` | Enum | API Key 状态枚举 |
| `infrastructure/ApiKeyAdminController.java` | Controller | API Key 管理接口 |
| `infrastructure/ApiKeyAuthenticationFilter.java` | Filter | API Key 认证过滤器 |
| `infrastructure/ApiKeyRepository.java` | Repository | API Key 持久化仓库 |
