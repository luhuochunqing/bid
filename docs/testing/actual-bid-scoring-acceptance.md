# 087 v2 实际评分验收记录

状态：实施中，业务恢复未验收。原件、签名链接、OCR正文和模型输入不入Git。

## 基线

- 工作区：xiyu-zcode-review-ai-scoring-20261113；分支agent/zcode/review-ai-scoring-20261113。
- HEAD：59ae5ef8f417dab7a5b146e45a866e41921debf6。origin/main刷新为0ce13d7fd900c0c81a58d043772304d12230e18f，仅4个文档文件变化，评分业务代码无漂移。
- 本轮只读生产release仍为6d843c212，激活2026-11-12T11:52:44Z；此处非候选部署凭证。
- 本轮只读确认project_documents.id375属于1123、BID、Word，元数据586MB；存储为obs-direct；用户重新登录后已通过页面下载原件，保存在Git外/tmp/xiyu-087-private/。实测614173466字节，与bid_file一致；SHA-256=e2507b959b349f952411effdbd0b004070ebe664fa9dfaddab99dcefab0b43e0。
- Docker29.1.2及Maven可用；纯源码worktree未启动服务。宿主健康脚本未找到配置服务且git-wrapper检查失败，属于基线环境状态，不得伪称健康通过。

## 当前1123规则快照

本轮只读确认parseTask230，score_item641–652共12项，总权重100，客观73、主观27；当前关联score_result仍0条。最近5条任务均PARSE，最新task230。该快照证明规则身份和待评分状态，不是原件符合情况或预期实际分数。

## 已执行验证

| 检查 | 结果 | 限制 |
| --- | --- | --- |
| 原53项评分OCR回归 | 53 passed，14.07秒 | 修改前基线，不证明v2实现 |
| 生产发布/附件身份只读 | release及附件身份已刷新 | 未执行评分；原件已通过用户登录页面下载 |

## 原件验收矩阵

| 原件 | SHA/字节 | 规则金标 | 处理范围 | 耗时/RSS | DB/API/UI | 验收 |
| --- | --- | --- | --- | --- | --- | --- |
| 1123附件375 | 已测SHA/614173466字节 | 第652项已由用户确认9分，其余待核对 | 最终单次6624/6624，0失败/未读，122空OCR，COMPLETE | 759.569秒/进程树峰值1516191744字节 | 未运行候选完整链 | UNVERIFIED |
| 580 | 未获取当前原件 | 待标注 | 未运行v2 | 未测 | 未运行候选 | UNVERIFIED |
| 1117 | 未获取当前原件 | 待标注 | 未运行v2 | 未测 | 未运行候选 | UNVERIFIED |

正常原件仍需COMPLETE和预先标注客观分/引用一致。局部PARTIAL可用、HTTP成功、任务完成及单层模拟均不替代此验收。代码验证与生产恢复分别记录。

## 1123原件结构与首轮采样

- 主word/document.xml为7,175,112字节；ZIP含1,740媒体，raw SHA去重后仍1,740；媒体未压缩总量613,291,942字节。流式哈希/结构检查0.711秒，进程峰值RSS30,670,848字节。
- 基线SAX正文189,102字符，0.356秒；包含依赖加载的进程峰值RSS203,259,904字节。
- 按ZIP媒体顺序0/347/695/1043/1391/1739采样，真实Tesseract耗时0.282/1.227/0.946/0.860/1.499/3.558秒。仅用于定位OCR瓶颈，不证明全卷在1500秒内完成。
- 原件与样本、正文留私有目录，不入Git；没有执行生产评分。完整coverage、逐项人工预期与候选DB/API/UI尚待验证。

## 独立预标注

- 在运行候选模型前，依据原件响应表及当前规则预标注score_item652预期9分；具体原文/位置与依据保存在私有1123-golden-item652.json，用户现已确认预期9分。此时未执行任何真实模型评分，不以模型输出反向定义预期。
- 其余客观项需要扫描证据与条件逐项核对，不能凭目录、投标人自述或总分猜测。

