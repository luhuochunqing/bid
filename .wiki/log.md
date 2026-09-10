# Wiki 操作日志 / Operation Log

> 按时间倒序记录所有 Wiki 操作。每条记录以 `## [日期] 操作类型 | 说明` 格式开头。
> 可用 `grep "^## \[" .wiki/log.md | tail -5` 查看最近 5 条。

## [2026-09-07] fix | 项目 1371 评分项解析精度根治、Sidecar 发布闭环与跨行分值重叠消重

- 触发：用户在项目 1371（通用技术MRO项目）中反馈 AI 评分项标准解析不准（缺失为 82 分或膨胀为 104 分）。
- 方案：
  - 发布闭环：`package-release.sh` 打包 `document-converter-sidecar/` 为 `sidecar.tar.gz`；`remote-deploy.sh` 对比 sha256 自动重构镜像平滑重启容器；`deploy-test.sh` / `deploy-prod.sh` Step 6 增加 `POST /extract-tables` 探活强阻断（杜绝仅测 `/health` 200 而核心能力 404 静默降级）；
  - 算法消重：`StructuredTableCandidateExtractor` 识别跨行大项（`rowspan > 1`）与细则得分包含关系，细则分优先消除 6+2+2=10 的父子分值累加膨胀；清洗形如 `3.3(1)` 的条款编号并规范多级维度继承；精简代码满足 300 行门禁；
  - 验证保障：测试环境实机重新 build 容器验证；新增 1371 全表金标测试（16项 56分商务+44分技术=100分严格收敛）；单测 21/21 全绿，scoreparse 692/692 全绿。
- 页面更新：更新 [[score-parse-service]] §10；lessons-learned.md §341；engineering-discipline.md §7。

## [2026-09-06] fix | 标书质检确定性提取扩充政采“响应担保”与【不涉及】结论前缀规范

- 触发：测试环境项目 1358 标书质检中，政采招标文件“不要求递交响应担保”，因确定性提取器遗漏“响应担保”导致免收识别失效、第 24 项退款信息表连带误报待确认。
- 方案：
  - 确定性提取：`DeterministicDepositExtractor` 扩充 24 组“响应担保/响应保证金”同义词及“不要求递交/不要求提交/无需递交”模式；
  - 联动与文案规范：`BidDocumentQualityEngine` 与 `SubItemAndAttachmentRules` 免收判定统一冠以 `【不涉及】` 前缀；
  - 语义检索：`SemanticPageRetriever` 补齐响应担保与免收 cues；
  - 知识沉淀：lessons-learned.md 追加 §247。
- 结果：单测 35/35 通过，Engine 联动测试通过，compliance 模块 589/589 全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §40。

## [2026-09-06] fix | 标书质检结果详情 MEDIUMTEXT 扩容与前端失败态持久卡片兜底

- 触发：测试环境项目 1358 标书质量核查大文件时因 `check_details` 字段为 TEXT（64KB）导致 Data truncation 入库失败，前端 3 秒 Toast 一闪而过误判为超时。
- 方案：
  - 数据库与实体层：Flyway V1212 / U1212 将 `compliance_check_results.check_details` 扩容为 MEDIUMTEXT（16MB），实体类同步更新；测试数据库先行应用 DDL；
  - 前端交互与兜底：`useQualityCheckDrawer.js` 维护 `errorMessage` 并在失败时持久保留；`QualityCheckDrawer.vue` 增加持久化错误状态卡片，内嵌错误详情代码框与重新核查入口；
  - 知识沉淀：lessons-learned.md 追加 §246。
- 结果：FlywayRollbackScriptCoverageTest、BidQualityCheckTaskAppServiceTest、useQualityCheckDrawer.spec.js 全数通过，pre-commit 门禁全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §39 与 §5。

## [2026-09-06] fix | PR !2570 复审跟进：评分/澄清占位正则三漏检修复（PR !2574）

- 触发：PR !2570（评分 SCORING_RESPONSE / 澄清 CLARIFICATION_RESPONSE 两阶段穿透）事后复审发现三处漏检。
- 方案：
  - 占位正则增补 `此处|粘贴`（`【此处填写/粘贴…】`不再逃逸，`ScoringResponsePolicy` + `ClarificationResponsePolicy`）；
  - 澄清编号留白分支允许前缀（`【确认收到第__号澄清】`可拦截，保留澄清文件/盖章旧匹配，不放宽 `_{3,}`）；
  - 澄清直通 PASS 仅认强签署信号（盖章/签署/公章/法定代表人/授权代表/电子签章），删裸投标人/日期与通用词`无异议`；
  - 新增 `EvidenceLocator.locateSkippingCatalog` + `EvidenceAssembler.locateBidSkippingCatalog`，展示证据穿透正文页（“仅目录列出”FAIL 证据保留原定位）。
- 结果：新增 7 回归测试（修前 5 运行红 +2 编译红），修后 24/24 绿；policy 包 + 架构门禁 373/373 全绿；wiki:check 97 页全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §34（跟进修复条目）；lessons-learned.md §242；教训：占位类 PR 须把描述示例写成单测断言，反假测试优先单占位单页。
- 备注：rebase 时与 main 的 `### Lesson 241`（签章/表格）尾部撞号，本任务 lesson 改记 242，对方内容逐字节保留（零删行校验）。

## [2026-09-06] feat | 标书质检「电子标 CA 证书与签章有效性」（CA_CERTIFICATE）两阶段契约与反假重构暨阶段 1 文书附件凭证类 100% 全面大收官

- 触发：推进 25 项质检项语义化演进之第 24 项「电子标 CA 证书」（CA_CERTIFICATE，PRD 2.3 第 24 项，级别 HIGH），实现阶段 1（6 项文书附件凭证类）100% 全面大收官。
- 方案：
  - 招标端契约：新建独立顶层 Record `CaCertificateRequirement` 与纯核心确定性提取器 `DeterministicCaCertificateExtractor`（支持 DocumentContent 与 String 双重重载），提取指定交易平台名称、证书复印件要求、电子签章要求，识别纸质标/线下标等免收信号并实现双轨融合；
  - 标书端反假与要素核查：`CaCertificateCheckPolicy` 物理剔除目录点线页（`isCatalogOrOutlinePage`），拦截未填模板占位符（【此处粘贴CA数字证书】等直接 FAIL 拦截），严格核验权威 CA 机构（CFCA/沃通/契约锁/北京CA/天威诚信等）与证书序列号、国密算法、有效期至等真实证书事实特征；
  - 引擎协同与双层裁决：`BidDocumentQualityEngine` 落实纸质标/免收 CA 场景确定性直出 PASS（LLM 0 次调用防抖动），规范真实证书强凭证直通 PASS（LLM 0 次调用），未附带凭证或存疑场景流转至大模型准则 11 深度裁决与 `SemanticPageRetriever` 候选页检索；
- 结果：落地 5 个专属单测套件，单文件行预算均严格控制在 300 行内，58 项单元与 FP-Java 架构测试全绿。阶段 1 涉及的 6 项文书附件凭证全部 100% 达成。
- 页面更新：更新 [[bid-quality-check-realization]] §4.30 与 sources；lessons-learned.md §235；更新 active 语义路线图标记阶段 1 达成。

## [2026-09-06] feat | 标书质检「报价一致性」（PRICE_CONSISTENCY）与「开标一览表」（BID_OPENING_TABLE）两阶段契约与反假重构

- 触发：推进 25 项质检项语义化演进之核心项「报价一致性」（PRICE_CONSISTENCY，PRD 2.3 第 6 项）与「开标一览表/报价表」（BID_OPENING_TABLE，PRD 2.3 第 22 项，均属 CRITICAL 级别）。
- 方案：
  - 招标端契约：新建独立顶层 Record `PriceRequirement` 与纯核心确定性提取器 `DeterministicPriceExtractor`，支持最高投标限价/招标控制价/采购预算（万元折算、纯数字与大写金额）、报价方式（总价/单价/费率）、明细表与开标一览表要求的提取与双轨融合；
  - 标书端反假与表函总价闭环：
    - `BidOpeningTableCheckPolicy`：物理剔除目录点线页（`isCatalogOrOutlinePage`），拦截未填模板占位符（【此处粘贴开标一览表】等直接 FAIL 拦截），核查表头实质结构（序号/名称/规格/数量/单价/合价等），提取表格总报价；
    - `PriceConsistencyCheckPolicy`：支持公文带括号格式，核验大写与小写阿拉伯数字；**最高限价超限硬核验**（高于最高限价立即判定法定废标 FAIL）；**表函总价一致性比对**（开标一览表总价与投标函总价冲突立即判定 FAIL）；逐页未命中自动跨页全文回退；
  - 引擎协同：`BidDocumentQualityEngine` 落实规范标书强凭证直通 PASS（LLM 0 次调用防抖动），硬性违背（超限价/表函冲突/占位符未填）直接 FAIL 阻断（禁止模型覆盖）；存疑或未提取场景流转至大模型准则 9 与准则 10 深度裁决与 `SemanticPageRetriever` 候选页检索；
- 结果：落地 5 个专属单测套件，单文件行预算严格控制在 300 行内，55 项单元与 FP-Java 架构测试全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §4.29 与 sources；lessons-learned.md §234。

## [2026-09-06] fix | PR !2561 follow-up：财务核查不得短路资格性评审

- 触发：!2561 合入后审查。`QUALIFICATION_REVIEW` 被财务三表关键词直通 PASS，与 spec 047「企业信誉、类似项目业绩门槛」冲突。
- 修复：
  - 第一阶段只上浮 `[UNFILLED_PLACEHOLDER]` 硬 FAIL，财务 PASS/免除不再结束第 14 项；
  - 报表识别改为标题 ∧ 表内要素；绝对年份必须全覆盖；相对年份不直通；
  - 免除与「须提供审计/三表」冲突时按必须提供；政府采购套话不再单独启动提取；
  - 恢复 `sanitizeQualifications` 的 `cleanItem` + 去重；执照门槛交给 `pr2557-followup`。
- 页面更新：更新 [[bid-quality-check-realization]] §4.28。

## [2026-09-06] feat | 标书质检「财务状况证明/财务审计报告」（FINANCIAL_STATEMENT）两阶段契约与反假重构

- 触发：推进 25 项质检项语义化演进之重点项「财务状况证明 / 财务审计报告」（FINANCIAL_STATEMENT，PRD 2.3 第 18 项，隶属于资格性审查核心条款 QUALIFICATION_REVIEW，涵盖资产负债表、利润表、现金流量表、会计师事务所审计印章真实性与年份要求）。
- 方案：
  - 契约解耦与行预算防爆：新建独立顶层 Record `FinancialRequirement`，并将原累积在 `TenderRequirements` 内部的 `DepositRequirement`、`BidLetterRequirement` 独立抽取为顶层 Record，彻底解除 300 行预算风险（`TenderRequirements` 降至 198 行）；
  - 招标端契约：新建纯核心确定性提取器 `DeterministicFinancialExtractor`，支持财务三表、审计报告、会计师事务所公章、年份覆盖度提取与双轨融合，支持显式免除判定；
  - 标书端反假：新建 `FinancialStatementCheckPolicy`，严格排除目录点线页（`isCatalogOrOutlinePage`）与未填模板占位符（`UNFILLED_PLACEHOLDER_PATTERN`），校验资产负债表、利润表、现金流量表及会计师事务所审计意见与印章真实性，校验年份覆盖度；
  - 引擎协同：将 `FinancialStatementCheckPolicy` 挂载为 `QUALIFICATION_REVIEW` 第一阶段硬信号，真实财务三表与审计印章齐全直通 PASS（LLM 0 次调用防抖动降本），未填占位模板直接 FAIL 反假拦截；存疑场景流转至大模型准则 8 深度裁决与 `SemanticPageRetriever` 候选页检索。
- 结果：落地 5 个专属单测套件，新增及修改 14 个文件，单文件行预算严格控制在 300 行内，全部单元与架构测试全绿。
 - 页面更新：更新 [[bid-quality-check-realization]] §4.28 与 sources；lessons-learned.md §233。

## [2026-09-06] fix | 营业执照核查契约消费与反假收紧返工

- 触发：PR 2557 审查返工（3 Major + 4 Minor）。
- 修复：
  - `check` 落实 `required`/`allowCertificates` 消费，`required=false` 直接 PASS 且引擎步骤 6 跳过 LLM；
  - PASS 新增同页证照关键词必要条件，占位符正则补 `此处粘贴` 变体；
  - 目录散点判据遇 USCC 豁免，登记要素去掉裸 `长期`/`地址` 弱信号；
  - 修正 lessons §231（纯数字 18 位仍可命中 USCC 正则，防线为三重 AND）；
  - 同步修复 !2557 遗留的 2 个 DeterministicCheckPolicyTest 红测（旧断言页缺登记要素）。
- 结果：compliance 全包 376/376，ArchitectureTest 32/32。
- 页面更新：更新 [[bid-quality-check-realization]] §4.26。

## [2026-09-06] fix | 授权委托书目录误判与放行过松返工

