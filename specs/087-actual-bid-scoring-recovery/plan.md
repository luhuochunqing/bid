# 投标文件实际评分恢复 Implementation Plan v2

> **For agentic workers:** 本轮采用 `unified-dev-flow`、controller:none，由根代理协调文件隔离的工作单元；使用现有独立worktree及三技能审查门禁。2026-11-13 v2正在本地实施，交付上限working_tree；未提交、部署或生产重算。

**Goal:** 让1123等项目的投标文件实际评分能读取有效证据、真实重算、正确落库，并在页面如实呈现。

**Architecture:** 复用现有评分异步任务、流式文件装载、Sidecar/Tesseract、逐项评估和版本化落库。扩展评分专用结构化提取报告，取消12/24页静默成功；评分资格按项判断，人工重评绕过三层旧结果复用。使用已有 `run_summary` 保存有字节上限的摘要，复用SAX、模型请求配置及统一转换入口。延期跨原件历史评分展示，不增加表、队列或OCR平台。

**Tech Stack:** Vue 3、Java 21/Spring Boot 3.2、MySQL 8.0/JPA、Python Sidecar/PyMuPDF/Tesseract；pytest、JUnit、真实MySQL事务测试、Vitest、Playwright真实接口验收。

**Revision:** R01–R10均已纳入，落实位置见第9节；实际实施状态以tasks.md及验收记录为准。

**Baseline:** `59ae5ef8f417dab7a5b146e45a866e41921debf6`。需求见[spec.md](spec.md)，合同见[contracts/scoring-recovery.md](contracts/scoring-recovery.md)，执行清单见[tasks.md](tasks.md)。

## Global Constraints

- 执行前已刷新主线、部署及1123当前规则；当前证据见验收记录，不能用旧快照覆盖新状态。
- 保留主观项不给数字、知识库预估与实际分离、最新任务/规则版本守卫、已有权限和文件配额。
- 不调整招标文件评分表抽取、报价公式、知识库预估算法；解析规则准确性仅作为评分前提核对。
- 不以HTTP 200、COMPLETED、测试全绿或全项PENDING替代真实出分验收。
- 客户原件、OCR正文、模型输入及数据库备份留在Git外受控目录；测试仓库只放合成样本。
- 既有持久worktree保留；代码在任务分支实现；临时worktree不启动开发服务。
- 不全库自动重算，不直接SQL修改分数；上线与历史恢复分别记录。
- 同原件保留局部重评；原件变化的ITEMS/UNSATISFIED明确拒绝并提示ALL，不自动扩量。首轮不建设跨原件历史分数展示。
- 正常执行但无可确认分数属于质量NEEDS_REVIEW，不等于技术FAILED；完整恢复验收仍要求代表原件真实正确出分。

## 1. 已确认事实与尚未验证事项

| 项目 | 已确认事实 | 对计划的约束 |
| --- | --- | --- |
| 生产版本 | 2026-11-13只读读取为6d843c212，运行jar与release一致 | !3016/3017/3018尚未在该快照生效 |
| 1123 | 586MB DOCX，附件375；当前规则641..652共12项、parseTask230；无关联score_result | 当前空分首先是新规则未执行实际评分，不能错误归因于复用跳过 |
| 旧任务 | 1123最后SCORING为11月5日task193，之后6次PARSE | 旧“完成”不证明新规则有结果 |
| 普遍性 | 现存28次历史评分24次FAILED，涉及16项目 | 是历史记录分布，不能当当前版本失败率 |
| OCR回归 | 53项通过；41页真实OCR反例只读前24页，独立第41页能识别证书 | 正常回归不足，必须新增反例 |
| 复用 | 地址、文件hash、章节hash三层；客观空分可能FRESH且任务完成 | 三层都要修，不只改按钮或第一处if |
| 未知 | 原件已测614173466字节、1739个被引用媒体；全卷提取测量见验收记录。模型证据召回与逐项准确率仍待验收 | Task 1先测，不能预先宣布可在30分钟内完成 |

原件身份→提取范围→评分项相关证据→模型/规则计算→版本化结果→API/UI，是本次完整验证链。

## 2. 方案选择

| 方案 | 收益 | 代价/结论 |
| --- | --- | --- |
| 直接部署现有PR、延长等待 | 可能越过部分空正文/超时阻断 | 漏证据、空分复用仍存在，不作为最终方案 |
| **完整覆盖目标＋按项证据判定＋有界资源（推荐）** | 修复已确认原因，复用现有组件，可逐项核验 | 复用现有组件，保留未知范围，按项保留成功结果，本文采用 |
| 新建异步OCR缓存/队列/向量检索平台 | 可能适合长期超大规模 | 当前没有证据证明必要；不纳入本轮 |

完整提取是正常原件恢复目标，不作为“任何局部异常都禁止所有项出分”的全局门禁。全文不一次性发给模型，复用评分项相关摘录；PARTIAL保持明确标识，不冒充完整。不能由模型自行宣告证据充分后放行；逐项规则见3.1。

## 3. 最小数据和接口设计

### 3.1 评分专用提取

扩展已有 `POST /convert` 的可选 `X-Pdf-Ocr:true`，缺省路径不变；新增 `X-Scoring-Report-Version:1` 标识客户端要求结构化覆盖报告。保留普通 `/convert-stream` 纯文本契约。Java新增 `extractScoringDocument(Path|byte[],fileName,deadlineEpochMillis)`，评分调用切换；非评分 `extract()` 不动。

返回既有markdown/pages并增加 `extraction`：`schemaVersion`、`engineVersion`、`coverageStatus`、`sourceUnitCount`、`processedUnitCount`、`uniqueOcrUnitCount`、`unreadUnitCount`、`failedUnitCount`、`emptyUnitCount`、`unreadUnits`、`failedUnits`、`emptyUnits`、`elapsedMillis`。物理PDF用页码；Word用段落/表格位置、关系ID和媒体路径，禁止伪造物理页码。

