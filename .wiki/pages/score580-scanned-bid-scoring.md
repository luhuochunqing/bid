---
title: 项目580扫描投标PDF实际评分与预估边界
space: engineering
category: lesson
tags: [scoreparse, OCR, 扫描件, 实际评分, 预估]
sources:
  - docs/lessons/lessons-learned.md
  - specs/20261111-scanned-bid-scoring/spec.md
  - document-converter-sidecar/converter.py
  - document-converter-sidecar/scoring_extraction.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SalesRevenueScoreProof.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoringSourceIndex.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringExecution.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/BidFileLoadingCoordinator.java
backlinks:
  - _index
  - score-parse-service
created: 2026-11-11
updated: 2026-11-17
health_checked: 2026-11-17
---
# 项目580扫描投标PDF实际评分与预估边界

## 历史机制（2026-11-13，当前生产机制见后节）

- 生产任务d4c579d0，release2960e7ea5：177MB下载到Sidecar后200空正文（chars0、low_text_density），extractOnDisk:140终止，AI未开始。
- 评分入口的 PDF OCR 必须覆盖内存/磁盘两种文件路径。表格 OCR 的 30 页样本不用于评分。候选不超过 40 时不另设页数上限。
- X-Pdf-Ocr:true 为评分专用 opt-in；默认转换不变。PDF：有图或无原生文字的页送现有 Tesseract；单页识别为空保留该页已有文字，整卷无文字才失败。引擎缺失或非零退出整卷失败。已经有正文时，识别超时交回正文，不再整卷作废；完全没有正文时超时仍失败。超过 40 个候选时，已有正文只光栅化 12 页，没有正文时 24 页；先选没有原生文字的页，同组再选嵌入图更大的页。跳过的页仍交回原生正文。送识别前长边缩到约 2000 像素。
- DOCX 走同一评分入口。原生文字保留。嵌入图片按体积从大到小识别，超过 40 张时只打开上述张数，其余媒体留在压缩包里，不解码。有正文后默认再识别 180 秒就停下，避免上千张图吃掉 30 分钟打分窗口。不按像素尺寸跳过小型证据截图；透明图（RGBA/LA/调色板透明度）先合成白底再识别，避免透明黑底变成全黑；图标识别为空可继续，整卷无文字才失败。引擎缺失或非零退出仍然整卷失败。超时且已有正文则交回正文。
- 旧版 .doc 内存文件保留原 textExtractor → DocInsight → POI HWPF 回退；原磁盘分流不变。Linux Sidecar 尚无已验证的 DOC 转 DOCX 工具，不宣称扫描 .doc 已支持 OCR。
- Java安全错误映射只认BID_OCR_UNAVAILABLE/TIMEOUT/EMPTY；任务和逐项missedReason同源。
- 预估5项主观+4项客观不支持自动匹配：显示待专家评审/需人工评估；不是零分，也不是实际评分失败导致。
- 跨模块：Casework/Compliance/Tender其他extract调用仍保留原契约。评分 HTTP 1800 秒；无正文时 OCR 上限 1500 秒，已有正文后再识别 180 秒。Sidecar和后端需配套发布。
- 交付界限：仅本地修复与合成真实OCR验证；未部署、未生产重算，177MB全卷时延待验收。


## 2026-11-16 生产评分失败：25分钟OCR预算耗尽

- 范围：只读取生产任务/API、systemd和Sidecar日志；未修改业务代码、未部署、未重评分。
- 生产发布记录：`df970b534`；运行中Sidecar的`/app/scoring_extraction.py` SHA256与该提交相同（`08e431450b929e4b5952debc6f744ca8b483d207788000d02ff71c75162df41e`）。
- 任务：`b5252e88-ca71-412d-a8d7-80ac6d337908`，13:28:30开始、13:55:23结束（CST），FAILED/95%/FULL，`SCORING_EXTRACTION_FAILED`。
- 输入：1225页PDF，185837065字节。Sidecar转换耗时1501581ms，返回398294字符及HTTP200；覆盖回执耗时1500013ms、processed843、failed1、unread382、empty4、PARTIAL。第843页`BID_OCR_TIMEOUT`，第844至1225页未读。HTTP200不代表原件完整读取。
- 当前机制：`_Coverage`将OCR预算硬限制为`min(1500, configuredSeconds)`；`_pdf`按物理页串行读取/OCR。单独调大`SIDECAR_BID_OCR_TIMEOUT_SECONDS`不能越过25分钟上限。旧节的候选页截断机制不能用于解释本次执行。
- 因果链：OCR预算耗尽→PARTIAL正文继续模型评分及补图→客观分证据校验拒绝→逐项被标记EXTRACT_FAILED→整任务失败；5个主观项保留建议，4个客观项未出分。
- 生产日志trace `bd75818a0cf445eab64fcaa3f4fb816c`：销售额654两次rawScore15且原文命中，拒绝原因`COVERAGE_PROOF_INSUFFICIENT`；信用额度657补图后rawScore5但`QUOTE_NOT_IN_EXCERPT`；免运费658缺引用/引用过短；折扣661为`COVERAGE_PROOF_INSUFFICIENT`。原始模型分不能作为业务验收分。
- 跨模块副作用：`ScoreScoringExecution`在提取不完整且无可信分时覆盖具体引用拒绝理由，统一显示原件提取故障；不能仅凭该文案断定模型调用没有发生。
- 证据边界：已证实预算耗尽与发布拦截；未对客户原件重跑、未做CPU争用/吞吐受控实验，未证明某个性能修复有效。下一步修复需同时验证完整覆盖、端到端预算及逐项有效证据；不能靠放宽校验宣称恢复。