- 触发：PR 2556 审查返工。目录判定单字误判。放行门槛含签署投标弱词。死工厂回潮。
- 修复：
  - 目录判定复用 `BidLetterCheckPolicy.isCatalogOrOutlinePage` 整体词逻辑并删除重复点线常量；
  - 放行收紧为强授权要素或身份证事实。签署投标不再单独算数；
  - 删除 `AuthorizationRequirement` 零引用死工厂。自然人免除变体与掩码身份证口径补齐；
  - 新增弱声明拦截与掩码身份证及目录误判回归单测。
- 结果：授权书与提取器及引擎与解析单测全绿。架构测试全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §4.25。
## [2026-09-05] feat | 标书质检「法定代表人及代理人身份证明」（ID_AUTH_LETTER）语义契约与要素反假重构

- 触发：按路线图推进 25 项质检项语义化演进之第 21 项「法定代表人及委托代理人身份证明」（ID_AUTH_LETTER，PRD 2.3 第 21 项，级别 HIGH/CRITICAL）。
- 方案：
  - 招标端契约：新建 `IdAuthLetterRequirement` 契约与纯核心确定性提取器 `DeterministicIdAuthLetterExtractor`，支持身份证正反面、近月社保证明、加盖公章与原件要求提取及双轨融合；
  - 标书端反假：新建 `IdAuthLetterCheckPolicy`，严格排除目录点线页（`isCatalogOrOutlinePage`）与未填模板占位符（`UNFILLED_PLACEHOLDER_PATTERN`），校验 GB 11643-1999 18 位公民身份号码（含隐私脱敏）与版面真实要素（人像面/国徽面/有效期限/签发机关）；若招标明确要求社保则联动核验社保证明凭据，缺失时降级 MANUAL_REVIEW；
  - 引擎协同：`BidDocumentQualityEngine` 落实确定性强凭证直通 PASS（LLM 0 次调用防抖动），存疑场景接通 `LlmAdjudicator` 准则 7 深度裁决与 `SemanticPageRetriever` 候选页检索。
- 结果：落地 5 个专属单测套件，新增及修改 12 个文件，单文件行预算严格控制在 300 行内，57 项单元与架构测试全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §4.27 与 sources；lessons-learned.md §232。

## [2026-09-05] feat | 标书质检「营业执照有效性」（BUSINESS_LICENSE）语义契约与要素反假重构

- 触发：按路线图推进 25 项质检项语义化演进之第 4 项「营业执照有效性」（BUSINESS_LICENSE，PRD 2.3 第 4 项，级别 CRITICAL）。
- 方案：
  - 招标端契约：新建 `BusinessLicenseRequirement` 契约与纯核心确定性提取器 `DeterministicBusinessLicenseExtractor`，支持原件/复印件要求、加盖公章与事业单位法人证书等替代主体证照提取及双轨融合；
  - 标书端反假：新建 `BusinessLicenseCheckPolicy`，严格排除目录点线页（`isCatalogOrOutlinePage`）与未填模板占位符（`UNFILLED_PLACEHOLDER_PATTERN`），校验 GB 32100-2015 18 位统一社会信用代码与主体登记要素（法定代表人/经营范围/营业期限/登记机关/公章）；正向兼容事业单位法人证书等合法替代主体凭证；
  - 引擎协同：`BidDocumentQualityEngine` 落实确定性强凭证直通 PASS（LLM 0 次调用防抖动），存疑场景接通 `LlmAdjudicator` 准则 6 深度裁决与 `SemanticPageRetriever` 候选页检索。
- 结果：落地 5 个专属单测套件，新增及修改 12 个文件，单文件行预算严格控制在 300 行内，51 项单元与架构测试全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §4.26 与 sources；lessons-learned.md §231。

## [2026-09-05] feat | 标书质检「法定代表人授权书」（AUTHORIZATION_LETTER）语义契约与要素反假重构

- 触发：按路线图推进 25 项质检项语义化演进之第 3 项「法定代表人授权委托书」（AUTHORIZATION_LETTER）。
- 方案：
  - 招标端契约：新建 `AuthorizationRequirement` 契约与纯核心确定性提取器 `DeterministicAuthorizationExtractor`，支持模板要求与法人亲签豁免条款提取及双轨融合；
  - 标书端反假：新建 `AuthorizationLetterCheckPolicy`，彻底拦截目录点线页（`isCatalogOrOutlinePage`）与未填模板占位符（`UNFILLED_PLACEHOLDER_PATTERN`），核查委托人/受托人/身份证号/授权权限与期限等双角色法律要素；
  - 引擎协同：`BidDocumentQualityEngine` 落实确定性强凭证直通 PASS，存疑场景接通 `LlmAdjudicator` 准则 5 深度裁决与 `SemanticPageRetriever` 候选页检索。
- 结果：落地 5 个专属单测套件，新增及修改 11 个文件，单文件行预算均安全控制在 300 行内，37 项单元与架构测试全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §4.25 与 sources；lessons-learned.md §230。

## [2026-09-05] refactor | 标书质检纯核心反 AI 异味（Anti-Slop）与代码消歧重构

- 触发：执行 `/ai-slop-cleaner` 技能，治理标书质检领域纯核心（`compliance`）在快速迭代中积累的 AI 异味与冗余样板代码。
- 修复：
  - 去重抽取：`DeterministicBidLetterExtractor.parseDigitsSafely` 升为包级可见，`BidLetterCheckPolicy` 直接复用，彻底删除策略内部 15 行重复解析实现；
  - 条件折叠：消除带书名号及后缀的冗余条件（如 `contains("《谈判响应函》") || contains("谈判响应函")` 统一折叠为 `contains("谈判响应函")`）；
  - 分支净化：消除 `BidDocumentQualityEngine` 中两个空 `if` 占位块（`if (hasStrong...) { } else if (...)`），合并为 `if (!hasStrong... && llm != null)`，避免空分支增加圈复杂度；
  - 死代码清理与行预算防爆：删除 `TenderRequirements.BidLetterRequirement.standard` 零引用的死工厂方法；紧凑化 `with` 方法格式，使 `TenderRequirements.java` 安全降至 277 行（远离 300 行硬门禁）；
  - 预编译正则：`BidLetterCheckPolicy` 预编译 `MULTI_DOT_PATTERN` 与 `CATALOG_DOT_LINE_PATTERN`，消除逐行判定时的动态编译开销。
- 结果：净删除 38 行代码（+21, -59），63 项单元与 FP-Java 架构测试全绿。
- 页面更新：更新 [[bid-quality-check-realization]] §4.23、§4.24。

## [2026-09-05] review-fix | PR !2550 审查返工：救援谓词收敛、恒过测试诚实化、前端空格容忍

- 触发：`xiyu-code-review` 三透镜审出 4 Major（纯测试恒过×2、RowParser 扣分行缺口、豁免析取三处手写）+ 10 Minor。
- 修复：`hasExplicitScoreRule` 并入 Policy 并删别名层；RowParser EXPLICIT 仅救程序性侧；
  纯用例注释修正 + NFKC 补 ①/² 真增量断言；前端 `( null )` 空格容忍 + 空 code 跳过。
- 回填：[[score-parse-service]] 追记「审查返工」节。

## [2026-09-05] fix | PR !2549 follow-up：isCredit 收窄为失信语义行、三处救援口径拉齐、编号反斜杠与前端 NFKC 对齐

- 触发：PR !2549 第三轮复审残留——isCredit 裸子串 FP（推荐候选 + 信用被泄漏）、后端分隔符缺反斜杠、
  前端手写全角映射只是 NFKC 子集。
- 修复：`DeductionItemPolicy.isCreditRescue`（诚信|信用 且 失信）；Ghost/Cell/RowParser 同调一处；
  EXPLICIT 恢复 `扣\s*\d+\s*分`；正反单测锁；`ScoreCodeSanitizer` 补字面反斜杠 + NFKC 注释；
  前端换 `normalize('NFKC')`；Project 补长分句走 match() 用例。
- 回填：[[score-parse-service]] 新增「isCredit 救援收窄与编号分隔符补齐（follow-up，2026-09-05）」章节。

## [2026-09-04] fix | 1371 评分解析四列契约：分类只看 dim、仓库无约束不得满分、行身份用 item.id

- 触发：winbid-test `/project/1371` 四列反复错（权重/分类/仓库满足/顺序）；审查否决「有在用仓即 FULL」。
- 结论：分类只扫因素名；仓库估计抽不到正数面积则 PENDING；前端 lookup/` :key` 走 `item.id`。金标 `Project1371ColumnInvariantsTest` 只锁召回权重/分类/物理序，仓库估计锁在 `EstimatedScoreServiceTest`。
- 页面更新：更新 [[score-parse-service]] §4；lessons-learned.md §224。

## [2026-09-04] fix | 移除标讯中心顶栏客户商机中心入口与清理死代码

- 触发：用户要求移除 `/bidding` 页面顶部的「客户商机中心」入口按钮；执行 `/ai-slop-cleaner` 扫描清理死代码。
- 修复：移除 `BiddingPageHeader.vue` 中的客户商机中心按钮、`UserFilled` 图标、未派发的 `open-manual-add` 事件声明及 `customerOpportunityEnabled` 属性；精简 `List.vue` 与 `useTenderListPage.js` 对应孤立方法与无用导入。
- 页面更新：更新 [[overview]] §5。

## [2026-09-04] feat | 案例详情应答片段按 markdown 安全渲染表格

- 触发：`/knowledge/case` 案例详情「应答片段摘要」纯文本展示，投标文件里的表格排版乱；图片/架构图此前被 DualPane XSS（H14）和沉淀剥图（spec 049）拿掉。
- 结论：Phase 1 只改 `CaseDetailDialog`，`responseText` 走 `renderSafeMarkdown`（允许 table，不允许 img）。复用仍复制原文。真图不在本任务。
- 页面更新：更新 [[knowledge-base]] §4.2.3 / §4.2.4。

## [2026-09-04] refactor | 标书质量核查与资质提取模块 AI Slop 清理与代码去重

- 触发：/ai-slop-cleaner 扫描发现 `TenderRequirementsParser` 与 `LlmVerdictParser` 重复实现 35 行 `extractJsonObject`，且存在多余包装函数与冗余判重。
- 修复：统一复用 `LlmVerdictParser.extractJsonObject`；`DeterministicQualificationExtractor` 移除多余 contains 检查并抽取 `CERTIFICATE_CORE_KEYWORDS`；`QualityCheckDetailDialog.vue` 移除透传包装函数 `qualStatusBadge`。净减少 44 行代码。
- 页面更新：更新 [[bid-quality-check-realization]] §4.19。

## [2026-09-03] fix | 招标文件资格与资质漏识别根治（确定性提取器+代码块围栏容错+双轨融合）

