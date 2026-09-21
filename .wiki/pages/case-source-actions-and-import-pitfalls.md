---
title: 案例详情"查看源项目 / 标书原文"操作契约与导入数据陷阱
space: engineering
category: guide
tags: [案例库, knowledge_case, 源项目, 标书原文, blob下载, 枚举混用, 章节溯源, 伪标题, 打平文本, 章节号契约]
created: 2026-08-31
updated: 2026-09-21
health_checked: 2026-09-18
sources:
  - src/views/Knowledge/views/useBidCaseSourceActions.js
  - src/views/Knowledge/views/CaseGrid.vue
  - src/views/Project/stages/components/AiRecommendDrawer.vue
  - src/utils/download.js
  - scripts/slice_all_v9.py
  - scripts/import-case-slices-to-knowledge-case.py
  - backend/src/main/java/com/xiyu/bid/casework/application/MarkdownHeadingParser.java
  - backend/src/main/java/com/xiyu/bid/casework/application/MarkdownTitleRules.java
  - backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppService.java
  - backend/src/main/java/com/xiyu/bid/bootstrap/CaseProvenanceBackfillCommandLineRunner.java
  - src/views/Knowledge/views/components/caseDetailFormatter.js
  - backend/src/test/resources/casework/sentence-like-title-cases.json
backlinks:
  - _index
---
# 案例详情"查看源项目 / 标书原文"操作契约与导入数据陷阱

> **一句话**：案例详情两个操作按钮的共享契约收敛在 `useBidCaseSourceActions.js`
> （CaseGrid 与 AiRecommendDrawer 复用）；本页同时记录历史切片导入的两个数据陷阱
> （300 字截断 / 枚举混填），防止重导数据时复发；另记录「应答片段摘要」的三个排版与
> 章节溯源契约（打平文本还原 / 伪标题门控 / **章节号契约** / 存量回填，2026-09-21）。

## 操作契约（2026-09-03 修复后事实）

### 1. 查看源项目
- 跳转路由是 **`/project/{sourceProjectId}`**（投标项目详情）。
- 旧实现跳 `/bidding/detail/{id}`——该路由不存在，打开必空白；且 `bidding/:id`
  是标讯详情，语义也不对（源项目是投标项目不是标讯）。

### 2. 标书原文下载（三段防护 + 单标书兜底）
1. **预检未归档与原版单标书兜底**：先 `GET /api/archive/files?projectId={id}&size=1`，
   若 `totalElements > 0` 则走项目归档 ZIP 导出；
   若 `totalElements === 0`，兜底查询项目原始文件（`GET /api/projects/{id}/documents?documentCategory=BID`），
   若存在已上传的投标文件则直接下载单标书文件；皆无才提示"标书原文未归档：该项目暂无档案附件"。
2. **错误体识别**：`export-zip/{id}` 带项目成员校验（403/404），
   `responseType:'blob'` 会把错误 JSON 体也包成 blob。下载前用
   `isJsonErrorBlob()`（content-type 含 application/json）拦截并解析错误消息，
   不再落盘假 ZIP（`readJsonErrorMessage` 带 FileReader 兜底）。
3. **`res.data` 构造**：blob 下载必须用 `res.data`，`new Blob([res])` 会把
   整个 AxiosResponse 序列化成 `[object Object]`（CO-429 同款，
   ArchiveDetailDrawer 先修过，`ProjectArchive.spec.js` 有锁定用例）。

### 3. 项目阶段标书下载守卫（CO-442 对齐）
- 标书制作页（`DraftingStage.vue`）对齐后端 `ProjectDocumentDownloadService`：
  允许 `DRAFTING`（含审核子状态）与 `CLOSED`（结项后知识积累）下载；
  中间阶段（`EVALUATING` / `RESULT_PENDING` / `RETROSPECTIVE`）给出明确的“阶段只读不可下载”提示。

## 导入数据陷阱（重跑切片导入前必读）

### 陷阱 1：切片正文 300 字截断
`slice_all_v9.py` 解析 docx 时有 `full_text`，但 JSONL 原来只写
`text_preview[:300]`——导入的案例全是半截话，无实战复用价值。
修复：JSONL 增加 `text` 全文字段；导入脚本 `response_text` 取
`sl.get('text') or sl['text_preview']`（`knowledge_case.response_text` 是 TEXT 型）。

### 陷阱 2：枚举混填导致前台显示原始码
`knowledge_case.project_type`（项目类型：办公/综合/集采/工业品/其他，
见 `src/constants/projectTypes.js`）与客户类型（`LOCAL_SOE` 地方国企等）
是**两套不同枚举**。早期导入脚本在 `industry` 为空时回退取 `customer_type`，
导致前台项目类型标签查不到映射、原样显示 `LOCAL_SOE`。
修复：`effective_project_type` 仅取 `industry`，为空置 None（前台兜底显示"常规项目"）。
**通用教训：跨表灌数时字段语义归属必须按目标字段枚举校验，禁止跨枚举回退。**

## 应答片段摘要：排版与章节溯源（2026-09-21 修复后事实）

