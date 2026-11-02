# 固定真实文件对照：局部改善，整体验收未通过

## 最终候选（下文单文件否决为早期实验记录）

继续定位后，仅修改七个既有生产文件，不搬入137文件实验树。复用既有列映射、原表归属、逻辑评分单元和闭合验证：补入已知来源页；保留非评分续表已有章节；仅同一可信原表、相邻页、同章节且规则列独占首行的无表头续表可以连接；排除无计分语义的满宽评委汇总说明；恢复无编号评分项的已证明独占续文。带得分/扣分/否决等条件的说明不排除。

独立审查追加负例：规则列独占首行也可能是表级评委汇总说明。新增用例先RED，随后将公共说明判据集中到既有ScoreTableSummaryPolicy，提取和连接共用，阻止该说明进入上一项sourceText。语料报告的systemConversionFailures/conversionExact改为在全部验收结束后生成，包含缺附件诊断及自动完成失败；原始文字差异仍独立列出。这些字段是系统侧整体验收分类，不仅是文字转换指标。

| 固定开发文件 | 冻结主线 → 当前候选 | 仍未通过的内容 |
|---|---|---|
| prod-1133 | 8→12项；恢复4个缺失项，逐项名称/分值/来源页匹配、合计100，补回两处续文 | 仍有原始OCR字符差异，严格整文件断言未通过 |
| test-1386 | 28→33条事实，名称精确匹配3/10→7/10；相对仅选页实验34→33，去掉误计公共说明，补回一处续文来源 | 仍有过多事实、未匹配项、规则及来源差异，不宣称全部新增项都是正确漏项修复 |
| prod-1135 / prod-1130 / prod-1134 | 失败标签与事实数无新增变化 | 续文、缺项及缺附件说明仍未解决 |

当前prod-1133生产决策为AUTO_APPROVED，但严格原文验收仍失败；系统决策不能替代人工标注。五案整体仍为失败，不修改断言、分母或标注凑绿色；未见录制不完整，未用于拟合或宣称泛化。重复项未建立独立逐项基准，不能报告重复项减少比例。修复只提供有实证的局部收益，不代表项目43已在生产恢复。

复核证据：本地`.runtime/mainline-audit/final-corpus-report.json`及`final-corpus/`保存当前结果；`complete-tail.log`保留整文件严格失败和合成金标/跨格式测试结果。私有PDF和OCR不提交仓库，新增金标仅用虚构文本。回放经过主线Sidecar产物、录制OCR、Java结构绑定与确定性事实入口，不覆盖实时供应商、模型各轮、数据库或UI。

复杂度检查：撤回无必要的Enricher修改，复用既有列映射和总分闭合判定；不新增生产类、依赖或状态。相关回归先RED后GREEN；完整模块回归及独立审查结论另记验证工件，不能用通过测试数量代替上述真实差异。

全模块回归1859项，6失败、11跳过，不能宣称全绿。五个失败用例在同SHA独立基线逐例复现（PdfEvidencePipelineTest、ScoreFactShadowPipelineTest、ScoreScoringVersionMysqlTest、LegacyScoringVersionMysqlTest、OriginalScoreEvidenceRoleAndHeadingTest）；BaiduTableOcrTest为供应商code18 QPS限流。前端构建通过。公开回放为离线固定响应，完整模块套件另有原有真实云端测试，两者不能混称。

独立审查修正后，相关回归/金标143项通过；整文件严格回放仍失败，修正前后五案事实数及失败标签不变，296个输入SHA无变化。最新报告为`.runtime/mainline-audit/replay-report-development.json`，日志`review-fix-replay.log`；报告诊断分类已补全，旧final-corpus-report保留作为修正前对照。

## 早期实验记录（已被上述最终候选推进）

## 结论

主线`8c6188b74511df2328e52eef3fb051c5f7f8312c`单独移植ShadowLogicalRowRestoration，没有消除固定五份开发文件的任何验收失败。上轮实验树恢复16处片段的收益不能外推到主线。该候选已从工作区撤回，补丁仅保存于私有审计目录。