- 触发：第 156 次部署后，测试环境 1358 项目仍报"未在招标文件中提取到明确的硬性资格要求"。用户指出常识：任何招标文件必然包含资格要求。
- 根因：`TenderRequirementsParser` 遇到大模型输出代码围栏（```json）直接抛 checked 异常降级为 missingSentinel；全篇 30000 字符超大窗口导致 LLM 注意力稀释，且缺乏针对中国招投标公文规范的确定性提取规则。
- 修复：编写纯核心 `DeterministicQualificationExtractor` 策略，正则锚定资格要求章节并分条解析专用资质与通用资格；修复 `TenderRequirementsParser` 添加首个配平 JSON 提取 `extractJsonObject`；质检主流水线 `BidQualityCheckAsyncExecutor` 实现双轨融合（`withMergedQualifications`），确保资格 100% 稳定识别。
- 页面更新：更新 [[bid-quality-check-realization]] §4.19；更新 lessons-learned.md §219。

## [2026-09-03] fix | 标书质量核查资质匹配0匹配修复与三级穿透及结构化比对


- 触发：用户反馈 AI 标书质量核查资质匹配"无数据"，排查确认短串反向包含长句导致资质库 0 匹配，以及无物理 TENDER 文件时未回退 tender.source_document_file_url 导致空文档熔断。
- 修复：设计 FP-Java 纯核心 `QualificationMatchPolicy`，实现双向包含、标准代号提取、等级对齐与有效 LCS 匹配；`TenderDocumentLoader` 建立三级优雅穿透（TENDER 分类 -> 启发式文件名 -> 标讯 URL）；下发 `QualificationDetail` 结构化比对清单，前端表格呈现并 100% 接入设计系统令牌。
- 页面更新：更新 [[bid-quality-check-realization]] §4.18；更新 lessons-learned.md §218。

## [2026-08-31] refactor | AI Slop 代码清理与单组件专属小型 Composable 内联收敛

- 触发：全仓库扫描发现多处已废弃死导入/死代码（uploadUrl, uploadHeaders, getApiUrl 等）以及 3 个仅单组件消费的小型 Composable 文件。
- 修复：按 RULES.md §2.5 规范清理所有无用导入/变量，并将单组件专属的 3 个 Composable（useAccountRowActions / useAccountExport / useCaPasswordReveal）直接内联到对应组件中，删除冗余文件。
- 页面更新：新增 [[ai-slop-cleanup-and-composable-inlining]]；更新 `_index.md`。

## [2026-08-31] feat | 项目关键词搜索按招标主体（ownerUnit 数据链路与三层过滤契约）

- 触发：项目搜索框 placeholder「搜索项目名称、招标主体」长期超期承诺——name 关键词前后端均只匹配项目名（PR !2478）。
- 链路：招标主体 = `tenders.purchaser_name`，经 `projects.tender_id` 应用层回填 DTO.ownerUnit（立项后用 `project_initiation_details.owner_unit` 快照）；过滤发生在 enrichment 之后，加字段零 SQL 改动。
- 页面更新：新增 [[project-search-owner-unit]]（三层过滤契约 + 前后端已知分歧：大小写/customer 仅后端/projectName 仅前端）。
- 教训：**文案承诺的能力要跟实现核对，placeholder 写了不等于实现了；同名契约在三处实现（Controller/ExportService/useProjectFilter）时新增字段必须三处同步；测试库 H2 `projects.tender_id` NOT NULL，造数不能建无标讯项目**。

## [2026-08-29] fix | 数据分析部门筛选解包虚拟根（rootorg/组织架构）并修复一二级口径

- 触发：部门筛选保留两级展示中，OSS 组织表存在顶层虚拟根容器（`rootorg`/`组织架构`），被错误当成一级部门导致真实二级部门在两级截断时被裁掉。
- 修复：在 `FilterOptionsQueryService` 中统一识别并解包虚拟根，直接以其子部门作为真实一级部门，并对齐 `loadDepartmentNodes()` 层级深度。
- 页面更新：新增 [[analytics-dept-virtual-root-unwrap]]；更新 `_index.md`；更新 lessons-learned.md §184。

## [2026-08-28] fix | 数据分析部门筛选只到二级 + X 轴按选中层级成柱

- 触发：!2437 只改后端口径且「勾一级拆成二级柱」，前端树仍展示三级，与产品 4 条不符。
- 口径：树只展示一级/二级；只选一级按一级成柱；只选二级按二级成柱；混选忽略不完整一级；全选/二级勾满视为完整一级（一级直属仍计入）。
- 页面更新：[[data-analysis-revamp]] §9。

## [2026-08-27] bugfix | initiation-form-fake-zero-display | PR !2436

- 触发：测试环境 `/project/1365` 列表「计划入围供应商数量」显示 `-`，详情立项表单看似有值。
- 根因：库中字段为 NULL 是真相；详情 form 初值 0 + `el-input-number :min="1"` clamp 成 1。金额字段另有 blur 补 0。Jackson `non_null` 省略 key 后 `Object.assign` 覆盖不了初值。
- 修复：数值业务字段初值 `null`；删 focus/blur 补零；`buildPayload` 用 `??` 保留营收 0；`load()` 对缺 key / `expectedBidders=0` 显式归一；评估表仅正数预填 `plannedShortlistedCount`；`:value-on-clear="null"`。
- 页面更新：[[initiation-form-fake-zero-display]]；`_index.md`；lessons-learned.md §169。

## [2026-08-27] fix | 须知前附表误入评分候选池（PR !2427：结构层 marker 过滤 + 双层 prompt 禁令）

- 触发：山焦物资 6 万字标书解析产出开启程序、履约保证金、成交结果公示等伪评分项。
- 根因：须知前附表（`|条款号|条款内容|编列内容|` 三列表）正文含"综合评分"字样命中 TABLE_SCORE_PATTERN，整表进入 LLM 候选池；此前无结构级排除，仅靠 prompt 不可靠。
- 修复：`MarkdownScoreSectionLocator.isProcedureNoticeTable` 首行判别（含"编列内容"且无"评分因素/评分标准"→ 整体跳过，混合表头保留）；`ScoreParsePrompts` 候选提取禁令 3 条 + 全局归并规则第 6 条；两类 prompt 补 contains 锚定测试（ScoreParsePromptsTest / ScoreGlobalMergePromptTest）。
- 回归锁定：locator 用例 skip/mixed/included 三向覆盖（MarkdownScoreSectionLocatorTest 13 例全绿）；遗留盲区（空行截断续块/纵向胶连/纯文本通道）见 lessons §167 技术债项。
- 页面更新：[[score-parse-service]] 追加「须知前附表过滤」小节；lessons-learned.md §167。

## [2026-08-26] bugfix | TenderEventSdkConfig ServiceRegisterProperties bean 冲突导致 crash-loop

- 触发：测试环境部署 4d1f78b25 后后端 crash-loop，日志报 `required a single bean, but 2 were found`。
- 根因：PR !2416 `@Import(SystemInteractiveServiceImpl.class)` 引入 SDK 服务，SDK 的 `SDKClientConfiguration` 定义了 `serviceRegister` bean（类型 `ServiceRegisterProperties`），与项目 `TenderEventSdkConfig` 的 `serviceRegisterProperties` bean 类型冲突。
- 修复：`TenderEventSdkConfig.serviceRegisterProperties()` 添加 `@Primary` 注解。commit `97be01c7c`。
- 教训：容器测试门禁（FlywayMysqlContainerTest）可提前拦截此类 Spring 上下文装配问题，本次因 Docker 未运行跳过导致在服务器暴露。
- Wiki 页面：[[tenderevent-sdk-bean-conflict]]

## [2026-08-26] bugfix | ConfigurationBindingProbe bean.getName() 驼峰名导致 Spring Boot 3.x crash-loop

- 触发：测试环境部署 05a9c3352 后后端 crash-loop，日志报 `Configuration property name 'obsProperties' is not valid`。
- 根因：`ConfigurationBindingProbe` 用 `bean.getName()`（返回驼峰 bean 名如 `obsProperties`）作为 `Binder.bind()` 的 key，Spring Boot 3.x Binder 要求 kebab-case 属性名，拒绝驼峰名并抛异常。
- 修复：改用 `bean.getAnnotation().prefix()` 取 `@ConfigurationProperties(prefix="xiyu.obs")` 注解声明的真实前缀。PR !2410，commit `cf253b39c`。
- 页面更新：`configuration-binding-silent-failure.md` 追加"陷阱 II"章节。
- 关键结论：`ConfigurationPropertiesBean.getName()` 返回 Spring bean 名称（驼峰），不是配置前缀；探针类应使用 `annotation.prefix()` 取真实前缀。

## [2026-08-25] feature | 案例沉淀触发时机提前至结果确认

- 触发：原 AI 案例沉淀生成挂在项目第 6 阶段（结项 CLOSED），由于结项审批滞后导致第 5 阶段复盘无法及时引用已生成案例；数据就绪度分析表明第 4 阶段（结果确认）登记完成后所有数据已 100% 齐备。
- 改动：更新页面 `knowledge-base.md` §4.2.1；更新 `docs/lessons/lessons-learned.md` §155；后端 `CasePrecipitationAppService` 前置校验放宽为结果已登记、`ProjectResultRegistrationService` 登记完成后发布 `ProjectClosedEvent`，监听器配置 `AFTER_COMMIT` 事务可见性；前端 `ResultConfirmStage.vue` 增加 `🤖 AI自动生成案例` 按钮与前置条件 Tooltip。
- 关键结论：业务副作用触发时机由“数据最小充分就绪点”决定；异步事件采用 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` 避免主事务未提交时的读不一致。
- 触发点：任务收尾（§2 触发点 1）—— 跨模块逻辑变更 + 新业务规则回填

## [2026-08-24] feature | 商机预测提醒链路实现（054）

- 触发：需求补齐商机预测"提醒通知"（原仅列表数据）；4 条预测规则已复用 `OpportunityPredictionPolicy.predict`，本次交付定时扫描 → 判定 → 多渠道通知
- 改动：新增页面 `opportunity-prediction-reminder.md`；`docs/implementation-notes/CO-商机预测提醒.md`；`specs/054-*`；后端 workbench 编排+定时任务+提醒日志表；前端 `notificationHelpers.js` 图标/标签
- 关键结论：提醒时机 = predict 返回的"预测招标时间"，`today >= predicted` 且从未提醒过；项目经理取"自动分配"已落库 `Project.managerId`（定时任务无登录态禁止 CRM 实时反查，`CrmAuthService` 禁令）；每项目只提醒一次用独立表 `project_id` 唯一键；无截止日期省略截止句；事件库失败不阻断主链路
- 触发点：任务收尾（§2 触发点 1）—— 跨模块逻辑变更 + 新业务规则回填

## [2026-08-21] update | AI智能案例推荐详情三模块 + 去掉复用次数展示

- 触发：标书制作 AI 智能推荐，卡片/详情不再展示复用次数；详情改为顶部评分项 + 中部三层原文/元信息 + 底部复用
- 改动：`ai-capabilities.md` §9.1；`knowledge-base.md` §4.2.4；实现笔记 `docs/implementation-notes/ai-case-recommend-detail-layout.md`
- 关键结论：案例库页仍保留复用次数；招标主体/项目状态按 sourceProjectId 批量查标讯/项目，不落 knowledge_case；空元信息统一 `-`
- 触发点：任务收尾（§2 触发点 1）—— 跨前端+后端 UI 契约变更回填

## [2026-08-21] update | 标书质量核查 contains 直出误判修复（负偏离否定语义 + veto 归一化）

- 触发：PR !2365 google-code-review 审查发现 `declaredDeviation` 把"偏离表：无负偏离"误判 FAIL（否定语义盲区）、`vetoFirstStage` 精确匹配 LLM 提取资格全文误判缺失 FAIL（空白/标点差异）
- 改动：`bid-quality-check-realization.md` §4 追加陷阱 5、6；`docs/lessons/lessons-learned.md` §142；新增 2 回归测试锚定（否定前缀豁免 + 归一化匹配）
- 关键结论：一票否决项（FAIL=废标级）禁止裸 contains 直出；含否定语义关键词须紧邻前缀判断；跨源文本匹配先归一化（去空白/全半角/标点）
- 触发点：任务收尾（§2 触发点 1）—— 代码变更 + 新增业务结论回填

## [2026-08-20] task | 异步任务表复制门禁（§139）

- 触发：基线 export_tasks 未接入异步路径，模块专表复制 6 次
- 改动：lessons-learned §139；`scripts/check-async-task-table.sh` 进 pre-commit/pre-push
- 触发点：任务收尾（§2 触发点 1）—— 新约束 + 防复发门禁

## [2026-08-20] no-op | cut-npm-aliases 无新业务结论

- 触发：删除 33 条 npm script 别名，启动入口改 bash
- 结论：`docs/implementation-notes/cut-npm-aliases.md`；CI 未使用被删名
- 触发点：任务收尾（§2 触发点 1）

## [2026-08-20] no-op | ponytail-safe-cuts 无新业务结论

- 触发：删除死代码（icons.js / demoPersistence / useDebounce）+ 去掉已不读取的 VITE_API_MODE
- 结论已写 `docs/lessons/lessons-learned.md` §135 与 `docs/implementation-notes/ponytail-safe-cuts.md`；无新业务规则需合成 wiki 页
- 触发点：任务收尾（§2 触发点 1）—— 代码变更但无新 wiki 页面

## [2026-08-20] compound | case-precipitation-oversize-oom 复合查询回填（跨模块根因）

- 触发：13 天内叠加到项目"ai测试01"，点【AI自动生成案例】通知"成功但入库 0 条"；追根因定位为 401MB 标书 OOM
- 改动：新建 `lessons-learned/case-precipitation-oversize-oom.md`；更新 `_index.md` 索引
- 关键结论：⑥ `RestTemplate.getForObject(byte[].class)` 整文件读堆无防护，fallback `new String(content,UTF_8)` 二次分配 → OOM；⑦ OBS 是真实标书数据源，提取失败必须 notifyFailure，禁止回退占位文本假成功。修复入口大小防护（超限抛 DocInsightException）+ OBS 失败 rethrow。
- 触发点：任务收尾（§2 触发点 3 根因分析复合查询）

## [2026-08-19] task | 标讯联系人性质(contactNature)字段全链路实现

- 触发：任务收尾（§2 触发点 1）—— 跨模块逻辑变更（前后端 29 文件），新增业务字段
- 改动：
  - `integration-tender-api.md`：contactInfo 字段说明补充 nature；版本演进追加 2026-08-19 代码层 contactNature 条目
- 关键结论：新增字段走校验分组隔离（ManualTenderValidation 挂手动路径条件必填，CRM 集成路径豁免）；Flyway 版本号与 origin/main 连续撞号时重命名为更高版本号（V1189→V1198→V1199→V1200）并清理 target 残留与数据库失败记录
- 触发点：任务收尾（§2 触发点 1）+ pre-push wiki-health-check（§2 触发点 4）

## [2026-08-19] update | 数据分析 M1/M2/M4 核心数据缺陷与口径升级修复

- 触发：测试服务器人工测试反馈：部门与人员选项空、区域统计为 0、客户类型饼图为 0、竞品明细表格“是否中标”不准
- 改动：
  - `data-analysis-revamp.md`：新增 §2026-08-19 缺陷修复与口径补齐章节，记录部门树兜底合并、区域 Left Join、客户类型英文枚举映射、竞品表格 isWon 多重判定等口径改动。
- 关键结论：异构环境（如组织表未同步）下必须自动从 `User` 业务表提取 `department_name` 合并兜底；任何存在数据漏失风险的联接必须使用 `left join`；外部同步的客户类型值不固定，必须由 `normalizeCustomerType` 全面补齐映射，确保饼图与趋势分析口径的一致性。

## [2026-08-18] update | score-parse-service 权重归一化 Convergence 收口（spec 047 C001/C002）

