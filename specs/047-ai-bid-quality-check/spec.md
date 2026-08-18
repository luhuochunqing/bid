# Feature Specification: AI 标书质量核查（真实文件解析、25项质量核查、证据链与抽屉详情）

**Feature Branch**: `047-ai-bid-quality-check`  
**Created**: 2026-08-18  
**Status**: Draft  
**Input**: PRD《AI 标书质量核查—抽屉详情》、链路核查问题清单（CO 标书质量核查生产化重构）

---

## 背景与目标

当前系统中「AI 标书质量核查」已具备入口按钮、权限拦截、控制器与弹窗的流程骨架，但核心核查逻辑存在严重的“POC 虚假实现”和“模型错位”：
1. **未读取真实上传文件**：当前仅读取 `project.name` 与 `project.description` 拼接字符串作为输入，未读取用户实际上传的 PDF/Word 投标文件及招标文件；
2. **25 项检查规则模型错位**：现有代码中基本信息检查与格式检查存在大量重复代码，且附件清单检查使用了自创字段（如 `performanceProof` 等），与 PRD 明确定义的 3 大类 25 项标准严重偏离；
3. **缺少证据链（Evidence）**：未能输出页码（page）、原文摘录（quote）、知识库来源（sourceLink）等关键证据信息，导致核查结论不可追溯、无法辅助人工复核；
4. **知识库未接入**：未与企业资质库、人员库、业绩库、品牌授权库等实际数据源联动校验；
5. **同步阻塞与无版本缓存**：长耗时 AI 核查走同步阻塞接口，缺少 `taskId` 异步任务流转与基于文件哈希（SHA-256）的缓存复用机制；
6. **前端 UI 偏离规范**：当前为普通对话框（Dialog）且存在 PRD 未定义的质量评分字段，需重构为右侧滑出抽屉（Drawer）并补齐二次确认与分类看板。

本需求旨在彻底消除上述 POC 遗留问题，建立生产级的 AI 标书质量核查系统。

---

## 检查项标准规范（25 项 CheckItem 唯一模型）

系统将严格固化 3 大类共 25 项核查项，每项具备唯一编码、严重级别与核查维度：

### 1. 基本信息检查（10项 - BASIC_INFO）
1. `COVER_INFO`（封面信息完整性）：项目名称、标段、投标人全称、日期等要素完整
2. `BID_LETTER_FORMAT`（投标函格式规范）：符合招标文件格式模板要求
3. `AUTHORIZATION_LETTER`（法定代表人身份证明与授权委托书）：签署、有效期、委托权限完整
4. `BUSINESS_LICENSE`（营业执照与主体资格）：统一社会信用代码、经营范围匹配
5. `QUALIFICATION_MATCH`（资质等级与范围匹配）：满足招标文件最低资质要求
6. `PRICE_CONSISTENCY`（报价金额大写与小写一致性）：大写金额与小写金额严格一致
7. `CURRENCY_UNIT`（货币单位与计价规范）：符合招标文件计价货币与单位要求
8. `SCHEDULE_RESPONSE`（工期/交货期承诺响应）：明确且不劣于招标文件要求工期
9. `SIGNATURE_SEAL`（法定代表人/委托代理人签章完整性）：关键页签名或加盖印章齐全
10. `CONTACT_INFO`（投标人联系人及联系方式）：电话、邮箱、通讯地址清晰有效

