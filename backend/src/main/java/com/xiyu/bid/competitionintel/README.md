# CompetitionIntel 模块 (竞争情报模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
竞争情报模块负责竞争对手管理、竞争分析和胜率判断，服务于项目的市场与对手研判。这里是项目决策支持域的一部分，侧重分析记录和策略输入。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/CompetitionIntelController.java` | Controller | 竞争情报接口 |
| `service/CompetitionIntelService.java` | Service | 竞争对手与分析编排 |
| `entity/Competitor.java` | Entity | 竞争对手实体 |
| `entity/CompetitionAnalysis.java` | Entity | 竞争分析实体 |
| `repository/CompetitorRepository.java` | Repository | 竞争对手数据访问 |
| `repository/CompetitionAnalysisRepository.java` | Repository | 竞争分析数据访问 |
| `dto/CompetitorDTO.java` | DTO | 竞争对手视图对象 |
| `dto/CompetitionAnalysisDTO.java` | DTO | 竞争分析视图对象 |
| `dto/CompetitorCreateRequest.java` | DTO | 创建竞争对手请求 |
| `dto/AnalysisCreateRequest.java` | DTO | 创建分析请求 |
