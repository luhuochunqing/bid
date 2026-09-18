# Research: 招标文件大文件解析放行

**Feature**: `specs/065-tender-large-file-parse` | **Date**: 2026-09-18

> 本文件收敛 Phase 0 全部待澄清项。结论均来自本仓实测代码与已合入 spec（043/044/045/049），无外部假设残留。

## R1. 拒绝口径：字节 50MB → 文本量约 500 万字

**Decision**: 解析可解性判定从文件字节数改为提取后文本量，上限默认约 500 万字（`max-text-chars:5000000`，配置项）。

**Rationale**: 字节≠文本——50MB 扫描图 PDF 提取后仅几万字（AI 毫无压力，正是用户“压一下就下来”的类型），50MB 纯文字 PDF 可达上千万字（上千 chunk，串行 LLM 调用到 30min 任务超时）。字节口径把两种相反情况一刀切错；打分侧 spec 045 已用文本量口径验证 1.62GB 文件。

**Alternatives considered**: 直接把 `MAX_BYTES` 50 改 512（否决：OOM 与超时全回来）；维持 50MB 不变（否决：与 OBS 已放行的上传现实矛盾，死路依旧）。

## R2. 读取路径：复用打分侧流式四件套，不新写

**Decision**: OBS 签名 URL 下载复用 `StreamingBidFileFetcher` 自适应 spill（≤50MB 内存快路径，超即 flush `{taskId}.part` 落盘，单文件超 512MB 抛 `BidFileQuotaException`）；OnDisk 文本提取复用 `SidecarStreamTextExtractor`（`/convert-stream` 流式上传 + char 计数）；临时文件经 `TempBidFileStore` 用毕即释。

**Rationale**: 四件套在 spec 045/049 经 401MB–1.62GB 真实文件验证（堆峰值与文件大小解耦）；`BoundedHttpDownloader` 现骨架（ofInputStream + 8KB buf + Content-Length 预检）与 fetcher 同源，改造成 spill 是最小增量；sidecar 无需新端点。

**Alternatives considered**: 招标侧自建流式下载器（否决：与 fetcher 重复，违 Constitution VIII）；整包读入 + 提高堆内存（否决：容器 4g 上限，并发两任务即回 OOM）。

## R3. 配置键：新增 `tender-file.*`，不复用 `bid-file.*`

**Decision**: 新增 `app.score-parse.tender-file.max-file-bytes`（默认 512MB）、`app.score-parse.tender-file.max-text-chars`（默认 500 万字）、`ram-buffer-bytes`（默认 50MB），`application.yml` +3 行。

**Rationale**: 两链路调参互踩风险——打分侧调大文本上限不应自动放宽招标侧；spec 045 FR-005 明文要求上限以配置项存在。

**Alternatives considered**: 复用 `bid-file.*` 键（否决：耦合两链路运维）；硬编码常量（否决：违 FR-002 与 Constitution 配置化要求）。

## R4. 超限语义：新建 `OversizedTenderTextException`，复用 `BidFileQuotaException`

**Decision**: 文本量超限新建招标侧异常（MESSAGE="招标文件文本量超过上限（约 500 万字），无法解析，请检查是否误传或拆分文档"）；配额超限（单文件 >512MB/并发总量超限）直接复用 `BidFileQuotaException`。

**Rationale**: 打分侧 `OversizedBidTextException` 文案主语是“投标文件…无法完成打分”，招标侧照搬会误导（抽屉标题是招标文件解析）；语义分类沿用 045 契约（业务类可自救 vs 资源类联系管理员），前后端映射一致。

**Alternatives considered**: 复用 `OversizedBidTextException`（否决：文案主语错误）；复用旧 `TOO_LARGE_MESSAGE` 字节文案（否决：与新口径矛盾，用户无法区分字节/文本）。

## R5. `ScoreParseAppService` 295/300 行天花板：拆纯函数

**Decision**: 将 `rejectUnavailable` 抽为包内纯函数（输入 reason/source → 输出动作描述或直接由调用方落 task），`triggerParseInternal` 与 `doExecuteParse` 只透传新异常 MESSAGE，净增约 +2 行。

**Rationale**: 文件现 295 行，硬上限 300（Constitution IV 棘轮门禁，CI 拒绝破线）；拒绝语义本身是无框架纯逻辑，符合 FP-Java 拆分方向，且可单测。

**Alternatives considered**: 原方法内加分支（否决：必破 300）；新建 Service 类（否决：过度工程，单一纯函数足够）。

## R6. 文案口径：三处分工，不统一成一句

**Decision**: 上传配额拦截沿用上传侧口径（对标 `UploadValidationPolicy`“项目文档大小超过 50MB 限制”家族，C1 用配额值）；解析文本超限用 R4 新文案；抽屉失败态主句透传后端、次行给动作（回立项替换/锁态联系管理员）；「重试」统一为「重新解析」。

**Rationale**: 上传超限（字节配额）与解析超限（文本量）是两种失败，用户下一步动作不同（一处是压缩重传，一处是拆分/替换/找管理员），统一成一句会丢动作信息；PRD 5.3 原文保留给上传侧，解析侧用新文本量文案。

**Alternatives considered**: 全链路统一 PRD 5.3 一句（否决：解析侧丢“文本量”关键信息）；后端不动、只改前端文案（否决：治标，50MB 字节拒解仍在）。

## R7. TypeSafe：Phase 2，不进本期

**Decision**: chunk 相关性重排（Noul rerank）与抽取验证级联（SDE cascade）记附录 A，Phase 2 实施；前置条件为安全评审 + API 密钥 + 自有标书阈值校准。

**Rationale**: 本期瓶颈是工程口径（堆/超时/死路），AI 上下文（4k chunk vs 32–64k 窗口）本就够用；外部 vendor 引入涉及敏感标书外发，需 `cso` 安全评审；当前环境无 `TYPESAFE_API_KEY`，无法实测。

**Alternatives considered**: 本期同步引入（否决：安全未评审 + 无实测 + 扩 scope）；永久不做（否决：超大文档费用/幻觉收益明确，值得 Phase 2）。