- `COMPLETE`：应处理单元均已执行，未读/技术失败为0；不代表OCR内容100%正确。
- `PARTIAL`：预算终止、部分媒体不支持或处理失败，有明确范围。
- `UNKNOWN`：旧Sidecar缺少报告或报告无法验证，不能默认为COMPLETE。
- 纯白像素可明确记为处理完成；非空白图片OCR成功但文字为空列入emptyUnits警告，不能声称该图片不存在证据。正文/模型证据仍需逐项验证。
- PARTIAL按项处理：服务端能够核验完整评分规则、来源引用及充分条件，并确认未读部分不可能改变结论时，才允许该项数字生效。现有规则/校验不能证明此条件，则NEEDS_REVIEW；不新增通用自然语言规则引擎，也不信任模型自报“充分”。
- 依赖全卷累计数量、未提供判断、排除/扣分条件的项，在相关范围未覆盖时待确认；仅找到一份合同不能确定累计业绩分。可证明已达封顶且不存在未核验否定条件时，才属于局部充分证据。
- UNKNOWN或报告损坏不产生新的可信数字；配置/协议错误终结失败并保留旧结果。COMPLETE仅代表处理完成，空OCR/证据矛盾仍按项待确认。
- 覆盖质量与执行故障分别记录：技术超时/损坏可使任务FAILED，但已验证成功项保留；正常执行而依据不足为COMPLETED+NEEDS_REVIEW。正常原件恢复验收仍需COMPLETE及预标注客观项正确出分。
- 旧版DOC继续保留可用原生提取；Linux扫描DOC缺转换能力应说明格式限制，不能假称已覆盖。

### 3.2 保存请求与报告

保持 `pipeline_version=SCORING_VERSION_V1` 和现有规则指纹协议，不简单改成V2。现有代码把非V1视为历史兼容，贸然修改会改变旧结果读取行为。

在 `score_parse_task.run_summary.scoring` 保存：请求来源/scope/itemIds、`engineVersion=actual-score-v2`、实际文件hash、规则fingerprint、覆盖计数、逐项outcome/直接来源和输出数量。PARSE任务原有命名空间不变。完整JSON序列化后UTF-8上限为60KiB（61,440字节），小于TEXT的65,535字节。

仅可裁剪可选说明和位置样例（首轮最多20个，单说明最多256字符），必须保留总计数、PARTIAL/UNKNOWN、samplesTruncated、请求/身份/每项outcome及来源。详细清单留现有受控验收工件，不写正文到数据库，不新建报告服务。若必要字段本身超限，不发布本批结果：用不含超限正文、至多4KiB的小型REPORT_TOO_LARGE失败报告调用finish(replacement=null)，保留原score_result；不得把超限detached.runSummary继续传给兜底事务。请求必要字段超限则创建任务前拒绝。

`ScoreScoringVersionService.finish()` 必须复制并保存runSummary；当前只改detached对象不会自动持久化该字段。请求上下文创建时写入，异步阶段从报告读取，不能靠可变stage文案或chapterHashes中的IDS临时串恢复请求。

### 3.3 重评与复用

不新增force开关：现有 `source=MANUAL` 就表示用户明确要求重评。`AUTO`仍可优化。

```java
boolean reuseAllowed = "AUTO".equals(request.source());
// 先按 scope/itemIds 选中，再决定能否复用；MANUAL不能走任何SKIPPED分支。
List<ScoreItem> selected = selectScope(currentItems, request.scope(), request.itemIds());
if (!reuseAllowed) return new Plan(selected, "FULL", "按选择范围重新评分", List.of());
```

上述是拟实现语义，`selectScope`复用现有picker.applyScope，并非另造筛选器。MANUAL/ITEMS和UNSATISFIED都不得被扩成ALL。区分preserveEligible与autoSkipEligible：同原件/规则/引擎下，FAILED任务中逐项验证成功的结果可保留；整任务AUTO跳过必须所有目标项有效且来源覆盖COMPLETE，不能因一项成功把FAILED任务整体当缓存。URL相同本身不证明文件未变；取消仅凭URL的即时SKIPPED，在一次下载得到的同一LoadedBidFile上流式hash后判断，符合AUTO跳过条件时不再做OCR；不先调contentHashOnly再load形成二次下载。除非未来另有已证明不可变的对象版本。

客观空分/越界/技术失败不复用；主观项无数字属于合法产品语义，可复用其有效分析。合法0分和3/5部分分可复用，不按PENDING一刀切。

### 3.4 页面摘要

沿用已有okCount/dangerCount/pendingCount（满足程度），新增独立质量字段：`qualityStatus`（COMPLETE/NEEDS_REVIEW/PARTIAL/UNKNOWN）、`objectiveScored`、`objectivePending`、`subjectivePending`、`failed`、`diagnosticsKnown`，以及报告的规则身份和结果来源任务。

四类互斥并覆盖当前全部项：技术失败优先计failed；其余客观合法数字计objectiveScored、无数字计objectivePending；其余主观计subjectivePending。旧记录诊断缺失时diagnosticsKnown=false，failed不得被文案当成“确定零失败”。旧字段缺省不是0/COMPLETE。

部分重评继续遵守全项原子替换：每个新score_result.scoring_task_id属于新任务，同原件未选成功项复制保留，不以其所属任务FAILED为由清空。run_summary仅携带直接originTaskId和已核验的输入/引擎/覆盖身份，不递归遍历历史链，不复制历史分数正文。原件hash变化或旧来源hash未知时，ITEMS/UNSATISFIED返回SCORING_REQUIRES_ALL，保持旧结果未变并提示用户选择ALL；不得自动扩大范围。拒绝任务保存本次已验证的sourceDocumentId和fileHash；读侧复用既有结果投影，将当前附件已确认变化的旧来源分标为待重评/来源过期，响应actualScore为空且不计当前总分，数据库旧score_result不删除。不得把旧值继续当当前原件有效分数；本轮不新增历史页面。

