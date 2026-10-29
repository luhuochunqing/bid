# 评分解析 Holdout 留出评测基准体系（ScoreParse Benchmark Suite）

> **定位与目标**：建立一套涵盖 **25 份代表性真实标书** 的留出评测基准体系（Holdout Evaluation Suite），终结“修一个标书、退化另一个标书”的逐案补丁模式。作为后续推进 Sidecar 表格网格自动平铺归一化、布局感知与统一网格契约演进的客观验收门禁与衡量标尺。

---

## 一、评测集构建原则与样本矩阵

评测集严格涵盖企业级招投标中的所有核心文件形态与表格拓扑复杂度，保证评测结果具备统计代表性。

### 1.1 样本形态覆盖矩阵（25 份核心样本）

| 分类编码 | 格式分类 | 拓扑/排版特征 | 代表项目/用例 | 核心挑战与考点 |
|---|---|---|---|---|
| **DOCX-01** | 原生 Word (DOCX) | 顶层大类纵向合并（vMerge）、子评分项单行排列 | 项目 1371（山焦物资） | 纵向合并列单元格行闭合判定、引用单元格原文保留、多项子细则抽取 |
| **DOCX-02** | 原生 Word (DOCX) | 跨页分段表格、续表无重复表头 | 项目 1384（中原油田） | 跨页同构表格合并、大纲编号连续性、表尾断点识别 |
| **DOCX-03** | 原生 Word (DOCX) | 3 列表格、分值内嵌于规则括号中 | `case-3col-embedded-bracket-weight` | 括号内嵌分值正则提取与叶子总分校验 |
| **DOCX-04** | 原生 Word (DOCX) | 规则列前置、分值列在后 | `case-docx-run-boundary-score` | XML Run 边界断词与分值匹配 |
| **DOCX-05** | 原生 Word (DOCX) | 复杂双级章节号（1.1 / 1.1.1）重新编号 | `case-dual-section-renumber` | 编号树拓扑树深度解析与序号防漂移 |
| **PDF-TXT-01** | 矢量文本 PDF | 倒数第二列规则、最后一列分值、跨页断行 | 项目 1392 / 项目 1130 | 规则列跨页物理截断首行续接、逻辑行缝合 |
| **PDF-TXT-02** | 矢量文本 PDF | 无表格线、仅靠缩进与排版空格呈现的评分细则 | `case-text-peer-scope` | 纯文本同级作用域判定、对等项归一化 |
| **PDF-TXT-03** | 矢量文本 PDF | 初审资质表与详细打分表混排在同一页 | `case-1381-pdf-preliminary-and-summary` | 资格预审（0分/合格制）与打分方案分流 |
| **PDF-TXT-04** | 矢量文本 PDF | 跨章节多表、商务表与技术表分散在不同章节 | `case-multi-table-tech-commercial` | 跨章节独立表分值聚合与总分 100 分对账 |
| **PDF-TXT-05** | 矢量文本 PDF | 单单元格内罗列 10+ 证书/业绩项（逗号或分号分隔） | `case-pdf-cert-list-single-item` | 单元格内细粒度条件拆解与多候选防歧义 |
| **PDF-TXT-06** | 矢量文本 PDF | 中英文括号、全半角标点混排、特殊序号字符（①②③） | `case-quote-glyph-ordinal-normalize` | 字符编码与统一字形归一化 |
| **PDF-OCR-01** | 扫描/图像 PDF | 倾斜扫描件、表格线断续、全页红色公章覆盖 | 项目 1130 附件（真实百度 OCR 回放） | 百度 OCR 视觉模型主干接管、单元格坐标自适应收缩 |
| **PDF-OCR-02** | 扫描/图像 PDF | 连续多页长表、表头仅在第一页出现 | `case-project-1382-pdf-real-shape` | 百度跨页续表自动拼合、逻辑行连续性保持 |
| **PDF-OCR-03** | 扫描/图像 PDF | 跨列单元格文字中含中英文空格干扰 | `case-ocr-chinese-token-spaces` | 视觉 OCR 字符间距自适应重组与精确原文 span 映射 |
| **COMPLEX-01**| 复杂复合表 | 复合分值声明（如“满分10分，每项2分，最多扣完”） | `case-1347-compound-percent` | 扣分/加分上限逻辑约束提取 |
| **COMPLEX-02**| 复杂复合表 | 公式化价格打分（含最高限价、基准价算法） | `case-1374-redundant-formula` | 主观/客观项分类、公式文本完整保真 |
| **COMPLEX-03**| 复杂复合表 | 总结行位于中间、非标准尾行位置 | `case-misaligned-summary-row` | 小计/合计行穿透过滤与防污染 |
| **COMPLEX-04**| 复杂复合表 | 声明总分非 100 分（如 80 分制、120 分制） | `case-declared-total-80` | 非百总分感知与合法性受护 |
| **COMPLEX-05**| 复杂复合表 | 附录独立小分加分项（如附加分、绿色环保分） | `case-1391-appendix-weight-closure` | 附加分与主表总分闭合拓扑关联 |
| **EXTREME-01**| 极端退化场景 | 纯文本描述、完全无网格且层级嵌套达 4 层 | `case-long-text-scoring-table` | 长文本段落式评分细则分层状态机解析 |
| **EXTREME-02**| 极端退化场景 | 单一单元格包含多项不同权重的评分条目 | `case-wrapped-item-maximum` | 共享单元格歧义防护（拒绝冒充单项规则） |
| **EXTREME-03**| 极端退化场景 | 来源文本缺失或不可读时模型生成候选 | 项目 1371 优雅降级实测用例 | 模型主张回退（`MODEL_CLAIM`）与人工复核守卫 |
| **EXTREME-04**| 极端退化场景 | 缺少评审因素名称、仅有规则长文本 | `case-generic-dimension-name` | 基于规则文本自动提取修饰语作为叶子项名称 |
| **EXTREME-05**| 极端退化场景 | Sidecar 提取流超时或单页解析失败 | `case-sidecar-stream-failure` | 优雅容错降级与错误诊断保留 |
| **EXTREME-06**| 极端退化场景 | 跨项目历史真实语料库全集重放 | `ScoreParseRealCorpusRecorderTest` | 线上万份标书特征留出回归无退化验证 |