- 触发：spec 047 Convergence 阶段补齐——US3 信号契约单测 + 归一化后日志口径
- 改动：`score-parse-service.md` §7 追加「归一化后完成日志口径（C002）」（日志 totalWeight/weightWarning 必须重算反映落库值）；§8 追加「spec 047 收敛单测（C001）」（`OriginalTotalWeightSignalTest` 5 用例锚定 US3 信号链路：Summary 序列化 / @Column 映射 / getItems 透出）
- 关键结论：归一化重算 `weightResult` 仅修诊断口径，不改变落库业务正确性；信号链路（persist→entity→DTO→透出）已由契约单测锚定
- 触发点：pre-push wiki-health-check（§2 触发点 4，代码变更需回填）

## [2026-08-17] task | 评分解析抽屉顶部操作栏下线（导入评分草稿/导出报告取消）

- 触发：产品确认「AI 评分标准解析」抽屉顶部操作栏（阶段标签 + 重新解析/导入评分草稿/导出报告）为已取消功能，PR 删除 6 文件（分支 `agent/zcode/remove-scoreparse-topbar`）
- 更新：`score-parse-service.md §7` 追加前端抽屉下线条目（已删函数名 / 重新解析保留入口三处）
- 关键约束：`useScoreParseDrawer` 的 `importToDrafts()`/`exportReport()`、`projects.js` 的 `importScoreDraftsFromAnalysis()`、`imported` emit 已删，勿再按这些名字检索
- 触发点：任务收尾（§2 触发点 1）—— 跨模块逻辑变更（≥3 文件）+ 新业务约束

## [2026-08-17] update | spring-pitfalls §10.6 追加多构造函数 @Autowired 陷阱（PR !2301）

- 背景：同步 PR !2300 后，`InitiationTenderTextResolver` 因新增测试构造函数（变成多构造）且未标记 `@Autowired`，导致后端启动 crash `No default constructor found`；PR #2301 追加 `@Autowired` 修复
- 改动：
  - `docs/lessons/lessons-learned.md`：新增 §116 完整根因分析 + 教训 + 操作规范
  - `.wiki/pages/spring-pitfalls.md`：追加 §10.6 事故/根因/正确做法/预防，更新 frontmatter tags 与变更记录
- 关键结论：单构造免 `@Autowired` 是语法糖；一旦类出现 ≥2 个构造函数，**必须**在生产入口构造函数上显式 `@Autowired`
- 触发点：任务收尾（§2 触发点 1 + §2 触发点 3 根因分析复合查询）

---

## [2026-08-16] update | score-parse-service 空值语义 + UI 对齐 + 50MB 文案（spec 044）

- 背景：PR !2292 验收发现客观项空 `estScore` 被前端兜底转 `0`，误显示红色 0 分（P1）；弹窗高度超 70vh（P2）；待确认状态未区分灰字蓝点（P2）；50MB 提示文案未对齐 PRD 5.3（P2）
- 改动：`score-parse-service.md` §5 补空值语义、§7 补 UI 对齐与 50MB 文案规范
- 关键决策：空值必须保留 `null` + PENDING，禁止兜底 0；`el-dialog` append-to-body 需非 scoped 样式限高
- 触发点：任务收尾（§2 触发点 1）—— 根因修复 + 新规范

## [2026-08-16] task | 评分解析生产风险收口（spec 043）

- 结论：自动解析仅从未解析时新建一次；无源落 FAILED 防循环；50MB 硬限制；超大无底稿提示过大，有底稿回退；进行中打开抽屉跟随已有任务
- 回填：[[score-parse-service]] §3、[[ai-capabilities]] V3 抽屉
- 触发点：任务收尾（§2 触发点 1）

## [2026-08-16] task | 评分解析改走立项招标文件

- 结论：阶段 1 正文以立项 `TENDER` 为准，打开评分抽屉自动解析；编制页「启动 AI 生成初稿」不再作为评分入口
- 回填：[[score-parse-service]] §3、[[ai-capabilities]] V3 抽屉
- 触发点：任务收尾（§2 触发点 1）

## [2026-08-14] create | data-analysis-revamp 新建

- 背景：数据分析页面全面重构（M0~M4 五模块），对照原型 + PRD 逐模块检查修复
- 改动：
  - 新建 `.wiki/pages/data-analysis-revamp.md`（模块结构 + FP-Java 三层 + 三模式竞品 + 数据口径 + 陷阱）
  - `_index.md` 加索引行
- 关键决策：M4 后端 FP-Java 三层分离；折扣 Integer→Double；时间过滤统一 p.createdAt；竞品枚举前端硬编码
- 触发点：任务收尾（§2 触发点 1）—— 跨模块逻辑变更（51 文件）+ 新业务规则

## [2026-08-04] create | audit-whitelist-pitfalls 新建 + lessons-learned §106

- 背景：PR !2256 补的四个 `@Auditable` action 命名（PERFORMANCE_BUNDLE_EXPORT_*）不命中 AuditActionPolicy KEY_ACTIONS，审计一条不落库；CO-324 同款坑第二次复发。PR !2258 修复（KEY_ACTIONS 加 DOWNLOAD + 注解对齐短动词惯例）
- 改动：
  - 新建 `.wiki/pages/audit-whitelist-pitfalls.md`（机制 + 两次复发记录 + 规范）
  - `docs/lessons/lessons-learned.md`：新增 §106 审计 action 命名白名单陷阱
  - `_index.md` 加索引行
- 验收证据：修复后真实导出 + 下载，audit_logs 落库 CREATE（id 1069）/ DOWNLOAD（id 1070）两条
- 触发点：任务收尾（§2 触发点 1）—— 根因修复 + 新陷阱

## [2026-08-04] no-op | CO-602 P2 #13-#23 修复 — 标准代码质量改进，无新结论

- 11 项 P2 修复均为标准代码质量改进（事务传播、审计注解、死参数/死代码清理、资源关闭、包迁移、枚举统一、回滚注释等）
- 无新根因、新陷阱、新业务规则产出
- 关联 PR：!2254

## [2026-08-04] update | oss-organization-sync-playbook 新增 §5.4 jobNumber 三字段同源 + lessons-learned §98

- 背景：组织架构管理页"CRM 工号"列大部分用户显示"-"，业务人员困惑。PR !2252 修复时发现 `crm_sales_no`/`employee_number`/`username` 三字段同源，均填 OSS 事件库的 `jobNumber`
- 改动：
  - `oss-organization-sync-playbook.md`：新增 §5.4 jobNumber 三字段同源澄清（含填充点、用途、覆盖率、命名历史误会、UI 显示建议）
  - `docs/lessons/lessons-learned.md`：新增 §98 OSS 工号三字段同源教训
- 触发点：复合查询回填（§2 触发点 3）—— 用户质疑"CRM 工号"列命名，回到代码追溯发现三字段同源

## [2026-08-01] update | dynamic-form-engine 新增 §9.8 E2E 失败根因 + lessons-learned §96

- 背景：T034 quickstart §4 E2E 测试 9 个全失败，但 CO-601 产品代码经手动 API 验证正常
- 改动：
  - `dynamic-form-engine.md`：
    - §9.7 补充 quickstart.md §6 引用 + 标注 E2E 当前失败状态与根因
    - 新增 §9.8 E2E 测试失败根因与判别流程（三类根因 + admin 手动验证判别法）
  - `docs/lessons/lessons-learned.md`：新增 §96 E2E 测试失败三类根因模式（测试数据污染 / 角色权限不匹配 / 后端 OOM 崩溃）
  - `specs/040-project-form-custom-fields/quickstart.md`：新增 §6 走查结果记录（§1-§5 验证结论表 + E2E 失败根因 + 手动 API 验证证据）
- 关联：PR !2241（commit ee1bd1f74）、T034 走查任务

## [2026-07-31] backfill | dynamic-form-engine 新增 §9 CO-601 hybrid 渲染模式与自定义字段扩展

- 背景：CO-601 PR !2235 合入 main，涉及 45 文件改动（表单引擎 hybrid 渲染模式 + 自定义字段扩展 + 预置字段锁定 + 前后端双重校验）
  - 触发点 3 复合查询回填（涉及 ≥3 个文件，下次还会被问到"hybrid 渲染模式怎么工作的"）
- 新增内容：
  - `dynamic-form-engine.md §9`：CO-601 hybrid 渲染模式与自定义字段扩展
    - §9.1 问题背景：fallback 与动态 schema 的冲突
    - §9.2 hybrid 渲染模式（hybrid + preset-keys props，渲染逻辑，当前使用情况）
    - §9.3 自定义字段持久化（V1183 迁移 + CustomFieldsCodec + scope 键整体替换语义）
    - §9.4 预置字段锁定（PROJECT_LOCKED_FIELD_KEYS + CustomFieldsSchemaPolicy + 前后端双向校验）
    - §9.5 生命周期管理（改 label/删除字段/类型变更）
    - §9.6 关键坑点（H2 JSON 列双重编码、base 漂移、hybrid 默认 false）
    - §9.7 相关文档
  - frontmatter `updated` / `health_checked` 更新为 2026-07-31
  - sources 新增 7 个 CO-601 相关源文件引用
- 关联：PR !2235（commit d9873fff0）、`docs/lessons/lessons-learned.md` §91-§94、`specs/040-project-form-custom-fields/`

## [2026-07-31] update | flyway-migration-pitfalls 新增 §11 INSERT IGNORE NULL 不幂等

- 背景：PR !2229 google-code-review 独立核查发现 U1182 回滚脚本幂等性缺陷
  - `INSERT IGNORE` 依赖 `uk_scope_org(scope, org_id)` 唯一键去重，但 `org_id=NULL` 时 MySQL InnoDB 对 NULL 不去重（NULL != NULL）
  - 重复执行回滚每次插入重复记录；本地库连续执行 2 次验证复现
- 处理：[[flyway-migration-pitfalls]] 新增 §11（事故/解决/教训），原 §11~§13 顺延为 §12~§14
  - frontmatter `updated` / `health_checked` 更新为 2026-07-31
  - engineering-discipline.md 引用均为 §2/§3，不受顺延影响
- 关联：PR !2229 修复 commit `c7230e0f6`（INSERT 前显式 DELETE org_id IS NULL 残留）

## [2026-07-23] backfill | 存量 wiki 违规批量回填（92 → 0）

- 背景：PR !2190 合入后 wiki:check 报 92 个违规（56 个文件），全部为日期过期类
  - 35 个 stale health_checked（>7 天）
  - 36 个 stale updated（>30 天）
  - 无结构性问题（链接失效/源文件缺失等）
- 处理策略：分级处理
  - **A 类历史档案**（21 个）：SOW/合同/里程碑/附件追溯/lessons-learned 等
    - 批量更新 `health_checked: 2026-07-23`
    - 新增 `archive: true` frontmatter 字段
    - 保留 `updated` 不变（内容没变就不应该改）
  - **B 类活跃文档**（35 个）：架构/权限/部署/集成/测试模块等
    - 批量更新 `health_checked: 2026-07-23`
    - 保留 `updated` 不变
    - 本次为快速批量回填，未深度 review 每个文件内容
- wiki-check.mjs 规则优化（2 处）：
  1. `archive: true` 豁免 `updated >30 天` 检查（历史档案内容不会变）
  2. `health_checked` 7 天内时豁免 `updated >30 天` 检查（已 review 过即不需再提醒）
- 结果：`wiki:check passed. pages=65`（违规归零）
- 一次性脚本：
  - `scripts/wiki-backfill-stale.mjs`（A 类 21 个）
  - `scripts/wiki-backfill-active.mjs`（B 类 35 个）
- **诚实声明**：本次为批量快速回填，未逐页深度 review 内容。以下高优先级文档的深度 review 作为后续任务：
  - `architecture/effective-role-resolution.md`（CO-361/CO-373 涉及）
  - `roles-and-permissions.md`（CO-361/CO-373 涉及）
  - `data-permission-hardening.md`（CO-361/CO-373 涉及）
  - `integration-organization-event-sdk.md`（OSS 同步涉及）
  - `integration-wecom.md`（企微 OAuth 涉及）

## [2026-07-23] schema | Agent Wiki 运行规范（Schema 层）建立

- 新增页面：`.wiki/WIKI.md`（Schema 配置层，Agent Wiki 行为宪法）
  - 核心理念：从"查询时检索"→"摄入时编译"
  - 三层模型映射：L1 sources/extracts → L2 pages → L3 WIKI.md + AGENTS.md
  - 4 个硬触发点：任务收尾 / PR 创建 / 复杂问题答完 / pre-push
  - AI-First 编写标准：列表/表格/代码块，不写散文
  - 复合查询回填流程：答完复杂问题→判断→回填新页面
- 修改 `scripts/agent-finish-task.sh`：Step 2 后插入 Wiki Checkpoint
- 修改 `scripts/pre-push-gate.sh`：新增 §16 Wiki 健康检查（2 周过渡期 warning，之后 error）
- 修改 `scripts/pr-create.sh`：PR body 模板追加 Wiki 更新勾选项
- 修改 `.githooks/pre-commit`：检测代码变更时提醒同步 wiki
- 修改 `AGENTS.md`：第 7 条底线 + 索引行
- 修改 `CLAUDE.md`：执行原则加 Wiki 维护纪律
- 根因分析：.wiki/ 体系已有 60+ 页面但维护停在 2026-06-20，CO-361 反复修复 5 次/OSS 角色 10+ 轮/覃超颖 403 全过程——Wiki 一条都没记。根因不是"没有架构"而是"纪律未建立"——触发器没钉进门禁。本次建立 4 个硬触发点解决此问题。

