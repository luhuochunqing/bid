# Systems 模块 (外部系统集成)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责

Systems 模块负责与外部系统（第三方平台、菜单/权限系统等）的集成适配，提供统一的外部系统接入入口。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `external/package-info.java` | 基础设施 | 外部系统集成包标记 |
| `external/ExternalMenuTreeNode.java` | 值对象 | 外部菜单树节点模型 |
| `external/ExternalMenuService.java` | 服务 | 外部菜单数据查询与同步 |
| `external/ExternalMenuResponse.java` | DTO | 外部菜单接口响应 |
| `external/SystemsExternalMenuController.java` | Controller | 外部菜单查询 REST 接口 |
