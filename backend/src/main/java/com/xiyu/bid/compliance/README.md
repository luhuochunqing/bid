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
| `service/KnowledgeBaseOnDemandLoader.java` | Service | 真实检索（企业资质委托、品牌授权、人员资质、业绩库、CA证书库真实台账检索） |
| `service/QualificationQueryDelegate.java` | Service Delegate | 企业资质库查询委托（封装新业务资质库与旧资质库的优先回退逻辑） |
| `service/KnowledgeMatchFormatters.java` | Service Helper | 知识库匹配记录摘要描述与转换辅助工具（降低加载器行数） |
| `service/ComplianceCheckPolicy.java` | Policy | 总体状态、风险分和默认风险建议等纯决策 |
| `service/ComplianceIssueFactory.java` | Factory | 统一构造规则失败与执行失败问题 |
| `service/ComplianceRuleEvaluator.java` | Evaluator | 规则定义解析与项目/标书规则求值 |
| `domain/policy/BidDocumentQualityEngine.java` | Domain Engine | 标书 25 项规则求值与 AI 混合裁决纯核心引擎 |
| `domain/policy/CaPlatformNamePolicy.java` | Policy | 按平台定义与投标上下文筛选具体名称，排除泛称/公告网站，统一已核实的平台别名 |
| `domain/policy/CaCertificateEvidence.java` | Domain Model | 电子标 CA 证书纯领域证据模型（FP-Java 不可变 Record，支持平台匹配、过期、借用状态判定与 KnowledgeMatch 转换） |
| `domain/policy/CaCertificateEvidencePort.java` | Domain Port | 电子标 CA 证书证据查询端口（依赖倒置，resources 模块提供实现适配器） |
| `domain/policy/CaCertificateCheckPolicy.java` | Policy | 电子标 CA 证书质量核查策略（原文页码与真实台账比对；未确认平台或查询失败转人工，正文确定缺陷保留） |
| `domain/policy/QualificationMatchPolicy.java` | Policy | 资质双向模糊匹配与三方比对清单纯核心算法（相关度打分优先、通用停用词防错配、国字头剥离与标书页码定位） |
| `domain/policy/QualificationDetailReconciler.java` | Policy | 材料事实校准：已证实过期/等级不足为FAIL，检索未命中或证据不足为MANUAL_REVIEW |
| `domain/policy/DeterministicQualificationExtractor.java` | Policy | 招标文件硬性资格与专用资质确定性提取纯核心策略（通用主体资格与专用行业资质分流、导语过滤、序号标点清洗、Markdown 表格切分、长度门禁、评分与模板排除、双轨兜底） |
| `domain/policy/QualificationCondition.java` | Domain Model | 完整条件、来源及证书子项；作用对象、强制/可选/未知分开保存 |
| `domain/policy/CertificateRequirementPolicy.java` | Policy | 证书候选沿用既有类型能力，子句作用域不明保留UNKNOWN与依据 |
| `domain/policy/QualificationConditionExtractor.java` | Policy | 完整章节、表格单元格和连续段落组装，绑定实际原文来源 |
| `domain/policy/QualificationVetoPolicy.java` | Policy | 占位与明确违规保留失败；证书名称命中与检索缺口转人工，不代表完整资格已通过 |
| `domain/policy/DeterministicDepositExtractor.java` | Policy | 招标投标保证金/担保及政采响应担保确定性提取与免收识别纯核心策略 |
| `domain/policy/RuleCitationContext.java` | Policy | 区分条件规则引用与投标人自身违规，按命中语境定位证据 |
| `domain/policy/CommitmentSealCheckPolicy.java` | Policy | 承诺正文优先定位，目录/评分索引不作为签章事实 |
| `domain/policy/SemanticEvidenceBoundary.java` | Policy | 无定位、抽样跳页或图片文本缺口不得升级确定缺失 |
| `domain/policy/SemanticPageRetriever.java` | Domain Service | 60K 预算语义页面智能召回器（单页超预算硬截断） |
| `domain/DocumentContent.java` | Domain Model | 文档结构化内容；分页优先级：Sidecar pages → \f 切分 → virtualPages 虚拟分页兜底（无分页符长文本按标题/段落切块） |
| `domain/PageImage.java` | Domain Model | 质检任务内存中的页面图像及页码元数据，不落库 |
| `domain/policy/SealVisionFusionPolicy.java` | Policy | 文本签章结果与逐页视觉证据融合，输出页码、坐标和置信度证据 |
| `entity/ComplianceRule.java` | Entity | 合规规则实体 |
| `entity/ComplianceCheckResult.java` | Entity | 合规检查与标书质量核查结果实体 |
| `repository/ComplianceRuleRepository.java` | Repository | 合规规则数据访问 |
| `repository/ComplianceCheckResultRepository.java` | Repository | 检查结果数据访问 |
| `dto/ComplianceCheckResultDTO.java` | DTO | 检查结果视图对象 |
| `dto/QualityCheckTaskDTO.java` | DTO | 质量核查任务视图对象 |
| `dto/QualityCheckReportDTO.java` | DTO | 质量核查报告视图对象 |
| `dto/ComplianceIssue.java` | DTO | 合规问题对象 |
| `dto/RiskAssessmentDTO.java` | DTO | 风险评估对象 |

