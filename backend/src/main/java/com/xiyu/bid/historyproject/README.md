> 一旦我所属的文件夹有所变化，请更新我。

# HistoryProject 模块

历史项目模块负责承接“项目归档 -> 历史快照 -> 案例沉淀输入”的应用边界。
该目录只管理历史项目快照的采集、查询与对外适配，不直接承担案例编辑或文档导出协议。
对外提供可追溯的归档快照能力，供案例库、归档查询和后续业务消费复用。

| 文件 | 地位 | 功能 |
|------|------|------|
| `application/` | 子目录 | 历史项目快照应用服务 |
| `application/HistoricalProjectSnapshotAppService.java` | AppService | 编排快照采集与读取 |
| `application/HistoricalProjectSnapshotCaptureCommand.java` | Command | 历史快照采集命令 |
| `dto/` | 子目录 | 历史项目快照协议对象 |
| `dto/HistoricalProjectSnapshotDTO.java` | DTO | 历史项目快照读模型 |
| `entity/` | 子目录 | 历史项目快照实体边界 |
| `entity/HistoricalProjectSnapshotRecord.java` | Entity | 归档快照持久化实体 |
| `repository/` | 子目录 | 历史项目快照访问边界 |
| `repository/HistoricalProjectSnapshotRecordRepository.java` | Repository | 历史项目快照数据访问 |
