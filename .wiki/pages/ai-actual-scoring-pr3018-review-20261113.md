---
title: AI实际评分最新PR审查与项目1123生产核对
space: engineering
category: reference
tags: [scoreparse, 实际评分, OCR, PR审查, 1123]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/CreditLimitScoreProof.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoringDocument.java
  - document-converter-sidecar/converter.py
  - document-converter-sidecar/app.py
  - document-converter-sidecar/scoring_extraction.py
  - document-converter-sidecar/conversion_execution.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringExecution.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoringRunReportCodec.java
  - document-converter-sidecar/tests/test_scanned_bid_ocr.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringAppService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringVersionService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemPersistenceService.java
  - src/composables/projectDetail/useScoreParseDrawer.js
backlinks: [_index]
created: 2026-11-13
updated: 2026-11-13
health_checked: 2026-11-13
archive: true
---
# AI 实际评分最新 PR 审查

## 结论与范围

- 2026-11-13 只读核对 Gitee PR、代码、生产/测试发布记录、生产数据库和日志；无部署、无重算、无业务代码或业务数据修改。
- 审查基线：`59ae5ef8f417dab7a5b146e45a866e41921debf6`。
- !3016 解抽屉锁，!3017 接入 PDF/Word OCR，!3018 限制大文档 OCR 工作量。这些改动解决部分阻断，不能证明完整可靠评分已恢复。
- 本轮新视角：将任务完成、数值结果、输入证据覆盖、线上部署版本分别核对。

## 生产现场证据（当日快照）

| 核对对象 | 事实 |
| --- | --- |
| 生产发布记录 | `/opt/xiyu-bid/deployed-release.json` 为 `6d843c212`，2026-11-12 19:52:44 CST 激活 |
| 生产 jar | `cmp -s` 确认 shared/backend/app.jar 与该 release 内 jar 相同 |
| 测试发布记录 | `68224194a-api8080`；生产和测试都未包含 !3016/!3017/!3018 |
| 项目1123投标附件 | project_documents.id=375，BID，docx，元数据大小586MB |
| 项目1123当前规则 | score_item.id=641..652，共12项，parse_task_id=230，active_version_id均空 |
| 当前规则解析 | task230，2026-11-12 15:26:58..15:30:11，PARSE/COMPLETED |
| 当前实际结果 | 12项均无关联score_result，数值得分均空 |
| 最新实际评分 | task193，UUID 05e08bce-24dd-46ef-972e-c6ff35254c2c，2026-11-05，SCORING/COMPLETED；之后6次PARSE，没有新SCORING |
| 预估与实际 | 客户合作业绩12、认证证书8、仓储网点12是est_score，不是actual_score |
| 全库历史任务 | 现存28次SCORING，涉及16项目；24 FAILED、4 COMPLETED。是历史记录统计，不是当前版本成功率 |
| 近期失败实例 | task235/项目580：OCR超时；task234/项目1117：扫描页无有效文字；项目1132：处理配额超限 |

数据库通过服务器既有凭据连接，`SET SESSION TRANSACTION READ ONLY; START TRANSACTION` 中执行 SELECT，未导出凭据、文件地址或原件。

## 1123空分的直接原因

- 当前规则重建后没有再次执行实际评分，故不存在当前规则对应的实际结果。
- `ScoreItemPersistenceService:87-102,204-211`：规则指纹改变时清旧结果并重建评分项；规则相同另有保留逻辑，不能笼统说每次解析都清空。
- `ScoreScoringVersionService:105-107` 对没有结果的当前项显示“当前规则尚未评分，请重新评分”。
- 当前结果0条/规则12条，不满足跳过条件；不得将下面的SKIPPED缺陷认作1123当前空分原因。

## 审查发现

### Major：大文档部分OCR没有传递完整度

- `converter.py:1239-1255,1361-1370,1444`：超过40候选时，有原生正文只识别12页/图片，无正文24页/图片；Word按图片体积排序。与文件是否实际达到500MB无关。
- 返回值仅有markdown/pages，没有缺失扫描证据标志；下游对非空正文正常评分。
- 真实PyMuPDF+Tesseract反例（无mock）：41页扫描PDF，第41页为CERTIFICATE ABC999；整卷仅返回1..24页，ABC999缺失；单独识别第41页成功。运行4.76秒，排除超时和不可识别。
- 本机反例工件：`/var/folders/hb/fw83wt2j5psblns41081bgh40000gn/T/xiyu-pr3018-review-6luaa73z/result.json`，同目录scan41.pdf。均为合成文档。
- 影响：有凭证但未读取，可能被当成未提供证据而扣分；不是线上1123已发生误扣分的证明。
- 修复方向：把未识别部分明确传递到评分决策，证据不足项待确认；按评分项补读相关证据，不可仅按图片体积认定完整。