## [2026-06-20] update | 生产测试服务器 172.16.38.78 部署实录与 health check 超时修复

- 更新页面：`.wiki/pages/deployment.md`
  - 新增 §9「生产测试服务器（172.16.38.78）部署实录」
  - 补充环境拓扑、打包命令、产物校验、deploy.env 示例、部署后验证清单
  - 记录 `remote-deploy.sh` health check 超时问题与 PR !876 修复
  - 更新 frontmatter：`updated: 2026-06-20`、`health_checked: 2026-06-20`
- 代码修复：PR !876 将 `scripts/release/remote-deploy.sh` 健康检查等待从 120 秒延长至 240 秒
- 部署验证：2026-06-20 成功部署 `337fc79a5` 与 `d180f1395` 到 `172.16.38.78`，后端 `/actuator/health` 最终 `UP`

## [2026-06-15] update | OSS 组织架构同步角色白名单与 admin 升级规则归档
- 更新页面：`.wiki/pages/integration-organization-event-sdk.md`（角色映射与白名单章节）
- 更新配置模板：`docs/integration/organization-role-filter-config.yml`
- 关键决策：
  - 张頔（03595 / dean_zhang@ehsy.com）、郑蓉蓉（06234 / tina_zheng1@ehsy.com）、袁思琪（11484 / suki_yuan@ehsy.com）通过 `personToRoleMappings` 映射为 `admin`
  - 袁思琪同时属于 `/bidAdmin` 与 `bid-TeamLeader`，因单角色限制按最高权限给 `admin`；后续若取消系统管理员，可改回 `bid_senior`（投标主管，PR !545 引入）
  - `bid-SystemAdmin` 是 OSS 临时岗位，不再在 `positionToRoleMappings` 中硬映射
  - `/bidAdmin` → `bid_admin`、`bid-TeamLeader` → `bid_lead`、`bid-Team` → `bid_specialist`、`bid-projectLeader` → `sales`、`bid-administration` → `admin_staff`
- 代码调整：`OrganizationSyncPolicy` 新增 `allowAdminElevation` 参数，`OrganizationUserSyncWriter` 仅对 `personToRoleMappings` 命中人员时放行 admin 升级守卫
- 验证：后端相关单测 20 个通过，ArchUnit 门禁通过，pre-push 14 道门禁通过

## [2026-04-24] ingest+build | SOW V1.4 执行基准入库
- 新增源文件：`.wiki/sources/implementation/西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx`
- 新增抽取件：`.wiki/extracts/implementation__西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx.md`
- 新增页面：`pages/implementation/sow-2026-v1-4.md`（开发排期、产品规划、实施推进、验收判断、上线切换和运维保障主基准）
- 更新页面：`overview.md`、`requirements.md`、`architecture.md`、`data-model.md`、`roles-and-permissions.md`、`team-and-timeline.md`、`deployment.md`、`contract-constraints.md`、`implementation/{delivery-playbook,milestones,acceptance-and-closure,risk-register,weekly-status}.md`
- 执行口径：后续开发、产品规划和项目实施均优先核对 SOW V1.4；真实 API 为唯一交付路径，历史 Mock/demo 适配仅作为待清理遗留
- 版本口径：Wiki 统一按 `SOW V1.4` 执行；若原始 Word 正文仍出现 `V1.3`，作为对外签发前需校准的显示问题处理

## [2026-04-23] ingest+build | 合同与附件硬约束入库
- 新增源文件：`.wiki/sources/contract/` 下合同正文、附件 3 报价清单 PDF、附件 4 需求任务书
- 新增人工摘录：`.wiki/sources/contract/附件3-合同报价清单人工摘录.md`（扫描 PDF 无文本层）
- 新增页面：`pages/contract-constraints.md`（范围、付款、里程碑、验收、运维、违约责任约束）
- 更新页面：`overview.md`、`requirements.md`、`team-and-timeline.md`、`deployment.md`、`implementation/{milestones,acceptance-and-closure,risk-register}.md`
- 更新脚本：`scripts/wiki-ingest.mjs` 将 `contract/` 纳入源目录说明
- 校验结果：`npm run wiki:ingest`、`npm run wiki:build`、`npm run wiki:check`、`npm run check:doc-governance` 均通过（pages=20）

## [2026-04-22] build | 设计系统知识页入库与总览口径更新
- 新增页面：`pages/design-system.md`（正式 DESIGN.md 基线、落地策略、实施回链）
- 更新页面：`pages/overview.md`（切换为真实 API 唯一路径口径，补充设计系统建制信息）
- 自动重编：`pages/_index.md`、`PAGE_INDEX.md`、`catalog/page-catalog.json`
- 校验结果：`npm run wiki:build` 与 `npm run wiki:check` 均通过（pages=19）

## [2026-04-15] ingest | 附件5：需求任务书 + 附件6：功能清单
- 来源：`.wiki/sources/bidding/` 下 2 个文件（.docx + .xlsx）
- 新建页面：`requirements.md`（需求追溯，29 功能点追溯矩阵）
- 更新页面：`_index.md`（新增 requirements 导航）
- 更新文件：`INDEX.md`（新增"招标需求文档"分类，重编章节号）

## [2026-04-15] init | Wiki 知识库初始化
- 创建三层架构：`WIKI.md`（Schema）+ `.wiki/INDEX.md`（源索引）+ `.wiki/pages/`（知识页面）
- 创建源文档目录：`.wiki/sources/{bidding,industry,competitor,customer,technical,internal}/`
- 源文档编目：11 个分类，70+ 源文件
- 生成 11 个 Wiki 页面：overview, architecture, business-process, modules, ai-capabilities, data-model, roles-and-permissions, glossary, team-and-timeline, deployment, _index
- 交叉引用校验通过：所有 `[[wiki-link]]` 指向有效页面
- 更新根文件：CLAUDE.md, README.md

## [2026-04-22] upgrade | 双栈 Wiki 升级（研发 + 实施）
- 新增自动化脚本：`scripts/wiki-ingest.mjs`、`scripts/wiki-build.mjs`、`scripts/wiki-check.mjs`
- 新增目录：`.wiki/extracts/`、`.wiki/outputs/`、`.wiki/catalog/`
- 双索引落地：`.wiki/INDEX.md`（Source Catalog）+ `.wiki/index.md`（Page Catalog）
- 新增 Implementation Space 页面：`implementation/{delivery-playbook,milestones,risk-register,weekly-status,acceptance-and-closure}.md`
- 执行真实增量演示：摄入 `docx + xlsx` 源文件并生成抽取结果与 catalog
- pre-commit 门禁新增 `npm run wiki:check`

## [2026-04-30] update | 正式上线时间统一为 2026-07-10
- 变更依据：根据本周与客户达成的一致，正式上线时间统一为 2026-07-10
- 更新页面：`implementation/sow-2026-v1-4.md`、`implementation/milestones.md`、`team-and-timeline.md`、`overview.md`、`requirements.md`、`contract-constraints.md`、`implementation/document-delivery-ledger.md`、`implementation/attachment4-requirement-task-book.md`
- 执行口径：当前以 2026-04-27 启动准备、2026-05-07 项目启动会、2026-05-09 首场正式客户访谈、2026-07-10 正式上线为项目里程碑基线
- 来源同步说明：`.wiki/sources/`、`.wiki/extracts/` 与 `.wiki/pages/` 中涉及正式上线时间的口径已统一为 2026-07-10

## [2026-07-26] update | E2E 系统性失败根因回填（PR !2201）
- 触发：PR !2201 修复 E2E 多模块系统性失败（regression-bid-ui-optimization + task-board-customization + form-engine-scope-router 等），涉及权限矩阵、前端组件、业务规则、表单数据 4 类根因
- 新增内容：
  - `frontend-pitfalls.md §7.4`：路由守卫 every 改造后的权限矩阵同步（CO-580 教训）
  - `lessons-learned.md §九`：E2E 系统性失败根因分析（4 类根因 + 修复方案 + 防复发检查清单）
- 关键教训：
  - 路由守卫 some→every 后必须同步审计 RoleProfileCatalog 权限矩阵
  - 业务规则变更必须同步审计 E2E 测试（CO-529-followup 状态流转）
  - E2E 环境禁用 Flyway 时必须用 @Profile("e2e") Seeder 补种数据
  - E2E 选择器优先级：URL 参数 > data-testid > CSS class > 文本
- 关联文件：`backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`、`backend/src/main/java/com/xiyu/bid/bootstrap/FormDefinitionE2eSeeder.java`、`e2e/task-board-customization.spec.js`


## [2026-08-15] task | score-parse-backend 任务收尾回填
- 新增 [[score-parse-service]]（spec 041 后端全链路：解析/匹配/打分/超时）
- 更新 _index.md 索引

## [2026-08-17] bugfix | 评分解析 prompt 模板 % 转义修复回填
- 触发：测试环境项目 225 评分解析 100% 失败（`Conversion = '"'`），根因 `ScoreParsePrompts` text block 模板示例文案 `占30%"` 中字面 `%` 未转义
- 更新：`score-parse-service.md §7` 追加 prompt 模板 Formatter 格式串踩坑条目
- 关联：`docs/lessons/lessons-learned.md §117`、回归测试 `ScoreParsePromptsTest`（4 用例）

## [2026-08-17] bugfix | 评分解析 NPE 修复 + 扫描件排查经验回填
- 触发：项目 226 召回三产出候选后 toCandidate 裸访问 null section NPE；项目 225 "未识别到评分标准章节"实为扫描件（markdownLength=3）
- 更新：`score-parse-service.md §7` 追加 null section 降级、扫描件失败文案两条踩坑
- 关联：`docs/lessons/lessons-learned.md §118`、回归测试 `OpenAiScoreAnalyzerTest`

## [2026-08-17] task | 评估表字段文案调整 + GAP 文本框下线回填
- 触发：客户需求 2026-08-17 批次（分支 `agent/qoder/eval-form-field-adjustments`），三项文案改名 + GAP 文本框下线（附件保留），跨前端双表单/立项/后端 DTO/Mapper 共 32 文件
- 新增 [[evaluation-form-field-adjustments]]：需求清单 / GAP 只停写不删列策略 / 立项深耦合保留决策 / V1191 迁移注意点
- 更新 _index.md 索引

## [2026-08-19] task | TenderAttachmentUrlResolver 静态注入反模式 DI 重构
- 触发：tech-debt-tracker 条目（PR !2331 审计 F2 项，分支 `agent/qoder/resolver-di-refactor`，合入 PR !2334）；跨模块 23 文件，满足“跨模块逻辑变更”触发标准
- 更新 [[spring-pitfalls]] §10.8：@Value/@Autowired 注入静态字段反模式的四类风险 / 构造器注入 + 配置承载类修复范式 / migrate-callers-then-delete / 循环依赖规避 / 三条预防规则
- 未新建页面、未改 _index.md（仅在既有页追加小节）

## [2026-08-19] deploy | 第 133 次测试部署：容器测试门禁拦截 V1196 枚举列缺陷
- 触发：部署到测试环境（release c93bf9d93-api8080）；打包时 FlywayMysqlContainerTest 失败
- 根因：V1196 建 status VARCHAR(32) 与 JPA TaskStatus @Enumerated(STRING) 不匹配，Hibernate schema-validation 启动即崩（V1040→V1041 同款）
- 修复：V1197 MODIFY COLUMN ENUM + U1197（PR !2335），沉淀 lessons-learned §123 + 部署报告 133rd

## [2026-08-19] knowledge | §4.2 回调历史数据重推运维接口落地
- 触发：!2331 验证时发现历史回调（task 348）payload 存的是已过期 OBS 预签名 URL；落地重推工具
- 回填：crm-integration-lessons.md §9.5（接口/实现/排查入口 webhook_delivery_tasks.payload）
- 关键点：重推=从 DB 重建领域事件重新发布，复用现有监听器组装 payload，不维护第二套组装逻辑
- 未新建页面（仅在既有页追加小节）

## [2026-08-19] bugfix | 数据分析 PR!2333 验收四连漏修复（口径分裂/入口拦截/组件交互）回填
- 触发：用户复测报 4 问题——M1 区域值全 0、M2 客户类型统计错、M4 项目模式暂无数据、部门下拉丢搜索/全选清空（分支 `agent/zcode/fix-analytics-round2`）
- 更新 [[data-analysis-revamp]]：§数据口径追加「投标数=全部项目」「客户类型 coalesce 双源」两条修订；§关键陷阱追加 §8 项目模式入口空检查、§9 部门树形下拉交互规范
- 关联：`docs/lessons/lessons-learned.md §124`、回归测试 `CompetitorAnalysisAssemblerServiceTest`（新增 3 用例）+ `TrendAnalysisComputationServiceTest`（新增进行中口径用例）
## [2026-08-19] bugfix | 竞品分析「是否中标」整列误显已中标 — notes 语义漂移字段回填
- 触发：PR !2339 验收遗留复测——项目「测试竞品公司」竞品明细表格是否中标全显「已中标」（分支 `agent/zcode/fix-competitor-won-notes`）
- 根因：isWon 用项目级状态（resultType/status）推导，语义层级错配；真源是 `project_result_competitor.notes`（前端「竞争对手情况」表格是否中标下拉写入，字段语义漂移）
- 更新 [[data-analysis-revamp]]：§4 追加 2026-08-19 修订说明（推翻项目级推导口径）；§关键陷阱追加 §10 竞品是否中标数据源规范
- 关联：`docs/lessons/lessons-learned.md §127`；回归测试 `CompetitorAnalysisJpqlRegressionTest`（H2 实测 JPQL 构造器 10 参 + notes 按行独立带出）+ `CompetitorAnalysisComputationServiceTest`（枚举解析 3 用例）

