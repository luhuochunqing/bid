# AI实际评分恢复执行入口

状态：Implementing v2，2026-11-13。本地代码与测试进行中；未部署/重算。

- [v2完整实施计划](../../../specs/087-actual-bid-scoring-recovery/plan.md)
- [需求](../../../specs/087-actual-bid-scoring-recovery/spec.md)
- [35项执行清单](../../../specs/087-actual-bid-scoring-recovery/tasks.md)
- [接口与报告合同](../../../specs/087-actual-bid-scoring-recovery/contracts/scoring-recovery.md)
- [v2 HTML阅读版](../../artifacts/actual-bid-scoring-plan-v2-20261113.html)
- [v1历史阅读版（已替代）](../../artifacts/actual-bid-scoring-plan-v1-20261113.html)

## 进度
- [x] 生产只读审查与41页真实OCR反例。
- [x] 需求、方案、接口及任务计划。
- [x] 按R01–R10同步修订v2，实施任务仍全部未执行。
- [ ] 原件性能/人工金标。
- [ ] 业务实现与回归。
- [ ] 测试环境真实验收。
- [ ] 生产配套发布、指定项目恢复。

## 决策日志
2026-11-13：复用现有Tesseract/评分任务/run_summary，不引入平台。MANUAL无需新force参数。独立审查补齐originTaskId追溯、换文件部分重评总分隔离、请求deadline及子进程回收、实际提示文件落点。原件性能未知有明确验收门，不预先放宽。


## 2026-11-13 MCP方案反向审查（v1历史发现）

审查对象：`specs/087-actual-bid-scoring-recovery/plan.md`及配套合同，代码基线`59ae5ef8f417dab7a5b146e45a866e41921debf6`。已调用MCP sequential-thinking，并由两个独立只读代理分别核验OCR/资源与评分/业务路径。以下保留为v1历史审查记录，所引plan行号属于v1。用户随后授权修订，R01–R10已纳入v2；最新口径以v2 plan及合同为准，业务代码仍未实现。

结论：**需修订后实施**。完整度报告、MANUAL真重评、逐项有效性和真实原件验收应保留；跨原件历史展示可以延期，正文定位及预算传递应复用现有能力。复杂度为含针对性回归的相对评估，各项存在共享改动，不能简单相加。

### R01 / P1：全局PARTIAL门禁扩大故障范围

- 计划位置：plan.md:59、138；逐项正向证据处理见169。
- 代码位置：`backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java:46-69`，现有边界本来按项评估。
- 问题：任一无关附图未读/失败即停止所有模型评分，使已具备充分证据的项目仍全空。全卷覆盖是质量属性，不能替代每项能否确定分数的判断。此为拟议行为的业务风险，未声称1123已经触发。
- 建议：覆盖报告保持真实PARTIAL；只允许证据及适用规则足以唯一确定分数的项出分。依赖全卷累计数量、否定条件、缺失判断的项继续待确认；不能仅找到一份合同就宣称累计业绩分确定，也不能把部分文档作为完整成功验收。
- 复杂度：中，约1–2人日。新增判定进入纯策略，复用现有逐项评估；补“无关附图失败、正向项仍可评”和“计数/缺失项不误判”用例。

### R02 / P1：混合成功失败后，局部重试会丢未选成功项

- 计划位置：plan.md:149要求未选项保留，170–171规定任一技术失败使整任务FAILED。
- 代码位置：`ScoreScoringVersionService.java:95-108`仅COMPLETED进valid；`ScoreScoringAppService.java:199-215`用snapshot.results作为旧结果，未选且不在集合中则写空分；`ScoreScoringVersionService.java:128-131`全项原子替换。上述文件均位于`backend/src/main/java/com/xiyu/bid/scoreparse/application/`。
- 场景：A=8分、B技术失败，任务FAILED；再ITEMS=[B]，A不进入valid，最终被空分覆盖。FAILED结果当下仍可见，不能误报“失败立即隐藏全部分数”：read():139使用visibleResults。
- 建议：复用拟新增ScoreResultReusePolicy，按单项成功状态及文件/规则/引擎身份判断，允许失败任务中的已验证成功项保留，失败项继续禁止复用。
- 复杂度：中，约0.5–1人日。真实MySQL回归必须覆盖“混合结果→只重试B→A不丢”。

### R03 / P1：业务待确认被当成执行失败，误触发共享熔断

