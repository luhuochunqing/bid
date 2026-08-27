# 057 金标夹具回放 v0 实现笔记（US3 / plan D-C）

> 记录 spec 未明说、实现时必须拍板的决策与取舍。规范本体见 `scripts/eval-scoreparse/README.md`。

## 决策清单

1. **夹具分值合计必须=100（双章节案）**：`WeightNormalizationPolicy` 会在合计偏离 100（±0.5 外）时
   等比例缩放全部分值。若两表真实感权重合计为 90，归一化会把 weight=5 缩放成 ~17，
   匹配谓词的 ±0.5 容差下 Recall 直接归零。故 case-dual 的商务(40)+技术(60) 设计成恰好 100；
   权重缺口形态则由 case-weight-gap 单独承担（见决策 3）。

2. **F1 在"归一化前"度量**：任务描述的管线顺序（…→normalize→算 P/R/F1）若按字面执行，
   归一化会整体改写分值，expected 人工真值将无法命中。最终口径：谓词作用于守卫过滤后
   （merge → ghostFilter → 生产同序 filterInvalidWeights）、归一化前的 curated 清单；
   normalize 只做行为断言（originalTotal 记录、缩放后合计=100）。README §匹配谓词第 5 条已文档化。

3. **新增了生产同序步骤 filterInvalidWeights**：任务列举的六件套流程未含此步，但生产
   `ScoreParseAppService` 在 ghost 过滤后有"weight 非空且 ∈(0,100]"过滤。缺它会导致前附表
   程序条款行（weight=null，由表格 lane 泄入——注意 `ScoreTableBlockLocator` 没有
   `MarkdownScoreSectionLocator.isProcedureNoticeTable` 那样的前附表防护，spec 改进点
   是补在 locator 层只挡正则结构通道）以 FP 身份存活拉低 Precision。测试内镜像该规则。

4. **幽灵行的构造方式**：真实触发 `GhostCandidateFilter` 规则一（dim==detail≤4 字）的管道行是
   `| 得分 | 得分 | 100 |`：解析得 dim=detail="得分"、weight=100。另在数据区重复一行原表头
   `| 序号 | 评审因素 | 评分标准 | 分值 |`（版式转换回声形态），其候选无编号、weight=null，
   由决策 3 的有效性过滤兜住。两条都在 case-weight-gap 夹具中，均不得入 expected.items。

5. **lane 计数口径**：报告 laneCounts 记 merge 前各 lane 原始候选数；
   `regex`=定位章节内的正则结构通道（Locator→Extractor），`regexFullText`=全文直抽通道
   （生产召回一），`table`=表格行直抽。全文通道会如实暴露前附表行泄漏（后被权重过滤兜住），
   属有意保留的可观测信号，不是缺陷。

6. **头注释措辞**：任务给的注释串中 "非 any 真实客户文档" 有中英混杂，落盘改为
   `非任何真实客户文档`；lessons §151/§167 引用保留（§151=跨维度重号误吞，§167=前附表污染，均可查证）。

7. **正则通道映射的自实现**：`ScoreCandidateMapper.fromCriterion` 为包级私有，测试在
   `scoreparse/golden` 包内按字段序镜像其语义（indicator 优先、source 截断 500、固定
   semanticPattern 标记），并注明与生产实现的一致性义务。若生产 Mapper 演进需同步。

8. **case2 的注写法**：「条件…得X分」语义段落写成表后注记，且确保段落不以数字+分收尾——
   全文直抽通道的 `matchNoNumberLine` 会把这类行尾当作评分项。这是合成文本的一般纪律，
   已写入 README《如何新增 case》。

## 未决问题

- nightly-full.sh 的 curl 模板基于现路由 `POST /api/projects/{projectId}/score-parse/parse`，
  但语料上传→fileUrl 获取链路在模板里仍是占位符，待第二层真正启用时补上传接口调用。
- 第二层采集后的夜间比对存储格式（lessons 编号挂接）留待首跑后再定。
