# ROI 模块 (投资回报率分析模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
ROI 模块负责项目 ROI 计算、敏感性分析以及风险和假设条件记录，服务于投标收益判断。这里是分析型支撑域，不承载项目主状态。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ROIAnalysisController.java` | Controller | ROI 分析接口 |
| `service/ROIAnalysisService.java` | Service | ROI 计算与分析编排 |
| `entity/ROIAnalysis.java` | Entity | ROI 分析实体 |
| `repository/ROIAnalysisRepository.java` | Repository | ROI 分析数据访问 |
| `dto/ROIAnalysisDTO.java` | DTO | ROI 结果视图对象 |
| `dto/ROIAnalysisCreateRequest.java` | DTO | 创建 ROI 请求 |
| `dto/SensitivityAnalysisRequest.java` | DTO | 敏感性分析请求 |
| `dto/SensitivityAnalysisResult.java` | DTO | 敏感性分析结果 |