- 计划位置：plan.md:171、data-model.md“执行与质量”；spec.md:20又要求两者分开。
- 代码位置：`backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAutoPolicy.java:32-47`合并PARSE/SCORING的AUTO失败；`backend/src/main/java/com/xiyu/bid/scoreparse/domain/AutoFailCircuit.java:9-10,20-26`在30分钟两次失败时熔断，后续MANUAL/COMPLETED可提前解除。
- 场景：模型和OCR正常完成但客观依据不足，两次AUTO全待确认被记FAILED，连自动解析也被阻止。窗口到期可恢复，不是永久锁死。
- 建议：技术故障用FAILED；正常执行但证据不足用COMPLETED加明确NEEDS_REVIEW质量结果。页面不能给绿色出分成功，恢复验收仍失败。若坚持任务FAILED，必须从既有熔断计数排除这类业务原因，不新增第二套状态机。
- 复杂度：低至中，约0.5人日；补PARSE/SCORING交叉影响用例。

### R04 / P2：OCR后仍默认第二次全卷上传抽图

- 计划位置：plan.md:135-138、167、188-195未明确取消或约束该旧路径。
- 代码位置：`backend/src/main/java/com/xiyu/bid/scoreparse/application/BidFileLoadingCoordinator.java:66,133-136,151`默认多模态并在OCR后抽图；同目录`SidecarImageExtractor.java:76-83,95-96`已有定向PDF页渲染，但全卷抽图独立600秒；`document-converter-sidecar/image_extractor.py:148-156`媒体总量超预算则返回空图；`app.py:671`在async路由中同步抽图。
- 问题：大文件重复上传/解析，可能最终一张图也拿不到；同步抽图还可能阻塞事件循环。1123是否超过媒体预算须检查原件，不能用586MB总大小直接证明。
- 建议：充分OCR证据的项直接走已有纯文本评分；确需视觉判断时按证据位置补图。PDF复用renderPages；Word定向媒体需利用已采集关系引用，不能假称renderPages已支持Word。所有补图消费剩余预算；不全局关闭其他模块的多模态。
- 复杂度：删无效抽图低；定向补图中，约0.5–1.5人日。验证同一评分不再无条件上传原件两次。

### R05 / P1：新增一层模型重试，实际调用次数与总预算仍未闭合

- 计划位置：plan.md:170“最多重试一次”、190“共享截止时间”，但未写清SDK配置透传。
- 代码位置：`backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiScoreAnalyzer.java:266,285`使用通用USE_CASE配置；`backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolver.java:45-54`只有另一用途multimodal score fact extraction把maxRetries设0；同目录`OpenAiBidAgentRequestConfig.java:12-14`默认2次SDK重试，`OpenAiSdkStructuredOutputTransport.java:135-137`实际传入SDK；`ScoreItemAssessor.java:81-87`另有多模态降级调用。
- 问题：外层两次业务尝试可对应最多六次SDK请求尝试，尚未包括格式/多模态降级。只给Sidecar传deadline不能约束模型调用，不能承诺整任务1800秒。
- 建议：复用已有不可变OpenAiBidAgentRequestConfig，在评分调用边界传min(配置timeout,剩余预算)并显式选择唯一重试层。若外层负责重试，评分请求maxRetries=0；降级也重新核验剩余预算。不改解析用途的全局默认，不造新AI客户端。
- 复杂度：中，约0.5–1人日；需验证实际请求次数、临近截止时间和降级分支。

### R06 / P1：run_summary没有字节预算，详细报告可能在最后落库失败

