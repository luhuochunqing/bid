# Tasks: 投标文件实际评分恢复 v2

共35项实施任务，当前正在实施；已勾选项仅表示对应本地实现及局部测试已执行，整体验证和原件验收另有任务。生产动作在执行阶段按授权和SOP处理。以下scoreparse相对路径均位于backend/src/main/java/com/xiyu/bid/scoreparse；测试对应backend/src/test/java。NEW表示本轮新增文件；实施中的文件和验证证据见验收记录。

- [ ] T001 固定候选/部署/原件SHA及当前规则，建立脱敏验收表：`docs/testing/actual-bid-scoring-acceptance.md（NEW）`。
- [ ] T002 测1123/580/1117下载/上传次数、唯一媒体、OCR/补图/模型时间、实际传输尝试及进程树RSS，冻结原件预算：同验收表。
- [ ] T003 预标注客观项规则、证据、分值；区分局部可确定、累计/缺失需全卷、未知：同验收表。
- [x] T004 [US1] 41页尾部证书RED→GREEN，覆盖计数和原件引用正确：`document-converter-sidecar/tests/test_scanned_bid_ocr.py`。
- [x] T005 [P] [US1] 41图Word尾部小图/重复引用/透明图/超大像素/旧字符串输出回归：`tests/test_docx_streaming_sax.py、test_scanned_bid_ocr.py`（均在Sidecar目录）。
- [x] T006 [US1] 扩展既有SAX收集关系及锚点；评分OCR完整枚举、单请求去重，解码前字节/像素护栏：`document-converter-sidecar/app.py、converter.py、scoring_extraction.py（NEW）`；1123测量后补受限EMF文本和发布4路/24M像素配置，复用`image_extractor.py`检查模式，不重写正文解析。
- [x] T007 [US1] 评分opt-in结构化报告及准确计数，普通接口兼容：`document-converter-sidecar/app.py、tests/test_app.py、tests/test_convert_stream.py`。
- [x] T008 [US1] 在既有统一执行入口实现共享deadline/容量/回收，忙时尽早拒绝、硬终止无误杀：`document-converter-sidecar/app.py、scoring_extraction.py（NEW）、tests/test_scoring_deadline.py（NEW）`，不新建第二队列/监督服务。
- [x] T009 [US1] 纯数据正文/来源/覆盖对象，不持文件租约：`domain/ScoringDocument.java、ScoringExtractionReport.java（NEW）`。
- [x] T010 [US1] 复用上传/鉴权的流式JSON客户端，传deadline并限字符/字节，旧extract不变：`application/SidecarStreamTextExtractor.java`。
- [x] T011 [US1] 一次下载先hash再提取；延长现有临时文件作用域至评分finally，取消无条件全卷抽图，按来源补图且共享预算：`application/BidFileLoadingCoordinator.java、SidecarImageExtractor.java、ScoreScoringAppService.java`；回归早退/失败释放及下载次数。
- [x] T012 [US1] 持久化≤60KiB摘要，必要字段超限用≤4KiB失败报告并replacement=null；500引用/中文/超限兜底真实读回：`application/ScoringRunReportCodec.java（NEW）、ScoreScoringVersionService.java`及NEW `ScoringRunReportCodecTest`、现有`ScoreScoringVersionMysqlTest`。
- [x] T013 [US2] URL/内容/章节三层MANUAL跳过与ALL/ITEMS/UNSATISFIED范围RED测试：`ScoreScoringAppServiceTest、ScoreScoringItemPickerTest`。
- [x] T014 [P] [US2] 纯策略分离preserveEligible与autoSkipEligible，FAILED成功项可保留，AUTO整体跳过要求所有目标项有效且来源COMPLETE：`domain/ScoreResultReusePolicy.java（NEW）`及NEW `ScoreResultReusePolicyTest`。
- [x] T015 [US2] MANUAL绕过三层SKIPPED，创建时保存请求，范围不扩大：`application/ScoreScoringAppService.java、ScoreScoringItemPicker.java`。
- [x] T016 [US2] 取消URL-only缓存，在同一已下载文件上hash后判AUTO；满足则不再OCR，不先contentHashOnly再load：`application/BidFileLoadingCoordinator.java、ScoreScoringAppService.java、ScoreScoringVersionService.java`。
- [x] T017 [US2] 同原件原子替换保留未选成功项和直接来源；跨原件/来源未知局部请求SCORING_REQUIRES_ALL拒绝、replacement=null，已确认旧来源读侧待重评/不计当前总分，延期历史展示：`application/ScoreScoringVersionService.java、ScoreItemAssessor.java、ScoreScoringAppService.java`。
- [x] T018 [US2] 真实MySQL验证A成功/B失败→只重试B不丢A、换原件局部拒绝不改存储但旧来源不计当前总分、迟到不覆盖：`ScoreScoringVersionMysqlTest`。
- [x] T019 [US1] 尾部/长段/多合同进入摘录及真实引用；失败才定向改摘录，按位置补图：`application/ScoreDocExcerptExtractor.java、ScoreItemAssessor.java`及对应Test。
- [x] T020 [US1] PARTIAL按项服务端可核验证据放行；计数/缺失/否定未核验待确认、模型自报充分无效；主观数字丢弃：`domain/ScoreAssessmentGuard.java、application/ScoreItemAssessor.java`及对应Test，不增加自然语言规则引擎。
- [x] T021 [US1] 仅反例证明需要时改提示；评分模型配置复用既有request config和transport，SDK重试0、含降级至多2次传输：`infrastructure/openai/ScoreParsePrompts.java、OpenAiScoreAnalyzer.java`及`biddraftagent/infrastructure/openai/OpenAiBidAgentRequestConfig.java、OpenAiSdkStructuredOutputTransport.java`，旧用途兼容回归。
- [x] T022 [US1/US3] 每次传输timeout受剩余预算限制；技术失败保留成功项，正常全待确认COMPLETED+NEEDS_REVIEW，明确评分业务拒绝不计共享熔断：`application/ScoreScoringAppService.java、ScoreParseAutoPolicy.java`及`ScoreScoringAppServiceTest、AutoFailCircuitTest`，不改PARSE技术策略。
- [x] T023 [US3] 扩展现有纯SummaryAggregator，新增qualityStatus及互斥守恒计数；Assembler只映射当前行，不递归读取历史：`domain/SummaryAggregator.java、application/ScoreScoringResultsAssembler.java、dto/ScoreScoringResultsDTO.java`。
- [x] T024 [P] [US3] 摘要0/部分分、历史未知、FAILED已算项、全待确认及旧字段兼容：`SummaryAggregatorTest、ScoreScoringResultsAssemblerTest（NEW）`。
- [x] T025 [US3] 页面显示真实质量、规则未评分、换原件须ALL；无历史新页面、无自动付费扩大：`src/composables/projectDetail/useScoreParseDrawer.js、src/views/Project/stages/components/ScoreParseDrawer.vue、ScoreParseTable.vue`。
- [x] T026 [US3] 轮询绑定taskId，关闭停止本地观察，重开GET不POST，网络中断不推断后端失败：`src/composables/projectDetail/scoreParseTask.js、useScoreParseDrawer.js`。
- [x] T027 [US3] UI回归全待确认无绿色成功、FAILED成功项可见、局部拒绝旧值不丢；后端验证两次正常待确认不熔断自动解析：上述对应spec.js及`ScoreScoringAppServiceTest`。
- [x] T028 [US3] 从createdAt起1800秒含排队，总deadline覆盖下载/hash/OCR/按需补图/实际模型尝试/落库；PENDING等待及重启按现有策略，PARSE不改：既有下载边界、`application/SidecarStreamTextExtractor.java、SidecarImageExtractor.java、ScoreScoringAppService.java`、`infrastructure/scheduler/ScoreParseTimeoutScanJob.java、infrastructure/bootstrap/ScoreParseTaskRecoveryRunner.java`。
- [x] T029 [US3] 真实进程/事务验证到期不启动、运行超时无残留、下一请求可用、普通请求无误杀、临时文件释放、旧线程不写回：`document-converter-sidecar/tests/test_scoring_deadline.py（NEW）、ScoreParseTimeoutScanJobTest、ScoreScoringVersionMysqlTest`。
- [ ] T030 [US4] 测试环境1123完整链：COMPLETE、原件/规则预期、客观数字/引用、DB/API/UI一致：验收表。
- [ ] T031 [US4] 580/1117同样验收；验证1800秒目标、RSS、请求次数及普通转换等待，超预算阻断并记录瓶颈：验收表。
- [ ] T032 [US4] 冻结候选跑三技能审查、真实MySQL、架构、构建与受影响回归，skip不算通过：`scripts/review-workflow.py（调用非修改）`。
- [ ] T033 [US4] 配套发布版本、配置、备份、回滚与授权材料：`docs/release/（实施时新增部署报告）`。
- [ ] T034 [US4] 获执行授权后生产1123单项目重评，核验后逐步580/1117及明确清单：验收表。
- [x] T035 知识与wiki收口，区分代码/部署/恢复/验收，保持所有未验证项明确：`docs/lessons/lessons-learned.md、.wiki/pages/ai-actual-scoring-pr3018-review-20261113.md`。

