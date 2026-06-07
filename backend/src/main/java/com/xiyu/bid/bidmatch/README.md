# 投标匹配评分模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明

`bidmatch` 提供“投标匹配评分模型”的后端主链路：维护自定义评分模型与版本快照，基于标讯、资质、案例和中标结果证据生成真实匹配评分，并把总分、维度分、规则命中和证据返回给前端展示。

本模块只表达评分建议，不替代销售、经理或法务对是否投标、报价策略和商务风险的人工判断。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `domain/` | 纯核心 | 评分模型、维度、规则、证据、校验结果和评分计算策略 |
| `application/` | Application Service / Mapper | 编排模型读取、版本快照、评分生成、证据组装和 JSON 转换 |
| `controller/` | API 边界 | 暴露模型配置、模型启用、标讯评分读取和生成接口 |
| `dto/` | DTO | API 入参、出参和前端展示 payload |
| `infrastructure/persistence/entity/` | JPA Entity | 评分模型、模型版本和评分结果持久化结构 |
| `infrastructure/persistence/repository/` | Repository | 评分模型、版本和评分结果的数据访问 |

## 纯核心边界

- `domain/*` 受 `FPJavaArchitectureTest` 保护。
- `domain/*` 不依赖 Spring、JPA、Repository、日志、IO、时间、随机数或异常业务流。
- 业务失败通过 `ValidationResult` 等返回值表达，不用异常承载预期分支。
- `application/*` 只做取数、保存、事务、边界转换和调用纯核心。
- `controller/*` 只做 HTTP 入出参转换，不承载评分规则。

## 关键链路

1. 管理员在系统设置中维护自定义维度、规则和权重，并启用投标匹配评分模型。
2. 业务侧进入标讯详情或 AI 分析页时读取当前标讯评分。
3. 用户触发生成评分后，应用层读取已启用模型与当前证据快照。
4. `BidMatchScoringPolicy` 计算总分、维度分、规则命中状态和证据摘要。
5. 应用层持久化评分结果，并通过 API 返回给前端评分面板。

## 关键 API

- `GET /api/bid-match/models`
- `POST /api/bid-match/models`
- `PUT /api/bid-match/models`
- `POST /api/bid-match/models/{modelId}/activate`
- `GET /api/tenders/{tenderId}/match-score/latest`
- `GET /api/tenders/{tenderId}/match-score/history`
- `POST /api/tenders/{tenderId}/match-score/evaluate`
