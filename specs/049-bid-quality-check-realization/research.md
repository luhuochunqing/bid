# Phase 0 Research: AI 标书质量核查实质化（049）

**Date**: 2026-08-20 | **Plan**: [plan.md](./plan.md)

> 所有 NEEDS CLARIFICATION 均已解决。研究基础：2026-08-20 基于 main（ce267ddaa）的代码级核查 + 五步路线技术锚点核实。

---

## R1. 生产文件存储如何取流（第一步核心未知）

- **Decision**: 按 `ProjectDocumentStorageType.fromFileUrl(fileUrl)` 三前缀分派取流：
  - `LOCAL_BID_AGENT`（`bid-agent://`）/ `DOC_INSIGHT`（`doc-insight://`）→ `ProjectDocumentFileStorage.load` 流式读取
  - `OBS_DIRECT`（`obs-direct:`）→ `ObsShareUrlSigner` 生成预签名 URL 后 HTTP 下载到临时文件
  - 取到物理文件后交 `SidecarStreamDocumentAdapter.convertDocument(path, fileName)` → `DocumentContent`
- **Rationale**: `ProjectDocumentDownloadService` 已实现完全相同的分派逻辑（下载场景），直接复用其分派模式，避免重写存储适配；Sidecar 已按 `\f` 输出真实 `PageContent(pageNumber, text, headings)`，分页地基现成。
- **Alternatives considered**:
  - 在 `ComplianceTargetLoader` 内自行解析三种前缀 → 拒绝：与 `ProjectDocumentDownloadService` 重复，未来存储扩展双处维护。
  - 让 Sidecar 直接拉 URL → 拒绝：Sidecar 不应持有 OBS 凭证，预签名职责属后端。
- **根因记录**: 现状 `tryResolveLocalPath` 只认 `/` 开头与 `file://`，三种生产前缀 100% 落入 `buildFallbackDocumentText` 元数据降级路径。

## R2. 招标文件从哪里来（第二步核心未知）

- **Decision**: 复用 `scoreparse/application/InitiationTenderTextResolver.findLatestTenderDocument`（按 ProjectDocument category = TENDER / TENDER_FILE 定位最新招标文件），取到 fileUrl 后走 R1 相同的存储分派 + Sidecar 解析。替代 `ComplianceTargetLoader.loadProjectTenderText` 只读 `Tender.description` 的路径。
- **Rationale**: scoreparse 模块已验证该定位逻辑在生产可用，避免在 compliance 包重复实现"找最新招标文件"。
- **Alternatives considered**:
  - 在 compliance 包内自行查询 ProjectDocument → 拒绝：与 scoreparse 重复且 category 判定细节易漂移。
  - 继续用 `Tender.description` → 拒绝：spec P0 ② 明确禁止。
- **未上传分支**: 招标文档不存在时，独立检查项照常、比对项置 MANUAL + reasonCode `REFERENCE_MISSING`（PRD §1.1/§5.2）。

## R3. 招标要求结构化提取用什么技术（第二步 LLM 契约）

- **Decision**: 经 `RoutingAiProvider` 调用 LLM，单次结构化提取（JSON 输出契约见 `contracts/tender-requirements-extraction.md`）：评分项、硬性要求（资格条件）、否决条款及关键词、组成要求、格式要求。招标文件长文本按章节分段送入，提取结果落为纯核心 record（`TenderRequirements`）。
- **Rationale**: `RoutingAiProvider` 已有运行时配置路由（含 Noop 降级），scoreparse 的 `OpenAiScoreAnalyzer` 已证明"长文档 → 结构化 JSON"链路在本仓可行。
- **Alternatives considered**:
  - 关键词规则提取（现状 `extractRequiredCerts` 只认 ISO27001/ISO9001/CMMI）→ 拒绝：spec P0 ⑤ 明确禁止。
  - 独立微调/专用抽取服务 → 拒绝：违反 Boring Proven Patterns，无规模需求。
- **失败分支**: 提取失败/AI 未启用 → 依赖招标要求的比对项置 MANUAL + `REFERENCE_MISSING`/`LOW_CONFIDENCE`，独立项照常。

## R4. 四知识库真实数据源（第三步核心未知）

- **Decision**:
  | 知识库 | 数据源 | 查询方式 |
  |---|---|---|
  | 资质库 | `QualificationRepository` | 保留现有 name contains 查询，输出结构化匹配结果 |
  | 品牌授权库 | `BrandAuthorizationRepository` | 按品牌/关键词查询授权记录（品牌、期限、范围） |
  | 人员库 | `PersonnelRepository` | 按项目要求关键词查询人员及其资质证书 |
  | 业绩库 | performance 模块 `PerformanceRepository`（`PerformanceRecordEntity`） | 替换误注入的 `WarehouseRepository`，真实业绩记录查询 |