- 计划位置：plan.md:66、91，data-model.md输入extraction/逐项evidenceRefs/历史值。
- 代码位置：`backend/src/main/java/com/xiyu/bid/scoreparse/entity/ScoreParseTask.java:95`及`backend/src/main/resources/db/migration-mysql/V1204__add_score_task_run_summary.sql:7`定义TEXT。
- 本轮容量探针：500个合成Word未读位置，仅partName、paragraphOrTableAnchor、relationshipId、mediaName、reasonCode及计数，紧凑JSON为84,783 UTF-8字节；未含请求和评分诊断。超过TEXT的65,535字节容量。不是实际生产溢出复现。
- 建议：run_summary只存身份、准确计数、有限失败样例、单项状态、截断标记和必要直接来源。完整范围清单使用既有受控工件留存；不能丢失PARTIAL标记，不把“样例截断”解释为“无其余失败”。新增序列化字节上限和中文边界回归，不优先扩大表型。
- 复杂度：低至中，约0.5人日。[MySQL TEXT官方上限](https://dev.mysql.com/doc/refman/8.0/en/string-type-syntax.html)。

### R07 / P2：跨原件局部重评扩成了新的历史评分模型

- 计划位置：plan.md:91、149、153，contracts/scoring-recovery.md:41。
- 代码位置：`ScoreItemAssessor.java:120-126`仅复制当前评分行；`ScoreScoringVersionService.java:128-131`删除后替换；`ScoreScoringResultsAssembler.java:38-44`仅装配当前行。均位于`backend/src/main/java/com/xiyu/bid/scoreparse/application/`。
- 问题：方案额外要求历史值入JSON、沿originTaskId找旧报告、跨原件页面展示，新增保留期、引用失效、批量读取和容量责任；这超出恢复当前标书评分的主要目标。
- 建议：首轮同原件支持ITEMS/UNSATISFIED；原件hash已变时，局部重评明确拒绝并提示用户选择ALL，不能偷偷扩大计费范围。同原件保留直接来源标记，避免递归历史链。恢复快照照常留存，延期历史评分展示。
- 复杂度：收缩方案低至中，约0.5人日；原历史功能中至高，宜独立需求评估。

### R08 / P2：普通pool与评分进程两套生命周期，共享1个槽仍会排队

- 计划位置：plan.md:124、193-194。
- 代码位置：`document-converter-sidecar/app.py:176-181`已有进程池隔离和worker回收，394-409已有统一异常/OOM处理；`converter.py:1287-1302`已有Tesseract超时。
- 问题：新增评分进程组、断连监督、门禁和清理，同时维护普通pool；共享容量1并未解决长OCR阻塞普通转换，最长1500秒占槽会扩大等待。具体吞吐影响需压测，当前没有实测数字。
- 建议：保留隔离、硬终止和资源回收；共用一个执行入口管理容量、错误映射、清理。先测现有执行器的队列/超时，再确定替换范围。评分忙时尽早拒绝，不在完整大文件上传后无限排队；是否分配独立容量以RSS测量决定，不盲目加并发。
- 复杂度：中至高，约1–2人日。不能把Future.cancel当硬终止：Python文档明确运行中的任务不会因此取消。[Python 3.12官方说明](https://docs.python.org/3.12/library/concurrent.futures.html#concurrent.futures.Future.cancel)。

### R09 / P2：缩到2000像素发生在解码之后，单张峰值仍无界

- 计划位置：plan.md:123-127只有媒体读取受限、缩图、finally释放的方向，缺少可验收的解码前约束。
- 代码位置：`document-converter-sidecar/converter.py:1448-1456`先archive.read/image.load再构建RGBA/RGB，1267-1284才resize；`image_extractor.py:322-329,352-363`已有ZIP目录和图片头检查模式。
- 问题：小压缩体积的大像素图片可在缩图前分配多份全尺寸缓冲；逐张释放仅限制并存数量，不限制单图峰值。不是1123已发生OOM的结论。
- 建议：解码前限制单媒体解压字节和像素数，复用目录/头信息检查，支持的格式先降采样解码；超限单元显式报告。完整任务用进程树峰值RSS验收，不新增第二套通用图片库。
- 复杂度：中，约0.5–1人日。

### R10 / P2：Word来源定位未明确复用现有SAX遍历器

- 计划位置：plan.md:118、122-123。
- 代码位置：`document-converter-sidecar/app.py:197-296`已有流式正文/表格/标题处理，252-254识别blip和imagedata，但只输出统一占位符。
- 问题：若直接在新scoring_ocr.py另写全文遍历和拼接，就会维护两套Word解析。当前只是计划缺少复用约束，不能说重复代码已经存在。
- 建议：扩展既有遍历器的可选引用收集，记录关系ID及段落/表格位置；普通字符串输出保持兼容。OCR模块只负责识别与覆盖报告。页眉页脚等未覆盖part可复用同一处理器补齐。
- 复杂度：低至中，约0.5–1人日。

### 不应为简化而删掉的设计

- MANUAL绕过三层旧结果复用；AUTO并非死代码，`src/views/Project/stages/composables/useQualityCheckDrawer.js:168`仍触发autoScore。
- `/convert`本来就是结构化JSON，增量加入extraction是复用，改为响应头塞完整清单更差。
- 小型不可变值对象和纯策略符合`ARCHITECTURE.md:17-26`，不能为少几个文件把业务规则塞回Application Service。摘要可扩展现有`scoreparse/domain/SummaryAggregator.java`，IO/事务留在边界。
- 短事务、最新任务/规则守卫、主观无数字、合法0分/部分分、原件金标和数据库/API/UI一致性验收均保留。
- `matchRatio`覆盖模型数值属于spec041 FR012/013既有合同；本次不擅改公式。如与原件真实规则冲突，应另行确认产品口径。

### 验证边界

本轮执行源码与合同交叉核验、两个独立审查、无客户数据JSON容量探针和文档检查。未运行新的业务实现、MySQL故障复现、586MB原件全链路或并发压测；以上代码分支结论、设计风险和未验证性能分别标注。原4.5–7人日估算不能当可靠交付承诺，应在原件基线及上述删减确定后重估。


## 2026-11-13 v2修订记录

- R01：PARTIAL按项核验，模型自报充分无效；正常原件仍以COMPLETE+人工金标验收。
- R02/R03：保留FAILED任务中的成功项；AUTO整体跳过另判。正常全待确认为COMPLETED+NEEDS_REVIEW，明确业务拒绝不进入技术熔断。
- R04/R05：一次下载、现有临时文件延长到评分finally、按来源补图；SDK重试0，含降级每项至多2次实际传输，共享deadline。
- R06/R07：摘要60KiB，必要字段超限使用4KiB小型失败报告、replacement=null；换文件局部拒绝提示ALL，延期历史展示。
- R08/R09/R10：共用既有转换执行入口，保留硬终止；解码前资源限制；扩展现有SAX，不另造正文解析。
- 已同步spec/plan/tasks/contracts/data-model/research/quickstart/checklist及HTML v2，v1标记已替代；35项实施任务未勾选。
- 原v1工期估算作废，原件性能基线完成后重估。此次修订不包含业务实现、提交、部署、重算。

### v2文档验证

- 独立只读一致性复核通过：R01–R10均映射，补齐旧来源读侧过期/不计总分及createdAt起1800秒含排队；无剩余文档阻断。
- 35个唯一T001–T035全部未执行；Markdown相对链接检查通过。
- HTML与plan源SHA-256一致；桌面/390px手机无水平溢出，目录无断锚，主题切换与打印隐藏导航验证通过。
- 本轮仅文档验证，业务测试、原件性能、部署与恢复均未执行。

## 本地实施进度

- 用户已授权unified-dev-flow实施，delivery_target=working_tree。
- 已下载1123真实原件，614173466字节、1740个不同媒体哈希；SHA及采样证据见docs/testing/actual-bid-scoring-acceptance.md。原件Git外保管。
- UI当前34文件520测试、lint/build通过；Sidecar含EOCD兼容133测试通过。Java真实MySQL与合同边界仍收口。
- 第1轮独立只读反馈已交修复；完整有界原件提取尚在运行。未授予整体验收通过。

## 2026-11-13 v2本地实施收口入口

- 交付上限working_tree：已实现完整提取、真实手工重评、成功项保留、质量摘要、共享deadline及有界摘要。未提交/推送/部署/生产重算。
- T004–029与T035本地实现/分组测试已执行；1123原件身份和性能反例已采集。最终统一三技能门禁和整件复验以本地.omc/verify-087-final.md、review-087-final.md和087-progress.md为准，无工件即未完成该项。
- T001–003尚缺其他代表原件及完整业务金标；T030–031/T033–034未执行，不将本地代码或Docker合成真OCR等同真实模型及DB/API/UI业务恢复。
- 完整测量和已接受的EMF原生文本可见性Minor限制见docs/testing/actual-bid-scoring-acceptance.md。

### 原件反例后的候选修正

1123完整提取759.569秒/树RSS1516191744字节、6624/6624 COMPLETE、122empty。该组合暴露非ISO全局门禁；root在自动cleaner因职责ESCALATE后接回S4，有限额度proof及定向摘录已本地实现。真实652固定模型工程回放接受9并拒raw8+ratio100、7类112项通过、独立Astra复审通过；实际模型0次、金标pending。最终重新验证/审查以本地工件为准，前次BLOCKED记录保留。