## 质量核查证据边界（spec 079）

- 招标规则中的违规词或条件式负偏离不等于投标人自述违规；仍检查同页后续真实声明。
- 相关页抽样显式标记，缺号不证明原PDF缺页；授权书/身份证等证据不足转人工，明确留白或正文数值矛盾仍失败。
- CA平台在所有构造入口剔除泛称，未知具体平台人工确认，不虚构“或称交易平台”。
- ISO标准码冲突禁止模糊匹配；查询与明细复用同标准最佳有效候选，保留过期/等级不足失败。
- 财务须由同句明确义务建立要求，否定、目录和可选模板不得生成硬性审计要求。
- 本次代码验证不替代部署后同文件hash强制重算；历史核查结果不会自动更新。

- R2历史检查点（2026-10-30）：结构化模型保留完整资格与来源，但当时已知强制证书的纯检索未命中仍沿旧UNMATCHED分支判FAIL，三轮后升级人工。该阻断由下述R3处理；历史检查点不作为当前实现结论。

- 人工确认后的R3材料事实映射：`QualificationDetail.Finding`区分LOOKUP_MISS、EVIDENCE_INCOMPLETE、MATCHED_REFERENCE、EXPIRED、GRADE_INSUFFICIENT、UNKNOWN；旧JSON不从备注猜测等级缺陷。校准使用事实原因，未命中及未证实的模型FAIL转人工，已证实过期、等级冲突及实际占位保持失败。证书独立level参与选证，原始名称仍用于定位；本地验证及审查见`.omc/*quality1030-material.md`，不代表生产报告已重算。

- R4审查修复：证书分类、匹配、证据和引用共用`CertificateRequirementPolicy.isCertificateReference`，完整子句不再被旧名称长度上限裁剪；有证书要求而无比对明细时转人工，Async始终进入校准。多级编号标题与章节结束边界共用编号语法，表格行解析独立保留原文来源。原有通用主体资格不进入证书清单的兼容性仍由回归保护。

- 编号使用`NumberPath`比较层级，`SourceLine`分别保留清洗文本、原文及条目开始标记；日期/金额保留数值，歧义范围的证书保持UNKNOWN且随来源绑定透传。已明确的强制证书依据不能被另一处未知重复条款掩盖。旧证书名称提取API继续保留长度约束，新完整条件入口复用同一语义关键词能力。

- R5最终事实入口：资质项的空/null明细不再提前保留模型FAIL。调用参数没有可用条目时回用结果中已有的结构化事实，再校准有效性；完整业务入口（四参）另校准适用性并优先核验真实占位。未知缺证断言转人工，适用且已证实的过期/等级冲突仍FAIL；二参兼容入口处理调用方已筛选的事实。其他检查项的null/空参数保留原结果与元数据。

- spec079 安全兜底：财务核查及复合资格性评审的自动 PASS 统一转 MANUAL_REVIEW，规则入口、LLM 入口和引擎输出共享边界；保留证据与明细，不代表财务条款解析已完成修复。

## 报告58程序证据链修复

- `DocumentEvidencePageSelector`：抽图关键词包含签章关键词，单次扫描选取关键业务页与稀疏扫描邻页，抽图集合包含全部签章目标，避免整本渲染先耗尽预算。
- `ScanPageTextPolicy` / `DocumentPageVisionDetector`：授权/身份证明附近至多8个低文本页允许真实视觉转写；只接受送检物理页、0.90以上置信度及有界非重复文本，哈希不变。文字转写不证明身份证正反面与授权签署有效。
- `AuthorizationLetterCheckPolicy`：实际填写的人员及权限/期限优先于组成说明与填写要求；不完整仍人工。
- `RuleCitationContext`：评分条件豁免须绑定当前命中和局部规则导语，不能遮蔽投标人自身事实。
- 退款账户核查需要户名、开户行和账号实值，不以退还流程/填写模板直接通过；未知适用性保留人工。
- 证书临期及资格财务人工门禁保持；不改变业务时点，不补章、不更新台账。

## 人工确认后的授权与退款字段契约

- `domain/policy/AuthorizationFieldPolicy.java`：人员、权限、期限三态；支持明确动作、代表公司参加投标、投标有效期关联、长期有效或有效有序日期，法人证明接受姓名加掩码身份证；`declaresAuthorizationFacts` 判定该页是否参与授权冲突比对（覆盖空格与跨行写法）；未覆盖格式人工，不验证真实身份。
- `domain/policy/AuthorizationLetterCheckPolicy.java`：不同内容授权页一律人工并保留原页证据，第二份无冒号写法不再被静默忽略；法定代表人证明标题或任意图片不直接豁免。
- `domain/policy/BidDocumentQualityEngine.java`：最终输出强制重用授权三态门禁，AI不能覆盖未知/占位人工。
- `domain/policy/RefundAccountEvidencePolicy.java`：唯一企业户名、银行名称及数字账号的文字完整性；元标签和掩码不构成实值，不验证账户归属/机构真实性。