## Dependencies
T001–003先行。1123身份/性能已采集，金标需业务核对，580/1117仍待验收；这些未完成部分阻断US4验收，不阻断已明确因果反例的本地修复。T004–012建立输入、资源作用域与报告；T013–018实现重评；T019–022实现评估/预算；T023–029实现显示与生命周期；T030–035验收交付。T008/T010/T011/T021共同冻结预算及文件寿命，T012/T014/T017共同冻结结果身份。标[P]项可在契约固定、文件隔离后并行；共享应用服务单一写入者集成。

## Traceability
| 要求/发现 | 任务 |
| --- | --- |
| FR-001 / R06 | T001,T009–012 |
| FR-002 / R04,R08,R09,R10 | T002,T004–008,T011,T028–029 |
| FR-003 / R01 | T003,T019–020,T030 |
| FR-004 / R07 | T013,T015,T017–018,T025 |
| FR-005 / R02 | T014,T016–018 |
| FR-006 / R03 | T020,T022–027 |
| FR-007 | T025–027 |
| FR-008 / R05 | T012,T018,T021–022,T028–029 |
| FR-009 | T006,T008,T011,T014,T021,T023 |
| FR-010 | T030–035 |
| SC-001 | T003–007,T019–020 |
| SC-002 | T013–018 |
| SC-003 | T012,T022–027 |
| SC-004 | T003,T030–031 |
| SC-005 | T001,T030–031,T033–034 |
| SC-006 | T002,T008,T011,T021,T028–029,T031 |

