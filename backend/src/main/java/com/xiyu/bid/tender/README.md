# Tender 模块 (标讯主数据模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
标讯模块负责标讯数据的查询、创建与更新，是投标机会识别和标讯主列表的后端入口。
检索走实体维护的 normalized 字段；精确维度查询使用普通索引，关键词/采购方包含查询在 MySQL 8.0 上使用全文索引能力。
销售/员工角色可创建标讯主数据，标讯源配置、更新、删除等管理动作仍由经理或管理员承担。
人工录入标讯时由 doc-insight 解析过的源附件会保存名称、类型和 `doc-insight://` 文件地址，后续项目详情页可复用该源附件完成招标文件解析，不要求用户重复上传。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/TenderController.java` | Controller | 标讯接口 |
| `service/TenderQueryService.java` | Service | 标讯条件检索与读取 |
| `service/TenderCommandService.java` | Service | 标讯创建、更新、删除与 AI 分析命令 |
| `service/TenderMapper.java` | Mapper | 标讯实体、DTO、请求对象转换 |
| `service/TenderSearchCriteria.java` | Query Model | 标讯检索条件 |
| `service/TenderSpecification.java` | Persistence Helper | 标讯动态查询规格 |
| `service/TenderService.java` | Service | 旧调用入口兼容门面 |
| `dto/TenderDTO.java` | DTO | 标讯视图对象 |
| `dto/TenderRequest.java` | DTO | 标讯创建/更新请求 |
