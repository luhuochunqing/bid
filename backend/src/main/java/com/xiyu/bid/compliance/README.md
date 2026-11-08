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
| `service/SidecarComplianceDocumentAdapter.java` | Adapter | 组合文本解析、页面图像抽取（含大体积 .docx 本地流式抽图回退）及百度OCR/多模态文字补充 |
| `service/DocxComplianceImageExtractor.java` | Service Helper | 大体积 .docx 标书关键合规页（承诺函、授权书、身份证、资质证书等）流式抽图与非目录页码绑定 |
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
| `domain/policy/QualificationVetoPolicy.java` | Policy | 占位与明确违规保留失败；强制证书在命中页具备证书编号与有效期实证时 PASS，仅名称命中或检索缺口转人工 |
| `domain/policy/DeterministicDepositExtractor.java` | Policy | 招标投标保证金/担保及政采响应担保确定性提取与免收识别纯核心策略 |
| `domain/policy/RuleCitationContext.java` | Policy | 区分条件规则引用与投标人自身违规，按命中语境定位证据 |
| `domain/policy/CommitmentSealCheckPolicy.java` | Policy | 承诺正文优先定位，目录/评分索引不作为签章事实 |
| `domain/policy/SemanticEvidenceBoundary.java` | Policy | 无定位、抽样跳页或图片文本缺口不得升级确定缺失；具备可验证证书编号+有效期、多维资格实证或身份证图像+正反面完整要素时保持正向 PASS |
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

## 资质与响应语境回归

- `CertificateRequirementPolicy`：单纯处罚/撤销条款不生成证书查询；包含明确持有义务的证书条件保留完整条款，适用性不明确时交人工核验。
- `QualificationPlaceholderPolicy`：逐个保留占位符及页内位置，结合证书身份、招标要求的作用对象和适用性核验。明确属于投标人强制证书的留白可判失败；多候选、可选或无法绑定时转人工。同一 ISO 编号的普通空格、大小写和连字符按完整编号比较；其他空格写法可能转人工。
- `ResponsivenessReviewPolicy`：回归招投标第一性原理，法定偏离表（商务/技术偏离表）为实质性响应与负偏离事实源；彻底废除全文正文扫描负词直接判定 FAIL 机制，合同解除条件不触发误判，真实负偏离精准定位偏离表或明确自述。

- 响应性引用核对：`ResponsivenessRuleCitation` 排除招标原文规则引用（如禁止负偏差），配合结构化偏离表法定直通 PASS；真实负偏离精准定位所属页原始行。

## 项目1360质量规则证据边界

- ScoringResponsePolicy：评分字段留白直接携带命中页和原文位置；合同示例、未知字段及目录召回缺口不直接FAIL。SemanticEvidenceBoundary阻止模型重新升级缺乏具体字段证据的评分失败。
- QualificationVetoPolicy：只核验资格声明字段自身的占位，证据不按下划线全文重搜。
- PricingSubmissionPolicy：明确系统折扣率报价且无另附表格要求时，Word不能证明系统填报状态，保留人工；传统表格要求不豁免。
- IdAuthLetterCheckPolicy及SemanticEvidenceBoundary：号码、图片标记、文字转写只供定位；当前没有身份证图像内容核验结果，不能自动证明附件齐全。
- SubItemAndAttachmentRules：文本片段数不等于完整卷册，非空文字不等于版式符合要求；保留原件核对。

## 项目1360 R2：应答角色与来源

- `ScoringResponsePolicy`：明确评分应答义务先于协议材料类别；参考样例及相冲突的应答角色保留人工，确定留白优先拦截。
- `EvidenceTextSlice`：评分和资格核验共用的纯值切片，保留输入页号；仅有全文时使用null，不创建页1。上游虚拟分页与物理分页的区分仍依赖解析层。
- `QualificationVetoPolicy`：占位与违规证据直接来自命中切片及位置，不再次全文检索相同文本。

## 项目1360 R3：评分指令解释