- **Rationale**: 四库 Repository 均已存在且有真实数据；业绩库数据源在前期核查中确认为 performance 模块（`WarehouseRepository` 是误注入且从未使用）。
- **Alternatives considered**: 全文检索/ES → 拒绝：当前数据规模下 JPA 查询足够，违反 Simplicity。
- **异常分支**: 单库查询超时/异常 → 依赖该库的检查项置 MANUAL，不阻断其他项（PRD §5.1，对齐 Constitution VII 降级精神）。

## R5. Evidence 页码与原文如何真实定位（第四步核心未知）

- **Decision**: 新增纯核心 `EvidenceLocator`：输入 `DocumentContent.pages` + 关键词/章节线索，遍历 `PageContent` 返回首个命中页的 `pageNumber` 与命中上下文摘录（quote）。无法定位时 page = null（禁止填 1）。`QualityEvidence` record 扩展 source 枚举语义（qualification / brand-authorization / personnel / performance）、match 布尔，`sourceRecordId` 复用现有字段承载 recordId。
- **Rationale**: `PageContent` 已含真实页码与文本，定位逻辑是纯函数，可无框架单测；record 扩展保持向后兼容序列化（DB 中 JSON 列内容演进，无需 Flyway 迁移）。
- **Alternatives considered**:
  - 引入 PDF 坐标级定位（bbox）→ 拒绝：Sidecar 当前不输出坐标，页级定位已满足 PRD"能看到第几页 + 原文"。
  - 新建独立 Evidence 表 → 拒绝：现有 JSON 序列化足够，YAGNI。

## R6. LLM 裁决如何接入 25 项（第五步核心未知）

- **Decision**: 混合裁决架构——确定性规则 lane（18 项毫秒级）与 LLM lane（语义项）并行产出，LLM lane 输入 = 招标结构化要求（R3）+ 知识库真实数据（R4）+ 文档 Evidence（R5），输出 PASS/FAIL/MANUAL + reason（含 reasonCode）+ evidence。经 `RoutingAiProvider` 接入；进度经 `ProgressReporter` 回调并入异步任务 progress 上报（参照 scoreparse `OpenAiScoreAnalyzer` 召回 lane 范式）。
- **Rationale**: 纯 LLM 直出 25 项不可控且慢；纯规则无法覆盖语义项。混合架构是 spec §4 第五步已拍板方案。
- **Alternatives considered**:
  - 全部 25 项走 LLM → 拒绝：spec 明确禁止"LLM 一步出全部结果"。
  - 同步阻塞调用 → 拒绝：破坏 120s 轮询预算，且无进度反馈。
- **失败分支**: 单项 LLM 失败置 MANUAL + `LOW_CONFIDENCE`；AI 未启用（Noop）时语义项全部 MANUAL 并注明原因。MANUAL reasonCode 集合：EVIDENCE_INSUFFICIENT / AMBIGUOUS / LOW_CONFIDENCE / REFERENCE_MISSING / MANUAL_REVIEW_REQUIRED（PRD §3.5）。

## R7. 存储口径（Redis TTL vs DB + hash）

- **Decision**: 工作假设维持现行工程方案（DB 保存报告 + 文件版本 hash 缓存 + force=true 强制重跑）。**此项不是技术研究结论，是产品拍板项**（spec §5.2 / G6），研究阶段不阻塞，收尾合入前必须落定。
- **Rationale**: PRD §4.4"不持久化 + Redis TTL 30 分钟"与现行实现冲突；两方案取舍见 spec §5.2 表格。
- **Alternatives considered**: 见 spec §5.2 方案 A/B 对比。

## R8. 删除旧链的连带影响面

- **Decision**: 按 spec §3.2 清单一次性删除；删除前先 grep 确认三个旧规则类（`BasicInfoCheckRules`/`FormattingCheckRules`/`AttachmentCheckRules`）无保留链消费（预期仅旧 Policy 消费），`ComplianceTargetLoader` 两个旧方法删除前确认无其他引用；api-tests 中旧接口 .http 用例实施时 grep `bid-document/check` 确认。
- **Rationale**: "代码做了没人消费"是本功能复发的根因，删除必须彻底（spec 已拍板决策 2）。
- **Alternatives considered**: 保留旧 API 作向后兼容 → 拒绝：已拍板不留兼容残留。

---

**结论：无遗留 NEEDS CLARIFICATION（R7 为产品决策项，非技术未知项），满足进入 Phase 1 的前置条件。**