> 症状：案例库详情弹窗「应答片段摘要」塌成一大段无换行文本；「原文所在标题」显示的是
> 正文句子（"因投标文件篇幅有限，如下仅展示自 2023 年 1 月 1 日至……"），
> 而不是真实章节（应为 `13.2.1. 对接成功案例清单`）。

### 契约 1：章节标题必须过"句子型伪标题"门控
- 根因：PDF→markdown 转换器把**加粗正文句**输出成 `## 正文句子`，而
  `MarkdownHeadingParser.parseHeadingLine` 原先接受任何 `#` 前缀行作为标题，
  片段因此归属到伪章节，并写入 `knowledge_case.source_section_title`。
- 规则（`MarkdownTitleRules.isSentenceLikeTitle`）：含句读 `。！？!?；;` → 伪标题；
  长度 > 80 → 伪标题；含逗号且长度 > 30 → 伪标题；null/空白 → 不可用。
  命中即不作为章节边界，片段回落到前一个真实章节。
- **Java 与前端 `caseDetailFormatter.isSentenceLikeTitle` 是同一规则的两份实现**，
  由共享夹具 `backend/src/test/resources/casework/sentence-like-title-cases.json`
  （14 例，含 80/81、30/31 阈值边界与真/假两类）双向锚定：两侧测试共读该文件，
  任一侧单改阈值都会被契约测试拦下。**改规则必须同时改两侧 + 夹具。**

### 契约 2：打平文本按"递增行号序列"还原换行
- 根因在转换层（sidecar/markitdown，spec 050~052 领域）：表格/编号列表被压成单行，
  `1启明信息技术股份有限公司2河北省……` 之间既无换行也无 `|` 分隔。
- 前端兜底 `caseDetailFormatter.splitFlattenedNumberedRows`：候选行号 = 1~3 位数字且后紧跟中文，
  且要求 **≥2 个连续递增**（即最小 2 行表格）才切分；另加**结构判据**：每个行号（**含末行**）
  后面至少要有 4 个字的行内容，否则不算行结构。日期（`2023/1/5内网`）、
  数量（`共3个月`/`188个`）、括号内（`（2周）`）、`[2]说明`、以及**「第」引导的数字**
  （`第1章…第2章…` 是章节引用不是表格行号）等由行号正则排除；
  `第1章第2节`、`1号楼2单元` 这类数字相邻但中间没有行内容的由结构判据排除。
  **门槛从 3 行降到 2 行是有意为之**：真实库存在只有 2 行的打平表格，降门槛新增的误切面由结构判据兜住，
  两者必须同进同退。
- ⚠️ **不对整行长度设先验门槛**（2026-09-21 收口）：切分器一度带 `FLATTENED_SPLIT_MIN_LINE_LENGTH = 50`
  的"整行不够长就跳过"，它把"短的两行表格"整类漏掉——49 字的真实两行表格（`序号客户名称企业类型1启明…2河北…`）
  证据齐备却不切，加一个字符才切。是否成行**只由结构证据决定**（候选行号 + 递增序列 + 每行行内容长度），
  长度不能替代证据。代价是短句里若恰好出现"递增两位数字且各自带 ≥4 字内容"仍会被切开（如
  `分1阶段实施2阶段验收`），这是需求方在"宁可少切"与"2 行表格必须还原"之间明确选择的后者；
  降门槛与去长度门槛都必须与结构判据同进同退。
- ⚠️ 教训：结构判据（行号后至少 4 字行内容）**兜不住「第N章…第N+1章…」式引用**——
  引用之间天然隔着十几个字，距离判据必然放行；只有把「第」引导的数字在候选阶段就排除才有效。
  降门槛当天即有反例（`请参照第1章…并结合第2章…` 的 55 字正文行被切成两段），已补回归用例。
- 若后续把打平还原上移到转换层，可撤掉该启发式。

### 契约 3：「原文所在标题」只承认"章节号 + 标题文字"的**已验证章节**
- 根因：句子门（契约 1）只拦得住正文句，拦不住 PDF 转换器输出的**无编号加粗小标题**
  （`## 项目实施`）。这类标题是真实排版层级（层级树必须继续接受它，否则 `source_section_path`
  会整体错位），但没有序号可核对，不足以自证是标书章节；存量库里它同样会冒充「原文所在标题」。
- 规则（`MarkdownTitleRules.isChapterTitle`）：先过句子门，再剥掉首尾空白/分隔符噪声，要求串首是章节号
  （`第X章/节`、`1.2.3`、`1.`、`一、`、`（1）`、`①`），**且编号之后还有标题文字**——
  「章节号 + 标题文字」缺了后半段的串（`1.`、`第一章`、`（1）`、`①`、`A.`）同样不是章节，
  它指不到具体内容。**字母列表标记也不算章节号**（`A. 附录清单`、`B、服务方案`、`a. 投标函`）：
  字母枚举与正文里的字母列表从字面上无从区分，不足以自证是数值章节序号。前端同一契约
  `caseDetailFormatter.isVerifiedChapterTitle`，由共享夹具
  `backend/src/test/resources/casework/chapter-title-contract-cases.json`
  （31 例，含"只有编号没有标题文字"9 例、字母列表标记 4 例、全角空格 U+3000 边界 2 例）双向锚定。
  **改规则必须同时改两侧 + 夹具。**