### 2. 分项合规检查（10项 - SUB_ITEM_COMPLIANCE）
11. `QUALIFICATION_VETO`（资格条件一票否决项核查）：财务、体系认证（如 ISO 等）、安全生产许可证等硬性指标
12. `FRONT_TABLE_RESPONSE`（招标前附表关键条款响应）：前附表特别强调条款逐条响应
13. `RESPONSIVENESS_REVIEW`（实质性响应条款符合性）：付款方式、质保期、验收标准等无实质性负偏离
14. `QUALIFICATION_REVIEW`（资格性评审条款响应）：企业信誉、类似项目业绩门槛
15. `SCORING_RESPONSE`（评分标准逐项对应与支撑材料）：对应技术/商务评分细则提供明确章节或材料
16. `DOCUMENT_COMPLETENESS`（投标文件组成要素完整性）：目录与正文结构无缺失分卷
17. `TEMPLATE_FORMAT`（排版与格式模板符合性）：字体、间距、表格样式规范
18. `VETO_KEYWORD`（废标/否决项敏感关键词扫描）：排查串标、转包、歧义或自相矛盾词句
19. `CLARIFICATION_RESPONSE`（补遗/澄清文件响应）：吸收最新答疑澄清文件要求
20. `COMMITMENT_SEAL`（各类承诺函签署与印章）：廉洁承诺、无违法记录承诺等盖章

### 3. 附件清单检查（5项 - ATTACHMENT_CHECKLIST）
21. `ID_AUTH_LETTER`（授权委托人身份证及社保证明）：身份证正反面及合规社保清单
22. `BID_OPENING_TABLE`（开标一览表/分项报价明细表）：分项合计与总价无计算错误
23. `DEPOSIT_RECEIPT`（投标保证金缴纳凭证/保函）：金额、账户、保函有效期符合要求
24. `CA_CERTIFICATE`（电子标 CA 数字证书签章及加密状态）：电子签章状态有效
25. `DEPOSIT_REFUND`（保证金退还信息与基本账户信息）：退款开户行、账号信息完整

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 真实文件解析与核查上下文加载 (Priority: P0)

系统能够定位当前项目已上传的最新投标文件（`ProjectDocument` 中 `category = 'BID'`）和招标文件（`category = 'TENDER'`），通过文档解析服务提取结构化内容（包含全文文本、分页 `PageContent`、图片/印章标记、目录与文件哈希 SHA-256），构建结构化核查上下文。

**Why this priority**: 核心数据输入源。若无法读取真实文件与页码，后续所有检查与证据链均为无效输出。

**Independent Test**:
- 后端单元测试与集成测试验证 `ComplianceTargetLoader` / `BidDocumentParserAdapter` 成功加载多页真实测试文档并输出 `DocumentContent(pages, text, fileHash)`。

**Acceptance Scenarios**:
1. **Given** 项目已上传 PDF 格式的投标文件（包含 50 页正文与印章），**When** 触发核查，**Then** 解析器输出包含 50 个 `PageContent` 的结构化对象，提取各页 text、headings 与文件 SHA-256 哈希值。
2. **Given** 项目未上传任何投标文件，**When** 触发核查，**Then** 系统抛出明确业务异常并提示“当前项目未上传有效的投标文件”。

---

### User Story 2 - 25 项质量核查领域模型与确定性规则执行 (Priority: P0)

系统根据 25 项标准 CheckItem 建立纯 Java 领域模型与规则策略接口（`QualityCheckStrategy`），优先对确定性规则（金额大写与小写计算一致性、货币单位、印章检测、敏感词扫描、附件清单项存在性）执行毫秒级规则匹配与页码定位。

**Why this priority**: 规则检查具备高确定性与高执行效率，是质量核查最稳固的基础防线。

**Independent Test**:
- 运行针对 25 项 CheckItem 的纯 Java 领域单测（无外部依赖，符合 FP-Java 原则），输入包含金额不一致、缺失盖章或废标关键词的样例文档，断言返回正确的 FAIL 状态与定位页码。

**Acceptance Scenarios**:
1. **Given** 投标文件第 5 页小写金额为 `¥1,000,000`，大写金额为“人民币壹佰万元整”，**When** 执行 `PRICE_CONSISTENCY` 检查，**Then** 判定为 `PASS`，证据包含对应页码与原文。
2. **Given** 投标文件第 5 页小写金额为 `¥1,000,000`，大写金额为“人民币壹仟万元整”，**When** 执行 `PRICE_CONSISTENCY` 检查，**Then** 判定为 `FAIL`，精准指出“大写金额与小写金额不一致”，并记录第 5 页原文。

