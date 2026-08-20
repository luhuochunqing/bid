# Contract: 招标要求 LLM 结构化提取（049 第二步）

**调用方**: `BidQualityCheckAsyncExecutor`（PARSING 阶段）
**通道**: `RoutingAiProvider`（运行时配置路由；未启用时为 Noop，走降级分支）
**替代对象**: `BidQualityCheckAsyncExecutor.extractRequiredCerts` 的 ISO27001/ISO9001/CMMI 硬编码（删除）

---

## 1. 输入契约

| 项 | 内容 |
|---|---|
| 文档来源 | `InitiationTenderTextResolver.findLatestTenderDocument` 定位的招标文件，经存储分派 + Sidecar 解析的 `DocumentContent` |
| 分段策略 | 按 `PageContent` 章节（headings）/页窗口分段送入，单段不超过模型上下文预算（实施时对齐 scoreparse 既有分段参数） |
| 指令要求 | 仅从给定文本提取，不得臆造；无法确定的类别返回空数组；输出严格 JSON |

## 2. 输出契约（LLM 必须返回的 JSON 结构）

```json
{
  "scoringItems": [
    { "name": "技术方案", "maxScore": 40, "criteria": "方案完整性与针对性" }
  ],
  "mandatoryQualifications": [
    "ISO27001 信息安全管理体系认证",
    "CMMI 3 级及以上"
  ],
  "vetoClauses": [
    { "clause": "未提供投标保证金的，其投标无效", "keywords": ["投标保证金", "无效"] }
  ],
  "compositionRequirements": [
    "投标函", "法定代表人授权书", "分项报价表"
  ],
  "formatRequirements": [
    "A4 胶装", "正本一份副本四份", "逐页加盖公章"
  ]
}
```

字段约束：

- 五个顶层键必须全部存在（允许空数组），缺失键按空数组兜底解析
- `scoringItems[].maxScore`：数值，允许 null（文本未标分值时）
- `vetoClauses[].keywords`：用于第一步否决关键词语义判断的候选词
- 解析失败 / JSON 非法 → `TenderRequirements.missing()` 哨兵值（见 data-model.md §3.1）

## 3. 降级分支（禁止阻断任务）

| 场景 | 行为 |
|---|---|
| AI 未启用（Noop provider） | `TenderRequirements.missing()`；依赖项 MANUAL + `[REFERENCE_MISSING]` 并注明"AI 能力未启用" |
| LLM 调用失败 / 超时 | 同上；`log.error` 记录堆栈（对齐 Constitution VII §3 诊断标准） |
| JSON 解析失败 | 同上；保留原始响应摘要入日志 |
| 招标文件未上传 | 不发起提取；行为同上（PRD §1.1/§5.2） |

独立检查项（格式、签章、附件完整性等不依赖招标要求的项）在任何降级场景下照常执行。

## 4. 测试契约（契约测试固定输入输出）

1. 给定含评分办法/资格审查/否决条款章节的真实招标 PDF 解析文本 → 提取结果 `scoringItems` 与 `vetoClauses` 各至少 1 条，且内容与文档原文可对应
2. 给定无相关章节的文本 → 五个键均为空数组，不臆造
3. LLM 返回非法 JSON → 得 `missing()` 哨兵，不抛异常中断任务
4. Noop provider → 得 `missing()` 哨兵，比对项 MANUAL + `[REFERENCE_MISSING]`
