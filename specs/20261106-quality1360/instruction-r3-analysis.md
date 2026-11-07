# R3一致性与因果证据

用户批准R2交接新周期；旧原周期/R2三轮失败历史不覆盖。HEAD adbb2facf，working_tree。

| 契约 | 路径 | 当前证据 |
| --- | --- | --- |
| AC01单动作多对象 | ScoringRequirementRolePolicy对象集合 → ScoringResponsePolicy → Engine | 、/及/和/与/以及/逗号，31项矩阵clean通过 |
| AC02极性作用域 | 协调动作继承previous，显式modality重置 | 否定/正向重置/已填前对象对照 |
| AC03角色局部性 | 对象保留括注；字段角色聚合 | 原21项+括注/多对象对照；额外限定语自查RED执行中 |
| AC04未解析相关性 | AMBIGUOUS，不恢复名称推断 | 条件/或选/未知字段人工，模型双向对照 |
| AC05旧行为与来源 | EvidenceTextSlice等其余实现不改 | 70项关联通过；完整回归待执行 |

## 实际RED及因果

Quality1360InstructionScopeTest原实现31项：5断言失败/14无结论错误，日志r3-red.log；属于业务RED。旧REQUESTED_FIELD把动作后整串当一个对象，或用“并”创造正模态。当前类将动作、对象集合、极性独立保存，不再用名称/材料类别直接替代该契约。

## 构建证据清洗

r3-green.log、r3-integrated.log曾因增量编译缓存仍加载旧ScoringResponsePolicy（javap仍显示requiredRole/REQUESTED_FIELD）；不当作新实现结果。mvn clean test后javap确认仅消费新policy，101项/零失败/零错误/零跳过，r3-clean-green.log。后续源码冻结期间不并发修改编译输入。

## 待完成

Mode B清理、完整相关/三架构、新鲜build、隔离旧实现反证和独立全diff审查。尚未声明本周期关闭。

## 清理后准备验证

额外3项限定语RED已复现，补充对象角色和组限定范围后104项目标+关联全通过（34项R3）。Mode B删除评分旧角色解释块、去除新内部record中后续决策不消费的动作名称副本；词法位置/模态与对象集合足够表达本策略动作义务，不保留无用字符串。整理同包Role导入，不新增依赖或适配层。清理后开始完整相关/三架构命令，新鲜构建r3-build.log已exit0。

## R3独立round1：NEEDS_FIXES

Spec复现换行丢失条件及技术说明材料名被切成动作，Standards复现仅改变评分项顺序即改变角色。旧完整1156项及build通过不能覆盖这三项；报告review-quality1360-r3-{standards,spec}.md独立保留。

新4项词法/条件回归的业务RED见r3-round2-red.log，排序实际JUnit断言隔离运行exit1，r3-order-red/red.log。句界不再采用格式换行；无模态动作必须位于起点或协调边界；评分名称仅作所有明确角色均缺失时的人工提示，空标题线索不与真实义务争夺极性。Mode B删除单项内依赖累计集合状态的分支，聚合顺序无关，未增加新兼容路径。

## Round2冻结与局部验证

109项目标/关联（39项R3）通过，r3-round2-targeted.log，exit0。当前28项源码清单source-inputs-r3-round2.json SHA9f27b8577bf067408883c4e1d35e868bbe48e3f20c781e9011918a2797cf5350。同一原始两项JUnit断言：隔离旧评分策略exit1（多对象无结果、否定误FAIL），当前类exit0；日志r3-counterfactual/reintroduced.log、r3-current-runner/current-round2.log。排序新断言旧版exit1、当前exit0，r3-order-red/{red,green}.log。没有变动共享target或线上数据进行故障注入。

格式/源码预算检查通过。Wiki检查exit1仍为197项既有日期问题，本页无违规；不据此批量刷新历史页面。当前完整相关/三架构r3-full-round2.log及独立双轴round2尚待结论。

## 新鲜完整验证

清理后backend目录mvn test -Dtest=com.xiyu.bid.compliance.**,FPJavaArchitectureTest,ArchitectureTest,MaintainabilityArchitectureTest，exit0，1161项、0失败、0错误、1外部PDF跳过；含39项R3，日志r3-full-round2.log。新输入清单28项SHA保持9f27b857...。npm run build exit0，r3-build.log；前端未作本周期修改。独立Spec已核验上轮反例新结果，最终双轴裁决待落盘。

## R3最终结论：本地契约通过

独立Spec和Standards第二轮均LGTM；Standards给出MERGE_READY且仅限本地working_tree，无待接受Minor/Info。两轴都从全部累计差异（含untracked）检查，当前28项源码清单与验证后完全一致。Spec独立16组真实Engine PASS/FAIL端口矩阵及跨周期反例通过；Standards独立排序/角色反例通过。报告.omc/review-quality1360-r3-spec.md、review-quality1360-r3-standards.md，旧round1和R2全部历史保留。