## 完整原件性能反例与配置校准

| 运行 | 覆盖 | 时间 | 内存 | 结论 |
| --- | --- | --- | --- | --- |
| 单路1500秒 | PARTIAL；6624源单元、1586已处理、5038未读、1失败 | 1500.012秒 | 父进程峰值381272064字节；未测树，不作为树RSS | 性能目标失败，禁止当恢复PASS |
| 有界四路/默认16M像素 | PARTIAL；6624已处理、0未读、4失败、122空OCR；1735次唯一OCR | 总703.073秒，提取701.880秒 | 进程树峰值1422737408字节，每0.5秒采样，采样错误0 | 性能进入预算，但格式/像素兼容仍失败 |

- 6624源单元包括4810原生文本及1814图片引用；1739个被引用媒体，另1个WDP未被引用，不属于未读。4个失败为3张>16M像素图片（最大22268416）及1个EMF。
- 发布配置维持现有4GiB限制，显式4路/24000000像素；本地Docker真实HTTP+Tesseract合成PDF尾部ABC999和普通DOCX转换已通过。候选镜像需在EMF模块冻结后重建复验；宿主ARM64结果不能冒充生产amd64验收。
- 四路时原生文本已完整读取计入processed；单路历史计数在后续修复前包含已读文本，保留历史运行原报告不回写。
- 私有测量包含原件SHA、启动时源文件SHA、完整报告及正文；Git仅保存脱敏摘要。无生产SCORING、模型调用或结果改写。

## 原件失败单元修复的局部验证

- 在原件同一SHA上只选4个原失败媒体验证，不修改原件：默认16M配置为3像素超限+EMF成功，正确保持PARTIAL；24M配置4/4处理成功、0失败/未读/空OCR，3次实际Tesseract，3.451秒、树RSS峰值1393508352字节。
- EMF得到590字符，与独立预先解析文本逐字符完全一致；对应合成边界测试21项，整组Sidecar160项通过。未引入新运行模块或依赖。
- 此为单元恢复证明，不能合成为一次完整原件COMPLETE。最终单次整件复验、源文件一致性与耗时/树RSS另记本地.omc/verify-087-final.md；无该凭证时不视为通过。模型分值/引用及DB/API/UI仍未验收。

## 已接受的局部审查限制

- Minor：EMF读取Unicode文本而不渲染裁剪/字体/遮挡，空裁剪合成文本仍可被读取；COMPLETE仅为源单元执行范围，不承诺视觉可见性。1123实际有裁剪状态，直接禁止将重新造成PARTIAL。本轮沿用Word/PDF原生文本提取边界，记录限制，不添加通用EMF渲染平台；尚无原件误读的证明，逐项金标/可见引用仍是业务验收要求。
- 最终Dockerfile.cn镜像重建后，Python3.11容器内真实HTTP合成扫描PDF的ABC999与COMPLETE、普通DOCX纯文本转换均通过；容器限制4GiB、4路/24M像素，无宿主端口。首轮验收脚本遗漏协议必需deadline头，400拒绝正确；补齐X-Scoring-Deadline-Epoch-Millis后通过，无业务源码改动。

## 本地交付记录入口

本表保留阶段测量，不将局部成功覆盖历史失败。最终本地命令退出码、整件复验和审查处置分别读取工作区 `.omc/verify-087-final.md`、`.omc/review-087-final.md`、`.omc/087-progress.md`（本地忽略工件，不含客户正文）。未生成相应工件或verdict未通过即未完成对应门禁；T032的最终执行状态以这些记录为准。代码测试和提取COMPLETE不替代US4逐项金标及部署后业务恢复。

## 最终完整提取与审查阻断

