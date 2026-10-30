# 评分提取预算修复计划

## Summary
独立评分提取300秒预算，SDK零自动重试，6000字符文字分片，模型请求异常向任务失败传播；修复无方案null，不降低原文证据门。
## Technical Context
Java21/Spring Boot3.2/OpenAI SDK4.32.0；Vue界面无需新增分支，FAILED已有错误提示。无schema变化。
## Constitution Check
FP-Java：范围判断纯函数；请求/日志副作用仍在infra/application。真实socket与真实gateway验证，不增加业务Mock。原始客户文件不提交。默认5参数请求配置保留SDK重试2，评分专用6参数配置重试0。
## Project Structure
- biddraftagent/infrastructure/openai：RequestConfig兼容字段maxRetries；Resolver按score use-case解析独立timeout；Transport显式把maxRetries交给SDK。
- scoreparse/infrastructure/openai：缩小文字分片，SDK请求失败包装ScoreModelRequestException（安全消息+原cause）。
- scoreparse/application：技术异常不再整轮重试/结构降级；现有异步入口记录原cause并标记FAILED。
- scoreparse/application/MultimodalExtractionReviewPolicy：null schemeId保留未知归属。
## Implementation Steps
1. 配置隔离、null未知范围回归先RED。
2. 增加兼容请求配置字段；真实socket延迟/503测试RED证明旧transport重发。
3. 显式零重试、300秒评分预算、6000字符上下文；技术异常终止、语义复核保留。
4. 补合成金标与未知总分/正常AI兼容反例。
5. 清理无用代码；fresh focused/architecture/golden测试；独立review，私有原件实际模型回放。
6. 测试发布产物与回滚锚点准备；实际测试环境确认后验证1135同文件。生产最终发布仅在测试证据齐全及环境门禁满足后。
## Validation
mvn -Dtest=ScoreExtractionRequestBudgetTest,ScoreExtractionUnknownScopeTest,ScoreExtractionTransportBudgetTest,ScoreModelFailurePropagationTest test；既有SDK/提取/方案回归；GoldenCasesReplayTest；FPJavaArchitectureTest/MaintainabilityArchitectureTest等相关门禁。
## Rejected Alternatives
只把全局OPENAI_TIMEOUT调大影响其他业务且保留重试放大；只开OCR不会改变前置模型依赖；无限等待/自动补100分均拒绝。
## Complexity Tracking
不新增控制器或共享服务。最多一个新增异常类型；复用现有配置、传输与任务失败路径。