### 3.5 单项结果与任务终态

| 场景 | 任务状态 | 质量/显示 | 后续保留 |
| --- | --- | --- | --- |
| 正常执行，客观项均有可信数字 | COMPLETED | COMPLETE（仍需来源完整）；主观项正常待评 | 有效项可保留 |
| 正常执行，部分或全部客观项依据不足 | COMPLETED | NEEDS_REVIEW，无绿色出分成功，不通过恢复验收 | 仅可信成功项可保留 |
| 单项技术失败，其他项已成功 | FAILED | PARTIAL，显示已算项及失败项 | 成功项按身份保留，失败项不可复用 |
| 提取/协议系统错误，无可信新结果 | FAILED | 原因明确，保留既有结果来源 | replacement=null |
| 报告必要字段超60KiB | FAILED | REPORT_TOO_LARGE，小型失败报告 | replacement=null |
| 原件变化的局部重评 | FAILED / 请求拒绝 | SCORING_REQUIRES_ALL，不自动ALL | replacement=null |

SCORING_REQUIRES_ALL若创建任务后才确认hash，则在原任务明确终结；它是业务拒绝，不计技术熔断。正常全待确认用NO_VALID_OBJECTIVE_SCORE作为质量原因码，不作为FAILED错误码。保留共享AutoFailCircuit，ScoreParseAutoPolicy只排除明确SCORING业务拒绝，不改变PARSE技术失败规则；全待确认的COMPLETED不会进入失败计数。不得把历史未知失败一律排除。

## 4. 分任务实施

### Task 1 — 固定原件、预期和性能基线（最先执行）

**文件**：新建 `docs/testing/actual-bid-scoring-acceptance.md`（脱敏清单）；扩展 `document-converter-sidecar/tests/test_scanned_bid_ocr.py`；真实工件放Git外。

- [ ] 记录1123附件375实际SHA-256/字节数、当前规则fingerprint/规则项数；同样采集580、1117。
- [ ] 只读统计Word正文长度、有效图片引用数、唯一图片数/格式/尺寸；PDF页数及有无原生文字。记录下载、原生提取、OCR、抽图、摘录、模型、落库各阶段时间、实际HTTP尝试数和进程树峰值RSS；核查重复下载/上传。固定单媒体/像素/总RSS预算后再处理原件。
- [ ] 人工从原件预先建立“评分项→规则→证据位置→预期分数/待评理由”清单；开发者输出不能反过来定义预期。
- [ ] 把已有41页反例变成失败回归，再增加41图Word尾部小证书、重复图及空白图对照。测试可用合成真实OCR；故障注入仅限测试，不能替代真实链路验收。

```python
def test_tail_certificate_survives_full_scoring_extraction(tmp_path):
    # 合成41个同尺寸扫描页，只有第41页包含CERTIFICATE ABC999。
    path = make_41_page_bid(tmp_path)  # 本任务在测试文件内实现该夹具
    report = converter.convert_scoring_pdf_document(str(path))
    assert "CERTIFICATE ABC999" in report["markdown"]
    assert report["extraction"]["coverageStatus"] == "COMPLETE"
    assert report["extraction"]["unreadUnits"] == []
```

当前版本应在证书断言失败；不得把期望改回24页。以原件测量决定优化优先级，不在这一步盲目更换模型或上调所有超时。

### Task 2 — 让正文提取全覆盖且保持有界资源

**修改**：`document-converter-sidecar/converter.py`、`app.py`。**NEW**：`document-converter-sidecar/conversion_execution.py`（现有入口的进程监督实现）、`document-converter-sidecar/scoring_extraction.py`（仅移动评分OCR代码与新覆盖报告，不改一般PDF表格解析）。**测试**：`tests/test_scanned_bid_ocr.py`、`test_docx_streaming_sax.py`、`test_app.py`、`test_convert_stream.py`、`test_pdf_memory_guard.py`。

- [ ] 先锁定普通convert/stream、合规抽图不变的契约测试。
- [ ] PDF所有需识别页进入完整清单，逐页光栅化/缩图/释放；不使用12/24候选上限作为成功结束。
- [ ] 扩展app.py现有_extract_docx_streaming_sax的可选引用收集，在既有iterparse中保留blip/imagedata关系ID及段落/表格锚点；保留普通字符串输出。relationships与页眉页脚part复用同一遍历器，不在scoring_extraction.py重写正文拼接。不按ZIP媒体大小判断重要性；未被文档引用的媒体不算漏读。
- [ ] OCR文字接回引用附近并保留来源标记；同一图片内容hash在单次请求内复用OCR结果，保留多个引用位置；哈希计算和媒体读取受内存限制。
- [ ] 所有转换经现有_convert_document一个执行入口，容量、错误映射、deadline和finally清理共用；底层评分硬终止能力按Task7补齐，不能另起一套评分调度系统。并发初始仍1，不盲目放大。
- [ ] 解码前复用ZIP目录/图片头检查：编码媒体沿用64MiB单图上限，解码像素首轮上限16,000,000，可配置且Task1按宿主RSS校准；大于预算显式计失败/未读。流式读取有字节上限，不先archive.read无限分配。支持的编码先降采样解码，再白底合成、2000长边；finally逐单元释放。
- [ ] 小字需高分辨率重试时仍受原媒体/像素/RSS和剩余时间约束，最多一次；不为提高识别率绕过内存护栏。
- [ ] 删除180秒“有文字就交部分当成功”的语义；1500秒是首轮转换硬预算，30秒是单图初始限制。到限返回PARTIAL与未读范围，不伪装全卷完成。
- [ ] 保留引擎缺失/异常稳定错误；单图失败形成可定位记录；临时文件与图片对象finally释放。
- [ ] 完整处理后的性能若不达目标，按实测瓶颈优化原生提取/重复媒体/内存，最多有界并发试验并验证进程树RSS；不直接开启无界线程/嵌套进程池。

