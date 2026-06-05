# Bootstrap 模块 (启动时初始化)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
启动时初始化层，存放 `ApplicationRunner` 实现，负责在应用启动后执行一次性种子化与检测逻辑。放置于此包（而非 `config`）是为了避免 ArchitectureTest RULE 9（config 包不得依赖 service/repository 包）的约束。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `DefaultAdminInitializer.java` | Bootstrap | 检测零用户状态，种子化默认管理员账户（dev/prod） |
| `LocalDevAccountInitializer.java` | Bootstrap | 在 dev profile 下补齐登录页提示的本地员工/经理账号 |
| `package-info.java` | Bootstrap | 包文档 |
