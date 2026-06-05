一旦我所属的文件夹有所变化，请更新我。

# warehouse

仓库信息管理模块 — 统一管理仓库基础信息、租约/服务信息、资料核验与附件。

## 文件清单

| 文件 | 功能 |
|------|------|
| `domain/WarehouseType.java` | 仓库类型枚举 (自营/云仓) |
| `domain/WarehouseStatus.java` | 仓库状态枚举 (使用中/即将到期/已到期/已关仓) |
| `infrastructure/WarehouseEntity.java` | JPA 实体 |
| `infrastructure/WarehouseRepository.java` | Spring Data JPA 仓库 |
| `controller/WarehouseController.java` | REST API 控制器 |
| `dto/WarehouseDTO.java` | 数据传输对象 |