**验收**：末尾证据不丢，COMPLETE/PARTIAL/UNKNOWN定义成立；普通调用方保持兼容。全量原件仍超预算则本任务性能验收未通过，不能发布“恢复成功”。

### Task 3 — 一次装载、覆盖透传与有界报告

**修改**：`SidecarStreamTextExtractor.java`、`SidecarImageExtractor.java`、`BidFileLoadingCoordinator.java`、`ScoreScoringAppService.java`、`ScoreScoringVersionService.java`（均在scoreparse/application）。**NEW**：`scoreparse/domain/ScoringDocument.java`、`ScoringExtractionReport.java`、`application/ScoringRunReportCodec.java`。**测试**：`SidecarStreamTextExtractorTest`、`SidecarImageExtractorTest`、`BidFileLoadingCoordinatorTest`、`ScoreScoringVersionMysqlTest`、NEW `ScoringRunReportCodecTest`。

- [ ] 新评分方法复用multipart流式上传、鉴权、连接超时与错误映射，调用带评分opt-in的convert；Jackson流式解析JSON，保留500万字符上限，并设置响应字节上限（初始64MiB）。
- [ ] 内存和落盘两条文件路径都返回ScoringDocument；引用/覆盖报告不在中间对象丢失。普通extract()签名保持。
- [ ] 缺少/错误元数据按UNKNOWN；新Java遇旧Sidecar不能猜成完整。普通请求不要求新字段。
- [ ] PARTIAL连同来源传到Task5按项判定，不整卷一刀切空分；UNKNOWN/无可信正文终结明确错误，replacement=null保留旧结果。发现范围不足不凭空写0分。
- [ ] 将现有LoadedBidFile装载/hash/提取放在一次评分的资源作用域：下载一次，先hash验证复用或拒绝跨原件局部请求，再提取；OnDisk的tempFileStore.release从load内finally延后至本次评分最外层finally，所有成功/失败/跳过路径都释放。不返回已删除Path，不新建缓存。
- [ ] 移除load结尾无条件extractImages。文本充分项使用现有纯文本评估；确需视觉证据时按已定位来源补图，PDF复用renderPages，Word按已有关系引用从当前文件流式取出有界媒体再复用既有图片转换能力。没有可信关联或格式不支持则该项待确认，不退回前3张图。不得修改其他模块的多模态开关。
- [ ] SidecarImageExtractor的按需调用与下载均消耗剩余预算，补图执行经同一受控转换边界，不在async路由直接CPU解码；禁止为了补图重新下载或无条件再上传整卷。
- [ ] run_summary在序列化后检查60KiB UTF-8上限，裁剪仅限可选样例；500引用、中文、必要字段超限分别回归，失败兜底换成至多4KiB报告再finish(replacement=null)。
- [ ] 用现有短事务保存runSummary，读回核验；长HTTP/OCR/模型调用不持有项目锁。
- [ ] 测旧规则、新规则、同项目并发、旧任务迟到，保持现有writable/latest-task/timeout守卫。旧有效结果能否展示沿用版本契约，并明确其来源及最近重评失败，不能伪装成这次结果。

**重要**：不直接引入compliance的DocumentContent或整套适配器；其部分路径剥媒体/限8页，不满足评分全覆盖。

### Task 4 — 人工真重评、自动可信复用

**修改**：`ScoreScoringAppService.java`、`ScoreScoringItemPicker.java`、`ScoreScoringVersionService.java`；复用 `ScoreScoringCommand`。**NEW**：`scoreparse/domain/ScoreResultReusePolicy.java`（纯函数）。**测试**：`ScoreScoringAppServiceTest`、`ScoreScoringItemPickerTest`、`ScoreScoringVersionMysqlTest`、NEW `ScoreResultReusePolicyTest`。

- [ ] 先写同URL、同内容不同URL、同章节三种MANUAL失败用例；再分别写ALL/ITEMS/UNSATISFIED范围用例。
- [ ] 同文件MANUAL绕过三处SKIPPED；scope先选中。真实MySQL验证原子替换且每行归新task，未选成功项保留直接来源。换文件或来源身份未知的局部请求，在任何覆盖前以SCORING_REQUIRES_ALL拒绝，提示ALL但不自动执行；旧分存储不变，读侧标来源过期且不计当前总分。拒绝不存在/跨项目itemIds，权限不变。
- [ ] 创建任务时持久化source/scope/itemIds，异步时从结构化请求读取，不能依赖被改写的stage。
- [ ] AUTO只复用完整且同策略、规则、内容的结果；旧run_summary缺策略时进行一次新评估。引擎版本包含提取/摘录/评分提示与实际模型配置的稳定版本指纹，配置凭据不入指纹或日志。
- [ ] 客观null/越界/模型技术失败排除有效结果；主观无数字、合法0分和部分分按契约处理。
- [ ] 分离preserveEligible与autoSkipEligible：A成功/B技术失败形成FAILED任务后，只重试B必须保留A。AUTO整任务跳过仍要求全部目标项有效且来源COMPLETE。复制必要身份及直接originTaskId，不递归读取旧报告、不新增历史展示；缺字段保守不当有效。

```text
输入：同文件、同规则，A旧结果null/FRESH，B旧结果3/5/PENDING。
MANUAL ITEMS=[A]：A重新调用模型，B不调用且保持合法数值。
AUTO：A不得当成功缓存；B可在同引擎/完整输入等条件满足后复用。
```

