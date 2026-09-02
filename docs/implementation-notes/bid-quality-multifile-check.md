# 实现笔记：AI 标书质量核查多文件支持（多册合并）

> 任务分支：`agent/claude/bid-quality-multifile`（worktree `xiyu-claude-bid-quality-multifile`）
> 日期：2026-09-02 ｜ 流程：unified-dev-flow 小需求路径（S0 → S4 TDD → S5 → S6 → S7）

## 需求背景

spec 047/049 设定质量核查只读"最新一份"投标文件（`specs/047-ai-bid-quality-check/spec.md:65` 明确写"最新投标文件"）。真实业务中项目常按**价格册/商务册/技术册**分三份上传，旧实现只核查最后上传的一册，其余册完全不查，且报告只显示一份文件名，造成"都查过了"的假象。

## 方案（第一性原理结论）

核查引擎消费的不是"文件"而是 `DocumentContent`（fileName/fileHash/fullText/pages 四元组）。**合并发生在 Loader 边界**：`ComplianceTargetLoader` 把 N 份 BID 文件逐份取流解析后合并为单个 `DocumentContent`，25 项引擎、证据链、报告链路、前端全部零改动。

## spec 之外必须做的决策

| # | 决策 | 理由 |
|---|---|---|
| 1 | `DocumentContent.merge` 对**单册输入原样返回同一实例** | 保持既有单文件项目的缓存哈希不变（聚合哈希 ≠ 原始单文件哈希，若不 identity 则全网缓存一次性失效） |
| 2 | **册序 = id 升序**（自增主键即上传顺序） | createdAt 同秒会撞序，且单测中实体未落库时 createdAt 为 null；id 升序天然确定、null 安全 |
| 3 | **fileUrl 空白的脏记录过滤掉 + log.warn，不整体失败** | 无物理文件的记录不构成"册"；整体失败会让脏元数据升级成核查事故。trigger 对"过滤后为空"仍 422，语义不变 |
| 4 | **任一册解析失败 → 整个任务失败**，BusinessException 信息带上该册文件名 | 宁可报错不可假查：静默跳过失败册 = 用 2/3 内容出"合格"报告，复刻本次要修的假信心问题 |
| 5 | 聚合哈希 = 各册哈希按册序 `join("\|")` 后整体 SHA-256（小写 hex） | 同序确定性保证缓存命中稳定；任一册重传/增删 → 聚合哈希变 → 缓存自然失效 |
| 6 | 全文拼接用 `"\n\n"`，**不把文件名注入正文** | 文件名常含"价格/金额/日期"字样，注入正文可能误触 PRICE_CONSISTENCY 等正则规则 |
| 7 | `bid_file_name` 顿号拼接 + **超 255 截断追加"等N册"** | 列宽 255（无显式 length 的默认值），防多册长文件名把 trigger 落库打成 500 |
| 8 | **删除 `findLatestBidDocument` 的"全类别兜底"** | 旧逻辑在没有 BID 文件时会拿全类别最新文档（可能是招标文件）当投标文件核查，属假查，一并清除 |

## 改动清单

- `compliance/domain/DocumentContent.java`：新增 `merge(List<DocumentContent>)` 纯函数 + `aggregateFileHash` 私有纯函数（JDK MessageDigest，FP-Java 合规）
- `compliance/service/ComplianceTargetLoader.java`：`findLatestBidDocument` → `findAllBidDocuments`（id 升序、脏记录过滤、无兜底）；`loadBidDocument` 循环取流解析后 merge，解析失败包文件名抛 BusinessException
- `compliance/application/BidQualityCheckTaskAppService.java`：trigger 校验改列表判空；`bidFileName` 多册拼接（`joinBidFileNames`）
- 测试：新增 `DocumentContentMergeTest`（7 例）；`ComplianceTargetLoaderStorageDispatchTest` 增多册合并/顺序/失败语义/脏记录/无兜底 5 例；`BidQualityCheckTriggerPreconditionTest` 改多册 bidFileName 拼接断言；`BidQualityCheckTaskAppServiceTest` 桩更新

## 已知取舍（后续如需升级的方向）

- **页码为合并文档页码**（跨册累加），不再是单册内页码。当前证据只有 `(sourceType, pageNumber, quote)` 文本锚点、无 PDF 深链，代价为零；未来若做"点击页码跳原文"需升级为每册偏移映射
- **LLM token/耗时随总页数增长**（语义裁决按页检索）；10 分钟轮询上限需观察，超长分册场景后续可加降级策略
- `DOCUMENT_COMPLETENESS`（目录完整性）在多目录合并文本上可能退化为 MANUAL_REVIEW，不会假 PASS，可接受

## 独立评审发现与修复（S6，评审结论 NEEDS-FIX → 已修复待复核）

| 级别 | 发现 | 修复 |
|---|---|---|
| P0 | 「任一册解析失败即整体失败」生产不可达：`SidecarStreamDocumentAdapter.convertDocument` 内部 `catch (Throwable)` 全吞，失败时**静默返回空文本册**（非抛异常），原实现只用 mock 抛异常验证 = 假绿。扫描件/损坏册会被静默并入，2/3 内容出报告——恰是本功能要消灭的假查 | `loadBidDocument` 每册解析后显式校验：`fullText` 空且 `pages` 空 → 指名该册抛 BusinessException；测试改为 mock 真实失败形态（返回空册），另保留适配器意外抛异常的防御路径用例 |
| P1 | trigger 侧 255 截断被异步链路绕过：`updateFileMetadata` 会把**未截断**的合并文件名覆写回 `bid_file_name VARCHAR(255)`，长名多册任务在 PARSING 阶段 data-too-long 失败 | 截断逻辑下沉为 `DocumentContent.capMergedFileName` 共享纯函数（码点边界安全），merge 与 trigger `joinBidFileNames` 都走它，两个写点口径一致 |
| P2 | 脏记录过滤 javadoc 说"log 提示"但无日志；两侧空名兜底口径不一（loader 兜底 `bid_document.pdf`、trigger 直接跳过）；截断可能切出孤立代理项 | 均已修：过滤时 `log.warn` 指名记录；两侧统一兜底 `bid_document.pdf`；`capMergedFileName` 按码点收尾裁剪（有专门单测） |
| P2 | DB 查询异常 → 空列表 → trigger 422"请先上传"，基础设施故障被包装成用户错误 | **有意保留**（沿袭旧 `findLatestBidDocument` 的既有模式，改动会变更 trigger 对外语义；如需拆分应单独任务处理，避免本 PR 夹带行为变更） |
| P2 | 删除"全类别兜底"是有意行为变更：历史上从未打 BID 类目标的老项目从"能查（可能查错对象）"变为 422 | 方向正确，保留；需进 release note（见上"已知取舍"之外的行为变更提示） |

## 测试证据（S5，修复后终态）

- RED：先写测试，`mvn test -Dtest='...'` 编译失败（merge/findAllBidDocuments 不存在）
- GREEN：4 个目标测试类 25 例全过（修复前基线）
- 评审修复后全量回归：`mvn test -Dtest='com.xiyu.bid.compliance.**'` → **Tests run: 236, Failures: 0, Errors: 0，BUILD SUCCESS**（较基线净增 6 例：merge 截断×2、loader 空册/防御抛异常/全脏记录×2 等）
- 静态确认：`findLatestBidDocument` 全仓零残留；`DocumentContent` 无 compliance 模块外引用