R3-AC01–05在声明本地范围内满足。最终1161项回归零失败/错误、1冻结PDF跳过，构建通过，原两项相同JUnit断言在旧策略重新失败、当前通过；不把自动化绿作为独立审查替代。工作树未提交；未运行clean-HEAD acceptance凭证门禁，也未执行push/PR/合并/部署/线上1360重算。保留25项全业务准确率、真实模型/图像、物理分页及原件UAT边界。


## review-workflow fix 第1/3轮：ESCALATE（2026-11-06）

本阶段基线200fa5bf80b86b425e488ebb39a6e49aaf6d3260；检查累计diff与未跟踪文件，不覆盖上述历史审查记录。本轮两条真实审查发现已复现：对象括注适用条件被mask吞掉；UNKNOWN技术字段被SCORING_FIELD升级为必填。最小修改保留对象局部条件为AMBIGUOUS，并将技术词表兜底改为人工召回，不改变公共接口或证据来源。

无监听端口保护：原39项R3通过（/tmp/quality1360-protection.log）。新增7项规则/真实Engine双向模型对照首先全部RED（46项中7失败，/tmp/quality1360-fix-red.log）；修复后46项R3全部通过，21项角色/来源全部通过。关联命令 `mvn -q -o -f backend/pom.xml -Dtest=Quality1360InstructionScopeTest,ScoringResponsePolicyTest,Quality1360RegressionTest,Quality1360RoleAndLocationTest test` 共116项、7失败、0错误、0跳过，exit1，日志/tmp/quality1360-fix-green.log。

阻断是既有断言与本轮R3要求冲突：Quality1360RegressionTest第190–198行要求仅绑定响应时间时，未绑定技术方案留白仍FAIL；另外两项该类用例及四项ScoringResponsePolicyTest用例在TenderRequirements.empty()时同样要求技术词表硬FAIL。当前均为MANUAL_REVIEW。未删除或改写这些断言、未弱化门禁；依据用户“需求冲突返回ESCALATE”停止继续修改，交主执行器裁定旧测试契约。不能声明本轮全绿或交付就绪。

未提交、push、PR、部署、启动服务或写自批凭证；未嵌套review-workflow及收尾。监听测试仍由主执行器verify负责。

## PR阶段最终BLOCKED（当前）

仓库强制review-workflow首轮清理PASS，最新main上的完整compliance/三架构1161项零失败/错误、1外部PDF跳过，25项组件与build均exit0。随后Google独立审查发现对象括注中的适用条件被mask丢失，以及UNKNOWN技术词表直接升级必填。fix进程先新增7项RED再写最小修复；46项R3及21项角色/来源通过，但116项关联仍有7项旧断言从预期FAIL变MANUAL_REVIEW。Google之后尚未执行西域审查，当前修复版没有完整回归全绿结论。

fix按需求冲突返回ESCALATE；review-workflow整体exit1，state=BLOCKED。依据AGENTS.md第88行“ESCALATE…立即停止…不创建PR”，停止业务/测试修改。没有commit/push/PR/合并/部署。旧R3本地LGTM和1161项全绿属于修复前历史，不能用于放行当前工作树。

原两个明确反例已补测试，但未知技术字段的新边界与旧7个FAIL断言需要裁定。建议明确两类：本次已确认必填且原位留白继续FAIL，相关定位/占位测试补齐真实招标要求与字段绑定；参考模板、未绑定义务及适用性不明转人工。不能把7个FAIL盲目全部改为人工，也不能仅为通过旧测试恢复词表硬拒绝。此方案尚未实施，需人工裁定后重新完整运行门禁。

失败用例清单pr-blocked-tests.json、独立报告pr-blocked-google-code-review-report.json、修复报告pr-blocked-fix-report.json及pr-blocked-state.json均保留仓库外。当前28项源码/测试清单source-inputs-pr-blocked.json SHA ebda87c9b9fdb267a618576fce4f4bda223c40a8c0b91293adad5444139f5e4e。未提交差异、已合并双方知识库内容及同步前stash均保留；未创建PR，用户的PR交付目标尚未完成。

## 用户裁定后测试前置条件对齐

用户确认后保留原7条FAIL断言，补本次明确要求及字段身份，不把未知留白当明确缺陷。ScoringResponsePolicyTest四用例增加同文档无要求MANUAL对照；定位/省略号两用例保留原位证据或正文识别检查，并补强制项目经理拟派要求；跨行参数场景各有实际评分义务。生产角色与评分策略未回退，新增7条审查反例保留。目标验证及强制提交门禁将重新运行；旧7失败记录与BLOCKED凭证仍属历史，未修改以伪装通过。

## 同契约集成用例范围补齐

裁定后第1轮完整回归1168项仅1失败：ScoringAndClarificationIntegrationTest以“满分要求”替代具体字段义务，却断言总体实施规划留白FAIL。自动fix因为该文件未在原范围返回ESCALATE并未修改文件，原流程已停止且无发布。主执行器按用户已确认的同一契约把该文件纳入范围：原文与结构化要求同时声明“必须填写总体实施规划”，保留评分/澄清FAIL全部断言，并加无绑定MANUAL对照。不是新的业务语义决定，不需再次确认已授权边界。