### Task 5 — 按项可信评分、单层重试与状态分离

**修改**：`ScoreItemAssessor.java`；`ScoreDocExcerptExtractor.java`只在反例证明遗漏时定向改；模型调用入口为 `scoreparse/infrastructure/openai/OpenAiScoreAnalyzer.java`；如反例证明需调整提示，修改 `scoreparse/infrastructure/openai/ScoreParsePrompts.java::buildObjectiveAssessmentPrompt/buildSubjectiveSuggestionPrompt`。**复用/修改纯核心**：`domain/ScoreAssessmentGuard.java`承接可核验输入条件下的数值放行；不让模型文本成为可信布尔。**调用配置**：既有`biddraftagent/infrastructure/openai/OpenAiBidAgentRequestConfig.java`与`OpenAiStructuredOutputService`，仅评分调用派生带剩余预算的配置。**测试**：`ScoreItemAssessorTest`、`ScoreDocExcerptExtractorTest`、`ScoreAssessmentGuardTest`、既有`OpenAiSdkStructuredOutputTransportTest`。

- [ ] 将“原件→OCR正文→摘录→模型输入”逐段留本地证据；末尾证书、同项多份合同、长段落、目录干扰分别断言。
- [ ] 摘录满预算不能把最强相关证据丢在前置无关内容后；采用现有segments/来源偏移，必要时对超长段按句/表格行分割。不要更换为向量库。
- [ ] 不把给所有项固定前3张图当可靠证据检索；优先完整OCR文本与来源；确需多模态时按所选证据位置关联图片，仍保留预算。
- [ ] 客观评分需要规则、合法数字、可回查依据；模型正常返回空分/越界写明确诊断码，不算有效评分。只因局部摘录未检出不能写“整卷未提供”。
- [ ] 按3.1逐项核验证据是否足以唯一确定分数；PARTIAL不能自动全拒绝，也不能因模型声称充分放行。现有规则校验不能确认、计数范围不足、缺失/否定条件未核验、空OCR或矛盾，均NEEDS_REVIEW。quote必须回指真实来源；无依据不能制造0分。
- [ ] 复用OpenAiBidAgentRequestConfig派生评分配置：timeout=min(原配置,剩余预算)，maxRetries=0；原解析用途不变。外层只重试可恢复故障一次；多模态/格式降级也进入同一个最多2次传输尝试预算，不能隐含SDK重试或降级后重置计数。必要时给现有transport增加评分专用剩余尝试预算参数，不另建AI客户端。
- [ ] 逐项失败保存诊断并继续其他项；鉴权/配置等系统性错误立即终止，保留已执行结果。每次网络尝试前重算剩余时间，deadline到期不再安排请求，不用宽泛catch吞错。
- [ ] 技术失败任务FAILED并保留已验证成功项；正常执行但部分或全部客观依据不足为COMPLETED+NEEDS_REVIEW，质量原因NO_VALID_OBJECTIVE_SCORE不算技术错误，UI无绿色出分成功、业务验收不通过。主观全部待评是正常产品语义。ScoreParseAutoPolicy保留原熔断，只排除SCORING_REQUIRES_ALL等明确业务拒绝；回归证明两次正常全待确认不误伤自动解析。

**验收**：预标注客观样本逐项分数和证据匹配；非满分不等于失败，主观数字继续丢弃。模型准确率未验证时不得宣称修复完成。

### Task 6 — 页面、等待与结果摘要

**修改**：`ScoreScoringResultsDTO.java`、`ScoreScoringResultsAssembler.java`、`ScoreScoringVersionService.java`；`src/composables/projectDetail/useScoreParseDrawer.js`、`scoreParseTask.js`、`src/views/Project/stages/components/ScoreParseDrawer.vue`、`ScoreParseTable.vue`。**复用纯核心**：`domain/SummaryAggregator.java`聚合质量计数，Assembler只做映射。**NEW**：`ScoreScoringResultsAssemblerTest.java`。扩展对应spec.js。

- [ ] 摘要按实际数值和结构化诊断分类；四类互斥且数量守恒；3/5/PENDING计已评分，0分也计已评分。
- [ ] 缺新字段显示旧版/覆盖未知，不报“0失败、完整识别”；新增字段不删旧字段，逐个检查DTO构造点与JSON兼容。
- [ ] 显示“客观已评分X项、待确认Y项、主观待评Z项、失败N项”；COMPLETED+NEEDS_REVIEW也不用绿色出分成功；FAILED仍展示可信已算项。显示原件变化须全量重评提示，不加历史分数页面。
- [ ] 解析/采纳后仍不自动付费重算，显示“规则已更新，实际评分尚未执行”，按钮沿用现有runScoring。
- [ ] 关闭抽屉停止本地轮询，后台继续；重开/刷新先GET状态并恢复观察，不POST。轮询绑定触发taskId；发现最新任务ID变化时转为明确观察新任务，不把它认作本次完成。
- [ ] 网络中断/前端等待结束显示“暂未获取结果，可稍后查看”，不推断后端失败；后台明确FAILED则显示其错误。

### Task 7 — 时间、资源、重启边界

**修改范围**：评分专属配置、`BidFileLoadingCoordinator`及既有下载边界、`SidecarStreamTextExtractor`、`SidecarImageExtractor`、`ScoreScoringAppService`、`OpenAiScoreAnalyzer`评分请求配置透传、`ScoreParseAutoPolicy`的评分业务拒绝识别、`ScoreParseTimeoutScanJob`、`ScoreParseTaskRecoveryRunner`；只处理SCORING预算，不改变PARSE技术失败策略。测试对应现有TimeoutScanJobTest及VersionMysqlTest。