### Major：旧空分结果可阻止手动重新打分（存量缺口）

- `ScoreScoringAppService:128-131,187-196`：ALL范围在同URL/文件内容与规则匹配、已有有效结果行数完整时跳过，MANUAL也适用。
- `ScoreItemAssessor:62-69`：正常模型响应但空分/越界时可写PENDING + FRESH。
- `ScoreScoringVersionService:95-98`：COMPLETED + FRESH/REUSED计为有效，没有要求客观项具有数值得分；OCR代码升级不改变文件或规则指纹。
- 生产项目581目前有3条数字、9条PENDING/FRESH空分，任务199为SCORING_VERSION_V1/COMPLETED；未进一步验证其所有当前规则哈希是否满足SKIPPED，不宣称该项目已实测重试跳过。
- 模型请求抛异常则FAILED，不属于此跳过路径。主观项按产品契约本来不自动给数字，不能将所有空分视为故障。
- 修复方向：人工重评允许绕过复用；复用判据区分主观待评与客观评分失败，并考虑提取策略版本。

### Minor：完成提示不等于出分

- `useScoreParseDrawer.js:175-186` 按任务COMPLETED提示“AI实际打分完成”，没有核验本轮客观项有效评分数量。
- 影响：任务正常返回但所有项待确认时，用户仍看到完成提示。

## 调用链与变更边界

按钮 → POST scoring（MANUAL/ALL） → 文件查找/下载 → BidFileLoadingCoordinator → Sidecar评分OCR → ScoreItemAssessor/OpenAiScoreAnalyzer → ScoreScoringVersionService落库 → results接口/前端展示。

- 修改目的：解除UI锁定、读取扫描证据、缩短大文件OCR等待。
- 模块：前端抽屉、Java文件装载、Python转换；流程：正文提取与等待交互。
- 数据库/缓存/权限：这三PR无变更；接口：复用评分接口与X-Pdf-Ocr请求；第三方：既有Tesseract。
- 配置：!3018增加OCR补充时长、候选阈值、候选上限、图片长边设置；后端与Sidecar需匹配部署。
- 结构判断：评分OCR通过opt-in复用转换边界；主要未闭合契约是输入完整度与结果复用有效性。

## 验证与限制

- 执行 `PYTHONDONTWRITEBYTECODE=1 /Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python -m pytest -q -p no:cacheprovider document-converter-sidecar/tests/test_scanned_bid_ocr.py`：53 passed，9.72秒。
- 执行三PR范围 `git diff --check`：通过。
- 独立只读代理分别审查前端入口/结果与后端OCR/落库；根代理核对现场事实并裁决。
- 未跑Java构建/ArchUnit/全量测试、浏览器评分端到端，未复验586MB客户原件；不能授予完整上线验收PASS。
- 交付建议：先补证据完整度与有效重试缺口，配套发布后用1123真实原件重评，核对客观项得分、证据、落库及页面；历史空分不会因合并PR自动恢复。

参见 [[_index]]。

## 2026-11-13 修复计划

- 已形成 `specs/087-actual-bid-scoring-recovery/spec.md`、`plan.md`、`tasks.md`，HTML位于 `docs/artifacts/actual-bid-scoring-plan-v1-20261113.html`。
- 推荐完整提取、MANUAL真重评、AUTO可信复用、准确状态和逐项目恢复；复用现有任务与run_summary。
- 独立审查补齐全项原子替换的originTaskId追溯、换原件部分重评不能混分、共享deadline与请求子进程回收。
- 这是拟实施设计，未修改业务代码，未部署或重算。


## 2026-11-13 方案反向审查补充

- 087原计划需修订，详细发现见 `docs/exec-plans/active/actual-bid-scoring-recovery-plan.md` 的MCP审查段；原spec/plan/tasks未自动改写。
- 全局PARTIAL禁止所有项评分会扩大故障；按规则可唯一确定的项与依赖全卷计数/缺失判断的项须区分。
- FAILED结果可以显示，但snapshot.results仅含COMPLETED所有者；后续ITEMS重试会把未选成功项变空。应按项有效性保留。
- 业务证据不足若被标FAILED，会进入PARSE/SCORING共用的AUTO熔断；执行状态与质量要分离。
- OCR之后默认仍整卷上传convert-images；实际评分SDK默认2次重试，新增外层重试会叠加。总预算需覆盖这些既有链路。
- run_summary为TEXT；500个合成Word未读位置的紧凑JSON已84,783字节。只持久化有字节上限的摘要；不能把64MiB HTTP响应上限当数据库容量。
- 可删减：跨原件历史评分页面首轮延期，原件变化的局部重评提示ALL；Word引用复用既有SAX；进程执行复用一个容量/错误/清理边界，不能拿Future.cancel代替硬终止。
- 内存限制必须在完整像素解码前检查；缩图与finally不保证单图峰值受控。
- 来源：基线59ae5ef8f、MCP sequential-thinking、两个独立只读审查及官方Python/MySQL文档。未部署/重算；性能和原件业务验收仍未验证。