## 2026-11-16 MCP方案反向审查

- 结论：原三点建议只能作为候选方案，尚不能证明项目580评分恢复；未实施、未部署、未重评。
- 阻断1：并行OCR没有同原件吞吐与峰值内存证据。现场14:19快照为主机8核、约16GiB，容器上限4GiB、无CPU硬配额；当时空闲状态不能证明评分高峰性能。约36分钟整卷串行耗时仅为前843页速度外推，不是实测。
- 阻断2：回执已有4个识别为空单元（54/75/445/749页）。`ScoreItemAssessor`要求完整覆盖且emptyUnitCount为0，或有受支持的单项局部证明；因此清除unread并不保证销售额可以出分。识别为空不等于真实白页，需查原页内容与识别输出；不得直接忽略empty计数。
- 阻断3：信用额度QUOTE_NOT_IN_EXCERPT未证明由页码错位造成。现有`CreditLimitScoreProof`仅匹配特定四档规则并要求Word原生表格，不能作为本项目PDF三档“赊销总额度”规则已受支持的依据。需核验实际节选、关联图片、模型引用及最终守卫分支。
- 错误文案改进只影响可解释性，不直接恢复数值评分。
- 验收补充：同一1225页原件、相同规则，核实未读/失败页清零、empty单元真实分类、总耗时和内存符合目标环境预算；四客观项逐项对原文与业务预期核对，证据不足保持明确人工确认；同一任务数据库/API/UI一致。现场恢复必须在部署并获授权重评后确认。


## 2026-11-16 本地候选修复与原件回放

- 分支`agent/codex/score580-ocr-proof`、基线`eddf17831`，仅工作区交付。候选未提交、推送、部署或线上重评分。
- 原件185837065字节、1225页，SHA256与生产任务一致：`f013508c600fe42b553359f12dd2b88bf6938442208b298f0e1f108f72cfdb44`。客户原件、OCR正文与输入输出均留在Git外。
- PDF调用线程串行读取/渲染，独立Tesseract进程有界并行（沿用SIDECAR_BID_OCR_WORKERS，1..4；PDF缺省2）。不在多个线程共享PyMuPDF对象；窗口内保留编码PNG，完整物理范围不截断。
- 同原件本地4路回放：444.29秒，1225/1225处理，未读0、技术失败0，父进程峰值RSS420659200字节；此值不含并发Tesseract子进程，不能当容器总内存峰值。空单元5页为54/75/445/749/999，警告保留。
- 同机同输入65页对照：旧串行94.25秒、新4路26.27秒，约3.59倍；这是本地对照，不能外推为生产提速或1800秒端到端已验收。
- empty图页有设备照片、淡字/印章和商品截图，PSM11输出噪声不能用来清空empty计数。销售额新增狭义最高档证明：完整连续单调年度平台工业品销售额分档，最高档==权重，原始/最终模型分一致，引用含年度/指标/金额/亿元且唯一绑定非失败来源，已识别同指标声明无矛盾。附加条件/未知语法/低档/缺失0分不适用。
- 用同原件、当前653..661规则和生产已保存的15分/引用做离线主链回放，销售额可返回15且empty5保留；实际模型调用数为0，不代表真实模型/UAT通过。
- 召回修复：长中文维度允许弱4字片段召回，仍保留逐段原文偏移；只影响检索，不作为评分事实。原件免运费命中818页，但旧补图按页码取前三张仍丢弃818；补图改为复用同一相关度排序，最终张数/字节/缓存预算不变。
- 保留细项引用拒绝原因，不再被提取故障通用文案覆盖；既有质量摘要增加处理/未读/失败/识别为空计数。
- 信用额度/免运费第818页盖章遮挡导致OCR文字不可靠，折扣率所需报价附件是否齐全未验证；这些项目不得因为技术任务可结束就被强制填数字。需要真实模型/实际证据验收后才能宣称四项恢复。

