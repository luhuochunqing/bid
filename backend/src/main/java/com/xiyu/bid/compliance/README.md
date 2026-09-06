# Compliance 模块 (合规检查模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
合规检查模块负责项目和标讯的规则检查、标书质量核查（25项规则与AI混合裁决）、检查结果记录以及风险评估输出。这里负责把合规规则、质量核查、检查结果和对外 API 边界收拢到统一位置。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ComplianceController.java` | Controller | 合规检查与标书质量核查接口 |
| `application/BidQualityCheckTaskAppService.java` | Application Service | 标书质量核查任务应用编排服务（毫秒级响应、流转控制） |
| `application/BidQualityCheckAsyncExecutor.java` | Async Executor | 标书质量核查后台异步执行器（aiTaskExecutor专用线程池） |
| `application/BidQualityCheckProgressUpdater.java` | Progress Updater | 标书质量核查进度与状态独立事务更新器（REQUIRES_NEW） |
| `service/ComplianceCheckService.java` | Service | 合规检查入口编排、结果持久化、DTO 组装 |
| `service/KnowledgeBaseOnDemandLoader.java` | Service | 四知识库真实检索（优先对接 BusinessQualificationRepository 企业资质库） |
| `service/ComplianceCheckPolicy.java` | Policy | 总体状态、风险分和默认风险建议等纯决策 |
| `service/ComplianceIssueFactory.java` | Factory | 统一构造规则失败与执行失败问题 |
| `service/ComplianceRuleEvaluator.java` | Evaluator | 规则定义解析与项目/标书规则求值 |
| `domain/policy/BidDocumentQualityEngine.java` | Domain Engine | 标书 25 项规则求值与 AI 混合裁决纯核心引擎 |
| `domain/policy/QualificationMatchPolicy.java` | Policy | 资质双向模糊匹配与三方比对清单纯核心算法（相关度打分优先、通用停用词防错配、国字头剥离与标书页码定位） |
| `domain/policy/DeterministicQualificationExtractor.java` | Policy | 招标文件硬性资格与专用资质确定性提取纯核心策略（通用主体资格与专用行业资质分流、导语过滤、序号标点清洗、双轨兜底） |
| `domain/policy/DeterministicDepositExtractor.java` | Policy | 招标投标保证金/担保及政采响应担保确定性提取与免收识别纯核心策略 |
| `domain/policy/SemanticPageRetriever.java` | Domain Service | 60K 预算语义页面智能召回器（单页超预算硬截断） |
| `domain/DocumentContent.java` | Domain Model | 文档结构化内容；分页优先级：Sidecar pages → \f 切分 → virtualPages 虚拟分页兜底（无分页符长文本按标题/段落切块） |
| `entity/ComplianceRule.java` | Entity | 合规规则实体 |
| `entity/ComplianceCheckResult.java` | Entity | 合规检查与标书质量核查结果实体 |
| `repository/ComplianceRuleRepository.java` | Repository | 合规规则数据访问 |
| `repository/ComplianceCheckResultRepository.java` | Repository | 检查结果数据访问 |
| `dto/ComplianceCheckResultDTO.java` | DTO | 检查结果视图对象 |
| `dto/QualityCheckTaskDTO.java` | DTO | 质量核查任务视图对象 |
| `dto/QualityCheckReportDTO.java` | DTO | 质量核查报告视图对象 |
| `dto/ComplianceIssue.java` | DTO | 合规问题对象 |
| `dto/RiskAssessmentDTO.java` | DTO | 风险评估对象 |