- `ScoringRequirementRolePolicy`：纯核心，解析顶层动作、对象集合、显式或继承极性与对象参考用途；括注内动作不作为新指令。相关但未完整绑定/条件不明的字段转人工，名称或词语提及不构成必填证明。
- `ScoringResponsePolicy`：消费角色，保留字段定位/原位证据；删除旧单段正则和角色聚合。对象及否定范围覆盖见`Quality1360InstructionScopeTest`，真实Engine和模型PASS/FAIL双向对照。
  非必填命中与未命中路径共用相邻行状态更新，明确必填缺陷仍优先返回。

- R3 fix第1轮：对象括注中的适用条件保留为AMBIGUOUS且不传播到同组其他对象；未绑定技术字段的词表仅召回人工复核，不建立必填义务。

- PR重跑清理：Engine出口按资格、授权、语义、报价、偏离要求的原顺序显式校准；身份证命中分支共用定位摘录，不改变判断及证据。
- 报价校准复用方法内的`requiresPaper`判断，明确另附纸面材料的阻断条件保持一致。
- 报价提取复用一次系统折扣率指令解析的原文，同时确定报价方式与渠道描述，避免重复扫描同一招标文本。

- fix第1轮：模态前否定保留人工；资格占位以句内字段边界隔离；系统报价动作内部否定不建立正向填报渠道。新增真实Engine及确定性提取反例保留正向对照。
- 清理第2轮：身份证定位分支仅在号码或图片标记命中后进入，移除不可达的通用身份证锚点；保持原位摘录和人工核验结论。

## 项目1360业务证据边界（P6）

当前验收见`specs/20261106-quality1360/business-acceptance.md`。原件/报告固定为144，六项误判纠正不等于25项全部合规。

- 评分/资格：已绑定的明确必填留白仍失败，延保样例和未知来源仅待确认；未解析来源不推定肯定或否定义务。分页证据不伪造物理页。
- 报价：直接系统折扣率指令保留原位证据，系统提交事实待人工核对。第二提交介词无法可靠绑定时不声明任何已识别渠道，不用目的地/时间/副词词表猜测；明确纸面true义务仍保留。
- 响应性：确定性负偏离事实仍失败，模型无对应事实的负结论待确认；通过必须绑定完整真实招标条款。
- 身份、组成和版式：文本及图片标记不足以证明实际材料，保留待确认；联系方式等已确认文字事实仍可通过。

`Quality1360BusinessAcceptanceTest`使用脱敏真实上下文及Engine→资格明细校准链，原文件本地完整文本回放另存私有证据。完整中文语义、视觉、物理分页、部署和线上重算均不由这些测试证明。P4/P5撤回的自动语义目标与失败证据保留在历史规格，不能继续作为当前承诺。

- P6 fix第1轮：报价渠道前的外层来源/条件也必须绑定；仅有限直接指令前缀可确认SYS，其余保留从句首开始的限定证据并人工确认。禁止把“若采用线上报价”或“尚未要求”截掉后生成肯定义务。

## 项目1360 HTML标签清洗与资质文案人性化

- `HTML标签清洗`：PDF/Docx 表格转换为 Markdown 单元格时，单元格内部换行会生成 `<br>` 或 `<br/>`（如 `EDI和<br>ICP`）。原始 HTML 在 `QualificationConditionExtractor` 条件装配或 `TenderRequirements` 未覆盖的旧文本入口剥离已知标签并解码一次；编号清理、证书分类与结构化条件匹配只消费已解码文本。模型重建复用已有结构化条件，避免将 `&lt;b&gt;` 解码所得字面 `<b>` 再当标签吞掉。直接匹配入口 `isQualificationMatch` 消费已解码文本，与 `findBestMatchingCert` 保持一致避免人员库二次解码；另提供 `isRawQualificationMatch` 用于未清洗的原始 HTML。证据保留原始引文，定位时按原始单元格一次清洗后比较，重复命中仍保持未知索引。
- `资质比对文案人性化`：`QualificationDetailReconciler` 不再向未确认条件备注追加技术化英文代码（如 `[UNKNOWN]`、`原检索状态=MATCHED` 等），转换为自然中文“条件适用范围或作用对象待确认；资质库已匹配有效证书...”；保留原始备注和人工复核状态。文案格式化为类内实现细节。

- P6 fix第2轮：评分来源采用完整直接来源绑定，历史/参考/未知来源不证明本次义务；报价载体限定单独绑定，非系统报价表/单不被认定为系统渠道，保留独立材料失败。新增真实Engine双模型对照和确定性提取/校准回归。