- [ ] 明确首轮总预算从任务createdAt起1800秒（含PENDING排队），OCR最多1500秒且受剩余总预算约束，给下载/hash、模型及落库留时间；所有重试共享同一截止时间，不能3次HTTP各重新拿1800秒。
- [ ] createdAt+1800秒是唯一总截止；startedAt只用于拆分执行耗时，队列等待另报但不额外赠送预算。PENDING等待初始最多10分钟且受总截止约束，先到者终结排队超时/拒绝，不能永远不被扫描。
- [ ] 当前扫描按updated_at静默30分钟，前端900次轮询也不是准确30分钟；配置必须统一到评分执行预算，解析任务维持旧契约。
- [ ] Java按任务createdAt+1800秒计算绝对UTC截止时间，传X-Scoring-Deadline-Epoch-Millis；Sidecar到达后校验并钳制到服务端上限，再转为本机monotonic deadline。部署校验时钟偏差不超过5秒并预留5秒安全余量；绝对时间覆盖网络上传、multipart解析及worker等待，不从worker开始重新计时。
- [ ] 复用_convert_document一个执行入口：容量许可、超时/错误映射、回收只实现一份。先测试现有pool排队/运行中取消边界；若需请求自有进程组实现硬终止，将其作为该入口内部实现，不新建独立评分队列/监督服务。普通端点契约仍不变，不能终止其他请求。
- [ ] 容量初始1；评分容量不足时尽早返回503可重试繁忙，Java不在原请求里反复上传整卷抢槽。利用既有请求头依赖/中间件在可行的最早阶段检查容量；若FastAPI已解析multipart，不声称能阻止已发生的网络上传，但须阻止额外副本与排队执行。是否单独扩容以Task1进程树RSS和普通转换等待时间决定。
- [ ] deadline/断连回收该请求的子进程与临时文件；所有SAX/解码循环检查截止，Tesseract子调用受剩余时间约束。Future.cancel只能取消尚未运行的工作，不能当硬终止。真实测试涵盖过期不启动OCR、运行中超时无残留、下一请求可用和普通请求无误杀。
- [ ] 到期停止安排新项；HTTP/OCR子进程也须遵守deadline并清理。扫描置失败后即使旧线程返回也不得写回。
- [ ] 重启沿用现有恢复策略明确失败并允许人工重试，不新增假断点续跑。禁止仅刷Redis心跳假装进度。
- [ ] 原件不满足从createdAt起含排队的1800秒目标则性能门禁不通过，记录具体耗时；预算调整必须依据测量、同步所有边界并在计划中重新记录目标，不能自行把30分钟验收改成任意时长。

### Task 8 — 集成、交付与历史恢复

**文件**：`docs/testing/actual-bid-scoring-acceptance.md`、`docs/lessons/lessons-learned.md`、相关wiki；必要配置涉及 `scripts/release/remote-deploy.sh` 的既有Sidecar参数透传。

- [ ] 执行真实Sidecar→Java→MySQL→UI验证；测试替身只验证单层分支，不充当最终验收。
- [ ] 三技能审查顺序：ai-slop-cleaner → 真实验证命令 → 独立google-code-review → 独立xiyu-code-review；最多三轮，按仓库熔断规则收口。
- [ ] 打包候选SHA并核对前后端/Sidecar一致；测试环境先发布，1123/580/1117每个原件分别验证。
- [ ] 生产变更执行前给出测试证据、候选版本、备份与回滚锚点；部署授权作为具体发布方案最后一步，不以本次计划代替。
- [ ] 生产先1123单项目重评，核对当前规则与原件。结果通过后580、1117，再按清单逐项目恢复，不重跑已经有可信结果的项目。
- [ ] 每个项目保存重评前后快照、任务ID、API响应和页面；未通过立即停止扩大范围。

## 5. 验证矩阵与命令

| 验证层 | 核心断言 | 阻断条件 |
| --- | --- | --- |
| 真实OCR | 41页/图尾部证据、透明小图、混合页、去重 | 任何证据静默丢失 |
| 协议 | 缺报告UNKNOWN、传输超限、普通端点不变 | 旧端点破坏或字段丢失 |
| 纯规则 | 人工范围重评、自动复用、0/部分分 | 强制重评仍SKIPPED或扩大范围 |
| 真实MySQL | 有界报告读回、FAILED成功项保留、换文件局部拒绝、版本/迟到守卫 | 丢旧分、超限兜底再失败、身份错配 |
| 前端/自动策略 | COMPLETED+NEEDS_REVIEW、FAILED部分分、计数、重开、业务拒绝不熔断 | 空分绿色成功、丢分或自动解析被误拦 |
| 原件全链 | 文件SHA→证据→数字→DB→API→UI | 只有HTTP/COMPLETED，没有正确客观结果 |
| 性能/调用预算 | 一次下载、无条件抽图为0、单项最多2次模型传输、解码前限制、进程树RSS | 重试叠加、临时文件/进程泄漏、超时/资源失控 |

