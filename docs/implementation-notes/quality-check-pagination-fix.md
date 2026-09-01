# Implementation Notes: AI 标书质量核查分页修复（agent/codex/quality-check-pagination）

> 任务：修复质量核查"完全不准"的根因——文档分页信息在 Sidecar→Java 链路中系统性丢失。
> 诊断结论见 PR 描述；本文件记录**规格之外的决定、取舍与变更**，随实现滚动更新。

## 背景诊断（已实测验证）

- Sidecar markitdown 输出的 markdown **不含 `\f` 分页符**（docx/PDF 实测均为 0），
  Java 端 `buildDocumentContent` 仅按 `\f` split → 整篇标书永远是 1 页。
- 连锁后果：DOCUMENT_COMPLETENESS 误报 FAIL（pages<3）；所有确定性证据 pageNumber=1 且
  quote 恒为页首 200 字（封面）；单页超长文档在 SemanticPageRetriever 触发 `selected.isEmpty()`
  短路全量塞入 prompt（60k 预算失效）→ 6/7 语义项 LLM 裁决失败转人工。
- 现场样本：winbid-test project/1352（张家口银行标书，134+ 页 docx）。

## 方案与取舍（spec 外决定）

1. **Sidecar 响应新增结构化 `pages` 字段，而非改 markdown 页分隔**
   - 原 plan 倾向"页间插 `\f`"，但 scoreparse（报价评分）消费同一 markdown，其跨页报价表
     合并逻辑（`_merge_split_price_tables`）依赖"页边界 = `\n\n` + 间隔≤3 空行"判定；
     插 `\f` 会破坏合并。故 markdown 保持现状，新增 `pages: [{pageNumber, text}]` 字段，
     消费方只有 compliance，互不影响。
2. **docx 不做物理分页，Java 侧"虚拟分页"兜底**
   - Word 自动分页只存在于渲染层，mammoth/markitdown 无法还原；引入 python-docx 自行解析
     分页符成本高且覆盖率低（显式分页符之外的自动分页依然不可得）。
   - 虚拟分页语义 = 按标题/段落的**内容切块**：PDF 仍是物理页（pdfplumber 天然逐页），
     docx 为章节块。`pageNumber` 语义变为"物理页或内容块序号"，对完整性判断、证据定位、
     prompt 预算三个消费方都恢复有效；代价是 docx 证据的"第 X 页"不再承诺 Word 物理页码
     （在本来就无法拿到物理页的前提下，这比现在恒为 1 准确得多）。
3. **阈值**：单页文本 > 4000 字符触发虚拟分页；单块上限 6000 字符，按段落边界再切。
   依据：投标书章节普遍 1-5k 字符；4000 保证小文档不被切碎，6000 保证单块不失控。
4. **语义检索硬截断**：修复 `selected.isEmpty()` 短路 → 超预算单页截断至 charBudget 并标注
   "（已截断）"。保留"预算内全文透传"行为不变（有既有测试锚定）。
5. **证据定位**：确定性 lane 新增 `snippetAround`（关键词命中位置为中心取窗），
   语义 lane 的 EvidenceLocator 本就是中心取窗（extractQuote），不动。
   checkCoverInfo 保留页首 snippet——封面检查的语义就是"第一页开头"。
6. **词表扩充**：BID_OPENING_TABLE 增加"投标报价表/报价一览表/投标一览表"。
   依据 1352 现场文档目录用词"投标报价表"。

## 变更清单

- Java：`SemanticPageRetriever`（硬截断）、`DocumentContent`（virtualPages 纯函数）、
  `SidecarStreamDocumentAdapter`（消费 pages + 兜底虚拟分页）、
  `BasicInfoCheckPolicy` / `SubItemAndAttachmentRules`（snippetAround + 词表）
- Sidecar：`converter.py`（convert_pdf_document：一次解析同时产出合并 markdown 与逐页 pages）、
  `app.py`（worker 统一携带 pages；/convert 响应新增 pages 字段——**仅 /convert**，
  /convert-stream 是纯文本流契约（spec 045）未动）
- 测试：Java 5 处新增 Nested/类；sidecar pytest 新增 pages 透传断言 +
  **真 PDF fixture golden 测试**（scoreparse markdown 字节级兼容锚定）

## 独立评审（S6）后的补充决定

1. **PRICE_CONSISTENCY 跨页回退**：分页恢复后，大写/小写金额可能分处两页
   （物理页或虚拟块），逐页匹配会从（可能正确的）PASS 退化为转人工。
   已增加全文回退：逐页未命中时用 `fullText()`（保留合并后 markdown，
   与分页前行为等价）再比对一次；全文命中时证据 pageNumber=null（前端
   `Number.isInteger` 过滤已有兜底，渲染"未定位"）。
2. **废标敏感词证据**：`checkVetoKeyword` 上下文同步改用 `snippetAround`，
   以命中词位置取窗。
3. **词表对齐**：`snippetAround` 词表与 `findFirstPageContaining` 对齐
   （货币项补"元/万元"，签章项补"法定代表人（签字）"）。
4. **pageNumber 防御**：Sidecar pages 中显式非法页号（≤0）钳制为 1。
5. **已知限制（记录不改码）**：BID_OPENING_TABLE 词表扩充后，`findFirstPageContaining`
   取首个命中页，"投标报价表"可能命中目录条目而非实体报价表——结论方向正确
   （PASS），仅证据精度受限；后续可考虑跳过目录块，不在本次范围。

## 风险与回滚

- scoreparse 零影响（markdown 输出字节级不变，仅新增字段）。
- 虚拟分页只影响"无 `\f` 且未返回 pages"的旧 Sidecar 兼容路径与新 docx 路径；
  PDF 走 pages 后不再落入兜底。
- 回滚单位：整 PR revert 即可，无迁移、无契约破坏（新增字段向后兼容）。