保留scope ESCALATE历史与全部失败证据；后续累计门禁总上限仍3轮，当前已用1轮，不能靠范围调整无限重置。当前定向验证及完整流程将重新运行，通过后才提交/push/PR，不合并或部署。

### 当前调度 fix 第1/3轮（基线200fa5bf，2026-11-06）

仅修复Google审查三项阻断：模态前否定不能由内部“必须”建立正义务，无法确定的双重否定仍人工；资格占位按句号/分号/逗号及换行隔离当前字段；系统报价指令内部出现否定时不建立正向渠道。明确必填、真实资格留白和正向系统填报对照保留。

无端口保护：两类原有88项通过（/tmp/q1360-protect.log）；新增10项后98项中9失败、0错误，双重否定原已人工（/tmp/q1360-red.log）。修复后四类126项全部通过、0失败/错误/跳过（/tmp/q1360-green.log）。评分反例通过真实Engine及模型PASS/FAIL双向对照；资格验证句界与真实留白，报价验证确定性提取和出口不改写原FAIL。当前阶段不是独立审查放行；主执行器继续verify/独立复审，监听测试由verify执行。未提交/push/PR/部署，无自批凭证。

本轮三架构测试41项通过（0失败/错误/跳过，/tmp/q1360-architecture.log，exit0）；基线完整diff及当前未暂存diff格式检查均通过。

## 当前：最终候选仍BLOCKED，三轮上限已停止

用户裁定后的累计过程：同契约集成用例缺失前置条件/范围阻断为第1轮；扩展范围后的Google发现三个源代码Major为第2轮，修复后126项行为/41项架构通过；当前清理与1178项完整验证后，独立Spec在第3轮确认新报价回归。旧失败及修复记录全部保留，不再自动进入第4轮。

**当前唯一已确认的新阻断**：PricingSubmissionPolicy扫描整条匹配指令的任意“不”，将报价对象“不含税折扣率”错误解释为禁止报价。独立编译及真实Engine对照：“在系统报价表中填报折扣率”→MANUAL；“在系统报价表中填报不含税折扣率”→hasSystem=false、最终FAIL“未检测到开标一览表”；真正“不得填报”仍被排除。证据approved-pr-final-spec-probe.log及独立报告review-quality1360-approved-pr-final-spec.md。原AC05禁止把明确系统折扣率因缺Word传统表判FAIL，故该结果不能放行。

当前29项源码清单source-inputs-approved-pr-final.json，SHA f9ec5d6241f15751ee66488289e23b2bb035d6d1c6e8bd7fe0991698adbe84cc。1178项/0失败/0错误/1外部PDF跳过、25项组件与build均通过，但未覆盖这个新反例。主执行器收到独立Major后按累计轮次上限发送SIGINT停止当前repo工作流，机器状态BLOCKED记录“进程超时/中断”；实际为有意中断，不是环境超时。CLI Google最后一轮未完成，西域审查尚未执行，不能声称双审查通过或MERGE_READY。

下一周期应只明确报价指令的“动作极性—报价对象（含/不含税）”边界，先将此正向/禁止对照转为真实Engine RED；否定仅作用于报价动作，不能按整句出现“不”裁决。保留不含税/含税、不同折扣率、明确不得/无需报价、独立纸面义务及原位证据对照，不顺手重写其他25项。该方案未实施，当前已停止业务/测试编辑，需人工决定后续。

截至当前没有commit、push、PR、合并、部署或线上重算。用户的提交PR目标尚未完成，所有工作树差异及同步前stash保留。

## 本次调度fix第1/3轮验证（基线200fa5bf）

- 既有无监听保护集：PricingSubmissionPolicyTest、Quality1360InstructionScopeTest，80项通过，命令`mvn -q -f backend/pom.xml -Dtest=PricingSubmissionPolicyTest,Quality1360InstructionScopeTest test`，日志`/tmp/quality1360-fix-protection.log`，exit0。
- 新反例RED：89项中9失败、0错误，覆盖渠道后转折正义务、转向纸面目的位置、否定要求谓词；`/tmp/quality1360-fix-red.log`，exit1。补充的渠道前禁止转折用例独立断言原位摘录，摘录从渠道起点开始，不包含前置“不得”。
- 最终GREEN：`mvn -q -f backend/pom.xml -Dtest=PricingSubmissionPolicyTest,Quality1360InstructionScopeTest,Quality1360RegressionTest,Quality1360RoleAndLocationTest,ScoringResponsePolicyTest,ScoringAndClarificationIntegrationTest,BidDocumentQualityEngineTest,DeterministicPriceExtractorTest,QualityEvidenceBoundaryTest,ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest test`，256项、0失败/错误/跳过，`/tmp/quality1360-fix-regression.log`，exit0。
- 纯核心两处修复，无新增依赖、外部IO、接口或数据库改动。保持税价对象、明确必填FAIL、独立纸面义务、页80原位证据及模型PASS/FAIL双向对照。
- `git diff --check`通过。只修改白名单业务/测试/文档，未提交、push、PR、部署、启动服务、写自批凭证或嵌套review-workflow。完整verify和独立复审仍交主执行器，本阶段通过不代表PR可合并或线上验收。