```bash
# 在实现worktree；仅测试，不启动开发服务。实际解释器复用已验证的Sidecar环境。
PYTHONDONTWRITEBYTECODE=1 /Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python -m pytest -q -p no:cacheprovider document-converter-sidecar/tests/test_scanned_bid_ocr.py document-converter-sidecar/tests/test_app.py document-converter-sidecar/tests/test_convert_stream.py document-converter-sidecar/tests/test_docx_streaming_sax.py document-converter-sidecar/tests/test_pdf_memory_guard.py document-converter-sidecar/tests/test_scoring_deadline.py
mvn -f backend/pom.xml -Dtest=SidecarStreamTextExtractorTest,SidecarImageExtractorTest,BidFileLoadingCoordinatorTest,ScoreScoringAppServiceTest,ScoreScoringItemPickerTest,ScoreItemAssessorTest,ScoreAssessmentGuardTest,ScoreDocExcerptExtractorTest,ScoreScoringVersionMysqlTest,LegacyScoringVersionMysqlTest,ScoreParseTimeoutScanJobTest,ScoreResultReusePolicyTest,ScoreScoringResultsAssemblerTest,ScoringRunReportCodecTest,SummaryAggregatorTest,AutoFailCircuitTest,OpenAiSdkStructuredOutputTransportTest test
mvn -f backend/pom.xml -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest test
npx vitest run src/composables/projectDetail/useScoreParseDrawer.spec.js src/composables/projectDetail/scoreParseTask.spec.js src/composables/projectDetail/scoreExtraction.spec.js src/views/Project/stages/components/ScoreParseDrawer.spec.js src/views/Project/stages/components/ScoreParseTable.spec.js
npm run lint
npm run build
git diff --check
```

真实MySQL测试若Testcontainers因Docker不可用而skip，不算通过；部署前完整后端回归 `mvn -f backend/pom.xml test` 串行执行，避免共享target污染。前端扩大回归仅在实际依赖变更/失败时；三技能verify使用上述真实命令，不用门禁脚本自身测试充数。

## 6. 发布、恢复与回滚

1. **发布顺序**：兼容的Sidecar先到位→后端→前端；新后端遇旧Sidecar只能UNKNOWN/明确错误，不能静默兼容为成功。整套尽量在同一发布窗口激活；回滚按前端→后端→Sidecar配套恢复。
2. **配置核对**：中文Tesseract语言包、shared key、上传/文本/内存限制、OCR/HTTP/任务deadline；readiness不替代真实OCR验证。
3. **无DDL预期**：run_summary已存在。若实现证明必须新增列，单独走Flyway创建与回滚规范，不修改已发布迁移。
4. **1123恢复**：先确认规则仍为正确12项/100分（实施时重新核对，不硬编码）；只有规则错误才重解析。确认附件375对应真实原件，执行一次MANUAL/ALL，记录新taskId，核对逐项输入引用及数值。
5. **其他项目分类**：正文失败走新提取；配额失败先核对实际大小/可处理上限，不能无条件加配额；模型失败按诊断处理；未评分不当成OCR故障。
6. **恢复凭证**：发布SHA、Sidecar版本、文件SHA、规则fingerprint、taskId、执行耗时、覆盖报告、每项预期/实际/引用、API/UI截取。
7. **回滚触发**：原件证据缺失、新旧结果混用、越权/数据损坏、服务资源异常。停止新评分，留存失败报告，配套回滚运行包；不能删除坏结果伪造验收。旧代码未必理解新run_summary，回滚后暂停AUTO，需验证读取兼容。
8. **数据回滚**：代码回滚不自动恢复被替换的score_result。每个恢复项目必须事先保存评分表及相关任务的一致快照；恢复数据另走核对/授权操作，不自动覆盖恢复期间新增业务数据。

## 7. 顺序、并行和工期估算

Task1 → Task2/3接口与资源作用域 → Task4/5 → Task6/7验证 → Task8。契约冻结后，前端可并行编写摘要/轮询测试，复用策略纯函数可与Sidecar并行；ScoringAppService/VersionService共享文件串行由同一人负责。Task7的deadline协议和进程监督须与Task2/3一起设计实现，最后独立执行超时/排队/断连验证，不能等集成后才补接口。

建议三个逻辑提交批次：A完整提取及协议；B真实重评/评估/任务诊断；C页面/全链验收/发布文档。各批次独立测试与审查，生产使用整套已验证候选，不拆开宣称恢复。

原v1的4.5–7人日估算作废，不作为交付承诺。先完成Task1（初估0.5–1人日）测清原件、唯一媒体、实际请求次数及RSS，再按冻结后的执行边界和原件验收工作重估；R01–R10共享文件和测试，单项估算不可相加。首轮已删跨原件历史展示、重复正文解析和无条件全卷抽图。

## 8. 交付检查与设计约束

- 需求FR001–010、SC001–006及审查R01–R10分别由Tasks1–8覆盖，详见tasks.md矩阵；实施状态见tasks.md，局部测试不代替最终验收。
- Pure Core：覆盖判定、结果复用和摘要分类为纯函数；HTTP/事务/文件IO留在应用边界。
- 不复用合规模块的限页/剥媒体行为；不建持久OCR缓存；不改评分标准解析与知识库算法。
- 文档沿用已有隔离任务分支，替代Spec Kit通用新分支默认；依次产出spec→plan→tasks。可选自动commit钩子本次不执行。
- 明确未验证：原件性能、模型真实逐项准确性、发布兼容与历史恢复，均有执行任务和阻断标准；本次文档完成不代表这些项通过。


## 9. v2审查建议落实

| 审查项 | 修订决定 | 任务/验收 |
| --- | --- | --- |
| R01 全局PARTIAL门禁 | 按服务端可核验证据逐项决策；未知规则保守待确认，不加通用规则引擎 | Task3/5，局部充分证据与累计/缺失对照 |
| R02 FAILED丢成功项 | preserveEligible按项；AUTO整任务跳过另判 | Task4，A成功B失败只重试B |
| R03 待确认误熔断 | 正常执行COMPLETED+NEEDS_REVIEW，技术失败FAILED | Task5/6，PARSE交叉回归 |
| R04 重复抽图 | 下载一次，取消无条件抽图，按证据补图，延长现有临时文件作用域 | Task3/7，下载/上传次数与释放 |
| R05 重试叠加 | SDK重试0，单项最多2次实际传输含降级，共享截止 | Task5/7，真实边界计数与超时 |
| R06 TEXT超限 | 60KiB摘要、仅裁剪可选样例、4KiB失败兜底不覆盖结果 | Task3，500位置/中文/必需超限 |
| R07 历史评分扩张 | 跨原件局部请求拒绝并提示ALL，延期历史展示 | Task4/6，旧结果不被覆盖、来源过期不计当前总分 |
| R08 双调度 | 一个执行入口/容量/异常/清理，硬终止仍保留 | Task2/7，过期、无残留、无误杀 |
| R09 解码内存 | 解码前编码字节/像素限额，测进程树RSS | Task1/2，超大像素与正常小字 |
| R10 Word重复遍历 | 扩展既有SAX引用收集，OCR模块不另写正文解析 | Task2，旧字符串输出及来源回指 |