## 2026-11-13 用户授权修订v2

- 087的spec/plan/tasks/接口/数据模型/研究/验收步骤已同步v2，R01–R10映射至35项未执行任务；v1审查仅作为历史原因。
- 当前口径：按项充分证据、FAILED成功项保留、正常待确认不技术熔断；一次下载/按来源补图/临时文件最外层finally；60KiB摘要与4KiB失败兜底；跨原件局部拒绝提示ALL、历史页面延期。
- SDK重试与降级共用每项最多2次实际传输；SAX、转换入口和纯策略复用，解码前资源护栏。代表原件完整金标验收未放宽。
- 阅读版：docs/artifacts/actual-bid-scoring-plan-v2-20261113.html；v1已加替代提示。文档修订不是业务恢复证据。

- v2独立文档一致性及HTML桌面/手机/主题/打印检查通过；已补齐旧来源过期投影及createdAt起含排队1800秒口径。业务验收仍未执行。

## 2026-11-13 本地实施与原件测量

- 用户授权按087 v2实施，delivery_target=working_tree。保留此前只读调查作为历史快照，当前已有未提交业务代码；未push、部署或生产重算。
- Java已实现覆盖/身份报告、MANUAL真重评、按项保留、跨原件局部拒绝、共享deadline、2次传输上限及60KiB摘要。UI区分质量/执行、合法0分/部分分、已评分/待确认，重开GET不POST。
- 1123原件SHA=e2507b959b349f952411effdbd0b004070ebe664fa9dfaddab99dcefab0b43e0；614173466字节。原件/正文/金标留Git外私有目录。
- 单路1500秒PARTIAL；4路703.073秒，树RSS1422737408字节，6624单元全处理但4失败。3张像素超限+1个EMF仍需兼容补齐，不能以“全处理”冒充COMPLETE。
- 发布配置继续4GiB，显式4路和24M像素；默认仍1路/16M。受限EMF只处理可证明完整的Unicode文本子集，未知图形语义明确失败。不增加图形平台。
- 副作用：Python新模块必须进入两份Docker COPY；共享OpenAI传输仅在评分预算激活时禁SDK重试，其他用途保持兼容；FAILED成功项复制到新任务但只保留直接来源；规则重新解析后仍须手动实际评分。
- 最终测试与独立审查及真实业务未验证项以docs/testing/actual-bid-scoring-acceptance.md、specs/087-actual-bid-scoring-recovery/tasks.md为准。完整提取仍不等于模型金标准确，生产恢复另验。

## 2026-11-13 自动审查第1轮修复

- `CertificateScoreRule.parse().supported` 会忽略已识别的证明材料条款，不能作为有效期、公章等材料条件已验证的事实。`ScoreAssessmentGuard` 的证书局部证明仅适用于无附加证明材料/证明文件条款的规则；标题不能放行未读或空OCR中的材料条件。
- 当前规则未执行的真实接口行是PENDING、outcome=UNKNOWN、无reuseKind并携带“当前规则尚未评分，请重新评分”。抽屉排除这些占位行；UNKNOWN本身不是执行证据，已执行的待确认诊断仍显示。
- RED：后端4组材料条件错误发布10分；前端composable及组件2项占位回归失败。GREEN：后端3类19项、前端4文件98项通过，失败/错误/跳过均0；前端定向ESLint及git diff --check通过。Java以JAVA_TOOL_OPTIONS预载现有Byte Buddy agent，未修改测试配置或门禁。
- 本轮只改白名单内2个实现、3个测试及这两处现有知识记录；不含提交、push、PR、部署或服务启动。真实模型、监听端口测试及DB/API/UI完整验收留主执行器verify阶段。

## 2026-11-13 原件反证后的本地根因补齐

- 最终1123全卷：759.569秒、树RSS1516191744字节，6624/6624、0失败/未读、122空OCR、COMPLETE。证明处理范围，不证明空图没有证据。
- 三技能首轮发现证书“证明材料”被预估规则解析剥离而错误满分、未评分占位DTO提示隐藏；已修。第二轮cleaner发现非ISO被空OCR全局阻断，因不得改变行为而ESCALATE。root记录阻断后接回已授权S4，不伪造通过或创建PR。
- 新有限额度proof复用金额归一与连续native表行，最高档服务端复算，模型raw/ratio不符不能洗分；定向原文行进入既有12k摘录。无通用NL引擎、无额外模型尝试、无新增OCR。
- 真正完整无empty的合法低档/0分保持。真实652固定模型工程回放9通过并拒raw8+ratio100，独立Astra五文件复审PASS；实际模型调用0、业务确认pending。最终统一验证/审查仍以本地最终工件为准，不等同部署/恢复。