---

## 二、四大核心金标度量指标（Four Golden Metrics）

每一轮架构重构、算法演进（包括 Sidecar 网格归一化）必须在评测集上计算以下四大指标，必须满足门禁阈值方可上线：

```
+---------------------------------------------------------------------------------+
|                       ScoreParse 黄金评测指标雷达                                |
+---------------------------------------------------------------------------------+
| 1. 叶子项精确率 & 召回率 (Precision / Recall / F1)  >= 92.0%                     |
| 2. 权重总分闭合率 (Weight Closure Rate)             >= 96.0%                     |
| 3. 证据链保真与溯源率 (Evidence Lineage Rate)       >= 95.0%                     |
| 4. 优雅降级与可采纳率 (Graceful Adoption Rate)      = 100.0% (零截断清空)        |
+---------------------------------------------------------------------------------+
```

### 2.1 叶子项精确率与召回率（Leaf Precision & Recall）

- **定义**：算法抽取的叶子评分项集合 $P$ 与人工标定的标准叶子集合 $G$ 的匹配度。
- **匹配判定**：当且仅当 $\text{dim}(p) \approx \text{dim}(g)$ 且 $|\text{weight}(p) - \text{weight}(g)| \le 0.01$ 时视为命中。
- **公式**：
  $$\text{Precision} = \frac{|P \cap G|}{|P|}, \quad \text{Recall} = \frac{|P \cap G|}{|G|}, \quad F_1 = \frac{2 \cdot \text{Precision} \cdot \text{Recall}}{\text{Precision} + \text{Recall}}$$
- **基准门禁**：评测集全集微平均 $F_1 \ge 92\%$。

### 2.2 权重总分闭合率（Weight Closure Rate）

- **定义**：最终进入生效应答状态的评分项权重之和与标书声明总分（通常为 100 分）一致的样本比例。
- **公式**：
  $$\text{ClosureRate} = \frac{\sum_{i=1}^{N} \mathbb{I}\left( \sum w_i = \text{DeclaredTotal}_i \right)}{N}$$
- **基准门禁**：标准 100 分制样本的闭合率 $\ge 96\%$。

### 2.3 证据链保真与溯源率（Evidence Lineage Rate）

- **定义**：每个评分项的 `sourceText`（标书原文）是否具备真实的来源凭证（`SourceSpan`、物理单元格引用或模型主张留痕），禁止虚构或盲目填充。
- **要求**：
  - 若属于已闭合证明行：`fieldSources.get("ruleText") == "SOURCE"`，且能在原文中 1:1 坐标高亮。
  - 若属于未闭合证明行：`fieldSources.get("ruleText") == "MODEL_CLAIM"`，并带有 `UNVERIFIED_CLOSURE` 或 `FULL_RULE_UNIT_UNRESOLVED` 诊断。
  - **严禁**：在没有证据的情况下盲目清空为 `""` 导致前端展示 `—`，也严禁冒充通过了闭合证明。
- **基准门禁**：全集有效溯源率 $\ge 95\%$。

### 2.4 优雅降级与可采纳率（Graceful Adoption Rate）

- **定义**：在面对异构排版、未完全闭合的表格时，系统不得粗暴崩溃或抹空，必须能够通过提取的名称与降级原文完整呈现给用户进行一键采纳（`ADOPT_CANDIDATES`）。
- **指标要求**：
  - 发生结构未闭合时，原文展示非空率 $= 100\%$（通过引用单元格或模型候选保真）。
  - 项名称有效提取率（避免退化为一级大类） $\ge 95\%$。
  - 采纳后落库合法率 $= 100\%$。

---

## 三、基准运行与回归防护流水线

评测体系不是死文档，而是集成在每次发布与重构门禁中的自动化可执行代码：

### 3.1 离线金标重放门禁

```bash
# 1. 运行 53+ 项全格式端到端金标重放
mvn -f backend/pom.xml -Dtest=GoldenCasesReplayTest test

# 2. 运行评分根因与全链路回归守护脚本（覆盖 DOCX/PDF/百度 OCR/优雅降级等 35+ 专项）
bash scripts/check-scoreparse-root-cause.sh
```

### 3.2 演进验证契约（Sidecar 归一化升级验收）

在后续实施“Sidecar 表格网格自动平铺归一化”阶段，必须遵循以下评测准则：
1. **基线留存**：重构前记录 25 份评测标书的当前精确率与召回率明细作为基线（Baseline）。
2. **非倒退红线（Zero-Regression）**：任何现有评测用例的 $F_1$ 值与溯源状态不得发生负向漂移。
3. **闭合率提升验证**：针对 DOCX 嵌套纵向合并与扫描 PDF 跨页长表，未闭合项比例应降低 80% 以上，实现从 `REVIEW_REQUIRED` 向 `AUTO_APPROVED` 的确定性升级。