- 第6页原件OCR还包含针对所有订单无最低起订金额限制、无配送区域限制、全程免费配送的完整响应。新增FreeDeliveryScoreProof只覆盖该闭合规则的最高档正向证明；新选图优先保留相关响应，第818页不再作为唯一验收页。免费配送10分的固定输出原件回放仅证明服务端核验路径，不能冒充原生产模型已返回10分。


## 2026-11-17 10:48 只读复核：历史失败与新任务分开

- 已证实：PARSE233（508b7d42）2026-11-12 20:12:28完成。SCORING243（b5252e88）2026-11-16 13:55:23失败，error_message=SCORING_EXTRACTION_FAILED。其覆盖回执仍为1225页/843已处理/382未读/1超时，日志trace bd75818a0cf445eab64fcaa3f4fb816c证实覆盖和引用拒绝。因此该错误属于实际评分，而非评分标准解析失败。
- 当前生产：release 5cbbf4925，2026-11-17 08:31:49 CST激活。容器/app/scoring_extraction.py SHA256=403ba9d576013e00bf2cd9043454ba653f5c8991b4141b0724862ea8f103af28，与该版本源码一致。容器快照可见4个Tesseract进程；仅证明执行活动，不证明完整读取或评分成功。
- 新任务SCORING245（c3bf4a83-13f7-49e7-b38a-7a897a6b84b0）10:39:19开始，10:47:57状态PROCESSING/5%/读取投标原件，尚未结束；原件哈希与旧任务一致。该请求早于本轮取证，由MANUAL请求启动，本轮未触发解析或打分。
- 跨模块影响：新任务未完成期间，results仍可返回originTaskId=243的旧结果；不能把这些历史EXTRACT_FAILED行判为245再次失败，也不能因新代码已部署便宣布恢复。
- 待验证：245终态、完整覆盖与四客观项数据库/API/UI一致性；本轮不改生产数据、不部署、不重评分。证据留在Git外 /tmp/xiyu-580-diagnose-20261117/（SQL、API、日志、诊断记录）。


## 2026-11-17 重开抽屉重复弹错与技术码暴露

- 直接路径：useScoreParseDrawer.open → useActualScoring.restoreObservation → 读取历史FAILED → showFailure → notifyErrorUnlessRateLimit → ElMessage.error。恢复历史状态和当前操作失败共用弹错函数，故每次重开都会重新通知，并不代表每次打开都执行了新评分。
- 文案漏洞：notifyErrorUnlessRateLimit优先采用error.message，只有没有message时才用中文fallback。SCORING_EXTRACTION_FAILED既不为空，又未映射，所以原样进入toast及scoringHint（含任务UUID）。
- 已有getAiDocumentErrorPresentation只用于drawer.error等展示，实际评分失败走独立弹窗路径，未调用该映射；其未知错误分支也会返回raw，不能构成统一兜底。
- 逃逸证据：现有重开FAILED测试只断言不POST，没有断言不重复toast、不暴露技术码；扫描件友好文案测试只覆盖扫描关键词及普通中文，未覆盖未知技术错误码。近期8b1b5696a只改后端覆盖缺口汇总及相邻后端测试，未改上述前端历史恢复路径。
- 隔离执行：保留当前useActualScoring和error-utils函数体，仅替换Vue/网络/消息依赖，注入同一历史FAILED响应，连续调用restoreObservation两次，实际收集两次SCORING_EXTRACTION_FAILED通知；未请求生产写接口。证据 /tmp/xiyu-580-diagnose-20261117/page-reentry-replay.json。该实验验证函数链，不冒充真实浏览器新任务复现。
- 修复边界（尚未实施）：历史失败仅在对应实际打分区域展示带时间的状态；用户本次操作失败才做即时反馈；评分状态、轮询终态、提交拒绝和未知技术错误经过一致的用户语义映射，原码及UUID留诊断详情；补齐重开、首次失败、刷新及未知码回归。不能通过删除历史任务或只替换某条后端文案关闭问题。


## 2026-11-17 本地交互修复（未部署）

- useActualScoring恢复历史状态及恢复中的任务终态失败只更新页内提示；主动打分/提交失败才即时通知；评分读写请求的HTTP错误由抽屉统一处理，限流也只在主动操作时给一次中文反馈。
- aiDocumentError统一实际评分错误映射；未知技术错误中文兜底；保留可理解的中文业务拒绝原因，OCR超时不再被解释为不支持扫描件。UUID保留后端诊断，不进入评分用户提示。
- 后续COMPLETED/无任务读取清理上次失败提示；评分状态使用role=status，通知消失后解释仍可阅读。
- 代码位于隔离分支；测试与独立审查结果以本轮执行证据为准，尚不宣称全部通过。不删除生产历史记录、不触发重评、不宣称现场恢复。
