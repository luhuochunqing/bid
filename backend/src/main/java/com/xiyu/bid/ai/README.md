# AI 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
AI 分析能力的后端入口，负责标讯分析、项目分析与 AI Provider 适配。
该模块只负责能力编排，不承载业务主数据或页面状态。
真实接入、后台动态路由与 mock 接入通过 Provider 抽象切换。
新增能力默认走真实 API 单一路径，不再扩散 demo/mock 分支。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `client/` | 子目录 | AI 提供商接入边界 |
| `client/AiProvider.java` | Interface | AI 提供者统一抽象 |
| `client/AiProviderRuntimeConfig.java` | Record | 运行时厂商、模型、密钥配置 |
| `client/MockAiProvider.java` | Implementation | Mock AI 提供者 |
| `client/OpenAiProvider.java` | Implementation | OpenAI Java SDK + Responses API 真实接入实现，配置由 Spring 注入 |
| `client/OpenAiCompatibleClient.java` | Client | OpenAI-compatible Chat Completions 调用与响应解析 |
| `client/RoutingAiProvider.java` | Implementation | 根据后台系统设置动态选择 OpenAI / DeepSeek / 通义千问 / 豆包 |
| `core/` | 纯核心 | 评分、风险、缺口、任务状态等无副作用业务规则 |
| `core/` | 纯核心 | 评分、风险、缺口、任务状态等无副作用业务规则 |
| `service/` | 子目录 | AI 分析编排边界 |
| `service/AiDeepCapabilityService.java` | Application Service | 查询、调用 Provider、写库、DTO 组装 |
| `service/AiDeepCapabilityAssembler.java` | Mapper/Assembler | AI 分析结果 DTO 组装 |
| `service/AiService.java` | Service | 标讯/项目分析服务 |
| `dto/` | 子目录 | AI 响应与评分边界 |
| `dto/AiAnalysisResponse.java` | DTO | AI 分析响应 |
| `dto/DimensionScore.java` | DTO | 维度评分结果 |
| `dto/ProjectAnalysisDTO.java` | DTO | 项目分析结果 |
| `config/` | 子目录 | 包边界声明（异步线程池由顶层 `config/AsyncConfig.java` 提供） |

## 纯核心边界

- `core/*` 受 `FPJavaArchitectureTest` 保护，不得依赖 Spring、Repository、JPA、日志、IO、时间或随机数。
- `client/*` 是外部模型供应商边界，可以使用 OpenAI SDK、配置和网络调用。
- `service/*` 只做应用编排，不新增评分/风险/缺口业务规则。
