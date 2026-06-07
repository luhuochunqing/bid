# Platform 模块 (平台账户管理)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
平台账户模块负责账号的创建、借用、归还和密码加密存储，是平台资源生命周期的一部分。这里重点管理账户状态与审计入口。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/PlatformAccountController.java` | Controller | 平台账户接口 |
| `service/PlatformAccountService.java` | Service | 借用、归还与状态流转 |
| `entity/PlatformAccount.java` | Entity | 平台账户实体 |
| `repository/PlatformAccountRepository.java` | Repository | 平台账户数据访问 |
| `dto/PlatformAccountDTO.java` | DTO | 平台账户视图对象 |
| `dto/PlatformAccountCreateRequest.java` | DTO | 创建账户请求 |
| `dto/BorrowAccountRequest.java` | DTO | 借用账户请求 |
| `dto/PlatformAccountStatisticsDTO.java` | DTO | 账户统计信息 |
| `util/PasswordEncryptionUtil.java` | Util | 密码加密工具 |