采用证据：既有代码位置和v1审查见[审查记录](../../docs/exec-plans/active/actual-bid-scoring-recovery-plan.md)。v2未改变已确认现场事实，也不意味着审查风险已通过运行验证。

## 10. 实施测量后冻结的资源配置

- Sidecar请求容量仍为1。单次评分OCR默认1路，配置clamp为1–4；4路只做有界图片工作，不增加第二调度系统。
- 1123单路完整原件1500秒到限仍PARTIAL；4路701.880秒完成全6624单元处理，进程树峰值1422737408字节，但4个格式/像素失败使整体仍为PARTIAL。该测量不是最终恢复PASS。
- 实际发布脚本原有4GiB容器限制下，配置workers=4、单图像素上限24000000（默认仍16000000）；1123三个超限媒体最大22268416像素。保持64MiB编码上限、1500秒OCR及1800秒总预算不变。
- 一个实际被引用EMF含Unicode文本。仅增加有边界校验的受限文本子集读取；未知绘图/嵌入位图或不支持文本语义均显式PARTIAL，不引入图形转换服务。对应合成夹具和4个私有媒体验证纳入本地回归。
- 配置文件修改仅准备发布工件，未执行服务器发布。最终原件重测、模型金标及测试环境DB/API/UI分别记录于[验收记录](../../docs/testing/actual-bid-scoring-acceptance.md)。

## 11. 原件复验后补齐按项充分条件

- 最终完整原件6624/6624、0失败/未读、122 empty、COMPLETE，759.569秒、树RSS1516191744字节；不等于逐项模型业务正确。
- 该真实报告证明“任何空OCR都只允许ISO证书例外”的实现会使明确的非证书项永远待确认。恢复T020：仅为完整闭合、单指标的四档额度规则增加有限最高档证明；复用既有金额BigDecimal/单位归一和原文来源，绑定同一原生响应行，服务端比较且与模型原始及最终值一致才发布。低档、未达、缺失/否定、多指标、冲突、附加材料要求不据此放行。此限制只针对不完整证据的例外；真正COMPLETE且无空OCR时，既有合法0/部分分路径保留。
- 不建设通用规则引擎，不拓宽报价/主观项，不把模型自报充分当证明。证书规则里的证明材料、有效期、公章条件未核验时，标题匹配仍须待确认。
- 自动清理阶段因职责只限行为保真而ESCALATE已停止；root依据已授权的087按项恢复目标接回S4修复，再跑新鲜验证和独立审查。未创建PR或部署。

- 真实652固定模型工程回放先证明来源与服务端阈值均成立，但通用12k摘录漏掉末尾原生响应行，仍被quote-in-excerpt守卫置空。按T019“有反例才定向修正”：完整消费额度规则后，只在该项找到唯一、连续来源绑定的同指标原生响应行时，将原文纳入既有12k预算；不改通用摘录器、不增加检索系统，仍校验引用确实出现在实际模型输入内。

- 用户随后明确确认第652项预期9分，作为真实评分对照；固定模型工程回放与真实模型/DB/API/UI验收仍分开，其他金标尚未完成。


## 2026-11-16 项目580增量实现计划

基线eddf17831，目标working_tree。使用writing-plans方法，根Agent顺序执行，controller:none，不开启第二套工作流。纯核心为已有domain证据策略；Sidecar文件/进程、ScoreItemAssessor模型调用为外壳。

1. 先补真实OCR输出一致/并行上限/时限失败/顺序保真回归并证明RED；PDF渲染在原调用线程，只有Tesseract任务并发，沿用SIDECAR_BID_OCR_WORKERS（1..4）和1500秒总预算。只持有有界窗口，不一次解码1225页。
2. 核对54/75/445/749页；识别为空时做有界识别复核，纯图页不能凭空当白页。只有实际充分证据可改变出分资格，保持原全局守卫的保守默认。
3. 比较真实评分规则、原文页、12000字符节选及补图，证明引用首次丢失位置后补回归修复；不预设页码错位。
4. 保留细项失败原因并在既有质量摘要显示未读/失败/识别空单元；不新建UI流程。
5. 同原件回放并记录覆盖、耗时与RSS；补相邻回归、架构、Vitest、build，再清理及独立审查。客户工件不入Git。

验证命令：Sidecar venv Python -m pytest document-converter-sidecar/tests/test_scoring_extraction_v2.py及新增PDF测试；mvn -f backend/pom.xml -Dtest=ScoreItemAssessorTest,ScoringEvidenceCoverageTest,ScoringCreditLimitProofTest,ScoreScoringAppServiceTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest test；npx vitest run（具体新增测试）与npm run build。红/绿日志存/tmp/xiyu-580-scoring-diagnose-20261116。


### 同原件进一步定位
第6页完整响应已满足免运费最高档文字条件；新增FreeDeliveryScoreProof，仅闭合二分支规则、原始/最终模型分等于最高档、两个无限制条件与全程免费同时出现、来源已验证时适用。0元起送单独不放行。第818页并非唯一有效来源，补图验收要求保留足够证据的响应页（同原件第6页），不硬编码页码。信用额度OCR不足仍保留待确认，不把肉/财猜成赊销。