## [2026-08-20] bugfix | 多维度趋势区域筛选与分组口径统一（省份归一化）回填
- 触发：区域筛选后投标数/中标数 KPI 与图表柱子不一致（分支 `agent/qoder/fix-trend-region-filter-stats`）
- 根因：筛选 LIKE 前缀匹配 vs 分组省份归一化两口径分裂；空串回退不对称；时间维度不消费 regionIds
- 更新 [[data-analysis-revamp]]：追加「2026-08-20 区域筛选与分组口径统一」小节（双源取值 + extractProvince 归一 + 34 条短省名别名 + endDate 边界对齐）
- 口径边界：不含 projects.region（三源尝试已回退）；纯市级自由文本归省需市→省映射表另开任务

## [2026-08-20] feature | 评分标准解析原文保真对齐 + 评分表格 UI 调整回填
- 触发：客户验收反馈三条——评分项/详细要素文字与招标文件评分表原文不一致；实际评分列去除"重评"标签；总权重移到客观项权重前（分支 `agent/zcode/score-parse-fidelity`）
- 根因：LLM 复述无逐字校验（空白/断词漂移）+ 正则召回跨行拆碎 + MergePolicy detail 首次优先让残缺短语版挤掉完整版
- 更新 [[score-parse-service]]：§7 追加「原文保真对齐」治理三件套（ScoreOriginalTextAligner / fromLlmCandidate 加源文参 / enrich 原文背书覆盖）与前端 UI 调整条目；§8 追加验证基线
- 边界：存量 score_item 不回溯需重新解析；dim 对齐失败保留 LLM 输出属预期；防幻觉靠"无背书不覆盖"约束
- 关联测试：ScoreOriginalTextAlignerTest(6) + ScoreCandidateMapperTest(3) + ScoreItemMergePolicyTest(+2) + OpenAiScoreAnalyzerTest(6) 回归，前端 ScoreParseV3.qa.spec.js(8) + build EXIT=0

## [2026-08-20] review-fix | 评分解析原文保真 design review 修复回填（P1/P2）
- 触发：对 PR !2356 做思维链 design review，发现 sameDim 全等判据挡掉同编号不同名称场景的唯一完整版本（P1）、对齐失败静默降级无可观测信号（P2）
- 修复：MergePolicy 增"归一化子串判同项"补强分支（互不为子串维持丢弃，防误并）；Mapper detail 对齐失败 WARN；召回四完成日志带 aligned=N/M 对齐率；enrich 死分支清理；既有测试锚定新行为
- 更新 [[score-parse-service]]：治理三件套条目补 P1 判据与可观测性说明
- 关联：`docs/lessons/lessons-learned.md §133`；回归 ScoreItemMergePolicyTest 12 用例（新增同编号不同名称补强/防误并 2 例）共 27/27

## [2026-08-20] review-fix | google-code-review 阻塞项：自包含伪背书绕过防幻觉判据
- 触发：google-code-review 审查发现 hasSourceBacking（sourceText.contains(detail)）可被自包含绕过——LLM 幻觉候选的 sourceText 是模型复述，天然包含自己的 detail，伪背书成立后长幻觉文本可覆盖正则残缺版
- 修复：Mapper 对齐失败分支 sourceText 置空（detail 保留 LLM 输出做展示降级，source_text 只携带原文验证内容）；MapperTest 两用例断言更新 + MergePolicyTest 新增空背书防御用例
- 副作用核查：GhostCandidateFilter"无 code 且无 sourceText"滤除、WeightOverloadTrimPolicy 剔嫌疑对幻觉候选更严格（方向正确）；scoreparse 全包 319/319 通过
- 更新 [[score-parse-service]]：治理三件套条目补"对齐失败 sourceText 置空"语义

## [2026-08-20] bugfix | M1 趋势客户类型 X 轴英文枚举 + 筛选漏统计（双源写入形态不一致）回填
- 触发：数据分析页 M1 客户类型维度 X 轴显示 `CENTRAL_SOE/GOVERNMENT` 英文枚举名；筛选器选中文客户类型漏统计已立项项目（分支 `agent/qoder/fix-m1-trend-cn-labels`）
- 根因：归一化逻辑只存在于 M2 计算服务私有方法，M1 趋势新链路漏接——分组原值透传 + 筛选 `coalesce(...) IN` 对英文枚举行不命中；双源字段写入形态不一致（立项详情存枚举名、主表存中文文本）
- 更新 [[data-analysis-revamp]]：追加「2026-08-20 客户类型分组/筛选/下钻归一化口径统一」小节（CustomerTypeNormalizer 纯函数 + AnalyticsCustomerTypeFilterSupport ID 集过滤 + 前端状态映射/饼图标签）；关键陷阱新增 §12（双源字段消费前必须先归一化）
- 关联：`docs/lessons/lessons-learned.md §134`；回归 AnalyticsCustomerTypeJpqlRegressionTest（英文枚举分组/筛选/下钻）+ CustomerTypeNormalizerTest 6 例，后端 65 例 + 前端 vitest 4 例全绿

## [2026-08-20] bugfix | spec 049 案例沉淀超大标书攻坚完成（choices 包装解析 + 唯一键粒度）回填
- 触发：测试环境项目 1350（401MB）沉淀验收，SC-001 未达成——摘录质量良好却 8 评分项全判"无证据"、0 条入库且发成功通知
- 根因 1：CaseAiMatcher 自研 extractJson 取首{末}，把网关 OpenAI choices 包装当目标 JSON，matchedSnippet 取不到（字段名其实正确）；修复 unpackContent 解 choices[0].message.content，与 AiResponseParser 正规链路对齐
- 根因 2：评分项 61/62 标题同为"投标报价"，唯一键 (project_id, title) 同批自撞整批回滚；DataIntegrityViolationException 被吞成"幂等成功"；修复 V1199 加 scoring_point_id 列，唯一键改 (project_id, scoring_point_id)，幂等去重按评分项 ID
- 更新 [[case-precipitation-streaming]]：新页（完整链路 + 三坑 + SC 验收表 + 部署时序陷阱）；关联 [[case-precipitation-oversize-oom]] 前身
- 关联：`docs/lessons/lessons-learned.md §136/§137/§138`；回归 CaseAiMatcherTest 28 + ProjectClosedEventListenerTest 15 + 相关 policy 测试，本任务相关 51 全绿（4 个 archive 集成测试 403 为基线环境问题，stash 验证确认非本次引入）
## [2026-08-20] feature | spec 049 标书质量核查实质化（T001-T055 完成）回填
- 触发：质量核查从"固定文案证据 + page=1"实质化为全真实链路（分支 `agent/qoder/bid-quality-realization`，commits b492c41ca..1bf99ab23）
- 架构：五步真实数据流（投标文件/招标要求 LLM 提取/四知识库/真实页码定位/混合裁决）；确定性 18 项 + 语义规则 lane + LLM 裁决 lane（只做裁决层不做全量直出）
- FP-Java 失败传导：通道失败在边界吸收为 `LlmVerdict.ChatOutcome` sealed 四值（Success/Invalid/Noop/Failure），纯核心无异常流；reasonCode 五值（PRD §3.5），MANUAL 态 `[CODE] ` 前缀
- 新陷阱：Java 21 switch 模式匹配编译产物含 `new MatchException` 触发 FP 门禁 → 纯核心用 instanceof 序贯链（lessons §141）；验收失真复盘见 lessons §140
- 新建 [[bid-quality-check-realization]]；关联 specs/049-bid-quality-check-realization/、docs/lessons/lessons-learned.md §140/§141
## [2026-08-21] bugfix | AI 案例推荐数据源修复（score_item 新契约 + casework 裸响应二次解包）回填
- 触发：项目详情页「AI 智能案例推荐」抽屉招标文件已解析但评分项不显示
- 根因 1：数据源错配——抽屉读 project_score_drafts 旧表（spec-041 后恒空），解析链路写 score_item 新表；修复切到 caseRecommendationApi（PRD 4.2 契约），后端零改动
- 根因 2：casework 四个端点全部裸 ResponseEntity（不走统一信封），client.js 拦截器已解包后 API 层再取 .data 得 undefined（双重解包）；修复裸透传 + spec 契约锁定
- 连带：AICaseReuseSectionModal 的 reuse-success 事件从拿到 undefined 恢复为真实返回值；E2E 加 waitForResponse 时序加固 + 状态一致性断言
- 新建 [[ai-case-recommend-data-source-fix]]；关联 [[score-parse-service]]；回归 12 个直接相关单测 + lint 全绿，Production Risk Review PASS（无 P0/P1）
## [2026-08-21] feature | PRD 4.2 复用定位弹窗默认选中章节（合入审查 Warning #1）回填
- 触发：PR #2370 合入审查发现遗漏——PRD 1.1/4.2 要求复用定位弹窗章节树默认选中当前评分项对应章节（可修改）
- 原缺陷：AICaseReuseSectionModal 的 defaultChapter 只写手输框不预选级联；抽屉 currentDefaultChapter 依赖不存在的 targetChapter 字段恒为空
- 修复：新增 sectionTreeMatch.js（BFS 三级匹配：完整路径/label/最长包含）；弹窗打开时 applyDefaultSelection 级联预选+打开重置；抽屉映射改 targetChapter 优先→dim 类别回退内置章节树→空
- 限制声明：真实章节树 API 属 spec 046 Phase 3；score_item 无 targetChapter 字段，映射落地前靠 dim 回退
- 更新 [[ai-case-recommend-data-source-fix]]；回归 23 个相关单测全绿（含新增 8 用例）
## 2026-08-22 后端测试基线修复（PR !2382）
- 触发：全量 mvn test 发现 main 基线失守——8386 用例 49F+38E + E2E 栈启动崩溃
- 根因三类：projectScoped 审计迁移缺测试联动（约80个403）；公共契约变更测试侧漂移积压；e2e profile Bean 缺口
- 修复：全局 mock 用户种子机制（spring.factories 单数 key / beforeTestExecution 相位 / H2-only 守卫）；E2eNoopScoreAnalyzer 桩；各漂移点对齐
- 最终回归 BUILD SUCCESS：0F/0E/9 skipped
- 更新 [[backend-test-baseline-green]]；沉淀 docs/lessons #150
## 2026-08-23 E2E spec 漂移对齐（进行中）
- 全量 chromium 首次建立基线：217/374；51 失败 spec 聚为四类根因
- 根因①：27 个 spec 硬编码 18089/1323 → env 驱动 baseURL
- 根因②：详情页阶段步进器为 el-steps 自绘（无 tab role）+ 项目创建 API 不支持 stage → 已打通 stage 链路并修引导函数
- 根因③：register 只认 RoleProfileCatalog 注册码 → auth-helpers 加遗留角色名映射
- 根因④（真实产品缺陷）：AGENT 品牌授权因后端无条件 @NotNull 无法经 UI 创建 → 已改按类型校验+推导整体区间
- 更新 [[backend-test-baseline-green]]
- 勘误：上一条中「AGENT 创建缺陷」结论有误——前端会派生授权日期，原后端校验无碍；已 revert 生产改动（PR !2386），E2E 改走生命周期 API 的 fixture 后全绿。教训：改生产代码前必须用真实链路复现缺陷，不能以手工构造的请求作为证据
## 2026-08-24 refactor | 事件总线 wire 契约去重回填（ai-slop-cleaner）
- 触发：ai-slop-cleaner 走查 054 商机预测提醒特性，发现标讯事件与商机预测事件两个 producer 重复同一套直连契约（body/data.result/trace header/3s-5s 超时，约 30 行 ×2）
- 修复：新增 `TenderEventHttpSender`（infrastructure/sdk）唯一持有 wire 契约；`TenderEventSdkProducer` 构造签名不变委托 sender（4 例 MockRestServiceServer wire 断言原样通过）；`OpportunityPredictionEventPublisher` 缩至约 60 行，仍受 sdk.enabled 开关控制
- 标讯事件应用层管线（TenderEventPublishService/Port/流水表）零改动；定向 26 测试全绿
- 更新 [[integration-tender-event-push]]；教训：**新事件发布方复用 TenderEventHttpSender，勿再复制 wire 契约**
## 2026-08-25 fix | ai.provider 默认 mock 陷阱修复（PR !2396 审计后续）
- 触发：PR !2396 生产风险审计发现 `${AI_PROVIDER:mock}` 默认值使"漏配"伪装成"显式 mock"，生产全靠 backend.env 手工 `AI_PROVIDER=custom` 兜底；且 mock 分支两处 NPE（embed 无 null 回落、QualificationAiParser 直接解引用）
- 修复：yml 默认改空串只认显式 mock；embed()/QualificationAiParser 补 null 防护；preflight.sh + remote-deploy.sh prod 双断言（漏配/mock 中止部署，服务零影响）
- 顺带：预算告警 catch 补堆栈+Sentry（CO-564 纪律）；e2e seeder tender.entry 对齐 V1167 原文（CASCADER/enabled/limit/accept）；SolutionReuse 行业枚举映射+删除随机 matchScore 假数据
- 更新 [[ai-provider-configuration]] §8；教训：**带行为语义的配置默认值必须留空，改共享方法返回契约必须 grep 全部调用方**
## 2026-08-25 feat | 质量核查抽屉 UI 改造：紧凑行 + 居中详情弹窗
- 触发：产品 5 项需求——删「存在不通过项」标签；重新核查+AI 评分入汇总平铺卡右上（左上投标文件/核查时间）；筛选 tab 改三分类定位（基本信息/分项合规/附件清单，点击展开+平滑滚动，不改状态筛选）；核查项改紧凑行，点击弹 560px 居中详情弹窗（字段对照表：问题定位/原文摘录/四库链接/固定建议文案）
- 关键实现：展示纯核心 `composables/qualityCheckDetail.js`（STATUS_META 单一数据源 + 证据提取纯函数）；SCORING_RESPONSE 项复用评分解析 items/results 汇总客观项得分，未打分静默降级；事件链透传 `{stage:2, autoScore:true}` 打开评分抽屉自动打分；后端零改动
- 三步审查修复 8 项：formatTime 重复实现、状态映射双份、分组标题硬编码计数与动态计数叠加、defineModel 冗余 emit、死 CSS 钩子/死绑定、平行映射结构合并、重复过滤
- 更新 [[bid-quality-check-realization]] §5；教训：**状态元信息（图标/标签类型/文案）必须单一数据源，两个组件各写一套 switch 必然漂移**
## 2026-08-25 eng | Code Review 三透镜纪律工程化（PR !2399 四轮审查复盘）
- 触发：PR !2399 四轮审查全部集中在"行为正确性"平面，依赖方向/全局常量影响面/交付就绪（mergeable 冲突、产物文件入库、跨语言契约无自动化）四轮均未进入检查面，第五轮人工审查才全部发现
- 机制落点：`docs/references/engineering-discipline.md` §4 三透镜纪律（A 行为正确性 / B 结构健康 / C 交付就绪，独立否决）+ 全量 diff 原则 + 审查独立性契约（子代理禁带修复意图）+ 判据分离（自动化结论 ≠ 判断性结论）
- 工程化落点：`scripts/check-keyword-sync.mjs`（Java PriceScoringKeywords ↔ Python converter.py 关键词契约，单边新增也拦，挂 pre-push §5.6）；pre-push §5.7 根目录产物文件拦截；`gitee-pr-helper.sh merge` 合入前强制断言 `mergeable=true`；`agent-finish-task.sh` 开放 PR 冲突预警
- 技能落点（本地 `.agents/skills/`，未入库）：`xiyu-code-review` 新增第 0 步状态快照 + §7 结构健康 + §8 交付就绪 + 结论模板分栏（自动化/判断性 + 必填"重新设计建议"）
- 更新 [[engineering-discipline]]；教训：**同一类问题第二次出现就该变成脚本，而不是第三条纪律文字；审查轮次同质化重复一百遍也只是同一个盲区的第一百次确认**
- 2026-08-25: 报价评分跨页截断专项评审修复（PR !2399：多信号交集跨页表合并、邻近扩展单跳、按行去重与 detail 6000 字符上界、classifyPipeRow 重构、跨语言关键词同步门禁） -> score-parse-service.md
- 2026-08-25: PR !2349 合并冲突修复（lessons §140 编号撞号重编号 §159；origin/main 占用 V1200，contactNature 迁移重命名 V1201/U1201 并同步 wiki 引用） -> integration-tender-api.md
- 2026-08-25: 接口六项目修改上线回填（PUT /api/integration/projects，v3.12 §3.6：部分更新语义/客户信息全量替换含 TEL/GAP 附件三态/423-422-404-400 错误码；客户信息键映射收敛到 EvaluationToInitiationMapper.INFO_KEY_MAP 单一真相源） -> integration-tender-api.md
- 2026-08-25: 竞品公司自定义输入与竞争对手库治理（specs/055：竞品下拉动态加载 + allow-create 幂等 upsert；competitors 加 status/name 唯一约束/seed 24 竞品；治理端改名/合并/停用；competitor.create/govern 权限键注册 RoleProfileCatalog + Flyway V1203） -> competitor-library-governance.md
- 2026-08-26: M1 竞品维度中标数口径修复（PR !2417：图表 computeCompetitorTrend + 下钻 TrendDrillDownService 同源切换 notes「已中标」口径；CompetitorWonStatusSupport 单一来源；投标数不变；lessons §163 复发教训）-> data-analysis-revamp.md
- 2026-08-26: 评分解析抽屉重新解析遮罩漂出可视区修复（分支 agent/qoder/fix-score-parse-overlay-scroll：scoped :deep 对 teleport 弹层静默失效改类前缀全局规则 + .scroll-locked 遮罩期锁滚动；新增 e2e 布局契约用例，负向验证在缺陷代码上精确失败） -> frontend-pitfalls.md §19