- 1123同一原件在4路/24M配置下单次全卷COMPLETE：6624/6624源单元已处理，1738次唯一OCR，0 failed/unread、122 empty。总759.569秒，提取758.442秒，进程树峰值1516191744字节，采样错误0。空OCR仍需可见证据核验，不代表没有材料。
- 本地基准启动时app.py SHA0bfa09cc…，其后清理仅合并普通convert_document_stream布尔分支；实际评分入口/SAX/worker/监督AST及scoring_extraction SHA62aa7786…未变。清理后完整Python162项及重新构建的Docker真实HTTP/真OCR通过，未把不同整app SHA当作同一候选。
- 统一门禁第1轮Google发现证书材料要求被解析器剥离后错误满分（Major）及未评分占位行隐藏提示（Minor），已做RED→GREEN修正。第2轮清理确认COMPLETE+任意空OCR全局阻断非ISO项（Major），因清理职责限于行为保真而ESCALATE停止，门禁未通过；root已接回授权S4修正，不创建PR。
- 652完整规则再次只读核对：parseTask230、weight9、detail SHA f034686ce483739dc370fb6ea3d4b856eefd389c8a3e30cc853f0da07f59f59f。原文在私有1123-current-item652.json。有限单指标额度最高档证明正在实现；9分工程预期仍待业务确认，非模型验收。

- 额度工程回放首轮仍未出分：连续原生行绑定、源文本引用和服务端阈值证明成立，通用摘录却未包含末尾响应行；因此保留失败记录，未用合成GREEN冒充真实形状通过。后续仅对这个完整闭合额度规则做定向摘录输入修正，保持12k上限和quote-in-excerpt守卫。真正COMPLETE且empty0的既有合法0/部分分不被新例外阻断。

## 额度缺口修正后的工程结果

- 最终5文件有限修复已冻结，直接影响7类112项全部通过、0失败/跳过（MySQL33、既有金额调用者41）；先前已记录8项行为RED。独立Astra复审无Critical/Major/Minor。
- Git外真实652回放由同SHA原件重新native SAX refs与既有全卷正文/COMPLETE覆盖报告组合，不再执行OCR。固定模型输出9及完整原生行引用，服务端接受9；raw8+ratio100被拒，连续native行及quote-in-actual-excerpt均成立（274字符≤12k）。实际模型传输0，businessConfirmation仍pending。
- 金额归一复用既有包内纯函数；仅闭合单指标四档规则的最高档支持不完整证据例外，低档/0或未知条件不凭局部例外发布；真正COMPLETE且empty0的原有正常低档/0分保持。
- 这是修正根因的工程证据；不将固定模型回放当真实模型或UAT。前次自动门禁停止记录保留，后续新鲜整组验证与独立三技能结论以本地最终工件为准。

## 最新业务金标确认

用户明确回复“确认，预期9分”：1123第652项的业务验收预期现固定为9分，对应同SHA原件及当前规则。此前工程回放/阶段记录中的pending保留为当时状态，已由本次确认更新；实际模型调用仍为0，确认金标不等于真实模型评分或DB/API/UI验收通过。最新确认见本地087-business-golden-confirmation.json。

### PR3021 设计审查修正验证

本轮仅修复评分主链/证据补图/来源定位/会话图片复用。统一回归为 10 类 91 项，零失败、零错误、零跳过：ScoreScoringAppServiceTest、ScoreItemAssessorTest、ScoringFileSessionTest、ScoringSourceIndexTest、ScoringSourceRefTest、ScoringEvidenceCoverageTest、ScoringCreditLimitProofTest、ScoringSidecarResponseReaderTest、NativeScoringDocumentExtractorTest、ScoreDocExcerptExtractorTest。命令为 `mvn -f backend/pom.xml -Dtest=<上述逗号分隔类名> test`。独立只读源码复审通过，diff 格式检查通过。

此记录不覆盖真实MySQL/Testcontainers或现场模型评分；T030–T034状态不变。完整三技能流程凭证仍未形成，沿用用户已授权的本地review-gate例外，不能声称三技能门禁通过。
