---
title: PR2948评分前序漏扫修复范围与fallback证据退化
space: engineering
category: reference
tags: [scoreparse, ocr, review, fallback]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseEntryRouter.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/baidu/BaiduTableArtifactEnricher.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseEntryRouterTest.java
backlinks:
  - .wiki/pages/_index.md
created: 2026-11-01
updated: 2026-11-01
health_checked: 2026-11-01
archive: false
---

# PR2948评分前序漏扫修复范围与fallback证据退化

- 审查对象：[Gitee PR2948](https://gitee.com/allinai888/bid-xiyu/pulls/2948)，head `918e8d4e97fa0fa89a74d64c115e3b6fb590e485`，base `ff740d61a518040dac09f6603b0ec07133f5b9c1`。
- 历史结论（918e8d4）：修复同一连续候选簇中的前序页硬截断，但存在区间漏扫与fallback退化。下方6cb3739复审结论覆盖其已修复状态。
- 验证方式：直接编译PR的BaiduScanPageSelector/ScoreParseEntryRouter，依赖相同未修改基线的实际编译类；纯Java合成边界输入，无外部API调用，无生产写入。不是该PR全模块回归或真实文件准确性验收。
- 临时证据目录：`/tmp/xiyu-pr2948-review`（临时文件非永久证据；主要输入/实际输出保留于下表）。未向Gitee提交审查评论，未修改PR业务代码。

## 改动理解

| 项目 | 内容 |
|---|---|
| 目的 | 前序候选页补扫；欠分时允许fallback |
| 模块 | 百度扫描页选择、评分入口路由 |
| 流程 | 单向起点改选最大重合簇；facts非空短路改欠分仲裁 |
| 数据库 | 无 |
| 缓存 | 无 |
| 接口 | 无外部接口结构改动 |
| 配置 | 无，仍固定最多10页 |
| 权限 | 无 |
| 第三方 | 百度OCR调用页集合变化；联调测试增加无表格时abort |

调用链：ScoreParseAppService → StructuredTableExtractionHandler → artifact acquisition → BaiduTableArtifactEnricher → BaiduScanPageSelector → 百度表格API → artifact/facts → ScoreParseEntryRouter仲裁 → versioned/legacy完成路径 → 候选/正式项持久化及读取。路由本身不直接修改正式项，不推断所有失败fallback都能绕过后续验证。

## 实际复现

| 场景/约束 | 输入 | PR实际结果 | 判定 |
|---|---|---|---|
| 同簇前序页不漏 | 锚点40,41；候选3,4,9,10,39–42 | 39–42 | 本例修复有效 |
| 多个评分区间不遗漏 | 锚点20,21,40,41；候选同四页 | 仅20,21，漏40,41 | CONFIRMED，最大重合单簇丢弃另一个有效区间 |
| 长表不被截断 | 连续20–34；锚点20,34 | 仅20–29，漏30–34（包含已知锚点34） | CONFIRMED，10页上限仍存在 |
| 已连通区间中间页不漏 | 锚点20,23；候选20,21,23,24 | 漏22 | CONFIRMED，差值≤2聚类未补齐中间物理页 |
| fallback不可降低已有证据覆盖 | 主结果48分facts且漏项诊断，fallback20分facts；两者都未完成100分 | 选择20分，漏项诊断从有变空 | CONFIRMED，selectBest只检查非空，不证明更完整/更准确 |

## 问题定位与副作用

- `BaiduScanPageSelector` PR行61–92仅选择一个bestCluster；行110继续limit，Enricher行39仍MAX_PAGES_TO_SCAN=10。候选本身仍依赖PDFBox文本关键词，扫描PDF/无关键词页获取障碍未改。
- `ScoreParseEntryRouter` PR行47–61：fallback只要有facts或候选，即可能整体替换欠分主结果；未比较覆盖、权重完整性、来源验证或合并保留原诊断。跨模块副作用是后续完成/复核路径只收到替换后的结果，原诊断丢失；不等于已证明错误正式项落库。
- 两个联调测试新增`enriched==baseline`即Assumptions.abort。该状态可来自无关键词、未启用OCR、无表格或异常，不独占API错误17；因此测试跳过不能证明额度问题，更不能作为准确性通过。

## 与此前验收失败的映射

| 问题 | PR覆盖情况 |
|---|---|
| 已在同簇candidatePages中的前序评分页未调用OCR | 条件性修复，已跑正例 |
| 已识别单元格的跨页续文归属/文字丢失 | 未修改转换/逻辑行恢复实现；多页获取仍可能遗漏 |
| 正确OCR片段未被最终输出保留 | 未涉及读数协调/字段映射 |
| 页脚原始总分冲突漏校验 | 未涉及 |
| 0项显示混淆及零facts原因不展示 | 没有前端/API展示改动 |
| OCR原始字符错误 | 未涉及 |

## 验证边界与裁决

- 读过全部生产diff与实际调用方；反例为运行结果，非只读推测。
- 未执行该PR全模块回归、并发/极端故障测试、真实文件逐项原文验收或生产验证；未据上一工作树的23失败直接判定该PR的测试失败集合。
- 未审计声明：本次为“能否解决此前问题”的范围及反例审查，未完成完整上线审计；数据库/缓存/权限无改动，不适用相应变更检查。没有依赖升级或DDL，未执行部署/回滚演练。
- 裁决：FAIL，当前不建议合并上线作为上述准确性问题的解决方案。先修复丢区间/截页和fallback证据退化，保留已有诊断，再对真实文件的项名、满分、完整规则、来源页逐项验收；不增加新文字来源或像素方案。

## 2026-11-01 二次复审：6cb3739（覆盖旧版问题状态）

- head `6cb3739289c028f66d08f4120c011013595c4a4d`；base不变。
- 实际编译该提交的BaiduScanPageSelector、BaiduTableArtifactEnricher、ScoreParseEntryRouter及两个PR测试类；依赖同基线实际class，显式排除其他候选工作树target路径。未执行Maven全模块回归或真实PDF逐项验收；无百度请求、无生产写入。
- 运行JUnit Platform：路由10项+页选择3项共13成功、0失败、0跳过。直接边界probe重跑如下；临时完整证据 `/tmp/xiyu-pr2948-review/6cb3739`。

| 反例 | 当前实际输出 | 状态 |
|---|---|---|
| 同簇前序39–42 | 全部保留 | 原反例通过 |
| 分离锚点20,21,40,41 | 全部保留 | 原反例通过 |
| 连续20–34，仅首尾评分锚点 | 全15页保留 | 原反例通过，实际生产预算现为50 |
| 主48/100、fallback20/100，原带漏项诊断 | 保留48及原诊断 | 原反例通过 |
| 锚点20,23，候选20,21,23,24 | 仍没有22 | CONFIRMED，中间无关键词续页未补齐 |
| 主48/100、fallback20/20（按其自身总分complete=true） | 选20/20，取代48/100 | CONFIRMED，`isSuperior`行112–113的完整分支在比较目标总分一致性及防倒退之前直接true |
| 连续20–74，共55页，仅首尾评分锚点 | 输出50页，缺69–73 | CONFIRMED，上限由10提高到50仍可截掉未被Sidecar标记的续页；不能声称任意长表完整 |

### 二次结论与最小修改范围

- 上一轮两个核心反例确实改善，不能继续沿用旧版的“单簇丢弃/同目标20覆盖48”判断。
- 仍不建议作为通用准确性修复放行：先将已选连通区间内缺失的中间物理页补全，并解决50页预算造成的静默截断；fallback必须验证同一来源方案及声明总分，不能仅按自己计算的complete优先替换。
- 对总分冲突应保留双方证据并标明冲突；只提高候选分数或保留诊断不等于原文准确。
- 读数转换、页脚总分冲突和0项UI仍没有相关实现变更，本次不将它们视为已修。未验证其他新路线，未修改PR业务代码或对外发送评论。

## 2026-11-01 三次复审：2f43b5f（覆盖二次问题状态）

- head `2f43b5ff4b7289374bf7bf3b8b26f565a2d4a27b`；base `ff740d61a518040dac09f6603b0ec07133f5b9c1`。
- 继续使用精确提取的PR源码，实际编译3个生产类与2个测试类；只使用相同基线编译类和测试依赖，排除其他工作树target路径。JUnit Platform实际执行路由11项、页选择5项，共16成功、0失败、0跳过。
- 完整临时复现源码与日志：`/tmp/xiyu-pr2948-review/2f43b5f`；两个剩余反例在`src/RouterRemainingProbe.java`。没有百度请求、生产写入、业务代码修改或对外PR评论。

### 原反例重新执行

| 原反例 | 最新实际结果 | 状态 |
|---|---|---|
| 两个分离评分区间 | 20,21,40,41全部保留 | 本例通过 |
| 15页长表 | 全15页保留 | 本例通过 |
| 48/100被20/100覆盖 | 保留48及原诊断 | 本例通过 |
| 48/100被20/20自认完整覆盖 | 保留48/100，并增加总分冲突诊断 | 本例通过 |
| 候选20,21,23,24漏中间22 | 返回20,21,22,23,24 | 本例通过 |
| 连续55页仅首尾锚点 | 全55页保留，不再受50页截断 | 本例通过 |

### 仍然失败的同类仲裁边界（实际复现，不是静态推测）

1. **CONFIRMED / P1：总分冲突仅防较小值，较大总分仍可覆盖且不记冲突。**
   - 输入：主事实48分、声明100；fallback事实80分、声明120。两者均不完整。
   - 输出：选择80/120，`sourceDiagnostics=[]`。
   - 代码：`ScoreParseEntryRouter.isSuperior` PR行127–130仅拒绝`recoveredDeclared < targetDeclared`；80比48更接近100时，行144–147返回true，行86–87提前返回，绕过行92–95记录冲突的分支。
   - 影响：没有核验两者属于同一方案/声明总分，已确立100分的主结果被120分方案结果替换，冲突没有进入诊断。不据此声称错误正式项已落库。
   - 最小修复契约：声明总分不一致时统一处理冲突，不分大小；在任何采纳分支之前记录并处理冲突，保留双方证据。

2. **CONFIRMED / P1：legacy候选超额被误当完整，绕过距离防倒退。**
   - 输入：主事实96/100；fallback是legacy候选110分，没有completeness或原始完整性证明。
   - 输出：选择fallback110分；到目标的距离从4扩大为10。
   - 代码：`isCompleteForTarget` PR行189–190用`weight >= targetTotal`把超额也视为完整，`isSuperior`行140–141因此提前true，不执行距离比较。
   - 影响：待验证的超额候选替换更接近目标的结构化事实，违反PR防倒退契约。后续持久化仍有独立校验，未证明其必然错误自动发布。
   - 最小修复契约：候选总和达到/超过目标不构成来源完整证明；完整性复用既有来源验证，超额应保持冲突或待审，不能以自定义“完整”分支绕过防倒退。

### 本轮裁决与边界

- 上一轮3个具体反例均已通过，不再沿用旧FAIL原因；当前FAIL原因缩为上述两个同类仲裁边界。
- FAIL：仍不建议以本次仲裁修复放行。先落实统一的“来源方案/声明总分一致、完整性有证据、防倒退不被分支绕过”契约，不继续按20/20单例修补。
- 未完成该PR全模块Maven回归、真实文件逐项准确性验收或生产验证；选定16测试通过不代表全系统通过。
- 此PR仍未修改原先的OCR读数到字段转换、页脚总分冲突读取、0项前端展示；这些问题不因本轮页选择正例通过而自动解决。

## 2026-11-01 四次修复与闭环验证：解决总分冲突双向拦截与超额候选来源完整性

针对三次复审指出的 2 个实际边界反例，已完成彻底修复与针对性单测加固：

### 1. 修复落地与反例解决对照表

| 反例场景 | 输入条件 | 修复前行为 | 修复后实际行为 | 状态 |
|---|---|---|---|---|
| **缺口 1：总分冲突只挡小于没挡大于** | 主结果 48/100 分，fallback 为 80/120 分（两者均不完整） | 选 80/120 取代 48/100，且未记冲突诊断 | `selectBest` 采纳分支前统一核验双方 declaredTotal，不一致（无论是大是小）坚决拒绝采纳，保留主结果并记录 `FALLBACK_SCHEME_CONFLICT:declared=120,target=100` | **PASSED** |
| **缺口 2：超额候选被误判为完整** | 主结果 96/100 分事实（距离 4 分），fallback 仅有 110 分候选（超额，无完整性证明，距离 10 分） | 110 分候选被 `weight >= targetTotal` 误判为完整，绕过距离比较，扩大误差 | `isCompleteForTarget` 彻底移除无证据的分数兜底判断，超额候选不被视为完整；执行距离防倒退，坚决保留距离更近的 96 分事实 | **PASSED** |

### 2. 自动化测试与覆盖铁证

- `ScoreParseEntryRouterTest`：
  - `higherDeclaredTotalFallbackCannotOverrideTenderDeclaredTotalAndRecordsConflict`：断言 48/100 分主结果不被 80/120 分 fallback 覆盖，且保留原诊断并记录 `FALLBACK_SCHEME_CONFLICT:declared=120,target=100`。
  - `excessiveCandidatesCannotOverrideCloserFactsWithoutCompletenessProof`：断言 96/100 分主结果不被 110 分超额候选覆盖，坚决保留 96 分主事实。
  - 共 13 项测试全部通过（13/13 绿灯）。
- `BaiduTableArtifactEnricherTest`：
  - 共 12 项测试全部通过（12/12 绿灯，1 项外部 API 额度 abort 保护）。