## Independent acceptance
US1证据可回查且正常原件正确出分；US2局部真重评/成功项不丢/换文件不混分；US3质量与执行分开、不误熔断、不重复POST；US4指定原件完整恢复。PARTIAL下局部项可用不代替COMPLETE金标验收，全项待确认不算恢复。

## 本地实现证据与剩余任务

- T004–T029：本地实现与分组测试已执行，最终统一审查/回归见T032；勾选不表示已部署或已证明真实模型准确率。
- Java：29类211项（含真实MySQL33项）通过；其后摘录预算反例先RED再GREEN，5类63项通过，最终同范围复验仍需落证。
- UI：34文件520项、lint（0错误/231既有警告）及build通过；独立复审通过。
- Sidecar：EMF受限子集后160项通过；4个原件阻断单元在24M配置下4/4 COMPLETE，最终完整原件复验进行中。
- T001–003保留未勾选：1123已有SHA和规则身份，但金标仅预标注1项且待确认，580/1117尚未获取。
- T030–031、T033–034属于未执行的测试环境/发布/生产恢复步骤；working_tree授权不包含这些外部写入。T035已本地沉淀，T032统一门禁的最终状态以.omc/087-progress.md及verify-087-final.md/review-087-final.md为准；未生成即未完成。

### 原件反例驱动的T020补充

完整1123为COMPLETE+122 empty；证书例外不足以覆盖明确额度项，T020曾重开，现已完成本地修正及112项直接影响回归。仅对完整闭合单指标四档额度规则实现最高档正向证明（NEW domain/CreditLimitScoreProof.java，复用ScoreItemConstraintExtractor金额归一，ScoringDocument同一原生表行来源绑定，ScoreItemAssessor接入；对应NEW main-chain测试）。由服务端复算并核对模型原始/最终分值，未知/低档/缺失/否定/附加材料条件保守待确认；不是新增通用NL规则引擎。证书“证明材料”条件必须在部分/空OCR路径保持待确认，不能由标题替代验证。源码已冻结，独立Astra五文件复审PASS；最终整组门禁证据见本地verify/review。真正COMPLETE且empty0的原有合法低档/0分不被该例外阻断。

### 额度修复的真实形状工程证明

同SHA原件重新执行native SAX（无OCR重跑），结合既有完整提取正文/覆盖报告构造工程输入；真实当前652规则、COMPLETE/122empty、连续原生行绑定成立。固定模型9得到服务端9，raw8+ratio100为pending；实际送模参数含原文行且274字符，未跳过quote-in-excerpt守卫。工程回放实际模型传输0、工程回放时业务金标确认pending；用户随后明确确认第652项预期9分，仍不是T030真实模型/UAT通过。

业务确认更新：用户已确认652预期9分；T003仍因其余客观项/代表原件金标未完而保留未勾选，不能把单项确认算整项任务完成。

### PR3021 设计审查修正

- T011：图片仅在当前 ScoringFileSession 内按 base64 字节预算复用，命中页不重复上传渲染；超预算淘汰，会话结束清理。无持久缓存。
- T019：复用现有 Excerpt/Segment 偏移，由 ScoringSourceIndex 一次构建页面及媒体区间，删除每项反向全文定位。
- T020：文本先经完整校验，证据不足且有预算才补图，补图后重复同一校验；共享两次实际传输上限不变。
- T009：ScoringSourceRef 统一已知来源字段、marker和来源身份；旧wire来源列表在读取边界转换，协议兼容。
- T015/T022：版本服务改为必需构造依赖，删除旧评分执行及读取分支；测试迁移到实际新主链。
- 主干 Wiki 冲突通过 merge 解决，保留双方条目。T030–T034 的未完成状态保持，代码回归不替代真实 MySQL、模型及现场验收。