P6最终来源边界：用原句而非括注遮罩核对来源；逗号不创建可靠来源，同句协调动作保守传递未确认状态。只有明确本次来源/直接指令及精确字段对象才建立必填证明，字段名直接精确比较，名称子串不是对应关系；限定名与简写无可靠映射时人工。混合历史与当前来源可能继续人工，不承诺复杂转折语义全自动。

P7仅收紧报价对象证明：RATE_OBJECT_CANDIDATE只召回，动作后完整对象须精确属于既有折扣率词组才确认SYS。未解析的前后限定进入既有uncertain出口并保留完整对象原文；不增加否定词表，不改评分/资格等规则。两排除对象真实Engine先RED、281项关联GREEN，原件选定不变量及明确纸面义务保持。

P8最小修复：`PricingSubmissionPolicy.paperRequirements`只从明确直接提交条款建立纸面表/明细义务，免交与抽取冲突待确认，原位免交证据保留；`DeterministicPriceExtractor`共用同一条款结果。否定编号清单先区分条目自身事实，`RuleCitationContext`与`SubItemAndAttachmentRules`保留明确自身违规FAIL，局部否定豁免，未确认自身语境人工。不扩展接口、主体实体识别或模型调用。

P8 fix第1轮：关键词后自身语境只取主体所在首个分句，后续独立分句的否定词不得倒灌；普通自身否定保留豁免，未知自身语境仍人工。纸面表名/免交逻辑本轮无需修改，现有71项保护通过。

P8 fix第2轮：否定清单自身语境仅在主体及既有时态标记后的谓语起点检查否定，宾语“不实材料”不能豁免已提交事实；未知自身语境保留人工。新增5例，19项先3失败/0错误，随后6类239项零失败/错误/跳过；报价71项保持通过，不改报价及评分源码。

P9设计收敛：敏感词只在当前分句的直接主体/动作上裁决，不继承整条目首谓语；主题后置陈述人工。ScoringRequirementRolePolicy只保留单直接动作/对象列表，Prepared同次检查复用，括号仅做标点隔离、不推导内部语义或跨动作极性。PricingSubmissionPolicy.Analysis共用纸面条款/系统指令/原文，未知义务保留null，报价页号精确匹配选中原文。Engine调用包内LLM候选并在出口统一证据护栏；公开LLM接口仍带护栏。响应性在自身双文档入口完成一次最终校准，缺证直接人工，不再经Engine重复校准/模型负结论后重跑原始规则。新增人工结论复用ManualReasons。

P9 fix第1轮：评分同名字段不能证明当前出现位置属于实际应答；相邻合同示例/合同模板标题使该留白保留人工，不按“协议”材料名豁免明确必填。敏感词直接否认须完整匹配当前前缀（可带既有自身主体），不移除先前敏感词拼接动作，不用后缀匹配豁免复杂极性。直接否认/直接违规/真必填对照保留。

评分样例用途边界：已出现合同示例/合同模板线索后，普通字段和分页不能证明样例范围结束；后续同名必填留白保留人工，不推断范围或满分。线索前直接绑定的必填留白仍FAIL。

## 当前生效：用户裁定最小裁剪（2026-11-06）

用户已同意scope-reconciliation.md方案A：本次撤回自然文本评分留白自动FAIL，涉及所有项目此类检查，不做1360特判。评分留白仅召回人工核对及真实摘录；评分模型负结论也不单独判FAIL。无留白的评分保留原模型通道。资格缺陷、真实违规、负偏离、明确纸面提交义务的FAIL保护不变。

BA14取代旧BA05、BA10、BA13中评分直接必填自动FAIL及出现统计的承诺；保留这些输入作MANUAL回归，撤回ScoringRequirementRolePolicy，不重建统一字段解析器。其他BA条款仍有效。原25项6误判/8正确处理/11证据不足不变；不宣称全部25项准确或已线上生效。

历史三轮NEEDS_FIXES保留；人工裁定仅授权一次完整验收和独立审查窗口，再失败停止，不自动fix或重开。PR授权已有，不合并/部署/重算。health/codehealth不可用，本次显式以源码结构删减比较及三项既有架构测试替代，无工具健康评分声明。

