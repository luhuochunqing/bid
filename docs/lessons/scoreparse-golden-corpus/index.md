# 评分解析金标语料登记表（059 Phase 0）

> 本文件登记评分解析的金标夹具。仅写脱敏后的样本来源 / 描述 / 期望，**不得包含真实客户文件名、文件名 hash、内容文字或可定位数据**。
>
> 同目录下 `cases/` 子目录保存合成或人工脱敏样本（合成来源可在仓库中；真实文件只能入 `XIYU_EVAL_CORPUS_DIR` 本地路径）。
>
> **可执行基线约定**：每个夹具的"可执行基线"列指向钉死其预期的单测。修改抽取语义前先跑基线；修改后语义有变的，必须同步回填本表并更新对应用例——登记表与测试不允许单方面漂移。

## 事故样本

| 样本代号 | 来源描述（脱敏） | 失败原因 | 预期行为 | 可执行基线 |
|---|---|---|---|---|
| 1347 | 山焦物资办公用品服务平台招标文件摘录（脱敏） | 商务部分父项重复计分 + 总分被静默归一化 | 1 父 + 1 子共 2 条事实；父项被引用 → 拦截出自动评分集；只允许末级合计 | `GoldenCorpusBaselineTest#incident_1347_parent_and_children_never_scored_together` |
| 1358 | 某省级政采办公耗材评审表（脱敏） | LLM 复述导致 detail 失去原文引用、对齐失败被误丢 | 每条事实必须携带 ≥1 个可定位 source cell + 可定位 weight evidence，否则 REVIEW_REQUIRED | `GoldenCorpusBaselineTest#incident_1358_every_fact_keeps_locatable_evidence` |
| 1362 | 某医院信息化项目评分细则（脱敏） | 大类 20 分与子项 5/5/10 被当成 4 个平级子项各自建项 | 3 条事实（1 父 + 2 叶）；自动评分只算末级合计 15；父项 20 不得与子项相加；15≠20 的缺口由 Phase 4 完整性策略兜底 REVIEW_REQUIRED | `GoldenCorpusBaselineTest#incident_1362_category_weight_not_summed_with_children` |

> 脱敏锚点说明：上表"来源描述"仅保留行业 + 文档类型；事故原文只允许存于 `XIYU_EVAL_CORPUS_DIR` 本地语料目录，禁止入仓。

## 合成夹具（可执行基线已落地）

| 代号 | 场景 | 主要校验点 | 预期末级事实数 | 权重 / 父子 | 预期决定 | 不可接受的误识别 | 可执行基线 |
|---|---|---|---|---|---|---|---|
| SYN-MERGE-V | 单列纵向合并 | cell 锚定、rowspan、continuation 不重复计分 | 2（1 父 + 1 叶） | 10+空：子项分值缺失 → 叶子 0 条可自动 | 子项 REVIEW_REQUIRED | continuation 行重复建项；父项入自动评分集 | `GoldenCorpusBaselineTest#syn_merge_v_blank_continuation_weight_is_review_required` |
| SYN-MERGE-H | 行内 gridSpan 跨列 | cell 锚定、isMergedContinue 引用起始单元格 | 1 | 10（分值列被跨列覆盖时回退标题括号分值） | AUTO_APPROVED | detail 文本重复出现；cont cell 进入 source cell | `GoldenCorpusBaselineTest#syn_merge_h_gridspan_detail_extracted_once`；结构层 `test_extract_tables_v2.py::TestMergedRegions::test_gridspan_recorded` |
| SYN-MERGE-VH | 纵横同时合并 | 复合合并覆盖 | 2（1 父 + 1 叶） | 父 20（标题）+ 子 10（显式列）；子挂父 | 父拦截 / 叶可自动 | 延续行证据引用父项行 cell | `GoldenCorpusBaselineTest#syn_merge_vh_combined_span_parent_child_linkage`；结构层 `test_combined_vh_merge` |
| SYN-CROSS-PAGE | 跨页评分表 | 页码锚点、表顺序 | — | — | — | — | **缺口**：Sidecar v2 契约 `sourcePage` 目前恒为 `None`，页码锚点需真实语料标定 → 挂 Phase 6（T601）；表顺序由 `test_multiple_tables_have_distinct_ids` + SYN-MULTI-TABLE 代管 |
| SYN-MULTI-TABLE | 文档含多个评分表 | 选主表 + 副表不互相污染 | 每表 1 | 权重按表内列解析 | 各表事实独立 AUTO_APPROVED | 跨表 factId 冲突；跨表挂父子 | `GoldenCorpusBaselineTest#syn_multi_table_facts_do_not_cross_contaminate`；`test_multiple_tables_have_distinct_ids` |
| SYN-HEADER-SPAN | 表头跨列 | headerRows 推断、列语义识别 | 1 | 5（显式列） | AUTO_APPROVED | 表头 cell/文本泄漏进事实证据 | `GoldenCorpusBaselineTest#syn_header_span_does_not_leak_into_facts` |
| SYN-NESTED-PARA | Word 表格中嵌套段落 | 段落不破坏文本证据 | — | — | — | 第二段文本丢失导致证据链断裂 | `test_extract_tables_v2.py::TestCellMetadata::test_nested_paragraph_cell_text_concatenated`；事实层由 1358 基线代管 |

### 列语义与列坐标口径（夹具统一约定）

- 列语义：编号=0、评分因素=1、评分标准=2、分值=3，表头 1 行（`TableColumnMapping(0, -1, 1, 2, 3, 1, false)`）。
- 事实 source cell 只允许包含本行 origin cell，禁止包含 merged continuation 或他行 cell。
- 每条事实的 `weightEvidence.cellId` 必须能在同表内定位。

## 匹配谓词与报告格式

口径与 [scripts/eval-scoreparse](../../../scripts/eval-scoreparse/README.md) 一致：
- P/R/F1 计算使用去空白归一化后的 detail。
- weight 容差 ±0.5。
- 父子匹配：父 fact 不计入末级 P/R，只算叶子节点。
- evidence 覆盖率 = 至少一个 source_cell_id 可定位的事实数 / 事实总数。
- 结构失败率 = STRUCTURE_INCOMPLETE 任务数 / 任务总数。

## 已知缺口（不阻塞 Phase 0-2 合入，挂账跟踪）

1. **双轨回放脚本**（`scripts/scoreparse-dual-replay.sh`）属 Phase 5 T505，落地前"只读回放"由上述单测基线代行。
2. **SYN-CROSS-PAGE 页码锚点**：等真实脱敏语料标定 `sourcePage` 后补齐（Phase 6 T601）。
3. **真实语料 nightly 评估**：Phase 6 T603 CI/nightly 接入后才有 lane/复核率报告。

## 状态

- 2026-09-02 新增本目录；当时仅空白模板。
- 2026-09-03 评审修复：事故 1347/1358/1362 + 5 类合成夹具全部登记预期并挂接可执行基线（`GoldenCorpusBaselineTest` 8 例 + Python 结构测试）；缺口显式挂账见上节。