---

### User Story 3 - 企业知识库注入、AI 语义联合核查与证据链生成 (Priority: P0)

核查引擎接入企业 5 大知识库（资质库 `Qualification`、人员库 `Personnel`、业绩库 `Performance`、品牌授权库 `BrandAuthorization`、仓库库 `Warehouse`），将复杂语义条款（如资格条件响应、评分项覆盖、前附表偏离）结合招标文件与投标文件上下文投递给 AI 进行深度推理，统一输出包含 `sourceType`、`page`、`quote`、`sourceLink` 的结构化 Evidence。

**Why this priority**: 解决高阶合规风险，形成“招标文件要求 ↔ 投标文件响应 ↔ 知识库储备”的闭环可审计证据链。

**Independent Test**:
- Mock/集成测试验证 AI 语义裁决器输出格式规范，且生成的 `QualityCheckResult` 中每个 CheckItem 均包含结构化 Evidence 列表。

**Acceptance Scenarios**:
1. **Given** 招标文件要求具备“ISO27001 认证”，投标文件商务部分未提及且企业资质库中无此证书，**When** 执行 `QUALIFICATION_VETO` 检查，**Then** 输出 `status = FAIL`，并在 `evidence` 数组中包含招标文件引用（第 12 页）、投标文件对比结果与企业资质库检索记录。

---

### User Story 4 - 异步长任务流转与 SHA-256 版本缓存 (Priority: P1)

由于真实文件解析与 25 项深度语义核查耗时较长（通常在 10s~40s），接口采用异步任务机制：
1. `POST /api/compliance/bid-quality/trigger` 返回 `taskId`；
2. 前端每 2 秒轮询 `GET /api/compliance/bid-quality/progress/{taskId}` 获取任务状态（`INIT` / `PARSING` / `CHECKING` / `COMPLETED` / `FAILED`）与当前进度；
3. 系统根据 `bidFileHash + tenderFileHash` 进行缓存，若文件未变更且未强制刷新，直接秒级返回缓存结果；
4. 用户在界面点击「重新核查」时，弹出二次确认对话框，确认后强制跳过缓存重新执行。

**Why this priority**: 避免 HTTP 长连接超时，提供平滑加载进度体验，大幅降低重复调用 LLM 的算力成本。

**Independent Test**:
- 验证任务创建、进度推进、状态轮询与结果持久化全流程；验证同一文件哈希二次触发直接命中缓存，加 `force=true` 触发全量重跑。

**Acceptance Scenarios**:
1. **Given** 用户触发质量核查，**When** 接口响应，**Then** 立即返回 `taskId`，后台异步任务开始按步骤推进并更新进度（0% -> 30% -> 70% -> 100%）。
2. **Given** 用户再次点击质量核查且标书文件未发生任何修改，**When** 未勾选强制重新检查，**Then** 直接返回已完成的最新缓存结果。

---

### User Story 5 - 前端抽屉 UI（QualityCheckDrawer）与交互闭环 (Priority: P1)

前端将原有的 `QualityCheckDialog.vue` 重构为右侧滑出的 `QualityCheckDrawer.vue`（Element Plus `el-drawer`，宽度 880px）：
1. **抽屉头部**：展示标书文件名、核查完成时间、整体状态徽标（`全部通过` / `存在不通过项` / `待人工确认`）、统计看板（通过数、不通过数、待确认数）；
2. **25 项分类卡片**：按「基本信息（10）」、「分项合规（10）」、「附件清单（5）」分组，支持折叠展开与状态筛选；
3. **证据链展开**：点击不通过或待确认项，展示详细结论建议、招标文件要求引用（含页码与摘录）、投标文件原文高亮（含页码）、知识库核对结果；
4. **重新核查交互**：支持右上角「重新核查」按钮，弹出二次确认弹窗防误触。

**Why this priority**: 交付给终端用户的最终视觉与交互载体，确保信息层级清晰、操作流畅。