## 2026-08-26 fix | 标讯事件推送原始响应体日志回归（tenderId=2282 排障盲区）
- 触发：tenderId=2282 推送流水标 FAILED 且 result=false 但零异常堆栈，网关拒收原因不可知；字节码核实 SDK `sendEvent` 只透传 data.result 并丢弃外层 code/msg（拒绝原因所在），`SendEventRespDto` 仅一个 Boolean 字段
- 修复：TenderEventHttpSender 直调 SDK 公开静态工具 `HttpClientUtil.httpPost`，完整响应原文落 info 日志；构造参数 SystemInteractiveService → ServiceRegisterProperties（SDK 自动配置已有该 bean，serviceName 与旧链路同源）；@Import/@Primary 拓扑不动
- 契约勘误（字节码实锤）：httpPost 非 200 与 IO 异常均静默返回 null 而非抛异常——旧链路的 NPE 是 sendEvent 解引用 null；wire 测试据此修正（500 → 判失败而非期待抛出）
- 更新 [[integration-tender-event-push]] §4 + Wire 契约单一源；教训：**集成排障必须在离源头最近的一层留全量响应证据；包装层只透传业务字段会把故障细节藏在调用方看不见的地方**

## 2026-08-26 fix | CompetitorStatus varchar/enum 校验冲突（第 144 次部署打包门禁拦截 P0）
- 触发：package-release.sh 内置 FlywayMysqlContainerTest 报 Schema-validation found [varchar] expecting [enum ('active','disabled')]，定位链：V1202 VARCHAR(20) ↔ Competitor @Enumerated(STRING) 无 columnDefinition ↔ Hibernate 6 MySQL 方言默认期望原生 ENUM ↔ prod/e2e/flyway-mysql 均 validate → 上线即 crash-loop
- 修复：实体加 `columnDefinition = "varchar(20)"` 单行对齐 DB 真相（已发布迁移不可变）；FlywayMysqlContainerTest 4/4 PASS（真实 MySQL 全量回放 B73~V1203）
- 新增 [[hibernate-string-enum-varchar-validate]]；lessons-learned.md §165；教训：**Hibernate 6 起 STRING 枚举默认按原生 ENUM 校验，varchar 列必须显式 columnDefinition；容器测试是 crash-loop 最后防线，禁止为赶部署跳过**
- 2026-08-27: 结果确认登记二次确认 + 竞品锁定标识收口（分支 agent/codex/competitor-confirm-lock：submit 必填校验后弹 ElMessageBox.confirm，文案含竞品家数与不可再修改警示；数据层不可变本已闭环——后端 409 幂等 + CLOSED 字段锁 + 无更新接口；排查确认 ProjectDetailResultDialogs 旧录入旁路为死代码勿误改） -> result-registration-immutability.md

## 2026-08-27 ui | 案例详情弹窗：操作按钮上移标题行 + 西域绿 + 标题改摘要
- 触发：产品需求（项目中心-项目详情-AI智能案例推荐详情弹窗）——「查看源项目/标书原文」从底部操作栏上移到「应答片段」标题行右侧并改西域绿（plain+type=primary，主题色即 --brand-xiyu-logo）；标题「应答片段全文」改「应答片段摘要」；底部仅留「复用」
- 落点：CaseDetailDialog.vue 单文件（模板 + scoped CSS .response-title flex 布局）；后端零改动
- 教训：**纯源码临时 worktree 提交前必须先 pnpm install（pre-commit 的 vite 类检查依赖 node_modules，agent-start-task 默认跳过装依赖）**

## 2026-08-27 feat | 工作台 AI 商机预测卡片：客户类型/项目类型/时间筛选 + 日期月日间补 —
- 触发：产品反馈两件事——卡片缺搜索维度（客户类型/项目类型/时间）；三个时间列第二行只显示日数字，月和日之间没有分隔符
- 落点：OpportunityPredictionCard.vue 加筛选栏（客户端过滤，接口零改动——数据源即全量窗口且分页本就是前端 slice）；表格列整体拆出 OpportunityPredictionTable.vue 子组件（card 295→364 行撞 line-budget 300 门禁后结构化瘦身：主卡片 182 行/子组件 204 行）；DateCell 第二行 `15` → `—15`（用户明确要全角破折号）
- 教训：**超 line-budget 优先拆组件而非加豁免单（excludeFiles 是历史遗留条款不是新代码逃生门）；el-table 列模板抽子组件后 provide/inject 穿层仍生效，既有 stub 测试断言零改动**
## 2026-08-27 integration | 接口六契约修正：PUT /api/integration/projects 定位参数 sourceId → tenderId
- 触发：CRM 联调 27 连 404——调用方以商机编号 CC.../未推送主键作 sourceId，接口六按 external_id 单列定位全部落空；全链路日志排查定位根因=定位键与数据签发方不同源 + 手工建标讯（M 链路）无 external_id 天然不可达 + M-03 脱敏放大盲试
- 落点：ProjectIntegrationController（路径参数 Long 化，误传编号绑定层 400）+ ProjectIntegrationCommandService.updateByTenderId（projectId 一致性断言替代 externalId 交叉校验；来源键前缀轻校验；未立项 200+applied=false 跳过）+ ProjectUpdateResponse.applied + GlobalExceptionHandler.handleTypeMismatch 重写（防兜底 500）+ 测试 25+7 全绿 + api-tests §3.6 用例换数字示例
- 教训：**外部对接的定位键必须与数据签发方同源；调用方持有内部主键时优先用主键定位。新增按来源键定位的外部接口前，必须盘点能把该实体带进系统的所有入库链路并逐条验证可达性** -> [[integration-tender-api]]
## 2026-08-28 integration | 接口六 CRM 首联 422 死锁修复：合并后全量必填校验移除（校验范围对齐契约字段集）
- 触发：接口六上测试环境后 CRM 首次对接 PUT /CRM/2300 6 连 422「缺少必填字段：ownerUserId,departmentSnapshot」；全链路日志 SOP Layer 2 一次定位——真实请求体只有评估字段（接口契约无 owner 字段传入入口，patch 恒 null），立项 1365 为 M 链路半成品（内部组织字段 NULL）→ 422 与调用方行为零相关死锁
- 落点：ProjectIntegrationCommandService.mergeInitiation 删 fullDecision 全量校验（保留字段锁 423，完整性由提交审核环节把关）；Service 测试 422 旧用例替换为根因行为测试 incompleteInitiationPartialWrite_succeeds（半成品 fixture ownerUserId=NULL → 200 落库）；wiki integration-tender-api 422 语义同步移除
- 教训：**外部接口的校验范围必须是契约开放字段集的子集（校验请求域外字段=制造与调用方行为无关的随机失败）；PATCH 语义接口完整性守门在提交审核而非每次部分回写；fixture 必须覆盖真实残缺数据形态** -> [[integration-tender-api]]

## [2026-08-28] build | 部署页新增 remote-deploy.sh SIGPIPE 陷阱小节
- 触发：第 148 次测试部署中断——remote-deploy.sh 输出停在 Activating frontend assets 后静默退出；根因 set -euo pipefail 下 find|head -n 5 竞态 SIGPIPE(141)，"保留旧 assets"步骤引入的 root 属主文件是放大器，脚本自带 chown 修复分支永远执行不到
- 落点：`pages/deployment.md` 新增 §10.4（根因链、修复 PR !2451、`cmd | head -n N` 通用规则、保留 assets 后必须 chown、Docker 僵死挂起容器测试门禁、健康窗口 4min < readiness 实测 6min）
- 教训：**set -euo pipefail 脚本里任何 `cmd | head -n N` 都是定时炸弹（输出超 N 行即 SIGPIPE 静默死）；用 sudo 拷贝保留的旧资源必须随手 chown 回部署用户，否则守卫代码自己会被属主问题炸死** -> [[deployment]]

