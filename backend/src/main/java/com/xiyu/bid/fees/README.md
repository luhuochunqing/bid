# Fees 模块 (费用管理模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
费用模块负责投标相关费用的创建、审批、支付、退还和统计，覆盖费用生命周期管理。这里是项目资源侧的重要边界，核心关注状态流转和审计。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/FeeController.java` | Controller | 费用接口 |
| `service/FeeService.java` | Service | 费用流程编排、项目访问权限断言 |
| `service/FeeRequestValidator.java` | Pure Helper | 创建费用请求纯校验 |
| `service/FeeMapper.java` | Pure Helper | 费用实体到 DTO 的纯映射 |
| `service/FeeStatisticsFactory.java` | Pure Helper | 费用统计 DTO 纯组装 |
| `entity/Fee.java` | Entity | 费用实体 |
| `repository/FeeRepository.java` | Repository | 费用数据访问 |
| `dto/FeeDTO.java` | DTO | 费用视图对象 |
| `dto/FeeCreateRequest.java` | DTO | 创建费用请求 |
| `dto/FeeUpdateRequest.java` | DTO | 更新费用请求 |
| `dto/FeeStatisticsDTO.java` | DTO | 费用统计对象 |