**Independent Test**:
- Playwright E2E 测试验证点击「AI 标书质量核查」按钮后抽屉平滑滑出，正确展示统计卡片与 25 项分组，支持证据链展开与重新核查二次确认。

**Acceptance Scenarios**:
1. **Given** 用户在标书编制页面点击「AI 标书质量核查」，**When** 抽屉滑出，**Then** 顶部清晰显示统计数（如“18项通过，4项不通过，3项待确认”），各分类下展示 25 项卡片与严重等级标签。
2. **Given** 用户点击「重新核查」，**When** 弹出二次确认框并点击“确认重新核查”，**Then** 抽屉进入异步进度条轮询状态，完成后自动刷新结果。

---

## 数据模型设计 (Data Models)

### 1. 证据数据模型（QualityEvidence）
```json
{
  "sourceType": "TENDER | BID | KNOWLEDGE_BASE",
  "sourceTitle": "招标文件/第三章评标办法",
  "pageNumber": 12,
  "quote": "投标人须具备信息安全管理体系认证（ISO27001）",
  "sourceRecordId": "qual_001",
  "confidence": 0.95
}
```

### 2. 单项检查结果模型（QualityCheckItemResult）
```json
{
  "itemCode": "QUALIFICATION_VETO",
  "category": "SUB_ITEM_COMPLIANCE",
  "title": "资格条件一票否决项核查",
  "status": "FAIL",
  "severity": "CRITICAL",
  "conclusion": "未满足招标文件要求的 ISO27001 信息安全认证",
  "suggestion": "请补充企业 ISO27001 认证证书扫描件至商务资质章节",
  "evidences": [
    { ... }
  ]
}
```

### 3. 全量核查报告模型（QualityCheckReport）
```json
{
  "reportId": "qcr_987654321",
  "projectId": 1001,
  "bidDocumentId": 2001,
  "bidFileName": "某大型数智化采购项目_投标文件_v2.pdf",
  "bidFileHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "tenderFileHash": "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4",
  "overallStatus": "HAS_FAILED_ITEMS",
  "passedCount": 21,
  "failedCount": 3,
  "manualReviewCount": 1,
  "totalCount": 25,
  "checkTime": "2026-08-18T15:30:00Z",
  "items": [
    { ... }
  ]
}
```

---

## 边界与异常场景 (Edge Cases)

1. **大文件解析性能边界**：当投标文件超过 200 页或 100MB 时，解析采用分页流式处理，并在后台报告逐步生成的进度，防止 OOM。
2. **扫描件与非文字 PDF**：当 PDF 页面为纯图片且 OCR 耗时较长时，针对关键章节进行按需 OCR，并在证据链中明确标注 `[OCR识别]`。
3. **部分知识库为空**：当企业尚未录入人员库或业绩库时，涉及知识库的核查项不直接报系统异常，而是判定为 `MANUAL_REVIEW`（待人工确认）并提示“未在企业知识库中检索到相关对照数据，建议人工复核”。
4. **并发重复触发**：同一项目在核查进行中再次收到触发请求时，自动返回已在运行中的 `taskId`，避免多线程并发重复解析。

---

## 验收门禁清单 (Acceptance Gate)

- [ ] 后端通过 Flyway 迁移建立或更新核查任务与报告持久化表结构
- [ ] 25 项 CheckItem 领域模型与纯 Java 规则策略单测覆盖率 100%（FP-Java 纯核心无外部框架依赖）
- [ ] `ComplianceTargetLoader` 接入真实文件流与 `document-converter-sidecar` 解析器
- [ ] 真实生成包含 `pageNumber`、`quote`、`sourceType` 的结构化 Evidence
- [ ] 异步任务流转接口与 SHA-256 缓存验证通过
- [ ] 前端 `QualityCheckDrawer.vue` 完整呈现 25 项分组、统计卡片与证据详情
- [ ] Playwright E2E 测试真实端到端通过
