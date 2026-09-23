# Research: 评分解析页面证据与计分单元

## Decision 1: 用单元格物理页作为 PDF 图像选择依据

- **Decision**: Sidecar 在 PDF 单元格输出来源页，后端按来源格映射选择图像，并沿用当前图像数量/字节限额。
- **Rationale**: 续页表的 `CanonicalTable.sourcePage` 是起始页；PDF extractor 已知道 continuation row 对应的页，单一表级页码丢失该信息。
- **Alternatives considered**: 将整个 PDF 全页送模型会扩大成本并受限额截断；只按 table.sourcePage 会继续漏续页。

## Decision 2: 不增加 DOCX 转 PDF 渲染

- **Decision**: 延续 DOCX 结构化表格解析和 `gridSpan`/`vMerge`。
- **Rationale**: Sidecar 当前 render-pages 路径对 DOCX 不渲染文档页，新增转换依赖超出最小范围；DOCX 表格本身带网格和合并信息。
- **Alternatives considered**: 引入 LibreOffice/新渲染服务增加运行依赖且不能修复已有结构化来源格问题。

## Decision 3: 复核必须共享完整验证契约

- **Decision**: 初次提取及补充复核使用同一组图像、Artifact、事实校验与原文证明；只允许通过验证且覆盖严格更强的结果替换。
- **Rationale**: 纯文本重试丢弃图像，并且当前用非空判定覆盖，使结果强弱不可比较。
- **Alternatives considered**: 单靠模型自报 confidence 不足以证明来源格、页和总分闭合。

## Decision 4: 计分关系成为领域事实并持久化

- **Decision**: 使用稳定枚举 `SUM`, `SHARED_CAP`, `BEST_OF`, `MUTUALLY_EXCLUSIVE`, `UNKNOWN`，并通过一处纯领域 policy 派生有效计分单元。
- **Rationale**: total validation、原文证明、候选筛选和审核快照必须基于同一规则。瞬时字段不能满足人工复核审计。
- **Alternatives considered**: 增加独立 hierarchy/aggregation 表结构过重；不持久化会使 review snapshot 丢失模型判断；猜测关系有错误自动计分风险。

## Decision 5: 老事实 null 兼容为 SUM

- **Decision**: 新增可空迁移列，旧值留空并在 domain 边界按 SUM 解释。
- **Rationale**: 与既有校验约定兼容且不写历史数据。
- **Alternatives considered**: 全表回填 SUM 无业务价值并增加部署风险；新默认 UNKNOWN 会导致历史复核显示变化。
