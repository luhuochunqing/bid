# 后端架构 Ratchet 候选清单

## 当前策略

- 继续以 `ArchitectureTest` 作为唯一架构门禁入口。
- 现阶段只扩大 ArchUnit 覆盖面，不同步引入 `core` 包、JaCoCo 新阈值或静态检查三件套。
- 架构治理类任务默认执行 `mvn test -Dtest=ArchitectureTest`。

## 已纳入的 ratchet 方向

- `settings`
- `fees`
- `projectworkflow`
- `resources`
- `casework`
- `analytics`

这些模块已纳入以下基础规则的覆盖面：

- `controller -> repository` 禁令
- `service -> controller` 禁令

其中 `controller -> entity` 已继续覆盖到：

- `settings`
- `fees`
- `projectworkflow`
- `resources`
- `casework`
- `analytics`

并且 `settings`、`projectworkflow`、`analytics` 的 DTO 包已补上“不依赖 entity”的门禁，避免接口层继续透传实体枚举或实体类型。

## 纯逻辑候选点

以下能力先登记为下一阶段 `policy/core` 候选，不在本轮直接抽层：

- `analytics`：评分规则、聚合规则
- `projectworkflow`：解析逻辑、判定逻辑
- `settings`：角色策略、权限策略
- `batch`：编排层与执行器边界