- 业务确认更新：用户已明确确认652预期9分。此前pending记录为工程回放时的历史状态；真实模型/部署/DB/API/UI完整链仍未验收。

## 2026-11-13 Google 审查运行发现修复（fix 第1轮）

- SAX 表格状态改为栈，嵌套表格结束后恢复外层单元格、行和表；外层证书、内层证据及后续单元格按来源顺序保留。原生段落逐项核验进入输出；丢失记录 `SOURCE_TEXT_NOT_EMITTED`，不再仅凭枚举报告 COMPLETE。核验忽略图片占位和 Markdown 转义，内联图片两侧正文不会误报失败。
- `/convert-images` 复用 `ConversionAdmission` 和 `execute_conversion`。Java 已发送的截止头参与上传、容量和工作进程期限；到期、取消或断连复用现有进程组回收，补图不再阻塞事件循环。普通补图返回结构和非法页码的 422 错误结构保持。
- 提取报告存在失败或未读单元时，任务记录 `SCORING_EXTRACTION_FAILED`。已核验新结果与未选成功项可保留；受提取故障影响的待确认项记录失败诊断。没有可信新结果时 `replacement=null`，既有分数不被待确认占位覆盖；正常空 OCR、依据不足仍可正常完成。
- 保护：修改前 Sidecar 28 项、Java 33 项通过。RED：Sidecar 6 项复现证据丢失、补图超时/容量/回收缺口；Java 5 个技术故障场景错误传入 `failure=null`，正常依据不足对照通过。最终 Sidecar 6 文件 71 项、Java 6 类 51 项通过，失败/错误/跳过均 0。测试包含真实子进程超时/取消回收、下一请求可用、可信新分及历史成功项保留和失败计数；ASGI 内存请求不监听端口。
- 日志：`/private/tmp/scoring-fix-r1-python-final-20261113.log`、`/private/tmp/scoring-fix-r1-java-final-20261113.log`。本阶段未提交、push、创建 PR、部署或启动开发服务；监听测试、MySQL 与真实模型及 DB/API/UI 验收仍由主执行器 verify 阶段执行。

## 2026-11-13 fix 第 1 轮：超时仍保存已确认的原件身份

- 根因：`ScoreScoringExecution` 把新 hash 赋给 detached 任务，`finish` 在 deadline 到期或扫描器已置 FAILED 时提前返回。新身份未保存，读侧会回退旧任务 hash，把旧原件分数计入当前总分。
- 修复：下载并确认 hash 后，执行器先调用 `ScoreScoringVersionService.recordInput`。短事务保存 `bidContentHash` 和报告的 `input.fileHash/sourceDocumentId`，沿用项目锁、当前规则和最新任务守卫。扫描器已标超时的任务可补记身份，但不恢复执行、不发布结果。`finish` 继续拒绝超时结果写回。
- 行为证据：新增 `ScoreScoringInputIdentityTest` 位于 `ScoreScoringVersionMysqlTest.java`。6 项内存 H2 真实事务用例在旧实现上全部 RED，修复后全部 GREEN。覆盖换 B 后超时、扫描器在提取中或 hash 返回后抢先置失败、同原件 A 超时、规则变化及更新任务抢先出现。换 B 时旧数据库行保留，接口单项分数为空、标记 `SOURCE_EXPIRED`、总分为 0；迟到成功结果不能覆盖旧行。
- 最终验证：`JAVA_TOOL_OPTIONS=-javaagent:/Users/user/.m2/repository/net/bytebuddy/byte-buddy-agent/1.15.4/byte-buddy-agent-1.15.4.jar mvn -o -q -f backend/pom.xml -Dtest=ScoreScoringInputIdentityTest,ScoreScoringAppServiceTest,ScoreScoringResultsAssemblerTest,ScoringRunReportCodecTest test`，33 项通过，失败、错误、跳过均为 0。启动时加载既有 agent 用于避开沙箱禁止 JVM 自附加的限制，未修改测试配置或门禁。日志：`/private/tmp/scoring-fix-red.log`、`/private/tmp/scoring-fix-final.log`。
- 验证边界：本阶段未监听端口或启动开发服务。`ScoreScoringVersionMysqlTest.changedOriginalTimeoutKeepsRowsButNeverPublishesOldOrLateScore` 的两种 MySQL 超时场景已补充，尚未执行，交主执行器 verify 阶段核验。真实模型及 DB、API、UI 业务验收仍未完成。本阶段没有提交、push、PR、部署或流程凭证写入。
