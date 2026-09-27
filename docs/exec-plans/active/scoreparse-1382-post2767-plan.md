# Score extraction and active-version implementation

## Global Constraints
- 用户已批准实现。候选允许评分；未知/冲突数值及未确定聚合不可计算，不伪造0/100。
- 首次可计算解析立即可用；不完整重解析保留旧版本并可显式切换；人工编辑不可自动覆盖。部分采纳保持兼容。
- 原文、模型解释、人工修改分开。没有来源证明不能标为已验证。完整快照一直可见。
- 无1382/页码/名称/分数生产硬编码，无任意模糊匹配，无跨文档跨方案拼接。
- Java纯领域无框架，新增类尽量200行内，兼容旧API/产物。新链路开关默认关闭。
- 每项先根因测试再实现。真实fixture与预期独立。不得部署、改线上数据、启动共享服务、push或merge。工作树交付，不自动提交。

### Task 1: Source evidence and anchored extraction
范围：Sidecar PDF logical rows、后端 evidence binders/grid、OpenAI anchored prompts及mapper、对应测试。
1. 从私有真实artifact而非expected建失败输入；五个漏项覆盖中文换行、跨页引用、模型错字/重复。真实PDF SHA 18061117e166335881c6a1864f708e5109988af773aea321b26c0f4b751ae18f。
2. 补充可追溯 SourceSpan：文件身份/格式/方案/页/表格/单元格或文本块/字符偏移；原文不覆盖。规范化保留偏移映射，只消除确定布局空白。
3. 同一业务项跨页连续性要求同文件同方案相邻页、连续表结构和空标识续行；有新项、新章、未知来源或歧义不得连接。保留原物理坐标。
4. bundle保留全部文档textBlocks及源类型；不能只用首文件meta分流。
5. artifact锚点在模型调用前构造，prompt带可引用ID；模型引用ID校验后恢复原文。没有锚点的旧模型输出走兼容保守路径。已有模型配置不变；未解析片段最多一次有界定向重试，禁止全文件无限重采样。
6. 源名称/类别/最大分明确区分；共享格最大分按名称/父级语义归属，不借相邻数字；mapper原文使用已验证原文，不以ruleText概括替代。
7. 保持负例（跨章、同名、共享上限、否定、更正、比例歧义）拒绝验证；允许保存未验证候选。
8. 测试PDF/DOCX/text/OCR、混合顺序；真实回放应识别13叶子、业务15技术45价格40，但这些仅测试预期。错误物流引文必须被诊断或凭有效锚点恢复，不能模糊升格。
报告记录实际命令、RED/GREEN、未覆盖边界。只做本任务，不实现数据库/API/UI。

### Task 2: Extraction snapshots and version activation
范围：后端domain/application/entity/repository/dto/controller、迁移和测试。
1. 版本化 ExtractedScoreItem payload保存所有候选：stable itemId、scheme/file、name/category/rules/docs、rawMaximum/schemeRatio/effectiveMaximum可空、parent/aggregation、字段来源、独立diagnostics/computability。兼容历史facts。
2. facts增加版本化item_payload且weight可空，正式项source_fact_id；迁移增量、回滚完整，不重写历史数据。必要新增版本记录支持CAS/活动版本；先阅读create-migration技能。
3. scheme级归集独立提取/可计算/生效/声明总分，未知不默认100；物理表拆分不能丢方案总分。保留未验证项，不将validation失败当提取失败。
4. 增加 GET extraction/latest、GET extraction/{taskId} 和 POST extraction/{taskId}/activate（现有score-parse base下）；激活请求包括schemeId、expectedActiveFingerprint、reason。保持项目权限/任务归属/旧review和items兼容；接口明确400/403/404/409。
5. 原子激活使用项目锁和当前指纹CAS；首次单方案可计算候选可立即用。不完整/未知声明总分/冲突/减少/人工改旧版本的重解析不自动替换。失败/空/多方案不清空旧结果。用户显式选择可部分采纳。
6. 成功采纳不覆盖验证质量，不清除未采纳快照；正式项保留原fact身份/原文/scoreType/confidence/validation。未知权重不可0。
7. 特性开关默认关闭，关闭保持旧路径；开启贯穿新解析保存/读/激活。校验两种行为。
8. 测试首次、完整/不完整重解析、手改保护、部分切换、旧任务、跨项目、权限、双并发、事务回滚、历史payload缺失。DTO契约写specs/073.../contracts。
不改前端；交付接口说明供下一任务。

### Task 3: Scoring version consistency
范围：ScoreScoringAppService、persistence fingerprint、result保存边界及测试。
- 可计算候选能评分；不可计算项显式原因，不强制weight0。
- 指纹至少包括来源版本/fact身份、名称、规则、weight、scoreType、父子/聚合；同code不同规则不复用结果。
- 异步评分启动和最终落库校验相同生效版本，旧任务不能覆盖新版本；项目并发边界与激活一致。
- 版本切换和结果保留有确定性测试；保持旧API兼容。

### Task 4: Frontend snapshots and active versions
范围：API、projectDetail composables、ScoreParseDrawer/卡片/表格及Vitest。
- 同时展示最新提取结果和当前生效结果；所有候选始终可见，部分采纳后也不隐藏。
- 展示提取/可计算/生效/声明总分及对应未知状态；不能默认100或把缺失权重显示0。
- 单项原文、模型说明、来源位置、诊断、可计算状态分开。完整规则/材料要求可展开。
- 显式版本切换调用activation接口并携带当前指纹；显示差异，409刷新提示，保留ACL。
- 首次可用、不完整重解析旧版保留、MANUAL_APPROVED仍有未采纳项、失败/解析中、多方案与旧接口兼容覆盖。
- 完成复核与已生效不等于完整/已验证；时间和taskId各归所属。

### Task 5: Regression and delivery evidence
- 全部Sidecar、scoreparse Java、前端受影响测试和构建；真实artifact固定候选回放以及跨格式负例差分。
- 独立审查全分支：Spec合规、行为、结构、交付；修复load-bearing findings。
- 工程教训追加现有lessons，Wiki回填；既有pre-push接入根因回归（有界，不依赖私有原件或网络）。
- 更新spec/tasks/quickstart与证据命令、精确pass/fail/skip。真实测试环境三次模型/HTTP/DB/浏览器验证若未获部署执行条件，明确未验收，不冒充完成。

## 2026-09-27 实施状态：最终审查未通过

T001–T004 已完成各自审查，T005 保持未完成。最后全量1312条中1310通过、2历史环境跳过，根因门禁153通过，但独立复审仍发现跨页28/12项规则污染：物理引用不相交、同属一个逻辑行时，完整行扩展发生在peer识别之后，两项均被恢复为含双方规则的整行并标为SOURCE。此项不能用数值合计正确豁免。

剩余修复：先在已证明完整来源单元上识别同父子项，再分隔各项规则；无法证明则保留MODEL_CLAIM及未决诊断。须加入真实1382互不重叠引用的28/12反例，并验证生效明细/评分输入。完整证据见 `.superpowers/sdd/scoreparse-1382-post2767-plan/final-rereview.md` 和 `final-fix-report.md`。

本轮SDD最终修复/复审额度已用尽，已向用户请求解除流程上限后继续。当前不能标为已修复或可合入；保留未提交工作区，未部署/重解析/付费模型调用。上述审查后仅追加本状态记录，业务及测试仍为冻结字节。
