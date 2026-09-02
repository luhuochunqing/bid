# 评分解析金标语料登记表（059 Phase 0）

> 本文件登记评分解析的金标夹具。仅写脱敏后的样本来源 / 描述 / 期望，**不得包含真实客户文件名、文件名 hash、内容文字或可定位数据**。
>
> 同目录下 `cases/` 子目录保存合成或人工脱敏样本（合成来源可在仓库中；真实文件只能入 `XIYU_EVAL_CORPUS_DIR` 本地路径）。

## 事故样本（待补）

| 样本代号 | 来源描述 | 失败原因 | Phase 1 期望 |
|---|---|---|---|
| 1347 | 山焦物资办公用品服务平台招标文件摘录（脱敏） | 商务部分父项重复计分 + 总分被静默归一化 | 父项与子项分开记录；总和未对齐时进入 REVIEW_REQUIRED |
| 1358 | 某省级政采办公耗材评审表（脱敏） | LLM 复述导致 detail 失去原文引用、对齐失败被误丢 | detail 必须能回溯到至少一个 cell；否则 REVIEW_REQUIRED |
| 1362 | 某医院信息化项目评分细则（脱敏） | 双重合并 + 大类与子项相加等于父项，被当成子项各自建项 | 区分 MAX_SCORE 与 AWARD；父子分别记录；只算末级事实 |

> 待具体合成夹具落地后再补：
> - 行 0、1 不再贴事故文件名，而写"事故 ID + 段落段首 8 字"脱敏锚点。

## 合成夹具（待生成）

| 代号 | 场景 | 主要校验点 |
|---|---|---|
| SYN-MERGE-V | 单列纵向合并 | cell 锚定、rowspan、continuation 不重复计分 |
| SYN-MERGE-H | 行内 gridSpan 跨列 | cell 锚定、isMergedContinue 引用起始单元格 |
| SYN-MERGE-VH | 纵横同时合并 | 复合合并覆盖 |
| SYN-CROSS-PAGE | 跨页评分表 | 页码锚点、表顺序 |
| SYN-MULTI-TABLE | 文档含多个评分表 | 选主表 + 副表不互相污染 |
| SYN-HEADER-SPAN | 表头跨列 | headerRows 推断、列语义识别 |
| SYN-NESTED-PARA | Word 表格中嵌套段落 | 段落不破坏文本证据 |

## 匹配谓词与报告格式

口径与 [scripts/eval-scoreparse](../scripts/eval-scoreparse/README.md) 一致：
- P/R/F1 计算使用去空白归一化后的 detail。
- weight 容差 ±0.5。
- 父子匹配：父 fact 不计入末级 P/R，只算叶子节点。
- evidence 覆盖率 = 至少一个 source_cell_id 可定位的事实数 / 事实总数。
- 结构失败率 = STRUCTURE_INCOMPLETE 任务数 / 任务总数。

## 状态

- 2026-09-02 新增本目录；尚未填充合成夹具。
