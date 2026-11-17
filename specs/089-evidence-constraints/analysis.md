# Spec / Plan / Tasks 一致性分析

2026-11-17；working_tree交付；基线a20d1a7b415a07cd2d3dd252b6381c49f00f761d。

| 要求 | 实现边界 | 执行证据 |
| --- | --- | --- |
| FR001/003 | 先执行可验证日期、合同金额、类型/区域/岗位等，再计数；未知期限明确待核验 | KnowledgeEvidenceConstraintsTest、EstimateHardConstraintsTest |
| FR002 | 复用递增目标；1120原件资格至少1，目标3 | ProductionCrossChapterPerformanceGoldenReplayTest及原件只读核验 |
| FR004 | 明确证书等级验证并保留名称等级事实 | CertificateScorePolicyTest及五星/三星正反例 |
| FR005/006 | CAS只改预判列、规则版本封印、读侧隐藏旧分 | EstimateHardConstraintsMysqlTest、ScoreEstimateFreshnessTest |
| FR007 | 明确日期金额引用不合格/缺失待核验；单证不证明完整资格 | PerformanceDateScoringTest、QualificationWholeProofTest |
| FR008 | 合成金标，无原件入库 | case-evidence-hard-conditions |

无互相冲突的需求，材料未核验时保持阶段一粗估数值、状态待核验，阶段二不发布无证明正分。四项新增反例旧版337测试4失败0错误0跳过；定向补修回归已通过，最终内容仍须以下自动门禁完成。

限制：不承诺所有自由文本、材料真伪、跨合同事实归属均自动证明。原件递交日与项目登记日期差异未完成澄清链核对，保留上界待核验。非证书资料增删的实时指纹联动未新增，需主动刷新。原有实际评分历史记录和生产项目未重算。

T007以当前独立Git副本.git/review-gate/state.json最终状态及哈希报告为准；旧共享基线的BLOCKED记录仅是失效历史，不是当前通过凭证。


追加闭环：混合金额回归已通过要求合同标签的提取重载解决，保留旧通用API兼容；正式评分使用parseForScoring，不继承知识库材料缺口提示，保留明确日期/金额及未知报价期限保护。品牌多授权的先过滤再按brandId聚合已通过真实MySQL两种顺序及只计一次验证。以上接管后均需新完整门禁状态MERGE_READY，旧ESCALATE/BLOCKED凭证不复用。
