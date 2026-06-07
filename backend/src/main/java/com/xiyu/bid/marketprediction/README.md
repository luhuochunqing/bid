# 商机预测模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
基于历史采购数据提供商机时间预测能力，帮助识别采购规律和预测下次招标时间。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/MarketPredictionController.java` | Controller | 商机预测接口 |
| `service/MarketPredictionService.java` | Service | 商机预测编排 |
| `domain/MarketPredictionPolicy.java` | Pure Core | 基础预测策略 |
| `domain/IntervalBasedPredictionPolicy.java` | Pure Core | 基于间隔的预测策略 |
| `domain/MarketPredictionResult.java` | Value Object | 预测结果 |
| `dto/MarketPredictionDTO.java` | DTO | 预测数据传输对象 |

## 架构边界
- `domain/*` 满足 FP-Java Profile：`final class` + `static` 方法，返回不可变值对象
- Service 仅做编排与数据聚合