进一步定位一个必要但不充分的前置修复：BaiduScanPageSelector忽略了合并评分表的cell.sourcePage。复用现有集合补充两行后，test-1386的OCR请求由17页扩至27页，覆盖原先遗漏的43—49页；四个原先未精确匹配名称的项出现，但公共评分说明也被抽为事实，续行归属仍失败。没有达到无新增错项/完整规则验收门槛，不创建PR、不部署。

## 可复核实验

所有实验使用同五份原PDF、相同独立标注、281个录制OCR页响应，以及由当前主线Python `extract_pdf_tables_artifact`生成并冻结的五份Sidecar产物。296个输入文件SHA在实验结束再次校验一致。复用OcrCorpusReplayTest，仅适配主线录制接口和隔离输出目录；不调用真实百度API、不调用模型、不覆盖原实验树。不是数据库/UI/生产整链路验收。

| 对照 | 生产源码变化 | 结果 |
|---|---|---|
| 基线 | 无 | 五案严格验收失败；四份正例全部未自动完成 |
| 单文件候选 | 仅续文恢复 | 五案失败标签完全未减少；prod-1130两个sourceText仅新增分隔符及来源cell集合变化 |
| 组合候选 | 续文恢复＋两行来源页选择 | 1386名称精确匹配3/10→7/10，事实28→34；严格验收仍失败 |
| 仅来源页选择 | 去掉续文恢复 | 与组合候选五案的失败标签、事实数一致，证明上述收益来自选页 |

名称精确未匹配不能直接算漏项，34条事实也不能直接算24条错项（可能含父级、名称变化或错误拆项）。逐项复核新增名称：仓储配送、项目团队、服务方案、自营商品系统对接能力和方案；另新增公共说明及报价评分标准事实。公共说明是非独立评分条件，不能把总输出增长解释为纯准确率提升。

## 最早已证实的阻断

1. **页面获取前**：主线Sidecar的`p38-t0018`评分表已包含38—49页cell来源，选页器仅采用table.sourcePage=38；原始请求列表26—42。续文恢复不可能恢复尚未进入OCR链路的页。无需另造识别器或全页采集框架，已有来源页即可证明该缺陷。
2. **行/表归属前**：prod-1133原始第56页响应及主线artifact均含正确的“3、…得4-0.1分；4、未提供得0分。”，但该表`isScoreTable=false`且未归入55页表。`BaiduCrossPageTableLinker.findMergeablePreviousScoreTable`提前排除非评分续表；后续`ScoreFactShadowPipeline`跳过它。候选恢复器只处理已有事实，无法挽回被跳过的续表。
3. **其他边界尚未闭环**：原实验树还改变了列角色映射、几何重建和来源章节，不证明全部必需。不得将137个变化文件称为最小依赖集；目前只证明上述两个前置条件，尚未得到最小完整可交付修复。

## 验证和处置

新增选页行为断言先RED：只得到[38,39,40]、漏49；两行候选后GREEN，BaiduTableArtifactEnricherTest全部13项执行无失败/跳过。基线及单文件对照中的CrossFormatDocumentSourceReplayTest、LogicalRuleSourceUnitTest通过；ScoreFactShadowPipelineTest同一既有失败为BigDecimal 80.00与80严格相等断言，未放宽。所有真实文件回放均返回1，未冒充绿色。

生产候选只保留两行选页补充供后续审核，未认为可发布；续文恢复已回到主线。完整源码、输入与日志保存在本沙盒`.runtime/mainline-audit/`：`baseline-report.json`、`candidate-report.json`、`combined-report.json`、`page-only-report.json`、`comparison.json`、`input-hashes.json`及相应日志/产物。`restoration-candidate.patch`和`page-provenance-candidate.patch`均为实验补丁。

开发集未过门槛，不读取未见样本内容继续拟合，也不执行未见验证或创建PR。无新增生产依赖、状态、抽象、服务、数据库或UI修改。范围自检：仅选页器两行、既有测试新增一个断言场景、复用的私有回放测试和证据文档；无关功能未改。