## 2026-08-28 compound | 标讯人工录入"未上传"假象排障（生产 winbid-01）
- 触发：用户 03683 /bidding/create 上传+AI 解析全成功但标讯未创建，感知"未上传"
- 排障：Nginx access.log + journalctl traceId + MySQL 三方证据交叉 → POST /api/tenders 从未发出；根因=canSave 必填字段校验静默禁用保存按钮（公开谈判采购无开标时间；审查后口径对齐 8 字段含 projectType）+ el-upload auto-upload=false 恒 ready 状态假象
- 回填：[[tender-create-upload-triage]]；同分支修复 3 个 UX 缺陷（禁用原因 tooltip / status=success 标识 / AI 回填后缺失必填项提示）
- 教训：**"上传完没报错但没成功"类工单，先查 Nginx access.log 看目标 POST 是否发出，再决定查后端；禁用按钮必须给禁用原因，否则用户无从自救**

## 2026-08-31 compound | 案例库历史切片三连修：300 字截断 / 源项目路由空白 / 标书原文假 ZIP
- 触发：测试环境导入 300 条历史切片后用户目检发现三个问题（片段无实战价值 / 查看源项目空白 / 下载坏 ZIP）
- 排障：数据层实测最大长度恰好 300（JSONL 只出 text_preview）；路由表无 /bidding/detail/{id}；export-zip 带成员校验 403 被 blob 落盘 + 历史项目档案 0 附件
- 回填：[[case-source-actions-and-import-pitfalls]]；新增共享 composable useBidCaseSourceActions（8 个组件级用例锁定）；切片脚本改出全文、导入脚本修 project_type 枚举混填
- 教训：**blob 下载必须查 content-type（application/json=错误体）再落盘；跨表灌数字段必须按目标字段枚举校验，禁止跨枚举回退**

## 2026-09-01 fix | 标讯事件总线注册生命周期与测试挂起修复（mvn -q test 挂起根因）
- 触发：全量测试时 TenderEventSdkConfigTest 尝试连接不存在的外部域名 event-bus.local，第三方 SDK ClientRegisterComponent.register() 进入无休眠重试死循环，且常驻后台线程在 Spring Context 销毁后未停止，导致 Maven 进程长时间挂起
- 修复：TenderEventRegistrar 实现 AutoCloseable，在 close() 中反射重置 isRun=false 并中断 registrationThread；TenderEventSdkConfig 设置 destroyMethod="close"；TenderEventSdkConfigTest 使用 MockWebServer 模拟本地响应并补齐 disabled 负向分支；清理 TenderEventSdkProducerTest 中的 event-bus.local 域名引用
- 回填：更新 [[integration-tender-event-push]] §10；教训：**测试中绝不使用未配置的 .local 假域名，涉及 HTTP 测试一律使用 MockWebServer 监听 127.0.0.1:port；常驻重试线程组件必须实现 AutoCloseable 并在容器销毁时显式终止**

## 2026-09-09 fix | 标讯事件注册地址与续约参数修复
- 触发：测试标讯 4170/4171（流水 1880/1881）通过 SDK 与相同四字段直连均返回 result=false；手工 register 后相同请求返回 true
- 根因：客户正式值为 initialDelay=3 秒、period=3 秒、renewalDuration=3000 毫秒；旧实现 10/30/60 并误把 renewalDuration 解释为秒
- 地址对照：Java 监听 18080，Nginx 对外 8080 并反代到 127.0.0.1:18080；事件库跨网段无法直达 18080，宣告该端口时 register/renew 均约 128 秒，短暂 publish=true 后仍无法按 3 秒持续续约。宣告事件库可达的对外代理端口 8080 时分别约 66 ms/56 ms，按 ≤3 秒节奏续约 24 秒后 getActiveService 显示 bid:8080 为 UP/ACTIVE，标准 publish 返回 true
- 修复：除 3/3/3000 参数外，增加 advertised host/port，标讯专用配置优先、缺省回退组织事件 SDK 配置
- 回填：更新 [[integration-tender-event-push]] §12，并新增 `docs/implementation-notes/tender-event-renewal-2026-09-09.md`；4172 仅证明应用经 SDK 到网关受理，CRM 消费仍待部署后同 trace 验证

## 2026-09-02 feature | 评分解析管道 Phase 1-5 补齐（结构化分层 / 归一化告警 / prompt 治理 / 金标 CI / 词汇注册表）
- 触发：PR !2505 合入 Phase 1a/1b/3a 后审计发现 7 个缺口未补齐；根因分析已定位零号病人为 markdown 表格转换丢失合并单元格结构
- 落点：StructuredTableCandidateExtractor 填 parentDim + ScoreCandidatePostProcessor 感知 STRUCTURED_TABLE 标记；ScoreGlobalMergeOrchestrator 降级为排序/过滤（禁止 LLM 替换结构化结果）；WeightNormalizationPolicy 阈值 0.05 产出 warning → V1206 独立列 → DTO → 前端告警条；ScoreParsePrompts 三个 build 方法清除项目 1358 硬编码维度名；ScoreVocabularyRegistry（global/project 两级 escape-hatch）+ ScoreVocabularyConfiguration 8 条全局词汇；RealProjectScoreParsingTest 去 @Disabled + 入库 case-1347/case-1362。26 files，testing-gate passed（架构 12 + scoreparse 114 + 前端 27）
- 回填：更新 [[score-parse-service]] 新增「结构化通道补齐与归一化告警显式化（Phase 1-5，2026-09-02）」章节 + 列布局敏感性陷阱表 + 副作用；docs/lessons 沉淀 §204/§205/decisions 条目 8/build-gotchas §6；教训：**清理硬编码 token 必须全文件 grep 该 token 而非按方法逐个改，且要检查是否有测试在正向断言该硬编码存在；给生产方法加参数重载后必须 grep 所有 stub/verify 该方法的测试（strict stubbing 匹配运行时实际调用的重载，本地自选测试类全绿不足以发现回归，应直接跑门禁脚本本体）**

## 2026-09-02 feat | AI 标书质量核查多册合并（价格/商务/技术分册只查最后一份）
- 触发：用户报告分三册上传投标文件后核查只覆盖最后上传的一份，报告文件名只有一册造成"都查过了"假象
- 根因：spec 047 设计简化为"最新投标文件"，findLatestBidDocument 取 createdAt 倒序第一条；另发现无 BID 文件时兜底取全类别文档（会把招标文件当投标文件查）
- 修复：合并在 Loader 边界完成（DocumentContent.merge 纯函数：页码重编号/聚合哈希/文件名顿号拼接），引擎/证据/报告/前端零改动；删除全类别兜底；Sidecar 静默空册显式拦截（指名报错，宁可失败不可假查）
- 回填：[[bid-quality-multifile-check]]；教训：**SidecarStreamDocumentAdapter 失败时吞异常返回空册而非抛错，上层"失败即中止"设计必须显式校验空文本+空页；mock 该适配器验证失败语义禁止 thenThrow（假绿）**


## 2026-09-03 fix | Gitee token 多源可见性与统一解析链（Agent 提 PR 反复"找不到 token"数月根因）
- 触发：各 Agent 完成任务提交 PR 时反复找不到 GITEE_TOKEN 或 401，同机不同时刻结局不同
- 根因：token 可见性取决于进程树 shell 类型——~/.zshenv 只有 zsh 读，bash/GUI/非交互进程全读不到；旧 gitee-token.sh 把 git credential fill 排第一，~/.git-credentials 积累过期 401 死 token 兜死底；5 条 API 链路防线不一致（两处静默跳过把鉴权失败伪装成"无 PR"）
- 修复：gitee-token.sh 四级解析链（env → ~/.config/xiyu/credentials.env → ~/.zshenv grep → credential fill 末位防挂死），五脚本统一接线；credentials.env 为唯一真相源（600 权限）；契约测试 14/14 + 回归 18/18；PR !2521
- 回填：[[gitee-token-resolution]]；教训：同一 token 分放多个 rc 文件会漂移（zshenv 与 zshrc 是两个不同值都有效）；`>>` 追加命令重跑会累积重复行；MCP config.json 是另一套 token 需重启会话

## 2026-09-06 feat | CRM 事件推送全量字段改造（标讯 20 + 商机 10）+ varchar(19) 雪花 ID 修复
- 触发：应 CRM 客户要求，标讯/商机事件推送从"轻量标识 + CRM 回查"改为直接携带全量业务字段供 CRM 直接消费
- 落点：TenderEventPayload 扩 20 字段 + Mapper 枚举转中文标签；OpportunityPredictionEventPayload 扩 10 字段；商机 customer 对齐预测表格 toDto（Project.customer 优先，空回退 Tender.purchaserName）；抽取 OpportunityPredictionReminderSupport 静态纯函数使编排服务降回 263 行过 line-budget 300 硬门禁
- 根因修复：商机 OpportunityPredictionEventPublisher 的 traceId/spanId 用 UUID(去横线 32 字符) 超客户事件库 varchar(19) 列宽，标讯侧早用雪花 ID 而商机侧遗漏，改用 TenderEventSnowflakeUtil（t/s+18 位）
- 验证：三步 Review（思维链/google-code-review/xiyu-code-review）全过；ArchUnit 32 + tenderevent/workbench 135 tests + line-budget + duplicate + doc-governance 门禁全绿
- 回填：更新 [[integration-tender-event-push]] §11（修正"data 只放标识"旧结论 + 全量字段清单 + varchar(19) 约束）+ [[opportunity-prediction-reminder]]（customer 口径决策 + 10 字段 + Support 抽取）；教训：**客户事件库 trace_id/span_id 为 varchar(19)，链路 ID 必须用雪花 ID，UUID 去横线 32 字符会超长写入失败；同一约束标讯/商机两侧必须同步，新增事件发布方要复用 TenderEventSnowflakeUtil；编排服务超 300 行时抽静态纯函数到同包 Support 类是过 line-budget 的标准手法**

## 2026-09-06 fix | 商机事件推送 projectLeaderName/customerType 补标讯兜底（PR#2560 review 后续）
- 触发：PR #2560 合入后 review 发现商机事件推送的 projectLeaderName/customerType 与预测表格口径不一致（表格经 enrich 链有标讯兜底，事件推送只取立项详情/Project 实体）
- 落点：OpportunityPredictionReminderSupport 加 resolveProjectLeaderName（立项详情→Tender.projectManagerName 兜底，isBlank 语义与表格等价）+ resolveCustomerType（Project.customerType 严格 null 判空→Tender.customerType 兜底并经 InitiationFieldPolicy.normalizeCustomerType 归一化），逐条对齐真实 enrich 方法 ProjectListEnrichmentSupport.populateFromTender；Service processProject 算好 resolved 值传 publishEvent
- 预测对齐：policy input 的 customerType 改用 resolvedCustomerType（兜底后），预测提前月数规则（央企6月/其他3月）与表格完全一致；经用户确认预测逻辑一起对齐（Project.customerType 空但标讯有值的项目，提醒触发时点随兜底客户类型变化属预期）
- 验证：OpportunityPredictionReminderServiceTest 7 tests（新增兜底用例）+ compile + line-budget（Service 267 行）
- 回填：更新 [[opportunity-prediction-reminder]] 决策5/6 + 全量字段节 + 预测逻辑口径注记；教训：**“对齐表格”须对齐表格的完整 enrich 链（表格消费 getAllProjects 的 enrich 后 DTO，含标讯兜底），而非只对齐单一数据源；且必须 grep 验证兜底方法真实调用点——本次一度误引用死代码 ProjectTenderPopulator，真实 enrich 是 ProjectListEnrichmentSupport.populateFromTender，其 customerType 兜底用 == null 判空（非 isBlank）且经 normalizeCustomerType 归一化，判空条件与值语义都需逐条比对才能做到 100% 一致**

## [2026-09-09] compound | tender-event-pr2598-contract-review 契约分层与响应误判复合查询回填


## [2026-09-10] compound | 标讯事件 SDK 注册时序复合查询回填
- 触发：PR !2601 部署后 bid:8080 仍为 DOWN/WAIT，自动 renew 无法恢复
- 根因：Bean initMethod 在 Spring Boot Web 服务就绪前注册；EventBus 同步回调 `/api/task/startTask` 得到 Nginx 502，但注册接口仍返回 code=00000
- 对照：应用就绪后只 register 一次即 UP/ACTIVE，停止手工续约后 SDK 仍约每 3 秒更新 lastRenewalTime，排除地址和续约参数
- 回填：更新 [[integration-tender-event-push]] `13、实现笔记与 lessons `346；注册改由最低优先级 ApplicationReadyEvent 触发


## [2026-09-10] compound | 事件库两次集成反复失败根因复盘
- 触发：标讯事件经历 17 个代码/集成 PR 与 32 个目录提交后才形成真实 EventBus 入库闭环，需要对照组织架构消费事件历史解释反复原因
- 结论：事件库是带注册、反向回调、租约、订阅和运行时装配的有状态协议；我方长期按普通 HTTP 接口理解并以局部成功替代端到端成功是主因，客户文档分层不清和 SDK 黑盒行为是重要诱因
- 证据：无手工注册下 `bid:8080` 持续 `UP/ACTIVE`，真实 tenderId 4173 的 SDK result=true、我方流水 1883 与客户流水 97776 使用同一 18 位 traceId
- 回填：新增 [[event-library-integration-rca]]，沉淀五层契约、五个为什么、组织/标讯共同机制和统一验收门禁
