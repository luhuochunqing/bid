# roleprofile 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
提供角色档案的中性支撑能力，供 admin 配置边界和根层业务 service 共同使用，避免 `admin` 与 `service` 包互相依赖。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `RoleProfileBootstrap.java` | Component | 根据 `RoleProfileCatalog` 补齐缺失的内置系统角色，并保留管理员已保存的角色权限配置 |

## 配置所有权
- `RoleProfileCatalog` 只定义内置角色的首次创建默认值。
- `RoleProfileBootstrap` 只能补齐缺失角色，或修正系统角色身份标记；不得在已有角色上覆盖管理员保存过的菜单权限、数据范围、项目范围、部门范围或启停状态。
- 已上线环境需要补权限默认值时，必须使用一次性迁移脚本；管理员主动恢复默认值时，必须走 `RoleProfileService.resetRole` 显式入口。
- 相关门禁由 `RoleProfileBootstrapArchitectureTest` 与 `RoleProfileServicePersistenceTest` 覆盖。