- 展示口径的粒度必须与拒绝粒度一致：`extractSourceLocationText` 对 `source_section_path`
  **逐段校验**，只有整条路径都是已验证章节才展示完整路径；任一祖先是无编号排版层时，
  退回命中章节自身的标题（后端 `locate` 只把已验证章节写进 `source_section_title`）。
  这样「原文所在标题」字段内不会出现任何无章节号的文本。
- 两侧字符类必须逐字一致：Java 的 `trim()` 不剥全角空格（U+3000）、`\s` 也不含它，JS 的
  `trim()`/`\s` 都含——故两侧都显式列出 U+3000 与 U+00A0，并由夹具的边界用例钉住，
  否则会出现"后端拒绝、前端展示"式的判据漂移。
- 处置口径：**无已验证来源时显示「暂无来源标题」**（`CaseDetailDialog.vue` 的
  `.source-location-fallback` 分支）。评分项标题不再冒充「原文所在标题」——它在
  「评分项标题」字段独立展示；`responseText` 启发式（章节列表 / 详见引用 / 书名号方案名）
  已整段删除，不再从正文反推来源。
- 取舍（**已知缺口**，仅限"命中章节自身无编号"这一种）：命中的那个小节本身无编号时，
  `locate` 直接返回 null（不回退到有编号的祖先章节），即使祖先是真实章节
  （`13.2. 对接平台对接成功案例及证明材料 > 项目实施` 的片段定位为 null，前端显示「暂无来源标题」）。
  选择"宁可缺来源，不可给错来源"，与句子门同一价值取向。
  注意这与上一条不同：上一条是"命中章节有编号、祖先没有"，来源字段照写，只是展示时退回章节自身标题。

### 存量数据回填（本次未对任何库执行）
- `CaseProvenanceBackfillAppService.recomputeAll`：只取 `status=ACTIVE` 且来源标题**不是已验证章节**
  的案例（判据 = `!MarkdownTitleRules.isChapterTitle(...)`，同时覆盖空标题、句子型伪标题
  与无编号小标题），按项目分组、每项目只装载一次标书；定位成功写回
  标题/路径/序号/文档名/页码五字段，定位失败则**清空全部来源字段**（前端随即显示「暂无来源标题」）。
  清理判据覆盖**五个字段全体**：只要任一非空就整体清空，五个全空才返回 `UNCHANGED` 不写库——
  存量可能出现"标题已空、路径/页码仍残留"的部分写入状态，只看标题会把残留元数据留在库里。
  幂等可重复执行；标书装载失败按项目跳过并计入 `projectsFailed`。
- **标书不可用 ≠ 定位失败**：项目无归档 / 无 BID 文件 / 读取解析失败 / 未解析出任何章节时，
  来源无法查证，整批跳过并计入 `projectsFailed`，绝不写库——否则会把"查不到"误判成"没有来源"，
  清掉存量字段（`loadUsableBid` 三态守卫 + `BidDocument.available()`）。
- 入口：`--recompute-case-provenance=true`（`CaseProvenanceBackfillCommandLineRunner`），
  **默认不触发**；执行前应在开发环境先看 `BackfillResult` 计数与抽样数据。
- 门禁：该服务触及 `projectId`，按 `ProjectAccessGuardCoverageTest` 契约登记在
  `backend/src/test/resources/project-access-guard-baseline.txt` 并写明原因（启动期无用户上下文，
  调 `ProjectAccessScopeService` 会因缺少认证用户失败；只重算共享案例库溯源字段，不读写项目私有数据）。
  **新增 `*AppService` 会进入该扫描面。**

## 回归锁定
- `useBidCaseSourceActions.spec.js`：6 用例（路由 / 无 id / 未归档 / 错误体 / 正常下载）
- `AiRecommendDrawer.spec.js`：res.data 构造 + 未归档中止 2 用例
- `caseDetailFormatter.spec.js`：54 用例（含两份"与后端共享契约夹具逐例同判"、打平行切分与结构判据、
  短的两行表格同样还原（无整行长度先验门槛）、末行也必须带行内容、「第N章」引用不误切、
  已验证章节契约含"只有编号没有标题文字"/字母列表标记/全角空格边界、
  来源提取逐段校验与不再兜底）
- `CaseDetailDialog.spec.js`：36 用例（含「暂无来源标题」渲染分支、字母列表标记来源、评分项标题不兜底、responseText 启发式不再推断）
- `SentenceLikeTitleContractTest` + `ChapterTitleContractTest`（夹具 31 例）+ `MarkdownSourceSectionIndexTest`：两份契约夹具逐例同判、伪 `## 正文句子` 归入真实章节、无编号标题、字母列表标记与"只有编号没有标题文字"的标题均不给来源
- `CaseProvenanceBackfillAppServiceTest` + `CaseProvenanceBackfillCommandLineRunnerTest`：写回/清空/幂等/装载失败/无编号不写回 + 标题空而路径残留的整体清空 + 五字段全空不写库 + 旗标门控