本节为当前行为；前述R3/P6/P9评分解释器和硬判记载属于历史，不代表当前能力。ScoringResponsePolicy只召回留白；SemanticEvidenceBoundary不再重跑评分策略，Engine与公开LLM调用仅删除无用requirements参数。

PR结构收口：PricingSubmissionPolicy.instruction的句界定位只依赖当前渠道起点，4行原样移至动作循环之前；四层嵌套降为三层，无新方法或匹配规则，未知出口与原文索引不变。旧失败记录保留，交付仍需本轮新证据及独立审查。

2026-11-07经用户明确批准，报价纯结构收口：纸面义务原块提取为私有reconcileRequiredPaper，相邻同结果守卫合并；主方法5个return、私有方法2个return。匹配规则、文案、优先级、原文索引和测试预期不变；旧失败保留，最终以本轮新门禁及HEAD验收为准。

2026-11-07用户继续执行响应性纯结构收口：合并两个返回原对象的相邻守卫，空值优先及短路顺序不变，6return降为5；无新方法、规则或测试变化。旧失败保留，新门禁通过后才交付。

## 2026-11-07 第一刀 + 第二刀：解除后置硬编码待确认枷锁与接入百度OCR/大体积DOCX流式抽图

- **第一刀（后置证据边界解封与资格/评分/响应性收敛）**：
  - `QualificationConditionExtractor` / `CertificateRequirementPolicy`：过滤目录点线行与通用格式章节（投标文件的语言、第二卷技术标等），支持主语带关联公司括注（如“投标人（或与投标人为母子公司...）”）的强制属性推导，排除条件性补充授权/股权证明被误抽为独立证书。
  - `QualificationMatchPolicy` / `QualificationVetoPolicy` / `QualificationDetailReconciler` / `SemanticEvidenceBoundary`：当标书正文中对应证书命中页同时具备可验证的证书编号（许可证编号/证书编号/证号/合字等）与有效期限实证时，直接闭环判定 `PASS`（`MATCHED_REFERENCE` + `PASS`），不再无论有无实证一律降级 `MANUAL_REVIEW`；仅出现证书名称而无编号/有效期实证时仍保留 `MANUAL_REVIEW`。
  - `IdAuthLetterCheckPolicy` / `SemanticEvidenceBoundary`：当标书抽取到页面图像且正文中具备身份证正反面完整要素（18位号码、姓名、性别/民族/住址、签发机关、有效期限）时闭环判定 `PASS`；纯文本无图像或要素不全仍保留 `MANUAL_REVIEW`。
  - `BidLetterCheckPolicy` / `DeterministicBidLetterExtractor`：支持《投标人承诺函》作为投标函变体的识别与核心要素核验。
  - `ScoringResponsePolicy`：识别《延保服务协议书》等合同样板留白，避免样板下划线连坐阻断评分项响应。
  - `ResponsivenessReviewPolicy` / `RuleCitationContext` / `SubItemAndAttachmentRules` / `DeterministicDepositExtractor`：支持无表格差异表“无差异”正向通过、处罚警示语豁免、具备目录与商务/技术主卷册时文件组成 `PASS`、以及实质性长招标文件全文未提及保证金时判定为 `【不涉及】` 免收通过。
- **第二刀（百度OCR通道复用与大体积DOCX按需流式抽图）**：
  - `DocxComplianceImageExtractor` / `SidecarComplianceDocumentAdapter`：针对超过 Sidecar 64MB 媒体限制跳过抽图或无物理页码的 `.docx` 投标文件，通过 `ZipFile` + StAX 流式扫描合规章节锚点后的内嵌图片，并绑定到 `DocumentContent` 对应非目录逻辑页码 `pageHint`。
  - `OpenAiDocumentPageVisionDetector` / `ScanPageTextPolicy`：优先调用后台已配置的 `BaiduTableOcrClient`（APIHub `BAIDU_OCR_DOC_API`）识别合规页图片文字，按单图级追踪识别状态，单图未命中或异常时回退既有结构化多模态通道并按页合并转写结果，写回 `DocumentContent` 参与确定性与 LLM 核查。
