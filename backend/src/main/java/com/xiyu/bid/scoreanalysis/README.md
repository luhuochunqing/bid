# ScoreAnalysis 模块 (评分分析模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
评分分析模块负责多维评分和风险等级计算，为项目决策提供结构化评分结果。这里是分析支撑域，输出评分、维度明细和风险判断。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ScoreAnalysisController.java` | Controller | 评分分析接口 |
| `service/ScoreAnalysisService.java` | Service | 评分分析编排 |
| `entity/ScoreAnalysis.java` | Entity | 评分分析实体 |
| `entity/DimensionScore.java` | Entity | 维度评分实体 |
| `repository/ScoreAnalysisRepository.java` | Repository | 评分分析数据访问 |
| `repository/DimensionScoreRepository.java` | Repository | 维度评分数据访问 |
| `dto/ScoreAnalysisDTO.java` | DTO | 评分分析视图对象 |
| `dto/DimensionScoreDTO.java` | DTO | 维度评分视图对象 |
| `dto/ScoreAnalysisCreateRequest.java` | DTO | 创建评分请求 |
| `RiskLevel.java` | Enum | 风险等级枚举 |
